package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.FocusInset
import works.mees.dinghy.designsystem.layout.ListFrameInset
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * Fraction of the header icon slot the INERT identity glyph is rendered at (the e-stop button keeps the
 * full slot as its tap target). < 1.0 so edge-heavy Material Symbols clear the 1U bar / card corner.
 */
private const val IDENTITY_ICON_RATIO = 0.82f

/**
 * The Focus edge — the bounded-surface border whose COLOR/FORM encodes meaning (Focus Frame law §2,
 * `.planning/notes/2026-06-12-focus-frame-law-design.md`). The edge is the constant "this is the
 * Focus" signal; what it looks like tells you why. Accent is RESERVED for [Progress].
 */
sealed interface FocusEdge {
    /** Default resting edge: neutral [ThemeTokens.outline] at list-row stroke weight. */
    data object Neutral : FocusEdge

    /**
     * Edge tinted by the item's literal data color (THEME-01 carve-out — e.g. a spool's filament
     * color). Pass the parsed color directly; it is NOT brandTint-clamped.
     */
    data class Data(val color: Color) : FocusEdge

    /**
     * Print-progress: the edge becomes a perimeter progress bar (the Scrubber's visual language
     * unwrapped around the frame). Drawn specially in [FocusFrame] — NOT a uniform border.
     * Deferred; renders borderless until implemented.
     */
    data class Progress(val fraction: Float) : FocusEdge
}

/** A resolved uniform-border stroke. */
data class EdgeStroke(val color: Color, val widthDp: Float)

/**
 * Pure (host-testable) mapping from a [FocusEdge] to its uniform border stroke, or `null` when the
 * edge is drawn specially ([FocusEdge.Progress] — a perimeter bar, not a border).
 *  - [FocusEdge.Neutral] → [outline] at 1.5dp (the list-row outline weight).
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
 * (vs the inert identity glyph) ONLY while a print is active AND a halt handler is wired. Splash /
 * previews pass `onEmergencyStop = null` and so never show an e-stop.
 */
fun headerShowsEStop(isPrinting: Boolean, onEmergencyStop: (() -> Unit)?): Boolean =
    isPrinting && onEmergencyStop != null

/**
 * Where a [FocusFrame] sits, which decides who owns its 8dp edge-registration frame (LAYOUT.md R26).
 *  - [Region]   — the FocusFrame IS the whole Focus region; it self-owns the uniform 8dp frame on
 *                 ALL four sides. Callers pass SIZING ONLY (fillMaxSize / weight) — never frame padding.
 *  - [Composed] — the FocusFrame is one card inside a larger Focus column whose siblings/wrapper own
 *                 the VERTICAL registration (sort/filter rows, a foot bar). Keeps the horizontal-only
 *                 frame; the caller column owns top/bottom. (Spool/Files/Console — Phase-2 untangles these.)
 */
enum class FocusFramePlacement { Region, Composed }

/**
 * Pure (host-testable) mapping from [FocusFramePlacement] to the FocusFrame's outer registration
 * padding. [FocusFramePlacement.Region] frames all four sides at [inset]; [FocusFramePlacement.Composed]
 * frames horizontal only (the caller column owns vertical). Value defaults to the shared [ListFrameInset].
 */
fun focusFramePadding(placement: FocusFramePlacement, inset: Dp = ListFrameInset): PaddingValues =
    when (placement) {
        FocusFramePlacement.Region -> PaddingValues(inset)
        FocusFramePlacement.Composed -> PaddingValues(horizontal = inset)
    }

/**
 * The universal Focus container (Focus Frame law). Every Focus except Webcam uses this shell.
 *
 * ## Structure
 *  - Outer frame: with [FocusFramePlacement.Region] (default) self-owns the uniform 8dp registration
 *    frame ([ListFrameInset]) on ALL four sides — callers pass SIZING ONLY (`fillMaxSize`/`weight`),
 *    never frame padding. [FocusFramePlacement.Composed] keeps the horizontal-only frame for screens
 *    whose Focus column composes its own vertical registration (Spool/Files/Console).
 *  - Header: mandatory [FocusHeader] — 1U bar with a start-icon slot and a centered/marquee title.
 *    When [isPrinting] and [onEmergencyStop] are both set, the icon slot morphs into the e-stop
 *    button (no overlay, no double e-stop); otherwise it shows the inert identity glyph [icon].
 *  - Fill: [ThemeTokens.surface] — visually distinct from the translucent list/Field area.
 *  - Edge: [FocusEdge] — [FocusEdge.Neutral] by default ([ThemeTokens.outline]); [FocusEdge.Data]
 *    tints it with item data; [FocusEdge.Progress] draws a perimeter bar (deferred).
 *  - Content clip: content is clipped to the rounded bounds — it never overflows the frame.
 *  - Inner inset: [FocusInset] (16dp) on the content area below the header.
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
 * @param modifier         caller-supplied modifier (sizing + vertical padding).
 * @param edge             the Focus edge mode; defaults to [FocusEdge.Neutral].
 * @param isPrinting       when true AND [onEmergencyStop] is non-null, the icon slot shows e-stop.
 * @param onEmergencyStop  firmware E-stop handler; null means no e-stop is ever shown.
 * @param onPanic          optional long-press instant halt (no guard) wired to the e-stop slot.
 * @param contentInset     inner inset around the content area below the header; defaults to [FocusInset]
 *                         (16dp). Screens whose content reads better tighter can pass a smaller value.
 * @param placement       see [FocusFramePlacement]; defaults to [FocusFramePlacement.Region] (self-frames
 *                        all four sides). [FocusFramePlacement.Composed] for composed-focus screens.
 * @param content          column content rendered inside the framed, clipped, padded surface.
 */
@Composable
fun FocusFrame(
    title: String,
    icon: DinghyIcon,
    iconTint: Color? = null,
    uDp: Dp,
    modifier: Modifier = Modifier,
    edge: FocusEdge = FocusEdge.Neutral,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
    onPanic: (() -> Unit)? = null,
    contentInset: Dp = FocusInset,
    placement: FocusFramePlacement = FocusFramePlacement.Region,
    trailingActionIcon: DinghyIcon? = null,
    onTrailingAction: (() -> Unit)? = null,
    trailingActionContentDescription: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val stroke = focusEdgeStroke(edge, outline = t.outline)
    Column(
        modifier = modifier
            .padding(focusFramePadding(placement)) // outer registration frame (R26): Region = all 4 sides
            .clip(shape)
            .then(
                if (stroke != null) Modifier.border(BorderStroke(stroke.widthDp.dp, stroke.color), shape)
                else Modifier, // FocusEdge.Progress draws its own perimeter bar (deferred)
            )
            .background(t.surface),
    ) {
        FocusHeader(
            title = title,
            icon = icon,
            iconTint = iconTint,
            uDp = uDp,
            isPrinting = isPrinting,
            onEmergencyStop = onEmergencyStop,
            onPanic = onPanic,
            trailingActionIcon = trailingActionIcon,
            onTrailingAction = onTrailingAction,
            trailingActionContentDescription = trailingActionContentDescription,
        )
        // Content fills the space below the header; contentInset (FocusInset by default) insets it.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(contentInset),
            content = content,
        )
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
    icon: DinghyIcon,
    iconTint: Color? = null,
    uDp: Dp,
    isPrinting: Boolean,
    onEmergencyStop: (() -> Unit)?,
    onPanic: (() -> Unit)?,
    trailingActionIcon: DinghyIcon? = null,
    onTrailingAction: (() -> Unit)? = null,
    trailingActionContentDescription: String? = null,
) {
    val t = LocalTokens.current
    var showGuard by remember { mutableStateOf(false) }
    // e-stop / icon size — matches the retired float; uDp is rotation-stable so memoize.
    val slot = remember(uDp) { (uDp * 0.7f).coerceAtLeast(64.dp) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(uDp)
            .padding(horizontal = FocusInset),
        contentAlignment = Alignment.Center,
    ) {
        // Centered title (full-width track; the start icon overlaps its left end, app-bar style).
        Text(
            text = title,
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(20f, t.fs).sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = slot) // keep the centered text clear of the start icon
                .basicMarquee(), // overflow-only scroll (motion-law exception)
        )
        // Start icon slot: e-stop while printing, else the inert identity glyph.
        Box(modifier = Modifier.align(Alignment.CenterStart)) {
            if (headerShowsEStop(isPrinting, onEmergencyStop)) {
                OutlinedControl(
                    label = "",
                    onClick = { showGuard = true },
                    onLongClick = onPanic, // long-press = instant halt, no guard (float's onHold)
                    modifier = Modifier.size(slot),
                    intent = Intent.Danger,
                    icon = DinghyIcons.StatusStop,
                    contentDescription = stringResource(R.string.cd_emergency_stop),
                )
            } else {
                // Identity glyph renders a touch smaller than the e-stop slot and is centered within
                // it, so edge-heavy Material Symbols (e.g. linear_scale / blur_linear / linked_services)
                // don't clip against the 1U header bar or the card's rounded corner. The slot itself
                // (and thus the e-stop tap target, below) is unchanged — only the inert glyph shrinks.
                Box(Modifier.size(slot), contentAlignment = Alignment.Center) {
                    DinghyIconView(
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
                DinghyIconView(
                    icon = trailingActionIcon,
                    tint = t.text2,
                    sizeDp = slot * IDENTITY_ICON_RATIO,
                    contentDescription = trailingActionContentDescription,
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
