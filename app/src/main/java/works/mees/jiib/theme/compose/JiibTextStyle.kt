package works.mees.jiib.theme.compose

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.jiib.designsystem.layout.LocalUnitDp
import works.mees.jiib.theme.AppFont
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.TextRole
import works.mees.jiib.theme.ThemeTokens
import works.mees.jiib.theme.TypeRole
import works.mees.jiib.theme.fsSp

/**
 * Bake a [TextRole] into a Compose [TextStyle] for the active theme. The ONLY place outside the role
 * plumbing that a text `fontFamily`/`fontSize` is set (FontConformanceTest enforces this). For the
 * shrink-to-fit [works.mees.jiib.theme.JiibType.focusHero], use [FocusHeroText] instead.
 */
fun TextRole.toTextStyle(t: ThemeTokens): TextStyle = TextStyle(
    fontFamily = (if (role == TypeRole.Ui) t.uiFont else t.dataFont).family,
    fontSize = fsSp(baseSp, t.fs).sp,
    fontWeight = weight,
)

/**
 * Like [toTextStyle] but at an EXPLICIT, already-fs-scaled size — for UNIFORM shrink-to-fit blocks
 * (e.g. the home digest) where several rows must share ONE caller-measured size, which per-text
 * [FocusHeroText] autosize cannot coordinate. [sizeSp] is a final sp value (the caller already applied
 * [fsSp]). This is the sanctioned size-application point (FontConformanceTest allowlists this file).
 */
fun TextRole.toTextStyle(t: ThemeTokens, sizeSp: Float): TextStyle =
    toTextStyle(t).copy(fontSize = sizeSp.sp)

/**
 * The Focus region's primary value: renders at [TextRole.maxSp] when there's room and steps DOWN to
 * [TextRole.minSp] to fit, never up — the sanctioned shrink-to-fit pattern (THEMING.md). Pass the
 * focusHero role (or any role carrying max/min). Centered, single line, by default.
 */
@Composable
fun BoxScope.FocusHeroText(
    text: String,
    role: TextRole,
    t: ThemeTokens,
    color: Color,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
) {
    val max = (role.maxSp ?: role.baseSp)
    val min = (role.minSp ?: role.baseSp)
    BasicText(
        text = text,
        style = TextStyle(
            fontFamily = (if (role.role == TypeRole.Ui) t.uiFont else t.dataFont).family,
            fontWeight = role.weight,
            color = color,
            textAlign = textAlign,
        ),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(
            minFontSize = fsSp(min, t.fs).sp,
            maxFontSize = fsSp(max, t.fs).sp,
            stepSize = 1.sp,
        ),
        modifier = modifier,
    )
}

/**
 * Bounded Focus-body text (Focus-text law, 2026-06-29). Renders [text] in [role] and SHRINKS the font
 * (role base size → [minSp], never below) so the RENDERED content fits the HEIGHT budget without
 * overflowing the FocusFrame clip. The general (non-hero) sibling of [FocusHeroText]; the sanctioned
 * replacement for raw `Text(..., style = role.toTextStyle(t))` in a Focus body.
 *
 * **Mechanism:** Uses [BoxWithConstraints] to read the bounded pixel height, then derives a line-count
 * budget (`maxLines`) from the max font size using a conservative line-height factor of 2.0×. This
 * lets [TextAutoSize.StepBased] shrink via `didExceedMaxLines` — the mechanism that actually works for
 * soft-wrapped text (`didOverflowHeight` alone does NOT trigger autosize shrinkage in Compose
 * foundation 1.11.x). With `maxLines` computed at the MAX font size and rendered at any SMALLER
 * auto-chosen size, the rendered line height is always ≤ the estimated budget, so `didOverflowHeight`
 * stays false. When text is too long to fit fully (very long strings in small boxes), the composable
 * displays as many lines as the height allows at the smallest readable size.
 *
 * The factor is 2.0× (rather than a tighter Geist-specific 1.5×) because this app exposes a
 * user-selectable font library (16 UI + 10 Data faces). Latin faces with taller natural metrics can
 * carry line-height ratios above 1.5×; a face that exceeds the factor would force mid-line clipping
 * instead of a clean line boundary. 2.0× is a safe upper bound for the full bundled library — the
 * `heightIn(max = this.maxHeight)` safety net below remains as a visual backstop regardless.
 *
 * Vertical budget (use one): [maxHeightU] caps at N unit-grid heights (U from [LocalUnitDp]); or pass
 * `Modifier.weight(1f)`/`fillMaxSize()` in [modifier] to fill the leftover slot.
 *
 * WIDTH CONTRACT: the inner BasicText fills the budget box width, so the caller MUST give [modifier] a
 * bounded width — `weight(1f)` (in a Row OR Column) or `fillMaxSize()`. Do NOT drop a bare [FocusText]
 * into a Row cell without `weight(1f)`/a width, or it will greedily measure too wide.
 */
@Composable
fun FocusText(
    text: String,
    role: TextRole,
    t: ThemeTokens,
    color: Color,
    modifier: Modifier = Modifier,
    maxHeightU: Float? = null,
    textAlign: TextAlign = TextAlign.Center,
    minSp: Float = 15f,
    maxSp: Float? = null,
) {
    val uDp = LocalUnitDp.current ?: 64.dp   // LocalUnitDp is Dp? (null until a U-aware container provides it)
    val boxMod = modifier.then(
        if (maxHeightU != null) Modifier.heightIn(max = uDp * maxHeightU) else Modifier,
    )
    BoxWithConstraints(modifier = boxMod, contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val maxFontSizeSp = fsSp(maxSp ?: role.baseSp, t.fs)
        // Compute a line-count budget from the height constraint. Factor 2.0 is a safe upper bound
        // for the line-height-to-font-size ratio across the full user-selectable font library (16 UI
        // + 10 Data faces). Geist actual ≈ 1.4×, but faces with taller metrics can exceed 1.5×;
        // using 2.0× ensures that N rendered lines at ANY bundled font ≤ maxSp will not exceed the
        // box height — because rendered_height ≤ N × actual_lineHeight ≤ N × factor × maxSp_px
        // ≤ floor(maxHeight / (factor × maxSp_px)) × factor × maxSp_px ≤ maxHeight.
        val maxLines = if (constraints.hasBoundedHeight) {
            val lineHeightPx = with(density) { (maxFontSizeSp * 2.0f).sp.toPx() }
            (constraints.maxHeight.toFloat() / lineHeightPx).toInt().coerceAtLeast(1)
        } else Int.MAX_VALUE

        BasicText(
            text = text,
            style = TextStyle(
                fontFamily = (if (role.role == TypeRole.Ui) t.uiFont else t.dataFont).family,
                fontWeight = role.weight,
                color = color,
                textAlign = textAlign,
            ),
            softWrap = true,
            maxLines = maxLines,
            autoSize = TextAutoSize.StepBased(
                minFontSize = fsSp(minSp, t.fs).sp,
                maxFontSize = maxFontSizeSp.sp,
                stepSize = 1.sp,
            ),
            // fillMaxWidth gives bounded width for text wrapping.
            // heightIn caps the BasicText at the box height so didOverflowHeight reflects the
            // bounded constraint rather than defaulting to Infinity (always-false).
            modifier = Modifier.fillMaxWidth().then(
                if (constraints.hasBoundedHeight) Modifier.heightIn(max = this.maxHeight)
                else Modifier
            ),
        )
    }
}

/**
 * Render an arbitrary catalog [AppFont]'s own name in its OWN face, at the list-label tier, for the
 * font picker. The ONE sanctioned inline-family site outside role plumbing (FontConformanceTest
 * allowlists this file) — keeps the picker screen itself conformant.
 */
fun AppFont.previewTextStyle(t: ThemeTokens): TextStyle = TextStyle(
    fontFamily = this.family,
    fontSize = fsSp(JiibType.listLabel.baseSp, t.fs).sp,
    fontWeight = JiibType.listLabel.weight,
)
