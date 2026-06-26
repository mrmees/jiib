package works.mees.jiib.ui.printstatus

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import works.mees.jiib.render.RingBuffer
import works.mees.jiib.state.Capabilities
import works.mees.jiib.state.HeaterState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.PrinterStateStore

/**
 * Toolkit-agnostic holder for the Print Status home (SHELL-04, part 1 of 2). It transforms the
 * store's ALREADY-throttled [PrinterStateStore.printerState] into two consumable shapes for the
 * Compose [PrintStatusScreen]:
 *
 *  1. A bounded **primary-heater [RingBuffer] snapshot** ([sparkline]) — the rolling temperature
 *     window the 04-06b GraphView sparkline will draw. The holder owns the ONE `RingBuffer`; it
 *     pushes the resolved primary heater's temperature on every store emission.
 *  2. A **2×3 numeric grid model** ([grid]) with EXPLICIT capability fallback (review #9) so the
 *     screen never has to special-case odd printer configs.
 *
 * ## No second throttle (review of 04-PATTERNS § PrintStatus)
 * The holder consumes the store's conflated flow directly (`PrinterStateStore` already samples the
 * high-rate plane at `DEFAULT_SAMPLE_MS = 250`). It adds NO `sample`/`debounce`/`delay` of its own —
 * a second throttle would compound latency and drop the panic-stop responsiveness budget.
 *
 * ## Capability fallback rules (review #9) — deterministic, never fabricated
 *  1. **Primary heater** = the `extruder` heater if present, else the first heater whose object name
 *     starts with `extruder` (multi-tool `extruder1`/`extruder2` naming).
 *  2. **Secondary** = `heater_bed` if present; else the bed slot is OMITTED (no fabricated 0°/—) and
 *     the next available heater (e.g. `heater_generic chamber`) is promoted into the slot.
 *  3. A printer with **no bed and no other heater** renders the grid WITHOUT a secondary slot.
 *  4. **Nonstandard heater names** are surfaced by their Moonraker object name verbatim.
 *  5. When **fewer than 6 stats** are available, the remaining [PrintStatusGrid.cells] are `null`
 *     placeholders — the screen renders an empty cell, never a fabricated value.
 *
 * Plain Kotlin (no Compose annotations) so it is host-unit-testable; mirrors the [PrinterState] /
 * [PrinterStateStore] StateFlow discipline (the only new piece is the per-holder primary RingBuffer).
 *
 * @param scope the lifecycle scope the `printerState` collect runs on (the UI host supplies it).
 * @param store the already-assembled Phase-2 spine; the holder CONSUMES it, never opens a session.
 */
class PrintStatusHolder(
    scope: CoroutineScope,
    private val store: PrinterStateStore,
) {
    /** The ONE primary-heater rolling window (04-06b sparkline draws this). */
    private val ring = RingBuffer()

    private val _sparkline = MutableStateFlow(FloatArray(0))
    /** Bounded primary-heater temperature snapshot (oldest→newest), ≤ [RingBuffer.capacity]. */
    val sparkline: StateFlow<FloatArray> = _sparkline.asStateFlow()

    private val _grid = MutableStateFlow(PrintStatusGrid.EMPTY)
    /** The resolved 2×3 numeric grid model with capability fallback (review #9). */
    val grid: StateFlow<PrintStatusGrid> = _grid.asStateFlow()

    init {
        // Consume the store's ALREADY-throttled flow — NO second sample/debounce/delay here (review #9).
        scope.launch {
            store.printerState.collect { state ->
                val caps = store.capabilities.value
                val primaryName = resolvePrimary(state, caps)
                // Push the primary heater's live temperature into the bounded ring (sparkline source).
                val primaryTemp = primaryName?.let { state.heaters[it]?.temperature?.toFloat() } ?: 0f
                ring.push(primaryTemp)
                _sparkline.value = ring.snapshot()
                _grid.value = buildGrid(state, caps, primaryName)
            }
        }
    }

    // ---- capability resolution (review #9) ----------------------------------------------------------

    /** Rule 1: the `extruder` heater, else the first `extruder`-prefixed heater, else null. */
    private fun resolvePrimary(state: PrinterState, caps: Capabilities): String? {
        val names = orderedHeaterNames(state, caps)
        names.firstOrNull { it == "extruder" }?.let { return it }
        return names.firstOrNull { it.startsWith("extruder") }
    }

    /** Rule 2/3: `heater_bed` if present, else the first heater that is neither the primary nor a bed. */
    private fun resolveSecondary(state: PrinterState, caps: Capabilities, primary: String?): String? {
        val names = orderedHeaterNames(state, caps)
        if (caps.hasBed && names.contains("heater_bed")) return "heater_bed"
        // No bed → promote the next available heater (chamber/etc.) by its object name (rule 4).
        return names.firstOrNull { it != primary && it != "heater_bed" }
    }

    /**
     * The deterministic heater ordering used for resolution: the capability list when populated
     * (the derived superset), else the live state's keys. Capabilities is authoritative for "what
     * exists"; falling back to state keys keeps the holder useful in tests/seed-only paths.
     */
    private fun orderedHeaterNames(state: PrinterState, caps: Capabilities): List<String> =
        if (caps.heaters.isNotEmpty()) caps.heaters else state.heaters.keys.toList()

    private fun heaterCell(state: PrinterState, name: String?): HeaterCell? {
        val n = name ?: return null
        val h: HeaterState = state.heaters[n] ?: return null
        // A target of 0 means "off, no setpoint" — surface it as null so the cell shows CUR only.
        val target = if (h.target > 0.0) h.target else null
        return HeaterCell(objectName = n, current = h.temperature, target = target)
    }

    /**
     * Build the 2×3 grid: [primary], [secondary] (or omitted), progress, and a Z/filename info cell.
     * The six logical [PrintStatusGrid.cells] are a flattened view where any unfilled slot is `null`
     * (rule 5 — an empty placeholder, never a fabricated value).
     */
    private fun buildGrid(state: PrinterState, caps: Capabilities, primaryName: String?): PrintStatusGrid {
        val primary = heaterCell(state, primaryName)
        val secondaryName = resolveSecondary(state, caps, primaryName)
        val secondary = heaterCell(state, secondaryName)

        val progressCell: ProgressCell? =
            if (state.progress > 0.0) ProgressCell(state.progress) else null

        val infoCell: InfoCell? = state.printFilename
            .takeIf { it.isNotBlank() }
            ?.let { InfoCell(label = "FILE", value = it) }

        // Flattened 6-cell view: [primary, secondary, progress, info, null, null]. Unfilled = null.
        val cells: List<PrintStatusCell?> = listOf(
            primary,
            secondary,
            progressCell,
            infoCell,
            null,
            null,
        )

        return PrintStatusGrid(
            primary = primary,
            secondary = secondary,
            progress = progressCell,
            info = infoCell,
            cells = cells,
        )
    }
}

/** A single grid cell — either a heater readout, a progress %, or a labelled info value. */
sealed interface PrintStatusCell

/** A heater cell: CURRENT plus an optional SETPOINT (null when the heater is off / has no target). */
data class HeaterCell(
    val objectName: String,
    val current: Double,
    val target: Double?,
) : PrintStatusCell

/** The print-progress cell (0.0..1.0). */
data class ProgressCell(val fraction: Double) : PrintStatusCell

/** A labelled informational cell (e.g. the active filename, or Z-height later). */
data class InfoCell(val label: String, val value: String) : PrintStatusCell

/**
 * The resolved 2×3 numeric grid model. [primary]/[secondary]/[progress]/[info] are the typed,
 * convenient accessors; [cells] is the flattened 6-slot view (unfilled = `null` placeholder, never
 * fabricated — review #9).
 */
data class PrintStatusGrid(
    val primary: HeaterCell?,
    val secondary: HeaterCell?,
    val progress: ProgressCell?,
    val info: InfoCell?,
    val cells: List<PrintStatusCell?>,
) {
    companion object {
        val EMPTY = PrintStatusGrid(
            primary = null,
            secondary = null,
            progress = null,
            info = null,
            cells = listOf(null, null, null, null, null, null),
        )
    }
}
