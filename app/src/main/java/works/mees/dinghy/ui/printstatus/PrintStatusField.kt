package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.RegisteredRegion
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.ui.route.HomeAction
import works.mees.dinghy.ui.route.NavDest

/**
 * The Standby Field — the data-driven idle action list + the neutral Preheat/System foot bar
 * + the optional failure toast.
 *
 * Replaces the old `LauncherDest` tile-grid (Phase-24 jiib redesign, D-05/D-06/D-08/D-09/D-10).
 * Renders [idleActions] (built by [works.mees.dinghy.ui.route.buildIdleActions]) as a scrollable
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
    activeSpoolCardState: works.mees.dinghy.ui.spool.ActiveSpoolCardState,
    failureText: String?,
    onNavigate: (NavDest) -> Unit,
    heatersRows: List<works.mees.dinghy.ui.heaters.HeatersRow> = emptyList(),
    onHeatApply: (works.mees.dinghy.ui.heaters.HeatDispatch) -> Unit = {},
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
        if (heatersOpen && !isPrinting && !isComplete) {
            works.mees.dinghy.ui.heaters.HeatersList(
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
        val loadedSpool = (activeSpoolCardState as? works.mees.dinghy.ui.spool.ActiveSpoolCardState.Loaded)?.spool
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

/**
 * The home Spool row's identity text: the loaded filament's `name / material / vendor` (color /
 * type / mfg), non-null/non-blank fields joined with " / ". Null when no usable field exists
 * (the caller then shows "No Spool Loaded" or the generic "Spool" label). Pure — unit-tested.
 */
internal fun spoolRowText(filament: works.mees.dinghy.spool.SpoolmanFilament?): String? =
    listOfNotNull(filament?.name, filament?.material, filament?.vendor?.name)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .ifBlank { null }

/**
 * The home Spool row (data-rich, 2026-06-16): leading [DinghyIcons.SpoolFilament] (ev_shadow) tinted
 * to the loaded filament's color (THEME-01 data carve-out — same derivation as the Spool screen
 * header), and the `name / material / vendor` identity text scrolling on overflow. Uncolored icon +
 * "No Spool Loaded" when nothing is loaded; "Spool" when a spool is loaded but carries none of the
 * identity fields. Taps through to [NavDest.Spool] in every state.
 */
@Composable
private fun SpoolStatusRow(
    spool: works.mees.dinghy.spool.SpoolmanSpool?,
    uDp: Dp,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    val spoolColor = spool?.filament?.colorSwatches?.firstNotNullOfOrNull {
        works.mees.dinghy.ui.spool.parseNormalizedHex(it)
    }
    val text = spoolRowText(spool?.filament)
    val label = when {
        text != null -> text
        spool != null -> stringResource(R.string.cd_launcher_spool) // loaded but no identity fields
        else -> stringResource(R.string.printstatus_spool_none)     // nothing loaded
    }
    ListRow(
        selected = false,
        onClick = onClick,
        uDp = uDp,
        leadingContent = {
            ListRowIcon(
                icon = DinghyIcons.SpoolFilament,
                uDp = uDp,
                tint = spoolColor ?: t.accent,
            )
        },
    ) {
        androidx.compose.material3.Text(
            text = label,
            color = t.text,
            style = DinghyType.listLabel.toTextStyle(t),
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
    }
}
