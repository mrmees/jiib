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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

/**
 * The fixed color-palette swatches (D-06). Single source of truth for both the picker grid AND the
 * seed-hint family lookup (SpoolHolder.seedPrefilter) — they must NOT drift. Tapping one buckets the
 * library by [colorFamily]; each hex self-classifies to its own name (ColorFamilyTest).
 */
internal val PALETTE_SWATCHES: List<Pair<String, String>> = listOf(
    "Black" to "#000000",
    "White" to "#FFFFFF",
    "Natural" to "#EDE6D6",
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
 * The Color selector as a FILL-TO-FIT grid (Matthew, 2026-06-04 — every swatch on ONE screen, no scroll).
 * 3 columns × the 12 named palette = 12 tiles = 4 full rows (no gaps); rows share the height via `weight`
 * so the grid always fits in any orientation. Per-tile layout is orientation-aware: portrait = swatch over
 * title; landscape = title to the LEFT of the swatch. Clearing color is the gutter Clear.
 */
@Composable
internal fun ColorSwatchGrid(
    selectedHex: String?,
    onTapSwatch: (String) -> Unit,
    t: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    val columns = 3
    BoxWithConstraints(modifier) {
        val landscape = maxWidth > maxHeight
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PALETTE_SWATCHES.chunked(columns).forEach { rowSwatches ->
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowSwatches.forEach { (name, hex) ->
                        ColorTile(
                            name = name,
                            hex = hex,
                            selectedHex = selectedHex,
                            onTapSwatch = onTapSwatch,
                            landscape = landscape,
                            t = t,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    repeat(columns - rowSwatches.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** The swatch circle for a named palette color. */
@Composable
private fun ColorSwatchCircle(hex: String, t: ThemeTokens, modifier: Modifier) {
    Box(
        modifier.aspectRatio(1f).clip(CircleShape)
            .border(BorderStroke(2.dp, t.hair), CircleShape)
            .background(parseNormalizedHex(hex) ?: t.surface2),
    )
}

/**
 * One color tile. Portrait = swatch over title (circle scales to row height); landscape = title to the LEFT
 * of the swatch (Matthew, 2026-06-04). Selected = accent outline + soft fill + accent label.
 */
@Composable
private fun ColorTile(
    name: String,
    hex: String,
    selectedHex: String?,
    onTapSwatch: (String) -> Unit,
    landscape: Boolean,
    t: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    val selected = selectedHex.equals(hex, ignoreCase = true)
    val shape = RoundedCornerShape(t.rCtrl)
    val tileMod = modifier
        .clip(shape)
        .border(BorderStroke(2.dp, if (selected) t.accentLine else t.outline), shape)
        .background(if (selected) t.accentSoft else Color.Transparent)
        .clickable { onTapSwatch(hex) }
        .padding(8.dp)
    val labelColor = if (selected) t.accent2 else t.text

    @Composable
    fun TileLabel(mod: Modifier) = Text(
        text = name,
        color = labelColor,
        style = DinghyType.caption.toTextStyle(t),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = mod,
    )

    if (landscape) {
        Row(
            tileMod,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileLabel(Modifier.weight(1f))
            ColorSwatchCircle(hex, t, Modifier.fillMaxHeight(0.7f))
        }
    } else {
        Column(
            tileMod,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            ColorSwatchCircle(hex, t, Modifier.fillMaxHeight(0.6f))
            TileLabel(Modifier)
        }
    }
}

// parseNormalizedHex is an `internal` top-level helper in SpoolScreen.kt (18.3-01) —
// same `works.mees.dinghy.ui.spool` package, resolves with no import.
