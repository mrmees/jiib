package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.dinghy.designsystem.control.ControlSpec
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.IconRef
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
 * ## Usage (count-driven list API — preferred)
 * ```kotlin
 * ScreenScaffold(
 *     focus = { … },
 *     field = {
 *         ListBlock(modifier = Modifier.weight(1f)) { … }
 *         FootButtonBar(uDp = grid.uDp, actions = listOf(
 *             FootAction("Back", DinghyIcons.Back, onBack, Intent.Accent),
 *             FootAction("Save", DinghyIcons.Save, onSave, Intent.Go),
 *         )) // ≤2 → icon+text; ≥3 → icon-only (FOOT_BAR_ICON_ONLY_THRESHOLD)
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

// ---------------------------------------------------------------------------
// Count-driven list API (spec 2026-06-17)
// ---------------------------------------------------------------------------

/** ≥3 buttons → icon-only; ≤2 → icon+label (spec 2026-06-17). */
const val FOOT_BAR_ICON_ONLY_THRESHOLD = 3

/**
 * One foot-bar action. `icon` is REQUIRED and must be ligature-backed (both render modes carry
 * a glyph; OutlinedControl throws on drawable icons). `label` must be non-blank (used as text in
 * ≤2 bars and as the icon-only a11y fallback). `modifier` is appended after weight(1f) so existing
 * per-button modifiers (alpha-dim, selected/disabled semantics) survive migration verbatim.
 */
data class FootAction(
    val label: String,
    val icon: DinghyIcon,
    val onClick: () -> Unit,
    val intent: Intent = Intent.Neutral,
    val contentDescription: String? = null,
    val onLongClick: (() -> Unit)? = null,
    val enabled: Boolean = true,
    val fill: Color? = null,
    val modifier: Modifier = Modifier,
) {
    init {
        require(label.isNotBlank()) { "FootAction.label must be non-blank" }
        require(icon.primary is IconRef.Ligature) {
            "FootAction.icon must be ligature-backed (use a Ligature IconRef, not Drawable): " +
            "alternate='${icon.alternate}', primary=${icon.primary}"
        }
    }
}

/**
 * Build a [FootAction] from a named [ControlSpec]. The spec may be icon-only (`labelRes = null`,
 * e.g. Spool's load/scan/unload) — in that case the `contentDescriptionRes` supplies the required
 * non-blank label (it renders only in ≤2 bars; in ≥3 bars it's the a11y fallback).
 */
@Composable
fun footAction(spec: ControlSpec, onClick: () -> Unit, enabled: Boolean = true): FootAction {
    val labelRes = spec.labelRes ?: spec.contentDescriptionRes
    requireNotNull(labelRes) { "spec ${spec.key} needs a label or contentDescription for a foot button" }
    return FootAction(
        label = stringResource(labelRes),
        icon = requireNotNull(spec.icon) { "spec ${spec.key} needs an icon for a foot button" },
        onClick = onClick,
        intent = spec.intent,
        contentDescription = spec.contentDescriptionRes?.let { stringResource(it) },
        enabled = enabled,
    )
}

/**
 * Count-driven [FootButtonBar] overload: ≤[FOOT_BAR_ICON_ONLY_THRESHOLD]-1 buttons → icon+label;
 * ≥[FOOT_BAR_ICON_ONLY_THRESHOLD] buttons → icon-only. The bar owns the count rule; call sites
 * pass a list and the mode is computed centrally.
 */
@Composable
fun FootButtonBar(
    uDp: Dp,
    actions: List<FootAction>,
    modifier: Modifier = Modifier,
) {
    val iconOnly = actions.size >= FOOT_BAR_ICON_ONLY_THRESHOLD
    CompositionLocalProvider(LocalUnitDp provides uDp) {
        Row(
            modifier = modifier.fillMaxWidth().controlHeight(uDp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEach { a ->
                OutlinedControl(
                    label = if (iconOnly) "" else a.label,
                    onClick = a.onClick,
                    modifier = Modifier.weight(1f).then(a.modifier),
                    intent = a.intent,
                    icon = a.icon,
                    onLongClick = a.onLongClick,
                    contentDescription = a.contentDescription ?: a.label,
                    enabled = a.enabled,
                    fill = a.fill,
                )
            }
        }
    }
}
