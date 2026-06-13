package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

// ─────────────────────────────────────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single option in a [SortRow].
 *
 * @param key                  the sort key (opaque to [SortRow]; passed back via [SortRow.onSelect]).
 * @param icon                 the glyph rendered on the option tile — must be a registered
 *                             [DinghyIcons] token, never a raw ligature string (icon law).
 * @param contentDescriptionRes string resource ID for TalkBack — e.g. `R.string.cd_sort_by_name`.
 * @param directionUp          `true` = ascending arrow; `false` = descending arrow;
 *                             `null` = this option is not the active sort (no arrow shown).
 */
data class SortOption<K>(
    val key: K,
    val icon: DinghyIcon,
    val contentDescriptionRes: Int,
    val directionUp: Boolean? = null,
)

/**
 * A single option in a [FilterRow].
 *
 * @param key                  the filter key (opaque to [FilterRow]; passed back via [FilterRow.onSelect]).
 * @param icon                 the glyph rendered on the option tile — must be a registered
 *                             [DinghyIcons] token, never a raw ligature string (icon law).
 * @param contentDescriptionRes string resource ID for TalkBack.
 * @param isActive             whether this filter is currently active (drives [Intent.Accent] highlight).
 */
data class FilterOption<K>(
    val key: K,
    val icon: DinghyIcon,
    val contentDescriptionRes: Int,
    val isActive: Boolean,
)

// ─────────────────────────────────────────────────────────────────────────────
// SortRow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A compound sort-control row (docs/ui_design/COMPONENTS.md §"SortFilterControlRow anatomy").
 *
 * ## Compound anatomy — leading TYPE tile is MANDATORY
 * The row LEADS with a **recessed, non-interactive TYPE tile** ([DinghyIcons.Sort], `t.bg2`) that
 * identifies this row's function. A bare option-list Row without the type tile is NON-CONFORMANT.
 * The type tile has NO group-label `Text` words — context, fill, and accent carry the grouping.
 *
 * ## Fill convention
 *  - Type tile: `t.bg2` recessed background (non-interactive, sunken).
 *  - Option tiles: filled surface; [Intent.Accent] when active, [Intent.Neutral] when inactive.
 *
 * ## Direction indicator
 * The active sort tile overlays a small `arrow_upward`/`arrow_downward` glyph at `Alignment.TopEnd`
 * of its `Box`. This overlay is in addition to the option tile's primary icon — it does NOT
 * displace or replace the primary glyph.
 *
 * NOTE: `arrow_upward`/`arrow_downward` are rendered via [MaterialSymbol] (the raw ligature
 * primitive) because they are not yet registered in [DinghyIcons]. When these glyphs are
 * assigned in a future icon-registry phase, the direction indicator should switch to
 * [DinghyIconView] with the registered tokens.
 *
 * ## Icon registry
 * Option tile glyphs are passed via [SortOption.icon] (registered [DinghyIcon] tokens) and
 * rendered through the [DinghyIcon]-aware [OutlinedControl] overload — never raw ligature strings.
 *
 * @param options   the sort options; each supplies a registered [DinghyIcon] and a direction flag.
 * @param activeKey the currently-selected sort key (may be null for no selection).
 * @param onSelect  called when the user taps an option tile.
 * @param uDp       one unit U from [works.mees.dinghy.designsystem.layout.rememberUnitGrid];
 *                  tile height = `uDp` (full 1U — owner All-1U ruling, 2026-06-12).
 * @param modifier  caller-supplied modifier (e.g. `Modifier.padding(horizontal = 8.dp)`).
 */
@Composable
fun <K> SortRow(
    options: ImmutableList<SortOption<K>>,
    activeKey: K?,
    onSelect: (K) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    // Full 1U tiles (owner ruling 2026-06-12): toggle tiles are touch-target "buttons" and obey
    // the All-1U rule like every other control — the old U−12 tile broke 1U conformance and read
    // shorter than the foot bar. Floor 48dp keeps sub-floor hardware tappable.
    val tileHeight = maxOf(uDp, 48.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // LEADING TYPE TILE — mandatory, recessed, non-interactive (NON-CONFORMANT without it)
        Box(
            modifier = Modifier
                .heightIn(min = tileHeight)
                .heightIn(max = tileHeight)
                .clip(RoundedCornerShape(t.rCtrl))
                .background(t.bg2)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Decorative — no contentDescription (type tile is visual grouping, not interactive)
            DinghyIconView(
                icon = DinghyIcons.Sort,
                tint = t.text2,
                sizeDp = fsSp(20f, t.fs).dp,
                contentDescription = null,  // purely decorative grouping indicator
            )
        }

        // OPTION TILES — each uses the DinghyIcon-aware OutlinedControl overload (icon = option.icon)
        options.forEach { opt ->
            val active = opt.key == activeKey
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = tileHeight),
            ) {
                OutlinedControl(
                    label = "",
                    onClick = { onSelect(opt.key) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = tileHeight),
                    intent = if (active) Intent.Accent else Intent.Neutral,
                    icon = opt.icon,  // registered DinghyIcon — NEVER a raw ligature string
                )
                // Direction indicator — small arrow overlay at TopEnd, non-displacing
                // NOTE: arrow_upward/arrow_downward not yet in DinghyIcons; using MaterialSymbol
                // directly (same pattern as SpoolPicker analog). Register in future icon phase.
                val direction = opt.directionUp
                if (active && direction != null) {
                    MaterialSymbol(
                        name = if (direction) "arrow_upward" else "arrow_downward",
                        tint = t.accent2,
                        sizeSp = fsSp(14f, t.fs),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FilterRow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A compound filter-control row (docs/ui_design/COMPONENTS.md §"SortFilterControlRow anatomy").
 *
 * ## Compound anatomy — leading TYPE tile is MANDATORY
 * The row LEADS with a **recessed, non-interactive TYPE tile** ([DinghyIcons.FilterList], `t.bg2`).
 * A bare option-list Row without the type tile is NON-CONFORMANT.
 * No group-label `Text` words — the type tile + fill + accent carry the grouping.
 *
 * ## Fill convention
 *  - Type tile: `t.bg2` recessed background.
 *  - Option tiles: [Intent.Accent] when active, [Intent.Neutral] when inactive.
 *
 * ## Icon registry
 * Option tile glyphs are passed via [FilterOption.icon] (registered [DinghyIcon] tokens) and
 * rendered through the [DinghyIcon]-aware [OutlinedControl] overload — never raw ligature strings.
 *
 * @param options  the filter options; each has an [isActive][FilterOption.isActive] flag.
 * @param onSelect called when the user taps an option tile (callers toggle the active state).
 * @param uDp      one unit U from [works.mees.dinghy.designsystem.layout.rememberUnitGrid];
 *                 tile height = `uDp - 12.dp` (one unit minus the inter-row gap).
 * @param modifier caller-supplied modifier.
 */
@Composable
fun <K> FilterRow(
    options: ImmutableList<FilterOption<K>>,
    onSelect: (K) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    // Full 1U tiles (owner ruling 2026-06-12): toggle tiles are touch-target "buttons" and obey
    // the All-1U rule like every other control — the old U−12 tile broke 1U conformance and read
    // shorter than the foot bar. Floor 48dp keeps sub-floor hardware tappable.
    val tileHeight = maxOf(uDp, 48.dp)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // LEADING TYPE TILE — mandatory, recessed, non-interactive (NON-CONFORMANT without it)
        Box(
            modifier = Modifier
                .heightIn(min = tileHeight)
                .heightIn(max = tileHeight)
                .clip(RoundedCornerShape(t.rCtrl))
                .background(t.bg2)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Decorative — no contentDescription (type tile is visual grouping, not interactive)
            DinghyIconView(
                icon = DinghyIcons.FilterList,
                tint = t.text2,
                sizeDp = fsSp(20f, t.fs).dp,
                contentDescription = null,  // purely decorative grouping indicator
            )
        }

        // OPTION TILES — each uses the DinghyIcon-aware OutlinedControl overload (icon = option.icon)
        options.forEach { opt ->
            OutlinedControl(
                label = "",
                onClick = { onSelect(opt.key) },
                modifier = Modifier.weight(1f).heightIn(min = tileHeight),
                intent = if (opt.isActive) Intent.Accent else Intent.Neutral,
                icon = opt.icon,  // registered DinghyIcon — NEVER a raw ligature string
            )
        }
    }
}
