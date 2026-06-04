package works.mees.dinghy.ui.spool

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.spool.normalizeColorHex
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The fixed color-palette swatches (D-06; docs/view_specific_notes/spoolman.md §Spool Picker). Tapping
 * one fires the SLOW color-similarity two-step (ONLY on tap). A canonical normalized hex backs each.
 */
private val PALETTE_SWATCHES: List<Pair<String, String>> = listOf(
    "Black" to "#000000",
    "White" to "#FFFFFF",
    "Gray" to "#808080",
    "Red" to "#FF0000",
    "Orange" to "#FF8000",
    "Yellow" to "#FFFF00",
    "Green" to "#00C000",
    "Blue" to "#0050FF",
    "Purple" to "#8000FF",
    "Pink" to "#FF60C0",
    "Brown" to "#7A4A20",
)

/**
 * The Spool-picker FIELD (SPOOL-03): a sort toggle + the filter chip rows (material family / color
 * palette / vendor / location) over a dense scrollable spool list. Mirrors the Files dense-list grammar
 * and the Webcam CamPicker pure-Compose precedent — a Compose [LazyColumn], so the Views-in-Compose
 * pinned-height scroll lesson is moot (no RecyclerView here). All color via [LocalTokens] (THEME-01).
 *
 * Font scale matches the FilesScreen rows (D-16): row primary 17–18sp, metadata floor 15sp, chips 15sp,
 * remaining tabular value 18sp — never smaller. Every row shows the spool's ACTUAL swatch (D-06: a color
 * filter can match unexpectedly, so the row never lies about its own color).
 *
 * NB: row actions are NEVER gated on print-state (avoids the Files Delete-blocks-all-during-print defect).
 *
 * @param state the picker state (list + chip universes + applied filters/sort + active-spool mark).
 * @param onRowClick select a spool (its detail fills the Focus).
 * @param onToggleMaterial/[onToggleVendor]/[onToggleLocation]/[onTapSwatch]/[onSelectSort] chip actions.
 */
@Composable
fun SpoolPicker(
    state: SpoolPickerState,
    onRowClick: (SpoolmanSpool) -> Unit,
    onToggleMaterial: (String) -> Unit,
    onToggleVendor: (String) -> Unit,
    onToggleLocation: (String) -> Unit,
    onTapSwatch: (String) -> Unit,
    onSelectSort: (SpoolSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val activeId = state.activeStatus?.activeSpoolId
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Sort toggle (recent / low-remaining / default).
        SortRow(active = state.sort, onSelect = onSelectSort, t = t)

        // Material family chips (D-05) — the real inventory materials, comma-multi-select.
        if (state.materials.isNotEmpty()) {
            ChipRow {
                state.materials.forEach { material ->
                    FilterChip(
                        label = material,
                        selected = state.filters.materialFamilies.any { it.equals(material, ignoreCase = true) },
                        onClick = { onToggleMaterial(material) },
                        t = t,
                    )
                }
            }
        }

        // Color palette swatches (D-06) — the two-step fires ONLY on tap.
        ChipRow {
            PALETTE_SWATCHES.forEach { (name, hex) ->
                SwatchChip(
                    name = name,
                    hex = hex,
                    selected = state.filters.colorSwatchHex.equals(hex, ignoreCase = true),
                    onClick = { onTapSwatch(hex) },
                    t = t,
                )
            }
        }

        // Vendor + location chips (D-04). "No location" is the LOCATION_NONE sentinel.
        if (state.vendors.isNotEmpty() || state.locations.isNotEmpty()) {
            ChipRow {
                state.vendors.forEach { vendor ->
                    FilterChip(
                        label = vendor,
                        selected = state.filters.vendor.equals(vendor, ignoreCase = true),
                        onClick = { onToggleVendor(vendor) },
                        t = t,
                    )
                }
                state.locations.forEach { location ->
                    FilterChip(
                        label = location,
                        selected = state.filters.location == location,
                        onClick = { onToggleLocation(location) },
                        t = t,
                    )
                }
                FilterChip(
                    label = "No location",
                    selected = state.filters.location == SpoolFilters.LOCATION_NONE,
                    onClick = { onToggleLocation(SpoolFilters.LOCATION_NONE) },
                    t = t,
                )
            }
        }

        // The dense spool list (Compose LazyColumn — scroll lesson moot).
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (state.spools.isEmpty()) {
                Text(
                    text = when {
                        state.loading -> "Loading spools…"
                        state.error != null -> state.error
                        else -> "No spools match. Clear filters to see your inventory."
                    },
                    color = t.text2,
                    fontFamily = Geist,
                    fontSize = fsSp(15f, t.fs).sp,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.spools, key = { it.id }) { spool ->
                        SpoolRow(
                            spool = spool,
                            selected = spool.id == state.selected?.id,
                            isActive = spool.id == activeId,
                            onClick = { onRowClick(spool) },
                            t = t,
                        )
                    }
                }
            }
        }
    }
}

/** One dense spool row: the ACTUAL swatch + material·name + vendor + remaining grams + location/active. */
@Composable
private fun SpoolRow(
    spool: SpoolmanSpool,
    selected: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
    t: ThemeTokens,
) {
    val filament = spool.filament
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = when {
        selected -> t.accentLine
        isActive -> t.go
        else -> t.outline
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BorderStroke(2.dp, outline), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowSwatch(filament?.colorSwatches ?: emptyList(), t)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = listOfNotNull(filament?.material, filament?.name).joinToString(" · ").ifBlank { "Spool ${spool.id}" },
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
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = spool.remainingWeight?.let { "${it.roundToInt()} g" } ?: "—",
                color = if (spool.remainingWeight == null) t.text3 else t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(18f, t.fs).sp,
                maxLines = 1,
            )
            if (isActive) {
                Text("Loaded", color = t.go, fontFamily = Geist, fontWeight = FontWeight.SemiBold, fontSize = fsSp(15f, t.fs).sp)
            } else if (spool.archived) {
                Text("Archived", color = t.heat, fontFamily = Geist, fontSize = fsSp(15f, t.fs).sp)
            }
        }
    }
}

/** The dense-row swatch (smaller than the card's; D-08 normalized split, neutral marker on absence). */
@Composable
private fun RowSwatch(swatches: List<String>, t: ThemeTokens) {
    val size = fsSp(18f, t.fs).dp
    if (swatches.isEmpty()) {
        Box(Modifier.size(size).clip(CircleShape).background(t.surface2).border(BorderStroke(1.dp, t.hair), CircleShape))
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        swatches.take(3).forEach { hex ->
            Box(
                Modifier.size(size).clip(CircleShape)
                    .background(parseNormalizedHex(hex) ?: t.surface2)
                    .border(BorderStroke(1.dp, t.hair), CircleShape),
            )
        }
    }
}

/** The recent / low-remaining / default sort toggle. */
@Composable
private fun SortRow(active: SpoolSort, onSelect: (SpoolSort) -> Unit, t: ThemeTokens) {
    ChipRow {
        FilterChip("Browse", active == SpoolSort.DEFAULT, { onSelect(SpoolSort.DEFAULT) }, t)
        FilterChip("Recent", active == SpoolSort.RECENT, { onSelect(SpoolSort.RECENT) }, t)
        FilterChip("Low remaining", active == SpoolSort.LOW_REMAINING, { onSelect(SpoolSort.LOW_REMAINING) }, t)
    }
}

/** A horizontally-scrollable chip strip (the chip rows can overflow on a narrow Field). */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        content()
    }
}

/** One outline-led filter chip (selected = accent outline + accent text; metadata floor 15sp). */
@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, t: ThemeTokens) {
    Box(
        Modifier
            .clip(RoundedCornerShape(t.rPill))
            .border(BorderStroke(2.dp, if (selected) t.accentLine else t.outline), RoundedCornerShape(t.rPill))
            .background(if (selected) t.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) t.accent2 else t.text2,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(15f, t.fs).sp,
            maxLines = 1,
        )
    }
}

/** A color-palette swatch chip (D-06) — the swatch circle + name; selected = accent outline. */
@Composable
private fun SwatchChip(name: String, hex: String, selected: Boolean, onClick: () -> Unit, t: ThemeTokens) {
    Row(
        Modifier
            .clip(RoundedCornerShape(t.rPill))
            .border(BorderStroke(2.dp, if (selected) t.accentLine else t.outline), RoundedCornerShape(t.rPill))
            .background(if (selected) t.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(fsSp(16f, t.fs).dp).clip(CircleShape)
                .background(parseNormalizedHex(hex) ?: t.surface2)
                .border(BorderStroke(1.dp, t.hair), CircleShape),
        )
        Text(
            text = name,
            color = if (selected) t.accent2 else t.text2,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(15f, t.fs).sp,
            maxLines = 1,
        )
    }
}

/**
 * Parse an already-normalized hex to a Compose [Color] (D-08); re-runs [normalizeColorHex] as a guard so a
 * raw value still can't crash the render — a non-normalizable value → null (the neutral marker).
 */
private fun parseNormalizedHex(hex: String): Color? {
    val normalized = normalizeColorHex(hex) ?: return null
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}
