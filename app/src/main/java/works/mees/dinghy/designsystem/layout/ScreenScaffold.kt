package works.mees.dinghy.designsystem.layout

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
 * The Focus / Field responsive layout primitive (UI-01) — the reusable skeleton EVERY jiib screen
 * is built from (docs/ui_design/LAYOUT.md). One grammar, two regions, ONE shared grid. This is
 * settled here once, not redesigned per panel.
 *
 * Slots:
 *  - [focus]  — one primary item; its SQUARE visual content is wrapped by the CALLER in
 *               `Modifier.aspectRatio(1f)` INSIDE the region and centered. The region itself is
 *               NEVER made square (NON-NEGOTIABLE 2 — the `.ringwrap`, not `.focus`).
 *  - [field]  — a divisible info/control surface (an arbitrary grid the caller fills). Screen
 *               actions live in a [works.mees.dinghy.designsystem.components.FootButtonBar] as the
 *               LAST element of the field Column (the foot-of-list pattern — the Gutter region was
 *               RETIRED 2026-06-12 with the R1 PrintStatus migration; FootButtonBar is its successor).
 *
 * Either [focus] or [field] may be null; the other takes the freed space (LAYOUT.md).
 *
 * ## Orientation (BoxWithConstraints, no Configuration read)
 *  - LANDSCAPE (`maxWidth > maxHeight`): a Row of two columns weighted [focusGrow] / [fieldGrow]
 *    (50/50 by default).
 *  - PORTRAIT (`maxWidth <= maxHeight`): a Column stacking focus / field via [focusGrow] /
 *    [fieldGrow] (LAYOUT.md §1 "Portrait").
 *
 * ## Sizing discipline (NON-NEGOTIABLE 3 — ratios only)
 * Regions are split with `weight` / `fillMax*` ONLY — NO hardcoded px for regions, cells, or
 * structural gaps. The single permitted fixed values in the design system live in the leaf controls
 * (the ≥64dp touch floor and the `--fs` text step), not here.
 */
@Composable
fun ScreenScaffold(
    modifier: Modifier = Modifier,
    focus: (@Composable ColumnScope.() -> Unit)? = null,
    field: (@Composable ColumnScope.() -> Unit)? = null,
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
            // Stage Row: 50/50 weighted Focus | Field columns.
            Row(Modifier.fillMaxSize()) {
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
        } else {
            // Portrait stack: focus over field. When [portraitFocusAspect] is set, the focus is
            // instead aspect-locked (full-width square for Move's jog pad) and the field absorbs the
            // remaining height — the square's ratio, not a fixed rhythm, drives the split
            // (sacred-square fill, no side margins).
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
            }
        }
    }
}
