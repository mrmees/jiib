package works.mees.dinghy.prompt

/**
 * The PURE, TOTAL Macro Prompt Protocol line parser (PROMPT-01) — a faithful port of upstream
 * `packages/js/src/parse-action.ts` plus the `button.ts`/`style.ts`/`image.ts` sub-parsers. Mirrors
 * this project's tolerant-total-parser idiom ([works.mees.dinghy.ui.spool.scan.parseSpoolId] /
 * [works.mees.dinghy.ui.console.ConsoleSeverity.classify]): NO I/O, NO coroutines, NO Compose — same
 * input always yields the same output, and a malformed / partial / unknown line NEVER throws (it
 * returns `null` or a degraded event). A garbage gcode stream cannot wedge the parser (T-12-01).
 *
 * LOAD-BEARING (Pitfall 2, mock-vs-reality): `store.gcodeResponses` retains the leading `// `, and the
 * upstream PREFIX matches it AS-IS — so [PREFIX] is `"// action:prompt_"` and is matched verbatim with
 * NO `// ` stripping.
 */

/** The exact line prefix every prompt command carries (the `// ` is retained, NOT stripped). */
const val PREFIX: String = "// action:prompt_"

/** The image-path allow-list (V5 path-traversal mitigation, T-12-02) — enforced in the PURE layer. */
private val IMAGE_PATH_PREFIX = "config/"

/**
 * The manufactured disconnect event (not produced from a line). Mirrors `disconnectEvent()` — the
 * reducer treats it as a clear-active-prompt trigger. The fixture driver maps `"__disconnect__"` here.
 */
fun disconnectEvent(): PromptEvent = PromptEvent.Disconnect

/**
 * Parse a raw console/action [line] into a typed [PromptEvent], or `null` for a non-prompt line, an
 * unknown `prompt_*` command, or an empty-label button (dropped). Total — never throws. Faithful port
 * of `parseAction` in parse-action.ts: split on the FIRST space into `cmd` + `arg`, dispatch by `cmd`.
 */
fun parseAction(line: String): PromptEvent? {
    if (!line.startsWith(PREFIX)) return null
    val rest = line.substring(PREFIX.length)
    val sp = rest.indexOf(' ')
    val cmd = if (sp == -1) rest else rest.substring(0, sp)
    val arg = if (sp == -1) "" else rest.substring(sp + 1)

    return when (cmd) {
        "begin" -> PromptEvent.Begin(title = arg)
        "text" -> PromptEvent.Text(text = arg)
        "show" -> PromptEvent.Show
        "end" -> PromptEvent.End
        "row_start" -> PromptEvent.RowStart
        "row_end" -> PromptEvent.RowEnd
        "button_group_start" -> PromptEvent.ButtonGroupStart
        "button_group_end" -> PromptEvent.ButtonGroupEnd
        "markup" -> PromptEvent.Markup(markup = arg, plainText = markupToPlainText(arg))
        "button" -> parseButtonFields(arg)?.let {
            PromptEvent.Button(it.label, it.gcode, it.style)
        }
        "footer_button" -> parseButtonFields(arg)?.let {
            PromptEvent.FooterButton(it.label, it.gcode, it.style)
        }
        "image" -> {
            val parts = arg.split("|")
            PromptEvent.Image(
                path = parts.getOrNull(0).orEmpty().trim(),
                alt = parts.getOrNull(1).orEmpty().trim(),
                scale = parseImageScale(parts.getOrNull(2)),
            )
        }
        "target" -> {
            val targets = arg.split(",")
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
            PromptEvent.Target(targets)
        }
        "size" -> PromptEvent.Size(PromptSize.fromToken(arg))
        "align" -> PromptEvent.Align(PromptAlign.fromToken(arg))
        else -> null   // unknown prompt_* command: ignored
    }
}

/**
 * Parse a button arg (`label|gcode|style`) into [ButtonFields], or `null` if the label is empty
 * (the button is dropped). gcode defaults to the label when omitted/blank; style normalizes via
 * [normalizeStyle]. Faithful port of `parseButtonFields` in button.ts.
 */
fun parseButtonFields(raw: String): ButtonFields? {
    val parts = raw.split("|")
    val label = parts.getOrNull(0).orEmpty().trim()
    if (label.isEmpty()) return null
    val gcodeRaw = parts.getOrNull(1).orEmpty().trim()
    val gcode = gcodeRaw.ifEmpty { label }
    return ButtonFields(label = label, gcode = gcode, style = normalizeStyle(parts.getOrNull(2)))
}

/**
 * Normalize a raw style token to a [PromptStyle] (case-insensitive); any unknown/empty/null token
 * degrades to [PromptStyle.SECONDARY]. Faithful port of `normalizeStyle` in style.ts.
 */
fun normalizeStyle(raw: String?): PromptStyle =
    PromptStyle.fromToken(raw.orEmpty().trim()) ?: PromptStyle.SECONDARY

/**
 * The advisory image-scale grammar: digits with at most one dot, strictly positive and finite, else
 * `null` (an absent field, a comma, a sign/exponent, `Infinity`, `abc`, or `<= 0` all reject).
 * Faithful port of `parseImageScale` in image.ts. `"1"` → `1.0`; `"0.75"` → `0.75`.
 */
fun parseImageScale(raw: String?): Double? {
    if (raw == null) return null
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.contains(',')) return null
    // digits with an optional single dot; no sign/exponent/Infinity.
    if (!Regex("^[0-9]*\\.?[0-9]+$").matches(trimmed)) return null
    val n = trimmed.toDoubleOrNull() ?: return null
    if (!n.isFinite() || n <= 0.0) return null
    return n
}

/**
 * The image-path allow-list (T-12-02, V5 path-traversal mitigation) — enforced HERE in the pure layer
 * BEFORE any load (12-04 only ever receives a vetted `config/...` path). Faithful port of
 * `isValidImagePath` in image.ts: reject empty / leading `/` or `~` / non-`config/` prefix / a `\`,
 * and reject any empty / `.` / `..` segment or a segment containing `:`.
 */
fun isValidImagePath(path: String): Boolean {
    if (path.isEmpty() || path.startsWith("/") || path.startsWith("~")) return false
    if (!path.startsWith(IMAGE_PATH_PREFIX)) return false
    if (path.contains('\\')) return false
    for (seg in path.split("/")) {
        if (seg.isEmpty()) return false           // no empty segments (also catches `//`)
        if (seg == "." || seg == "..") return false
        if (seg.contains(':')) return false
    }
    return true
}
