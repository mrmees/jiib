package works.mees.dinghy.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.min
import works.mees.dinghy.render.bedMeshPoolColors
import works.mees.dinghy.render.resolveMeshColor
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.toTextStyle

/**
 * Pick a bed-mesh ramp color from the full 8-entry pool (intent colors + data pool).
 *
 * Options: 0..7 indices into [bedMeshPoolColors] (0=Accent, 1=Stop, 2=Heat, 3=Go, 4..7=pool
 * slots). Each tile is backgrounded by [resolveMeshColor] so the swatches track theme changes
 * live. The selected tile shows an accent outline + soft fill (mirrors SpoolPicker.ColorTile).
 *
 * Layout: fill-to-fit row of tiles, orientation-aware (landscape = label left of swatch,
 * portrait = swatch above label).
 *
 * @param selected stored Int selector (0..7).
 * @param onPick   called with the new selector when a tile is tapped.
 */
@Composable
internal fun PoolColorPicker(
    selected: Int,
    onPick: (Int) -> Unit,
    t: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    // Build visible options: all available entries in bedMeshPoolColors (up to 8).
    val options: List<Int> = bedMeshPoolColors(t).indices.toList()
    val intentLabels = listOf("Accent", "Stop", "Heat", "Go")
    val labels: Map<Int, String> = buildMap {
        for (i in options) {
            put(i, if (i < intentLabels.size) intentLabels[i] else "Pool ${i - intentLabels.size + 1}")
        }
    }

    BoxWithConstraints(modifier) {
        val landscape = maxWidth > maxHeight
        // Lay tiles as a 4-column × 2-row grid (mirrors SpoolPicker.ColorSwatchGrid chunked pattern).
        // chunked(4) handles a short final row gracefully — no crash on fewer than 8 entries.
        val rows = options.chunked(4)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEach { rowOptions ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    rowOptions.forEach { sel ->
                        PoolColorTile(
                            sel = sel,
                            label = labels[sel] ?: "Accent",
                            isSelected = sel == selected,
                            onPick = onPick,
                            t = t,
                            landscape = landscape,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    // Pad empty cells in a short last row so tiles share equal width
                    repeat(4 - rowOptions.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** One color tile in the pool picker. Selection = accent outline + soft fill. */
@Composable
private fun PoolColorTile(
    sel: Int,
    label: String,
    isSelected: Boolean,
    onPick: (Int) -> Unit,
    t: ThemeTokens,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val color: Color = resolveMeshColor(t, sel)
    val shape = RoundedCornerShape(t.rCtrl)
    val tileMod = modifier
        .clip(shape)
        .border(BorderStroke(2.dp, if (isSelected) t.accentLine else t.outline), shape)
        .background(if (isSelected) t.accentSoft else Color.Transparent)
        .clickable { onPick(sel) }
        .padding(8.dp)
    val labelColor = if (isSelected) t.accent2 else t.text

    @Composable
    fun TileLabel(mod: Modifier) = Text(
        text = label,
        color = labelColor,
        style = DinghyType.caption.toTextStyle(t),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = mod,
    )

    @Composable
    fun Swatch(heightFrac: Float) {
        // Cap the swatch so it never exceeds the tile's width in portrait.
        // BoxWithConstraints gives us the tile's inner dimensions after padding;
        // the diameter is min(tileWidth, tileHeight*frac, 80.dp).
        BoxWithConstraints {
            val diameter = min(maxWidth, min(maxHeight * heightFrac, 80.dp))
            androidx.compose.foundation.layout.Box(
                Modifier
                    .size(diameter)
                    .clip(CircleShape)
                    .border(BorderStroke(2.dp, t.hair), CircleShape)
                    .background(color),
            )
        }
    }

    if (landscape) {
        Row(
            tileMod,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileLabel(Modifier.weight(1f))
            Swatch(0.7f)
        }
    } else {
        Column(
            tileMod,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Swatch(0.6f)
            TileLabel(Modifier)
        }
    }
}
