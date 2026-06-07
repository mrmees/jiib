package works.mees.dinghy.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.ui.route.Dest
import works.mees.dinghy.ui.route.TopRoute
import works.mees.dinghy.ui.route.derive
import works.mees.dinghy.ui.screen.PrintersScreen
import works.mees.dinghy.ui.screen.SplashScreen

/**
 * The SINGLE root routing authority (review #2/#11) — the SOLE place that consumes
 * [derive()][derive] and decides Splash vs Shell, AND the one owner of the controlled "open Settings
 * OUTSIDE the Shell" escape. [SplashScreen] and [AppShell] NEVER navigate themselves: Splash emits an
 * `onEditConnection` event handled here; the shell hosts in-shell Settings as a normal
 * `Dest.Settings`. This removes the original plan's dangling `onOpenSettings`-vs-`Dest.Settings`
 * ambiguity — there is exactly ONE open-Settings path, owned by this controller.
 *
 * ## Routing (off the pure [derive], D-05-departure/D-06)
 * It collects the live [PrinterState] and `hasConfig`, computes `rawRoute = derive(hasConfig, state)`,
 * and owns ONE `settingsEscape` flag. As of 13-05 the socket
 * [works.mees.dinghy.state.ConnectionState] NOW routes the recovery Splash (a reconnect shows the full
 * Syncing splash — the deliberate D-05 departure, Matthew 2026-06-03), which is safe because the shell
 * nav state was HOISTED here (G-A1) so the splash no longer bounces the user off their screen:
 *  - `rawRoute is Connect` (first run) OR `settingsEscape` → [SettingsScreen] (the controlled escape; a
 *    successful save clears the escape). BYPASSES the Splash dwell.
 *  - the EFFECTIVE Splash (`rawRoute is Splash` OR the min-dwell floor still holding) → [SplashScreen]
 *    (hard override). The AppShell/drawer is NOT composed at all while splash is showing (D-06), so the
 *    drawer is structurally unreachable during recovery. Splash only flips the root-owned escape via
 *    `onEditConnection`. A RootController-OWNED min-dwell latch ([SPLASH_MIN_DWELL_MS]) floors the splash
 *    so a fast recovery is still perceptible (D-03) — it only delays HIDING, never the actual recovery.
 *  - else → [AppShell] (the drawer hosts in-shell Settings as `Dest.Settings`), passed the hoisted [nav].
 *
 * ## Dev-gated start_dest seed (18-04, SC-4b/D-06)
 * [startDest] is the OPTIONAL debug deep-jump target parsed by [works.mees.dinghy.MainActivity] from the
 * `start_dest` intent extra (null in release / when the dev gate is off). When non-null it seeds the
 * hoisted [ShellNavState.dest] EXACTLY ONCE here — NOT from a `LaunchedEffect` inside [AppShell], which
 * would re-fire on every recompose / recovery and fight the user's navigation (RESEARCH Q3 anti-pattern).
 * Seeding happens in the [nav] holder's initializer so it runs once at composition and never re-applies.
 * The Splash gate below still applies ON TOP (land-with-whatever-state): the user boots to Splash and
 * lands on the seeded screen once Klippy goes Ready. The gate-OFF / release path passes `startDest = null`,
 * leaving [ShellNavState.dest] at its [Dest.PrintStatus] default.
 *
 * @param container the process-scoped service-locator (the live spine + theme + session control).
 * @param startDest OPTIONAL dev-gated initial screen (null = default home); seeded once into [nav].
 */
@Composable
fun RootController(container: AppContainer, startDest: Dest? = null) {
    val state by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val hasConfig by container.hasConfig.collectAsStateWithLifecycle(initialValue = false)

    // The ONE "open Settings outside the Shell" escape (review #2) — used by first-run/Connect AND by
    // the Splash "Set up / Edit connection" action. No other surface owns an open-Settings path.
    var settingsEscape by remember { mutableStateOf(false) }

    // The shell's NAV state, HOISTED HERE (above the Splash/Shell switch) so a transient recovery Splash
    // that decomposes [AppShell] does NOT reset the user to Home (G-A1, 13-05 Task 2). [RootController]
    // stays composed across the Splash/Shell flip, so this `remember`-ed holder survives the blip.
    // The dev-gated [startDest] (null in release) seeds [ShellNavState.dest] ONCE in the holder's
    // initializer — it runs at first composition and never re-fires (18-04, SC-4b/D-06).
    val nav = rememberShellNavState(startDest)

    val rawRoute = derive(hasConfig, state)

    // ---- Minimum perceptible Splash dwell (D-03 / G-B1b, 13-05 Task 3) ----------------------------
    // A recovery can complete FASTER than the eye can catch (Matthew missed the splash on the 13-04
    // run). So the recovery Splash has a MINIMUM visible dwell: this RootController-OWNED UI latch only
    // delays HIDING the splash — it NEVER blocks actual recovery (the socket/session layer is untouched;
    // `derive` stays pure). `splashHeld` is true while the floor since the last Splash-entry has not yet
    // elapsed. On entering Splash it is set immediately; on leaving Splash it stays true for the
    // remaining floor, then clears. The Connect/Settings routes BYPASS the dwell entirely (below).
    val rawSplash = rawRoute is TopRoute.Splash
    var splashHeld by remember { mutableStateOf(false) }
    LaunchedEffect(rawSplash) {
        if (rawSplash) {
            splashHeld = true
        } else if (splashHeld) {
            // Leaving Splash: keep it visible for the floor, then hide. Re-entry cancels this (the
            // LaunchedEffect key flips), so a flapping reconnect re-arms the floor cleanly.
            delay(SPLASH_MIN_DWELL_MS)
            splashHeld = false
        }
    }

    // WR-03: when the user explicitly escapes to Settings (taps "Edit connection") DURING a sub-600ms
    // recovery dwell, the dwell intent is fulfilled — clear [splashHeld] so the dwell remainder cannot
    // re-show a ghost Splash after the subsequent Settings save (settingsEscape→false on a healthy,
    // Connected session would otherwise leave showSplash = rawSplash || splashHeld = false || true).
    // The normal recovery min-dwell (no escape) is untouched.
    LaunchedEffect(settingsEscape) {
        if (settingsEscape) splashHeld = false
    }

    // The EFFECTIVE splash = the raw route OR the held floor — but NEVER over Connect/Settings (those
    // bypass the dwell). The latch floors only the recovery Splash so it is perceptible on BOTH the
    // klippy-restart and the socket-reconnect paths.
    val showSplash = rawSplash || splashHeld

    // On RETURN from a recovery Splash (the EFFECTIVE splash is now down), clear the TRANSIENT sub-nav
    // state ([macroPopupFor]) — a half-state macro popup must not survive a reconnect — while
    // [dest]/[backStack]/[calibrationRoutine]/[macroShowSystem] are deliberately PRESERVED (G-A1: the
    // user returns to their screen, not Home).
    LaunchedEffect(showSplash) {
        if (!showSplash) nav.resetTransient()
    }

    when {
        // First run (no config) OR an active escape → the ONE connection-editing destination, owned here.
        // After the 15.2 IA dissolve, CONNECTION editing lives on the Printers screen (15.2-03 D-02) — not
        // the (now toggle-only) Settings screen — so the controlled escape lands there: a first-run user
        // gets the Add-printer tile, an "Edit connection" escape gets the printer grid + per-tile editor.
        // This BYPASSES the Splash dwell (a first-run/auth-edit escape must never be floored behind a
        // splash). A successful add/save flips `hasConfig` (derive moves off Connect); switching/backing
        // out clears the escape.
        rawRoute is TopRoute.Connect || settingsEscape -> {
            PrintersScreen(
                container = container,
                onAddPrinter = { },
                onSwitched = { settingsEscape = false },
                onBack = { settingsEscape = false },
            )
        }

        // Splash hard override (D-06): NO AppShell/drawer composed — the drawer is structurally
        // unreachable here. Splash never navigates itself; it only flips the root-owned escape. Gated on
        // the EFFECTIVE [showSplash] (raw route OR the min-dwell floor) so a fast recovery is still seen.
        showSplash -> {
            SplashScreen(
                container = container,
                hasConfig = hasConfig,
                state = state,
                onEditConnection = { settingsEscape = true },
            )
        }

        // The running shell (in-shell Settings is a Dest.Settings reached via the App Drawer). The nav
        // state is passed IN from the root-owned [nav] holder so it survives a transient Splash override.
        else -> {
            AppShell(container = container, nav = nav)
        }
    }
}

/**
 * Minimum perceptible recovery-Splash dwell (ms). A recovery that completes faster than this floor
 * still shows the Splash for the floor duration so it is never silent (D-03, 13-05 Task 3). This only
 * delays HIDING the splash — it never delays the actual reconnect/resync.
 */
private const val SPLASH_MIN_DWELL_MS = 600L
