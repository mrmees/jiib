package works.mees.dinghy.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.ui.route.TopRoute
import works.mees.dinghy.ui.route.derive
import works.mees.dinghy.ui.screen.SettingsScreen
import works.mees.dinghy.ui.screen.SplashScreen

/**
 * The SINGLE root routing authority (review #2/#11) — the SOLE place that consumes
 * [derive()][derive] and decides Splash vs Shell, AND the one owner of the controlled "open Settings
 * OUTSIDE the Shell" escape. [SplashScreen] and [AppShell] NEVER navigate themselves: Splash emits an
 * `onEditConnection` event handled here; the shell hosts in-shell Settings as a normal
 * `Dest.Settings`. This removes the original plan's dangling `onOpenSettings`-vs-`Dest.Settings`
 * ambiguity — there is exactly ONE open-Settings path, owned by this controller.
 *
 * ## Routing (off the pure [derive], D-05/D-06)
 * It collects the live [PrinterState] and `hasConfig`, computes `route = derive(hasConfig, state)`
 * (the socket [works.mees.dinghy.state.ConnectionState] NEVER routes), and owns ONE
 * `settingsEscape` flag:
 *  - `route is Connect` (first run) OR `settingsEscape` → [SettingsScreen] (the controlled escape; a
 *    successful save clears the escape).
 *  - `route is Splash` → [SplashScreen] (hard override). The AppShell/drawer is NOT composed at all
 *    while splash is showing (D-06), so the drawer is structurally unreachable during recovery. Splash
 *    only flips the root-owned escape via `onEditConnection`.
 *  - `route is Shell` → [AppShell] (the drawer hosts in-shell Settings as `Dest.Settings`).
 *
 * @param container the process-scoped service-locator (the live spine + theme + session control).
 */
@Composable
fun RootController(container: AppContainer) {
    val state by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val hasConfig by container.hasConfig.collectAsStateWithLifecycle(initialValue = false)

    // The ONE "open Settings outside the Shell" escape (review #2) — used by first-run/Connect AND by
    // the Splash "Set up / Edit connection" action. No other surface owns an open-Settings path.
    var settingsEscape by remember { mutableStateOf(false) }

    val route = derive(hasConfig, state)

    when {
        // First run (no config) OR an active escape → the ONE Settings destination, owned here.
        route is TopRoute.Connect || settingsEscape -> {
            SettingsScreen(
                container = container,
                onConnectionSaved = { settingsEscape = false },
            )
        }

        // Splash hard override (D-06): NO AppShell/drawer composed — the drawer is structurally
        // unreachable here. Splash never navigates itself; it only flips the root-owned escape.
        route is TopRoute.Splash -> {
            SplashScreen(
                container = container,
                hasConfig = hasConfig,
                state = state,
                onEditConnection = { settingsEscape = true },
            )
        }

        // The running shell (in-shell Settings is a Dest.Settings reached via the App Drawer).
        else -> {
            AppShell(container = container)
        }
    }
}
