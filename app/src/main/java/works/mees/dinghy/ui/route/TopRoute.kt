package works.mees.dinghy.ui.route

import works.mees.dinghy.state.ConnectionState
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
enum class Dest { PrintStatus, Temperature, Move, Extrude, Files, Macros, Console, Calibration, Webcam, Spool, Settings }

/**
 * PURE top-level route derivation (mirrors the `derive*` idiom in `state/DeriveCapabilities.kt`):
 * no I/O, no coroutines, no Compose — same input always yields the same output, host-unit-testable.
 *
 * Arm order is LOAD-BEARING (Codex-reviewed, 13-05 Task 3) — first-run and the klippy gate must still
 * WIN, and an auth/unreachable failure must NOT be trapped behind a bare Syncing splash:
 * - no config              → [TopRoute.Connect] (D-11),
 * - Klippy != Ready        → [TopRoute.Splash] (hard override, D-06),
 * - connection !is Connected → [TopRoute.Splash] — NEW (13-05): a socket RECONNECT (Connecting/Syncing/
 *   Disconnected/Error while config is present and klippy is otherwise Ready) now shows the full recovery
 *   Splash. This is the DELIBERATE D-05 departure (Matthew 2026-06-03): the socket [ConnectionState] now
 *   routes the recovery splash so a mid-print WiFi drop is visibly non-silent. It is SAFE only because
 *   13-05 Task 2 HOISTED the shell nav state above the Splash/Shell switch — the splash no longer bounces
 *   the user off their screen. (SplashScreen already maps Disconnected/Error → an "Unreachable" surface
 *   with Retry + "Edit connection", so a printer that is simply OFF stays reachable, not an eternal dead
 *   "Syncing" — the Settings escape is preserved on this path.)
 * - else                   → [TopRoute.Shell] of [Dest.PrintStatus] (printing AND idle resolve to the
 *   SAME surface; the screen itself adapts its content on [PrinterState.printState], D-06 — so printState
 *   is NOT a route input, only a content input the screen reads downstream).
 *
 * It reads [PrinterState.klippyState] AND [PrinterState.connection]. The perceptibility floor (a minimum
 * visible splash dwell on a FAST recovery) is NOT here — it is a [RootController]-owned UI latch (it must
 * only delay HIDING the splash, never block actual recovery), keeping this function pure.
 */
fun derive(cfgPresent: Boolean, s: PrinterState): TopRoute = when {
    !cfgPresent -> TopRoute.Connect
    s.klippyState != KlippyState.Ready -> TopRoute.Splash
    s.connection !is ConnectionState.Connected -> TopRoute.Splash
    else -> TopRoute.Shell(Dest.PrintStatus)
}
