package works.mees.dinghy.ui.spool

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.spool.normalizeColorHex
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The Spool screen (Dest.Spool host; SPOOL-03, plan 11-06) — the Files-style picker built on
 * [ScreenScaffold] (Focus/Field/Gutter, LAYOUT.md is LAW). Focus = the selected-spool detail; Field =
 * [SpoolPicker] (the dense list + filter/sort chips); Gutter = red Back / green Set-active / accent Scan.
 * Portrait collapses to a stacked Field-first layout (the Focus shows only once a spool is selected, the
 * FilesScreen idiom). All color via [LocalTokens] (THEME-01); ratio-only sizing (NON-NEGOTIABLE 3).
 *
 * Font scale matches the FilesScreen analogs (D-16): focus values 30sp+ (the remaining/used hero), detail
 * stat labels 17sp, metadata floor 15sp; gutter via [OutlinedControl] (18sp label). No hardcoded `.sp`.
 *
 * ## Two transports (D-07), inherited from the holder
 * Inventory list/filter reads ride [holder] (the lean SpoolmanClient). The active-spool WRITE (Set/Clear)
 * is dispatched here through [dispatcher] (`server.spoolman.post_spool_id` — D-13 `{}` clears) — the
 * holder reconciles the resulting `notify_active_spool_set` into its "loaded" marks (D-10).
 *
 * @param holder the per-session picker holder (state + mutators).
 * @param dispatcher the session action dispatcher (null when idle — Set/Clear no-op until reconnected).
 * @param onBack the neutral Back gutter exit (D-10).
 * @param onScan opens the QR scan sub-surface (the 11-07 surface; a no-op hook until wired).
 * @param prefilter the D-04 gcode-aware prefilter seed carried from a Files spool-warning "Pick spool"
 *   (null on a plain drawer open); applied ONCE on entry then cleared via [onPrefilterConsumed].
 * @param onPrefilterConsumed clears the one-time [prefilter] seed after the picker applies it (so a later
 *   manual reopen is unseeded).
 */
@Composable
fun SpoolScreen(
    holder: SpoolHolder,
    dispatcher: CommandDispatcher?,
    client: SpoolmanClient?,
    onBack: () -> Unit,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
    prefilter: SpoolPrefilterSeed? = null,
    onPrefilterConsumed: () -> Unit = {},
) {
    val state by holder.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val selected = state.selected
    // Which filter category's full-screen selector is open (null = none) — Matthew 2026-06-04.
    var openFilter by remember { mutableStateOf<SpoolFilterCategory?>(null) }
    // The spool whose measured-gross-weight page is open (null = none) — D-04 measured-weight correction.
    var measureSpool by remember { mutableStateOf<SpoolmanSpool?>(null) }

    LaunchedEffect(holder) { holder.load() }
    // D-04: apply the gcode-aware prefilter seed ONCE (keyed on the seed identity), then clear it so a
    // later manual reopen of the picker is unseeded. Runs after load() seeds the chip universes; seeding
    // the filters re-issues the list read with the file's material family + color hint.
    LaunchedEffect(holder, prefilter) {
        val seed = prefilter ?: return@LaunchedEffect
        holder.seedPrefilter(seed)
        onPrefilterConsumed()
    }

    // Focus = the selected-spool detail (top, flexible) + the filter/sort chips PINNED AT ITS BOTTOM
    // (Matthew 2026-06-04); the Field is then a pure scrolling list that gets the whole Field height.
    // Focus is always shown so the filters are always reachable (both orientations).
    Box(modifier.fillMaxSize()) {
        SpoolContent(
            state = state,
            selected = selected,
            isActive = selected?.id == state.activeStatus?.activeSpoolId,
            onSelectSort = { scope.launch { holder.applySort(it) } },
            onOpenFilter = { openFilter = it },
            onRowClick = { holder.selectSpool(it) },
            onMeasure = { selected?.let { measureSpool = it } },
            onBack = onBack,
            onScan = onScan,
            // Set the selected spool active (D-13 → post_spool_id {spool_id}) via the holder's
            // change-during-print mutator: this re-points active-spool tracking ONLY and does NOT interrupt
            // a running print — there is NO print-state gating (the change is allowed mid-print, the whole
            // point of change-during-print). The holder reconciles the resulting notify_active_spool_set
            // into its "Loaded" mark (D-10). No-op when nothing is selected OR no live session.
            onSetActive = {
                val id = selected?.id
                if (id != null) holder.setActiveSpool(dispatcher, id)
            },
            // Long-press UNLOADS whatever spool is currently loaded (D-13 → post_spool_id {}),
            // Matthew 2026-06-04. No-op when nothing is loaded.
            onClearActive = {
                if (state.activeStatus?.activeSpoolId != null) holder.clearActiveSpool(dispatcher)
            },
        )

        // The full-screen filter selector overlays the whole screen when a category button is tapped.
        openFilter?.let { category ->
            SpoolFilterPickerOverlay(
                category = category,
                state = state,
                onToggleMaterial = { scope.launch { holder.toggleMaterialFamily(it) } },
                onToggleVendor = { scope.launch { holder.toggleVendor(it) } },
                onTapSwatch = { scope.launch { holder.applyColorSwatch(it) } },
                onMultiColor = { scope.launch { holder.applyMultiColor() } },
                onClear = {
                    scope.launch {
                        when (category) {
                            SpoolFilterCategory.TYPE -> holder.clearMaterialFamilies()
                            SpoolFilterCategory.COLOR -> holder.clearColor()
                            SpoolFilterCategory.MFG -> holder.clearVendor()
                        }
                    }
                },
                onDone = { openFilter = null },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The full-screen measured-gross-weight page overlays the screen when the detail weight is tapped
        // (D-04). On a successful measure, refresh the list so the corrected remaining shows, then dismiss.
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
 * STATELESS preview/render seam (18-07, the D-01/D-02 Spool exemplar). Renders the same Focus/Field/Gutter
 * scaffold as the live [SpoolScreen] from a pure [SpoolPickerState] fixture — NO holder, NO dispatcher, NO
 * Moonraker — so the dense-data archetype previews in Studio across the theme combos + fs=L (SC-1). The
 * live-only overlays (filter selector, measured-weight page) are NOT part of this seam (they need the
 * holder/client); the preview proves the base list+detail+gutter surface, mirroring the anchor's treatment
 * of its live-only modals. All side-effect callbacks default to no-ops.
 */
@Composable
fun SpoolScreen(
    state: SpoolPickerState,
    modifier: Modifier = Modifier,
    onSelectSort: (SpoolSortKey) -> Unit = {},
    onOpenFilter: (SpoolFilterCategory) -> Unit = {},
    onRowClick: (SpoolmanSpool) -> Unit = {},
    onMeasure: () -> Unit = {},
    onBack: () -> Unit = {},
    onScan: () -> Unit = {},
    onSetActive: () -> Unit = {},
    onClearActive: () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        SpoolContent(
            state = state,
            selected = state.selected,
            isActive = state.selected?.id == state.activeStatus?.activeSpoolId,
            onSelectSort = onSelectSort,
            onOpenFilter = onOpenFilter,
            onRowClick = onRowClick,
            onMeasure = onMeasure,
            onBack = onBack,
            onScan = onScan,
            onSetActive = onSetActive,
            onClearActive = onClearActive,
        )
    }
}

/**
 * The shared, container-free scaffold (Focus = detail + filter chips · Field = the dense [SpoolPicker] list
 * · Gutter = red-free Back / accent Scan / green Set-active). Both the live [SpoolScreen] (holder/dispatcher
 * resolved) and the stateless preview overload render byte-identically through here — the side effects are
 * passed as lambdas so a preview drives it with no-ops (SC-1).
 */
@Composable
private fun SpoolContent(
    state: SpoolPickerState,
    selected: SpoolmanSpool?,
    isActive: Boolean,
    onSelectSort: (SpoolSortKey) -> Unit,
    onOpenFilter: (SpoolFilterCategory) -> Unit,
    onRowClick: (SpoolmanSpool) -> Unit,
    onMeasure: () -> Unit,
    onBack: () -> Unit,
    onScan: () -> Unit,
    onSetActive: () -> Unit,
    onClearActive: () -> Unit,
) {
    ScreenScaffold(
        focus = {
            // Detail fills the flexible top; the chips sit at the bottom of the Focus pane.
            Box(Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
                SpoolDetailFocus(
                    spool = selected,
                    isActive = isActive,
                    onMeasure = onMeasure,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            SpoolFilterControls(
                state = state,
                onSelectSort = onSelectSort,
                onOpenFilter = onOpenFilter,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )
        },
        field = {
            SpoolPicker(
                state = state,
                onRowClick = onRowClick,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
        },
        gutter = {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedControl(
                    label = stringResource(R.string.common_back),
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    intent = Intent.Neutral, // D-10: plain nav spends no safety color (matches Move).
                    symbol = "arrow_back",
                )
                // Icon-only Scan/Set buttons: the shared OutlinedControl has no a11y cd param yet (its
                // symbol/label API rides the Phase-22 backfill — out of this exemplar's tokenization
                // boundary, Codex MEDIUM-6); their cd_spool_* keys are pre-seeded in strings.xml for that
                // backfill to wire once OutlinedControl gains a contentDescription parameter.
                OutlinedControl(
                    label = "",
                    onClick = onScan,
                    modifier = Modifier.weight(1f),
                    intent = Intent.Accent, // physical/command accent.
                    symbol = "qr_code",
                )
                OutlinedControl(
                    label = "",
                    onClick = onSetActive,
                    onLongClick = onClearActive,
                    modifier = Modifier.weight(1f),
                    intent = Intent.Go, // green accept/commit.
                    symbol = "add_circle",
                )
            }
        },
    )
}

/**
 * The Focus: the selected spool's detail (material / color split swatch / vendor / remaining·used linked
 * (D-04) / location / archived badge D-09). Empty state when nothing is selected (landscape only — portrait
 * never shows the empty Focus). Values via [LocalTokens]; the remaining/used pair is the GeistMono hero.
 */
@Composable
private fun SpoolDetailFocus(
    spool: SpoolmanSpool?,
    isActive: Boolean,
    onMeasure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    // Match the detail window's border to the selected spool's own color (Matthew 2026-06-04): the first
    // valid swatch, falling back to the accent line (valid spool, unknown color) or the hair (no spool).
    val spoolColor = spool?.filament?.colorSwatches?.firstNotNullOfOrNull { parseNormalizedHex(it) }
    val borderColor = spoolColor ?: if (spool != null) t.accentLine else t.hair
    Box(
        modifier
            .clip(shape)
            .border(BorderStroke(3.dp, borderColor), shape)
            .background(t.surface)
            .padding(16.dp),
    ) {
        if (spool == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                DinghyIconView(
                    DinghyIcons.Inventory,
                    tint = t.text3,
                    sizeDp = fsSp(64f, t.fs).dp,
                    contentDescription = stringResource(R.string.cd_spool_empty),
                )
            }
            return@Box
        }
        val filament = spool.filament
        // The detail pane uses just TWO type sizes (Matthew 2026-06-04): one larger HEADER for the title,
        // and one consistent smaller BODY size for every secondary line. Icons match the body.
        val headerSp = fsSp(26f, t.fs)
        val bodySp = fsSp(18f, t.fs)
        val iconSp = fsSp(20f, t.fs)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header line (LARGER): the D-08 split swatch + the filament MATERIAL only.
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
            // Line 2 (BODY): storefront → vendor (no label) · palette → color/filament name (no label).
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DinghyIconView(DinghyIcons.Storefront, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_vendor))
                DetailValue(filament?.vendor?.name, bodySp, Modifier.weight(1f), t)
                DinghyIconView(DinghyIcons.Palette, tint = t.text2, sizeDp = iconSp.dp, contentDescription = stringResource(R.string.cd_spool_color))
                DetailValue(filament?.name, bodySp, Modifier.weight(1f), t)
            }
            // Line 3 (BODY): scale → "remaining/original g" (drop the "remaining" label). Tap to correct
            // the measured gross weight (D-04) — the trailing edit glyph hints it's editable.
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
            // Line 4 (BODY): calendar_add_on → the Spoolman registration date (date part only).
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
            // Line 5 (BODY): nozzle → recommended nozzle temp · bed → recommended bed temp.
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
                DetailBadge(
                    icon = DinghyIcons.CheckCircle,
                    text = stringResource(R.string.spool_badge_loaded),
                    contentDescription = stringResource(R.string.cd_spool_loaded),
                    textSp = bodySp, iconSp = iconSp, color = t.go,
                )
            }
            if (spool.archived) {
                DetailBadge(
                    icon = DinghyIcons.Archive,
                    text = stringResource(R.string.spool_badge_archived),
                    contentDescription = stringResource(R.string.cd_spool_archived),
                    textSp = bodySp, iconSp = iconSp, color = t.heat,
                )
            }
        }
    }
}

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

/** A label-less detail value (body size; "—" when absent). Used for the vendor / color-name on line 2. */
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

/** Line-5 temperature text from a Spoolman filament setting: `210°C`, or "—" when unset. */
@Composable
private fun tempText(temp: Int?): String =
    temp?.let { stringResource(R.string.spool_temp, it) } ?: stringResource(R.string.spool_value_unset)

/** An icon-led detail badge (loaded green / archived amber) at the body size. */
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

/** Parse an already-normalized hex to a Compose [Color] (D-08); guard via [normalizeColorHex]. */
private fun parseNormalizedHex(hex: String): Color? {
    val normalized = normalizeColorHex(hex) ?: return null
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}
