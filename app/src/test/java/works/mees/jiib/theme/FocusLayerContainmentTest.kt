package works.mees.jiib.theme

import org.junit.Test
import java.io.File

/**
 * Focus Content Law — enforcement mechanism 2 (containment, Task 29). The Layer-1 Focus-scaffold
 * internals (`FocusZones`, `DigestColumn`, `DigestLine`) are archetype-INTERNAL: screens under
 * `ui/` compose Layer-2 archetypes, never reach under them. Any Layer-1 call from a `ui/` file is a
 * failure. `designsystem/` (where they live) is exempt by walking only `ui/`.
 *
 * `FocusGlyph` is deliberately NOT in this list: it is a slot-legal LAW-3 proportional-glyph sizing
 * component (like the FocusText/FocusHeroText/FocusHeroValueText LAW-2 classes) — sanctioned for
 * use inside archetype slot content (BedMeshScreen's bed-mesh empty-state is the precedent).
 *
 * Word-boundary regex `(^|[^.\w])NAME\s*\(`: member access (`.`) and identifier suffixes are
 * excluded, so `DigestRow.Line(` (a VALUE constructor) does NOT match `DigestLine`.
 */
class FocusLayerContainmentTest {
    private val layer1 = listOf("FocusZones", "DigestColumn", "DigestLine")

    @Test fun screensDoNotReachUnderTheArchetypes() {
        val base = mainSrcDir()
        val offenders = mutableListOf<String>()
        File(base, "ui").walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            val stripped = stripKotlin(f.readText())
            for (name in layer1) {
                for (m in Regex("""(^|[^.\w])$name\s*\(""").findAll(stripped)) {
                    val line = stripped.substring(0, m.range.first).count { it == '\n' } + 1
                    offenders += "${f.relativeTo(base).path.replace('\\', '/')}:$line uses Layer-1 $name( — compose a Layer-2 archetype instead"
                }
            }
        }
        assert(offenders.isEmpty()) {
            "Layer 1 is archetype-internal; screens compose Layer-2 archetypes (docs/ui_design/COMPONENTS.md).\n" +
                offenders.joinToString("\n")
        }
    }
}
