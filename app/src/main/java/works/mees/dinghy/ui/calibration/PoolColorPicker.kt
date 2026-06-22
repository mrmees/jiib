package works.mees.dinghy.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import works.mees.dinghy.render.resolveMeshColor
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.toTextStyle

/**
 * Pick a bed-mesh ramp color from the theme data-pool (Accent + up to 4 slots).
 *
 * Options: -1 = Accent sentinel; 0..3 = pool slots. Each tile is backgrounded by
 * [resolveMeshColor] so the swatches track theme changes live. The selected tile shows an
 * accent outline + soft fill (mirrors the selection treatment in SpoolPicker.ColorTile).
 * Missing pool slots (pool size < 4) are omitted — guard via getOrNull.
 *
 * Layout: fill-to-fit row of tiles, orientation-aware (landscape = label left of swatch,
 * portrait = swatch above label).
 *
 * @param selected stored Int selector (−1 for Accent, 0..3 for pool slots).
 * @param onPick   called with the new selector when a tile is tapped.
 */
@Composable
internal fun PoolColorPicker(
    selected: Int,
    onPick: (Int) -> Unit,
    t: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    // Build visible options: always show the Accent sentinel; show pool slots that exist.
    val poolSize = t.pool.size
    val options: List<Int> = buildList {
        add(-1)                             // Accent sentinel
        for (slot in 0..3) {
            if (t.pool.getOrNull(slot) != null) add(slot)
        }
    }
    val labels: Map<Int, String> = buildMap {
        put(-1, "Accent")
        for (slot in 0..3) { put(slot, "Pool ${slot + 1}") }
    }

    BoxWithConstraints(modifier) {
        val landscape = maxWidth > maxHeight
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { sel ->
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
    fun Swatch(mod: Modifier) {
        androidx.compose.foundation.layout.Box(
            mod
                .aspectRatio(1f)
                .clip(CircleShape)
                .border(BorderStroke(2.dp, t.hair), CircleShape)
                .background(color),
        )
    }

    if (landscape) {
        Row(
            tileMod,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TileLabel(Modifier.weight(1f))
            Swatch(Modifier.fillMaxHeight(0.7f))
        }
    } else {
        Column(
            tileMod,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            Swatch(Modifier.fillMaxHeight(0.6f))
            TileLabel(Modifier)
        }
    }
}
