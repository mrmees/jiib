package works.mees.jiib.theme

/** Shared source scanner for Focus conformance tests. Length-preserving strip + call finder + arg parser. */

data class CallSite(val startOffset: Int, val endOffset: Int, val argSpan: String, val line: Int)

/**
 * Blank out `//` line comments, `/* */` block comments, the CONTENTS of `"..."` / `"""..."""`
 * string literals, and char literals (`'x'`, `'\n'`, `'\''`), replacing each consumed char with a
 * space (newlines preserved so line numbers stay accurate). Length-preserving.
 */
fun stripKotlin(src: String): String {
    val out = StringBuilder(src.length)
    var i = 0
    val n = src.length
    while (i < n) {
        val c = src[i]
        val c2 = if (i + 1 < n) src[i + 1] else ' '
        when {
            c == '/' && c2 == '/' -> { // line comment → blank to EOL
                while (i < n && src[i] != '\n') { out.append(' '); i++ }
            }
            c == '/' && c2 == '*' -> { // block comment → blank, keep newlines
                out.append("  "); i += 2
                while (i < n && !(src[i] == '*' && i + 1 < n && src[i + 1] == '/')) {
                    out.append(if (src[i] == '\n') '\n' else ' '); i++
                }
                if (i < n) { out.append("  "); i += 2 }
            }
            c == '"' && c2 == '"' && i + 2 < n && src[i + 2] == '"' -> { // triple-quoted
                out.append("   "); i += 3
                while (i < n && !(src[i] == '"' && i + 1 < n && src[i + 1] == '"' && i + 2 < n && src[i + 2] == '"')) {
                    out.append(if (src[i] == '\n') '\n' else ' '); i++
                }
                if (i < n) { out.append("   "); i += 3 }
            }
            c == '"' -> { // normal string, honor \" escapes
                out.append(' '); i++
                while (i < n && src[i] != '"') {
                    if (src[i] == '\\' && i + 1 < n) { out.append("  "); i += 2 }
                    else { out.append(if (src[i] == '\n') '\n' else ' '); i++ }
                }
                if (i < n) { out.append(' '); i++ }
            }
            // Char literal: 'x' or '\n' or '\'' — blank it, length-preserving.
            c == '\'' -> {
                out.append(' '); i++
                if (i < n && src[i] == '\\' && i + 1 < n) { out.append("  "); i += 2 }
                else if (i < n) { out.append(' '); i++ }
                if (i < n && src[i] == '\'') { out.append(' '); i++ }
            }
            else -> { out.append(c); i++ }
        }
    }
    return out.toString()
}

/**
 * Find all calls matching [callRegex] in the already-stripped source, returning a [CallSite] per
 * match. The regex's last character must be `(`; the paren-walk then finds the matching `)`.
 */
fun findCalls(stripped: String, callRegex: Regex): List<CallSite> {
    val calls = mutableListOf<CallSite>()
    for (match in callRegex.findAll(stripped)) {
        val openParen = match.range.last
        val line = stripped.substring(0, openParen).count { it == '\n' } + 1
        var depth = 1
        var pos = openParen + 1
        while (pos < stripped.length && depth > 0) {
            when (stripped[pos]) {
                '(' -> depth++
                ')' -> depth--
            }
            if (depth > 0) pos++
        }
        // pos is now at the closing ')' (or == length if source is malformed)
        calls += CallSite(
            startOffset = openParen,
            endOffset = pos,
            argSpan = stripped.substring(openParen + 1, pos),
            line = line,
        )
    }
    return calls
}

/**
 * Names of the call's TOP-LEVEL named arguments (depth-0 split on commas; `name =` before any
 * nesting). This is the fix for the substring false-pass hole: `maxLines` buried inside a nested
 * lambda `{ Text("x", maxLines = 1) }` is at depth > 0 and therefore NOT returned.
 */
fun topLevelArgNames(argSpan: String): Set<String> {
    val names = mutableSetOf<String>()
    var depth = 0
    var argStart = 0

    fun harvest(arg: String) {
        val trimmed = arg.trim()
        // Find the first top-level `=` that is NOT `==`, `!=`, `<=`, `>=`.
        val eq = run {
            var d = 0
            var idx = -1
            for ((i, ch) in trimmed.withIndex()) {
                when (ch) {
                    '(', '{', '[' -> d++
                    ')', '}', ']' -> d--
                    '=' -> if (d == 0 && idx == -1 &&
                        (i + 1 >= trimmed.length || trimmed[i + 1] != '=') &&
                        (i == 0 || trimmed[i - 1] !in "=!<>")
                    ) idx = i
                }
            }
            idx
        }
        if (eq > 0) {
            val name = trimmed.substring(0, eq).trim()
            if (name.isNotEmpty() && name.all { it.isLetterOrDigit() || it == '_' }) names += name
        }
    }

    for ((i, ch) in argSpan.withIndex()) {
        when (ch) {
            '(', '{', '[' -> depth++
            ')', '}', ']' -> depth--
            ',' -> if (depth == 0) { harvest(argSpan.substring(argStart, i)); argStart = i + 1 }
        }
    }
    if (argStart < argSpan.length) harvest(argSpan.substring(argStart))
    return names
}

/**
 * Span (start inclusive, end exclusive) of the trailing-lambda BODY after a call's closing paren,
 * or null if no `{` follows (possibly with whitespace between). The second param is [CallSite.endOffset]
 * (the position of the closing `)` in the stripped text).
 */
fun lambdaBodySpan(stripped: String, callEndOffset: Int): Pair<Int, Int>? {
    var pos = callEndOffset + 1
    while (pos < stripped.length && stripped[pos].isWhitespace()) pos++
    if (pos >= stripped.length || stripped[pos] != '{') return null
    val bodyStart = pos + 1
    var depth = 1
    pos++
    while (pos < stripped.length && depth > 0) {
        when (stripped[pos]) {
            '{' -> depth++
            '}' -> depth--
        }
        if (depth > 0) pos++
    }
    return bodyStart to pos
}
