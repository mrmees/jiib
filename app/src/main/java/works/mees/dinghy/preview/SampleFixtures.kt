package works.mees.dinghy.preview

import works.mees.dinghy.spool.SpoolmanFilament
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.spool.SpoolmanVendor
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.ui.finetune.FineTuneVm
import works.mees.dinghy.ui.printstatus.PrintStatusMode
import works.mees.dinghy.ui.printstatus.TerminalKind
import works.mees.dinghy.ui.spool.FieldMode
import works.mees.dinghy.ui.spool.SpoolFilterCategory
import works.mees.dinghy.ui.spool.SpoolPickerState

/**
 * The reusable, PURE, immutable fake-state library (SC-2) every preview exemplar consumes — and the
 * SAME seed the Phase-22 backfill reuses, so these are intentionally NOT per-preview one-offs.
 *
 * ⚠ NO live Moonraker, NO network, NO coroutines: these are plain immutable values. The analog is
 * [works.mees.dinghy.bench.SyntheticFeed] (deterministic on-device fixtures) — same discipline, but
 * for host-rendered previews. A consumer wraps any of these in [PreviewBox] to render a screen at a
 * known state without a printer.
 */
object SampleFixtures {

    // ---------------------------------------------------------------------------------------------
    // Print-Status modes (the 4/6-state classifier surface — PrintStatusMode.kt)
    // ---------------------------------------------------------------------------------------------

    /** All FOUR PrintStatusMode states, with Terminal expanded to its three kinds (six entries). */
    val printStatusModes: List<PrintStatusMode> = listOf(
        PrintStatusMode.Standby,
        PrintStatusMode.Printing,
        PrintStatusMode.Paused,
        PrintStatusMode.Terminal(TerminalKind.Complete),
        PrintStatusMode.Terminal(TerminalKind.Cancelled),
        PrintStatusMode.Terminal(TerminalKind.Error),
    )

    /**
     * Build a [PrinterState] whose `printState` classifies to [mode] (the inverse of
     * `classifyPrintStatus`). The PrintStatus screen derives its mode from `printState` ONLY
     * (PrintStatusMode.kt — print-state-only discipline), so a fixture that sets the matching
     * `printState` reproduces every screen state deterministically. Defaults fill everything else.
     */
    fun forMode(mode: PrintStatusMode): PrinterState =
        PrinterState(printState = printStateFor(mode))

    /** The raw [PrintState] that classifies to [mode] (inverse of `classifyPrintStatus`). */
    private fun printStateFor(mode: PrintStatusMode): PrintState = when (mode) {
        PrintStatusMode.Standby -> PrintState.Standby
        PrintStatusMode.Printing -> PrintState.Printing
        PrintStatusMode.Paused -> PrintState.Paused
        is PrintStatusMode.Terminal -> when (mode.kind) {
            TerminalKind.Complete -> PrintState.Complete
            TerminalKind.Cancelled -> PrintState.Cancelled
            TerminalKind.Error -> PrintState.Error
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Fine-Tune live-adjust variants (capability-gate present / absent / busy — FineTuneVm.kt)
    // ---------------------------------------------------------------------------------------------

    /**
     * Every tunable PRESENT: all capability gates true, all current values non-null + display-scaled
     * (percent values as Ints, raw motion limits as Doubles) — the "full panel" exemplar.
     */
    val fineTuneAllPresent: FineTuneVm = FineTuneVm(
        speedPct = 100,
        maxVelocity = 300.0,
        maxAccel = 3000.0,
        minCruisePct = 50,
        scv = 5.0,
        flowPct = 100,
        pressureAdvance = 0.045,
        smoothTime = 0.040,
        partFanPct = 60,
        retractLength = 0.8,
        unretractExtraLength = 0.0,
        retractSpeed = 35.0,
        unretractSpeed = 35.0,
        hasGcodeMove = true,
        hasToolhead = true,
        hasExtruder = true,
        hasFan = true,
        hasFwRetraction = true,
    )

    /**
     * FW-retraction ABSENT (the common dev-printer case): `hasFwRetraction = false` + null retraction
     * sub-values, so the FW-retraction tiles take the HIDDEN path (absent → hidden, not disabled — D-02).
     * Everything else is present.
     */
    val fineTuneNoFwRetraction: FineTuneVm = fineTuneAllPresent.copy(
        hasFwRetraction = false,
        retractLength = null,
        unretractExtraLength = null,
        retractSpeed = null,
        unretractSpeed = null,
    )

    /**
     * The whole-group BUSY lock (D-15): `groupBusy = true` over the all-present panel — every tile
     * renders its disabled/in-flight state while a state-flip is pending.
     */
    val fineTuneBusy: FineTuneVm = fineTuneAllPresent.copy(groupBusy = true)

    /** The three FineTune variants in order: present, absent (FW-retraction), busy. */
    val fineTuneVariants: List<FineTuneVm> = listOf(
        fineTuneAllPresent,
        fineTuneNoFwRetraction,
        fineTuneBusy,
    )

    // ---------------------------------------------------------------------------------------------
    // Spool list (a dense Spoolman picker fixture — SpoolmanModels.kt)
    // ---------------------------------------------------------------------------------------------

    /** A dense spool list spanning materials, colors (incl. a multi-color), vendors, and locations. */
    val spoolList: List<SpoolmanSpool> = listOf(
        spool(1, "Galaxy Black", "PLA", "#1A1A1A", "Prusament", "AMS-1", remaining = 740.0),
        spool(2, "Signal Red", "PLA", "#D32F2F", "Hatchbox", "AMS-2", remaining = 120.0),
        spool(3, "Ocean Blue", "PETG", "#1565C0", "Overture", "Shelf", remaining = 980.0),
        spool(4, "Forest Green", "PETG", "#2E7D32", "eSun", "Shelf", remaining = 55.0),
        spool(5, "Natural", "ABS", "#ECECEC", "Polymaker", "Dry-Box", remaining = 410.0),
        spool(6, "Flex Charcoal", "TPU", "#37474F", "SainSmart", "AMS-3", remaining = 300.0),
        spool(7, "Silk Gold", "PLA", "#FFC107", "Sunlu", "AMS-4", remaining = 660.0),
        spool(8, "Galaxy Twin", "PLA", "#6A1B9A,#00BCD4", "Eryone", "Shelf", remaining = 880.0),
    )

    /**
     * The [SpoolPickerState] with the Field-takeover TYPE filter picker open (23-06 FieldMode exercise).
     * The first spool is selected so the DetailCard shows a color ring + FillMeter, and the Field shows
     * the TYPE picker (material family options) in-place instead of the spool list.
     */
    val spoolWithFilterOpen: SpoolPickerState = SpoolPickerState(
        spools = spoolList,
        selected = spoolList.first(),
        fieldMode = FieldMode.FilterPicker(SpoolFilterCategory.TYPE),
    )

    /**
     * The [SpoolPickerState] with the D-08 measured-weight Field-takeover open (26-07 FieldMode exercise).
     * The first spool is selected and [FieldMode.MeasureWeight] is active, showing the inline weight-entry
     * numeric IME in place of the spool list — exercises the SpoolMeasureWeightField at design-time.
     */
    val spoolWithMeasureOpen: SpoolPickerState = SpoolPickerState(
        spools = spoolList,
        selected = spoolList.first(),
        fieldMode = FieldMode.MeasureWeight(spoolList.first()),
    )

    private fun spool(
        id: Int,
        name: String,
        material: String,
        colorHex: String,
        vendor: String,
        location: String,
        remaining: Double,
    ): SpoolmanSpool {
        val multi = colorHex.contains(',')
        return SpoolmanSpool(
            id = id,
            filament = SpoolmanFilament(
                id = id,
                name = name,
                material = material,
                colorHex = if (multi) colorHex.substringBefore(',') else colorHex,
                multiColorHexes = if (multi) colorHex else null,
                vendor = SpoolmanVendor(id = id, name = vendor),
                settingsExtruderTemp = if (material == "PLA") 210 else 240,
                settingsBedTemp = if (material == "PLA") 60 else 80,
            ),
            remainingWeight = remaining,
            location = location,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // Temperature series (a bounded sample series for the temp graph — oldest→newest)
    // ---------------------------------------------------------------------------------------------

    /**
     * A bounded nozzle-temperature series (°C, oldest→newest) — a heat-up curve from ambient toward a
     * ~215 °C target, then a plateau with small jitter. The shape a [GraphViewHost] sparkline draws.
     */
    val tempSeries: FloatArray = FloatArray(60) { i ->
        when {
            i < 30 -> 22f + (215f - 22f) * (i / 29f)            // ramp from ambient to target
            else -> 215f + (((i % 3) - 1) * 0.6f)               // plateau ±0.6 jitter
        }
    }
}
