package works.mees.dinghy.ui.spool

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
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
 * The three filter CATEGORIES surfaced as buttons in the second filter row (Matthew, 2026-06-04). Each
 * opens a full-screen touch selector ([SpoolFilterPickerOverlay]) rather than an inline chip strip.
 *  - [TYPE] — fuzzy material families (D-05; multi-select).
 *  - [COLOR] — the palette swatches (D-06; single-select, slow two-step on tap).
 *  - [MFG] — manufacturer / vendor (single-select).
 */
enum class SpoolFilterCategory(val label: String) {
    TYPE("Type"),
    COLOR("Color"),
    MFG("MFG"),
}

/**
 * The Spool-picker FIELD (SPOOL-03): JUST the dense scrollable spool list, so it gets the WHOLE Field
 * height (the filter/sort controls live at the bottom of the Focus — [SpoolFilterControls]). Mirrors the
 * Files dense-list grammar; a Compose [LazyColumn]. All color via [LocalTokens] (THEME-01).
 *
 * Font scale matches the FilesScreen rows (D-16): row primary 17–18sp, metadata floor 15sp, remaining
 * tabular value 18sp — never smaller. Every row shows the spool's ACTUAL swatch (D-06). Row actions are
 * NEVER gated on print-state (avoids the Files Delete-blocks-all-during-print defect).
 *
 * @param state the picker state (list + applied filters/sort + active-spool mark).
 * @param onRowClick select a spool (its detail fills the Focus).
 */
@Composable
fun SpoolPicker(
    state: SpoolPickerState,
    onRowClick: (SpoolmanSpool) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val activeId = state.activeStatus?.activeSpoolId
    Box(modifier.fillMaxWidth()) {
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

/**
 * The filter / sort controls (SPOOL-03), TWO rows of button-style controls at the bottom of the Focus
 * (Matthew, 2026-06-04 — buttons, not pills):
 *  - Row 1 — Name / Date / Remaining sort. Tapping re-sorts the list; re-tapping the active key flips
 *    direction (↑ asc / ↓ desc), and the active button reads accent.
 *  - Row 2 — Type / Color / MFG. Each opens a full-screen touch selector ([onOpenFilter]); the button
 *    reads accent (and Type shows a count) when that category has an active filter.
 */
@Composable
fun SpoolFilterControls(
    state: SpoolPickerState,
    onSelectSort: (SpoolSortKey) -> Unit,
    onOpenFilter: (SpoolFilterCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1 — sort (Name / Date / Remaining); active button shows direction arrow + accent outline.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpoolSortKey.entries.forEach { key ->
                val active = key == state.sortKey
                val arrow = if (active) (if (state.sortAscending) " ↑" else " ↓") else ""
                OutlinedControl(
                    label = key.label + arrow,
                    onClick = { onSelectSort(key) },
                    modifier = Modifier.weight(1f),
                    intent = if (active) Intent.Accent else Intent.Neutral,
                )
            }
        }
        // Row 2 — filter categories (Type / Color / MFG); accent outline when that category is filtered.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val typeCount = state.filters.materialFamilies.size
            CategoryButton(
                label = if (typeCount > 0) "${SpoolFilterCategory.TYPE.label} ($typeCount)" else SpoolFilterCategory.TYPE.label,
                active = typeCount > 0,
                onClick = { onOpenFilter(SpoolFilterCategory.TYPE) },
                modifier = Modifier.weight(1f),
            )
            CategoryButton(
                label = SpoolFilterCategory.COLOR.label,
                active = state.filters.colorSwatchHex != null,
                onClick = { onOpenFilter(SpoolFilterCategory.COLOR) },
                modifier = Modifier.weight(1f),
            )
            CategoryButton(
                label = SpoolFilterCategory.MFG.label,
                active = state.filters.vendor != null,
                onClick = { onOpenFilter(SpoolFilterCategory.MFG) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One filter-category button (Row 2) — accent outline when its category has an active filter. */
@Composable
private fun CategoryButton(label: String, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    OutlinedControl(
        label = label,
        onClick = onClick,
        modifier = modifier,
        intent = if (active) Intent.Accent else Intent.Neutral,
    )
}

/**
 * The full-screen touch selector for one [SpoolFilterCategory] (Matthew, 2026-06-04). A scrollable list of
 * big selectable option buttons (≥64dp), a category-scoped Clear, and a green Done. Type is multi-select
 * (D-05 families), Color single-select (D-06 swatch — the slow two-step fires on tap), MFG single-select.
 * All color via [LocalTokens]; option text floor 18sp (D-16).
 */
@Composable
fun SpoolFilterPickerOverlay(
    category: SpoolFilterCategory,
    state: SpoolPickerState,
    onToggleMaterial: (String) -> Unit,
    onToggleVendor: (String) -> Unit,
    onTapSwatch: (String) -> Unit,
    onMultiColor: () -> Unit,
    onClearColor: () -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(
        modifier.fillMaxSize().background(t.bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Filter — ${category.label}",
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(22f, t.fs).sp,
        )
        // Content fills the space between title and gutter. COLOR is a no-scroll fill grid (all swatches
        // on ONE screen); TYPE / MFG are scrollable option lists (variable length).
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (category) {
                SpoolFilterCategory.COLOR -> ColorSwatchGrid(
                    selectedHex = state.filters.colorSwatchHex,
                    onTapSwatch = onTapSwatch,
                    onMultiColor = onMultiColor,
                    onAny = onClearColor,
                    t = t,
                    modifier = Modifier.fillMaxSize(),
                )

                SpoolFilterCategory.TYPE -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MATERIAL_FAMILIES.forEach { (label, _) ->
                        OptionButton(
                            label = label,
                            selected = state.filters.materialFamilies.any { it.equals(label, ignoreCase = true) },
                            swatchHex = null,
                            onClick = { onToggleMaterial(label) },
                            t = t,
                        )
                    }
                }

                SpoolFilterCategory.MFG -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.vendors.isEmpty()) {
                        Text(
                            text = "No manufacturers found.",
                            color = t.text2,
                            fontFamily = Geist,
                            fontSize = fsSp(17f, t.fs).sp,
                            modifier = Modifier.padding(8.dp),
                        )
                    }
                    state.vendors.forEach { vendor ->
                        OptionButton(
                            label = vendor,
                            selected = state.filters.vendor.equals(vendor, ignoreCase = true),
                            swatchHex = null,
                            onClick = { onToggleVendor(vendor) },
                            t = t,
                        )
                    }
                }
            }
        }
        // Gutter — category-scoped Clear (red) + Done (green).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedControl(
                label = "Clear",
                onClick = onClear,
                modifier = Modifier.weight(1f),
                intent = Intent.Danger,
                symbol = "close",
            )
            OutlinedControl(
                label = "Done",
                onClick = onDone,
                modifier = Modifier.weight(1f),
                intent = Intent.Go,
                symbol = "check",
            )
        }
    }
}

/** A choice in the Color grid: a named palette swatch, the Multi-color option, or Any (clears color). */
private sealed interface ColorChoice {
    data class Named(val name: String, val hex: String) : ColorChoice
    data object Multi : ColorChoice
    data object Any : ColorChoice
}

/** The "multi-color" tile's rainbow swatch — a sweep gradient (data swatch, not a theme role token). */
private val MULTICOLOR_BRUSH: Brush = Brush.sweepGradient(
    listOf(
        Color(0xFFFF0000), Color(0xFFFF8000), Color(0xFFFFFF00), Color(0xFF00C000),
        Color(0xFF0050FF), Color(0xFF8000FF), Color(0xFFFF0000),
    ),
)

/**
 * The Color selector as a FILL-TO-FIT grid (Matthew, 2026-06-04 — every swatch on ONE screen, no scroll).
 * 3 columns; the rows share the available height via `weight`, so the grid always fits regardless of count
 * or orientation (the swatch circle scales to the row height). Includes the named palette + a Multi-color
 * tile (filters `multi_color_hexes` filaments) + an Any tile (clears the color filter).
 */
@Composable
private fun ColorSwatchGrid(
    selectedHex: String?,
    onTapSwatch: (String) -> Unit,
    onMultiColor: () -> Unit,
    onAny: () -> Unit,
    t: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    val columns = 3
    val choices: List<ColorChoice> = buildList {
        PALETTE_SWATCHES.forEach { (name, hex) -> add(ColorChoice.Named(name, hex)) }
        add(ColorChoice.Multi)
        add(ColorChoice.Any)
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.chunked(columns).forEach { rowChoices ->
            Row(
                Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowChoices.forEach { choice ->
                    ColorTile(
                        choice = choice,
                        selectedHex = selectedHex,
                        onTapSwatch = onTapSwatch,
                        onMultiColor = onMultiColor,
                        onAny = onAny,
                        t = t,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                // Pad a short final row so its tiles keep the same width as the full rows.
                repeat(columns - rowChoices.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

/** One color tile (named swatch / Multi-color sweep / Any ring); the circle scales to the row height. */
@Composable
private fun ColorTile(
    choice: ColorChoice,
    selectedHex: String?,
    onTapSwatch: (String) -> Unit,
    onMultiColor: () -> Unit,
    onAny: () -> Unit,
    t: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    val selected = when (choice) {
        is ColorChoice.Named -> selectedHex.equals(choice.hex, ignoreCase = true)
        ColorChoice.Multi -> selectedHex == SpoolFilters.MULTICOLOR
        ColorChoice.Any -> selectedHex == null
    }
    val label = when (choice) {
        is ColorChoice.Named -> choice.name
        ColorChoice.Multi -> "Multi"
        ColorChoice.Any -> "Any"
    }
    val shape = RoundedCornerShape(t.rCtrl)
    Column(
        modifier
            .clip(shape)
            .border(BorderStroke(2.dp, if (selected) t.accentLine else t.outline), shape)
            .background(if (selected) t.accentSoft else Color.Transparent)
            .clickable {
                when (choice) {
                    is ColorChoice.Named -> onTapSwatch(choice.hex)
                    ColorChoice.Multi -> onMultiColor()
                    ColorChoice.Any -> onAny()
                }
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        // The swatch circle scales with the row height (fillMaxHeight fraction), kept square via aspectRatio.
        val circleMod = Modifier.fillMaxHeight(0.6f).aspectRatio(1f).clip(CircleShape)
            .border(BorderStroke(2.dp, t.hair), CircleShape)
        when (choice) {
            is ColorChoice.Named -> Box(circleMod.background(parseNormalizedHex(choice.hex) ?: t.surface2))
            ColorChoice.Multi -> Box(circleMod.background(MULTICOLOR_BRUSH))
            ColorChoice.Any -> Box(circleMod.background(t.surface2), contentAlignment = Alignment.Center) {
                MaterialSymbol("block", tint = t.text3, sizeSp = fsSp(20f, t.fs))
            }
        }
        Text(
            text = label,
            color = if (selected) t.accent2 else t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(15f, t.fs).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One selectable option button (≥64dp; accent outline + soft fill + check when selected). */
@Composable
private fun OptionButton(
    label: String,
    selected: Boolean,
    swatchHex: String?,
    onClick: () -> Unit,
    t: ThemeTokens,
) {
    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, if (selected) t.accentLine else t.outline), shape)
            .background(if (selected) t.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatchHex != null) {
            Box(
                Modifier.size(fsSp(24f, t.fs).dp).clip(CircleShape)
                    .background(parseNormalizedHex(swatchHex) ?: t.surface2)
                    .border(BorderStroke(1.dp, t.hair), CircleShape),
            )
        }
        Text(
            text = label,
            color = if (selected) t.accent2 else t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            MaterialSymbol("check", tint = t.accent2, sizeSp = fsSp(20f, t.fs))
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

/**
 * Parse an already-normalized hex to a Compose [Color] (D-08); re-runs [normalizeColorHex] as a guard so a
 * raw value still can't crash the render — a non-normalizable value → null (the neutral marker).
 */
private fun parseNormalizedHex(hex: String): Color? {
    val normalized = normalizeColorHex(hex) ?: return null
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}
