package works.mees.jiib.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import kotlinx.collections.immutable.ImmutableList
import works.mees.jiib.designsystem.icons.JiibIcon

// ─────────────────────────────────────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A single option in a [SortRow].
 *
 * The leading TYPE tile is RETIRED (owner 2026-06-17) — Sort rows have no leading glyph. Each
 * option tile keeps its [icon]; its [label] text shows beside the icon only when the row has ≤2
 * options (the shared FootButtonBar count rule), else the tile is icon-only and [label] is a11y-only.
 *
 * @param key                  the sort key (opaque to [SortRow]; passed back via [SortRow.onSelect]).
 * @param icon                 the registered [JiibIcon] glyph rendered on the option tile.
 * @param label                short tile text; shown beside the icon when the row has ≤2 options.
 * @param contentDescriptionRes string resource ID for TalkBack — e.g. `R.string.cd_sort_by_name`.
 * @param directionUp          `true` = ascending arrow; `false` = descending arrow;
 *                             `null` = this option is not the active sort (no arrow shown).
 */
data class SortOption<K>(
    val key: K,
    val icon: JiibIcon,
    val label: String,
    val contentDescriptionRes: Int,
    val directionUp: Boolean? = null,
)

/**
 * A single option in a [FilterRow].
 *
 * The leading TYPE tile is RETIRED (owner 2026-06-17) — Filter rows have no leading glyph. Each
 * option tile keeps its [icon]; its [label] text shows beside the icon only when the row has ≤2
 * options (the shared FootButtonBar count rule), else the tile is icon-only and [label] is a11y-only.
 *
 * @param key                  the filter key (opaque to [FilterRow]; passed back via [FilterRow.onSelect]).
 * @param icon                 the registered [JiibIcon] glyph rendered on the option tile.
 * @param label                short tile text; shown beside the icon when the row has ≤2 options.
 * @param contentDescriptionRes string resource ID for TalkBack.
 * @param isActive             whether this filter is currently active (drives [Intent.Accent] highlight).
 */
data class FilterOption<K>(
    val key: K,
    val icon: JiibIcon,
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
 * ## Anatomy — icon tiles, count-driven labels (leading TYPE tile RETIRED 2026-06-17)
 * The leading recessed TYPE tile is RETIRED (owner 2026-06-17). The row is a bare list of
 * **icon option tiles** with NO leading glyph. Each tile keeps its [SortOption.icon]; its
 * [SortOption.label] text shows beside the icon only when the row has ≤2 options (the shared
 * [FOOT_BAR_ICON_ONLY_THRESHOLD] FootButtonBar count rule), else the tile is icon-only.
 *
 * ## Fill convention
 *  - Option tiles: filled surface; [Intent.Accent] when active, [Intent.Neutral] when inactive.
 *
 * ## Direction indicator
 * The active sort tile overlays a small direction glyph at `Alignment.TopEnd` of its `Box`. This
 * overlay is in addition to the option tile's primary label — it does NOT displace or replace the
 * label. As of the control baseline audit (Phase 3) the glyph is the registered
 * `JiibIcons.SortAsc` (`arrow_drop_up`) / `JiibIcons.SortDesc` (`arrow_drop_down`) token pair
 * (master-list §f#1), rendered via the shared [SelectorRow] primitive — no longer a raw ligature.
 *
 * @param options   the sort options; each supplies an icon, a label, and a direction flag.
 * @param activeKey the currently-selected sort key (may be null for no selection).
 * @param onSelect  called when the user taps an option tile.
 * @param uDp       one unit U from [works.mees.jiib.designsystem.layout.rememberUnitGrid];
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
    // Thin preset over SelectorRow: each option is an icon tile whose active state derives from
    // activeKey, with the registered direction-arrow overlay on the active tile. Label TEXT shows
    // beside the icon only when the row has ≤2 options (the shared FootButtonBar count rule); ≥3 →
    // icon-only. Every option still carries a label (shown when room, else a11y-only).
    val iconOnly = options.size >= FOOT_BAR_ICON_ONLY_THRESHOLD
    SelectorRow(
        options = options.map { opt ->
            val active = opt.key == activeKey
            SelectorOption(
                key = opt.key,
                icon = opt.icon,
                label = if (iconOnly) "" else opt.label,
                isActive = active,
                directionIcon = if (active) sortDirectionIcon(opt.directionUp) else null,
                contentDescription = stringResource(opt.contentDescriptionRes),
            )
        },
        onSelect = onSelect,
        uDp = uDp,
        modifier = modifier,
        // Type-tile retired (owner 2026-06-17) — no leading Sort glyph; option tiles keep their icons.
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// FilterRow
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A compound filter-control row (docs/ui_design/COMPONENTS.md §"SortFilterControlRow anatomy").
 *
 * ## Anatomy — icon tiles, count-driven labels (leading TYPE tile RETIRED 2026-06-17)
 * The leading recessed TYPE tile is RETIRED (owner 2026-06-17). The row is a bare list of
 * **icon option tiles** with NO leading glyph. Each tile keeps its [FilterOption.icon]; its
 * [FilterOption.label] text shows beside the icon only when the row has ≤2 options (the shared
 * [FOOT_BAR_ICON_ONLY_THRESHOLD] FootButtonBar count rule), else the tile is icon-only.
 *
 * ## Fill convention
 *  - Option tiles: [Intent.Accent] when active, [Intent.Neutral] when inactive.
 *
 * @param options  the filter options; each has an [isActive][FilterOption.isActive] flag.
 * @param onSelect called when the user taps an option tile (callers toggle the active state).
 * @param uDp      one unit U from [works.mees.jiib.designsystem.layout.rememberUnitGrid];
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
    // Thin preset over SelectorRow: each option is an icon tile whose active state is its own
    // isActive flag (no direction overlay). Label TEXT shows beside the icon only when the row has
    // ≤2 options (the shared FootButtonBar count rule); ≥3 → icon-only.
    val iconOnly = options.size >= FOOT_BAR_ICON_ONLY_THRESHOLD
    SelectorRow(
        options = options.map { opt ->
            SelectorOption(
                key = opt.key,
                icon = opt.icon,
                label = if (iconOnly) "" else opt.label,
                isActive = opt.isActive,
                contentDescription = stringResource(opt.contentDescriptionRes),
            )
        },
        onSelect = onSelect,
        uDp = uDp,
        modifier = modifier,
        // Type-tile retired (owner 2026-06-17) — no leading Filter glyph; option tiles keep their icons.
    )
}
