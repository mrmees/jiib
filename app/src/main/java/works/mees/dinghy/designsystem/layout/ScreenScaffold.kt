package works.mees.dinghy.designsystem.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The Focus / Field / Gutter responsive layout primitive (UI-01) — the reusable skeleton EVERY
 * Dinghy Display screen is built from (docs/ui_design/LAYOUT.md). One grammar, three slots, ONE
 * shared grid. This is settled here once, not redesigned per panel.
 *
 * Slots:
 *  - [focus]  — one primary item; its SQUARE visual content is wrapped by the CALLER in
 *               `Modifier.aspectRatio(1f)` INSIDE the region and centered. The region itself is
 *               NEVER made square (NON-NEGOTIABLE 2 — hifi.css `.ringwrap`, not `.focus`).
 *  - [field]  — a divisible info/control surface (an arbitrary grid the caller fills).
 *  - [gutter] — primary actions; big touch targets on the SAME column grid as the stage.
 *
 * Either [focus] or [field] may be null; the other takes the freed space (LAYOUT.md). [gutter] may
 * be null for screens whose Field already holds all navigation (tool pages, confirm guard, drawer).
 *
 * ## Orientation (BoxWithConstraints, no Configuration read)
 *  - LANDSCAPE (`maxWidth > maxHeight`): a Row "stage" of two columns weighted [focusGrow] /
 *    [fieldGrow] (50/50 by default) above a FULL-WIDTH gutter Box. Because the gutter spans the same
 *    content width as the stage, a 3-equal-button gutter lands its middle-button center on the
 *    Focus/Field divide and its outer edges on the stage's outer edges (LAYOUT.md §1, one shared grid).
 *  - PORTRAIT (`maxWidth <= maxHeight`): a Column stacking focus / field / full-width gutter with a
 *    tunable ~40/40/20 rhythm via [focusGrow] / [fieldGrow] (LAYOUT.md §1 "Portrait").
 *
 * ## Sizing discipline (NON-NEGOTIABLE 3 — ratios only)
 * Regions are split with `weight` / `fillMax*` ONLY — NO hardcoded px for regions, cells, or
 * structural gaps. The single permitted fixed values in the design system live in the leaf controls
 * (the ≥64dp touch floor and the `--fs` text step), not here.
 *
 * ## Weighted-gutter alignment caveat (RESEARCH A4 / Open Question 3)
 * Fractional `weight` on the gutter row should keep button edges/centers on the stage grid lines.
 * If in-gallery validation on flox shows drift off the divide, escalate to a custom `Layout` for the
 * GUTTER ROW ONLY — the stage stays slot-based. (Deferred to the on-device gallery, 03-VALIDATION.md.)
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    focus: (@Composable ColumnScope.() -> Unit)? = null,
    field: (@Composable ColumnScope.() -> Unit)? = null,
    gutter: (@Composable () -> Unit)? = null,
    focusGrow: Float = 1f,
    fieldGrow: Float = 1f,
    // PORTRAIT aspect-lock (NON-NEGOTIABLE 2 helper): when set, the stacked PORTRAIT focus region is
    // sized to `fillMaxWidth().aspectRatio(portraitFocusAspect)` and the FIELD flexes to absorb the
    // remaining height. This lets a screen whose focus is a sacred square (Move's jog pad) fill the
    // full width — the square's own ratio drives the focus/field split, instead of a fixed 40/40 that
    // would shrink the square to half-height with fat side margins. Null = the normal weighted split.
    // (Landscape is unaffected — there focus/field split the WIDTH as 50/50 columns.)
    portraitFocusAspect: Float? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            // Stage Row (50/50 weighted columns) over a full-width gutter Box on the same grid.
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    if (focus != null) {
                        Column(
                            Modifier
                                .weight(focusGrow)
                                .fillMaxHeight(),
                            content = focus,
                        )
                    }
                    if (field != null) {
                        Column(
                            Modifier
                                .weight(fieldGrow)
                                .fillMaxHeight(),
                            content = field,
                        )
                    }
                }
                if (gutter != null) {
                    Box(Modifier.fillMaxWidth()) { gutter() }
                }
            }
        } else {
            // Portrait stack: focus / field / full-width gutter, weighted ~40/40/(content) rhythm.
            // When [portraitFocusAspect] is set, the focus is instead aspect-locked (full-width square
            // for Move's jog pad) and the field absorbs the remaining height — the square's ratio, not
            // a fixed rhythm, drives the split (sacred-square fill, no side margins).
            Column(Modifier.fillMaxSize()) {
                if (focus != null) {
                    val focusMod = if (portraitFocusAspect != null) {
                        Modifier.fillMaxWidth().aspectRatio(portraitFocusAspect)
                    } else {
                        Modifier.fillMaxWidth().weight(focusGrow)
                    }
                    Column(focusMod, content = focus)
                }
                if (field != null) {
                    // Aspect-locked focus → field takes ALL the leftover height (weight 1); otherwise
                    // the normal weighted share.
                    val fieldMod = if (portraitFocusAspect != null) {
                        Modifier.fillMaxWidth().weight(1f)
                    } else {
                        Modifier.fillMaxWidth().weight(fieldGrow)
                    }
                    Column(fieldMod, content = field)
                }
                if (gutter != null) {
                    Box(Modifier.fillMaxWidth()) { gutter() }
                }
            }
        }
    }
}
