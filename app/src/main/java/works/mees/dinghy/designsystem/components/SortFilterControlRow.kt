package works.mees.dinghy.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import kotlinx.collections.immutable.ImmutableList
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons

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
 * The active sort tile overlays a small direction glyph at `Alignment.TopEnd` of its `Box`. This
 * overlay is in addition to the option tile's primary icon — it does NOT displace or replace the
 * primary glyph. As of the control baseline audit (Phase 3) the glyph is the registered
 * [DinghyIcons.SortAsc] (`arrow_drop_up`) / [DinghyIcons.SortDesc] (`arrow_drop_down`) token pair
 * (master-list §f#1), rendered via the shared [SelectorRow] primitive — no longer a raw ligature.
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
 * @param modifier  caller-supplied modifier (sizing only — the enclosing RegisteredRegion owns the
 *                  8dp frame; do NOT add frame padding here).
 */
@Composable
fun <K> SortRow(
    options: ImmutableList<SortOption<K>>,
    activeKey: K?,
    onSelect: (K) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    // Thin preset over SelectorRow: Sort type-tile leader; each option is an icon tile whose active
    // state derives from activeKey, with the registered direction-arrow overlay on the active tile.
    SelectorRow(
        options = options.map { opt ->
            val active = opt.key == activeKey
            SelectorOption(
                key = opt.key,
                icon = opt.icon,
                isActive = active,
                // Direction overlay only on the active sort tile (registered SortAsc/SortDesc).
                directionIcon = if (active) sortDirectionIcon(opt.directionUp) else null,
            )
        },
        onSelect = onSelect,
        uDp = uDp,
        modifier = modifier,
        leadingTypeTile = DinghyIcons.Sort,
    )
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
 *                 tile height = `uDp` (full 1U — owner All-1U ruling, 2026-06-12).
 * @param modifier caller-supplied modifier.
 */
@Composable
fun <K> FilterRow(
    options: ImmutableList<FilterOption<K>>,
    onSelect: (K) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    // Thin preset over SelectorRow: FilterList type-tile leader; each option is an icon tile whose
    // active state is its own isActive flag (no direction overlay).
    SelectorRow(
        options = options.map { opt ->
            SelectorOption(
                key = opt.key,
                icon = opt.icon,
                isActive = opt.isActive,
            )
        },
        onSelect = onSelect,
        uDp = uDp,
        modifier = modifier,
        leadingTypeTile = DinghyIcons.FilterList,
    )
}
