package works.mees.jiib.ui.route

import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.KlippyState
import works.mees.jiib.state.PrinterState

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

    /** Connection fault or host-not-up-yet (no live socket, or Klippy Disconnected/Startup) — the
     *  initializing/recovery surface (D-06). NOTE: Klippy Error/Shutdown with a live connection now
     *  route to [Shell], not here (2026-06-15). */
    data object Splash : TopRoute

    /** The running shell — the NavHost owns routing; the start destination is [NavDest.WaterfallHome]. */
    data object Shell : TopRoute
}

/**
 * PURE top-level route derivation (mirrors the `derive*` idiom in `state/DeriveCapabilities.kt`):
 * no I/O, no coroutines, no Compose — same input always yields the same output, host-unit-testable.
 *
 * Arm order is LOAD-BEARING (Codex-reviewed, 13-05 Task 3; re-ordered 2026-06-15) — first-run still
 * WINS, then a REAL connection fault is checked BEFORE the klippy state, and an auth/unreachable failure
 * must NOT be trapped behind a bare Syncing splash:
 * - no config              → [TopRoute.Connect] (D-11),
 * - connection !is Connected → [TopRoute.Splash] — a socket RECONNECT (Connecting/Syncing/Disconnected/
 *   Error while config is present) shows the full recovery Splash. This is the DELIBERATE D-05 departure
 *   (Matthew 2026-06-03): the socket [ConnectionState] routes the recovery splash so a mid-print WiFi drop
 *   is visibly non-silent. It is SAFE only because 13-05 Task 2 HOISTED the shell nav state above the
 *   Splash/Shell switch — the splash no longer bounces the user off their screen. (SplashScreen already
 *   maps Disconnected/Error → an "Unreachable" surface with Retry + "Edit connection", so a printer that
 *   is simply OFF stays reachable, not an eternal dead "Syncing" — the Settings escape is preserved.)
 *   This arm now PRECEDES the klippy arm so a connected Klippy fault can fall through to the shell.
 * - klippy Disconnected/Startup → [TopRoute.Splash] — the host is not up yet (D-06). Note: ONLY these two
 *   klippy states route Splash now; [KlippyState.Error] and [KlippyState.Shutdown] (with a LIVE connection)
 *   fall through to [TopRoute.Shell] — the home skeleton itself renders the Klippy fault (owner decision
 *   2026-06-15), rather than a hard Splash override.
 * - else                   → [TopRoute.Shell] of [Dest.PrintStatus] — Ready, Error, AND Shutdown (when
 *   connected) all resolve to the SAME home surface; the screen adapts its content on [PrinterState] (the
 *   printState/klippyState are content inputs the screen reads downstream, NOT route inputs here, D-06).
 *
 * It reads [PrinterState.klippyState] AND [PrinterState.connection]. The perceptibility floor (a minimum
 * visible splash dwell on a FAST recovery) is NOT here — it is a [RootController]-owned UI latch (it must
 * only delay HIDING the splash, never block actual recovery), keeping this function pure.
 */
fun derive(cfgPresent: Boolean, s: PrinterState): TopRoute = when {
    !cfgPresent -> TopRoute.Connect
    s.connection !is ConnectionState.Connected -> TopRoute.Splash      // real connection fault
    s.klippyState == KlippyState.Disconnected ||
        s.klippyState == KlippyState.Startup -> TopRoute.Splash         // host not up yet
    else -> TopRoute.Shell                                              // Ready, Error, Shutdown → home
}
