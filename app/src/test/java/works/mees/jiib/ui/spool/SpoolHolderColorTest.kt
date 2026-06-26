package works.mees.jiib.ui.spool

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.MoonrakerJson
import works.mees.jiib.spool.SpoolmanClient
import works.mees.jiib.spool.SpoolmanStatus

/**
 * Holder-level tests for the multi-select client-side color-family filter
 * ([SpoolHolder.applyColorSwatch]). See docs/superpowers/specs/2026-06-21-spool-screen-cleanups-design.md.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolHolderColorTest {

    private val noActiveSpool = MutableStateFlow<SpoolmanStatus?>(null)

    private fun proxyEnvelope(rowsJson: String): JsonElement =
        MoonrakerJson.parseToJsonElement(
            """{"response":$rowsJson,"error":null,"response_headers":{"X-Total-Count":"99"}}""",
        )

    /** A library: olive(Green id1), pure red(Red id2), multicolor red+green(id3), sky blue(Blue id4). */
    private val colorClient: SpoolmanClient = object : SpoolmanClient {
        override suspend fun listFilaments(query: String?): JsonElement = proxyEnvelope(
            """[
                {"id":1,"name":"Olive","color_hex":"64794b"},
                {"id":2,"name":"Red","color_hex":"ff0000"},
                {"id":3,"name":"Rainbow","multi_color_hexes":"ff0000,00c000"},
                {"id":4,"name":"Sky","color_hex":"5dc0f0"}
            ]""",
        )
    }

    private fun holder(client: SpoolmanClient) = SpoolHolder(
        scope = TestScope(UnconfinedTestDispatcher()),
        client = client,
        activeSpool = noActiveSpool,
    )

    @Test fun `tapping Green selects solid green AND a multicolor containing green`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00C000")
            assertEquals(listOf(1, 3), h.state.value.filters.colorFilamentIds)
            assertEquals(listOf("#00C000"), h.state.value.filters.colorSwatchHexes)
        }

    @Test fun `tapping a lowercase hex normalizes the stored swatch`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00c000")
            assertEquals(listOf("#00C000"), h.state.value.filters.colorSwatchHexes)
        }

    @Test fun `selecting Green then Red UNIONS both families' ids and keeps both swatches`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00C000")
            h.applyColorSwatch("#FF0000")
            // Green→{1,3}, Red→{2,3}; union (in filament order) = {1,2,3}.
            assertEquals(listOf(1, 2, 3), h.state.value.filters.colorFilamentIds)
            assertTrue(h.state.value.filters.colorSwatchHexes.containsAll(listOf("#00C000", "#FF0000")))
            assertEquals(2, h.state.value.filters.colorSwatchHexes.size)
        }

    @Test fun `re-tapping one of two colors removes only its contribution`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00C000")
            h.applyColorSwatch("#FF0000")
            h.applyColorSwatch("#FF0000") // toggle Red off
            assertEquals(listOf("#00C000"), h.state.value.filters.colorSwatchHexes)
            assertEquals(listOf(1, 3), h.state.value.filters.colorFilamentIds)
        }

    @Test fun `re-tapping the last color clears the whole color filter`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00C000")
            h.applyColorSwatch("#00C000")
            assertTrue(h.state.value.filters.colorSwatchHexes.isEmpty())
            assertNull(h.state.value.filters.colorFilamentIds)
        }

    @Test fun `a family with no matches yields an empty (unmatchable) id list`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#FFFF00") // Yellow — no filament classifies Yellow
            assertEquals(emptyList<Int>(), h.state.value.filters.colorFilamentIds)
            assertEquals(listOf("#FFFF00"), h.state.value.filters.colorSwatchHexes)
        }

    @Test fun `a failed fetch leaves the filter UNAPPLIED, not an empty list`() =
        runTest(UnconfinedTestDispatcher()) {
            val failing = object : SpoolmanClient {}
            val h = holder(failing)
            h.applyColorSwatch("#00C000")
            assertNull("fetch failure must not collapse the list", h.state.value.filters.colorFilamentIds)
            assertTrue(h.state.value.filters.colorSwatchHexes.isEmpty())
        }

    @Test fun `seedPrefilter maps an olive file color to the GREEN hint, not a hard filter`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.seedPrefilter(SpoolPrefilterSeed(filamentColors = listOf("#64794b")))
            assertEquals("#00C000", h.state.value.filters.colorSeedHex)
            assertTrue(h.state.value.filters.colorSwatchHexes.isEmpty())
            assertNull(h.state.value.filters.colorFilamentIds)
        }

    @Test fun `tapping the seeded swatch consumes the seed and applies a hard filter`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.seedPrefilter(SpoolPrefilterSeed(filamentColors = listOf("#64794b")))
            h.applyColorSwatch("#00C000")
            assertNull("seed must be consumed on the first hard tap", h.state.value.filters.colorSeedHex)
            assertEquals(listOf("#00C000"), h.state.value.filters.colorSwatchHexes)
            assertEquals(listOf(1, 3), h.state.value.filters.colorFilamentIds)
        }

    @Test fun `clearColor empties hexes, ids, and seed`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00C000")
            h.clearColor()
            assertTrue(h.state.value.filters.colorSwatchHexes.isEmpty())
            assertNull(h.state.value.filters.colorFilamentIds)
            assertNull(h.state.value.filters.colorSeedHex)
        }
}
