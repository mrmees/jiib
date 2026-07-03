package works.mees.jiib.theme

import org.junit.Test

/**
 * Focus Content Law — enforcement mechanism 1 (2026-07, Task 29). A Focus body (the trailing lambda
 * passed to `FocusFrame`) may compose ONLY the catalog archetypes (the [registry] allowlist). Any
 * hand-rolled layout/content primitive (`Column`/`Row`/`Box`/`Text`/`Icon`/`.padding(` …) that is
 * VISIBLE at the Focus-body level is a build failure.
 *
 * CONTRACT (what is and isn't scanned — see FocusScan.kt SEMANTICS NOTE + task-29-report.md):
 *  • ONLY the visible Focus-body level is scanned. This is a lexical, single-file scan — it does NOT
 *    follow calls. Local/helper composable BODIES are NOT resolved or scanned; only the call at the
 *    body level is seen (a lowercase helper call is permitted, an uppercase FORBIDDEN call is not).
 *  • F3 boundary: registry-archetype call spans — the arg list AND the trailing/`= { }` slot lambda —
 *    are MASKED (slots are the archetype's contract, governed by LAW-2 [FocusTextConformanceTest] +
 *    component-class law). Every OTHER lambda (`remember { }`, `LaunchedEffect { }`, control-flow) is
 *    scanned, so a hand-rolled `Column{}` at body level, inside a `when`-branch, or inside a generic
 *    lambda is caught. Value constructors / component classes / framework factories are permitted.
 *  • Fully-qualified (`foo.layout.Column(`) and per-file import-aliased (`import …Column as C`) forms
 *    of FORBIDDEN primitives are also caught.
 *
 * F2 guard: a `FocusFrame(…, content = body)` call — passing the body as a named arg instead of a
 * trailing lambda — is an OFFENDER outright (it would hide the body from this scanner). Likewise a
 * `FocusFrame` with NEITHER a trailing lambda NOR a `content =` arg is an offender (no scannable body).
 *
 * KNOWN LIMITATION: because the scan does not follow calls, a local wrapper that forwards a body
 * lambda — `fun frame(body: @Composable () -> Unit) { FocusFrame(...) { body() } }` then
 * `frame { Column {} }` — would EVADE this scanner (the `Column` is scanned inside `frame`'s call,
 * which is NOT a FocusFrame trailing lambda). Do NOT write such wrappers; inline the FocusFrame at
 * each site so its body is directly scannable (ThemeScreen precedent, 2026-07).
 *
 * Suppression: `// focus-archetype-exempt:` on the offending line (reserved for future owner rulings;
 * ZERO uses at ship).
 */
class FocusArchetypeConformanceTest {
    private val registry = setOf(
        "FocusExplainer", "FocusPlaceholder", "FocusDigest", "AdjusterPanel",
        "FocusInfoCard", "FocusMedia", "FocusStage", "FocusForm",
        "HardLockStatusCard", "UnknownStatusCard",
    )

    private val focusFrameCall = Regex("""(^|[^.\w])FocusFrame\s*\(""")

    @Test fun focusBodiesComposeOnlyArchetypes() {
        val base = mainSrcDir()
        val offenders = mutableListOf<String>()
        base.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val rel = f.relativeTo(base).path.replace('\\', '/')
            if (!rel.startsWith("ui/")) return@forEach
            val raw = f.readText()
            val original = raw.lines()
            val stripped = stripKotlin(raw)
            val forbidden = focusForbiddenFor(raw)
            fun exempt(line: Int) = original.getOrNull(line - 1)?.contains("focus-archetype-exempt:") == true
            for (call in findCalls(stripped, focusFrameCall)) {
                // F2: the Focus body must be a trailing lambda, never a `content =` named arg.
                if ("content" in topLevelArgNames(call.argSpan)) {
                    if (!exempt(call.line)) {
                        offenders += "$rel:${call.line} FocusFrame(content = …) — pass the Focus body as a trailing lambda"
                    }
                    continue
                }
                val span = lambdaBodySpan(stripped, call.endOffset)
                if (span == null) {
                    // F2b: neither a trailing lambda nor a `content =` arg — no scannable body.
                    if (!exempt(call.line)) {
                        offenders += "$rel:${call.line} FocusFrame without a trailing-lambda body — Focus body must be a scannable trailing lambda"
                    }
                    continue
                }
                val bodyLine = stripped.substring(0, span.first).count { it == '\n' } + 1
                offenders += scanFocusBody(
                    stripped.substring(span.first, span.second), registry, rel, bodyLine, forbidden,
                ).filterNot { off ->
                    val line = off.substringAfter(':').substringBefore(' ').toIntOrNull()
                    line != null && original.getOrNull(line - 1)?.contains("focus-archetype-exempt:") == true
                }
            }
        }
        assert(offenders.isEmpty()) {
            "Focus bodies must compose ONLY catalog archetypes (docs/ui_design/COMPONENTS.md §Focus archetypes).\n" +
                "New need → add archetype #N in designsystem/focus/ + doc entry (~30 min). NEVER inline layout.\n" +
                offenders.joinToString("\n")
        }
    }
}
