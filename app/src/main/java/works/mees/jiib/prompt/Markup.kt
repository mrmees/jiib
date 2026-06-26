package works.mees.jiib.prompt

/**
 * PromptMarkup — a SAFE, renderer-neutral markup grammar (NOT HTML, NOT Pango, NOT a WebView).
 * A faithful port of upstream `packages/js/src/markup.ts`: a total tokenizer producing a [MarkupNode]
 * AST plus [markupToPlainText]. PURE — NO `androidx.compose` import — so it is host-unit-testable;
 * the AnnotatedString translation lives in 12-04 (T-12-03: the parser yields a neutral AST only and
 * its decoded output is NEVER re-scanned as markup, so `&amp;lt;` → the literal `&lt;`).
 *
 * Grammar (all rules from markup.ts, verbatim):
 *  - Emphasis tags `b`/`i`/`u` are CASE-SENSITIVE (`<B>` is unknown → stripped, inner text kept).
 *  - `<color:#rrggbb>` / `<bgcolor:#rrggbb>` require exactly `^#[0-9a-fA-F]{6}$` (a 3-hex `#fff` is
 *    invalid → tag stripped, inner text preserved).
 *  - `<size:value>` value is lowercased and must be one of small/normal/large/x-large.
 *  - Unknown/invalid/mismatched tags are dropped but their inner text is preserved at the current level.
 *  - A `<` with no matching `>` is emitted as a literal `<`.
 *  - Entities are decoded EXACTLY ONCE, left-to-right: `&lt;`→`<`, `&gt;`→`>`, `&amp;`→`&`, and the
 *    two-char backslash-n (`\n`) → a real LF. The decoded output is never re-parsed.
 */

private val SIZE_TOKENS: Map<String, PromptTextSize> = mapOf(
    "small" to PromptTextSize.SMALL,
    "normal" to PromptTextSize.NORMAL,
    "large" to PromptTextSize.LARGE,
    "x-large" to PromptTextSize.X_LARGE,
)

private val EMPHASIS_TAGS: Map<String, EmphasisTag> = mapOf(
    "b" to EmphasisTag.B,
    "i" to EmphasisTag.I,
    "u" to EmphasisTag.U,
)

private val HEX_COLOR = Regex("^#[0-9a-fA-F]{6}$")

/**
 * Decode protocol entities in [s] in a single left-to-right pass. The result is never re-scanned, so a
 * literal `&amp;lt;` decodes to `&lt;` (not `<`). The two-char sequence backslash-`n` becomes an LF.
 */
private fun decodeEntities(s: String): String {
    val out = StringBuilder(s.length)
    var i = 0
    while (i < s.length) {
        when {
            s.startsWith("&lt;", i) -> { out.append('<'); i += 4 }
            s.startsWith("&gt;", i) -> { out.append('>'); i += 4 }
            s.startsWith("&amp;", i) -> { out.append('&'); i += 5 }
            s.startsWith("\\n", i) -> { out.append('\n'); i += 2 }
            else -> { out.append(s[i]); i += 1 }
        }
    }
    return out.toString()
}

/** A mutable open-tag frame during parsing: a (nullable root) tag node + its accumulating children. */
private class OpenFrame(val tag: String?, val children: MutableList<MarkupNode>)

/**
 * Build a typed tag node from the raw inner text of an opening tag (e.g. `"color:#22c55e"`), with an
 * empty child list to be filled by the parser. Returns `null` for an unknown/invalid tag — the parser
 * then strips the tag but keeps accumulating inner text at the current level.
 */
private fun makeTagNode(inner: String, children: MutableList<MarkupNode>): MarkupNode? {
    EMPHASIS_TAGS[inner]?.let { return MarkupNode.Emphasis(it, children) }

    val colon = inner.indexOf(':')
    if (colon == -1) return null
    val name = inner.substring(0, colon)
    val value = inner.substring(colon + 1)

    if (name == "color" || name == "bgcolor") {
        if (!HEX_COLOR.matches(value)) return null
        return MarkupNode.Color(background = name == "bgcolor", value = value, children = children)
    }
    if (name == "size") {
        val size = SIZE_TOKENS[value.lowercase()] ?: return null
        return MarkupNode.Size(size, children)
    }
    return null
}

/**
 * Tokenize [raw] into a [MarkupNode] AST (the list of top-level nodes). Total — never throws — and
 * tolerant: unknown/invalid/mismatched/unclosed tags degrade gracefully (markup stripped, inner text
 * preserved). Faithful port of `parseMarkup` in markup.ts.
 */
fun parseMarkup(raw: String): List<MarkupNode> {
    val root = OpenFrame(tag = null, children = mutableListOf())
    val stack = ArrayDeque<OpenFrame>().apply { addLast(root) }
    val buf = StringBuilder()

    fun top(): OpenFrame = stack.last()
    fun flush() {
        if (buf.isNotEmpty()) {
            top().children.add(MarkupNode.Text(decodeEntities(buf.toString())))
            buf.setLength(0)
        }
    }

    var i = 0
    while (i < raw.length) {
        if (raw[i] == '<') {
            val close = raw.indexOf('>', i)
            if (close == -1) { buf.append(raw[i]); i += 1; continue }
            val inner = raw.substring(i + 1, close)
            i = close + 1
            if (inner.startsWith("/")) {                 // closing tag
                flush()
                val name = inner.substring(1).trim()
                val open = top()
                if (open.tag != null && open.tag == name) {
                    stack.removeLast()
                }
                // mismatched/extra close: ignore; content already flushed to the current level.
                continue
            }
            // opening tag
            val children = mutableListOf<MarkupNode>()
            val node = makeTagNode(inner, children)
            flush()
            if (node != null) {
                top().children.add(node)
                stack.addLast(OpenFrame(tag = inner, children = children))
            }
            // unknown/invalid tag: skip the tag, keep accumulating inner text at the current level.
            continue
        }
        buf.append(raw[i]); i += 1
    }
    flush()
    // Any unclosed tags simply keep whatever children they collected.
    return root.children
}

/**
 * Project [raw] PromptMarkup to its plain-text fallback: the in-order concatenation of every text
 * node's decoded content, ignoring all tag chrome. This is the conformance `plain_text` field.
 */
fun markupToPlainText(raw: String): String = flatten(parseMarkup(raw))

private fun flatten(nodes: List<MarkupNode>): String {
    val out = StringBuilder()
    for (n in nodes) {
        when (n) {
            is MarkupNode.Text -> out.append(n.text)
            is MarkupNode.Emphasis -> out.append(flatten(n.children))
            is MarkupNode.Color -> out.append(flatten(n.children))
            is MarkupNode.Size -> out.append(flatten(n.children))
        }
    }
    return out.toString()
}
