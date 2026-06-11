package works.mees.dinghy.ui.spool

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.spool.SpoolmanStatus

/**
 * Holder-level tests for vendor multi-select toggle through [SpoolHolder.toggleVendor].
 *
 * These tests exercise the ACTUAL toggle state machine end-to-end — not just the [SpoolFilters]
 * data class in isolation. The mock-vs-reality gap that hid the on-device bug was that
 * [SpoolPickerStateTest] only tested the data layer; [SpoolHolder.toggleVendor] was never
 * exercised through a real holder instance.
 *
 * ## Bug the tests are written to catch (pre-fix regression guard):
 * Old code: `SpoolFilters.vendor: String?` — a scalar. Toggling vendor A then vendor B overwrote A
 * with B; only one vendor could be selected at a time.
 * New code: `SpoolFilters.vendors: List<String>` — list. Both A and B accumulate. These tests
 * assert the multi-select (accumulation) contract and will FAIL if the scalar is reintroduced.
 *
 * ## No-op client
 * The fake client returns null for every call (the [SpoolmanClient] default), which means
 * [SpoolHolder.refresh] degrades gracefully to an empty spool list. These tests only care about
 * the FILTER STATE after toggling — they do not inspect the spool list contents.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolHolderVendorTest {

    // A minimal no-op SpoolmanClient (all methods return null per the interface defaults).
    private val noopClient: SpoolmanClient = object : SpoolmanClient {}

    private val noActiveSpool = MutableStateFlow<SpoolmanStatus?>(null)

    private fun holder() = SpoolHolder(
        scope = kotlinx.coroutines.test.TestScope(UnconfinedTestDispatcher()),
        client = noopClient,
        activeSpool = noActiveSpool,
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Single-toggle ON
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toggleVendor ON — vendor appears in filters dot vendors`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.toggleVendor("Polymaker")
        assertTrue(h.state.value.filters.vendors.contains("Polymaker"))
    }

    @Test
    fun `toggleVendor ON — vendors list has exactly one entry`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.toggleVendor("Polymaker")
        assertEquals(1, h.state.value.filters.vendors.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-select (the core regression guard)
    //
    // These are the tests that FAIL against the old scalar `vendor: String?` field.
    // Pre-fix, toggling a second vendor would set vendor="Sunlu" (scalar overwrite),
    // so vendors.size would be 1 and "Polymaker" would not be present.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toggleVendor MULTI — two vendors accumulate independently`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()

        h.toggleVendor("Polymaker")
        h.toggleVendor("Sunlu")

        val vendors = h.state.value.filters.vendors
        // Both must be present — pre-fix, only "Sunlu" was present (scalar overwrite).
        assertTrue("'Polymaker' must remain selected after 'Sunlu' is added", vendors.contains("Polymaker"))
        assertTrue("'Sunlu' must be selected", vendors.contains("Sunlu"))
        assertEquals("Both vendors must be in the list", 2, vendors.size)
    }

    @Test
    fun `toggleVendor MULTI — three vendors all accumulate`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()

        h.toggleVendor("Polymaker")
        h.toggleVendor("Sunlu")
        h.toggleVendor("Jayo")

        val vendors = h.state.value.filters.vendors
        assertTrue(vendors.containsAll(listOf("Polymaker", "Sunlu", "Jayo")))
        assertEquals(3, vendors.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Toggle OFF (deselect)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toggleVendor OFF — re-tapping same vendor removes it`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()

        h.toggleVendor("Polymaker")
        h.toggleVendor("Polymaker")  // toggle off

        assertTrue("vendors must be empty after toggle-off", h.state.value.filters.vendors.isEmpty())
    }

    @Test
    fun `toggleVendor OFF — deselects only the tapped vendor, leaves others`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()

        h.toggleVendor("Polymaker")
        h.toggleVendor("Sunlu")
        h.toggleVendor("Polymaker")  // deselect Polymaker only

        val vendors = h.state.value.filters.vendors
        assertFalse("'Polymaker' must be removed", vendors.contains("Polymaker"))
        assertTrue("'Sunlu' must remain", vendors.contains("Sunlu"))
        assertEquals("Only Sunlu remains", 1, vendors.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Picker stays open (no auto-close on toggle)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toggleVendor does NOT close the picker — fieldMode stays FilterPicker MFG`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.openFilterPicker(SpoolFilterCategory.MFG)

        h.toggleVendor("Polymaker")

        // Pre-fix: closeFilterPicker() was called inline → fieldMode became Spools.
        val mode = h.state.value.fieldMode
        assertTrue(
            "Field mode must remain FilterPicker(MFG) after toggle — picker must NOT auto-close",
            mode is FieldMode.FilterPicker && (mode as FieldMode.FilterPicker).category == SpoolFilterCategory.MFG,
        )
    }

    @Test
    fun `toggleMaterialFamily does NOT close the picker — fieldMode stays FilterPicker TYPE`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.openFilterPicker(SpoolFilterCategory.TYPE)

        h.toggleMaterialFamily("PLA")

        val mode = h.state.value.fieldMode
        assertTrue(
            "Field mode must remain FilterPicker(TYPE) after toggle — picker must NOT auto-close",
            mode is FieldMode.FilterPicker && (mode as FieldMode.FilterPicker).category == SpoolFilterCategory.TYPE,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clear
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `clearVendor resets vendors to empty list`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.toggleVendor("Polymaker")
        h.toggleVendor("Sunlu")

        h.clearVendor()

        assertTrue("clearVendor must empty the vendors list", h.state.value.filters.vendors.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // buildSpoolQuery parity — the query issued after a vendor toggle must carry
    // a SINGLE comma-joined filament.vendor.name= param (the ONLY form Spoolman ORs).
    //
    // ⚠ This is the device-contract regression guard for the MFG multi-select bug
    // (spool-mfg-filter-multiselect): Spoolman declares filament.vendor.name as a
    // SCALAR str query param and ORs comma-separated terms WITHIN that one value
    // (database/utils.py add_where_clause_str → value.split(",") → sqlalchemy.or_).
    // The old code emitted REPEATED params (filament.vendor.name=A&filament.vendor.name=B);
    // FastAPI binds the scalar to the LAST occurrence, so only the last vendor filtered.
    // These tests FAIL if repeated-param emission is reintroduced.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `after multi-toggle, buildSpoolQuery carries ONE comma-joined vendor param`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.toggleVendor("Polymaker")
        h.toggleVendor("Sunlu")

        val query = buildSpoolQuery(h.state.value.filters, h.state.value.sortKey, h.state.value.sortAscending)
        // The single comma-joined param — the only form Spoolman ORs.
        assertTrue(
            "Expected one comma-joined vendor param 'filament.vendor.name=Polymaker,Sunlu', got: $query",
            query.contains("filament.vendor.name=Polymaker,Sunlu"),
        )
        // And there must be EXACTLY ONE filament.vendor.name= occurrence (no repeated params).
        val occurrences = Regex("filament\\.vendor\\.name=").findAll(query).count()
        assertEquals("Exactly one filament.vendor.name= param (repeated params filter to last only)", 1, occurrences)
    }

    @Test
    fun `single vendor produces a single un-comma'd vendor param`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.toggleVendor("Polymaker")

        val query = buildSpoolQuery(h.state.value.filters, h.state.value.sortKey, h.state.value.sortAscending)
        assertTrue("filament.vendor.name=Polymaker must be present", query.contains("filament.vendor.name=Polymaker"))
        assertFalse("A single vendor must not emit a trailing comma", query.contains("filament.vendor.name=Polymaker,"))
    }

    @Test
    fun `after clearVendor, buildSpoolQuery carries no vendor param`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.toggleVendor("Polymaker")
        h.clearVendor()

        val query = buildSpoolQuery(h.state.value.filters, h.state.value.sortKey, h.state.value.sortAscending)
        assertFalse("No filament.vendor.name= param expected after clear", query.contains("filament.vendor.name="))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Defect 2 (CORRECTED, owner 2026-06-10): vendor OPTION universe is derived from
    // the SPOOL list (spool → filament → vendor.name), NOT from the filament list and
    // NOT from the raw /v1/vendor manufacturers table.
    //
    // Spoolman classification is mfg → filament → spool. The owner has FILAMENT
    // definitions for manufacturers he owns ZERO physical spools of. The first fix
    // derived the universe from the FILAMENT list, so those spool-less manufacturers
    // STILL appeared. A manufacturer that has a filament definition (and a /v1/vendor
    // row) but ZERO physical spools — "Ghost" — must NOT appear as an option, because
    // it can never match a listed spool. The option list must contain only vendors
    // actually referenced by a PHYSICAL SPOOL's filament, deduped case-insensitively.
    // ─────────────────────────────────────────────────────────────────────────

    /** Wrap a Spoolman body array (rows) into the proxy-v2 `{response,error,response_headers}` envelope. */
    private fun proxyEnvelope(rowsJson: String): JsonElement =
        MoonrakerJson.parseToJsonElement(
            """{"response":$rowsJson,"error":null,"response_headers":{"X-Total-Count":"99"}}""",
        )

    /**
     * A client where:
     *  - /v1/spool returns PHYSICAL spools whose nested filament.vendor covers Polymaker, Sunlu, a
     *    DUPLICATE "sunlu" (lowercase) on a second spool, plus one spool whose filament has no vendor
     *    (must be ignored) — but NO spool for "Ghost".
     *  - /v1/filament returns filaments for Polymaker, Sunlu AND a Ghost filament definition (the
     *    pre-correction derived universe would WRONGLY surface Ghost from here).
     *  - /v1/vendor returns Polymaker, Sunlu AND a spool-less "Ghost" manufacturer (must NOT leak in).
     *
     * The correct (spools-derived) universe is {Polymaker, Sunlu}; Ghost is excluded because no
     * physical spool references it, even though it has both a filament definition and a vendor row.
     */
    private val spoolDerivedClient: SpoolmanClient = object : SpoolmanClient {
        override suspend fun listSpools(query: String?): JsonElement = proxyEnvelope(
            """[
                {"id":1,"filament":{"id":1,"name":"PolyTerra","vendor":{"id":1,"name":"Polymaker"}}},
                {"id":2,"filament":{"id":2,"name":"PLA Matte","vendor":{"id":2,"name":"Sunlu"}}},
                {"id":3,"filament":{"id":3,"name":"PLA Meta","vendor":{"id":2,"name":"sunlu"}}},
                {"id":4,"filament":{"id":4,"name":"No Vendor Filament"}}
            ]""",
        )

        override suspend fun listFilaments(query: String?): JsonElement = proxyEnvelope(
            """[
                {"id":1,"name":"PolyTerra","vendor":{"id":1,"name":"Polymaker"}},
                {"id":2,"name":"PLA Matte","vendor":{"id":2,"name":"Sunlu"}},
                {"id":5,"name":"Ghost Glow","vendor":{"id":9,"name":"Ghost"}}
            ]""",
        )

        override suspend fun listVendors(): JsonElement = proxyEnvelope(
            """[
                {"id":1,"name":"Polymaker"},
                {"id":2,"name":"Sunlu"},
                {"id":9,"name":"Ghost"}
            ]""",
        )
    }

    private fun spoolDerivedHolder() = SpoolHolder(
        scope = kotlinx.coroutines.test.TestScope(UnconfinedTestDispatcher()),
        client = spoolDerivedClient,
        activeSpool = noActiveSpool,
    )

    @Test
    fun `vendor options derive from physical spools, excluding spool-less manufacturers`() = runTest(UnconfinedTestDispatcher()) {
        val h = spoolDerivedHolder()
        h.load()

        val vendors = h.state.value.vendors
        assertTrue("Polymaker (has a physical spool) must be an option", vendors.any { it.equals("Polymaker", true) })
        assertTrue("Sunlu (has a physical spool) must be an option", vendors.any { it.equals("Sunlu", true) })
        assertFalse(
            "Ghost (filament definition + vendor row but ZERO physical spools) must NOT be an option",
            vendors.any { it.equals("Ghost", true) },
        )
    }

    @Test
    fun `vendor options are deduped case-insensitively across spools`() = runTest(UnconfinedTestDispatcher()) {
        val h = spoolDerivedHolder()
        h.load()

        val sunluCount = h.state.value.vendors.count { it.equals("Sunlu", true) }
        assertEquals("Sunlu / sunlu (two spools) must collapse to one option", 1, sunluCount)
    }
}
