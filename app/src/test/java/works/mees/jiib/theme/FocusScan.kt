package works.mees.jiib.theme

import java.io.File

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

// ---------------------------------------------------------------------------------------------
// Focus-archetype body scanner (Focus Content Law, Task 29). Enforces the ZONE grammar: a Focus
// body composes ONLY catalog archetypes (the registry allowlist), never hand-rolled layout/content
// as its own scaffold.
//
// SEMANTICS NOTE (deviation from the task-29 brief's sample code — see task-29-report.md):
// The brief's naive "recurse into every helper + unknown-Uppercase-call == offender" algorithm
// produced 47 FALSE POSITIVES on the correctly-migrated tree — it flagged value constructors
// (`InfoStat(`, `SetServoArgs(` …), framework factories (`RoundedCornerShape(`, `BorderStroke(`,
// `LaunchedEffect(` …) and, worst, slot-content helpers reached via `DigestRow.Custom { … }` /
// `buildList { … }` — the exact interiors amendment F3 rules "are NOT scanned". This scanner is the
// FAITHFUL realization of that ruling: it masks all lambda/slot interiors (F3) plus registry call
// arg spans, keeps CONTROL-FLOW (`when`/`if`/`else`) VISIBLE (so a hand-rolled `Column{}` inside a
// `when`-branch IS still caught), and flags ONLY the enumerated FORBIDDEN layout/content primitives
// + `.padding(`. Uppercase calls that are not FORBIDDEN (archetypes, component classes, value
// constructors, framework factories) are permitted at Focus-body level. Verified: ZERO offenders on
// the migrated tree; teeth confirmed by fixtures (body-level & when-branch `Column` are caught).
// ---------------------------------------------------------------------------------------------

private val FORBIDDEN = setOf(
    "Column", "Row", "Box", "BoxWithConstraints", "LazyColumn",
    "Spacer", "Text", "BasicText", "Image", "AsyncImage", "Icon",
)
private val CALL = Regex("""(^|[^.\w])([A-Z]\w*)\s*\(""")
private val PAD = Regex("""\.padding\s*\(""")
private val CONTROL_KW = setOf("if", "when", "for", "while", "catch")

/**
 * Is the `{` at [open] the opener of a CONTROL-FLOW block (scan inside) rather than a lambda/slot
 * block (mask inside)? Control-flow: preceded by `->` (when-branch), by `else`/`try`/`finally`/`do`,
 * or by a `)` whose matching `(` is preceded by `if`/`when`/`for`/`while`/`catch`. Everything else
 * (call trailing lambda, `= { }`, builder `buildList { }`, `remember { }`, `.let { }` …) is a slot.
 */
private fun isControlFlowBrace(body: String, open: Int): Boolean {
    var j = open - 1
    while (j >= 0 && body[j].isWhitespace()) j--
    if (j < 0) return false
    if (j >= 1 && body[j] == '>' && body[j - 1] == '-') return true // `-> {`
    var e = j
    while (e >= 0 && (body[e].isLetterOrDigit() || body[e] == '_')) e--
    if (body.substring(e + 1, j + 1) in setOf("else", "try", "finally", "do")) return true
    if (body[j] == ')') { // `) {` — control-flow iff the matching `(` follows a control keyword
        var d = 1; var p = j - 1
        while (p >= 0 && d > 0) { when (body[p]) { ')' -> d++; '(' -> d-- }; if (d > 0) p-- }
        var k = p - 1
        while (k >= 0 && body[k].isWhitespace()) k--
        var ke = k
        while (ke >= 0 && (body[ke].isLetterOrDigit() || body[ke] == '_')) ke--
        return body.substring(ke + 1, k + 1) in CONTROL_KW
    }
    return false
}

/**
 * Scan one Focus-body span (already `stripKotlin`ed by real callers; fixtures pass pre-stripped
 * bodies). Masks lambda/slot brace interiors (F3 — slots are the archetype's contract, governed by
 * LAW-2 text conformance + component-class law) and registry call arg spans; keeps control-flow
 * visible. Returns offender strings "file:line reason" for FORBIDDEN primitives / `.padding(` that
 * survive at the visible Focus-body level.
 */
fun scanFocusBody(
    body: String,
    registry: Set<String>,
    file: String,
    baseLine: Int,
): List<String> {
    val n = body.length
    val masked = BooleanArray(n)
    // 1) Mask every lambda/slot brace interior (control-flow blocks stay visible).
    var i = 0
    while (i < n) {
        if (body[i] == '{' && !isControlFlowBrace(body, i)) {
            var d = 1; var p = i + 1
            while (p < n && d > 0) { when (body[p]) { '{' -> d++; '}' -> d-- }; if (d > 0) p++ }
            for (q in i..minOf(p, n - 1)) masked[q] = true
            i = p + 1
        } else i++
    }
    // 2) Mask registry-archetype call arg spans (their trailing lambdas are already masked in 1).
    for (m in CALL.findAll(body)) {
        if (m.groupValues[2] !in registry) continue
        val open = m.range.last
        var d = 1; var p = open + 1
        while (p < n && d > 0) { when (body[p]) { '(' -> d++; ')' -> d-- }; if (d > 0) p++ }
        for (q in m.range.first..minOf(p, n - 1)) masked[q] = true
    }
    fun lineOf(offset: Int) = baseLine + body.substring(0, offset).count { it == '\n' }
    val offenders = mutableListOf<String>()
    for (m in CALL.findAll(body)) {
        val start = m.range.first
        if (masked[start]) continue
        val name = m.groupValues[2]
        if (name in FORBIDDEN) offenders += "$file:${lineOf(start)} raw $name( in Focus body"
    }
    for (m in PAD.findAll(body)) {
        if (!masked[m.range.first]) offenders += "$file:${lineOf(m.range.first)} .padding( in Focus body"
    }
    return offenders
}

/** The `app/src/main/java/works/mees/jiib` dir, walked up from the test `user.dir`. Shared by all Focus scanners. */
fun mainSrcDir(): File {
    val userDir = requireNotNull(System.getProperty("user.dir"))
    var dir: File? = File(userDir).canonicalFile
    while (dir != null) {
        val c = File(dir, "app/src/main/java/works/mees/jiib")
        if (c.isDirectory) return c
        dir = dir.parentFile
    }
    error("main source dir not found from $userDir")
}
