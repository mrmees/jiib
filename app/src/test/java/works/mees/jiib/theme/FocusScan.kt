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
// `buildList { … }` — the exact interiors amendment F3 rules "are NOT scanned".
//
// MASKING (F3 boundary): this scanner masks ONLY registry-archetype call spans — the archetype's
// arg list AND its trailing/`= { }` slot lambda. Those slot interiors are the archetype's contract
// (governed by LAW-2 text conformance + component-class law), so they are NOT scanned. EVERY OTHER
// lambda is scanned like the rest of the body: `remember { }`, `LaunchedEffect { }`, `key { }`,
// lowercase-call lambdas, and unknown-Uppercase-call lambdas are all visible. Control-flow
// (`when`/`if`/`else`) is likewise visible. So a hand-rolled `Column{}` at body level, inside a
// `when`-branch, OR inside a non-registry lambda is caught. The scanner flags ONLY the enumerated
// FORBIDDEN layout/content primitives + `.padding(` — including fully-qualified / member-access
// forms (`foo.layout.Column(`) whose LAST dotted segment is FORBIDDEN, and per-file import aliases
// (`import …Column as Foo` → `Foo` is forbidden in that file). Uppercase calls that are not
// FORBIDDEN (archetypes, component classes, value constructors, framework factories) are permitted.
// Verified: ZERO offenders on the migrated tree; teeth confirmed by fixtures.
// ---------------------------------------------------------------------------------------------

private val FORBIDDEN = setOf(
    "Column", "Row", "Box", "BoxWithConstraints", "LazyColumn",
    "Spacer", "Text", "BasicText", "Image", "AsyncImage", "Icon",
)
private val CALL = Regex("""(^|[^.\w])([A-Z]\w*)\s*\(""")
// Parenless block form — `Column { … }` / `Box{}` — bypasses CALL (which requires `(`). Detect the
// OPENER itself: Uppercase name directly before `{`. Control-flow keywords (`when`/`if`…) are
// lowercase and never match; registry archetypes with parenless trailing lambdas are skipped by name.
private val BLOCK = Regex("""(^|[^.\w])([A-Z]\w*)\s*\{""")
// Fully-qualified / member-access forms — `foo.bar.Column(` and `Something.Column {`. The `[^.\w]`
// guard on CALL/BLOCK deliberately exempts a leading `.` (so value constructors like `DigestRow.Line(`
// pass). These regexes re-catch the exempted forms but ONLY when the LAST dotted segment is FORBIDDEN,
// so `DigestRow.Line(` (Line ∉ FORBIDDEN) still passes while `androidx….layout.Column(` is flagged.
private val DOTTED_CALL = Regex("""\.([A-Z]\w*)\s*\(""")
private val DOTTED_BLOCK = Regex("""\.([A-Z]\w*)\s*\{""")
private val PAD = Regex("""\.padding\s*\(""")
private val IMPORT_ALIAS = Regex("""(?m)^\s*import\s+([\w.]+)\s+as\s+(\w+)""")

/**
 * The per-file FORBIDDEN set: the base [FORBIDDEN] primitives PLUS any `import x.y.Z as W` alias in
 * [src] whose target `Z` (last dotted segment) is a FORBIDDEN primitive — `W` is then forbidden in
 * that file too (alias-evasion defense). See Codex #1.
 */
fun focusForbiddenFor(src: String): Set<String> {
    val aliases = IMPORT_ALIAS.findAll(src)
        .filter { it.groupValues[1].substringAfterLast('.') in FORBIDDEN }
        .map { it.groupValues[2] }
        .toSet()
    return if (aliases.isEmpty()) FORBIDDEN else FORBIDDEN + aliases
}

/**
 * Scan one Focus-body span (already `stripKotlin`ed by real callers; fixtures pass pre-stripped
 * bodies). Masks ONLY registry-archetype call spans — the arg list AND the trailing/`= { }` slot
 * lambda (F3: slots are the archetype's contract, governed by LAW-2 text conformance +
 * component-class law). Every OTHER lambda (generic `remember { }` / `LaunchedEffect { }` /
 * lowercase- or unknown-Uppercase-call lambdas) is SCANNED, as is control-flow. Returns offender
 * strings "file:line reason" for [forbidden] primitives / `.padding(` — bare, fully-qualified, and
 * parenless — that survive at the visible level. [forbidden] defaults to the base [FORBIDDEN] set;
 * real callers pass [focusForbiddenFor] to fold in per-file import aliases.
 */
fun scanFocusBody(
    body: String,
    registry: Set<String>,
    file: String,
    baseLine: Int,
    forbidden: Set<String> = FORBIDDEN,
): List<String> {
    val n = body.length
    val masked = BooleanArray(n)
    fun matchParen(open: Int): Int {
        var d = 1; var p = open + 1
        while (p < n && d > 0) { when (body[p]) { '(' -> d++; ')' -> d-- }; if (d > 0) p++ }
        return p
    }
    fun matchBrace(open: Int): Int {
        var d = 1; var p = open + 1
        while (p < n && d > 0) { when (body[p]) { '{' -> d++; '}' -> d-- }; if (d > 0) p++ }
        return p
    }
    fun mask(from: Int, to: Int) { for (q in from..minOf(to, n - 1)) masked[q] = true }
    // Mask registry-archetype call spans ONLY: `Archetype(args) { slot }` — args AND trailing lambda.
    for (m in CALL.findAll(body)) {
        if (m.groupValues[2] !in registry) continue
        val close = matchParen(m.range.last)
        var end = close
        var p = close + 1
        while (p < n && body[p].isWhitespace()) p++
        if (p < n && body[p] == '{') end = matchBrace(p)
        mask(m.range.first, end)
    }
    // Mask registry parenless blocks: `Archetype { slot }`.
    for (m in BLOCK.findAll(body)) {
        if (m.groupValues[2] !in registry) continue
        mask(m.range.first, matchBrace(m.range.last))
    }
    fun lineOf(offset: Int) = baseLine + body.substring(0, offset).count { it == '\n' }
    val offenders = mutableListOf<String>()
    // Bare uppercase call form: `Column(`.
    for (m in CALL.findAll(body)) {
        val idStart = m.range.first + m.groupValues[1].length
        if (masked[idStart]) continue
        val name = m.groupValues[2]
        if (name in forbidden) offenders += "$file:${lineOf(idStart)} raw $name( in Focus body"
    }
    // Fully-qualified / member-access call form: `foo.layout.Column(` (last segment FORBIDDEN).
    for (m in DOTTED_CALL.findAll(body)) {
        val idStart = m.range.first + 1
        if (masked[idStart]) continue
        val name = m.groupValues[1]
        if (name in forbidden) offenders += "$file:${lineOf(idStart)} raw $name( (fully-qualified) in Focus body"
    }
    // Parenless block form: the name char (not the preceding boundary char) decides visibility —
    // a body-level `Column { }` or a when-branch `Box{}` is an offender.
    for (m in BLOCK.findAll(body)) {
        val idStart = m.range.first + m.groupValues[1].length
        if (masked[idStart]) continue
        val name = m.groupValues[2]
        if (name in forbidden && name !in registry) offenders += "$file:${lineOf(idStart)} raw $name { in Focus body"
    }
    // Fully-qualified parenless block: `Something.Column {`.
    for (m in DOTTED_BLOCK.findAll(body)) {
        val idStart = m.range.first + 1
        if (masked[idStart]) continue
        val name = m.groupValues[1]
        if (name in forbidden && name !in registry) offenders += "$file:${lineOf(idStart)} raw $name { (fully-qualified) in Focus body"
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
