package works.mees.dinghy.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.icons.IconRef
import works.mees.dinghy.ui.route.NavDest

/**
 * Host-side proof for the D-10 HIDE-not-grey Output drawer gate (19-07 Task 1). The decision is the PURE,
 * non-@Composable [visibleDrawerTiles] helper, so it is unit-testable with NO Compose harness.
 *
 * The Output tile DIVERGES from the Webcam/Spool shown-but-greyed pattern: when the connected printer
 * reports zero controllable outputs the tile must be FILTERED OUT entirely (D-10), never just greyed. When
 * ≥1 output is present the tile is present, navigates to [NavDest.Outputs] (D-11), and wears the owner-locked
 * `output` glyph sourced from [DinghyIcons.OutputSection] (D-07) — never a hand-typed string.
 */
class AppDrawerOutputsGateTest {

    @Test
    fun `output tile is hidden entirely when outputs absent (D-10)`() {
        val result = visibleDrawerTiles(DRAWER_TILES, outputsEnabled = false)
        // The Output tile must be ABSENT (filtered out — hide, not grey).
        assertNull(
            "Output tile must be filtered out entirely when no outputs are present (D-10 hide-not-grey)",
            result.firstOrNull { it.dest == NavDest.Outputs },
        )
        assertFalse(result.any { it.label == "Output" })
    }

    @Test
    fun `output tile is present and routes to Dest_Outputs when outputs present (D-11)`() {
        val result = visibleDrawerTiles(DRAWER_TILES, outputsEnabled = true)
        val output = result.firstOrNull { it.label == "Output" }
        assertTrue("Output tile must be present when outputs are present", output != null)
        assertEquals(NavDest.Outputs, output!!.dest)
    }

    @Test
    fun `output tile symbol is sourced from the OutputSection token (D-07)`() {
        val output = visibleDrawerTiles(DRAWER_TILES, outputsEnabled = true)
            .first { it.label == "Output" }
        val ligature = (DinghyIcons.OutputSection.primary as IconRef.Ligature).name
        assertEquals("output", ligature) // owner-locked D-07 glyph
        assertEquals(
            "Output tile symbol must be sourced from DinghyIcons.OutputSection, not a hand-typed string",
            ligature,
            output.symbol,
        )
    }

    @Test
    fun `only the Output tile is gated - no other tile is dropped by the filter`() {
        val enabled = visibleDrawerTiles(DRAWER_TILES, outputsEnabled = true)
        val disabled = visibleDrawerTiles(DRAWER_TILES, outputsEnabled = false)
        // Enabled = the full set; disabled drops EXACTLY one tile (the Output tile).
        assertEquals(DRAWER_TILES.size, enabled.size)
        assertEquals(DRAWER_TILES.size - 1, disabled.size)
        // Every non-Output tile survives BOTH passes unchanged.
        val nonOutput = DRAWER_TILES.filter { it.dest != NavDest.Outputs }
        assertEquals(nonOutput, disabled)
        nonOutput.forEach { tile ->
            assertTrue("Tile ${tile.label} must survive the outputs-disabled filter", tile in disabled)
        }
    }
}
