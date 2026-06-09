package works.mees.dinghy.ui.spool

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import works.mees.dinghy.BuildConfig
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.components.DetailCard
import works.mees.dinghy.designsystem.components.FillMeter
import works.mees.dinghy.designsystem.components.FilterOption
import works.mees.dinghy.designsystem.components.FilterRow
import works.mees.dinghy.designsystem.components.FloatingEStop
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.SortOption
import works.mees.dinghy.designsystem.components.SortRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.icons.SpoolGlyph
import works.mees.dinghy.designsystem.layout.ListBlock as DesignListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.spool.normalizeColorHex
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The Spool screen (Dest.Spool host; 23-06 jiib redesign rebuild) — now built on the new component-class
 * kit: [DetailCard] + [FillMeter] in Focus, [ListBlock] of [ListRow]s + [FootButtonBar] in Field,
 * [FloatingEStop] as Box sibling, [SortRow] + [FilterRow] at the Focus foot, `gutter = null`.
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
    container: works.mees.dinghy.di.AppContainer,
    onHome: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    prefilter: SpoolPrefilterSeed? = null,
    onPrefilterConsumed: () -> Unit = {},
) {
    val state by holder.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // The spool whose measured-gross-weight page is open (null = none) — D-04.
    var measureSpool by remember { mutableStateOf<SpoolmanSpool?>(null) }
    // Live printer-state for FloatingEStop (LastJobHolder convention — PrintState.Printing or Paused).
    val printerState by container.printerState.collectAsStateWithLifecycle(
        initialValue = works.mees.dinghy.state.PrinterState(),
    )
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    // E-stop ConfirmGuard gate (same pattern as PrintStatusScreen).
    var showEstopGuard by remember { mutableStateOf(false) }
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
            onTapSwatch = { scope.launch { holder.applyColorSwatch(it); holder.closeFilterPicker() } },
            onMultiColor = { scope.launch { holder.applyMultiColor(); holder.closeFilterPicker() } },
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
            onMeasure = { selected?.let { measureSpool = it } },
            onHome = onHome,
            onScan = onScan,
            onLoad = {
                val id = selected?.id
                if (id != null) holder.setActiveSpool(dispatcher, id)
            },
            onUnload = {
                if (state.activeStatus?.activeSpoolId != null) holder.clearActiveSpool(dispatcher)
            },
            onEmergencyStop = { showEstopGuard = true },
        )

        // The E-stop ConfirmGuard (same gate pattern as PrintStatusScreen).
        if (showEstopGuard) {
            ConfirmGuard(
                title = stringResource(R.string.spool_estop_guard_title),
                message = stringResource(R.string.spool_estop_guard_message),
                confirmLabel = stringResource(R.string.spool_estop_guard_confirm),
                cancelLabel = stringResource(R.string.common_cancel),
                onConfirm = {
                    dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit)
                    showEstopGuard = false
                },
                onCancel = { showEstopGuard = false },
                destructive = true,
            )
        }

        // The measured-gross-weight page (D-04).
        measureSpool?.let { target ->
            MeasuredWeightPage(
                spool = target,
                client = client,
                onCancel = { measureSpool = null },
                onMeasured = {
                    measureSpool = null
                    scope.launch { holder.refresh() }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
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
    onMultiColor: () -> Unit = {},
    onClearFilter: () -> Unit = {},
    onRowClick: (SpoolmanSpool) -> Unit = {},
    onMeasure: () -> Unit = {},
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
            onMultiColor = onMultiColor,
            onClearFilter = onClearFilter,
            onRowClick = onRowClick,
            onMeasure = onMeasure,
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
 * - Focus = [DetailCard] (color-reactive ring, [FillMeter]) + [SortRow] + [FilterRow] at the foot.
 *   [FloatingEStop] is a Box sibling over the [DetailCard] (printing-only, TopStart).
 * - Field = when([SpoolPickerState.fieldMode]) { [FieldMode.Spools] → [ListBlock]+[FootButtonBar];
 *   [FieldMode.FilterPicker] → in-place option list + clear/done buttons }
 * - gutter = null (redesigned; [FootButtonBar] lives in the field lambda).
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
    onMultiColor: () -> Unit,
    onClearFilter: () -> Unit,
    onRowClick: (SpoolmanSpool) -> Unit,
    onMeasure: () -> Unit,
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

        // Sort options — SortOption with registered DinghyIcons tokens.
        val sortOptions = persistentListOf(
            SortOption(
                key = SpoolSortKey.NAME,
                icon = DinghyIcons.MatchCase,
                contentDescriptionRes = R.string.cd_spool_sort_name,
                directionUp = if (state.sortKey == SpoolSortKey.NAME) state.sortAscending else null,
            ),
            SortOption(
                key = SpoolSortKey.DATE,
                icon = DinghyIcons.CalendarClock,
                contentDescriptionRes = R.string.cd_spool_sort_date,
                directionUp = if (state.sortKey == SpoolSortKey.DATE) state.sortAscending else null,
            ),
            SortOption(
                key = SpoolSortKey.REMAINING,
                icon = DinghyIcons.Scale,
                contentDescriptionRes = R.string.cd_spool_sort_remaining,
                directionUp = if (state.sortKey == SpoolSortKey.REMAINING) state.sortAscending else null,
            ),
        )

        // Filter options — FilterOption with registered DinghyIcons tokens.
        val filterOptions = persistentListOf(
            FilterOption(
                key = SpoolFilterCategory.TYPE,
                icon = DinghyIcons.Experiment,
                contentDescriptionRes = R.string.cd_spool_filter_type,
                isActive = state.filters.materialFamilies.isNotEmpty(),
            ),
            FilterOption(
                key = SpoolFilterCategory.COLOR,
                icon = DinghyIcons.Palette,
                contentDescriptionRes = R.string.cd_spool_filter_color,
                isActive = state.filters.colorSwatchHex != null,
            ),
            FilterOption(
                key = SpoolFilterCategory.MFG,
                icon = DinghyIcons.Storefront,
                contentDescriptionRes = R.string.cd_spool_filter_mfg,
                isActive = state.filters.vendors.isNotEmpty(),
            ),
        )

        ScreenScaffold(
            focus = {
                // Focus = DetailCard (color-reactive ring + FillMeter) with FloatingEStop as Box sibling.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    DetailCard(
                        ringColor = spoolColor,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        SpoolDetailContent(
                            spool = selected,
                            isActive = isSelectedLoaded,
                            spoolColor = spoolColor,
                            onMeasure = onMeasure,
                            t = t,
                        )
                    }
                    // FloatingEStop: Box sibling over the DetailCard, printing-only (Pitfall 7).
                    FloatingEStop(
                        visible = isPrinting,
                        onClick = onEmergencyStop,
                        uDp = grid.uDp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp),
                    )
                }
                // Sort and Filter control rows pinned below the detail card, at the foot of Focus.
                SortRow(
                    options = sortOptions,
                    activeKey = state.sortKey,
                    onSelect = onSelectSort,
                    uDp = grid.uDp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                FilterRow(
                    options = filterOptions,
                    onSelect = onOpenFilter,
                    uDp = grid.uDp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
                            onMultiColor = onMultiColor,
                            onClear = onClearFilter,
                            onDone = onCloseFilter,
                            t = t,
                        )
                    }
                }
            },
            gutter = null, // Redesigned screen — FootButtonBar is in the field lambda (Pitfall 1).
        )

        // DEBUG-ONLY uDp badge in the bottom-start corner (release-stripped via BuildConfig.DEBUG).
        if (BuildConfig.DEBUG) {
            Text(
                text = "U=${grid.uDp}",
                color = t.accent2,
                fontFamily = GeistMono,
                fontSize = fsSp(11f, t.fs).sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .background(t.bg.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
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
                .weight(1f)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    state.loading -> stringResource(R.string.spool_loading)
                    state.error != null -> stringResource(R.string.spool_error_load)
                    else -> stringResource(R.string.spool_empty_no_match)
                },
                color = t.text2,
                fontFamily = Geist,
                fontSize = fsSp(15f, t.fs).sp,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        DesignListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
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
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        OutlinedControl(
            label = "",
            onClick = onHome,
            modifier = Modifier.weight(1f),
            intent = Intent.Accent,
            icon = DinghyIcons.Home,
        )
        OutlinedControl(
            label = "",
            onClick = onScan,
            modifier = Modifier.weight(1f),
            intent = Intent.Accent,
            icon = DinghyIcons.QrCode,
        )
        // Conditional Load / Unload (LOCKED logic — RESEARCH §"Foot button logic").
        if (isSelectedLoaded) {
            OutlinedControl(
                label = "",
                onClick = onUnload,
                modifier = Modifier.weight(1f),
                intent = Intent.Neutral,
                icon = DinghyIcons.ExpandCircleDown,
            )
        } else {
            OutlinedControl(
                label = "",
                onClick = onLoad,
                modifier = Modifier.weight(1f),
                intent = Intent.Accent,
                icon = DinghyIcons.ExpandCircleUp,
            )
        }
    }
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
    onMultiColor: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    t: ThemeTokens,
) {
    when (category) {
        SpoolFilterCategory.TYPE -> {
            DesignListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                items(MATERIAL_FAMILIES, key = { it.first }) { (label, _) ->
                    val selected = state.filters.materialFamilies.any { it.equals(label, ignoreCase = true) }
                    ListRow(
                        selected = selected,
                        onClick = { onToggleMaterial(label) },
                        uDp = uDp,
                    ) {
                        Text(
                            text = label,
                            color = if (selected) t.accent2 else t.text,
                            fontFamily = Geist,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = fsSp(18f, t.fs).sp,
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
        SpoolFilterCategory.COLOR -> {
            // Color swatches: a fill grid (not a lazy list) — keep the existing ColorSwatchGrid.
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) {
                ColorSwatchGrid(
                    selectedHex = state.filters.colorSwatchHex,
                    onTapSwatch = onTapSwatch,
                    onMultiColor = onMultiColor,
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
                        .weight(1f)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No manufacturers found.",
                        color = t.text2,
                        fontFamily = Geist,
                        fontSize = fsSp(17f, t.fs).sp,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                DesignListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
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
                                fontFamily = Geist,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = fsSp(18f, t.fs).sp,
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
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        OutlinedControl(
            label = stringResource(R.string.spool_filter_clear),
            onClick = onClear,
            modifier = Modifier.weight(1f),
            intent = Intent.Danger,
            symbol = "close",
        )
        OutlinedControl(
            label = stringResource(R.string.spool_filter_done),
            onClick = onDone,
            modifier = Modifier.weight(1f),
            intent = Intent.Go,
            symbol = "check",
        )
    }
}

/**
 * The Detail card content for the selected spool (23-06 rebuild inside [DetailCard]).
 * The [SpoolGlyph] from Phase 18.3 stays the spool visual — do NOT reinvent it.
 * FillMeter shows the remaining fraction (remaining/original). No redundant Spoolman icon.
 */
@Composable
private fun SpoolDetailContent(
    spool: SpoolmanSpool?,
    isActive: Boolean,
    spoolColor: Color?,
    onMeasure: () -> Unit,
    t: ThemeTokens,
) {
    if (spool == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            SpoolGlyph(
                swatches = emptyList(),
                bodyTint = t.text3,
                keyline = t.hair,
                sizeDp = fsSp(64f, t.fs).dp,
                contentDescription = stringResource(R.string.cd_spool_empty),
            )
        }
        return
    }
    val filament = spool.filament
    val headerSp = fsSp(26f, t.fs)
    val bodySp = fsSp(18f, t.fs)
    val iconSp = fsSp(20f, t.fs)
    val detailSwatches: List<Color> =
        filament?.colorSwatches?.mapNotNull(::parseNormalizedHex).orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Spool glyph (Phase 18.3 — img/spool.svg, reactive to filament color).
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SpoolGlyph(
                swatches = detailSwatches,
                bodyTint = t.text2,
                keyline = t.hair,
                sizeDp = fsSp(64f, t.fs).dp,
                contentDescription = stringResource(R.string.cd_spool_color),
            )
        }
        // Header: color swatch + material name (no Spoolman icon — removed per redesign).
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailSwatch(filament?.colorSwatches ?: emptyList(), headerSp, t)
            Text(
                text = filament?.material?.ifBlank { null }
                    ?: stringResource(R.string.spool_unnamed, spool.id),
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = headerSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
        FillMeter(
            fraction = fillFraction,
            fillColor = spoolColor ?: t.accent,
            modifier = Modifier.fillMaxWidth(),
            label = fillLabel,
        )
        // Vendor + color name.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DinghyIconView(DinghyIcons.Storefront, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_vendor))
            DetailValue(filament?.vendor?.name, bodySp, Modifier.weight(1f), t)
            DinghyIconView(DinghyIcons.Palette, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_color))
            DetailValue(filament?.name, bodySp, Modifier.weight(1f), t)
        }
        // Weight row (tappable to correct measured weight via D-04).
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(t.rCtrl)).clickable(onClick = onMeasure)
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DinghyIconView(DinghyIcons.Scale, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_weight))
            Text(
                text = spoolWeightText(spool),
                color = if (spool.remainingWeight == null) t.text3 else t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = bodySp.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            DinghyIconView(DinghyIcons.Edit, tint = t.text3, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_weight_edit))
        }
        // Registration date.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DinghyIconView(DinghyIcons.CalendarAddOn, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_registered))
            Text(
                text = spool.registered?.substringBefore('T')?.ifBlank { null }
                    ?: stringResource(R.string.spool_value_unset),
                color = t.text,
                fontFamily = GeistMono,
                fontSize = bodySp.sp,
                maxLines = 1,
            )
        }
        // Nozzle + bed recommended temps.
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DinghyIconView(DinghyIcons.Nozzle, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_nozzle_temp))
            Text(tempText(filament?.settingsExtruderTemp), color = t.text, fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontSize = bodySp.sp, maxLines = 1)
            DinghyIconView(DinghyIcons.HeatBed, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_bed_temp))
            Text(tempText(filament?.settingsBedTemp), color = t.text, fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontSize = bodySp.sp, maxLines = 1)
        }
        if (isActive) {
            DetailBadge(DinghyIcons.CheckCircle, stringResource(R.string.spool_badge_loaded), stringResource(R.string.cd_spool_loaded), bodySp, iconSp, t.go)
        }
        if (spool.archived) {
            DetailBadge(DinghyIcons.Archive, stringResource(R.string.spool_badge_archived), stringResource(R.string.cd_spool_archived), bodySp, iconSp, t.heat)
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
            text = listOfNotNull(filament?.material, filament?.name).joinToString(" · ")
                .ifBlank { stringResource(R.string.spool_unnamed, spool.id) },
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
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
                fontFamily = Geist,
                fontSize = fsSp(15f, t.fs).sp,
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
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
            maxLines = 1,
        )
        if (spool.id == activeId) {
            Text(
                text = "Loaded",
                color = t.go,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(15f, t.fs).sp,
            )
        } else if (spool.archived) {
            Text(
                text = "Archived",
                color = t.heat,
                fontFamily = Geist,
                fontSize = fsSp(15f, t.fs).sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Detail card sub-components (reused from original SpoolScreen)
// ─────────────────────────────────────────────────────────────────────────────

/** The detail split swatch (D-08 normalized; multi-color split; neutral marker on absence). */
@Composable
private fun DetailSwatch(swatches: List<String>, sizeSp: Float, t: ThemeTokens) {
    val size = sizeSp.dp
    if (swatches.isEmpty()) {
        Box(Modifier.size(size).clip(CircleShape).background(t.surface2).border(BorderStroke(1.dp, t.hair), CircleShape))
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        swatches.take(4).forEach { hex ->
            Box(
                Modifier.size(size).clip(CircleShape)
                    .background(parseNormalizedHex(hex) ?: t.surface2)
                    .border(BorderStroke(1.dp, t.hair), CircleShape),
            )
        }
    }
}

/** A label-less detail value (body size; "—" when absent). */
@Composable
private fun DetailValue(value: String?, fontSizeSp: Float, modifier: Modifier, t: ThemeTokens) {
    Text(
        text = value?.ifBlank { null } ?: stringResource(R.string.spool_value_unset),
        color = if (value.isNullOrBlank()) t.text3 else t.text,
        fontFamily = Geist,
        fontWeight = FontWeight.Medium,
        fontSize = fontSizeSp.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Line-3 weight text: "remaining/original g" (e.g. `579/1000 g`); degrades to remaining-only or "—". */
@Composable
private fun spoolWeightText(spool: SpoolmanSpool): String {
    val remaining = spool.remainingWeight ?: return stringResource(R.string.spool_value_unset)
    val original = spool.originalWeight
    return if (original != null) {
        stringResource(R.string.spool_weight_pair, remaining.roundToInt(), original.roundToInt())
    } else {
        stringResource(R.string.spool_weight_single, remaining.roundToInt())
    }
}

/** Line-5 temperature text: `210°C`, or "—" when unset. */
@Composable
private fun tempText(temp: Int?): String =
    temp?.let { stringResource(R.string.spool_temp, it) } ?: stringResource(R.string.spool_value_unset)

/** An icon-led detail badge (loaded green / archived amber). */
@Composable
private fun DetailBadge(
    icon: DinghyIcon,
    text: String,
    contentDescription: String,
    textSp: Float,
    iconSp: Float,
    color: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DinghyIconView(icon, tint = color, sizeDp = iconSp.dp, contentDescription = contentDescription)
        Text(text, color = color, fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = textSp.sp)
    }
}

/**
 * Parse a (possibly un-normalized) filament hex to a Compose [Color] (D-08); guards via
 * [normalizeColorHex] (accepts `#`/no-`#`, 6/8 hex digits, else null) and never throws.
 *
 * Promoted from `private` to `internal` top-level (18.3-01) so the SpoolGlyph (plan 02) and the
 * surface-wiring (plan 03) call this ONE shared helper instead of duplicating the parse logic
 * (Pitfall 6). It lives in the `works.mees.dinghy.ui.spool` package, visible to every spool screen
 * without an import.
 */
internal fun parseNormalizedHex(hex: String): Color? {
    val normalized = normalizeColorHex(hex) ?: return null
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}
