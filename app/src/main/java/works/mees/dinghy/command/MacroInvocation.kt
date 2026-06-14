package works.mees.dinghy.command

/**
 * Thrown when a string macro-param value contains a forbidden character. The whole macro invocation is
 * rejected (never escaped/stripped/truncated); the Macros screen surfaces this as a SeverityToast.
 *
 * @param paramName the offending parameter's key.
 * @param reason    a short, non-sensitive explanation (no echo of the offending value).
 */
class MacroParamRejected(
    val paramName: String,
    val reason: String,
) : Exception("Macro param '$paramName' rejected: $reason")

/**
 * The load-bearing SECURITY gate of Phase 8 (ASVS V5, T-08-03-T1, block_on:high, D-10).
 *
 * D-10 allows the system alphanumeric keyboard for STRING macro params — the single sanctioned
 * alpha-keyboard site in the printer-control surface. That free text is the gcode-injection vector and
 * breaks the [PrinterCommands] invariant ("no user-supplied string is ever concatenated into a
 * script"). [MacroInvocation.build] restores the invariant by assembling the macro-invocation gcode
 * line itself, under a REJECT-on-forbidden-char policy.
 *
 * POLICY IS REJECT, NOT ESCAPE. Every string value is validated BEFORE concatenation. If a value
 * contains ANY forbidden character it throws [MacroParamRejected] — the whole invocation is refused, no
 * stripped/escaped/truncated line is ever produced. Rationale: Klipper's quote-parsing semantics are
 * UNCONFIRMED in RESEARCH, so escaping a character whose parser meaning we have not verified is exactly
 * the insecure path; rejecting any suspicious character is the safe default.
 *
 * The FORBIDDEN set (exactly):
 *  - carriage return `\r` (0x0D) and line feed `\n` (0x0A) — would split into a second gcode line;
 *  - semicolon `;` — Klipper treats `;` as a gcode comment delimiter → command-smuggling;
 *  - tab `\t` (0x09);
 *  - the embedded double-quote `"` — would break out of the `KEY="VALUE"` grammar;
 *  - ALL OTHER ASCII control characters: the full ranges 0x00–0x1F and 0x7F (DEL).
 *
 * This file is PURE: NO I/O, NO coroutines, NO Compose; host-testable. It does NOT dispatch — the
 * caller (08-06) feeds the result to [PrinterCommands.scriptParams] and never the raw user string. It
 * also does NOT clamp numeric ranges — numeric range-clamping is owned by the IME entry field (D-07).
 */
object MacroInvocation {

    /**
     * Assemble a single Klipper macro-invocation gcode line `NAME KEY="VALUE" KEY="VALUE" ...`.
     *
     * String values are validated then quoted as `KEY="value"`. Any forbidden character → throw
     * [MacroParamRejected] (no line is returned). The macro [macroName] comes from the discovered macro
     * list (never free-text) and is uppercased for dispatch. Insertion order of [params] is preserved.
     *
     * NOTE on numerics: this overload quotes every value as a string. A caller that wants the unquoted
     * `KEY=<number>` form for an already-clamped numeric uses [buildTyped].
     */
    fun build(macroName: String, params: Map<String, String>): String =
        buildLine(macroName, params.map { (k, v) -> Triple(k, v, false) })

    /**
     * Variant where each param declares whether it is numeric (`isNumeric = true` → emitted unquoted as
     * `KEY=<value>`) or a string (`KEY="<value>"`). String values are still rejected on any forbidden
     * char; numeric values are additionally REQUIRED to be a plain numeric literal (WR-04): they are
     * emitted UNQUOTED, so an empty value would produce a malformed bare `KEY=` token, and a value with
     * whitespace (e.g. a raw Jinja default expression like `printer.extruder.target * 0.5` seeded from
     * `MacroParam.default`) would token-split the macro line into extra `KEY=VALUE` pairs — potentially
     * overriding another param. Same REJECT-not-escape policy as the string path. (IME field clamping
     * covers the interactive path, but the default-seed path bypasses clamping entirely.)
     */
    fun buildTyped(macroName: String, params: List<Triple<String, String, Boolean>>): String =
        buildLine(macroName, params)

    /**
     * Assemble a macro invocation from a single freeform RAW argument string (the "CLI made easier"
     * fallback for `rawparams` macros and macros whose params can't be inferred). The whole [rawArgs]
     * tail is validated by [rejectForbidden] (same REJECT-not-escape policy as string params: `\n \r \t
     * ; "` and control chars are refused) then appended verbatim after the uppercased macro name. Spaces
     * and `=` are permitted — they are the raw arg grammar. A blank [rawArgs] yields the bare macro name.
     */
    fun buildRaw(macroName: String, rawArgs: String): String {
        val trimmed = rawArgs.trim()
        if (trimmed.isEmpty()) return macroName.uppercase()
        rejectForbidden("(raw args)", trimmed)
        return "${macroName.uppercase()} $trimmed"
    }

    private fun buildLine(macroName: String, params: List<Triple<String, String, Boolean>>): String {
        val sb = StringBuilder(macroName.uppercase())
        for ((key, value, isNumeric) in params) {
            rejectForbidden(key, value)
            if (isNumeric) {
                rejectNonNumeric(key, value)
                sb.append(' ').append(key).append('=').append(value)
            } else {
                sb.append(' ').append(key).append('=').append('"').append(value).append('"')
            }
        }
        return sb.toString()
    }

    /**
     * REJECT (throw) unless [value] is a plain numeric literal safe to emit unquoted (WR-04).
     * `toDoubleOrNull` alone is too lenient — `Double.parseDouble` trims surrounding whitespace, so
     * `" 210"` would parse yet still token-split when emitted verbatim; the explicit no-whitespace
     * check closes that hole.
     */
    private fun rejectNonNumeric(paramName: String, value: String) {
        val plainNumber = value.isNotEmpty() &&
            value.none { it.isWhitespace() } &&
            value.toDoubleOrNull() != null
        if (!plainNumber) {
            throw MacroParamRejected(
                paramName,
                "numeric value is not a plain number (unquoted-emission guard)",
            )
        }
    }

    /** REJECT (throw) if [value] contains any forbidden character. Never escapes/strips. */
    private fun rejectForbidden(paramName: String, value: String) {
        for (c in value) {
            val code = c.code
            val forbidden =
                c == '\n' || c == '\r' || c == '\t' || c == ';' || c == '"' ||
                    code in 0x00..0x1F || code == 0x7F
            if (forbidden) {
                throw MacroParamRejected(
                    paramName,
                    "value contains a forbidden character (gcode-injection guard)",
                )
            }
        }
    }
}
