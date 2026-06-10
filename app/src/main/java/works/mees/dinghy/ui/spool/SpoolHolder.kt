package works.mees.dinghy.ui.spool

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.SetSpoolArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.spool.SpoolmanFilament
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.spool.SpoolmanStatus
import works.mees.dinghy.spool.normalizeColorHex
import works.mees.dinghy.spool.parseSpoolmanFilaments
import works.mees.dinghy.spool.parseSpoolmanLocations
import works.mees.dinghy.spool.parseSpoolmanMaterials
import works.mees.dinghy.spool.parseSpoolmanSpoolDetail
import works.mees.dinghy.spool.parseSpoolmanSpools
import works.mees.dinghy.spool.parseSpoolmanVendors

/**
 * The Spool-picker SORT KEY (SPOOL-03; docs/view_specific_notes/spoolman.md §Spool Picker And Filters).
 * Each key is one axis; the DIRECTION ([SpoolPickerState.sortAscending]) is toggled by re-tapping the
 * active key's chip (Matthew, 2026-06-04). The Spoolman `sort=` field + per-key tiebreaker is built in
 * [buildSpoolQuery]; [defaultAscending] is the direction a key starts in when first selected.
 *  - [NAME] — `filament.name` (A→Z by default).
 *  - [DATE] — `last_used` (most-recent-first by default; "what did I touch last").
 *  - [REMAINING] — `remaining_weight` (lowest-first by default; "what's about to run out").
 */
enum class SpoolSortKey(val label: String, val icon: String, val field: String, val defaultAscending: Boolean) {
    NAME("Name", "match_case", "filament.name", true),
    DATE("Date", "calendar_clock", "last_used", false),
    REMAINING("Remaining", "scale", "remaining_weight", true),
}

/**
 * The fixed material FAMILY chips (D-05; Matthew 2026-06-04 — fuzzy family chips, NOT one chip per exact
 * inventory material). Each is a label → the comma terms sent as one UNQUOTED `filament.material=A,B`
 * term, so tapping "PLA" partial-matches `PLA`, `PLA+`, `PLA Meta`, … and "ABS/ASA" sends `ABS,ASA`.
 */
val MATERIAL_FAMILIES: List<Pair<String, List<String>>> = listOf(
    "PLA" to listOf("PLA"),
    "PETG" to listOf("PETG"),
    "ABS/ASA" to listOf("ABS", "ASA"),
    "TPU" to listOf("TPU"),
    "PC" to listOf("PC"),
    "Nylon" to listOf("PA", "Nylon"),
)

/** Map a selected family LABEL to its Spoolman partial-match terms (unknown label → itself). */
private fun familyTerms(label: String): List<String> =
    MATERIAL_FAMILIES.firstOrNull { it.first.equals(label, ignoreCase = true) }?.second ?: listOf(label)

/** Map a raw material string (slicer/inventory, e.g. `PLA+`) to its family chip LABEL, or null. */
internal fun materialFamilyLabel(raw: String): String? {
    val up = raw.trim().uppercase()
    if (up.isEmpty()) return null
    return MATERIAL_FAMILIES.firstOrNull { (_, terms) ->
        terms.any { up.startsWith(it.uppercase()) || up.contains(it.uppercase()) }
    }?.first
}

/**
 * The applied picker filters (D-04/D-05/D-06). All optional — an empty/absent filter is "no constraint".
 *  - [materialFamilies] — D-05 material FAMILY chips, comma-joined into one `filament.material=A,B`
 *    UNQUOTED term so `PLA` catches `PLA+`; multi-family is a comma list, NOT a fuzzy combined term.
 *  - [vendors] — multi-select manufacturer filter; OR within the facet (a spool matches if its
 *    filament vendor matches ANY selected vendor). AND across facets (standard faceted filtering).
 *    Empty list = no vendor constraint.
 *  - [location] — D-04 location shortcut. [LOCATION_NONE] is the sentinel for the "No location" chip
 *    (`location=` empty), distinct from null = "any location".
 *  - [colorFilamentIds] — D-06 result of a swatch-tap two-step: the filament ids the color-similarity
 *    endpoint returned (the spool list has no direct color filter), folded into `filament.id=<csv>`.
 *  - [colorSwatchHex] — the swatch the user tapped (for the active filter chip display only).
 */
data class SpoolFilters(
    val materialFamilies: List<String> = emptyList(),
    val vendors: List<String> = emptyList(),
    val location: String? = null,
    val colorFilamentIds: List<Int>? = null,
    val colorSwatchHex: String? = null,
) {
    companion object {
        /** The "No location" sentinel (D-04) — distinct from null ("any location"). */
        const val LOCATION_NONE: String = "__no_location__"

        /**
         * The "Multi-color" color-filter marker (Matthew, 2026-06-04) — stored in [colorSwatchHex] so the
         * grid highlights the Multi-color tile; [colorFilamentIds] carries the actual multi-color filament
         * ids. Distinct from any real `#hex`.
         */
        const val MULTICOLOR: String = "__multicolor__"
    }
}

/**
 * The D-04 gcode-aware prefilter seed (SPOOL-09, plan 11-08): the selected file's filament arrays carried
 * from a Files spool-warning "Pick spool" into the picker so it opens pre-filtered by what the file wants.
 *  - [filamentType] — the file's `filament_type[]` (per-extruder material families); seeded as the
 *    material-family chip(s) (D-05 multi-family).
 *  - [filamentColors] — the file's `filament_colors[]` (per-extruder `#hex`); surfaced as a color-similarity
 *    HINT (the nearest palette swatch is pre-selected, NOT a hard filter — D-04/D-06 "hint not strict").
 *
 * A one-time entry default the user can clear/refine; empty arrays apply no prefilter (T-11-08-03).
 */
data class SpoolPrefilterSeed(
    val filamentType: List<String> = emptyList(),
    val filamentColors: List<String> = emptyList(),
)

/**
 * Controls whether the Field shows the spool list or an in-place filter picker (Field-takeover pattern,
 * docs/ui_design/COMPONENTS.md §"Field-takeover picker"). No separate screen push — the Field swaps in place.
 *
 * - [Spools] — the normal spool list + FootButtonBar (default).
 * - [FilterPicker] — the Field shows the option list for [category]; tapping an option or Done/Clear returns
 *   to [Spools] by the caller setting `fieldMode = FieldMode.Spools`.
 */
sealed class FieldMode {
    /** Normal spool-list Field. */
    data object Spools : FieldMode()

    /** In-place filter picker for [category]; the Field swaps to that facet's option list. */
    data class FilterPicker(val category: SpoolFilterCategory) : FieldMode()
}

/**
 * The Spool-picker page state (SPOOL-03; docs/view_specific_notes/spoolman.md §Suggested UI state).
 * Mirrors [works.mees.dinghy.ui.files.FileBrowserState] — a plain value type the screen renders and the
 * holder mutators update. Every read degrades to empty/null (never throws); a malformed Spoolman row
 * already drops in the parser.
 *
 * @property spools the current picker result rows.
 * @property selected the row whose detail fills the Focus (null = nothing selected → portrait stays Field).
 * @property fieldMode controls whether the Field shows the spool list or an in-place filter picker (23-06).
 * @property materials/[vendors]/[locations] the dynamic chip universes (D-04) — empty when unread/idle.
 * @property activeStatus the live active-spool status (D-10 reconciled) — drives "this is loaded" marks
 *   and the active-spool reconcile; null when unavailable/idle.
 */
data class SpoolPickerState(
    val spools: List<SpoolmanSpool> = emptyList(),
    val selected: SpoolmanSpool? = null,
    val fieldMode: FieldMode = FieldMode.Spools,
    val filters: SpoolFilters = SpoolFilters(),
    val sortKey: SpoolSortKey = SpoolSortKey.NAME,
    val sortAscending: Boolean = SpoolSortKey.NAME.defaultAscending,
    val materials: List<String> = emptyList(),
    val vendors: List<String> = emptyList(),
    val locations: List<String> = emptyList(),
    val activeStatus: SpoolmanStatus? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * The Spool-picker page holder (SPOOL-03, plan 11-06). Mirrors
 * [works.mees.dinghy.ui.files.FileBrowserHolder] EXACTLY: a [MutableStateFlow] exposed via
 * [asStateFlow], a [scope].launch reaction to the upstream [activeSpool] flow (D-10 — an external
 * Fluidd/runout-macro change reconciles into the picker's "loaded" marks), `runCatching` reads through
 * [client] that degrade to empty/null, and suspend mutators the screen's chips/sorts/rows call.
 *
 * ## Two transports (D-07), inherited
 * Inventory list/filter reads ride [client] (the lean `server.spoolman.proxy` reader, 11-04). Active-spool
 * TRUTH is the [activeStatus] mirrored from the upstream [activeSpool] StateFlow (the
 * [works.mees.dinghy.spool.ActiveSpoolFacade]) — the picker NEVER writes the active spool itself; SET/CLEAR
 * is the screen's gutter action through the dispatcher. The holder is a READ surface plus selection state.
 *
 * ## Session-derived, not persisted (Discretion)
 * The applied filters/sort/selection are derived fresh each session (the holder is `remember`ed keyed on
 * the session in the shell, mirroring the Files holder) — no DataStore persistence.
 *
 * @param scope the composition/session scope (cancels the upstream collector on holder death).
 * @param client the session Spoolman inventory reader, or a no-op when idle (every read → null → empty).
 * @param activeSpool the live active-spool status flow (D-10) the picker mirrors for its "loaded" marks.
 */
class SpoolHolder(
    scope: CoroutineScope,
    private val client: SpoolmanClient,
    activeSpool: StateFlow<SpoolmanStatus?>,
) {
    private val _state = MutableStateFlow(SpoolPickerState(activeStatus = activeSpool.value))
    val state: StateFlow<SpoolPickerState> = _state.asStateFlow()

    // Non-terminating collector runs in a child scope whose SupervisorJob is NOT a child of
    // [scope]'s Job, so [scope] is never blocked waiting for it. The parent scope's cancellation
    // propagates via [invokeOnCompletion]; the holder can also be cancelled early via [cancel].
    // Mirrors the ConsoleHolder / MacroHolder pattern (WR-01 family, CR-01).
    private val holderJob = SupervisorJob()
    private val holderScope = CoroutineScope(scope.coroutineContext + holderJob)

    /**
     * The ALWAYS-LIVE active-spool DETAIL (18.3-04, D-06.2/D-07). [SpoolPickerState.activeStatus] carries
     * only the active id + connection — NOT a color-bearing record — and the inventory [spools] list only
     * populates after a [load] call the drawer never triggers. The shell/drawer needs the active spool's
     * COLOR without opening SpoolScreen, so this flow fetches + keeps the full [SpoolmanSpool] by the active
     * id. Null when there is no active spool (D-13 clear) OR the best-effort fetch fails/mismatches → the
     * drawer band degrades to the empty spool downstream, never throws.
     */
    private val _activeSpoolDetail = MutableStateFlow<SpoolmanSpool?>(null)
    val activeSpoolDetail: StateFlow<SpoolmanSpool?> = _activeSpoolDetail.asStateFlow()

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion { holderJob.cancel() }

        // D-10: mirror the upstream active-spool truth so the picker marks the currently-loaded spool and
        // reconciles an EXTERNAL change (Fluidd/runout-macro) without assuming Dinghy caused it. Read-only.
        // 18.3-04 (D-06.2): ALSO drive the live activeSpoolDetail color source off the active id — a
        // best-effort getSpool(id) so the shell/drawer can tint the Spool tile without a load() call.
        holderScope.launch {
            activeSpool.collect { status ->
                _state.update { it.copy(activeStatus = status) }
                val id = status?.activeSpoolId
                if (id == null) {
                    // No active spool (or a D-13 clear) → no color (the drawer renders the empty spool).
                    _activeSpoolDetail.value = null
                } else {
                    // Best-effort single-spool detail read; a network failure / null / id mismatch parses to
                    // null → empty spool downstream. Mirrors the holder's existing runCatching discipline.
                    val envelope = runCatching { client.getSpool(id) }.getOrNull()
                    _activeSpoolDetail.value = parseSpoolmanSpoolDetail(envelope, expectedId = id)
                }
            }
        }
    }

    /**
     * Cancel the detached collector NOW (CR-01). The host calls this when `remember(store)` swaps this
     * holder for a new one on a spine rebuild (reconnect): without it, the discarded holder's collector
     * keeps running the dead session's flow until the whole shell leaves composition, orphaning one
     * collector per reconnect. Cancelling [holderJob] is idempotent and coexists with the
     * [scope]-cancellation path ([invokeOnCompletion]) that handles shell teardown.
     * Mirrors [works.mees.dinghy.ui.console.ConsoleHolder.cancel] exactly.
     */
    fun cancel() {
        holderJob.cancel()
    }

    /**
     * Initial load: the dynamic chip universes (materials/vendors/locations — D-04) AND the default
     * unarchived spool list. Best-effort; each read degrades to empty independently. Idempotent — safe to
     * call from the screen's `LaunchedEffect(holder)` (the Files `loadRoot` precedent).
     */
    suspend fun load() {
        loadChips()
        refresh()
    }

    /** (Re)issue the spool-list read for the current [SpoolFilters] + [SpoolSort]; never throws. */
    suspend fun refresh() {
        _state.update { it.copy(loading = true, error = null) }
        val query = buildSpoolQuery(_state.value.filters, _state.value.sortKey, _state.value.sortAscending)
        val envelope = runCatching { client.listSpools(query) }.getOrNull()
        val parsed = parseSpoolmanSpools(envelope)
        _state.update { current ->
            // Keep the selection only if it survived the new result set (else clear it so the Focus follows
            // the visible list). A failed/empty read shows the empty list + an error notice, never a crash.
            val keptSelection = current.selected?.let { sel -> parsed.rows.firstOrNull { it.id == sel.id } }
            current.copy(
                spools = parsed.rows,
                selected = keptSelection,
                loading = false,
                error = if (!parsed.success && parsed.rows.isEmpty()) "Could not load spools." else null,
            )
        }
    }

    /** Select a row (its detail fills the Focus); a re-tap of the same row is a no-op. */
    fun selectSpool(spool: SpoolmanSpool) {
        if (_state.value.selected?.id == spool.id) return
        _state.update { it.copy(selected = spool) }
    }

    /** Clear the selection (portrait collapses back to Field-only). */
    fun clearSelection() {
        _state.update { it.copy(selected = null) }
    }

    /** Open the in-place Field-takeover filter picker for [category] (23-06 FieldMode pattern). */
    fun openFilterPicker(category: SpoolFilterCategory) {
        _state.update { it.copy(fieldMode = FieldMode.FilterPicker(category)) }
    }

    /** Close the Field-takeover filter picker and return to the spool list. */
    fun closeFilterPicker() {
        _state.update { it.copy(fieldMode = FieldMode.Spools) }
    }

    /**
     * D-05: toggle a material-FAMILY chip. Families are comma-joined UNQUOTED into one
     * `filament.material=A,B` term (so `PLA` catches `PLA+`; a multi-family chip is a comma list, NOT a
     * fuzzy combined term). Re-issues the list read.
     */
    suspend fun toggleMaterialFamily(family: String) {
        _state.update { current ->
            val present = current.filters.materialFamilies.any { it.equals(family, ignoreCase = true) }
            val next = if (present) {
                current.filters.materialFamilies.filterNot { it.equals(family, ignoreCase = true) }
            } else {
                current.filters.materialFamilies + family
            }
            current.copy(filters = current.filters.copy(materialFamilies = next))
        }
        refresh()
    }

    /**
     * Toggle a vendor in the multi-select MFG filter. Each vendor toggles independently (in/out of
     * the selected set). Filter semantics: OR within the facet (a spool matches if its vendor is in
     * the selected set). Re-issues the list read.
     */
    suspend fun toggleVendor(vendor: String) {
        _state.update { current ->
            val present = current.filters.vendors.any { it.equals(vendor, ignoreCase = true) }
            val next = if (present) {
                current.filters.vendors.filterNot { it.equals(vendor, ignoreCase = true) }
            } else {
                current.filters.vendors + vendor
            }
            current.copy(filters = current.filters.copy(vendors = next))
        }
        refresh()
    }

    /**
     * D-04: toggle a location chip. [location] = [SpoolFilters.LOCATION_NONE] is the "No location" chip
     * (`location=` empty); a plain string is a location shortcut. Re-tap clears. Re-issues the read.
     */
    suspend fun toggleLocation(location: String) {
        _state.update { current ->
            val next = if (current.filters.location == location) null else location
            current.copy(filters = current.filters.copy(location = next))
        }
        refresh()
    }

    /**
     * D-06: apply a color SWATCH filter — the two-step "slow operation, only on swatch tap". Step 1: ask
     * the color-similarity filament endpoint (`/v1/filament?color_hex=…&color_similarity_threshold=…`) for
     * nearby filament ids. Step 2: fold those ids into the spool list (`filament.id=<csv>`). A re-tap of
     * the SAME swatch clears the color filter. Best-effort — a failed similarity read clears the color
     * filter rather than poisoning the list.
     */
    suspend fun applyColorSwatch(swatchHex: String) {
        val current = _state.value
        if (current.filters.colorSwatchHex.equals(swatchHex, ignoreCase = true)) {
            // Re-tap clears.
            _state.update {
                it.copy(filters = it.filters.copy(colorFilamentIds = null, colorSwatchHex = null))
            }
            refresh()
            return
        }
        val hex = swatchHex.removePrefix("#")
        val query = "color_hex=$hex&color_similarity_threshold=$COLOR_SIMILARITY_THRESHOLD&limit=$FILAMENT_LIMIT"
        val envelope = runCatching { client.listFilaments(query) }.getOrNull()
        val ids = parseSpoolmanFilaments(envelope).rows.mapNotNull(SpoolmanFilament::id)
        _state.update {
            it.copy(filters = it.filters.copy(colorFilamentIds = ids, colorSwatchHex = swatchHex))
        }
        refresh()
    }

    /**
     * D-06 "Multi-color": filter to spools whose filament carries `multi_color_hexes` (Matthew, 2026-06-04).
     * Spoolman's spool list has no direct "is multi-color" filter, so reuse the color two-step: fetch the
     * filaments, keep client-side those with a non-blank `multiColorHexes`, then fold their ids into the
     * spool read (`filament.id=<csv>`). The [SpoolFilters.MULTICOLOR] marker drives the tile highlight. A
     * re-tap clears. Best-effort — a failed read clears the color filter rather than poisoning the list.
     */
    suspend fun applyMultiColor() {
        if (_state.value.filters.colorSwatchHex == SpoolFilters.MULTICOLOR) {
            clearColor()
            return
        }
        val envelope = runCatching { client.listFilaments("limit=$FILAMENT_LIMIT") }.getOrNull()
        val ids = parseSpoolmanFilaments(envelope).rows
            .filter { !it.multiColorHexes.isNullOrBlank() }
            .mapNotNull(SpoolmanFilament::id)
        _state.update {
            it.copy(filters = it.filters.copy(colorFilamentIds = ids, colorSwatchHex = SpoolFilters.MULTICOLOR))
        }
        refresh()
    }

    /**
     * Change the active sort (name / date / remaining). Re-tapping the ALREADY-active key flips its
     * direction (asc↔desc); switching to a different key resets to that key's [SpoolSortKey.defaultAscending]
     * (Matthew, 2026-06-04). Always re-issues the list read.
     */
    suspend fun applySort(key: SpoolSortKey) {
        _state.update { current ->
            if (current.sortKey == key) {
                current.copy(sortAscending = !current.sortAscending)
            } else {
                current.copy(sortKey = key, sortAscending = key.defaultAscending)
            }
        }
        refresh()
    }

    /** Clear every applied filter (keep the sort); re-issues the read. */
    suspend fun clearFilters() {
        _state.update { it.copy(filters = SpoolFilters()) }
        refresh()
    }

    /** Clear just the material-family filter (the Type selector's Clear); re-issues the read. */
    suspend fun clearMaterialFamilies() {
        _state.update { it.copy(filters = it.filters.copy(materialFamilies = emptyList())) }
        refresh()
    }

    /** Clear the vendor / MFG filter (the MFG selector's Clear); re-issues the read. */
    suspend fun clearVendor() {
        _state.update { it.copy(filters = it.filters.copy(vendors = emptyList())) }
        refresh()
    }

    /** Clear just the color filter (the Color selector's Clear); re-issues the read. */
    suspend fun clearColor() {
        _state.update { it.copy(filters = it.filters.copy(colorFilamentIds = null, colorSwatchHex = null)) }
        refresh()
    }

    /**
     * D-04 gcode-aware prefilter: seed the picker filters ONCE from a file's filament arrays (the seed
     * carried from a Files spool-warning "Pick spool"). The material chip(s) come from [SpoolPrefilterSeed.filamentType]
     * (D-05 multi-family — each family becomes a `filament.material` term); the file's first valid color
     * is surfaced as a color-similarity HINT — the nearest palette swatch is pre-selected for display
     * ([SpoolFilters.colorSwatchHex]) WITHOUT firing the D-06 hard `filament.id` two-step (D-04/D-06:
     * color is a hint, NOT a strict filter — non-matching colors are NOT filtered out). The seed is a
     * clearable one-time default: it REPLACES the current filters (the user can then clear/refine via the
     * chips). An empty seed applies no prefilter (T-11-08-03). Re-issues the list read.
     */
    suspend fun seedPrefilter(seed: SpoolPrefilterSeed) {
        // Map each raw slicer material (e.g. `PLA+`) to its family chip LABEL (D-05) and de-dup, so the
        // seed pre-selects the same fixed family chips the picker renders (Matthew, 2026-06-04).
        val families = seed.filamentType
            .mapNotNull { materialFamilyLabel(it) ?: it.trim().ifEmpty { null } }
            .distinctBy { it.uppercase() }
        // Color is a HINT (D-04/D-06): map the file's first valid color to the nearest palette swatch for
        // a pre-selected display chip, but do NOT fold it into a hard filament.id filter (colorFilamentIds
        // stays null → no rows are filtered out by color).
        val colorHint = seed.filamentColors
            .firstNotNullOfOrNull { normalizeColorHex(it) }
            ?.let { nearestPaletteSwatch(it) }
        _state.update {
            it.copy(
                filters = SpoolFilters(
                    materialFamilies = families,
                    colorSwatchHex = colorHint,
                    colorFilamentIds = null, // hint, not a hard filter (D-04/D-06).
                ),
            )
        }
        refresh()
    }

    /**
     * Change-during-print (D-10): set a new active spool mid-print WITHOUT interrupting the running print.
     * Dispatches `post_spool_id {spool_id}` via the SAME set-active path the Status card / picker Load
     * action uses — this only RE-POINTS active-spool tracking; it never sends a print-control command, so
     * the running print is untouched. There is NO print-state gating here (deliberately — the Files
     * Delete-blocks-all-during-print defect must NOT be copied): the whole point is to change the spool
     * while printing. Moonraker reconciles via `notify_active_spool_set`, and the holder's upstream
     * collector flips [SpoolPickerState.activeStatus] (the "Loaded" mark) to the new id — the card/picker
     * reconcile to it, they do not clobber. A null [dispatcher] (idle/disconnected) is a safe no-op.
     */
    fun setActiveSpool(dispatcher: CommandDispatcher?, id: Int) {
        dispatcher?.dispatch(CommandRegistry.spoolmanPostSpoolId, SetSpoolArgs(spoolId = id))
    }

    /**
     * Unload / clear the active spool (D-13 → `post_spool_id {}`, the null-spoolId clear contract). Used by
     * the selected-spool detail's Unload button when the selected spool is the currently-loaded one
     * (Matthew, 2026-06-04). Like [setActiveSpool] this only re-points active-spool tracking and never
     * interrupts a print; Moonraker reconciles via `notify_active_spool_set`. Null dispatcher = safe no-op.
     */
    fun clearActiveSpool(dispatcher: CommandDispatcher?) {
        dispatcher?.dispatch(CommandRegistry.spoolmanPostSpoolId, SetSpoolArgs(spoolId = null))
    }

    /** Load the dynamic chip universes (D-04) once; each degrades to empty independently. */
    private suspend fun loadChips() {
        val materials = parseSpoolmanMaterials(runCatching { client.listMaterials() }.getOrNull()).rows
        val vendors = parseSpoolmanVendors(runCatching { client.listVendors() }.getOrNull()).rows
            .mapNotNull { it.name }
        val locations = parseSpoolmanLocations(runCatching { client.listLocations() }.getOrNull()).rows
        _state.update { it.copy(materials = materials, vendors = vendors, locations = locations) }
    }

    private companion object {
        /** D-06 color-similarity threshold (Spoolman default-ish "close enough"); only fired on swatch-tap. */
        const val COLOR_SIMILARITY_THRESHOLD = 20

        /** The picker page size (docs/view_specific_notes/spoolman.md — limit=50). */
        const val SPOOL_LIMIT = 50

        /** The color-similarity filament fetch cap (the two-step step 1). */
        const val FILAMENT_LIMIT = 50
    }
}

/**
 * Build the `server.spoolman.proxy` `query` string for the spool list from the applied filters + sort
 * (docs/view_specific_notes/spoolman.md §Spool Picker And Filters). Plain `key=value&…` — the
 * [works.mees.dinghy.spool.MoonrakerSpoolmanClient] URL-encodes dotted keys (D-07/Pitfall 5). Pure +
 * host-testable (no Android, no I/O).
 *
 *  - `allow_archived=false` (the picker hides archived; the row still badges any that slip through).
 *  - D-05 material families → their partial-match terms, one UNQUOTED comma term `filament.material=PLA,PETG`.
 *  - D-06 color → the similarity-derived `filament.id=<csv>` (empty ids ⇒ a deliberately-empty result).
 *  - D-04 location: the [SpoolFilters.LOCATION_NONE] sentinel ⇒ `location=` (empty); a value ⇒ `location=<v>`.
 *  - sort: the [SpoolSortKey] field + direction + a per-key tiebreaker (Matthew 2026-06-04).
 */
fun buildSpoolQuery(filters: SpoolFilters, sortKey: SpoolSortKey, ascending: Boolean): String {
    val parts = mutableListOf<String>()
    parts += "allow_archived=false"
    if (filters.materialFamilies.isNotEmpty()) {
        val terms = filters.materialFamilies.flatMap { familyTerms(it) }.distinct()
        parts += "filament.material=${terms.joinToString(",")}"
    }
    // Multi-select vendors: OR within the facet — Spoolman supports repeated params for OR queries.
    // Each selected vendor gets its own `filament.vendor.name=<name>` param (Spoolman ORs them).
    filters.vendors.forEach { parts += "filament.vendor.name=$it" }
    filters.colorFilamentIds?.let { ids ->
        // An empty similarity result is a deliberate "no matches" — send an unmatchable id rather than
        // dropping the filter (which would silently show everything). -1 never matches a real spool.
        val csv = if (ids.isEmpty()) "-1" else ids.joinToString(",")
        parts += "filament.id=$csv"
    }
    when (filters.location) {
        null -> Unit
        SpoolFilters.LOCATION_NONE -> parts += "location="
        else -> parts += "location=${filters.location}"
    }
    val dir = if (ascending) "asc" else "desc"
    val sortClause = when (sortKey) {
        // Alphabetical = the displayed row title order ("<material> · <name>"): material first, then name
        // (Matthew 2026-06-04 — NOT just filament.name / the color name).
        SpoolSortKey.NAME -> "filament.material:$dir,filament.name:$dir,id:asc"
        SpoolSortKey.DATE -> "last_used:$dir,registered:$dir"
        SpoolSortKey.REMAINING -> "remaining_weight:$dir,id:asc"
    }
    parts += "sort=$sortClause"
    parts += "limit=50"
    return parts.joinToString("&")
}

/**
 * The fixed color palette the picker's swatch chips render (must mirror [SpoolPicker]'s `PALETTE_SWATCHES`
 * hexes so a seeded hint highlights the matching chip). D-06.
 */
private val PREFILTER_PALETTE: List<String> = listOf(
    "#000000", // Black
    "#FFFFFF", // White
    "#808080", // Gray
    "#FF0000", // Red
    "#FF8000", // Orange
    "#FFFF00", // Yellow
    "#00C000", // Green
    "#0050FF", // Blue
    "#8000FF", // Purple
    "#FF60C0", // Pink
    "#7A4A20", // Brown
)

/**
 * Map a normalized `#RRGGBB`(`AA`) hex to the NEAREST fixed-palette swatch (D-06 color HINT, NOT strict):
 * the file's exact slicer color rarely equals a palette swatch, so the seed pre-selects the closest one as
 * a visual hint. Nearest = smallest squared RGB distance (alpha ignored). Returns null only if the input
 * cannot be parsed (never throws).
 */
internal fun nearestPaletteSwatch(normalizedHex: String): String? {
    val rgb = parseRgb(normalizedHex) ?: return null
    return PREFILTER_PALETTE.minByOrNull { swatch ->
        val s = parseRgb(swatch) ?: return@minByOrNull Int.MAX_VALUE
        val dr = rgb[0] - s[0]
        val dg = rgb[1] - s[1]
        val db = rgb[2] - s[2]
        dr * dr + dg * dg + db * db
    }
}

/** Parse the RGB triple from a normalized `#RRGGBB` / `#RRGGBBAA` hex; null if unparseable. */
private fun parseRgb(normalizedHex: String): IntArray? {
    val h = normalizedHex.removePrefix("#")
    if (h.length != 6 && h.length != 8) return null
    return runCatching {
        intArrayOf(
            h.substring(0, 2).toInt(16),
            h.substring(2, 4).toInt(16),
            h.substring(4, 6).toInt(16),
        )
    }.getOrNull()
}
