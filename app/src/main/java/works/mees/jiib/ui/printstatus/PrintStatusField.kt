package works.mees.jiib.ui.printstatus

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.lazy.items
import works.mees.jiib.R
import works.mees.jiib.designsystem.ConfirmGuard
import works.mees.jiib.designsystem.Severity
import works.mees.jiib.designsystem.SeverityToast
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.components.ListRowLabel
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.icons.DinghyIcons
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.RegisteredRegion
import works.mees.jiib.theme.DinghyType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.ui.route.HomeAction
import works.mees.jiib.ui.route.NavDest
import works.mees.jiib.ui.spool.SpoolStatusRow

/**
 * The Standby Field — the data-driven idle action list + the neutral Preheat/System foot bar
 * + the optional failure toast.
 *
 * Replaces the old `LauncherDest` tile-grid (Phase-24 jiib redesign, D-05/D-06/D-08/D-09/D-10).
 * Renders [idleActions] (built by [works.mees.jiib.ui.route.buildIdleActions]) as a scrollable
 * [ListBlock] of [ListRow]s; capability-absent rows are absent (D-08 — never greyed). Tapping a
 * row calls [onNavigate] with the row's [NavDest]; the System foot button navigates to [NavDest.System]
 * (D-04/28-05 — formerly opened the App Drawer, now routes to the System page directly).
 *
 * The foot bar contains two [OutlinedControl]s placed below the list per the foot-of-list
 * pattern. Idle → Preheat (warn — heats) OR Cooldown (accent, when any heater is on — fires
 * TURN_OFF_HEATERS) + System (accent nav). The gutter region is retired app-wide (R1,
 * 2026-06-12): every Print-Status mode now carries its actions in a foot bar.
 *
 * This is the UNIVERSAL home Field — the data-driven idle action list + Preheat/System foot +
 * optional failure toast — rendered for EVERY printer state in the collapsed PrintStatus skeleton
 * (idle, printing, paused, terminal all share this one Field path now).
 *
 * Extracted as a named top-level composable so the ScreenScaffold `field` slot lambda body contains
 * ONLY a call to this function — creating an independently-restartable recomposition scope
 * (D-01/D-02 P0 fix).
 */
@Composable
internal fun HomeField(
    idleActions: List<HomeAction>,
    activeSpoolCardState: works.mees.jiib.ui.spool.ActiveSpoolCardState,
    failureText: String?,
    onNavigate: (NavDest) -> Unit,
    heatersRows: List<works.mees.jiib.ui.heaters.HeatersRow> = emptyList(),
    onHeatApply: (works.mees.jiib.ui.heaters.HeatDispatch) -> Unit = {},
    uDp: Dp,
    isPrinting: Boolean = false,
    isPaused: Boolean = false,
    onPause: () -> Unit = {},
    onResume: () -> Unit = {},
    onCancel: () -> Unit = {},
    isComplete: Boolean = false,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Pilot fix 2026-06-12: U is now PASSED from the SCREEN root, not derived here. Deriving it
    // from this Field-slot box gave the home list a SMALLER U than every other screen (the field
    // box ≠ the screen short edge, esp. in landscape's 50% column) and made U vary with rotation
    // — both LAYOUT.md §"The unit U" violations. One screen = one U, derived at the root.
    RegisteredRegion(modifier.fillMaxSize()) {
        var heatersOpen by remember { mutableStateOf(false) }
        // Drop the takeover if the printer leaves idle (print starts / completes) so it can't
        // silently reopen when standby returns — the Heaters button is idle-only.
        if (isPrinting || isComplete) {
            LaunchedEffect(isPrinting, isComplete) { heatersOpen = false }
        }
        if (heatersOpen && !isPrinting && !isComplete) {
            works.mees.jiib.ui.heaters.HeatersList(
                rows = heatersRows,
                onApply = onHeatApply,
                onApplied = { heatersOpen = false },
                onBack = { heatersOpen = false },
                uDp = uDp,
            )
            return@RegisteredRegion
        }

        // Data-driven idle action list (D-05/D-06) — scrollable, edge-faded, no scrollbar.
        // Each Destination row navigates; capability-absent rows are absent (D-08 HIDE, not grey).
        val loadedSpool = (activeSpoolCardState as? works.mees.jiib.ui.spool.ActiveSpoolCardState.Loaded)?.spool
        ListBlock(modifier = Modifier.weight(1f)) {
            items(
                items = idleActions.filterIsInstance<HomeAction.Destination>(),
                key = { it.dest::class.simpleName ?: it.dest.toString() },
            ) { action ->
                if (action.dest == NavDest.Spool) {
                    SpoolStatusRow(
                        spool = loadedSpool,
                        uDp = uDp,
                        onClick = { onNavigate(NavDest.Spool) },
                    )
                } else {
                    ListRow(
                        selected = false,
                        onClick = { onNavigate(action.dest) },
                        uDp = uDp,
                        leadingContent = {
                            // Leading icon — always a registered DinghyIcons token (icon law enforced
                            // by HomeAction.Destination.icon being a DinghyIcon from DinghyIcons.*).
                            // R23: canonical 0.6U list-row icon, U-relative.
                            ListRowIcon(
                                icon = action.icon,
                                uDp = uDp,
                                tint = LocalTokens.current.accent,
                            )
                        },
                    ) {
                        // Canonical list-label look — ListRowLabel (Geist SemiBold, R11 20sp default).
                        ListRowLabel(stringResource(action.labelRes))
                    }
                }
            }
        }

        failureText?.let { msg ->
            SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
        }

        // Foot bar branches on print state (R5 intents):
        //  - Printing → Pause (warn — interrupts the running process) + Cancel (danger — aborts).
        //  - Paused   → Resume (go — the expected action) + Cancel (danger).
        //  - Idle     → Preheat (warn — heats) + System (accent nav). "System" navigates to
        //    NavDest.System (D-04/28-05); while printing it lives in the list instead (buildIdleActions).
        var showCancelGuard by remember { mutableStateOf(false) }
        FootButtonBar(
            uDp = uDp,
            actions = buildList {
                if (isPrinting) {
                    if (isPaused) add(FootAction(stringResource(R.string.printstatus_foot_resume),
                        DinghyIcons.FootResume, onResume, Intent.Go))
                    else add(FootAction(stringResource(R.string.printstatus_foot_pause),
                        DinghyIcons.PauseCircle, onPause, Intent.Warn))
                    add(FootAction(stringResource(R.string.printstatus_foot_cancel),
                        DinghyIcons.FootCancel, { showCancelGuard = true }, Intent.Danger))
                } else if (isComplete) {
                    // Complete → Dismiss (clears the finished job to standby) + System nav.
                    add(FootAction(stringResource(R.string.printstatus_foot_dismiss),
                        DinghyIcons.FootDismiss, onDismiss, Intent.Go)) // R5: the expected action on a finished print
                    add(FootAction(stringResource(R.string.home_foot_system),
                        DinghyIcons.FootSystem, { onNavigate(NavDest.System) }, Intent.Accent)) // R5: plain navigation = accent
                } else {
                    // Standby: single Heaters button opens the unified Heaters takeover.
                    add(FootAction(stringResource(R.string.home_foot_heaters),
                        DinghyIcons.OutputHeater, { heatersOpen = true }, Intent.Accent))
                    add(FootAction(stringResource(R.string.home_foot_system),
                        DinghyIcons.FootSystem, { onNavigate(NavDest.System) }, Intent.Accent))
                }
            },
        )

        // Cancel confirm (destructive — aborts the print). Wrapped in a Dialog so the scrim escapes
        // the Field region and covers the whole screen (mirrors the FocusFrame e-stop guard).
        if (showCancelGuard) {
            Dialog(
                onDismissRequest = { showCancelGuard = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                ConfirmGuard(
                    title = stringResource(R.string.printstatus_cancel_guard_title),
                    message = stringResource(R.string.printstatus_cancel_guard_message),
                    confirmLabel = stringResource(R.string.printstatus_cancel_guard_confirm),
                    cancelLabel = stringResource(R.string.common_cancel),
                    onConfirm = { onCancel(); showCancelGuard = false },
                    onCancel = { showCancelGuard = false },
                    destructive = true,
                )
            }
        }
    }
}

