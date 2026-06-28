package works.mees.jiib.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding  // used for horizontal content padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.jiib.designsystem.control.neutralSoftFill
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIconView
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

// ─────────────────────────────────────────────────────────────────────────────
// Pure host-testable helpers — ListRowTest asserts these directly
//
// The fill convention (23-PATTERNS.md §ListRow / sketch-findings fill convention):
//   unselected → Color.Transparent background, 1.5dp outline border
//   selected   → t.accentSoft background, 2dp accentLine border
//
// These helpers are pure boolean/Dp — no ThemeTokens dependency — so they can
// be asserted in a plain JUnit4 host test without a Compose runtime.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Returns `true` when [selected] is `true` (the row should use [ThemeTokens.accentSoft] fill).
 * Returns `false` when unselected (the row should use [Color.Transparent]).
 *
 * This is the pure state-selection gate — extracted from the composable body so
 * [ListRowTest] can assert the `selected → accentSoft` contract without Compose.
 */
internal fun listRowUsesAccentFill(selected: Boolean): Boolean = selected

/**
 * Returns the border stroke width for a [ListRow] given its [selected] state.
 *
 *   selected   → 2.dp (emphasised selection ring)
 *   unselected → 1.5.dp (standard outline weight)
 *
 * Pure Dp arithmetic — host-testable without a Compose runtime.
 */
internal fun listRowBorderWidthFor(selected: Boolean): Dp =
    if (selected) 2.dp else 1.5.dp

/**
 * Returns `true` when the row should render the NEUTRAL press highlight — i.e. it is currently
 * [pressed] AND not [selected]. Selection wins: a selected row keeps its accent fill on press
 * (owner decision 2026-06-28) rather than flashing gray. Pure boolean — host-testable without
 * a Compose runtime, mirroring [listRowUsesAccentFill].
 */
internal fun listRowUsesPressedFill(selected: Boolean, pressed: Boolean): Boolean =
    pressed && !selected

// ─────────────────────────────────────────────────────────────────────────────
// Composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The single-row scrollable-list primitive for the jiib redesign grammar.
 *
 * ## Fill convention (docs/ui_design/COMPONENTS.md §"Content vs controls — fill convention")
 *  - **Unselected:** transparent background + 1.5dp [ThemeTokens.outline][works.mees.jiib.theme.ThemeTokens.outline] border.
 *    List rows are content surfaces; transparency is the correct fill for content.
 *  - **Selected:** [ThemeTokens.accentSoft][works.mees.jiib.theme.ThemeTokens.accentSoft] background + 2dp
 *    [ThemeTokens.accentLine][works.mees.jiib.theme.ThemeTokens.accentLine] border.
 *    The accent fill signals active selection without obscuring the content.
 *
 * All colors come from [LocalTokens.current] — **never a raw `Color(0x…)`**.
 * Text sizes inside the row content must use `fsSp(baseSp, t.fs).sp` — never a bare `.sp`.
 *
 * ## Touch floor
 * [uDp] is passed explicitly from the unit grid ([works.mees.jiib.designsystem.layout.rememberUnitGrid])
 * so the touch target is always exactly one unit U (≥ 64dp). Do not hardcode a Dp constant here.
 *
 * ## Usage
 * ```kotlin
 * ListBlock { items(spools, key = { it.id }) { spool ->
 *     ListRow(selected = spool.id == state.selected?.id, onClick = { onRowClick(spool) },
 *             uDp = grid.uDp) { SpoolRowContent(spool, t) }
 * }}
 * ```
 *
 * @param selected       whether this row is the active selection.
 * @param onClick        invoked on tap.
 * @param uDp            one unit U from [works.mees.jiib.designsystem.layout.rememberUnitGrid];
 *                       used as the [heightIn] minimum so the touch target equals the unit grid.
 * @param modifier       caller-supplied modifier chain.
 * @param leadingContent optional leading slot (e.g. color swatch, icon, avatar).
 * @param trailingContent optional trailing slot (e.g. weight value, arrow).
 * @param content        primary row body (rendered between leading and trailing, center-aligned).
 */
@Composable
fun ListRow(
    selected: Boolean,
    onClick: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    // Token-routed colors — THEME-01 compliance, no raw Color(0x…) for chrome.
    // PRESSED STATE (owner 2026-06-28): supersedes the R10 plain-clickable revert. That revert was
    // about scroll press-*timing* ("cause #4"), which lives in clickable's pointer logic and is NOT
    // touched here — we change only the press *visual*. On touch-down an UNSELECTED row fills with
    // the canonical neutral soft tint (the same value the Neutral-intent button press uses), instead
    // of the default Material ripple that merely dimmed toward the background ("nothing happened").
    // A SELECTED row keeps its accent fill on press (selection wins — listRowUsesPressedFill). Border
    // width/color stay constant on press so there is no layout reflow. LazyColumn composes only the
    // visible rows, so the per-row interactionSource allocation is bounded (~a dozen), exactly like
    // every OutlinedControl button already does.
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val bgColor = when {
        listRowUsesAccentFill(selected) -> t.accentSoft
        listRowUsesPressedFill(selected, pressed) -> neutralSoftFill(t)
        else -> Color.Transparent
    }
    val borderColor = if (selected) t.accentLine else t.outline
    val borderWidth = listRowBorderWidthFor(selected)

    Row(
        modifier
            .fillMaxWidth()
            // U touch floor — 1U height minimum on ALL surfaces (owner UAT ruling, Phase 28, 2026-06-12).
            // C6 densification = tighter section grouping + inline keyboard fields, NEVER sub-1U rows.
            .heightIn(min = uDp)
            .clip(shape)
            .background(bgColor)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Row anatomy is OWNED HERE (UAT-2 / pilot finding 2026-06-12): a standard gapM (12dp)
        // after the leading slot, and the content slot absorbs all slack via weight(1f) so the
        // trailing slot is ALWAYS end-aligned — call sites cannot drift the anatomy.
        leadingContent?.let {
            it()
            Spacer(Modifier.width(12.dp))
        }
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
        trailingContent?.invoke()
    }
}

/**
 * The canonical [ListRow] primary-label text — ONE place owns the list-label look so screens
 * cannot drift (pilot finding, 2026-06-12: FineTune/BedMesh labels diverged from the home list
 * in family/weight). Geist SemiBold at the R11 20sp list/button default, `t.text`.
 *
 * Use this for the row's NAME/label. Trailing VALUE readouts stay Geist Mono at the call site
 * (mono = live/tabular data, THEMING §type) — they are values, not labels.
 */
@Composable
fun ListRowLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color? = null,
) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = color ?: t.text,
        style = JiibType.listLabel.toTextStyle(t),
        // Rows are FIXED at 1U (R23 precondition: multiline must not grow them) — one line,
        // ellipsized. A genuinely-overflowing line may marquee per the panel-text law instead.
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/**
 * The canonical [ListRow] leading icon — **0.6U, U-relative, does NOT grow with the S/M/L text
 * setting** (owner ruling R23, 2026-06-12; revises R16's text-tracked fsSp(22)). Rows are fixed
 * 1U, so a U-fraction is visually stable on every device; vertical centering comes from
 * [ListRow]'s row alignment.
 *
 * @param icon the registered [JiibIcon] (icon law: registry-only).
 * @param uDp  one unit U from the screen's unit grid (same value passed to [ListRow]).
 * @param tint icon tint (token-routed at the call site).
 * @param contentDescription a11y label, or null when the row label already speaks.
 */
@Composable
fun ListRowIcon(
    icon: JiibIcon,
    uDp: Dp,
    tint: androidx.compose.ui.graphics.Color,
    contentDescription: String? = null,
) {
    JiibIconView(
        icon = icon,
        tint = tint,
        sizeDp = uDp * 0.6f,
        contentDescription = contentDescription,
    )
}
