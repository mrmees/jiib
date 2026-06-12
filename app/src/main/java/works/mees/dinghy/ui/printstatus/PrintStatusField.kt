package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.remember
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableList
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.state.PrintMetadata
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.theme.seriesColor
import works.mees.dinghy.ui.route.HomeAction
import works.mees.dinghy.ui.route.NavDest
import works.mees.dinghy.ui.spool.ActiveSpoolCardState

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
 * The foot bar contains exactly two [Intent.Neutral] [OutlinedControl]s — Preheat and System —
 * placed below the list per the foot-of-list pattern (the gutter is gone from this screen;
 * ScreenScaffold is called with `gutter = null` from the printing/terminal modes ONLY when
 * needed; the Standby root has no gutter slot per D-03/SC-3).
 *
 * Extracted as a named top-level composable so the Standby ScreenScaffold `field` slot lambda
 * body contains ONLY a call to this function — creating an independently-restartable recomposition
 * scope (D-01/D-02 P0 fix).
 */
@Composable
internal fun PrintStatusStandbyField(
    idleActions: List<HomeAction>,
    failureText: String?,
    onNavigate: (NavDest) -> Unit,
    onPreheat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        Column(
            Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Data-driven idle action list (D-05/D-06) — scrollable, edge-faded, no scrollbar.
            // Each Destination row navigates; capability-absent rows are absent (D-08 HIDE, not grey).
            ListBlock(modifier = Modifier.weight(1f)) {
                items(
                    items = idleActions.filterIsInstance<HomeAction.Destination>(),
                    key = { it.dest::class.simpleName ?: it.dest.toString() },
                ) { action ->
                    ListRow(
                        selected = false,
                        onClick = { onNavigate(action.dest) },
                        uDp = grid.uDp,
                        leadingContent = {
                            // Leading icon — always a registered DinghyIcons token (icon law enforced
                            // by HomeAction.Destination.icon being a DinghyIcon from DinghyIcons.*).
                            // Gap after the icon is OWNED by ListRow (12dp standard anatomy).
                            DinghyIconView(
                                icon = action.icon,
                                tint = LocalTokens.current.text2,
                                sizeDp = fsSp(22f, LocalTokens.current.fs).dp,
                            )
                        },
                    ) {
                        // Canonical list-label look — ListRowLabel (Geist SemiBold, R11 20sp default).
                        ListRowLabel(stringResource(action.labelRes))
                    }
                }
            }

            failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }

            // Idle foot bar: Preheat + System — both Intent.Neutral (D-09/D-10).
            // "System" navigates to NavDest.System (D-04/28-05 — formerly opened the App Drawer).
            // NOT red, NOT Power.
            FootButtonBar(uDp = grid.uDp) {
                OutlinedControl(
                    label = stringResource(R.string.home_foot_preheat),
                    onClick = onPreheat,
                    modifier = Modifier.weight(1f),
                    icon = DinghyIcons.FootPreheat,
                    intent = Intent.Neutral,
                )
                OutlinedControl(
                    label = stringResource(R.string.home_foot_system),
                    onClick = { onNavigate(NavDest.System) },
                    modifier = Modifier.weight(1f),
                    icon = DinghyIcons.FootSystem,
                    intent = Intent.Neutral,
                )
            }
        }
    }
}

/**
 * The active-print Field — ONE framed StatGrid + the shortcut OR babystep row + optional Spoolman
 * line + the failure toast. Shared by Printing AND Paused (Paused reuses the same toolset).
 *
 * Extracted as a named top-level composable so the Printing/Paused ScreenScaffold `field` slot
 * lambda body contains ONLY a call to this function — creating an independently-restartable
 * recomposition scope (D-01/D-02 P0 fix).
 */
@Composable
internal fun PrintStatusActiveField(
    state: PrinterState,
    metadata: PrintMetadata?,
    babystepShown: Boolean,
    spoolmanPresent: Boolean,
    spoolSwatches: ImmutableList<Color>,
    activeSpoolCardState: ActiveSpoolCardState,
    babystepStep: Double,
    failureText: String?,
    hasBookmarkedMacros: Boolean,
    ui: PrintStatusUiModel,
    onBabystepCompress: () -> Unit,
    onBabystepExpand: () -> Unit,
    onCycleBabystepStep: () -> Unit,
    onNavigate: (NavDest) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatGrid(
            state = state,
            metadata = metadata,
            babystepWindow = babystepShown,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        // Optional Spoolman print line (informational; accent when available < required, D-1c).
        if (spoolmanPresent) {
            SpoolmanPrintLine(
                cardState = activeSpoolCardState,
                metadata = metadata,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // The shortcut row, OR the babystep 3-cell row inside the early-layer window.
        if (ui.activeRow == PrintStatusFieldRow.Babystep) {
            BabystepRow(
                step = babystepStep,
                onCompress = onBabystepCompress,
                onExpand = onBabystepExpand,
                onCycleStep = onCycleBabystepStep,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            ShortcutRow(
                spoolmanPresent = spoolmanPresent,
                spoolSwatches = spoolSwatches,
                hasBookmarkedMacros = hasBookmarkedMacros,
                onNavigate = onNavigate,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
    }
}

/**
 * The Terminal Field — a finished-print summary column with optional error lines.
 *
 * Extracted as a named top-level composable so the Terminal ScreenScaffold `field` slot lambda
 * body contains ONLY a call to this function (D-01/D-02 P0 fix).
 */
@Composable
internal fun PrintStatusTerminalField(
    state: PrinterState,
    metadata: PrintMetadata?,
    ui: PrintStatusUiModel,
    errorLines: List<String>,
    failureText: String?,
    modifier: Modifier = Modifier,
) {
    // Roomier padding than the cockpit grid — the Terminal summary breathes, and the
    // right-aligned values don't hug the screen edge (2026-06-06 UAT).
    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Terminal field = a FINISHED-print summary LIST (not the live cockpit grid): the
        // file, how long it ran, filament used, and how far it got (2026-06-06 UAT).
        TerminalStatsList(
            state = state,
            metadata = metadata,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        // Terminal(Error) ONLY: the AppShell-projected ≤3 error lines (hidden if empty).
        if (ui.showErrorLines && errorLines.isNotEmpty()) {
            TerminalErrorLines(lines = errorLines, modifier = Modifier.fillMaxWidth())
        }
        failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
    }
}

/**
 * The 3×2 icon-led stat grid (mockup 03-print-status.png) — glanceable from across the room. Reads only
 * catalog-confirmed fields; a missing source shows "—" (never fabricated). Two cell shapes:
 *  - [IconTwoRowCell] (icon | active-over-inactive): Z height (altitude), Layer (layers), Nozzle/Bed temp.
 *  - [IconValueCell] (icon | single value): Elapsed (timer_arrow_up), Remaining (timer_arrow_down).
 * The "final height" (Z) and Remaining (ETA) need file metadata → "—" until Inc 2.
 */
@Composable
internal fun StatGrid(
    state: PrinterState,
    metadata: PrintMetadata? = null,
    babystepWindow: Boolean = false,
    terminal: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val nozzle = primaryHeater(state)
    val bed = state.heaters["heater_bed"]
    // Applied Z offset (SC-5): shown when non-zero OR inside the babystep window. The row stays stable
    // (em-dash placeholder) when shown-but-zero in-window; hidden entirely otherwise.
    val zOffset = state.gcodeZOffset ?: 0.0
    val showZOffset = !terminal && (babystepWindow || zOffset != 0.0)
    // Compute the icon size ONCE at the grid level (one BoxWithConstraints here, none per-cell).
    // All rows have equal weight(1f); the uniform cell height = totalGridHeight / numRows.
    // iconSp = cellHeight × CELL_ICON_FRACTION — identical to what each per-cell BoxWithConstraints
    // would compute independently (since each cell fills its row height via fillMaxHeight()).
    // Removing per-cell BoxWithConstraints eliminates 5–6 subcomposition scopes per StatGrid
    // recomposition (D-03 P1 fix). numRows accounts for the optional Z-offset 4th row.
    val numRows = if (showZOffset) 4 else 3
    BoxWithConstraints(modifier) {
        val cellHeightDp = (maxHeight - 8.dp * (numRows - 1)) / numRows
        val iconSp = cellHeightDp.value * CELL_ICON_FRACTION
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTwoRowCell(
                icon = { DinghyIconView(DinghyIcons.Altitude, tint = t.text2, sizeDp = iconSp.dp) },
                // Active = live Z; inactive = metadata object_height (final print height context), "—" when absent.
                active = if (terminal) "—" else fmtZ(state), inactive = metadata?.objectHeight?.let { fmt(it) } ?: "—", activeColor = t.text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            IconTwoRowCell(
                icon = { DinghyIconView(DinghyIcons.Layers, tint = t.text2, sizeDp = iconSp.dp) },
                active = if (terminal) "—" else (state.currentLayer?.toString() ?: "—"),
                // Total = live slicer value preferred, metadata layer_count as the reliable fallback.
                inactive = totalLayers(state, metadata),
                activeColor = t.text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Heater READOUTS read the accent-led N-series rule at the SAME canonical index as the
            // GraphView trace / Temperature legend (D-05/D-06): nozzle = seriesColor(0) = ACCENT (in all
            // modes), bed = seriesColor(1) = pool[0]. Cross-screen identity — same sensor = same color
            // everywhere; the nozzle readout shares one hue with the GraphView trace 0 and the Temperature
            // legend trace-0. D-06 supersession of the Phase-15-07 `nozzle = pool[0]` binding — the nozzle
            // is now accent, not pool[0]. NOT `t.heat` (now caution-only); `seriesColor` guards empty pool.
            val nozzleColor = t.seriesColor(0)
            val bedColor = t.seriesColor(1)
            IconTwoRowCell(
                icon = { DinghyIconView(DinghyIcons.Nozzle, tint = nozzleColor, sizeDp = iconSp.dp) },
                active = tempActive(nozzle), inactive = tempInactive(nozzle), activeColor = nozzleColor,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            IconTwoRowCell(
                icon = { DinghyIconView(DinghyIcons.HeatBed, tint = bedColor, sizeDp = iconSp.dp) },
                active = tempActive(bed), inactive = tempInactive(bed), activeColor = bedColor,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // Memoize the formatted strings on their integer-second keys so String allocation only
            // happens when the second count actually changes, not on every 4 Hz recomposition (D-03).
            val elapsedText = remember(state.printDuration.roundToInt()) { fmtDuration(state.printDuration) }
            IconValueCell(
                icon = { DinghyIconView(DinghyIcons.TimerUp, tint = t.text2, sizeDp = iconSp.dp) },
                value = elapsedText, valueColor = t.text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            // Remaining = slicer-file estimate × (1 − live progress) → H:MM; "—" when estimate unknown.
            val remainingSeconds = metadata?.estimatedTime?.let { it * (1.0 - state.progress.coerceIn(0.0, 1.0)) }
            val remaining = remember(remainingSeconds?.roundToInt()) { remainingSeconds?.takeIf { it > 0.0 }?.let { fmtDuration(it) } ?: "—" }
            IconValueCell(
                icon = { DinghyIconView(DinghyIcons.TimerDown, tint = t.text2, sizeDp = iconSp.dp) },
                value = remaining, valueColor = if (remaining != "—") t.text else t.text3,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        // Applied Z offset (SC-5): the running gcode_move.homing_origin[2] readback (16-04), shown only
        // when non-zero OR inside the babystep window (the row that pairs with the babystep field row).
        // The applied offset lives HERE in the stat frame, never in the babystep row itself.
        if (showZOffset) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconValueCell(
                    icon = { DinghyIconView(DinghyIcons.Height, tint = t.text2, sizeDp = iconSp.dp) },
                    value = stringResource(R.string.printstatus_z_offset, fmtSignedZ(zOffset)),
                    valueColor = if (zOffset != 0.0) t.text else t.text3,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        }
    }
}

/** Icon font size as a fraction of the cell height — kept SMALL so the icon is a quiet indicator and
 * the reading is the hero (Matthew: big icons distract from the values). */
private const val CELL_ICON_FRACTION = 0.45f

/** Icon span = 25% of the cell width; the reading gets the remaining 75% (Matthew, 2026-06-01). */
private const val CELL_ICON_WEIGHT = 0.25f

/**
 * Icon (LEFT, scaled to the cell height) | active-over-inactive value CENTERED in the cell. Active =
 * bold/bright, inactive = dim/smaller. The icon is pinned to the start edge while the reading sits in
 * the cell's center (Matthew, 2026-06-01 — icons left-justified, measurements centered).
 *
 * The `icon` lambda receives no size param — callers pass a zero-argument lambda that already has the
 * iconSp baked in (derived ONCE at the StatGrid BoxWithConstraints level, not per-cell). This removes
 * the per-cell BoxWithConstraints subcomposition scopes (D-03 P1 fix).
 */
@Composable
internal fun IconTwoRowCell(
    icon: @Composable () -> Unit,
    active: String,
    inactive: String,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Row(
        modifier.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Icon span = 25% of the cell width, glyph centered within it.
        Box(Modifier.weight(CELL_ICON_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
            icon()
        }
        // Value span = the remaining 75%, reading centered within it.
        Column(
            Modifier.weight(1f - CELL_ICON_WEIGHT),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(active, color = activeColor, fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = fsSp(26f, t.fs).sp)
            Text(inactive, color = t.text3, fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = fsSp(17f, t.fs).sp)
        }
    }
}

/**
 * Icon (LEFT, pinned to the start edge) | single value CENTERED in the cell — the time cells.
 *
 * The `icon` lambda receives no size param — callers pass a zero-argument lambda with iconSp already
 * captured from the StatGrid BoxWithConstraints scope (D-03 P1 fix, see [IconTwoRowCell]).
 */
@Composable
internal fun IconValueCell(
    icon: @Composable () -> Unit,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Row(
        modifier.padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(CELL_ICON_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
            icon()
        }
        Box(Modifier.weight(1f - CELL_ICON_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
            Text(value, color = valueColor, fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = fsSp(26f, t.fs).sp)
        }
    }
}

// --- Phase-16 four-state surfaces: Standby launcher -----------------------------------------------

/**
 * The Standby adaptive launcher grid (UI-SPEC). Every tile dispatches a real Dest via [onNavigate] —
 * NO tile is bound to a no-op. The Drawer tile variant is retired (D-04/28-05): the System foot button
 * in [PrintStatusStandbyField] now routes to [NavDest.System] directly.
 */
@Composable
internal fun LauncherGrid(
    dests: ImmutableList<LauncherDest>,
    spoolSwatches: ImmutableList<Color>,
    onNavigate: (NavDest) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 2 columns (portrait-stable, touch-friendly ≥64px).
    val columns = 2
    val rows = dests.chunked(columns)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { rowItems ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { d ->
                    LauncherTile(
                        dest = d,
                        spoolSwatches = spoolSwatches,
                        onClick = { launcherDestTarget(d)?.let(onNavigate) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

/** Map a [LauncherDest] to its route [NavDest]. */
internal fun launcherDestTarget(d: LauncherDest): NavDest? = when (d) {
    LauncherDest.Files -> NavDest.Files
    LauncherDest.Temperature -> NavDest.Temperature
    LauncherDest.Move -> NavDest.Move
    LauncherDest.Extrude -> NavDest.Extrude
    LauncherDest.Calibration -> NavDest.CalibrationHub
    LauncherDest.Spool -> NavDest.Spool
    LauncherDest.Macros -> NavDest.Macros
    LauncherDest.Console -> NavDest.Console
}

/** One neutral-outline launcher tile (navigation intent = neutral, UI-SPEC). ICON-ONLY (the text label
 *  was dropped on-device, 2026-06-06 UAT): the glyph fills the tile; [launcherLabel] now feeds a11y only. */
@Composable
internal fun LauncherTile(
    dest: LauncherDest,
    spoolSwatches: ImmutableList<Color>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val label = stringResource(launcherLabelRes(dest))
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.hair), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Icon-only: the glyph owns the whole tile, enlarged to read across the room.
        if (dest == LauncherDest.Spool) {
            // D-06.1/.3: the Spool launcher tile (and the mid-print shortcut Spool slot, which routes
            // through this same renderer) draws the reactive SpoolGlyph tinted by the resolved D-07
            // swatches. Body/keyline stay neutral role tokens (matching the sibling tiles' t.text2);
            // empty swatches → the honest empty spool (D-03). Same fsSp(40f) size as the other tiles.
            works.mees.dinghy.designsystem.icons.SpoolGlyph(
                swatches = spoolSwatches,
                bodyTint = t.text2,
                keyline = t.hair,
                sizeDp = fsSp(40f, t.fs).dp,
                contentDescription = label,
            )
        } else {
            // DinghyIconView owns the a11y (the tile is visually icon-only) — the launcher label is the
            // spoken cd.
            DinghyIconView(
                launcherIcon(dest),
                tint = t.text2,
                sizeDp = fsSp(40f, t.fs).dp,
                contentDescription = label,
            )
        }
    }
}

/** Distinct semantic icon token per launcher tile (icon-never-twice), routed through [DinghyIcons]. */
internal fun launcherIcon(d: LauncherDest): works.mees.dinghy.designsystem.icons.DinghyIcon = when (d) {
    LauncherDest.Files -> DinghyIcons.LauncherFiles
    LauncherDest.Temperature -> DinghyIcons.LauncherTemperature
    LauncherDest.Move -> DinghyIcons.LauncherMove
    LauncherDest.Extrude -> DinghyIcons.LauncherExtrude
    LauncherDest.Calibration -> DinghyIcons.LauncherCalibration
    LauncherDest.Spool -> DinghyIcons.LauncherSpool
    LauncherDest.Macros -> DinghyIcons.LauncherMacros
    LauncherDest.Console -> DinghyIcons.LauncherConsole
}

/** The tile's a11y label string-resource id (icon-only tiles; the label feeds TalkBack only). */
internal fun launcherLabelRes(d: LauncherDest): Int = when (d) {
    LauncherDest.Files -> R.string.cd_launcher_files
    LauncherDest.Temperature -> R.string.cd_launcher_temperature
    LauncherDest.Move -> R.string.cd_launcher_move
    LauncherDest.Extrude -> R.string.cd_launcher_extrude
    LauncherDest.Calibration -> R.string.cd_launcher_calibration
    LauncherDest.Spool -> R.string.cd_launcher_spool
    LauncherDest.Macros -> R.string.cd_launcher_macros
    LauncherDest.Console -> R.string.cd_launcher_console
}

/**
 * The LIVE Print-Status shortcut Tune tile (TUNE-01 / D-21) — taps open the Fine-Tune Hub. Icon-only,
 * mirroring [LauncherTile] (hair outline, ≥64dp, `instant_mix` sliders glyph — DISTINCT from `tune`
 * which is Calibration's, icon-no-repeat). The flexible/growing first cell of the shortcut row.
 */
@Composable
internal fun TuneShortcutTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.hair), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Icon-only: DinghyIconView owns the a11y (the spoken "Tune" cd).
        DinghyIconView(
            DinghyIcons.FineTune,
            tint = t.text2,
            sizeDp = fsSp(40f, t.fs).dp,
            contentDescription = stringResource(R.string.cd_tune),
        )
    }
}

/**
 * The LIVE Print-Status shortcut System tile (D-06/28-05) — provides mid-print access to the System
 * cluster ([NavDest.System]). Icon-only, mirroring [TuneShortcutTile] (hair outline, ≥64dp,
 * `bottom_panel_open` glyph — DISTINCT from the Fine-Tune `instant_mix`, icon-no-repeat).
 */
@Composable
internal fun SystemShortcutTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.hair), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        DinghyIconView(
            DinghyIcons.FootSystem,
            tint = t.text2,
            sizeDp = fsSp(40f, t.fs).dp,
            contentDescription = stringResource(R.string.home_foot_system),
        )
    }
}

/**
 * The Printing/Paused shortcut row (UI-SPEC combination matrix). Tune is the flexible/growing tile;
 * a System tile (D-06/28-05) provides mid-print access to the System cluster. The other slots are
 * navigation tiles per the Spoolman × bookmarked-macros combination.
 */
@Composable
internal fun ShortcutRow(
    spoolmanPresent: Boolean,
    spoolSwatches: ImmutableList<Color>,
    hasBookmarkedMacros: Boolean,
    onNavigate: (NavDest) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The trailing nav slots per the matrix (Tune + System are always first; rest fills the row).
    val tail: List<LauncherDest> = when {
        spoolmanPresent && hasBookmarkedMacros -> listOf(LauncherDest.Temperature, LauncherDest.Macros, LauncherDest.Spool)
        !spoolmanPresent && hasBookmarkedMacros -> listOf(LauncherDest.Temperature, LauncherDest.Macros, LauncherDest.Console)
        spoolmanPresent && !hasBookmarkedMacros -> listOf(LauncherDest.Temperature, LauncherDest.Spool, LauncherDest.Console)
        else -> listOf(LauncherDest.Temperature, LauncherDest.Console)
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Tune = the flexible/growing tile (weight grows when the row is short — the "Tune grows" case).
        // TUNE-01 / D-21: taps open the Fine-Tune Hub (NavDest.FineTune). Distinct `instant_mix` glyph.
        val tuneWeight = if (tail.size < 3) 2f else 1f
        Box(Modifier.weight(tuneWeight)) {
            TuneShortcutTile(
                onClick = { onNavigate(NavDest.FineTune) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // System shortcut tile (D-06/28-05): mid-print access to the System cluster.
        // Shell-level FloatingEStop is NOT suppressed on NavDest.System (D-06 — intentional).
        SystemShortcutTile(
            onClick = { onNavigate(NavDest.System) },
            modifier = Modifier.weight(1f),
        )
        tail.forEach { d ->
            LauncherTile(
                dest = d,
                spoolSwatches = spoolSwatches,
                onClick = { launcherDestTarget(d)?.let(onNavigate) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The babystep 3-cell row (SC-5) — `[ Compress ] [ step value ] [ Expand ]` — replaces the shortcut row
 * inside the early-layer window. Compress fires `babystepZ(-step)` (nozzle CLOSER), Expand `+step`
 * (FARTHER); the center cell shows the step value and tapping it cycles via `nextBabystepStep`. Both
 * controls are accent-outline, icon-only (distinct silhouettes), carrying their verbatim UI-SPEC
 * contentDescription. The applied offset lives in the StatGrid, not here (exempt from the flexible rule).
 */
@Composable
internal fun BabystepRow(
    step: Double,
    onCompress: () -> Unit,
    onExpand: () -> Unit,
    onCycleStep: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BabystepIconCell(
            icon = DinghyIcons.BabystepCompress,
            description = stringResource(R.string.cd_babystep_compress),
            onClick = onCompress,
            modifier = Modifier.weight(1f),
        )
        // Center: step value only; tap cycles the size.
        val stepCd = stringResource(R.string.cd_babystep_step_size, fmtStep(step))
        Box(
            Modifier.weight(1f).heightIn(min = 64.dp)
                .clip(RoundedCornerShape(t.rCtrl))
                .border(BorderStroke(2.dp, t.accentLine), RoundedCornerShape(t.rCtrl))
                .clickable(onClick = onCycleStep)
                .semantics { contentDescription = stepCd },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                fmtStep(step),
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(22f, t.fs).sp,
            )
        }
        BabystepIconCell(
            icon = DinghyIcons.BabystepExpand,
            description = stringResource(R.string.cd_babystep_expand),
            onClick = onExpand,
            modifier = Modifier.weight(1f),
        )
    }
}

/** An accent-outline icon-only babystep cell (Compress/Expand). The glyph carries the action direction;
 *  [description] is the TalkBack contract (the cell is visually icon-only). */
@Composable
internal fun BabystepIconCell(
    icon: works.mees.dinghy.designsystem.icons.DinghyIcon,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.accentLine), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // DinghyIconView owns the a11y (the cell is visually icon-only) — [description] is the spoken cd.
        DinghyIconView(
            icon,
            tint = t.accentLine,
            sizeDp = fsSp(32f, t.fs).dp,
            contentDescription = description,
        )
    }
}

/**
 * The optional Spoolman print line (Printing/Paused Field) — informational active-spool remaining.
 * Hidden entirely when Spoolman is unavailable (the caller gates that) or the remaining is unknown.
 */
@Composable
internal fun SpoolmanPrintLine(
    cardState: ActiveSpoolCardState,
    metadata: PrintMetadata?,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val availableG = (cardState as? ActiveSpoolCardState.Loaded)?.spool?.remainingWeight ?: return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        DinghyIconView(DinghyIcons.Progress, tint = t.text2, sizeDp = fsSp(18f, t.fs).dp)
        Text(
            stringResource(R.string.printstatus_spool_remaining, availableG.roundToInt()),
            color = t.text2,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(16f, t.fs).sp,
        )
    }
}

// --- Terminal Field sub-composables ---------------------------------------------------------------

/**
 * Terminal Field: a FINISHED-print summary LIST (2026-06-06 UAT) — replaces the live cockpit grid for
 * Complete/Cancelled/Error. Reads only retained, catalog-confirmed state (print_stats + the held
 * metadata); a missing value shows "—", never fabricated. Vertically centered, roomy rows.
 */
@Composable
internal fun TerminalStatsList(state: PrinterState, metadata: PrintMetadata?, modifier: Modifier = Modifier) {
    val file = state.printFilename.substringAfterLast('/').ifBlank { "—" }
    val durationSrc = state.printDuration.takeIf { it > 0.0 } ?: state.totalDuration
    val time = remember(durationSrc.roundToInt()) { fmtDuration(durationSrc) }
    val filament = state.filamentUsed.takeIf { it > 0.0 }?.let { "${fmt(it / 1000.0)} m" } ?: "—"
    val totalLayers = state.totalLayer ?: metadata?.layerCount
    val layers = when {
        totalLayers != null -> "${state.currentLayer ?: 0} / $totalLayers"
        state.currentLayer != null -> "${state.currentLayer}"
        else -> "—"
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        TerminalStatRow(stringResource(R.string.printstatus_terminal_file_label), file, marquee = true)
        TerminalStatRow(stringResource(R.string.printstatus_print_time_label), time)
        TerminalStatRow(stringResource(R.string.printstatus_terminal_filament_label), filament)
        TerminalStatRow(stringResource(R.string.printstatus_terminal_layers_label), layers)
    }
}

/** One Terminal summary row: dim caption (left) + GeistMono value filling the rest, right-aligned.
 *  [marquee] = true scrolls an over-long value (the filename) instead of ellipsizing it. */
@Composable
internal fun TerminalStatRow(label: String, value: String, marquee: Boolean = false) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = t.text2,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(20f, t.fs).sp,
        )
        Text(
            value,
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(22f, t.fs).sp,
            maxLines = 1,
            softWrap = false,
            overflow = if (marquee) androidx.compose.ui.text.style.TextOverflow.Clip else androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.weight(1f).then(if (marquee) Modifier.basicMarquee() else Modifier),
        )
    }
}

/** Terminal(Error) error lines (≤3, AppShell-projected). Plain text — NO markup execution (T-16-06-04).
 *  Hidden by the caller when the list is empty. */
@Composable
internal fun TerminalErrorLines(lines: List<String>, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(t.rCard)
    Column(
        modifier
            .clip(shape)
            .border(androidx.compose.foundation.BorderStroke(2.dp, t.hair), shape)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { line ->
            Text(
                line,
                color = t.text2,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Medium,
                fontSize = fsSp(15f, t.fs).sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
