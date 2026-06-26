package works.mees.jiib.ui.spool

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.MoonrakerJson
import works.mees.jiib.spool.SpoolmanClient
import works.mees.jiib.spool.SpoolmanStatus

@OptIn(ExperimentalCoroutinesApi::class)
class SpoolHolderChemistryTest {

    private val noActiveSpool = MutableStateFlow<SpoolmanStatus?>(null)

    private fun proxyEnvelope(rowsJson: String): JsonElement =
        MoonrakerJson.parseToJsonElement(
            """{"response":$rowsJson,"error":null,"response_headers":{"X-Total-Count":"99"}}""",
        )

    // Pure-helper coverage (no holder needed).
    @Test fun `helper keeps only families present in inventory, in declared order`() {
        val fams = availableMaterialFamilies(listOf("PETG", "PLA+", "ASA"))
        assertEquals(listOf("PLA", "PETG", "ABS/ASA"), fams)
    }

    @Test fun `helper ignores blank materials and dedups families`() {
        val fams = availableMaterialFamilies(listOf("PLA", "PLA Matte", "  ", ""))
        assertEquals(listOf("PLA"), fams)
    }

    @Test fun `helper returns empty for empty inventory`() {
        assertTrue(availableMaterialFamilies(emptyList()).isEmpty())
    }

    @Test fun `helper counts a hybrid material in every matching family`() {
        // PC-ABS contains both "PC" and "ABS" → both families available, in declared order.
        assertEquals(listOf("ABS/ASA", "PC"), availableMaterialFamilies(listOf("PC-ABS")))
    }

    // Spools own PLA+ and ASA only → families = {PLA, ABS/ASA}; TPU/PETG/PC/Nylon excluded.
    private val materialClient: SpoolmanClient = object : SpoolmanClient {
        override suspend fun listSpools(query: String?): JsonElement = proxyEnvelope(
            """[
                {"id":1,"filament":{"id":1,"name":"PolyTerra","material":"PLA+","vendor":{"id":1,"name":"Polymaker"}}},
                {"id":2,"filament":{"id":2,"name":"ASA Pro","material":"ASA","vendor":{"id":2,"name":"Sunlu"}}},
                {"id":3,"filament":{"id":3,"name":"No Material"}}
            ]""",
        )
    }

    private fun holder() = SpoolHolder(
        scope = TestScope(UnconfinedTestDispatcher()),
        client = materialClient,
        activeSpool = noActiveSpool,
    )

    @Test fun `loadChips derives available families from physical spools only`() =
        runTest(UnconfinedTestDispatcher()) {
            val h = holder()
            h.load()
            val fams = h.state.value.availableMaterialFamilies
            assertTrue("PLA family (PLA+) present", fams.contains("PLA"))
            assertTrue("ABS/ASA family (ASA) present", fams.contains("ABS/ASA"))
            assertFalse("TPU not owned → excluded", fams.contains("TPU"))
            assertFalse("PETG not owned → excluded", fams.contains("PETG"))
        }
}
