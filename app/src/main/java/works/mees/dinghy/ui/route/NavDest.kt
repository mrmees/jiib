package works.mees.dinghy.ui.route

import kotlinx.serialization.Serializable

/**
 * Type-safe Navigation-Compose route hierarchy for the in-shell destinations (Phase 24, D-01).
 *
 * Replaces the [Dest] enum with `@Serializable data object` members so Navigation-Compose can
 * serialize/deserialize them as back-stack entries. The 17 destinations carry over verbatim from
 * [Dest], with [Dest.PrintStatus] renamed to [WaterfallHome] (the morphing root, 24-CONTEXT D-01).
 *
 * ## Iteration
 * Sealed interfaces have no `.entries` (unlike enums). Use [knownNavDests] for iteration,
 * round-trip testing, and the [parseStartDest] safe-parse in [StartDestMapping].
 *
 * ## FOOT_GUN_DESTS / shouldPopToRoot (D-04)
 * The three printer-control destinations that become dangerous if a print starts while the user
 * is inside them (Move, Extrude, Calibration) are collected as [FOOT_GUN_DESTS]. The
 * [shouldPopToRoot] pure predicate checks membership — host-testable with NO NavHost, NO
 * TestNavController (FIX-8: a pure Kotlin predicate in src/test, not an instrumented Android test).
 */
@Serializable
sealed interface NavDest {
    @Serializable data object WaterfallHome  : NavDest   // renamed from Dest.PrintStatus (D-01)
    @Serializable data object Temperature    : NavDest
    @Serializable data object Move           : NavDest
    @Serializable data object Extrude        : NavDest
    @Serializable data object Files          : NavDest
    @Serializable data object Macros         : NavDest
    @Serializable data object Console        : NavDest
    @Serializable data object Calibration    : NavDest
    @Serializable data object FineTune       : NavDest
    @Serializable data object Webcam         : NavDest
    @Serializable data object Spool          : NavDest
    @Serializable data object Outputs        : NavDest
    @Serializable data object SystemInfo     : NavDest
    @Serializable data object Devices        : NavDest
    @Serializable data object Theme          : NavDest
    @Serializable data object Settings       : NavDest
    @Serializable data object About          : NavDest
}

/**
 * All 17 [NavDest] members in declaration order.
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
    NavDest.Calibration,
    NavDest.FineTune,
    NavDest.Webcam,
    NavDest.Spool,
    NavDest.Outputs,
    NavDest.SystemInfo,
    NavDest.Devices,
    NavDest.Theme,
    NavDest.Settings,
    NavDest.About,
)

// ---------------------------------------------------------------------------
// D-04: FOOT-GUN destinations + pure pop-to-root predicate
// ---------------------------------------------------------------------------

/**
 * The three in-shell destinations that become hazardous mid-print:
 * - **Move** — physically moves print head; wrong during active printing
 * - **Extrude** — extrudes/retracts filament; wrong mid-print
 * - **Calibration** — bed/probe routines; wrong mid-print
 *
 * When a print STARTS (or the state transitions in a way that makes these dangerous), the
 * AppShell's [LaunchedEffect] uses [shouldPopToRoot] to pop the user back to [NavDest.WaterfallHome].
 * Screens that are valid mid-print (Temperature, Macros, Fine-Tune, Console, Webcam) are intentionally
 * NOT in this set — D-04 only pops foot-gun destinations, never valid-mid-print ones.
 */
val FOOT_GUN_DESTS: Set<NavDest> = setOf(
    NavDest.Move,
    NavDest.Extrude,
    NavDest.Calibration,
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
