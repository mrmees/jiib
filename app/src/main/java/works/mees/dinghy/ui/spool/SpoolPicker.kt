package works.mees.dinghy.ui.spool

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
 * The three filter CATEGORIES surfaced in the Field-takeover picker (23-06 redesign). Tapping a filter
 * tile in the [FilterRow] swaps the Field in-place to show the option list for the selected category — no
 * separate screen push. See [works.mees.dinghy.ui.spool.FieldMode.FilterPicker].
 *  - [TYPE] — fuzzy material families (D-05; multi-select).
 *  - [COLOR] — the palette swatches (D-06; single-select, slow two-step on tap).
 *  - [MFG] — manufacturer / vendor (single-select).
 */
enum class SpoolFilterCategory(val label: String, val icon: String) {
    TYPE("Type", "experiment"),
    COLOR("Color", "palette"),
    MFG("MFG", "storefront"),
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
    val t = LocalTokens.current
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 1 — sort (icon-only: match_case / calendar_clock / scale); the ACTIVE key reads accent and
        // overlays a small direction arrow (↑ asc / ↓ desc) in its corner.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpoolSortKey.entries.forEach { key ->
                val active = key == state.sortKey
                Box(Modifier.weight(1f)) {
                    OutlinedControl(
                        label = "",
                        symbol = key.icon,
                        onClick = { onSelectSort(key) },
                        modifier = Modifier.fillMaxWidth(),
                        intent = if (active) Intent.Accent else Intent.Neutral,
                    )
                    if (active) {
                        MaterialSymbol(
                            name = if (state.sortAscending) "arrow_upward" else "arrow_downward",
                            tint = t.accent2,
                            sizeSp = fsSp(16f, t.fs),
                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                        )
                    }
                }
            }
        }
        // Row 2 — filter categories (icons: experiment / palette / storefront); accent outline when that
        // category has an active filter. Each opens the full-screen selector.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CategoryButton(
                category = SpoolFilterCategory.TYPE,
                active = state.filters.materialFamilies.isNotEmpty(),
                onClick = { onOpenFilter(SpoolFilterCategory.TYPE) },
                modifier = Modifier.weight(1f),
            )
            CategoryButton(
                category = SpoolFilterCategory.COLOR,
                active = state.filters.colorSwatchHex != null,
                onClick = { onOpenFilter(SpoolFilterCategory.COLOR) },
                modifier = Modifier.weight(1f),
            )
            CategoryButton(
                category = SpoolFilterCategory.MFG,
                active = state.filters.vendor != null,
                onClick = { onOpenFilter(SpoolFilterCategory.MFG) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** One icon-only filter-category button (Row 2) — accent outline when its category has an active filter. */
@Composable
private fun CategoryButton(category: SpoolFilterCategory, active: Boolean, onClick: () -> Unit, modifier: Modifier) {
    OutlinedControl(
        label = "",
        symbol = category.icon,
        onClick = onClick,
        modifier = modifier,
        intent = if (active) Intent.Accent else Intent.Neutral,
    )
}

/** A choice in the Color grid: a named palette swatch or the Multi-color option. */
private sealed interface ColorChoice {
    data class Named(val name: String, val hex: String) : ColorChoice
    data object Multi : ColorChoice
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
 * 3 columns × the 11 named palette + Multi-color = 12 tiles = 4 full rows (no gaps); rows share the height
 * via `weight` so the grid always fits in any orientation. Per-tile layout is orientation-aware: portrait
 * = swatch over title; landscape = title to the LEFT of the swatch. Clearing color is the gutter Clear.
 */
@Composable
internal fun ColorSwatchGrid(
    selectedHex: String?,
    onTapSwatch: (String) -> Unit,
    onMultiColor: () -> Unit,
    t: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    val columns = 3
    val choices: List<ColorChoice> = buildList {
        PALETTE_SWATCHES.forEach { (name, hex) -> add(ColorChoice.Named(name, hex)) }
        add(ColorChoice.Multi)
    }
    BoxWithConstraints(modifier) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            landscape = landscape,
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
}

/** The swatch circle for a [ColorChoice] (named solid color or the multi-color sweep). */
@Composable
private fun ColorSwatchCircle(choice: ColorChoice, t: ThemeTokens, modifier: Modifier) {
    val base = modifier.aspectRatio(1f).clip(CircleShape).border(BorderStroke(2.dp, t.hair), CircleShape)
    when (choice) {
        is ColorChoice.Named -> Box(base.background(parseNormalizedHex(choice.hex) ?: t.surface2))
        ColorChoice.Multi -> Box(base.background(MULTICOLOR_BRUSH))
    }
}

/**
 * One color tile. Portrait = swatch over title (circle scales to row height); landscape = title to the LEFT
 * of the swatch (Matthew, 2026-06-04). Selected = accent outline + soft fill + accent label.
 */
@Composable
private fun ColorTile(
    choice: ColorChoice,
    selectedHex: String?,
    onTapSwatch: (String) -> Unit,
    onMultiColor: () -> Unit,
    landscape: Boolean,
    t: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    val selected = when (choice) {
        is ColorChoice.Named -> selectedHex.equals(choice.hex, ignoreCase = true)
        ColorChoice.Multi -> selectedHex == SpoolFilters.MULTICOLOR
    }
    val label = when (choice) {
        is ColorChoice.Named -> choice.name
        ColorChoice.Multi -> "Multi"
    }
    val shape = RoundedCornerShape(t.rCtrl)
    val tileMod = modifier
        .clip(shape)
        .border(BorderStroke(2.dp, if (selected) t.accentLine else t.outline), shape)
        .background(if (selected) t.accentSoft else Color.Transparent)
        .clickable {
            when (choice) {
                is ColorChoice.Named -> onTapSwatch(choice.hex)
                ColorChoice.Multi -> onMultiColor()
            }
        }
        .padding(8.dp)
    val labelColor = if (selected) t.accent2 else t.text

    @Composable
    fun TileLabel(mod: Modifier) = Text(
        text = label,
        color = labelColor,
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = fsSp(15f, t.fs).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = mod,
    )

    if (landscape) {
        // Title to the LEFT of the swatch.
        Row(
            tileMod,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileLabel(Modifier.weight(1f))
            ColorSwatchCircle(choice, t, Modifier.fillMaxHeight(0.7f))
        }
    } else {
        // Swatch over the title.
        Column(
            tileMod,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            ColorSwatchCircle(choice, t, Modifier.fillMaxHeight(0.6f))
            TileLabel(Modifier)
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

// parseNormalizedHex was promoted to an `internal` top-level helper in SpoolScreen.kt (18.3-01) — the
// same `works.mees.dinghy.ui.spool` package, so it resolves here with no import and no duplicated logic.
