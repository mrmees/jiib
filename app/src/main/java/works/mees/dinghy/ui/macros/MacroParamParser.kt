package works.mees.dinghy.ui.macros

/**
 * PURE, heuristic macro-parameter parser (MACRO-02, D-09). Mirrors the
 * [works.mees.dinghy.command.PrinterCommands] purity discipline: NO I/O, NO coroutines, NO Compose;
 * same input → same output; fully host-testable off-hardware.
 *
 * This reproduces — VERBATIM — Mainsail's `getMacroParams` two-pass regex scan of a macro `.gcode`
 * body. Param discovery from a Jinja macro body is fundamentally a heuristic (the ecosystem standard):
 * a missed param simply degrades to "not pre-filled in the popup", it NEVER throws. The macro `.gcode`
 * body shape on the live Ender 5 (probed 2026-06-02, see docs/moonraker-capabilities.md and
 * /fixtures/macro_bodies_e5.json) is a newline-joined STRING — passed straight to the regex; if a
 * future printer returned an array, join it with "\n" before calling [parseMacroParams].
 *
 * The regexes are the EXACT Mainsail expressions and MUST NOT be "improved". A proven consequence on
 * the real fixture: `params.BED_TEMP|default(60)|float` parses to type=null because the `default`
 * group consumes `60` and the trailing `|float` falls into the regex `.*?` tail. The
 * MacroParamParserTest asserts that REAL behavior.
 */
object MacroParamParser {

    /**
     * Verbatim Mainsail `paramRegex`. Capture groups:
     *  1 = param name (after `params.`)
     *  2 = leading type filter `|int|string|double` (optional)
     *  3 = `|default(<expr>)` value (optional, with optional surrounding quotes stripped)
     *  4 = trailing `|int|string` type filter (optional)
     */
    val PARAM_REGEX = Regex(
        """\{%?.*?params\.([A-Za-z_0-9]+)(?:\|(int|string|double))?(?:\|default\('?"?(.*?)"?'?\))?(?:\|(int|string))?.*?%?\}""",
    )

    /**
     * Verbatim Mainsail `paramInRegex` — the `'NAME' in params` / `'NAME' not in params` membership
     * guard idiom. Capture group 1 = the guarded param name. (Absent on the live E5; covered by one
     * clearly-labelled synthetic body in the test.)
     */
    val PARAM_IN_REGEX = Regex(
        """\{%?.*?if.*?'([A-Za-z_0-9]+)' (?:not )?in params.*?%?\}""",
    )

    /**
     * Bracket-access param idiom `params["NAME"]` / `params['NAME']` (Codex extraction doc), with an
     * OPTIONAL trailing `|default(<expr>)` captured the same way the dot-regex does (group 2). Additive:
     * the verbatim Mainsail dot-regex is left untouched. A bracket param with a default is optional and
     * pre-fills it; without one it is required (BLOCK-1 fix).
     *  group 1 = param name · group 2 = default expr (optional, surrounding quotes stripped)
     */
    val PARAM_BRACKET_REGEX = Regex("""params\s*\[\s*['"]([A-Za-z_0-9]+)['"]\s*](?:\s*\|\s*default\('?"?(.*?)"?'?\))?""")

    /** A macro that reads the full unparsed arg string. When present, param inference is unreliable. */
    private val RAWPARAMS_REGEX = Regex("""\brawparams\b""")

    /**
     * Scan a macro `.gcode` body for declared parameters. Total function: garbage input yields
     * whatever could be detected (possibly empty), never an exception. First-seen order is preserved
     * and a name is recorded only ONCE (dedup) — first-seen wins (floor-safe `if (key !in map)` insert,
     * since `Map.putIfAbsent` is API 24 and minSdk is 23).
     */
    fun parseMacroParams(gcodeBody: String): List<MacroParam> {
        val out = linkedMapOf<String, MacroParam>() // first-seen order, dedup by name
        // BLOCK-2 fix: collect membership-guarded names FIRST. `{% if 'X' in params %}` means the macro
        // checks presence before use → X is optional even though a later `{params.X}` (dot-access, runs
        // before the first-seen dedup insert) would otherwise mark it required.
        val optionalByGuard = PARAM_IN_REGEX.findAll(gcodeBody).map { it.groupValues[1] }.toSet()
        for (m in PARAM_REGEX.findAll(gcodeBody)) {
            val name = m.groupValues[1]
            // type = leading filter (group 2) else trailing filter (group 4) else null
            val type = m.groupValues[2].ifEmpty { m.groupValues[4] }.ifEmpty { null }
            val default = m.groupValues[3].ifEmpty { null }
            // required heuristic (Codex doc): a param referenced WITHOUT |default(...) is required,
            // unless a membership guard proves it optional.
            // floor-safe first-seen dedup (Map.putIfAbsent is API 24; minSdk is 23).
            if (name !in out) out[name] = MacroParam(name, type, default, required = default == null && name !in optionalByGuard)
        }
        for (m in PARAM_BRACKET_REGEX.findAll(gcodeBody)) {
            val name = m.groupValues[1]
            val default = m.groupValues[2].ifEmpty { null }
            if (name !in out) out[name] = MacroParam(name, null, default, required = default == null && name !in optionalByGuard)
        }
        // Any guarded param NOT already discovered by dot/bracket access is added as optional.
        for (name in optionalByGuard) {
            if (name !in out) out[name] = MacroParam(name, null, null, required = false)
        }
        return out.values.toList()
    }

    /** Whether the macro body slurps the raw arg string (Codex doc): inference is unreliable → raw-args UI. */
    fun usesRawParams(gcodeBody: String): Boolean = RAWPARAMS_REGEX.containsMatchIn(gcodeBody)
}
