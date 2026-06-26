package works.mees.jiib.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.designsystem.icons.DinghyIcon
import works.mees.jiib.designsystem.icons.DinghyIconView
import works.mees.jiib.designsystem.icons.DinghyIcons
import works.mees.jiib.designsystem.layout.LocalUnitDp
import works.mees.jiib.designsystem.layout.controlHeight
import works.mees.jiib.designsystem.layout.gapS
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.fsSp

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
 * convention, matching [works.mees.jiib.designsystem.components.ListRow] and the IncrementPicker
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
    /** When false, the tile is dimmed + announced disabled and installs no click (R10). Default true. */
    val enabled: Boolean = true,
)

// ─────────────────────────────────────────────────────────────────────────────
// SelectorRow primitive
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The shared selector-row primitive (control baseline audit, Phase 3): a `Row` of option tiles built
 * on [OutlinedControl], with an optional leading recessed TYPE tile. [SortRow], [FilterRow], and
 * [IncrementPicker] are thin presets over this one base.
 *
 * ## Anatomy (COMPONENTS.md §3)
 *  - **Leading type tile** ([leadingTypeTile] non-null): an OPTIONAL general capability — a recessed,
 *    NON-interactive `t.bg2` tile that identifies the row's function. RETIRED for the Sort/Filter
 *    presets 2026-06-17 (they now pass it null and render text-label tiles, no leader). Null = no
 *    leader (IncrementPicker, Sort/Filter).
 *  - **Option tiles**: filled [OutlinedControl]s, weight 1f. Active = [Intent.Accent] + accentSoft
 *    fill; inactive = [Intent.Neutral] + default fill (R18).
 *  - **Direction overlay** ([SelectorOption.directionIcon] non-null): a small registered glyph at
 *    `Alignment.TopEnd`, non-displacing (does not replace the tile's primary label). Sort only.
 *
 * ## Sizing
 *  - Row height = exactly 1U via [controlHeight] — the single shared control-height rule, identical
 *    to [FootButtonBar] and [StepperRow] (owner UAT 2026-06-15).
 *  - [LocalUnitDp] is ALWAYS provided so each tile's glyph floors at 1U and hits the 0.6U icon tier,
 *    matching the foot bar (every preset — Sort/Filter/Increment/axis).
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
) {
    val t = LocalTokens.current
    // 1U tiles (owner All-1U ruling 2026-06-12), floored at 48dp touch target.
    val tileHeight = maxOf(uDp, 48.dp)

    // EXACT 1U height + LocalUnitDp for EVERY preset (owner UAT 2026-06-14 — uniform control height
    // and glyph tier matching the foot bar). Was gated by a `provideUnitDp` flag that left Sort/Filter
    // on `heightIn(min)` + the legacy glyph, so their tiles read a hair tall and the glyph differed.
    CompositionLocalProvider(LocalUnitDp provides uDp) {
        Row(
            modifier = modifier.fillMaxWidth().controlHeight(uDp),
            horizontalArrangement = Arrangement.spacedBy(gapS(uDp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // LEADING TYPE TILE — recessed, non-interactive (optional general capability; retired
            // from the Sort/Filter presets 2026-06-17 — they pass null).
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

            // OPTION TILES — structurally IDENTICAL to a FootButtonBar button (owner UAT 2026-06-14):
            // a plain OutlinedControl carrying its OWN 1U via heightIn(min=uDp) (LocalUnitDp provided
            // here), NOT a fillMaxHeight tile. The earlier `Box.fillMaxHeight { OutlinedControl
            // .fillMaxHeight }` form measured ~7px TALLER than the foot button at the same uDp — fill
            // semantics differed from the foot button's intrinsic-min sizing. The Box stays only to
            // host the non-displacing direction overlay; it wraps the control's natural 1U height.
            options.forEach { opt ->
                // A disabled tile (e.g. AxisSelectorRow's unhomed axis) dims + announces disabled
                // (the StepperRow/WR-07 convention); OutlinedControl(enabled=false) installs no click.
                val tileDisabledModifier =
                    if (!opt.enabled) Modifier.alpha(0.38f).semantics { disabled() } else Modifier
                Box(modifier = Modifier.weight(1f).then(tileDisabledModifier)) {
                    OutlinedControl(
                        label = opt.label,
                        onClick = { onSelect(opt.key) },
                        modifier = Modifier.fillMaxWidth(),
                        intent = selectorTileIntent(opt.isActive),
                        icon = opt.icon,
                        contentDescription = opt.contentDescription,
                        enabled = opt.enabled,
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
 * Tiles are exactly 1U (the shared [controlHeight] rule, [LocalUnitDp] provided by [SelectorRow]),
 * matching the Microstep jog/step rows. A disabled (unhomed) axis tile is greyed via [Modifier.alpha] + `disabled()`
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
                // Forward the homed gate so an unhomed axis renders dimmed + disabled (was dropped —
                // control-audit Codex review 2026-06-15; the KDoc promised this but the map omitted it).
                enabled = opt.enabled,
            )
        },
        onSelect = { axis ->
            // Gate the select to enabled (homed) axes — the disabled tile still renders (dimmed)
            // but its tap is a no-op, mirroring the old AxisSelectChip `clickable(enabled = homed)`.
            if (options.firstOrNull { it.axis == axis }?.enabled == true) onSelect(axis)
        },
        uDp = uDp,
        modifier = modifier,
    )
}
