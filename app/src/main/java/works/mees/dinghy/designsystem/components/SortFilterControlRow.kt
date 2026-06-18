package works.mees.dinghy.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import kotlinx.collections.immutable.ImmutableList

// ─────────────────────────────────────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single option in a [SortRow].
 *
 * The leading TYPE tile is RETIRED (owner 2026-06-17) — Sort rows render as text-label tiles
 * with no leading glyph. Each option tile shows its [label] text.
 *
 * @param key                  the sort key (opaque to [SortRow]; passed back via [SortRow.onSelect]).
 * @param label                the text rendered on the option tile.
 * @param contentDescriptionRes string resource ID for TalkBack — e.g. `R.string.cd_sort_by_name`.
 * @param directionUp          `true` = ascending arrow; `false` = descending arrow;
 *                             `null` = this option is not the active sort (no arrow shown).
 */
data class SortOption<K>(
    val key: K,
    val label: String,
    val contentDescriptionRes: Int,
    val directionUp: Boolean? = null,
)

/**
 * A single option in a [FilterRow].
 *
 * The leading TYPE tile is RETIRED (owner 2026-06-17) — Filter rows render as text-label tiles
 * with no leading glyph. Each option tile shows its [label] text.
 *
 * @param key                  the filter key (opaque to [FilterRow]; passed back via [FilterRow.onSelect]).
 * @param label                the text rendered on the option tile.
 * @param contentDescriptionRes string resource ID for TalkBack.
 * @param isActive             whether this filter is currently active (drives [Intent.Accent] highlight).
 */
data class FilterOption<K>(
    val key: K,
    val label: String,
    val contentDescriptionRes: Int,
    val isActive: Boolean,
)

// ─────────────────────────────────────────────────────────────────────────────
// SortRow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A compound sort-control row (docs/ui_design/COMPONENTS.md §"SortFilterControlRow anatomy").
 *
 * ## Anatomy — text-label tiles (leading TYPE tile RETIRED 2026-06-17)
 * The leading recessed TYPE tile is RETIRED (owner 2026-06-17). The row is now a bare list of
 * **text-label option tiles** with NO leading glyph. Each tile shows its [SortOption.label] text;
 * fill and accent carry the active-state grouping.
 *
 * ## Fill convention
 *  - Option tiles: filled surface; [Intent.Accent] when active, [Intent.Neutral] when inactive.
 *
 * ## Direction indicator
 * The active sort tile overlays a small direction glyph at `Alignment.TopEnd` of its `Box`. This
 * overlay is in addition to the option tile's primary label — it does NOT displace or replace the
 * label. As of the control baseline audit (Phase 3) the glyph is the registered
 * `DinghyIcons.SortAsc` (`arrow_drop_up`) / `DinghyIcons.SortDesc` (`arrow_drop_down`) token pair
 * (master-list §f#1), rendered via the shared [SelectorRow] primitive — no longer a raw ligature.
 *
 * @param options   the sort options; each supplies a text label and a direction flag.
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
    // Thin preset over SelectorRow: each option is a text-label tile whose active state derives
    // from activeKey, with the registered direction-arrow overlay on the active tile.
    SelectorRow(
        options = options.map { opt ->
            val active = opt.key == activeKey
            SelectorOption(
                key = opt.key,
                label = opt.label,
                isActive = active,
                directionIcon = if (active) sortDirectionIcon(opt.directionUp) else null,
                contentDescription = stringResource(opt.contentDescriptionRes),
            )
        },
        onSelect = onSelect,
        uDp = uDp,
        modifier = modifier,
        // Type-tile retired (owner 2026-06-17) — text-label tiles, no leading Sort glyph.
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// FilterRow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A compound filter-control row (docs/ui_design/COMPONENTS.md §"SortFilterControlRow anatomy").
 *
 * ## Anatomy — text-label tiles (leading TYPE tile RETIRED 2026-06-17)
 * The leading recessed TYPE tile is RETIRED (owner 2026-06-17). The row is now a bare list of
 * **text-label option tiles** with NO leading glyph. Each tile shows its [FilterOption.label] text;
 * fill and accent carry the active-state grouping.
 *
 * ## Fill convention
 *  - Option tiles: [Intent.Accent] when active, [Intent.Neutral] when inactive.
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
    // Thin preset over SelectorRow: each option is a text-label tile whose active state is its own
    // isActive flag (no direction overlay).
    SelectorRow(
        options = options.map { opt ->
            SelectorOption(
                key = opt.key,
                label = opt.label,
                isActive = opt.isActive,
                contentDescription = stringResource(opt.contentDescriptionRes),
            )
        },
        onSelect = onSelect,
        uDp = uDp,
        modifier = modifier,
        // Type-tile retired (owner 2026-06-17) — text-label tiles, no leading Filter glyph.
    )
}
