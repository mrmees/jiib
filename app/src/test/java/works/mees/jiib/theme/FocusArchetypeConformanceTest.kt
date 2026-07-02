package works.mees.jiib.theme

import org.junit.Test

/**
 * Focus Content Law — enforcement mechanism 1 (2026-07, Task 29). A Focus body (the trailing lambda
 * passed to `FocusFrame`) may compose ONLY the catalog archetypes (the [registry] allowlist). Any
 * hand-rolled layout/content primitive (`Column`/`Row`/`Box`/`Text`/`Icon`/`.padding(` …), or any
 * unknown composable, at Focus-body level is a build failure. Local helper composables called from a
 * Focus body are resolved via the whole-main index and scanned recursively (depth-cap 4).
 *
 * Slot interiors (Stage/Form bodies, watermarks, DigestRow.Custom) are the archetype's contract and
 * are NOT scanned here — they are governed by LAW-2 [FocusTextConformanceTest] + component-class law.
 *
 * Control-flow (`when`/`if`/`else`) stays visible (hand-rolled layout inside a branch is caught);
 * value constructors / component classes / framework factories are permitted. See FocusScan.kt
 * SEMANTICS NOTE for the (owner-visible) deviation from the task-29 brief's naive recursion algorithm.
 *
 * F2 guard: a `FocusFrame(…, content = body)` call — passing the body as a named arg instead of a
 * trailing lambda — is an OFFENDER outright (it would hide the body from this scanner).
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
            for (call in findCalls(stripped, focusFrameCall)) {
                // F2: the Focus body must be a trailing lambda, never a `content =` named arg.
                if ("content" in topLevelArgNames(call.argSpan)) {
                    if (original.getOrNull(call.line - 1)?.contains("focus-archetype-exempt:") != true) {
                        offenders += "$rel:${call.line} FocusFrame(content = …) — pass the Focus body as a trailing lambda"
                    }
                    continue
                }
                val span = lambdaBodySpan(stripped, call.endOffset) ?: continue
                val bodyLine = stripped.substring(0, span.first).count { it == '\n' } + 1
                offenders += scanFocusBody(
                    stripped.substring(span.first, span.second), registry, rel, bodyLine,
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
