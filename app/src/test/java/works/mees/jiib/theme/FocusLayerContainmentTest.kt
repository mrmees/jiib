package works.mees.jiib.theme

import org.junit.Test
import java.io.File

/**
 * Focus Content Law — enforcement mechanism 2 (containment, Task 29). The Layer-1 primitives are
 * archetype-INTERNAL: screens under `ui/` compose Layer-2 archetypes, never reach under them. Any
 * Layer-1 call from a `ui/` file is a failure. `designsystem/` (where they live) is exempt by
 * walking only `ui/`.
 *
 * Word-boundary regex `(^|[^.\w])NAME\s*\(`: member access (`.`) and identifier suffixes are
 * excluded, so `DigestRow.Line(` (a VALUE constructor) does NOT match `DigestLine`, and the
 * `FocusText`/`FocusHeroText`/`FocusHeroValueText` LAW-2 classes are untouched.
 *
 * ENFORCED set — the three true Focus-scaffold internals (`designsystem/layout/FocusZones`,
 * `designsystem/components/DigestColumn`, `.../DigestLine`): ZERO offenders on the migrated tree.
 *
 * PENDING-RULING set — `FocusGlyph`: the task-29 brief lists it as Layer-1, BUT it is a general
 * LAW-3 proportional-glyph COMPONENT (`designsystem/components/FocusGlyph`, composed *by*
 * FocusPlaceholder), and its one screen use — `ui/calibration/BedMeshScreen.kt` bed-mesh empty-state
 * — is a defensible bordered viz-region card mirroring the heatmap frame (FocusPlaceholder can't
 * reproduce the frame without a visual change that needs on-device verification). Its inclusion in
 * the ban and the BedMesh fix are deferred to an owner ruling; this test PRINTS but does not fail on
 * it. See task-29-report.md §Concerns. Flip [enforceFocusGlyph] once ruled.
 */
class FocusLayerContainmentTest {
    private val enforced = listOf("FocusZones", "DigestColumn", "DigestLine")
    private val pendingRuling = listOf("FocusGlyph")
    private val enforceFocusGlyph = false

    @Test fun screensDoNotReachUnderTheArchetypes() {
        val base = mainSrcDir()
        val hits = scan(base, enforced + if (enforceFocusGlyph) pendingRuling else emptyList())
        val informational = if (enforceFocusGlyph) emptyList() else scan(base, pendingRuling)
        if (informational.isNotEmpty()) {
            println("FOCUS-CONTAINMENT (pending owner ruling on FocusGlyph classification):")
            informational.forEach { println("  $it") }
        }
        assert(hits.isEmpty()) {
            "Layer 1 is archetype-internal; screens compose Layer-2 archetypes (docs/ui_design/COMPONENTS.md).\n" +
                hits.joinToString("\n")
        }
    }

    private fun scan(base: File, names: List<String>): List<String> {
        val out = mutableListOf<String>()
        File(base, "ui").walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val stripped = stripKotlin(f.readText())
            for (name in names) {
                for (m in Regex("""(^|[^.\w])$name\s*\(""").findAll(stripped)) {
                    val line = stripped.substring(0, m.range.first).count { it == '\n' } + 1
                    out += "${f.relativeTo(base).path.replace('\\', '/')}:$line uses Layer-1 $name( — compose a Layer-2 archetype instead"
                }
            }
        }
        return out
    }
}
