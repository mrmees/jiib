package works.mees.dinghy.ui.route

import works.mees.dinghy.state.KlippyState
import works.mees.dinghy.state.PrinterState

/**
 * The three top-level destinations the root controller (MainActivity, 04-07) branches on. This is
 * the SINGLE source of truth for top-level routing (review HIGH #2): `SplashScreen` and `AppShell`
 * do NOT route themselves — Splash emits an open-settings event the root controller handles, and the
 * shell hosts [Dest.Settings]. [Connect] is the first-run escape into Settings the root controller
 * owns (it is NOT a competing router).
 */
sealed interface TopRoute {
    /** First run / no saved connection config — the root controller opens Settings (D-11). */
    data object Connect : TopRoute

    /** Klippy is not Ready — a hard override initializing/recovery surface (D-06). */
    data object Splash : TopRoute

    /** The running shell, showing [dest]. */
    data class Shell(val dest: Dest) : TopRoute
}

/**
 * In-shell destinations reachable once Klippy is Ready. Print Status is the single home surface in
 * Phase 4 (there is deliberately NO `Dest.Job` — printing and idle share PrintStatus, content adapts
 * on `printState` inside the screen, D-06); Settings is reachable from the App Drawer. Extra panels
 * are a one-line addition later (D-05).
 */
enum class Dest { PrintStatus, Temperature, Move, Extrude, Files, Macros, Console, Settings }

/**
 * PURE top-level route derivation (mirrors the `derive*` idiom in `state/DeriveCapabilities.kt`):
 * no I/O, no coroutines, no Compose — same input always yields the same output, host-unit-testable.
 *
 * Routes off the Klippy LIFECYCLE only:
 * - no config        → [TopRoute.Connect] (D-11),
 * - Klippy != Ready  → [TopRoute.Splash] (hard override, D-06),
 * - else             → [TopRoute.Shell] of [Dest.PrintStatus] (printing AND idle resolve to the SAME
 *   surface; the screen itself adapts its content on [PrinterState.printState], D-06 — so printState
 *   is NOT a route input, only a content input the screen reads downstream).
 *
 * It reads ONLY [PrinterState.klippyState]; it NEVER reads `s.connection` (D-05) — the socket
 * [works.mees.dinghy.state.ConnectionState] is chrome, so a transient reconnect cannot bounce the
 * user off the home screen.
 */
fun derive(cfgPresent: Boolean, s: PrinterState): TopRoute = when {
    !cfgPresent -> TopRoute.Connect
    s.klippyState != KlippyState.Ready -> TopRoute.Splash
    else -> TopRoute.Shell(Dest.PrintStatus)
}
