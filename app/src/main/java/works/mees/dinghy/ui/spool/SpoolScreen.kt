package works.mees.dinghy.ui.spool

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
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
 * @param onBack the red Back gutter exit.
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
        ScreenScaffold(
            focus = {
                // Detail fills the flexible top; the chips sit at the bottom of the Focus pane.
                Box(Modifier.fillMaxWidth().weight(1f).padding(8.dp)) {
                    SpoolDetailFocus(
                        spool = selected,
                        isActive = selected?.id == state.activeStatus?.activeSpoolId,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                SpoolFilterControls(
                    state = state,
                    onSelectSort = { scope.launch { holder.applySort(it) } },
                    onOpenFilter = { openFilter = it },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            },
            field = {
                SpoolPicker(
                    state = state,
                    onRowClick = { holder.selectSpool(it) },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedControl(
                        label = "Back",
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Danger, // back = red (THEME-04).
                        symbol = "arrow_back",
                    )
                    OutlinedControl(
                        label = "Scan",
                        onClick = onScan,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent, // physical/command accent.
                        symbol = "qr_code_scanner",
                    )
                    OutlinedControl(
                        // Set the selected spool active (D-13 → post_spool_id {spool_id}) via the holder's
                        // change-during-print mutator: this re-points active-spool tracking ONLY and does
                        // NOT interrupt a running print — there is NO print-state gating (the change is
                        // allowed mid-print, the whole point of change-during-print). The holder reconciles
                        // the resulting notify_active_spool_set into its "Loaded" mark (D-10). No-op when
                        // nothing is selected OR no live session (the dispatcher is null then).
                        label = "Load spool",
                        onClick = {
                            val id = selected?.id
                            if (id != null) {
                                holder.setActiveSpool(dispatcher, id)
                            }
                        },
                        // Long-press UNLOADS whatever spool is currently loaded (D-13 → post_spool_id {}),
                        // Matthew 2026-06-04. No-op when nothing is loaded.
                        onLongClick = {
                            if (state.activeStatus?.activeSpoolId != null) {
                                holder.clearActiveSpool(dispatcher)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Go, // green accept/commit.
                        symbol = "check_circle",
                    )
                }
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
    }
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
                MaterialSymbol("inventory_2", tint = t.text3, sizeSp = fsSp(64f, t.fs))
            }
            return@Box
        }
        val filament = spool.filament
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Title line: the D-08 split swatch + the filament MATERIAL only (Matthew 2026-06-04).
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DetailSwatch(filament?.colorSwatches ?: emptyList(), t)
                Text(
                    text = filament?.material?.ifBlank { null } ?: "Spool ${spool.id}",
                    color = t.text,
                    fontFamily = Geist,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = fsSp(22f, t.fs).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Line 2: storefront → vendor (no label) · palette → color/filament name (no label).
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaterialSymbol("storefront", tint = t.text2, sizeSp = fsSp(20f, t.fs))
                DetailValue(filament?.vendor?.name, Modifier.weight(1f), t)
                MaterialSymbol("palette", tint = t.text2, sizeSp = fsSp(20f, t.fs))
                DetailValue(filament?.name, Modifier.weight(1f), t)
            }
            // Line 3: scale → "remaining/original g" tabular hero (drop the "remaining" label).
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaterialSymbol("scale", tint = t.text2, sizeSp = fsSp(24f, t.fs))
                Text(
                    text = spoolWeightText(spool),
                    color = if (spool.remainingWeight == null) t.text3 else t.text,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(26f, t.fs).sp,
                    maxLines = 1,
                )
            }
            // Line 4: calendar_add_on → the Spoolman registration date (date part only).
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaterialSymbol("calendar_add_on", tint = t.text2, sizeSp = fsSp(20f, t.fs))
                Text(
                    text = spool.registered?.substringBefore('T')?.ifBlank { null } ?: "—",
                    color = t.text,
                    fontFamily = GeistMono,
                    fontSize = fsSp(17f, t.fs).sp,
                    maxLines = 1,
                )
            }
            // Line 5: nozzle → recommended nozzle temp · bed → recommended bed temp (Spoolman settings).
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawableIcon(R.drawable.nozzle, t.text2, fsSp(22f, t.fs))
                Text(tempText(filament?.settingsExtruderTemp), color = t.text, fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontSize = fsSp(20f, t.fs).sp, maxLines = 1)
                DrawableIcon(R.drawable.heat_bed, t.text2, fsSp(22f, t.fs))
                Text(tempText(filament?.settingsBedTemp), color = t.text, fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontSize = fsSp(20f, t.fs).sp, maxLines = 1)
            }
            if (isActive) {
                DetailBadge("check_circle", "Loaded on this printer", t.go, t)
            }
            if (spool.archived) {
                DetailBadge("archive", "Archived — verify before loading", t.heat, t)
            }
        }
    }
}

/** The detail split swatch (D-08 normalized; multi-color split; neutral marker on absence). */
@Composable
private fun DetailSwatch(swatches: List<String>, t: ThemeTokens) {
    val size = fsSp(24f, t.fs).dp
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

/** A label-less detail value (17sp; "—" when absent). Used for the vendor / color-name on line 2. */
@Composable
private fun DetailValue(value: String?, modifier: Modifier, t: ThemeTokens) {
    Text(
        text = value?.ifBlank { null } ?: "—",
        color = if (value.isNullOrBlank()) t.text3 else t.text,
        fontFamily = Geist,
        fontWeight = FontWeight.Medium,
        fontSize = fsSp(17f, t.fs).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Render a vector drawable (nozzle / heat_bed) tinted via the token system, sized in sp (like fsSp). */
@Composable
private fun DrawableIcon(resId: Int, tint: Color, sizeSp: Float) {
    Icon(
        painter = painterResource(resId),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(sizeSp.dp),
    )
}

/** Line-3 weight text: "remaining/original g" (e.g. `579/1000 g`); degrades to remaining-only or "—". */
private fun spoolWeightText(spool: SpoolmanSpool): String {
    val remaining = spool.remainingWeight ?: return "—"
    val original = spool.originalWeight
    return if (original != null) {
        "${remaining.roundToInt()}/${original.roundToInt()} g"
    } else {
        "${remaining.roundToInt()} g"
    }
}

/** Line-5 temperature text from a Spoolman filament setting: `210°C`, or "—" when unset. */
private fun tempText(temp: Int?): String = temp?.let { "$it°C" } ?: "—"

/** An icon-led detail badge (loaded green / archived amber). */
@Composable
private fun DetailBadge(symbol: String, text: String, color: Color, t: ThemeTokens) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(symbol, tint = color, sizeSp = fsSp(20f, t.fs))
        Text(text, color = color, fontFamily = Geist, fontWeight = FontWeight.Medium, fontSize = fsSp(15f, t.fs).sp)
    }
}

/** Parse an already-normalized hex to a Compose [Color] (D-08); guard via [normalizeColorHex]. */
private fun parseNormalizedHex(hex: String): Color? {
    val normalized = normalizeColorHex(hex) ?: return null
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}
