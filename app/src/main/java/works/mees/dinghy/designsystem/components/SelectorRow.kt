package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.LocalUnitDp
import works.mees.dinghy.designsystem.layout.gapS
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

// ─────────────────────────────────────────────────────────────────────────────
// Pure helpers (host-testable — no Compose runtime)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The selector-tile intent (R18): an active/selected tile is [Intent.Accent]; an inactive tile is
 * [Intent.Neutral]. Valid for SELECTOR tiles (Sort/Filter/Increment/mode-select) where neutral is a
 * legitimate "this one isn't the chosen one" state — distinct from functional-command intent law.
 */
internal fun selectorTileIntent(isActive: Boolean): Intent =
    if (isActive) Intent.Accent else Intent.Neutral

/**
 * Whether an active selector tile requests the `accentSoft` FILL (the app-wide selected-state
 * convention, matching [works.mees.dinghy.designsystem.components.ListRow] and the IncrementPicker
 * active step). Inactive tiles keep the default surface fill (the [OutlinedControl] `fill = null`
 * path). The concrete `Color` is pulled from [LocalTokens] at render time — this helper only models
 * the boolean decision so it stays host-testable.
 */
internal fun selectorWantsAccentFill(isActive: Boolean): Boolean = isActive

/**
 * The 1U tile-height math (UAT-5 + owner All-1U ruling 2026-06-12): a selector tile is the full unit
 * `uDp`, floored at the 48dp touch target so sub-floor hardware stays tappable. Pure (operates on the
 * raw dp value) so the floor math is testable without a [Dp] instance.
 */
internal fun selectorTileHeightDp(uDp: Float): Float = maxOf(uDp, 48f)

/**
 * The registered direction-overlay token for a sort tile: ascending → [DinghyIcons.SortAsc]
 * (`arrow_drop_up`), descending → [DinghyIcons.SortDesc] (`arrow_drop_down`), none (not the active
 * sort) → null. Replaces the legacy raw `arrow_upward`/`arrow_downward` [MaterialSymbol] block —
 * the master-list §f#1 owner ruling registered this token pair for exactly this indicator.
 */
internal fun sortDirectionIcon(directionUp: Boolean?): DinghyIcon? = when (directionUp) {
    true -> DinghyIcons.SortAsc
    false -> DinghyIcons.SortDesc
    null -> null
}

// ─────────────────────────────────────────────────────────────────────────────
// Primitive option model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One option tile in a [SelectorRow]. Presets translate their own option types
 * ([SortOption]/[FilterOption]/increment step) into this shared shape.
 *
 * @param key          opaque selection value handed back via [SelectorRow]'s `onSelect`.
 * @param icon         registered glyph for an icon tile (Sort/Filter); null for a label tile.
 * @param label        tile text for a label tile (IncrementPicker `±N`); blank for an icon tile.
 * @param isActive     drives the [Intent.Accent] outline + accentSoft fill.
 * @param directionIcon optional non-displacing TopEnd overlay token (Sort direction arrow); null = none.
 * @param contentDescription optional TalkBack label for the tile glyph.
 */
internal data class SelectorOption<K>(
    val key: K,
    val icon: DinghyIcon? = null,
    val label: String = "",
    val isActive: Boolean = false,
    val directionIcon: DinghyIcon? = null,
    val contentDescription: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// SelectorRow primitive
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The shared selector-row primitive (control baseline audit, Phase 3): a `Row` of option tiles built
 * on [OutlinedControl], with an optional leading recessed TYPE tile. [SortRow], [FilterRow], and
 * [IncrementPicker] are thin presets over this one base.
 *
 * ## Anatomy (LOCKED for Sort/Filter — COMPONENTS.md §3)
 *  - **Leading type tile** ([leadingTypeTile] non-null): a recessed, NON-interactive `t.bg2` tile
 *    that identifies the row's function (Sort/Filter). No group-label text. Null = no leader
 *    (IncrementPicker).
 *  - **Option tiles**: filled [OutlinedControl]s, weight 1f. Active = [Intent.Accent] + accentSoft
 *    fill; inactive = [Intent.Neutral] + default fill (R18).
 *  - **Direction overlay** ([SelectorOption.directionIcon] non-null): a small registered glyph at
 *    `Alignment.TopEnd`, non-displacing (does not replace the tile's primary icon). Sort only.
 *
 * ## Sizing
 *  - Tile height = 1U (`maxOf(uDp, 48.dp)` — [selectorTileHeightDp]).
 *  - When [provideUnitDp] is true (IncrementPicker), the row is exactly `height(uDp)` and provides
 *    [LocalUnitDp] so each tile's glyph floors at 1U and hits the 0.6U icon tier. Sort/Filter use the
 *    `heightIn(min = tileHeight)` form and do NOT provide [LocalUnitDp] (preserving their exact look).
 *  - Inter-tile spacing = [gapS] (`uDp × 0.125`, = 8dp at U=64 — Phase-1 token).
 *
 * Stateless, tokens-only. Internal — presets are the public surface.
 */
@Composable
internal fun <K> SelectorRow(
    options: List<SelectorOption<K>>,
    onSelect: (K) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
    leadingTypeTile: DinghyIcon? = null,
    provideUnitDp: Boolean = false,
) {
    val t = LocalTokens.current
    // 1U tiles (owner All-1U ruling 2026-06-12), floored at 48dp touch target.
    val tileHeight = maxOf(uDp, 48.dp)

    val content: @Composable () -> Unit = {
        Row(
            modifier = if (provideUnitDp) {
                modifier.fillMaxWidth().height(uDp)
            } else {
                modifier.fillMaxWidth()
            },
            horizontalArrangement = Arrangement.spacedBy(gapS(uDp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // LEADING TYPE TILE — recessed, non-interactive (mandatory for Sort/Filter anatomy).
            if (leadingTypeTile != null) {
                Box(
                    modifier = Modifier
                        .heightIn(min = tileHeight)
                        .heightIn(max = tileHeight)
                        .clip(RoundedCornerShape(t.rCtrl))
                        .background(t.bg2)
                        .padding(horizontal = gapS(uDp)),
                    contentAlignment = Alignment.Center,
                ) {
                    DinghyIconView(
                        icon = leadingTypeTile,
                        tint = t.text2,
                        sizeDp = fsSp(20f, t.fs).dp,
                        contentDescription = null, // purely decorative grouping indicator
                    )
                }
            }

            // OPTION TILES
            options.forEach { opt ->
                Box(modifier = Modifier.weight(1f).heightIn(min = tileHeight)) {
                    OutlinedControl(
                        label = opt.label,
                        onClick = { onSelect(opt.key) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = tileHeight),
                        intent = selectorTileIntent(opt.isActive),
                        icon = opt.icon,
                        contentDescription = opt.contentDescription,
                        // Selected tile = accentSoft fill (ListRow selected convention).
                        fill = if (selectorWantsAccentFill(opt.isActive)) t.accentSoft else null,
                    )
                    // Direction overlay — registered glyph at TopEnd, non-displacing.
                    if (opt.directionIcon != null) {
                        DinghyIconView(
                            icon = opt.directionIcon,
                            tint = t.accent2,
                            // U-relative (~0.5U) so the arrow_drop caret reads as a clear direction
                            // indicator, not a dot (owner UAT 2026-06-14 — fsSp(14) was far too small).
                            sizeDp = uDp * 0.5f,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                        )
                    }
                }
            }
        }
    }

    if (provideUnitDp) {
        CompositionLocalProvider(LocalUnitDp provides uDp, content = content)
    } else {
        content()
    }
}

/**
 * One axis option for [AxisSelectorRow]. [enabled] gates the tap (unhomed axis is not selectable);
 * a disabled tile is dimmed via the standard true-disablement treatment.
 */
data class AxisOption(val axis: String, val isSelected: Boolean, val enabled: Boolean = true)

/**
 * The Move Microstep axis selector (control baseline audit, Phase 4) — a [SelectorRow] preset that
 * picks which axis the ± jog pair drives. Text-label tiles ("X"/"Y"/"Z" — icon-never-twice rule:
 * axis identity is a letter, not a glyph). Selected = [Intent.Accent] + accentSoft fill; inactive =
 * [Intent.Neutral] (R18). Replaces the inline `AxisSelectChip` Box+border+clickable rogue.
 *
 * `provideUnitDp = true` so each tile floors at 1U and fills the row (R26), matching the Microstep
 * jog/step rows. A disabled (unhomed) axis tile is greyed via [Modifier.alpha] + `disabled()`
 * semantics and installs no click (the tap is gated by routing the select only for enabled axes).
 *
 * @param options the X/Y/Z options in display order.
 * @param onSelect called with the tapped axis string (only for enabled tiles).
 * @param uDp      one unit U — caps the row at 1U and drives tile fill.
 */
@Composable
fun AxisSelectorRow(
    options: List<AxisOption>,
    onSelect: (String) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    SelectorRow(
        options = options.map { opt ->
            SelectorOption(
                key = opt.axis,
                label = opt.axis,
                isActive = opt.isSelected,
                contentDescription = opt.axis,
            )
        },
        onSelect = { axis ->
            // Gate the select to enabled (homed) axes — the disabled tile still renders (dimmed)
            // but its tap is a no-op, mirroring the old AxisSelectChip `clickable(enabled = homed)`.
            if (options.firstOrNull { it.axis == axis }?.enabled == true) onSelect(axis)
        },
        uDp = uDp,
        modifier = modifier,
        provideUnitDp = true,
    )
}
