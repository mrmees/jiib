package works.mees.jiib.designsystem.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * The outer edge-registration frame inset (LAYOUT.md R26 / C-E2): the FIRST visible outline lands
 * [RegionInset] from the region edge, the LAST lands [RegionInset] from the opposite edge. Derived
 * from [ListFrameInset] so the WHOLE app's 8dp grid is one knob.
 */
val RegionInset: Dp = ListFrameInset

/** The gap between stacked elements inside a [RegisteredRegion]. One source with [RegionInset]. */
val RegionGap: Dp = ListFrameInset

/**
 * The single owner of a screen region's 8dp registration frame (LAYOUT.md R26). A [Column] that
 * frames all four edges at [RegionInset] and spaces its DIRECT children by [RegionGap]. Every
 * region-filling component ([works.mees.jiib.designsystem.components.FocusFrame],
 * [ListBlock], [works.mees.jiib.designsystem.components.FootButtonBar],
 * [works.mees.jiib.designsystem.components.SortRow]/[works.mees.jiib.designsystem.components.FilterRow])
 * is authored FLUSH and never adds its own frame padding.
 *
 * [ScreenScaffold] wraps each slot in this by default; non-scaffold screens (Console) and
 * self-contained field helpers (PrintStatus) call it directly.
 *
 * ⚠ [RegionGap] spaces only DIRECT children. Content whose single child is a helper/Column that owns
 * its own padding must be flattened so its parts are direct children here (see the plan's pattern 2).
 */
@Composable
fun RegisteredRegion(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier.padding(RegionInset),
        verticalArrangement = Arrangement.spacedBy(RegionGap),
        content = content,
    )
}
