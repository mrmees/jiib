package works.mees.jiib.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import works.mees.jiib.R
import works.mees.jiib.designsystem.ConfirmGuard
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIconView
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.FocusInset
import works.mees.jiib.designsystem.layout.LocalUnitDp
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.ThemeTokens
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * Fraction of the header icon slot the INERT identity glyph is rendered at (the e-stop button keeps the
 * full slot as its tap target). < 1.0 so edge-heavy Material Symbols clear the 1U bar / card corner.
 */
private const val IDENTITY_ICON_RATIO = 0.82f

/** The resolved title padding for the Focus header, in whole icon-slot units (Task 1). */
internal data class HeaderTitleLayout(val startSlots: Int, val endSlots: Int, val marquee: Boolean)

/**
 * Decide the Focus-header title padding. The title stays TRULY centered (both slots reserved) whenever
 * it fits within the symmetric budget `available - 2*slot`; only an OVERFLOWING title reclaims the
 * trailing slot — and only when no end glyph occupies it — and marquees. Pure for unit testing.
 */
internal fun resolveHeaderTitleLayout(
    availableWidthPx: Float,
    titleWidthPx: Float,
    slotPx: Float,
    endSlotOccupied: Boolean,
): HeaderTitleLayout {
    val symmetricBudget = (availableWidthPx - 2f * slotPx).coerceAtLeast(0f)
    val fits = titleWidthPx <= symmetricBudget
    return if (fits) {
        HeaderTitleLayout(startSlots = 1, endSlots = 1, marquee = false)
    } else {
        HeaderTitleLayout(startSlots = 1, endSlots = if (endSlotOccupied) 1 else 0, marquee = true)
    }
}

/** Perimeter progress-bar stroke weight — heavier than the 3dp Data edge so the bar reads as a gauge. */
private const val PROGRESS_STROKE_DP = 4f

/**
 * Draw the [FocusEdge.Progress] bar: a rounded-rect perimeter traced CLOCKWISE FROM TOP-CENTER,
 * stroked for the first [fraction] of its length. The path is inset by half the stroke (and its
 * corner radius shrunk to match) so the full stroke sits inside the frame's clip — never clipped to
 * half-width on the outer edge.
 */
private fun DrawScope.drawFocusProgress(
    fraction: Float,
    color: Color,
    strokeWidthPx: Float,
    cornerRadiusPx: Float,
) {
    // Non-finite fraction (NaN/Inf from a degenerate progress value) → draw nothing, never crash.
    val f = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
    if (f <= 0f) return
    val inset = strokeWidthPx / 2f
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset
    // Frame smaller than the stroke (transient 0-size layout pass) → the inset rect is degenerate and
    // `coerceIn(0f, negative)` would throw; bail before drawing.
    val maxR = minOf(right - left, bottom - top) / 2f
    if (maxR <= 0f) return
    // Shrink the corner radius by the same inset so the arc stays CONCENTRIC with the frame corner.
    val r = (cornerRadiusPx - inset).coerceIn(0f, maxR)
    val cx = (left + right) / 2f
    val path = Path().apply {
        moveTo(cx, top)
        lineTo(right - r, top)
        arcTo(Rect(right - 2 * r, top, right, top + 2 * r), -90f, 90f, false)         // top-right
        lineTo(right, bottom - r)
        arcTo(Rect(right - 2 * r, bottom - 2 * r, right, bottom), 0f, 90f, false)      // bottom-right
        lineTo(left + r, bottom)
        arcTo(Rect(left, bottom - 2 * r, left + 2 * r, bottom), 90f, 90f, false)       // bottom-left
        lineTo(left, top + r)
        arcTo(Rect(left, top, left + 2 * r, top + 2 * r), 180f, 90f, false)            // top-left
        lineTo(cx, top)
    }
    val measure = PathMeasure().apply { setPath(path, false) }
    val dest = Path()
    measure.getSegment(0f, measure.length * f, dest, true)
    drawPath(dest, color, style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round))
}

/**
 * The Focus edge — the bounded-surface border whose COLOR/FORM encodes meaning (Focus Frame law §2,
 * `.planning/notes/2026-06-12-focus-frame-law-design.md`). The edge is the constant "this is the
 * Focus" signal; what it looks like tells you why. The resting edge carries a soft accent tint
 * ([ThemeTokens.accentLine]); the heavier full-strength accent stays the [Progress] perimeter bar's.
 */
sealed interface FocusEdge {
    /**
     * Default resting edge: a soft accent tint ([ThemeTokens.accentLine], the half-alpha accent that
     * also outlines selected list rows / focused fields) at list-row stroke weight — so a Focus frame
     * reads as "accent, but quiet" and rhymes with the rest of the accent-selection chrome.
     */
    data object Neutral : FocusEdge

    /**
     * Edge tinted by the item's literal data color (THEME-01 carve-out — e.g. a spool's filament
     * color). Pass the parsed color directly; it is NOT brandTint-clamped.
     */
    data class Data(val color: Color) : FocusEdge

    /**
     * Print-progress perimeter bar (the Scrubber's visual language unwrapped around the frame):
     * a [color] stroke tracing the rounded-rect perimeter CLOCKWISE FROM TOP-CENTER, arc length =
     * [fraction] (0..1). 0 → invisible; .5 → reaches 6 o'clock; .75 → 9 o'clock; 1 → closed loop.
     * Drawn specially in [FocusFrame] — NOT a uniform border. [color] is accent while printing,
     * the heat/amber token while paused (the caller resolves it from tokens).
     */
    data class Progress(val fraction: Float, val color: Color) : FocusEdge
}

/** A resolved uniform-border stroke. */
data class EdgeStroke(val color: Color, val widthDp: Float)

/**
 * Pure (host-testable) mapping from a [FocusEdge] to its uniform border stroke, or `null` when the
 * edge is drawn specially ([FocusEdge.Progress] — a perimeter bar, not a border).
 *  - [FocusEdge.Neutral] → the resting-edge [outline] color at 1.5dp (the list-row stroke weight).
 *    The caller feeds [ThemeTokens.accentLine] for the soft-accent resting edge.
 *  - [FocusEdge.Data]    → the literal data color at 3dp (heavier so the color reads as a signal).
 *  - [FocusEdge.Progress]→ `null` (perimeter bar drawn by [FocusFrame]).
 */
fun focusEdgeStroke(edge: FocusEdge, outline: Color): EdgeStroke? = when (edge) {
    FocusEdge.Neutral -> EdgeStroke(outline, 1.5f)
    is FocusEdge.Data -> EdgeStroke(edge.color, 3f)
    is FocusEdge.Progress -> null
}

/**
 * Pure (host-testable) rule for the Focus header icon slot: the slot renders the e-stop button
 * (vs the inert identity glyph) when a print is active OR [safetyActive] is true, AND a halt
 * handler is wired. Splash / previews pass `onEmergencyStop = null` and so never show an e-stop.
 *
 * [safetyActive] is set by gated screens (homing, mesh, jog, extrude) whenever the gating state
 * is non-Idle, so the e-stop is reachable during ALL motion-gated operations — not only while
 * printing. The [isPrinting] path is preserved unchanged; [safetyActive] only WIDENS the trigger.
 */
fun headerShowsEStop(
    isPrinting: Boolean,
    onEmergencyStop: (() -> Unit)?,
    safetyActive: Boolean = false,
): Boolean = (isPrinting || safetyActive) && onEmergencyStop != null

/**
 * The universal Focus container (Focus Frame law). Every Focus except Webcam uses this shell.
 *
 * ## Structure
 *  - Outer frame: NONE. The enclosing region ([works.mees.jiib.designsystem.layout.RegisteredRegion])
 *    owns the 8dp edge-registration frame; FocusFrame is flush. Callers pass SIZING ONLY
 *    (`fillMaxSize`/`weight`).
 *  - Header: mandatory [FocusHeader] — 1U bar with a start-icon slot and a centered/marquee title.
 *    When [isPrinting] and [onEmergencyStop] are both set, the icon slot morphs into the e-stop
 *    button (no overlay, no double e-stop); otherwise it shows the inert identity glyph [icon].
 *  - Fill: [ThemeTokens.surface] — visually distinct from the translucent list/Field area.
 *  - Edge: [FocusEdge] — [FocusEdge.Neutral] by default (soft-accent [ThemeTokens.accentLine]);
 *    [FocusEdge.Data] tints it with item data; [FocusEdge.Progress] draws a perimeter bar (deferred).
 *  - Content clip: content is clipped to the rounded bounds — it never overflows the frame.
 *  - Inner inset: [FocusInset] (16dp) on the content area's SIDES + BOTTOM only; the TOP inset is 0
 *    (the 1U header bar already separates), so content sits directly under the header.
 *
 * ## THEME-01 data carve-out — [FocusEdge.Data]
 * Carries the item's actual physical color hex (e.g. Spoolman colorSwatches). It is item DATA, not a
 * chrome token, and must NOT be brandTint-clamped. All other colors are role tokens.
 *
 * @param title            the screen/section name shown in the header.
 * @param icon             the identity glyph shown in the header's start slot when not printing.
 * @param iconTint        item-data override for the identity glyph tint; null (default) keeps
 *                        [ThemeTokens.text2]. THEME-01 data carve-out (e.g. a spool's filament
 *                        color) — pass parsed item data, never a brand/role token.
 * @param uDp              the unit grid value (1U) for the header height and icon sizing.
 * @param modifier         caller-supplied modifier — SIZING ONLY (`fillMaxSize`/`weight`); the 8dp
 *                        registration frame is owned by the enclosing [works.mees.jiib.designsystem.layout.RegisteredRegion],
 *                        never by the caller.
 * @param edge             the Focus edge mode; defaults to [FocusEdge.Neutral].
 * @param isPrinting       when true AND [onEmergencyStop] is non-null, the icon slot shows e-stop.
 * @param safetyActive     when true, the e-stop shows regardless of [isPrinting] — used by gated
 *                         screens (homing, mesh, jog, extrude) to show the e-stop during ALL
 *                         non-Idle gating states, not only while a print is active. Defaults to
 *                         false so all existing non-gated callers are byte-for-byte unchanged.
 * @param onEmergencyStop  firmware E-stop handler; null means no e-stop is ever shown.
 * @param onPanic          optional long-press instant halt (no guard) wired to the e-stop slot.
 * @param contentInset     inner inset on the content area's SIDES + BOTTOM (the TOP is always 0 so
 *                         content sits flush under the header); defaults to [FocusInset] (16dp).
 *                         Screens whose content reads better tighter can pass a smaller value.
 * @param content          column content rendered inside the framed, clipped, padded surface.
 */
@Composable
fun FocusFrame(
    title: String,
    icon: JiibIcon,
    iconTint: Color? = null,
    titleColor: Color? = null,
    uDp: Dp,
    modifier: Modifier = Modifier,
    edge: FocusEdge = FocusEdge.Neutral,
    isPrinting: Boolean = false,
    safetyActive: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
    onPanic: (() -> Unit)? = null,
    contentInset: Dp = FocusInset,
    trailingActionIcon: JiibIcon? = null,
    onTrailingAction: (() -> Unit)? = null,
    trailingActionContentDescription: String? = null,
    trailingStatusIcon: JiibIcon? = null,
    trailingStatusTint: Color? = null,
    trailingStatusContentDescription: String? = null,
    trailingStatusDotColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val stroke = focusEdgeStroke(edge, outline = t.accentLine)
    Column(
        modifier = modifier
            .clip(shape)
            .then(
                if (stroke != null) Modifier.border(BorderStroke(stroke.widthDp.dp, stroke.color), shape)
                else Modifier, // FocusEdge.Progress draws its own perimeter bar (below)
            )
            .background(t.surface)
            .then(
                if (edge is FocusEdge.Progress) Modifier.drawWithContent {
                    drawContent()
                    drawFocusProgress(
                        fraction = edge.fraction,
                        color = edge.color,
                        strokeWidthPx = PROGRESS_STROKE_DP.dp.toPx(),
                        cornerRadiusPx = t.rCard.toPx(),
                    )
                } else Modifier,
            ),
    ) {
        FocusHeader(
            title = title,
            icon = icon,
            iconTint = iconTint,
            titleColor = titleColor,
            uDp = uDp,
            isPrinting = isPrinting,
            safetyActive = safetyActive,
            onEmergencyStop = onEmergencyStop,
            onPanic = onPanic,
            trailingActionIcon = trailingActionIcon,
            onTrailingAction = onTrailingAction,
            trailingActionContentDescription = trailingActionContentDescription,
            trailingStatusIcon = trailingStatusIcon,
            trailingStatusTint = trailingStatusTint,
            trailingStatusContentDescription = trailingStatusContentDescription,
            trailingStatusDotColor = trailingStatusDotColor,
        )
        // Header/content divider (owner UAT 2026-06-15): a full-width hairline landmark under the
        // title so centered content reads against a clear top boundary instead of floating in the
        // borderless surface. Sits flush at the 1U header bottom (the content top inset is 0).
        Box(Modifier.fillMaxWidth().height(1.dp).background(t.outline))
        // Content fills the space below the header; contentInset (FocusInset by default) insets it.
        // Provide LocalUnitDp = uDp so EVERY control in the focus body floors at 1U (uDp) and sizes
        // its glyph to the 0.6U tier — matching the foot bar. Without this, a standalone focus button
        // falls to the flat 64dp floor and reads SHORTER than foot-bar buttons on larger screens
        // (owner UAT 2026-06-14 — the Move "pressed bookmark" Move/Delete buttons). weight(1f) must be
        // computed in this ColumnScope, so build the modifier here and pass it into the provider.
        val contentModifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            // TOP inset dropped (owner UAT 2026-06-15): the 1U header bar already separates header
            // from content, so an extra gap below it only pushed content down — most visibly with
            // vertically-centered panes. Keep the side + bottom inset for breathing room.
            .padding(start = contentInset, end = contentInset, bottom = contentInset)
        CompositionLocalProvider(LocalUnitDp provides uDp) {
            Column(modifier = contentModifier, content = content)
        }
    }
}

/**
 * The mandatory Focus header (Focus-header law, 2026-06-13): a 1U bar with a `start` icon slot and a
 * centered title. The icon slot is the screen's inert identity glyph normally; while a print is active
 * ([headerShowsEStop]) it morphs in place into the emergency-stop button (no overlay, no new element).
 * Title is centered across the full width; if it can't fit at the standard size it scrolls
 * ([basicMarquee]) — a named motion-law exception (single-line, overflow-only).
 */
@Composable
private fun FocusHeader(
    title: String,
    icon: JiibIcon,
    iconTint: Color? = null,
    titleColor: Color? = null,
    uDp: Dp,
    isPrinting: Boolean,
    safetyActive: Boolean,
    onEmergencyStop: (() -> Unit)?,
    onPanic: (() -> Unit)?,
    trailingActionIcon: JiibIcon? = null,
    onTrailingAction: (() -> Unit)? = null,
    trailingActionContentDescription: String? = null,
    trailingStatusIcon: JiibIcon? = null,
    trailingStatusTint: Color? = null,
    trailingStatusContentDescription: String? = null,
    trailingStatusDotColor: Color? = null,
) {
    val t = LocalTokens.current
    var showGuard by remember { mutableStateOf(false) }
    // e-stop / icon size — matches the retired float; uDp is rotation-stable so memoize.
    val slot = remember(uDp) { (uDp * 0.7f).coerceAtLeast(64.dp) }
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(uDp)
            .padding(horizontal = FocusInset),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val titleStyle = remember(t.fs, density.fontScale) { JiibType.focusHeader.toTextStyle(t) }
        val slotPx = with(density) { slot.toPx() }
        val availPx = with(density) { maxWidth.toPx() }
        // Memoize the single-line intrinsic width; re-measure only when the text, width, or scale changes.
        val titleWidthPx = remember(title, maxWidth, t.fs, density.density, density.fontScale) {
            measurer.measure(
                text = AnnotatedString(title),
                style = titleStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width.toFloat()
        }
        val endSlotOccupied =
            (trailingActionIcon != null && onTrailingAction != null) ||
                trailingStatusIcon != null || trailingStatusDotColor != null
        val layout = resolveHeaderTitleLayout(availPx, titleWidthPx, slotPx, endSlotOccupied)
        // Centered title: symmetric padding when it fits (true center); reclaim the trailing slot only
        // when it overflows AND no end glyph occupies it; marquee only on the overflow path.
        Text(
            text = title,
            color = titleColor ?: t.text,
            style = titleStyle,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = slot * layout.startSlots, end = slot * layout.endSlots)
                .then(if (layout.marquee) Modifier.basicMarquee() else Modifier),
        )
        // Start icon slot: e-stop while printing, else the inert identity glyph. BOTH render as a bare
        // glyph centered in the slot at the same IDENTITY_ICON_RATIO size — so the e-stop CLEANLY
        // REPLACES the identity glyph (same place/size), it is NOT a bordered button. The e-stop glyph
        // is tinted t.stop (the stop color) and carries tap→guard / long-press→panic on the slot. (The
        // old OutlinedControl wrapper double-boxed the disabled_by_default glyph — itself a square — and
        // tinted it text-color, not red; owner UAT 2026-06-16.)
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            if (headerShowsEStop(isPrinting, onEmergencyStop, safetyActive)) {
                Box(
                    modifier = Modifier
                        .size(slot)
                        .clip(RoundedCornerShape(t.rCtrl))
                        .combinedClickable(
                            onClick = { showGuard = true },
                            onLongClick = onPanic, // long-press = instant halt, no guard (float's onHold)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    JiibIconView(
                        icon = JiibIcons.StatusStop,
                        tint = t.stop,
                        sizeDp = slot * IDENTITY_ICON_RATIO,
                        contentDescription = stringResource(R.string.cd_emergency_stop),
                    )
                }
            } else {
                // Identity glyph renders a touch smaller than the e-stop slot and is centered within
                // it, so edge-heavy Material Symbols (e.g. linear_scale / blur_linear / linked_services)
                // don't clip against the 1U header bar or the card's rounded corner. The slot itself
                // (and thus the e-stop tap target, above) is unchanged — only the inert glyph shrinks.
                Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
                    JiibIconView(
                        icon = icon,
                        tint = iconTint ?: t.text2,
                        sizeDp = slot * IDENTITY_ICON_RATIO,
                    )
                }
            }
        }
        // End slot: optional BARE tappable action glyph (setting-adjustment compliance, 2026-06-13).
        // Mirrors the start identity icon — neutral text2 tint, same IDENTITY_ICON_RATIO size, NO
        // outline/fill. Reserved for SAFE actions only (e.g. revert-to-default); anything caution/
        // destructive stays a content button under the four-class intent law (THEMING.md).
        if (trailingActionIcon != null && onTrailingAction != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(slot)
                    .clip(RoundedCornerShape(t.rCtrl))
                    .clickable(onClick = onTrailingAction),
                contentAlignment = Alignment.Center,
            ) {
                JiibIconView(
                    icon = trailingActionIcon,
                    tint = t.text2,
                    sizeDp = slot * IDENTITY_ICON_RATIO,
                    contentDescription = trailingActionContentDescription,
                )
            }
        }
        // End slot, status variant: a NON-interactive indicator glyph (e.g. the loaded check). Mutually
        // exclusive with the tappable trailing action above — the action wins the slot if both are set.
        if (trailingStatusIcon != null && !(trailingActionIcon != null && onTrailingAction != null)) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(slot),
                contentAlignment = Alignment.Center,
            ) {
                JiibIconView(
                    icon = trailingStatusIcon,
                    tint = trailingStatusTint ?: t.text2,
                    sizeDp = slot * IDENTITY_ICON_RATIO,
                    contentDescription = trailingStatusContentDescription,
                )
            }
        }
        // End slot, status DOT variant: a NON-interactive colored circle (a shape, not a glyph —
        // icon-law safe). Used by Probe Test for the live probe-switch status (open/triggered).
        // Lowest priority — yields the slot to the tappable action and the status glyph.
        val dotColor = trailingStatusDotColor
        if (dotColor != null &&
            trailingStatusIcon == null &&
            !(trailingActionIcon != null && onTrailingAction != null)
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(slot),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(slot * 0.24f)
                        .clip(CircleShape)
                        .background(dotColor)
                        .then(
                            trailingStatusContentDescription?.let { cd ->
                                Modifier.semantics { contentDescription = cd }
                            } ?: Modifier,
                        ),
                )
            }
        }
    }

    // Internal e-stop ConfirmGuard, hoisted into a Dialog so the scrim escapes the Focus region and
    // covers the whole screen (ConfirmGuard is a fillMaxSize scrim). Replaces every screen's own
    // showEstopGuard + screen-level ConfirmGuard for the e-stop.
    if (showGuard) {
        Dialog(
            onDismissRequest = { showGuard = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ConfirmGuard(
                title = stringResource(R.string.printstatus_estop_guard_title),
                message = stringResource(R.string.printstatus_estop_guard_message),
                confirmLabel = stringResource(R.string.printstatus_estop_guard_confirm),
                cancelLabel = stringResource(R.string.common_cancel),
                onConfirm = { onEmergencyStop?.invoke(); showGuard = false },
                onCancel = { showGuard = false },
                destructive = true,
            )
        }
    }
}

/**
 * A [Modifier] extension that applies the card surface treatment to any composable: clip to
 * [ThemeTokens.rCard] radius, [ThemeTokens.surface] background, and a 1dp [ThemeTokens.hair]
 * decorative hairline border.
 *
 * The lightweight variant for call sites that need the card look without the [FocusFrame] Column
 * wrapper — embedding an image or custom layout in a card-styled container. This is the embryonic
 * shared "bounded surface" primitive (fill + edge + radius) the whole system could one day unify on.
 *
 * **THEME-01 compliance:** all colors are role tokens; no raw `Color(0x…)`.
 */
fun Modifier.cardSurface(t: ThemeTokens): Modifier =
    this
        .clip(RoundedCornerShape(t.rCard))
        .background(t.surface)
        .border(BorderStroke(1.dp, t.hair), RoundedCornerShape(t.rCard))
