package works.mees.dinghy.ui.route

import kotlinx.serialization.Serializable
import works.mees.dinghy.calibration.CalibrationRoutine

/**
 * Type-safe Navigation-Compose route hierarchy for the in-shell destinations (Phase 24, D-01).
 *
 * Replaces the [Dest] enum with `@Serializable data object` members so Navigation-Compose can
 * serialize/deserialize them as back-stack entries. The 17 destinations carry over verbatim from
 * [Dest], with [Dest.PrintStatus] renamed to [WaterfallHome] (the morphing root, 24-CONTEXT D-01).
 *
 * ## Calibration sub-routes (Phase 27, D-07)
 * [NavDest.Calibration] is renamed to [CalibrationHub] and expanded to 6 members — one hub + five
 * routine sub-destinations — replacing the old in-screen `when(calibrationRoutine)` dispatch.
 * The NavHost back-stack is now the SINGLE source of truth for the active calibration screen.
 *
 * ## System cluster (Phase 28, D-01)
 * [NavDest.System] is the new System page that replaces the retired drawer-as-system-hub. It is
 * intentionally mid-print reachable (D-06) and therefore NOT in [FOOT_GUN_DESTS].
 *
 * ## Iteration
 * Sealed interfaces have no `.entries` (unlike enums). Use [knownNavDests] for iteration,
 * round-trip testing, and the [parseStartDest] safe-parse in [StartDestMapping].
 *
 * ## FOOT_GUN_DESTS / shouldPopToRoot (D-04)
 * The destinations that become dangerous if a print starts while the user is inside them
 * (Move, Extrude, and all six CalibrationXxx routes) are collected as [FOOT_GUN_DESTS]. The
 * [shouldPopToRoot] pure predicate checks membership — host-testable with NO NavHost, NO
 * TestNavController (FIX-8: a pure Kotlin predicate in src/test, not an instrumented Android test).
 */
@Serializable
sealed interface NavDest {
    @Serializable data object WaterfallHome          : NavDest   // renamed from Dest.PrintStatus (D-01)
    @Serializable data object Temperature            : NavDest
    @Serializable data object Move                   : NavDest
    @Serializable data object Extrude                : NavDest
    @Serializable data object Files                  : NavDest
    @Serializable data object Macros                 : NavDest
    @Serializable data object Console                : NavDest
    // Calibration sub-routes (D-07, Phase 27): Calibration → CalibrationHub + 5 routine dests
    @Serializable data object CalibrationHub         : NavDest
    @Serializable data object CalibrationProbe       : NavDest
    @Serializable data object CalibrationBedMesh     : NavDest
    @Serializable data object CalibrationScrewsTilt  : NavDest
    @Serializable data object CalibrationZTilt       : NavDest
    @Serializable data object CalibrationQgl         : NavDest
    @Serializable data object FineTune               : NavDest
    @Serializable data object Webcam                 : NavDest
    @Serializable data object Spool                  : NavDest
    @Serializable data object Outputs                : NavDest
    @Serializable data object SystemInfo             : NavDest
    @Serializable data object Theme                  : NavDest
    @Serializable data object About                  : NavDest
    @Serializable data object System                 : NavDest   // System page hub (D-01, Phase 28)
    // Settings split (app-printer split task 2.1)
    @Serializable data object AppSettings            : NavDest
    @Serializable data object PrinterSettings        : NavDest
    @Serializable data object ManagePrinters         : NavDest
    @Serializable data object HeatPresets            : NavDest
}

/**
 * All 24 [NavDest] members in declaration order (17 original + 5 new calibration sub-routes D-07
 * + 1 System page hub D-01/Phase 28 + 3 settings-split routes AppSettings/PrinterSettings/ManagePrinters
 * − 2 retired routes Settings/Devices removed in task 7.1).
 *
 * Sealed interfaces have no `.entries` — use this list for round-trip testing ([parseStartDest]),
 * verification coverage, and any place that previously iterated [Dest.entries].
 */
val knownNavDests: List<NavDest> = listOf(
    NavDest.WaterfallHome,
    NavDest.Temperature,
    NavDest.Move,
    NavDest.Extrude,
    NavDest.Files,
    NavDest.Macros,
    NavDest.Console,
    NavDest.CalibrationHub,
    NavDest.CalibrationProbe,
    NavDest.CalibrationBedMesh,
    NavDest.CalibrationScrewsTilt,
    NavDest.CalibrationZTilt,
    NavDest.CalibrationQgl,
    NavDest.FineTune,
    NavDest.Webcam,
    NavDest.Spool,
    NavDest.Outputs,
    NavDest.SystemInfo,
    NavDest.Theme,
    NavDest.About,
    NavDest.System,           // Phase 28 D-01: System page hub
    // Settings split (task 2.1); count = 24 (Settings + Devices retired in task 7.1)
    NavDest.AppSettings,
    NavDest.PrinterSettings,
    NavDest.ManagePrinters,
    NavDest.HeatPresets,
)

// ---------------------------------------------------------------------------
// D-07 helper: CalibrationRoutine → NavDest mapping
// ---------------------------------------------------------------------------

/**
 * Maps a [CalibrationRoutine] to its [NavDest] sub-route (D-07, Phase 27).
 *
 * Used by AppShell's CalibrationHub composable `onOpen` lambda to navigate to the selected routine
 * via [navController.navigate(routine.toNavDest())].
 */
fun CalibrationRoutine.toNavDest(): NavDest = when (this) {
    CalibrationRoutine.PROBE_CALIBRATE   -> NavDest.CalibrationProbe
    CalibrationRoutine.BED_MESH          -> NavDest.CalibrationBedMesh
    CalibrationRoutine.SCREWS_TILT       -> NavDest.CalibrationScrewsTilt
    CalibrationRoutine.Z_TILT            -> NavDest.CalibrationZTilt
    CalibrationRoutine.QUAD_GANTRY_LEVEL -> NavDest.CalibrationQgl
}

// ---------------------------------------------------------------------------
// D-04: FOOT-GUN destinations + pure pop-to-root predicate
// ---------------------------------------------------------------------------

/**
 * The in-shell destinations that become hazardous mid-print:
 * - **Move** — physically moves print head; wrong during active printing
 * - **Extrude** — extrudes/retracts filament; wrong mid-print
 * - **CalibrationHub** — calibration routine hub; wrong mid-print
 * - **CalibrationProbe / BedMesh / ScrewsTilt / ZTilt / Qgl** — live calibration routines; wrong mid-print
 *
 * All six CalibrationXxx routes are included so pop-to-root on print start covers the whole
 * calibration sub-tree (D-17). A print starting while the user is in ANY routine will pop to root.
 *
 * When a print STARTS (or the state transitions in a way that makes these dangerous), the
 * AppShell's [LaunchedEffect] uses [shouldPopToRoot] to pop the user back to [NavDest.WaterfallHome].
 * Screens that are valid mid-print (Temperature, Macros, Fine-Tune, Console, Webcam) are intentionally
 * NOT in this set — D-04 only pops foot-gun destinations, never valid-mid-print ones.
 *
 * [NavDest.System] and all System-cluster sub-screens are intentionally absent: the whole cluster is
 * mid-print reachable (D-06, Phase 28). The `else -> null` fallback in [shouldPopToRoot] covers them.
 */
val FOOT_GUN_DESTS: Set<NavDest> = setOf(
    NavDest.Move,
    NavDest.Extrude,
    NavDest.CalibrationHub,
    NavDest.CalibrationProbe,
    NavDest.CalibrationBedMesh,
    NavDest.CalibrationScrewsTilt,
    NavDest.CalibrationZTilt,
    NavDest.CalibrationQgl,
)

/**
 * PURE predicate (host-testable, NO NavHost, NO NavController required — FIX-8):
 * returns `true` if [current] is a [FOOT_GUN_DESTS] destination that should be popped on a
 * print-state transition.
 *
 * The [printActive] parameter documents the call-site contract: the AppShell [LaunchedEffect] fires
 * on `printState` changes and passes the new print-active flag. The predicate itself returns `true`
 * for ANY foot-gun current destination — the decision of WHEN to call it (only on a transition into
 * printing) is the caller's responsibility. This separation keeps the predicate pure and leaves room
 * for a future "only pop when ENTERING printing" tightening without changing the test surface.
 *
 * @param current the destination currently on top of the back-stack (null = none / at root)
 * @param printActive whether a print is currently active (documents caller contract; not used
 *   directly in the predicate body — the AppShell LaunchedEffect already fires only on a
 *   printState change, so the caller controls the trigger, not this function)
 * @return `true` if [current] is in [FOOT_GUN_DESTS] and should be popped to root
 */
fun shouldPopToRoot(current: NavDest?, printActive: Boolean): Boolean =
    current != null && current in FOOT_GUN_DESTS
