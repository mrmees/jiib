package works.mees.dinghy.config

/** Result of parsing a user increment string. */
sealed interface IncrementParse {
    /** Valid: [canonical] is the space-stripped verbatim string; [values] the parsed doubles in order. */
    data class Ok(val canonical: String, val values: List<Double>) : IncrementParse
    /** Invalid: [reason] is a short user-facing message. */
    data class Error(val reason: String) : IncrementParse
}

/** Keystroke filter: keep only digits, '.', ',', and space; drop everything else. */
fun filterIncrementInput(raw: String): String =
    raw.filter { it.isDigit() || it == '.' || it == ',' || it == ' ' }

/**
 * Parse a user increment string into canonical form + values, or an error.
 *  - spaces stripped; split on ',' ; tokens kept verbatim (so "0.001" is preserved).
 *  - each token: non-blank, at most one '.', parses as a finite Double > 0.
 *  - at least one value; when [maxCount] != null, EXACTLY [maxCount] values (fixed-count controls).
 */
fun parseIncrementInput(raw: String, maxCount: Int?): IncrementParse {
    val stripped = raw.replace(" ", "")
    if (stripped.isEmpty()) return IncrementParse.Error("Enter at least one value")
    val tokens = stripped.split(",")
    val values = ArrayList<Double>(tokens.size)
    for (tok in tokens) {
        if (tok.isEmpty()) return IncrementParse.Error("Empty value — check the commas")
        if (tok.count { it == '.' } > 1) return IncrementParse.Error("'$tok' is not a number")
        val v = tok.toDoubleOrNull()
        if (v == null || !v.isFinite()) return IncrementParse.Error("'$tok' is not a number")
        if (v <= 0.0) return IncrementParse.Error("Values must be greater than 0")
        values.add(v)
    }
    if (maxCount != null && values.size != maxCount) {
        return IncrementParse.Error("This control needs exactly $maxCount values")
    }
    return IncrementParse.Ok(canonical = tokens.joinToString(","), values = values)
}

/** Render a value list to the canonical comma string (whole numbers lose the ".0"; zeros trimmed). */
fun formatIncrementList(values: List<Double>): String =
    values.joinToString(",") { v ->
        if (v == v.toLong().toDouble()) v.toLong().toString()
        else v.toBigDecimal().stripTrailingZeros().toPlainString()
    }
