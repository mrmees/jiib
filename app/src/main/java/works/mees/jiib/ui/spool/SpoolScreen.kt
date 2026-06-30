package works.mees.jiib.ui.spool

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import works.mees.jiib.BuildConfig
import works.mees.jiib.R
import works.mees.jiib.command.CommandDispatcher
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.components.FocusFrame

import works.mees.jiib.designsystem.components.FocusEdge
import works.mees.jiib.designsystem.components.FillMeter
import works.mees.jiib.designsystem.components.FilterOption
import works.mees.jiib.designsystem.components.FilterRow
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.footAction
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.SortOption
import works.mees.jiib.designsystem.components.SortRow
import works.mees.jiib.control.ControlSpecs
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIconView
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ListBlock as DesignListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.spool.SpoolmanClient
import works.mees.jiib.spool.SpoolmanSpool
import works.mees.jiib.spool.normalizeColorHex
import works.mees.jiib.state.PrintState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.ThemeTokens
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp

/**
 * The Spool screen (Dest.Spool host; 23-06 jiib redesign rebuild) — now built on the new component-class
 * kit: [FocusFrame] + [FillMeter] in Focus, [ListBlock] of [ListRow]s + [FootButtonBar] in Field,
 * [FloatingEStop] as Box sibling, [SortRow] + [FilterRow] at the Focus foot.
 *
 * The Field-takeover filter picker ([FieldMode.FilterPicker]) replaces the old full-screen overlay —
 * tapping a filter tile swaps the Field in-place (no separate screen push).
 *
 * ## Live FloatingEStop wiring
 * [FloatingEStop] is wired to the LIVE [container.printerState] StateFlow — `isPrinting` derives from
 * `printState == PrintState.Printing || PrintState.Paused` (the LastJobHolder convention reused). On
 * tap it raises a red [ConfirmGuard] then dispatches `CommandRegistry.emergencyStop` via [dispatcher].
 *
 * @param holder the per-session picker holder (state + mutators).
 * @param dispatcher the session action dispatcher (null when idle — operations are safe no-ops).
 * @param client the session Spoolman inventory reader (may be null when idle).
 * @param container provides the live [printerState] StateFlow for the FloatingEStop gate.
 * @param onHome navigates to the Home/PrintStatus screen.
 * @param onScan opens the QR scan sub-surface.
 * @param prefilter the D-04 gcode-aware prefilter seed (null on a plain drawer open).
 * @param onPrefilterConsumed clears the one-time [prefilter] seed after the picker applies it.
 */
@Composable
fun SpoolScreen(
    holder: SpoolHolder,
    dispatcher: CommandDispatcher?,
    client: SpoolmanClient?,
    container: works.mees.jiib.di.AppContainer,
    onHome: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    prefilter: SpoolPrefilterSeed? = null,
    onPrefilterConsumed: () -> Unit = {},
) {
    val state by holder.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Live printer-state for FloatingEStop (LastJobHolder convention — PrintState.Printing or Paused).
    val printerState by container.printerState.collectAsStateWithLifecycle(
        initialValue = works.mees.jiib.state.PrinterState(),
    )
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val selected = state.selected
    val isSelectedLoaded = selected?.id != null && selected.id == state.activeStatus?.activeSpoolId

    LaunchedEffect(holder) { holder.load() }
    LaunchedEffect(holder, prefilter) {
        val seed = prefilter ?: return@LaunchedEffect
        holder.seedPrefilter(seed)
        onPrefilterConsumed()
    }

    Box(modifier.fillMaxSize()) {
        SpoolContent(
            state = state,
            selected = selected,
            isSelectedLoaded = isSelectedLoaded,
            isPrinting = isPrinting,
            onSelectSort = { scope.launch { holder.applySort(it) } },
            onOpenFilter = { holder.openFilterPicker(it) },
            onCloseFilter = { holder.closeFilterPicker() },
            onToggleMaterial = { scope.launch { holder.toggleMaterialFamily(it) } },
            onToggleVendor = { scope.launch { holder.toggleVendor(it) } },
            onTapSwatch = { scope.launch { holder.applyColorSwatch(it) } },
            onClearFilter = {
                val mode = state.fieldMode
                if (mode is FieldMode.FilterPicker) {
                    scope.launch {
                        when (mode.category) {
                            SpoolFilterCategory.TYPE -> holder.clearMaterialFamilies()
                            SpoolFilterCategory.COLOR -> holder.clearColor()
                            SpoolFilterCategory.MFG -> holder.clearVendor()
                        }
                    }
                }
            },
            onRowClick = { holder.selectSpool(it) },
            onMeasure = { selected?.let { holder.openMeasureWeight(it) } },
            onCloseMeasure = { holder.closeMeasureWeight() },
            // WR-10 (26-rev): the measure write goes through the holder's own scope, NOT the
            // composition scope — same-frame navigation must not cancel a remote Spoolman write.
            onApplyMeasure = { spool, grams -> holder.measureSpoolAsync(spool, grams) },
            onHome = onHome,
            onScan = onScan,
            onLoad = {
                val id = selected?.id
                if (id != null) holder.setActiveSpool(dispatcher, id)
            },
            onUnload = {
                if (state.activeStatus?.activeSpoolId != null) holder.clearActiveSpool(dispatcher)
            },
            onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        )
    }
}

/**
 * STATELESS preview/render seam (23-06 rebuild). Renders the kit-based scaffold from a pure
 * [SpoolPickerState] fixture — NO holder, NO dispatcher, NO Moonraker — so the preview matrix
 * covers the rebuilt states (no-selection / selected+FillMeter / FieldMode.FilterPicker / printing).
 *
 * [isPrinting] drives the [FloatingEStop] visibility in previews; the live overload sources it
 * from [container.printerState] directly without requiring callers to supply it.
 */
@Composable
fun SpoolScreen(
    state: SpoolPickerState,
    modifier: Modifier = Modifier,
    onSelectSort: (SpoolSortKey) -> Unit = {},
    onOpenFilter: (SpoolFilterCategory) -> Unit = {},
    onCloseFilter: () -> Unit = {},
    onToggleMaterial: (String) -> Unit = {},
    onToggleVendor: (String) -> Unit = {},
    onTapSwatch: (String) -> Unit = {},
    onClearFilter: () -> Unit = {},
    onRowClick: (SpoolmanSpool) -> Unit = {},
    onMeasure: () -> Unit = {},
    onCloseMeasure: () -> Unit = {},
    onApplyMeasure: (SpoolmanSpool, Double) -> Unit = { _, _ -> },
    onHome: () -> Unit = {},
    onScan: () -> Unit = {},
    onLoad: () -> Unit = {},
    onUnload: () -> Unit = {},
    onEmergencyStop: () -> Unit = {},
    isPrinting: Boolean = false,
) {
    val isSelectedLoaded = state.selected?.id != null &&
        state.selected.id == state.activeStatus?.activeSpoolId
    Box(modifier.fillMaxSize()) {
        SpoolContent(
            state = state,
            selected = state.selected,
            isSelectedLoaded = isSelectedLoaded,
            isPrinting = isPrinting,
            onSelectSort = onSelectSort,
            onOpenFilter = onOpenFilter,
            onCloseFilter = onCloseFilter,
            onToggleMaterial = onToggleMaterial,
            onToggleVendor = onToggleVendor,
            onTapSwatch = onTapSwatch,
            onClearFilter = onClearFilter,
            onRowClick = onRowClick,
            onMeasure = onMeasure,
            onCloseMeasure = onCloseMeasure,
            onApplyMeasure = onApplyMeasure,
            onHome = onHome,
            onScan = onScan,
            onLoad = onLoad,
            onUnload = onUnload,
            onEmergencyStop = onEmergencyStop,
        )
    }
}

/**
 * The shared, container-free scaffold rebuilt on the new component kit (23-06):
 * - Focus = [FocusFrame] (color-reactive ring, [FillMeter]) + [SortRow] + [FilterRow] at the foot.
 *   [FloatingEStop] is a Box sibling over the [FocusFrame] (printing-only, TopStart).
 * - Field = when([SpoolPickerState.fieldMode]) { [FieldMode.Spools] → [ListBlock]+[FootButtonBar];
 *   [FieldMode.FilterPicker] → in-place option list + clear/done buttons }
 * - [FootButtonBar] lives in the field lambda (foot-of-list).
 */
@Composable
private fun SpoolContent(
    state: SpoolPickerState,
    selected: SpoolmanSpool?,
    isSelectedLoaded: Boolean,
    isPrinting: Boolean,
    onSelectSort: (SpoolSortKey) -> Unit,
    onOpenFilter: (SpoolFilterCategory) -> Unit,
    onCloseFilter: () -> Unit,
    onToggleMaterial: (String) -> Unit,
    onToggleVendor: (String) -> Unit,
    onTapSwatch: (String) -> Unit,
    onClearFilter: () -> Unit,
    onRowClick: (SpoolmanSpool) -> Unit,
    onMeasure: () -> Unit,
    onCloseMeasure: () -> Unit,
    onApplyMeasure: (SpoolmanSpool, Double) -> Unit,
    onHome: () -> Unit,
    onScan: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onEmergencyStop: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // U is derived once at the screen root from the landscape-constrained short edge, held constant
        // through rotation (portrait width == landscape height for the same physical short edge).
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        // DEBUG-ONLY: log uDp so the owner can confirm U is equal in both orientations at the device gate.
        if (BuildConfig.DEBUG) {
            Log.d("UnitGrid", "uDp=${grid.uDp} count=${grid.count} min=${minOf(maxWidth, maxHeight)}")
        }

        val t = LocalTokens.current
        val spoolColor = selected?.filament?.colorSwatches?.firstNotNullOfOrNull { parseNormalizedHex(it) }
        // Focus header title = vendor · material · name (MFG · Chemistry · Color); generic when none.
        val focusTitle = selected?.let { spoolFocusTitle(it) }
            ?: stringResource(R.string.cd_launcher_spool)

        // Sort options — three options → icon-only (count rule); type-tile retired 2026-06-17.
        val sortOptions = persistentListOf(
            SortOption(
                key = SpoolSortKey.NAME,
                icon = JiibIcons.MatchCase,
                label = stringResource(R.string.spool_sort_name),
                contentDescriptionRes = R.string.cd_spool_sort_name,
                directionUp = if (state.sortKey == SpoolSortKey.NAME) state.sortAscending else null,
            ),
            SortOption(
                key = SpoolSortKey.DATE,
                icon = JiibIcons.CalendarClock,
                label = stringResource(R.string.spool_sort_date),
                contentDescriptionRes = R.string.cd_spool_sort_date,
                directionUp = if (state.sortKey == SpoolSortKey.DATE) state.sortAscending else null,
            ),
            SortOption(
                key = SpoolSortKey.REMAINING,
                icon = JiibIcons.Scale,
                label = stringResource(R.string.spool_sort_remaining),
                contentDescriptionRes = R.string.cd_spool_sort_remaining,
                directionUp = if (state.sortKey == SpoolSortKey.REMAINING) state.sortAscending else null,
            ),
        )

        // Filter options — three options → icon-only (count rule); type-tile retired 2026-06-17.
        val filterOptions = persistentListOf(
            FilterOption(
                key = SpoolFilterCategory.TYPE,
                icon = JiibIcons.Experiment,
                label = stringResource(R.string.spool_filter_type),
                contentDescriptionRes = R.string.cd_spool_filter_type,
                isActive = state.filters.materialFamilies.isNotEmpty(),
            ),
            FilterOption(
                key = SpoolFilterCategory.COLOR,
                icon = JiibIcons.Palette,
                label = stringResource(R.string.spool_filter_color),
                contentDescriptionRes = R.string.cd_spool_filter_color,
                isActive = state.filters.colorSwatchHexes.isNotEmpty() || state.filters.colorSeedHex != null,
            ),
            FilterOption(
                key = SpoolFilterCategory.MFG,
                icon = JiibIcons.Storefront,
                label = stringResource(R.string.spool_filter_mfg),
                contentDescriptionRes = R.string.cd_spool_filter_mfg,
                isActive = state.filters.vendors.isNotEmpty(),
            ),
        )

        ScreenScaffold(
            focus = {
                // Focus = FocusFrame (color-reactive ring + FillMeter) flush in the registered region.
                FocusFrame(
                    title = focusTitle,
                    icon = JiibIcons.SpoolFilament,
                    iconTint = spoolColor,
                    uDp = grid.uDp,
                    edge = spoolColor?.let { FocusEdge.Data(it) } ?: FocusEdge.Neutral,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                    trailingStatusIcon = if (isSelectedLoaded) JiibIcons.CheckCircle else null,
                    trailingStatusTint = t.go,
                    trailingStatusContentDescription = stringResource(R.string.cd_spool_loaded),
                ) {
                    SpoolDetailContent(
                        spool = selected,
                        spoolColor = spoolColor,
                        onMeasure = onMeasure,
                        t = t,
                    )
                }
                // Sort and Filter control rows pinned below the detail card, at the foot of Focus.
                SortRow(
                    options = sortOptions,
                    activeKey = state.sortKey,
                    onSelect = onSelectSort,
                    uDp = grid.uDp,
                )
                FilterRow(
                    options = filterOptions,
                    onSelect = onOpenFilter,
                    uDp = grid.uDp,
                )
            },
            field = {
                when (val fieldMode = state.fieldMode) {
                    is FieldMode.Spools -> {
                        // Normal spool list.
                        SpoolListField(
                            state = state,
                            isSelectedLoaded = isSelectedLoaded,
                            uDp = grid.uDp,
                            onRowClick = onRowClick,
                            onHome = onHome,
                            onScan = onScan,
                            onLoad = onLoad,
                            onUnload = onUnload,
                            t = t,
                        )
                    }
                    is FieldMode.FilterPicker -> {
                        // In-place Field-takeover: show options for the selected filter category.
                        SpoolFilterPickerField(
                            category = fieldMode.category,
                            state = state,
                            uDp = grid.uDp,
                            onToggleMaterial = onToggleMaterial,
                            onToggleVendor = onToggleVendor,
                            onTapSwatch = onTapSwatch,
                            onClear = onClearFilter,
                            onDone = onCloseFilter,
                            t = t,
                        )
                    }
                    is FieldMode.MeasureWeight -> {
                        // D-08: in-place measured-weight numeric-IME Field-takeover.
                        SpoolMeasureWeightField(
                            spool = fieldMode.spool,
                            uDp = grid.uDp,
                            onCancel = onCloseMeasure,
                            onApply = { grams -> onApplyMeasure(fieldMode.spool, grams) },
                            t = t,
                        )
                    }
                }
            },
        )

    }
}

/**
 * The normal spool-list Field: [ListBlock] of [ListRow]s + [FootButtonBar] with the locked foot-button logic.
 *
 * Foot buttons (LOCKED per 23-06 plan): Home (accent) · Scan (accent) · conditional Load (expand_circle_up,
 * accent) when NOT loaded / Unload (expand_circle_down, neutral) when loaded. No "Set Active" button.
 */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SpoolListField(
    state: SpoolPickerState,
    isSelectedLoaded: Boolean,
    uDp: Dp,
    onRowClick: (SpoolmanSpool) -> Unit,
    onHome: () -> Unit,
    onScan: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    t: ThemeTokens,
) {
    val activeId = state.activeStatus?.activeSpoolId
    if (state.spools.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    state.loading -> stringResource(R.string.spool_loading)
                    state.error != null -> stringResource(R.string.spool_error_load)
                    else -> stringResource(R.string.spool_empty_no_match)
                },
                color = t.text2,
                style = JiibType.caption.toTextStyle(t),
                maxLines = 2,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        DesignListBlock(modifier = Modifier.weight(1f)) {
            items(state.spools, key = { it.id }) { spool ->
                ListRow(
                    selected = spool.id == state.selected?.id,
                    onClick = { onRowClick(spool) },
                    uDp = uDp,
                    leadingContent = { SpoolRowSwatch(spool.filament?.colorSwatches ?: emptyList(), t) },
                    trailingContent = { SpoolRowTrailing(spool, activeId, t) },
                ) {
                    SpoolRowBody(spool, t)
                }
            }
        }
    }
    FootButtonBar(
        uDp = uDp,
        actions = listOf(
            // owner 2026-06-17: the typical Back arrow (not the Home glyph); still navigates to PrintStatus.
            footAction(ControlSpecs.commonBack, onClick = onHome),
            footAction(ControlSpecs.spoolScan, onClick = onScan),
            // Conditional Load / Unload (LOCKED logic — RESEARCH §"Foot button logic").
            if (isSelectedLoaded) {
                footAction(ControlSpecs.spoolUnload, onClick = onUnload)
            } else {
                footAction(ControlSpecs.spoolLoad, onClick = onLoad)
            },
        ),
    )
}

/**
 * The in-place Field-takeover filter picker (23-06 FieldMode.FilterPicker). The Field swaps to show
 * the option list for [category]; picking an option or Done/Clear returns to [FieldMode.Spools].
 */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SpoolFilterPickerField(
    category: SpoolFilterCategory,
    state: SpoolPickerState,
    uDp: Dp,
    onToggleMaterial: (String) -> Unit,
    onToggleVendor: (String) -> Unit,
    onTapSwatch: (String) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    t: ThemeTokens,
) {
    when (category) {
        SpoolFilterCategory.TYPE -> {
            if (state.availableMaterialFamilies.isEmpty()) {
                // Mirror the MFG empty-state Box EXACTLY (SpoolScreen.kt:545-557).
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.spool_type_empty),
                        color = t.text2,
                        style = JiibType.body.toTextStyle(t),
                        maxLines = 1,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                DesignListBlock(modifier = Modifier.weight(1f)) {
                    items(state.availableMaterialFamilies, key = { it }) { label ->
                        val selected = state.filters.materialFamilies.any { it.equals(label, ignoreCase = true) }
                        ListRow(
                            selected = selected,
                            onClick = { onToggleMaterial(label) },
                            uDp = uDp,
                        ) {
                            Text(
                                text = label,
                                color = if (selected) t.accent2 else t.text,
                                style = JiibType.listLabel.toTextStyle(t),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        SpoolFilterCategory.COLOR -> {
            // Color swatches: a fill grid (not a lazy list) — keep the existing ColorSwatchGrid.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                ColorSwatchGrid(
                    selectedHexes = (state.filters.colorSwatchHexes + listOfNotNull(state.filters.colorSeedHex))
                        .mapNotNull { normalizeColorHex(it) }
                        .toSet(),
                    onTapSwatch = onTapSwatch,
                    t = t,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        SpoolFilterCategory.MFG -> {
            if (state.vendors.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.spool_mfg_empty),
                        color = t.text2,
                        style = JiibType.body.toTextStyle(t),
                        maxLines = 1,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                DesignListBlock(modifier = Modifier.weight(1f)) {
                    items(state.vendors, key = { it }) { vendor ->
                        val selected = state.filters.vendors.any { it.equals(vendor, ignoreCase = true) }
                        ListRow(
                            selected = selected,
                            onClick = { onToggleVendor(vendor) },
                            uDp = uDp,
                        ) {
                            Text(
                                text = vendor,
                                color = if (selected) t.accent2 else t.text,
                                style = JiibType.listLabel.toTextStyle(t),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
    // Filter picker footer: Clear (danger) · Done (go).
    FootButtonBar(
        uDp = uDp,
        actions = listOf(
            FootAction(
                label = stringResource(R.string.spool_filter_clear),
                onClick = onClear,
                intent = Intent.Danger,
                icon = JiibIcons.DeleteSweep, // owner-assigned glyph (2026-06-12; closes WR-02)
            ),
            FootAction(
                label = stringResource(R.string.spool_filter_done),
                onClick = onDone,
                intent = Intent.Go,
                icon = JiibIcons.Check,
            ),
        ),
    )
}

/**
 * D-08 measured-weight numeric-IME Field-takeover (26-07). Replaces the retired MeasuredWeightPage
 * with an in-place Field composable: the user enters the TOTAL gross weight (spool + filament) via the
 * system numeric keyboard; [onApply] receives the validated double. [onCancel] discards without writing.
 *
 * Security note (T-26-07-02): the raw text is filtered to digits + one decimal point; [onApply] is only
 * called when the parsed value is > 0. No raw IME text ever reaches the network layer.
 */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SpoolMeasureWeightField(
    spool: SpoolmanSpool,
    uDp: Dp,
    onCancel: () -> Unit,
    onApply: (Double) -> Unit,
    t: ThemeTokens,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var weightText by remember(spool.id) { mutableStateOf("") }
    val grams = weightText.toDoubleOrNull()
    val valid = grams != null && grams > 0.0

    // Info card: tare + current believed total (the spool's identity lives in the Focus header above).
    val tare = spool.effectiveSpoolWeight
    val believedTotal = if (tare != null && spool.remainingWeight != null) tare + spool.remainingWeight else null
    val headerShape = RoundedCornerShape(t.rCard)

    Column(
        Modifier
            .fillMaxWidth()
            .weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Numeric IME entry box (D-07: system keyboard — NumpadPage retired in 26-07). The popup's
        // identity is the Focus header above; the entry box + info card carry the rest (no prompt label,
        // freeing vertical space so the hint fits on phone-sized layouts).
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(t.rCtrl))
                .border(
                    BorderStroke(2.dp, if (valid) t.accentLine else t.outline),
                    RoundedCornerShape(t.rCtrl),
                )
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = weightText,
                onValueChange = { raw ->
                    // Filter: digits + single decimal only, max 8 chars (T-26-07-02).
                    val filtered = raw.filter { it.isDigit() || it == '.' }.let { s ->
                        val dotIdx = s.indexOf('.')
                        if (dotIdx >= 0) s.substring(0, dotIdx + 1) + s.substring(dotIdx + 1).filter { it.isDigit() }
                        else s
                    }.take(8)
                    weightText = filtered
                },
                singleLine = true,
                textStyle = JiibType.focusHero.toTextStyle(t).copy(color = t.text),
                cursorBrush = SolidColor(t.accent2),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                // IME Done only dismisses the keyboard — it does NOT apply. The value stays in the
                // cell so the user can review the tare/total math, then taps the Set button to apply.
                keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                modifier = Modifier.fillMaxWidth(),
            )
            if (weightText.isEmpty()) {
                Text(
                    text = stringResource(R.string.spool_measure_placeholder),
                    color = t.text3,
                    style = JiibType.focusHero.toTextStyle(t),
                    maxLines = 1,
                )
            }
        }
        // Info card: tare weight + current believed total (spool identity is in the Focus header).
        Column(
            Modifier
                .fillMaxWidth()
                .clip(headerShape)
                .background(t.surface)
                .border(BorderStroke(2.dp, t.hair), headerShape)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SpoolMeasureWeightStat(stringResource(R.string.spool_measure_tare_label), tare, t)
            SpoolMeasureWeightStat(stringResource(R.string.spool_measure_total_label), believedTotal, t)
            Text(
                text = stringResource(R.string.spool_measure_hint),
                color = t.text2,
                style = JiibType.caption.toTextStyle(t),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    // Footer: Back (Danger — discards; C7 rule) · Set (Go — applies when valid).
    FootButtonBar(
        uDp = uDp,
        actions = listOf(
            FootAction(
                label = stringResource(R.string.spool_measure_back),
                onClick = onCancel,
                intent = Intent.Danger,
                icon = JiibIcons.Back,
            ),
            FootAction(
                label = stringResource(R.string.spool_measure_set),
                onClick = {
                    if (valid) {
                        keyboardController?.hide()
                        onApply(grams!!)
                    }
                },
                intent = Intent.Go,
                icon = JiibIcons.Check,
            ),
        ),
    )
}

/** One labelled weight stat in the measure-weight header: label and grams or "not set" when null. */
@Composable
private fun SpoolMeasureWeightStat(label: String, grams: Double?, t: ThemeTokens) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = t.text2,
            style = JiibType.caption.toTextStyle(t),
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = grams?.let { "${it.roundToInt()} g" }
                ?: stringResource(R.string.spool_measure_weight_not_set),
            color = if (grams == null) t.text3 else t.text,
            style = JiibType.statValue.toTextStyle(t),
            maxLines = 1,
        )
    }
}

/**
 * The spool's display identity: `material · name` (e.g. `PLA · Galaxy Black`), degrading to
 * `Spool <id>` when both are blank. One definition shared by the list row, the Focus header title,
 * and the measure-weight info card (was duplicated inline at each).
 */
@Composable
private fun spoolDisplayTitle(spool: SpoolmanSpool): String =
    listOfNotNull(spool.filament?.material, spool.filament?.name).joinToString(" · ")
        .ifBlank { stringResource(R.string.spool_unnamed, spool.id) }

/**
 * The Focus-header identity for a selected spool: `vendor · material · name`
 * (MFG · Chemistry · Color), e.g. `Prusament · PLA · Galaxy Black`. Missing parts are skipped;
 * degrades to `Spool <id>`. Distinct from [spoolDisplayTitle] (the list-row / measure-card
 * `material · name`) because the list rows carry vendor in their meta line instead.
 */
@Composable
private fun spoolFocusTitle(spool: SpoolmanSpool): String =
    listOfNotNull(spool.filament?.vendor?.name, spool.filament?.material, spool.filament?.name)
        .joinToString(" · ")
        .ifBlank { stringResource(R.string.spool_unnamed, spool.id) }

/**
 * The Detail card content for the selected spool (inside [FocusFrame]). The spool's identity lives in
 * the Focus header (title = vendor · material · name, icon = spool-colored ev_shadow); the empty state
 * shows a neutral ev_shadow ([JiibIcons.SpoolFilament]). The card body is the tappable FillMeter
 * (also the measure-weight entry) plus the recommended temps and registration date.
 */
@Composable
private fun SpoolDetailContent(
    spool: SpoolmanSpool?,
    spoolColor: Color?,
    onMeasure: () -> Unit,
    t: ThemeTokens,
) {
    if (spool == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            JiibIconView(
                JiibIcons.SpoolFilament,
                tint = t.text3,
                sizeDp = fsSp(64f, t.fs).dp,
                contentDescription = stringResource(R.string.cd_spool_empty),
            )
        }
        return
    }
    val filament = spool.filament
    val iconSp = fsSp(20f, t.fs)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // The spool's identity now lives in the Focus header (title = material · name, icon tinted to
        // the spool color); the FillMeter below is the in-card color/fullness visual. No chip row here.
        // FillMeter — remaining fraction (remaining/original); fallback t.accent when no filament color.
        val remaining = spool.remainingWeight
        val original = spool.originalWeight
        val fillFraction = if (remaining != null && original != null && original > 0.0) {
            (remaining / original).toFloat()
        } else if (remaining != null) {
            0.5f // unknown original — show half as a neutral indicator
        } else {
            0f
        }
        val fillLabel = buildFillLabel(spool)
        // The fill bar is the spool's weight visual (label shows remaining/original g · %) AND the
        // tap target to correct the measured weight (the old standalone weight row was removed).
        val editWeightCd = stringResource(R.string.cd_spool_weight_edit)
        val measureInteraction = remember { MutableInteractionSource() }
        FillMeter(
            fraction = fillFraction,
            fillColor = spoolColor ?: t.accent,
            modifier = Modifier
                .fillMaxWidth()
                // Tap target to correct the weight — no rounded clip / indication frame so the
                // weight line carries no rounded border (owner UAT 2026-06-17).
                .clickable(
                    interactionSource = measureInteraction,
                    indication = null,
                    onClick = onMeasure,
                )
                .semantics { contentDescription = editWeightCd },
            label = fillLabel,
        )
        // Nozzle + bed recommended temps.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JiibIconView(JiibIcons.Nozzle, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_nozzle_temp))
            Text(tempText(filament?.settingsExtruderTemp), color = t.text, style = JiibType.dataInline.toTextStyle(t), maxLines = 1)
            JiibIconView(JiibIcons.HeatBed, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_bed_temp))
            Text(tempText(filament?.settingsBedTemp), color = t.text, style = JiibType.dataInline.toTextStyle(t), maxLines = 1)
        }
        // Registration date.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            JiibIconView(JiibIcons.CalendarAddOn, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_registered))
            Text(
                text = spool.registered?.substringBefore('T')?.ifBlank { null }
                    ?: stringResource(R.string.spool_value_unset),
                color = t.text,
                style = JiibType.dataInline.toTextStyle(t),
                maxLines = 1,
            )
        }
        if (spool.archived) {
            DetailBadge(JiibIcons.Archive, stringResource(R.string.spool_badge_archived), stringResource(R.string.cd_spool_archived), t, iconSp, t.heat)
        }
    }
}

/** Build the FillMeter label string: "remaining/original g · percent" or "remaining g". */
@Composable
private fun buildFillLabel(spool: SpoolmanSpool): String {
    val remaining = spool.remainingWeight ?: return ""
    val original = spool.originalWeight
    return if (original != null && original > 0.0) {
        val pct = ((remaining / original) * 100).roundToInt()
        stringResource(R.string.spool_fill_meter_label, remaining.roundToInt(), original.roundToInt(), pct)
    } else {
        stringResource(R.string.spool_fill_meter_label_no_original, remaining.roundToInt())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Spool list row sub-components
// ─────────────────────────────────────────────────────────────────────────────

/** Leading swatch cluster for a spool row (up to 3 color dots). */
@Composable
private fun SpoolRowSwatch(swatches: List<String>, t: ThemeTokens) {
    val size = fsSp(18f, t.fs).dp
    if (swatches.isEmpty()) {
        Box(
            Modifier.size(size).clip(CircleShape).background(t.surface2)
                .border(BorderStroke(1.dp, t.hair), CircleShape),
        )
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(end = 8.dp),
    ) {
        swatches.take(3).forEach { hex ->
            Box(
                Modifier.size(size).clip(CircleShape)
                    .background(parseNormalizedHex(hex) ?: t.surface2)
                    .border(BorderStroke(1.dp, t.hair), CircleShape),
            )
        }
    }
}

/** Row body content: material · name + vendor/location metadata line. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.SpoolRowBody(spool: SpoolmanSpool, t: ThemeTokens) {
    val filament = spool.filament
    Column(Modifier.weight(1f).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = spoolDisplayTitle(spool),
            color = t.text,
            style = JiibType.listLabel.toTextStyle(t),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOfNotNull(
            filament?.vendor?.name,
            spool.location?.let { "@ $it" },
        ).joinToString("  ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                color = t.text2,
                style = JiibType.dataMeta.toTextStyle(t),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Trailing weight + loaded/archived badge for a spool row. */
@Composable
private fun SpoolRowTrailing(spool: SpoolmanSpool, activeId: Int?, t: ThemeTokens) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        Text(
            text = spool.remainingWeight?.let { "${it.roundToInt()} g" }
                ?: stringResource(R.string.spool_value_unset),
            color = if (spool.remainingWeight == null) t.text3 else t.text,
            style = JiibType.dataInline.toTextStyle(t),
            maxLines = 1,
        )
        if (spool.id == activeId) {
            Text(
                text = stringResource(R.string.spool_row_badge_loaded),
                color = t.go,
                style = JiibType.caption.toTextStyle(t),
                maxLines = 1,
            )
        } else if (spool.archived) {
            Text(
                text = stringResource(R.string.spool_row_badge_archived),
                color = t.heat,
                style = JiibType.caption.toTextStyle(t),
                maxLines = 1,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail card sub-components (reused from original SpoolScreen)
// ─────────────────────────────────────────────────────────────────────────────

/** Line-5 temperature text: `210°C`, or "—" when unset. */
@Composable
private fun tempText(temp: Int?): String =
    temp?.let { stringResource(R.string.spool_temp, it) } ?: stringResource(R.string.spool_value_unset)

/** An icon-led detail badge (loaded green / archived amber). */
@Composable
private fun DetailBadge(
    icon: JiibIcon,
    text: String,
    contentDescription: String,
    t: ThemeTokens,
    iconSp: Float,
    color: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JiibIconView(icon, tint = color, sizeDp = iconSp.dp, contentDescription = contentDescription)
        Text(text, color = color, style = JiibType.body.toTextStyle(t), maxLines = 1)
    }
}

/**
 * Parse a (possibly un-normalized) filament hex to a Compose [Color] (D-08); guards via
 * [normalizeColorHex] (accepts `#`/no-`#`, 6/8 hex digits, else null) and never throws.
 *
 * Promoted from `private` to `internal` top-level (18.3-01) so the SpoolGlyph (plan 02) and the
 * surface-wiring (plan 03) call this ONE shared helper instead of duplicating the parse logic
 * (Pitfall 6). It lives in the `works.mees.jiib.ui.spool` package, visible to every spool screen
 * without an import.
 */
internal fun parseNormalizedHex(hex: String): Color? {
    val normalized = normalizeColorHex(hex) ?: return null
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}
