package works.mees.dinghy.ui.spool

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.spool.SpoolmanStatus

/**
 * Holder-level tests for the client-side color-family filter ([SpoolHolder.applyColorSwatch]).
 * Replaces the old Spoolman CIE76 similarity fetch. See
 * docs/superpowers/specs/2026-06-18-spool-color-family-filter-design.md.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolHolderColorTest {

    private val noActiveSpool = MutableStateFlow<SpoolmanStatus?>(null)

    private fun proxyEnvelope(rowsJson: String): JsonElement =
        MoonrakerJson.parseToJsonElement(
            """{"response":$rowsJson,"error":null,"response_headers":{"X-Total-Count":"99"}}""",
        )

    /** A library: olive(Green), pure red(Red), a multicolor with red+green, sky blue(Blue). */
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
            assertEquals("#00C000", h.state.value.filters.colorSwatchHex)
        }

    @Test fun `tapping Red selects the red and the multicolor`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#FF0000")
            assertEquals(listOf(2, 3), h.state.value.filters.colorFilamentIds)
        }

    @Test fun `a family with no matches yields an empty (unmatchable) id list`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#FFFF00")
            assertEquals(emptyList<Int>(), h.state.value.filters.colorFilamentIds)
        }

    @Test fun `re-tapping an active swatch clears the color filter`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.applyColorSwatch("#00C000")
            h.applyColorSwatch("#00C000")
            assertNull(h.state.value.filters.colorFilamentIds)
            assertNull(h.state.value.filters.colorSwatchHex)
        }

    @Test fun `a failed fetch leaves the filter UNAPPLIED, not an empty list`() =
        runTest(UnconfinedTestDispatcher()) {
            val failing = object : SpoolmanClient {}
            val h = holder(failing)
            h.applyColorSwatch("#00C000")
            assertNull("fetch failure must not collapse the list", h.state.value.filters.colorFilamentIds)
            assertNull(h.state.value.filters.colorSwatchHex)
        }

    @Test fun `seedPrefilter maps an olive file color to the GREEN hint swatch (not Gray)`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.seedPrefilter(SpoolPrefilterSeed(filamentColors = listOf("#64794b")))
            assertEquals("#00C000", h.state.value.filters.colorSwatchHex)
            assertNull(h.state.value.filters.colorFilamentIds)
        }

    @Test fun `tapping a seed-highlighted swatch APPLIES the filter (does not clear)`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder(colorClient)
            h.seedPrefilter(SpoolPrefilterSeed(filamentColors = listOf("#64794b")))
            h.applyColorSwatch("#00C000")
            assertEquals(listOf(1, 3), h.state.value.filters.colorFilamentIds)
        }
}
