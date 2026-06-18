package works.mees.dinghy.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.components.ToggleRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.PaletteMode
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.theme.ThemePrefs
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

/** Which Theme row is selected (null = resting overview). */
enum class ThemeRow { DarkLight, PaletteMode, Seed, Colors }

/**
 * One editable swatch in the Theme-Colors grid (Task 13 defines the grid + editor that consume this).
 * Declared here because the foot-Back step-back logic + [ThemeContent] hoist it; do NOT re-declare in Task 13.
 */
sealed interface ThemeSwatch {
    data class Pool(val index: Int) : ThemeSwatch
    data object Accent : ThemeSwatch
    data class Status(val slot: works.mees.dinghy.theme.StatusSlot) : ThemeSwatch
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
                        // Dark/Light is the ONE row with an inline control (owner law).
                        ToggleRow(
                            label = stringResource(R.string.theme_row_dark_light),
                            checked = working.dark,
                            onToggle = onDarkToggle,
                            uDp = grid.uDp,
                        )
                    }
                    item { ThemeSelectorRow(ThemeRow.PaletteMode, selected, DinghyIcons.InvertColors,
                        stringResource(R.string.theme_row_palette_mode), paletteModeLabel(working.paletteMode), grid.uDp, selectRow) }
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
        Box(Modifier.fillMaxWidth().fillMaxSize(), contentAlignment = Alignment.Center) {
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
            // Wired in Task 12.
            explainer(stringResource(R.string.theme_seed_focus))
        }
        ThemeRow.Colors -> frame(stringResource(R.string.theme_row_colors), DinghyIcons.Palette) {
            // Wired in Task 13.
            explainer(stringResource(R.string.theme_colors_focus))
        }
    }
}

/** The 3-way palette-mode segment (lives in the Focus, applies live + persists immediately). */
@Composable
private fun PaletteModeSegment(current: String, onPick: (String) -> Unit, uDp: Dp) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for ((mode, labelRes) in PALETTE_MODES) {
            OutlinedControl(
                label = stringResource(labelRes), onClick = { onPick(mode) },
                modifier = Modifier.weight(1f),
                intent = if (current == mode) Intent.Accent else Intent.Neutral,
            )
        }
    }
}

private val PALETTE_MODES = listOf(
    ThemeResolver.MODE_COLORFUL to R.string.theme_mode_colorful,
    ThemeResolver.MODE_SIMPLE to R.string.theme_mode_simple,
    ThemeResolver.MODE_HIGH_CONTRAST to R.string.theme_mode_high_contrast,
)

private fun paletteModeLabel(mode: String): String = when (mode) {
    ThemeResolver.MODE_SIMPLE -> "Simple"
    ThemeResolver.MODE_HIGH_CONTRAST -> "High contrast"
    else -> "Colorful"
}

/** True if the working tuple carries any pool/status/accent override (drives the "Custom" indicator). */
internal fun hasCustomColors(t: ThemePrefs.ThemeTuple): Boolean =
    t.poolOverrides.isNotEmpty() || t.statusOverrides.isNotEmpty() || t.accentOverride != null

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
