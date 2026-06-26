package works.mees.jiib.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.jiib.R
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.components.FocusEdge
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.components.ListRowLabel
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.designsystem.icons.DinghyIcon
import works.mees.jiib.designsystem.icons.DinghyIconView
import works.mees.jiib.designsystem.icons.DinghyIcons
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.LocalUnitDp
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.DinghyType
import works.mees.jiib.theme.PaletteMode
import works.mees.jiib.theme.StatusSlot
import works.mees.jiib.theme.ThemeResolver
import works.mees.jiib.theme.ThemePrefs
import works.mees.jiib.theme.ThemeTokens
import works.mees.jiib.theme.toComposeColor
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/** Which Theme row is selected (null = resting overview). */
enum class ThemeRow { DarkLight, PaletteMode, Seed, Colors }

/**
 * One editable swatch in the Theme-Colors grid (Task 13 defines the grid + editor that consume this).
 * Declared here because the foot-Back step-back logic + [ThemeContent] hoist it; do NOT re-declare in Task 13.
 */
sealed interface ThemeSwatch {
    data class Pool(val index: Int) : ThemeSwatch
    data object Accent : ThemeSwatch
    data class Status(val slot: works.mees.jiib.theme.StatusSlot) : ThemeSwatch
}

/**
 * The **Theme** screen — lists-first Focus/Field rebuild (supersedes the retired ThemeEditorScreen).
 * Field = 4 rows (Dark/Light inline toggle; Palette Mode, Seed, Theme Colors selectors); Focus swaps
 * by [ThemeRow]. Live preview via the AppContainer draft overlay; Save commits, Back/Revert discards.
 */
@Composable
fun ThemeScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeProfile by container.activeProfile.collectAsStateWithLifecycle(null)
    val hasActive = activeProfile != null
    val saved by container.activeThemeTuple.collectAsStateWithLifecycle(ThemePrefs.TUPLE_DEFAULT)
    val draft by container.themeDraft.collectAsStateWithLifecycle(null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val isPrinting = printerState.printState == PrintState.Printing || printerState.printState == PrintState.Paused

    // Safety: clear any uncommitted live-preview draft when leaving the screen by ANY path (system back,
    // foot Back, or any pop) so a dirty draft never leaks app-wide. The Activity uses configChanges for
    // rotation, so this does NOT fire on rotate — only on genuine route departure (no lost edits on rotate).
    DisposableEffect(Unit) {
        onDispose { container.clearThemeDraft() }
    }

    // The tuple the editor edits: the live draft if one is open, else the saved tuple.
    val working = draft ?: saved

    ThemeContent(
        working = working,
        saved = saved,
        hasDraft = draft != null,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onBack = onBack,
        // immediate (no draft) controls:
        // Codex fix: effectiveTokens NEVER reads themeResolver.tokens, and a live draft wins
        // unconditionally — so update the draft too (if one is open) AND persist immediately.
        onDarkToggle = { d -> container.updateThemeDraft { it?.copy(dark = d) }; container.setActiveDark(hasActive, d) },
        onPaletteMode = { m -> container.updateThemeDraft { it?.copy(paletteMode = m) }; container.setActiveMode(hasActive, m) },
        // draft lifecycle (Seed/Colors editors call these — wired in Tasks 12/13):
        container = container,
        hasActive = hasActive,
        modifier = modifier,
    )
}

@Composable
internal fun ThemeContent(
    working: ThemePrefs.ThemeTuple,
    saved: ThemePrefs.ThemeTuple,          // Codex fix #3: discard/compare baseline (Revert/Cancel restore to this)
    hasDraft: Boolean,
    isPrinting: Boolean,
    onEmergencyStop: (() -> Unit)?,
    onBack: () -> Unit,
    onDarkToggle: (Boolean) -> Unit,
    onPaletteMode: (String) -> Unit,
    container: AppContainer?,          // null in previews
    hasActive: Boolean,
    modifier: Modifier = Modifier,
    initialSelected: ThemeRow? = null,
) {
    var selected by rememberSaveable { mutableStateOf(initialSelected) }
    // Codex fix #4: hoisted here (so the foot Back can step through it). `remember` NOT `rememberSaveable`
    // — the sealed `ThemeSwatch` is not Parcelable/Serializable, so rememberSaveable would crash.
    var editingSwatch by remember { mutableStateOf<ThemeSwatch?>(null) }
    // Selecting a different row always closes any open swatch editor.
    val selectRow: (ThemeRow?) -> Unit = { editingSwatch = null; selected = it }

    // FIX D: system back follows the same contextual ladder as foot-Back (swatch → grid → list).
    // Disabled when neither is set, so system back exits normally (the DisposableEffect clears the draft).
    androidx.activity.compose.BackHandler(enabled = editingSwatch != null || selected != null) {
        if (editingSwatch != null) editingSwatch = null else selected = null
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        // Back is RED while a draft is unsaved (the "you haven't saved" cue, R5); accent otherwise.
        val backIntent = if (hasDraft) Intent.Danger else Intent.Accent
        ScreenScaffold(
            focus = {
                ThemeFocus(
                    selected = selected, working = working, saved = saved, uDp = grid.uDp,
                    isPrinting = isPrinting, onEmergencyStop = onEmergencyStop,
                    onDarkToggle = onDarkToggle, onPaletteMode = onPaletteMode,
                    container = container, hasActive = hasActive,
                    editingSwatch = editingSwatch, onEditSwatch = { editingSwatch = it },
                    onCloseEditor = { editingSwatch = null; selected = null },
                    modifier = Modifier.fillMaxSize(),
                )
            },
            field = {
                ListBlock(Modifier.weight(1f)) {
                    item {
                        // FIX C: Dark/Light is selectable (opens its explainer Focus) AND carries an inline
                        // switch that toggles WITHOUT selecting. Leading Contrast icon. The switch's
                        // onCheckedChange must NOT call selectRow.
                        val t = LocalTokens.current
                        ListRow(
                            selected = selected == ThemeRow.DarkLight,
                            onClick = { selectRow(ThemeRow.DarkLight) },
                            uDp = grid.uDp,
                            leadingContent = { ListRowIcon(icon = DinghyIcons.Contrast, uDp = grid.uDp, tint = t.text) },
                            trailingContent = {
                                androidx.compose.material3.Switch(
                                    checked = working.dark,
                                    onCheckedChange = onDarkToggle,
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        checkedThumbColor = t.bg,
                                        checkedTrackColor = t.accent,
                                        uncheckedThumbColor = t.text3,
                                        uncheckedTrackColor = t.outline,
                                        uncheckedBorderColor = t.outline,
                                        checkedBorderColor = t.accent,
                                    ),
                                )
                            },
                        ) { ListRowLabel(stringResource(R.string.theme_row_dark_light)) }
                    }
                    item { ThemeSelectorRow(ThemeRow.PaletteMode, selected, DinghyIcons.InvertColors,
                        stringResource(R.string.theme_row_palette_mode), stringResource(paletteModeLabelRes(working.paletteMode)), grid.uDp, selectRow) }
                    item { ThemeSelectorRow(ThemeRow.Seed, selected, DinghyIcons.Colors,
                        stringResource(R.string.theme_row_seed), "", grid.uDp, selectRow) }
                    item { ThemeSelectorRow(ThemeRow.Colors, selected, DinghyIcons.Palette,
                        stringResource(R.string.theme_row_colors),
                        if (hasCustomColors(working)) stringResource(R.string.theme_indicator_custom)
                        else stringResource(R.string.theme_indicator_default), grid.uDp, selectRow) }
                }
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            icon = DinghyIcons.Back,
                            onClick = {
                                // Codex fix #5: contextual step-back (swatch → grid → list → exit-with-discard).
                                when {
                                    editingSwatch != null -> editingSwatch = null
                                    selected != null -> selected = null
                                    else -> { container?.clearThemeDraft(); onBack() }
                                }
                            },
                            intent = backIntent,
                            contentDescription = stringResource(R.string.cd_back),
                        ),
                    ),
                )
            },
        )
    }
}

@Composable
private fun ThemeSelectorRow(
    row: ThemeRow, selected: ThemeRow?, icon: DinghyIcon, label: String, indicator: String, uDp: Dp,
    onSelect: (ThemeRow?) -> Unit,
) {
    val t = LocalTokens.current
    ListRow(
        selected = selected == row,
        onClick = { onSelect(if (selected == row) null else row) },
        uDp = uDp,
        leadingContent = { ListRowIcon(icon = icon, uDp = uDp, tint = t.text) },
        trailingContent = if (indicator.isBlank()) null else {
            { Text(indicator, color = t.text2, style = DinghyType.caption.toTextStyle(t)) }
        },
    ) { ListRowLabel(label) }
}

@Composable
private fun ThemeFocus(
    selected: ThemeRow?, working: ThemePrefs.ThemeTuple, saved: ThemePrefs.ThemeTuple, uDp: Dp,
    isPrinting: Boolean, onEmergencyStop: (() -> Unit)?,
    onDarkToggle: (Boolean) -> Unit, onPaletteMode: (String) -> Unit,
    container: AppContainer?, hasActive: Boolean,
    editingSwatch: ThemeSwatch?, onEditSwatch: (ThemeSwatch?) -> Unit, onCloseEditor: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    @Composable
    fun frame(title: String, icon: DinghyIcon, body: @Composable ColumnScope.() -> Unit) {
        FocusFrame(title = title, icon = icon, uDp = uDp, modifier = modifier,
            isPrinting = isPrinting, onEmergencyStop = onEmergencyStop, onPanic = onEmergencyStop, content = body)
    }

    @Composable
    fun explainer(text: String) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text, color = t.text2, style = DinghyType.body.toTextStyle(t))
        }
    }

    when (selected) {
        null -> frame(stringResource(R.string.theme_screen_title), DinghyIcons.Palette) {
            explainer(stringResource(R.string.theme_intro))
        }
        ThemeRow.DarkLight -> frame(stringResource(R.string.theme_row_dark_light), DinghyIcons.Contrast) {
            explainer(stringResource(R.string.theme_dark_light_focus))
        }
        ThemeRow.PaletteMode -> frame(stringResource(R.string.theme_row_palette_mode), DinghyIcons.InvertColors) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.theme_palette_mode_focus), color = t.text2, style = DinghyType.body.toTextStyle(t))
            }
            PaletteModeSegment(working.paletteMode, onPaletteMode, uDp)
        }
        ThemeRow.Seed -> frame(stringResource(R.string.theme_row_seed), DinghyIcons.Colors) {
            val hue = seedHexToHue(working.seedHex)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                works.mees.jiib.designsystem.HueSlider(
                    hue = hue,
                    onHandleMove = { h ->
                        container?.updateThemeDraft { (it ?: working).copy(seedHex = hueToHex(h)) }
                    },
                    onSettle = { h ->
                        container?.updateThemeDraft { (it ?: working).copy(seedHex = hueToHex(h)) }
                    },
                )
            }
            OutlinedControl(
                label = stringResource(R.string.theme_save),
                onClick = { container?.commitThemeDraft(hasActive); onCloseEditor() },
                modifier = Modifier.fillMaxWidth(), intent = Intent.Go,
            )
        }
        ThemeRow.Colors -> {
            val ed = editingSwatch
            if (ed == null) {
                frame(stringResource(R.string.theme_row_colors), DinghyIcons.Palette) {
                    val tk = LocalTokens.current
                    ThemeSwatchGrid(tk, working, onTap = { onEditSwatch(it) })
                    // FIX B: ONE action row of three (5U budget) — Randomize · Revert · Save.
                    // Icon-only (owner UAT 2026-06-18): blank label + contentDescription = a11y label.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedControl("",
                            onClick = {
                                container?.updateThemeDraft {
                                    (it ?: working).copy(poolOverrides = emptyMap(), statusOverrides = emptyMap(),
                                        accentOverride = null, poolShift = nextShift(working.poolShift))
                                }
                            },
                            modifier = Modifier.weight(1f), intent = Intent.Warn, icon = DinghyIcons.Shuffle,
                            contentDescription = stringResource(R.string.theme_randomize))
                        OutlinedControl("",
                            onClick = { container?.clearThemeDraft() },
                            modifier = Modifier.weight(1f), intent = Intent.Warn, icon = DinghyIcons.Revert,
                            contentDescription = stringResource(R.string.theme_revert))
                        OutlinedControl("",
                            onClick = { container?.commitThemeDraft(hasActive); onCloseEditor() },
                            modifier = Modifier.weight(1f), intent = Intent.Go, icon = DinghyIcons.Save,
                            contentDescription = stringResource(R.string.theme_save))
                    }
                }
            } else {
                // FIX A: snapshot the swatch's override AS IT WAS when the editor opened. Cancel restores
                // exactly this (set-or-clear), undoing only THIS editor session's edit — not all the way
                // back to SAVED. The editor independently snapshots the same open-time working value for
                // its HSV init.
                val entryOverride = remember(ed) { swatchOverride(working, ed) }
                ThemeSwatchEditor(
                    swatch = ed, working = working, uDp = uDp, modifier = modifier,
                    isPrinting = isPrinting, onEmergencyStop = onEmergencyStop,
                    onMovePreview = { argb -> container?.updateThemeDraft { applySwatch(it ?: working, ed, argb) } },
                    onSave = { onEditSwatch(null) },          // already staged into the draft live
                    onCancel = {
                        container?.updateThemeDraft { setOrClearSwatch(it ?: working, ed, entryOverride) }
                        onEditSwatch(null)
                    },
                )
            }
        }
    }
}

/** The 3-way palette-mode segment (lives in the Focus, applies live + persists immediately). */
@Composable
private fun PaletteModeSegment(current: String, onPick: (String) -> Unit, uDp: Dp) {
    Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((mode, labelRes, icon) in PALETTE_MODES) {
            // Icon-only (owner UAT 2026-06-18): blank label + contentDescription = a11y label.
            OutlinedControl(
                label = "", onClick = { onPick(mode) },
                modifier = Modifier.weight(1f),
                intent = if (current == mode) Intent.Accent else Intent.Neutral,
                icon = icon,
                contentDescription = stringResource(labelRes),
            )
        }
    }
}

/** A palette-mode chip: the mode key, its label (a11y), and its owner-chosen glyph. Order = display order. */
private data class PaletteModeChip(val mode: String, val labelRes: Int, val icon: DinghyIcon)

private val PALETTE_MODES = listOf(
    PaletteModeChip(ThemeResolver.MODE_COLORFUL, R.string.theme_mode_colorful, DinghyIcons.HumidityHigh),
    PaletteModeChip(ThemeResolver.MODE_HIGH_CONTRAST, R.string.theme_mode_high_contrast, DinghyIcons.InvertColors),
    PaletteModeChip(ThemeResolver.MODE_SIMPLE, R.string.theme_mode_simple, DinghyIcons.WaterDrop),
)

@androidx.annotation.StringRes
private fun paletteModeLabelRes(mode: String): Int = when (mode) {
    ThemeResolver.MODE_SIMPLE -> R.string.theme_mode_simple
    ThemeResolver.MODE_HIGH_CONTRAST -> R.string.theme_mode_high_contrast
    else -> R.string.theme_mode_colorful
}

/** True if the working tuple carries any pool/status/accent override (drives the "Custom" indicator). */
internal fun hasCustomColors(t: ThemePrefs.ThemeTuple): Boolean =
    t.poolOverrides.isNotEmpty() || t.statusOverrides.isNotEmpty() || t.accentOverride != null

@Composable
private fun ColumnScope.ThemeSwatchGrid(
    t: ThemeTokens, working: ThemePrefs.ThemeTuple, onTap: (ThemeSwatch) -> Unit,
) {
    // 1U-capped wide cells, 4 columns × 2 rows. Pool = number; intent = symbol. No captions.
    // Pool + accent come from the baked tokens (overrides already applied in all modes); STATUS uses the
    // literal draft override (statusFill) because t.stop/heat/go are mode-gated (Codex fix).
    val uDp = LocalUnitDp.current ?: 64.dp
    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                t.pool.take(4).forEachIndexed { i, c -> SwatchCell(c, uDp, Modifier.weight(1f), number = i + 1) { onTap(ThemeSwatch.Pool(i)) } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SwatchCell(t.accent, uDp, Modifier.weight(1f), symbol = DinghyIcons.Star) { onTap(ThemeSwatch.Accent) }
                SwatchCell(statusFill(StatusSlot.Stop, working, t), uDp, Modifier.weight(1f), symbol = DinghyIcons.StatusStop) { onTap(ThemeSwatch.Status(StatusSlot.Stop)) }
                SwatchCell(statusFill(StatusSlot.Caution, working, t), uDp, Modifier.weight(1f), symbol = DinghyIcons.Warning) { onTap(ThemeSwatch.Status(StatusSlot.Caution)) }
                SwatchCell(statusFill(StatusSlot.Go, working, t), uDp, Modifier.weight(1f), symbol = DinghyIcons.CheckCircle) { onTap(ThemeSwatch.Status(StatusSlot.Go)) }
            }
        }
    }
}

/** Literal status fill for the grid: the draft override if set, else the (mode-gated) baked token. */
private fun statusFill(slot: StatusSlot, working: ThemePrefs.ThemeTuple, t: ThemeTokens): Color =
    working.statusOverrides[slot.key]?.toComposeColor() ?: when (slot) {
        StatusSlot.Stop -> t.stop
        StatusSlot.Caution -> t.heat
        StatusSlot.Go -> t.go
    }

@Composable
private fun SwatchCell(
    fill: Color, uDp: Dp, modifier: Modifier,
    number: Int? = null, symbol: DinghyIcon? = null, onClick: () -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val ink = if (fill.luminance() > 0.5f) Color(0xFF101010) else Color(0xFFF5F5F5)
    Box(modifier.height(uDp).clip(shape).background(fill)
        .border(BorderStroke(2.dp, t.outline), shape)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (number != null) Text(number.toString(), color = ink, style = DinghyType.dataInline.toTextStyle(t))
        if (symbol != null) DinghyIconView(icon = symbol, tint = ink,
            sizeDp = (uDp * 0.45f), contentDescription = null)
    }
}

@Composable
private fun ThemeSwatchEditor(
    swatch: ThemeSwatch, working: ThemePrefs.ThemeTuple, uDp: Dp, modifier: Modifier,
    isPrinting: Boolean, onEmergencyStop: (() -> Unit)?,
    onMovePreview: (Long) -> Unit, onSave: () -> Unit, onCancel: () -> Unit,
) {
    // FIX A: edit the WORKING value (not SAVED). t = LocalTokens.current is the live working-baked
    // tokens, so savedSwatchArgb(swatch, working, t) reads the swatch's CURRENT WORKING displayed
    // color (override first, else the live token) — the correct HSV start after Randomize / a prior
    // staged edit. The edge tracks the LIVE pick below.
    val t = LocalTokens.current
    // TODO(uat): saved-value compare swatch in header (no FocusFrame slot yet)
    val startArgb = remember(swatch) { savedSwatchArgb(swatch, working, t) }
    val hsv = remember(swatch) { argbLongToHsv(startArgb) }
    var h by rememberSaveable(swatch) { mutableStateOf(hsv[0]) }
    var s by rememberSaveable(swatch) { mutableStateOf(hsv[1]) }
    var v by rememberSaveable(swatch) { mutableStateOf(hsv[2]) }
    FocusFrame(
        title = swatchTitle(swatch), icon = swatchIcon(swatch), uDp = uDp, modifier = modifier,
        isPrinting = isPrinting, onEmergencyStop = onEmergencyStop, onPanic = onEmergencyStop,
        // FIX A: the header edge reads out the LIVE picked color (recomputes as the sliders move).
        edge = FocusEdge.Data(Color(hsvToArgbLong(h, s, v).toInt())),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            works.mees.jiib.designsystem.HsvSliders(
                hue = h, sat = s, value = v,
                onMove = { nh, ns, nv -> h = nh; s = ns; v = nv; onMovePreview(hsvToArgbLong(nh, ns, nv)) },
                onSettle = { nh, ns, nv -> h = nh; s = ns; v = nv; onMovePreview(hsvToArgbLong(nh, ns, nv)) },
            )
        }
        if (swatch is ThemeSwatch.Status && t.mode != PaletteMode.Colorful) {
            val modeLabel = stringResource(when (t.mode) {
                PaletteMode.Simple -> R.string.theme_mode_simple
                PaletteMode.HighContrast -> R.string.theme_mode_high_contrast
                else -> R.string.theme_mode_colorful
            })
            Text(stringResource(R.string.theme_status_saved_for_colorful, modeLabel),
                color = t.text3, style = DinghyType.caption.toTextStyle(t))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedControl(stringResource(R.string.theme_cancel), onClick = onCancel, modifier = Modifier.weight(1f), intent = Intent.Danger)
            OutlinedControl(stringResource(R.string.theme_save), onClick = onSave, modifier = Modifier.weight(1f), intent = Intent.Go)
        }
    }
}

// ---- swatch staging helpers (pure) ----
/** A fresh random pool-hue shift, guaranteed to visibly differ from [current]. */
private fun nextShift(current: Int): Int =
    (current + kotlin.random.Random.nextInt(40, 320)) % 360

private fun applySwatch(tuple: ThemePrefs.ThemeTuple, sw: ThemeSwatch, argb: Long): ThemePrefs.ThemeTuple = when (sw) {
    is ThemeSwatch.Pool -> tuple.copy(poolOverrides = tuple.poolOverrides + (sw.index to argb))
    ThemeSwatch.Accent -> tuple.copy(accentOverride = argb)
    is ThemeSwatch.Status -> tuple.copy(statusOverrides = tuple.statusOverrides + (sw.slot.key to argb))
}

private fun restoreSwatch(tuple: ThemePrefs.ThemeTuple, sw: ThemeSwatch, saved: ThemePrefs.ThemeTuple): ThemePrefs.ThemeTuple = when (sw) {
    is ThemeSwatch.Pool -> tuple.copy(poolOverrides = saved.poolOverrides[sw.index]
        ?.let { tuple.poolOverrides + (sw.index to it) } ?: (tuple.poolOverrides - sw.index))
    ThemeSwatch.Accent -> tuple.copy(accentOverride = saved.accentOverride)
    is ThemeSwatch.Status -> tuple.copy(statusOverrides = saved.statusOverrides[sw.slot.key]
        ?.let { tuple.statusOverrides + (sw.slot.key to it) } ?: (tuple.statusOverrides - sw.slot.key))
}

/** The override Long? for a swatch in [tuple] (null = no override → generated color). */
private fun swatchOverride(tuple: ThemePrefs.ThemeTuple, sw: ThemeSwatch): Long? = when (sw) {
    is ThemeSwatch.Pool -> tuple.poolOverrides[sw.index]
    ThemeSwatch.Accent -> tuple.accentOverride
    is ThemeSwatch.Status -> tuple.statusOverrides[sw.slot.key]
}

/** Set ([argb] non-null) or CLEAR ([argb] null) a swatch's override in [tuple]. */
private fun setOrClearSwatch(tuple: ThemePrefs.ThemeTuple, sw: ThemeSwatch, argb: Long?): ThemePrefs.ThemeTuple =
    if (argb == null) when (sw) {
        is ThemeSwatch.Pool -> tuple.copy(poolOverrides = tuple.poolOverrides - sw.index)
        ThemeSwatch.Accent -> tuple.copy(accentOverride = null)
        is ThemeSwatch.Status -> tuple.copy(statusOverrides = tuple.statusOverrides - sw.slot.key)
    } else applySwatch(tuple, sw, argb)

private fun savedSwatchArgb(sw: ThemeSwatch, working: ThemePrefs.ThemeTuple, t: ThemeTokens): Long = when (sw) {
    is ThemeSwatch.Pool -> working.poolOverrides[sw.index] ?: (t.pool.getOrNull(sw.index) ?: t.accent).toArgb().toLong() and 0xFFFFFFFFL
    ThemeSwatch.Accent -> working.accentOverride ?: t.accent.toArgb().toLong() and 0xFFFFFFFFL
    is ThemeSwatch.Status -> working.statusOverrides[sw.slot.key]
        ?: when (sw.slot) { StatusSlot.Stop -> t.stop; StatusSlot.Caution -> t.heat; StatusSlot.Go -> t.go }.toArgb().toLong() and 0xFFFFFFFFL
}

@Composable
private fun swatchTitle(sw: ThemeSwatch): String = when (sw) {
    is ThemeSwatch.Pool -> stringResource(R.string.theme_swatch_pool, sw.index + 1)
    ThemeSwatch.Accent -> stringResource(R.string.theme_swatch_accent)
    is ThemeSwatch.Status -> stringResource(when (sw.slot) {
        StatusSlot.Stop -> R.string.theme_status_slot_stop
        StatusSlot.Caution -> R.string.theme_status_slot_caution
        StatusSlot.Go -> R.string.theme_status_slot_go
    })
}
private fun swatchIcon(sw: ThemeSwatch): DinghyIcon = when (sw) {
    is ThemeSwatch.Pool -> DinghyIcons.Palette
    ThemeSwatch.Accent -> DinghyIcons.Star
    is ThemeSwatch.Status -> when (sw.slot) {
        StatusSlot.Stop -> DinghyIcons.StatusStop
        StatusSlot.Caution -> DinghyIcons.Warning
        StatusSlot.Go -> DinghyIcons.CheckCircle
    }
}

// ---- migrated color-math helpers (from the retired ThemeEditorScreen) ----
internal fun hueToHex(hue: Float): String {
    val argb = androidx.compose.ui.graphics.Color.hsv(((hue % 360f) + 360f) % 360f, 1f, 1f).let {
        android.graphics.Color.argb(255, (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt())
    }
    return "#%06X".format(java.util.Locale.US, argb and 0xFFFFFF)
}
internal fun hsvToArgbLong(hue: Float, sat: Float, value: Float): Long {
    val argb = android.graphics.Color.HSVToColor(floatArrayOf(((hue % 360f) + 360f) % 360f, sat.coerceIn(0f, 1f), value.coerceIn(0f, 1f)))
    return argb.toLong() and 0xFFFFFFFFL
}
internal fun seedHexToHue(hex: String?): Float {
    val rgb = (hex ?: "#000000").removePrefix("#").take(6).toIntOrNull(16) ?: 0
    val hsv = FloatArray(3); android.graphics.Color.colorToHSV(0xFF000000.toInt() or rgb, hsv); return hsv[0]
}
internal fun argbLongToHsv(argb: Long): FloatArray {
    val hsv = FloatArray(3); android.graphics.Color.colorToHSV(argb.toInt(), hsv); return hsv
}
