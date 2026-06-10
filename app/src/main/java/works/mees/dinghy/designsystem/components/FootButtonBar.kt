package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * A row of foot-of-list action controls (docs/ui_design/COMPONENTS.md §"Component catalog — FootButtonBar").
 *
 * ## STRUCTURAL RULE — placement inside `field`, NOT `gutter` (Pitfall 1)
 * [FootButtonBar] lives as the **last element inside the `field` lambda Column** of a
 * `ScreenScaffold`. Redesigned screens pass `gutter = null` to `ScreenScaffold` and place
 * their actions here instead. This avoids the portrait gap that appears when actions live in
 * the legacy gutter slot.
 *
 * **Anti-pattern to avoid:** do NOT pass [FootButtonBar] to `ScreenScaffold`'s `gutter` slot —
 * the gutter slot is preserved only for pre-redesign screens (backward compat). The new grammar
 * is: `gutter = null`, actions in `FootButtonBar` at the foot of `field`.
 *
 * ## Usage
 * ```kotlin
 * ScreenScaffold(
 *     focus = { … },
 *     field = {
 *         ListBlock(modifier = Modifier.weight(1f)) { … }
 *         FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(8.dp)) {
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
 *                 intent = Intent.Neutral,
 *             )
 *         }
 *     },
 *     gutter = null,   // redesigned screens null the gutter — actions are in FootButtonBar above
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
 * @param modifier caller-supplied modifier (e.g. `Modifier.padding(8.dp)`).
 * @param content  the [OutlinedControl] instances rendered with `Modifier.weight(1f)` for
 *                 equal distribution.
 */
@Composable
fun FootButtonBar(
    uDp: Dp,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = uDp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
