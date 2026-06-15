package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.dinghy.designsystem.layout.LocalUnitDp
import works.mees.dinghy.designsystem.layout.controlHeight

/**
 * A row of foot-of-list action controls (docs/ui_design/COMPONENTS.md §"Component catalog — FootButtonBar").
 *
 * ## STRUCTURAL RULE — placement inside `field` (Pitfall 1)
 * [FootButtonBar] lives as the **last element inside the `field` lambda Column** of a
 * `ScreenScaffold`. It is the successor of the retired Gutter region (the `ScreenScaffold`
 * gutter slot was DELETED 2026-06-12 with the R1 PrintStatus migration) — screen actions
 * always live here, on the field's own grid, avoiding the portrait gap the old full-width
 * gutter strip created.
 *
 * ## Usage
 * ```kotlin
 * ScreenScaffold(
 *     focus = { … },
 *     field = {
 *         ListBlock(modifier = Modifier.weight(1f)) { … }
 *         FootButtonBar(uDp = grid.uDp) {
 *             OutlinedControl(
 *                 label = "",
 *                 onClick = onLoad,
 *                 modifier = Modifier.weight(1f),
 *                 icon = DinghyIcons.ExpandCircleUp,
 *                 intent = Intent.Accent,
 *             )
 *             OutlinedControl(
 *                 label = "",
 *                 onClick = onUnload,
 *                 modifier = Modifier.weight(1f),
 *                 icon = DinghyIcons.ExpandCircleDown,
 *                 intent = Intent.Go,
 *             )
 *         }
 *     },
 * )
 * ```
 *
 * ## Token compliance
 * [FootButtonBar] itself has no chrome colors — it is a structural Row wrapper. The controls
 * inside it (typically [works.mees.dinghy.designsystem.control.OutlinedControl]) carry their
 * own token-routed colors via their [Intent].
 *
 * @param uDp      one unit U from [works.mees.dinghy.designsystem.layout.rememberUnitGrid];
 *                 used as the row's [heightIn] minimum so the foot bar matches the grid unit.
 * @param modifier caller-supplied modifier — flush; the enclosing [works.mees.dinghy.designsystem.layout.RegisteredRegion]
 *                 owns the 8dp frame + inter-element gap; non-region callers must pass their own padding.
 * @param content  the [OutlinedControl] instances rendered with `Modifier.weight(1f)` for
 *                 equal distribution.
 */
@Composable
fun FootButtonBar(
    uDp: Dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    // R24: provide U to the controls inside so their glyphs size at the 0.6U icon tier
    // (OutlinedControl reads LocalUnitDp; null elsewhere falls back to legacy sizing).
    CompositionLocalProvider(LocalUnitDp provides uDp) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                // The single shared exact-1U rule (controlHeight) — same as every other control row,
                // so the foot bar can't drift off 1U vs focus tiles. Flush: the enclosing
                // RegisteredRegion owns the 8dp frame + inter-element gap.
                .controlHeight(uDp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}
