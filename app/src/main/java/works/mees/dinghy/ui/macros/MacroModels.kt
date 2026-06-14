package works.mees.dinghy.ui.macros

/**
 * Headless data models for the macro layer (MACRO-02/MACRO-03). Plain immutable Kotlin data classes —
 * NO I/O, NO Compose — in the same spirit as [works.mees.dinghy.state.Capabilities]. They carry the
 * heuristically-parsed macro parameter metadata ([MacroParam]) and the per-macro view-model the macro
 * screens render ([MacroVm]).
 */

/**
 * One macro parameter discovered by heuristically scanning a macro `.gcode` body
 * ([MacroParamParser.parseMacroParams], D-09).
 *
 * @param name    the parameter name as it appears after `params.` (Klipper convention is UPPERCASE).
 * @param type    `int` / `string` / `double` if the body declared one via the Jinja filter; `null`
 *                otherwise. NOTE the Mainsail-regex reality: a trailing `|float` AFTER a
 *                `|default(...)` is NOT captured (it falls into the regex tail) → `type == null`.
 * @param default the default expression captured from `|default(<expr>)`, or `null` if none declared.
 */
data class MacroParam(
    val name: String,
    val type: String?,
    val default: String?,
    /**
     * Heuristic (Codex extraction doc §"required"): true when the param is referenced WITHOUT a
     * `|default(...)` — i.e. the macro author expects the caller to supply it. Optional (has a default)
     * → false. Defaulted to `false` so preview/test constructions that omit it still compile. This is a
     * DISPLAY hint only (a `*` marker in the param field); Execute is never hard-gated on it.
     */
    val required: Boolean = false,
) {
    /**
     * Whether this param routes to the numeric IME field (D-07/D-12). Only an EXPLICITLY-declared
     * `int`/`double` filter is treated as numeric; `string` and an un-declared/unclassified `null` type
     * fall to the string keyboard field — the heuristic must NOT block Execute on a param it couldn't
     * classify, and the safe default for an unknown type is the (sanitized) string path.
     */
    val isNumeric: Boolean
        get() = type == "int" || type == "double"
}

/**
 * View-model for a single discovered macro the launcher/system screens render.
 *
 * @param name         the macro NAME (from the discovered macro list — never free-text).
 * @param isBookmarked whether the user pinned it to the Bookmarked launcher (from [MacroPrefs]).
 * @param isHidden     whether it is an underscore-prefixed (System / private) macro (MACRO-03).
 * @param params       the heuristically-parsed parameter list (may be empty — never null).
 */
data class MacroVm(
    val name: String,
    val isBookmarked: Boolean,
    val isHidden: Boolean,
    val params: List<MacroParam>,
)
