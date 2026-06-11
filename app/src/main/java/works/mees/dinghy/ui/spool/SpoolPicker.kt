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
import works.mees.dinghy.theme.Geist
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

// parseNormalizedHex is an `internal` top-level helper in SpoolScreen.kt (18.3-01) —
// same `works.mees.dinghy.ui.spool` package, resolves with no import.
