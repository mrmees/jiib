package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

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

// ─────────────────────────────────────────────────────────────────────────────
// Composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The single-row scrollable-list primitive for the jiib redesign grammar.
 *
 * ## Fill convention (docs/ui_design/COMPONENTS.md §"Content vs controls — fill convention")
 *  - **Unselected:** transparent background + 1.5dp [ThemeTokens.outline][works.mees.dinghy.theme.ThemeTokens.outline] border.
 *    List rows are content surfaces; transparency is the correct fill for content.
 *  - **Selected:** [ThemeTokens.accentSoft][works.mees.dinghy.theme.ThemeTokens.accentSoft] background + 2dp
 *    [ThemeTokens.accentLine][works.mees.dinghy.theme.ThemeTokens.accentLine] border.
 *    The accent fill signals active selection without obscuring the content.
 *
 * All colors come from [LocalTokens.current] — **never a raw `Color(0x…)`**.
 * Text sizes inside the row content must use `fsSp(baseSp, t.fs).sp` — never a bare `.sp`.
 *
 * ## Touch floor
 * [uDp] is passed explicitly from the unit grid ([works.mees.dinghy.designsystem.layout.rememberUnitGrid])
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
 * @param uDp            one unit U from [works.mees.dinghy.designsystem.layout.rememberUnitGrid];
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
    val bgColor = if (listRowUsesAccentFill(selected)) t.accentSoft else Color.Transparent
    val borderColor = if (selected) t.accentLine else t.outline
    val borderWidth = listRowBorderWidthFor(selected)
    // R10 (26.5-03 + codex review): plain clickable RESTORED — the explicit
    // interactionSource/LocalIndication draft didn't deliver Part 5 cause #4 immediacy (the
    // scrollable press-delay lives in clickable's pointer logic, not indication laziness) and
    // eagerly allocated per row. Cause #4 deferred pending morning instrumentation; a real fix
    // is custom press detection, not parameter plumbing.

    Row(
        modifier
            .fillMaxWidth()
            // U touch floor — 1U height minimum on ALL surfaces (owner UAT ruling, Phase 28, 2026-06-12).
            // C6 densification = tighter section grouping + inline keyboard fields, NEVER sub-1U rows.
            .heightIn(min = uDp)
            .clip(shape)
            .background(bgColor)
            .border(BorderStroke(borderWidth, borderColor), shape)
            .clickable(onClick = onClick)
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
        style = DinghyType.listLabel.toTextStyle(t),
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
 * @param icon the registered [DinghyIcon] (icon law: registry-only).
 * @param uDp  one unit U from the screen's unit grid (same value passed to [ListRow]).
 * @param tint icon tint (token-routed at the call site).
 * @param contentDescription a11y label, or null when the row label already speaks.
 */
@Composable
fun ListRowIcon(
    icon: DinghyIcon,
    uDp: Dp,
    tint: androidx.compose.ui.graphics.Color,
    contentDescription: String? = null,
) {
    DinghyIconView(
        icon = icon,
        tint = tint,
        sizeDp = uDp * 0.6f,
        contentDescription = contentDescription,
    )
}
