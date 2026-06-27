package works.mees.jiib.preview

import works.mees.jiib.calibration.BedMeshModel
import works.mees.jiib.calibration.BedMeshVm
import works.mees.jiib.calibration.CalibrationRoutine
import works.mees.jiib.calibration.GuidedLoopState
import works.mees.jiib.calibration.ProbeCalibrateVm
import works.mees.jiib.calibration.ProbePageState
import works.mees.jiib.calibration.RoutineEntry
import works.mees.jiib.calibration.ScrewPoint
import works.mees.jiib.calibration.ScrewTurn
import works.mees.jiib.calibration.ScrewsTiltVm
import works.mees.jiib.calibration.TiltState
import works.mees.jiib.calibration.TiltVm
import works.mees.jiib.calibration.ZAdjustment
import works.mees.jiib.render.BedMeshHeatmapView
import works.mees.jiib.ui.calibration.MeshFieldMode
import works.mees.jiib.ui.calibration.TiltVariant
import works.mees.jiib.config.Profile
import works.mees.jiib.spool.SpoolmanFilament
import works.mees.jiib.spool.SpoolmanSpool
import works.mees.jiib.spool.SpoolmanVendor
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import works.mees.jiib.state.HeaterState
import works.mees.jiib.state.KlippyState
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.ui.finetune.FineTuneVm
import works.mees.jiib.ui.spool.FieldMode
import works.mees.jiib.ui.spool.SpoolFilterCategory
import works.mees.jiib.ui.spool.SpoolPickerState

/**
 * The reusable, PURE, immutable fake-state library (SC-2) every preview exemplar consumes — and the
 * SAME seed the Phase-22 backfill reuses, so these are intentionally NOT per-preview one-offs.
 *
 * ⚠ NO live Moonraker, NO network, NO coroutines: these are plain immutable values. The analog is
 * [works.mees.jiib.bench.SyntheticFeed] (deterministic on-device fixtures) — same discipline, but
 * for host-rendered previews. A consumer wraps any of these in [PreviewBox] to render a screen at a
 * known state without a printer.
 */
object SampleFixtures {

    // ---------------------------------------------------------------------------------------------
    // Print-Status states (the single collapsed home path — PrintStatusScreen.kt)
    // ---------------------------------------------------------------------------------------------

    /** All six raw [PrintState] values — the home renders ONE path for each. */
    val printStates: List<PrintState> = listOf(
        PrintState.Standby,
        PrintState.Printing,
        PrintState.Paused,
        PrintState.Complete,
        PrintState.Cancelled,
        PrintState.Error,
    )

    /**
     * Build a [PrinterState] in the given print [s]tate. The collapsed PrintStatus home renders ONE
     * path off [PrinterState.printState] (plus [PrinterState.klippyState] for the title), so a fixture
     * that sets the matching `printState` reproduces every home state deterministically. A filename is
     * supplied for the active/paused states so the Focus has a job to present; defaults fill the rest.
     */
    fun forState(s: PrintState): PrinterState {
        val printing = s == PrintState.Printing || s == PrintState.Paused
        val hasJob = printing || s == PrintState.Complete   // Complete shows the finished job's block too
        return PrinterState(
            printState = s,
            printFilename = if (hasJob) "benchy.gcode" else "",
            klippyState = KlippyState.Ready,
            progress = if (printing) 0.42 else if (s == PrintState.Complete) 1.0 else 0.0,
            printDuration = if (hasJob) 2700.0 else 0.0,    // 45m
            totalDuration = if (hasJob) 3120.0 else 0.0,    // 52m
            currentLayer = if (hasJob) (if (s == PrintState.Complete) 220 else 5) else null,
            totalLayer = if (hasJob) 220 else null,
            filamentUsed = if (hasJob) 4200.0 else 0.0,    // 4.2m
            gcodePosition = if (hasJob) persistentListOf(0.0, 0.0, 1.2, 0.0) else null,
            heaters = if (hasJob) {
                persistentMapOf(
                    "extruder" to HeaterState(temperature = 229.6, target = 230.0),
                    "heater_bed" to HeaterState(temperature = 75.2, target = 75.0),
                )
            } else {
                persistentMapOf()
            },
        )
    }

    /** A Klipper-host SHUTDOWN fixture — exercises the host-fault home title (homeStateLabelRes). */
    fun klippyShutdown(): PrinterState = PrinterState(klippyState = KlippyState.Shutdown)

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
     * The first spool is selected so the FocusFrame shows a color ring + FillMeter, and the Field shows
     * the TYPE picker (material family options) in-place instead of the spool list.
     */
    val spoolWithFilterOpen: SpoolPickerState = SpoolPickerState(
        spools = spoolList,
        selected = spoolList.first(),
        fieldMode = FieldMode.FilterPicker(SpoolFilterCategory.TYPE),
        availableMaterialFamilies = listOf("PLA", "PETG", "ABS/ASA"),
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

    // ---------------------------------------------------------------------------------------------
    // Calibration hub fixtures (all 5 routines, supported-first per CalibrationGate order — 27-04)
    // ---------------------------------------------------------------------------------------------

    /**
     * All 5 calibration routines in supported-first order matching [works.mees.jiib.calibration.calibrationSupport]:
     * PROBE_CALIBRATE, BED_MESH, SCREWS_TILT are supported; Z_TILT and QUAD_GANTRY_LEVEL are greyed
     * (unsupported — D-06: all 5 always render, unsupported dimmed `t.text3` but still selectable).
     *
     * The E3/E5 test printer profile (no `z_tilt` / no `quad_gantry_level` objects). Mirrors the
     * real-device fixture that the on-device UAT (27-07) will exercise.
     */
    val calibrationRoutineList: List<RoutineEntry> = listOf(
        RoutineEntry(CalibrationRoutine.PROBE_CALIBRATE, isSupported = true),
        RoutineEntry(CalibrationRoutine.BED_MESH, isSupported = true),
        RoutineEntry(CalibrationRoutine.SCREWS_TILT, isSupported = true),
        RoutineEntry(CalibrationRoutine.Z_TILT, isSupported = false),
        RoutineEntry(CalibrationRoutine.QUAD_GANTRY_LEVEL, isSupported = false),
    )

    /**
     * Probe tool hub list fixture (Task 14 — ProbeHubContent previews).
     *
     * E3/E5 test printer profile: Z_OFFSET (manual_probe), PROBE_TEST (probe), and
     * APPLY_BABYSTEP (gcode_move) are supported; the three EDDY_* tools are unsupported
     * (no eddy probe on these printers). Supported tools sort first (D-06 convention).
     *
     * The [showUnsupportedTools] flag is true in preview so all six rows render — unsupported
     * entries are visible but dimmed, exercising the D-06 greyed-but-listed path.
     */
    val probeToolList: List<works.mees.jiib.calibration.ProbeToolEntry> = listOf(
        works.mees.jiib.calibration.ProbeToolEntry(works.mees.jiib.calibration.ProbeTool.Z_OFFSET, isSupported = true),
        works.mees.jiib.calibration.ProbeToolEntry(works.mees.jiib.calibration.ProbeTool.PROBE_TEST, isSupported = true),
        works.mees.jiib.calibration.ProbeToolEntry(works.mees.jiib.calibration.ProbeTool.APPLY_BABYSTEP, isSupported = true),
        works.mees.jiib.calibration.ProbeToolEntry(works.mees.jiib.calibration.ProbeTool.EDDY_CALIBRATE, isSupported = false),
        works.mees.jiib.calibration.ProbeToolEntry(works.mees.jiib.calibration.ProbeTool.EDDY_TAP, isSupported = false),
        works.mees.jiib.calibration.ProbeToolEntry(works.mees.jiib.calibration.ProbeTool.EDDY_DRIVE_CURRENT, isSupported = false),
    )

    // ---------------------------------------------------------------------------------------------
    // ProbeCalibrateContent fixtures (per-state stub snapshots — no live VM — 27-04)
    // ---------------------------------------------------------------------------------------------

    /**
     * A [ProbeCalibrateVm] snapshot for the given [state] (no live Moonraker, no holder).
     *
     * Active supplies a representative live-Z position (the klicky-probe paper-test sweet spot).
     * Accepted supplies a captured offset so the Focus card's delta text is non-empty.
     *
     * @param state         the [ProbePageState] to render (Idle / Active / Accepted).
     * @param homedGate     true when all axes are homed — only meaningful for [ProbePageState.Idle].
     */
    // ---------------------------------------------------------------------------------------------
    // BedMesh preview fixtures (Field profile-list + SaveName-takeover states — 27-05)
    // ---------------------------------------------------------------------------------------------

    /**
     * A [BedMeshVm] snapshot for the given preview scenario.
     *
     * - [profiles] the saved-profile names shown in the Field list.
     * - [activeProfile] the currently loaded mesh profile name (`""` = no active mesh).
     * - [homed] whether all axes are homed (drives the D-14 foot branch).
     */
    fun bedMeshVm(
        profiles: List<String> = listOf("default", "25.06.11_09.30", "adaptive"),
        activeProfile: String = "default",
        homed: Boolean = true,
    ): BedMeshVm {
        // Build a BedMeshModel that has the right profileNames + a non-empty (loaded) mesh when
        // activeProfile is non-blank so that isEmpty returns false and the heatmap Focus branch renders.
        val model = if (activeProfile.isNotEmpty()) {
            BedMeshModel(
                profileName = activeProfile,
                // A tiny 2×2 meshMatrix so isEmpty is false (non-empty matrix + non-empty profileName).
                meshMatrix = listOf(listOf(-0.05, 0.03), listOf(0.01, -0.02)),
                profileNames = profiles,
            )
        } else {
            // No active mesh (isEmpty = true): the Focus shows the empty-state copy.
            BedMeshModel(profileNames = profiles)
        }
        return BedMeshVm(model = model, homed = homed, scaleMode = BedMeshHeatmapView.ScaleMode.RELATIVE)
    }

    /**
     * A [BedMeshVm] for the empty-profiles / pre-calibration scenario (no saved profiles, no active mesh,
     * unhomed). The Field shows the empty-state copy; the foot shows Home All + Back.
     */
    val bedMeshEmpty: BedMeshVm = BedMeshVm(
        model = BedMeshModel(),   // profileNames = emptyList(), isEmpty = true
        homed = false,
    )

    /**
     * A [BedMeshVm] with profiles but no active mesh loaded (homed, no loaded profile). The Focus shows
     * the empty-state copy ("No active mesh"); the Field shows the profile list; foot = Calibrate+Save+Back.
     */
    val bedMeshProfilesNoActive: BedMeshVm = BedMeshVm(
        model = BedMeshModel(profileNames = listOf("default", "25.06.11_09.30", "adaptive")),
        homed = true,
    )

    fun probeVm(
        state: ProbePageState,
        homedGate: Boolean = true,
    ): ProbeCalibrateVm = when (state) {
        ProbePageState.Idle -> ProbeCalibrateVm(
            state = ProbePageState.Idle,
            savedZOffset = 1.425,   // typical klicky probe z_offset (mm, positive)
            homedGate = homedGate,
        )
        ProbePageState.Active -> ProbeCalibrateVm(
            state = ProbePageState.Active,
            zPosition = 0.050,      // paper-test gap: mid-descent, ~0.05 mm above bed
            savedZOffset = 1.425,
            homedGate = true,
        )
        ProbePageState.Accepted -> ProbeCalibrateVm(
            state = ProbePageState.Accepted,
            capturedOffset = 0.023, // accepted paper-test result
            savedZOffset = 1.425,
            homedGate = true,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // ScrewsTiltContent fixtures (idle + result snapshots — 27-06)
    // ---------------------------------------------------------------------------------------------

    /**
     * A [ScrewsTiltVm] idle snapshot (no results yet, all axes homed). The bed visualization
     * shows the 4-screw layout at their real positions; the Field list shows "—" placeholders.
     */
    val screwsTiltIdle: ScrewsTiltVm = ScrewsTiltVm(
        loop = GuidedLoopState(),
        points = listOf(
            ScrewPoint(key = "screw1", index = 1, name = "front left",  x = 30.0,  y = 30.0),
            ScrewPoint(key = "screw2", index = 2, name = "front right", x = 200.0, y = 30.0),
            ScrewPoint(key = "screw3", index = 3, name = "rear right",  x = 200.0, y = 200.0),
            ScrewPoint(key = "screw4", index = 4, name = "rear left",   x = 30.0,  y = 200.0),
        ),
        hasCoords = true,
        homedGate = true,
        errorText = null,
    )

    /**
     * A [ScrewsTiltVm] result snapshot — 4 screws measured, front-left is the base reference,
     * rear-right needs the largest turn. The Field list shows turn instructions in the trailing slot.
     */
    val screwsTiltResult: ScrewsTiltVm = run {
        fun turn(key: String, idx: Int, name: String, x: Double, y: Double,
                 z: Double, sign: String?, adjust: String, isBase: Boolean = false): ScrewPoint {
            val adjustSecs = if (adjust == "00:00") 0 else {
                val parts = adjust.split(":"); parts[0].toInt() * 60 + parts[1].toInt()
            }
            val deg = adjustSecs * 6.0
            val t = ScrewTurn(key, idx, name, z = z, sign = sign, adjust = adjust,
                adjustSeconds = adjustSecs, degrees = deg,
                isInTol = adjust == "00:00", isBase = isBase)
            return ScrewPoint(key, idx, name, x, y, t)
        }
        val points = listOf(
            turn("screw1", 1, "front left",  30.0,  30.0,  1.25, null, "00:00", isBase = true),
            turn("screw2", 2, "front right", 200.0, 30.0,  1.17, "CW", "00:05"),
            turn("screw3", 3, "rear right",  200.0, 200.0, 0.97, "CW", "01:45"),
            turn("screw4", 4, "rear left",   30.0,  200.0, 1.20, "CCW","00:03"),
        )
        val screws = points.mapNotNull { it.turn }
        ScrewsTiltVm(
            loop = GuidedLoopState(
                screws = screws,
                worstScrew = screws.find { it.key == "screw3" },
                inToleranceCount = 2,
                totalScrews = 4,
                error = false,
            ),
            points = points,
            hasCoords = true,
            homedGate = true,
            errorText = null,
        )
    }

    // ---------------------------------------------------------------------------------------------
    // TiltContent fixtures (idle / running / result snapshots — 27-06)
    // ---------------------------------------------------------------------------------------------

    /**
     * A [TiltVm] + [TiltState] snapshot pair for the given [state].
     *
     * @param variant  the [TiltVariant] (ZTilt or Qgl) — drives the title string.
     * @param state    the [TiltState] to render (Idle / Running / Done / Failed).
     * @param homedGate whether all axes are homed (only meaningful for [TiltState.Idle]).
     */
    fun tiltContent(
        variant: TiltVariant = TiltVariant.ZTilt,
        state: TiltState = TiltState.Idle,
        homedGate: Boolean = true,
    ): TiltVm = when (state) {
        TiltState.Idle -> TiltVm(ran = false, homedGate = homedGate)
        TiltState.Running -> TiltVm(ran = true, homedGate = true)  // running derives from in-flight
        TiltState.Done -> TiltVm(
            ran = true,
            homedGate = true,
            adjustments = listOf(
                ZAdjustment("stepper_z",  0.0),
                ZAdjustment("stepper_z1", -0.0523),
                ZAdjustment("stepper_z2", 0.0312),
            ),
        )
        TiltState.Failed -> TiltVm(
            ran = true,
            homedGate = true,
            failed = true,
            errorText = "bed level exceeds configured limits",
        )
    }

    // ---------------------------------------------------------------------------------------------
    // SystemPageContent fixtures (28-02)
    // ---------------------------------------------------------------------------------------------

    /**
     * App version string used by [SystemPageContent] preview panels.
     * Mirrors [BuildConfig.VERSION_NAME] shape at preview time; static fixture.
     */
    const val systemPageVersion: String = "0.1.0-debug"

    // ---------------------------------------------------------------------------------------------
    // Printers screen fixtures (28-06, D-15)
    // 2 profiles: the first (id="fixture-ender5") is the active one; the second is inactive.
    // Built against the post-28-04 Profile shape (no maxItems field).
    // ---------------------------------------------------------------------------------------------

    /** The stable ID for the active-printer fixture (Ender 5 Plus). */
    const val printerActiveId: String = "fixture-ender5"

    /** 2 printer profiles — one active, one inactive. Pure immutable data; no network. */
    val printerProfileList: List<Profile> = listOf(
        Profile(
            id = printerActiveId,
            name = "Ender 5 Plus",
            host = "192.168.1.120",
            port = 7125,
        ),
        Profile(
            id = "fixture-ender3",
            name = "Ender 3 Pro",
            host = "192.168.1.121",
            port = 7125,
        ),
    )
}
