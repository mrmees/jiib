package works.mees.jiib.theme.compose

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlin.math.ceil
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
 * The Focus value+unit hero (Focus-text law, 2026-06-29). Renders [value] at [JiibType.focusHero]
 * size and [unit] at a RELATIVE [unitEm] of that size, baseline-shared, as ONE line that shrinks
 * both together to fit the available width. The two-size sibling of [FocusHeroText] for the
 * adjuster/readout "120mm/s²" form (preserves the as-built value/unit size ratio).
 *
 * **Mechanism (width shrink):** `TextAutoSize.StepBased` does NOT shrink single-line AnnotatedString
 * on width overflow in Compose foundation 1.11.x (verified on-device 2026-06-29). Instead, uses
 * [BoxWithConstraints] to read the available pixel width, then steps down from [JiibType.focusHero]
 * maxSp to minSp via [rememberTextMeasurer], picking the largest size whose measured width fits.
 *
 * The `em`-relative [unitEm] span scales correctly with the chosen base [fontSize] because
 * `em` is resolved against the enclosing [TextStyle.fontSize] at render time — the unit scales
 * proportionally with the value for every chosen size in the step-down range.
 */
@Composable
fun BoxScope.FocusHeroValueText(
    value: String,
    unit: String,
    t: ThemeTokens,
    valueColor: Color,
    unitColor: Color,
    modifier: Modifier = Modifier,
    unitEm: Float = 0.65f,   // statValue(26)/focusHero(40) ≈ 0.65 — keeps the as-built size ratio
) {
    val role = JiibType.focusHero
    val maxSpScaled = fsSp(role.maxSp ?: role.baseSp, t.fs)
    val minSpScaled = fsSp(role.minSp ?: role.baseSp, t.fs)
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val maxWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.MAX_VALUE

        // Annotation for measurement: colors don't affect layout, omit them for a clean measure pass.
        val textForMeasure = buildAnnotatedString {
            append(value)
            if (unit.isNotBlank()) {
                withStyle(SpanStyle(fontSize = unitEm.em)) { append(unit) }
            }
        }

        // Step down from max to min (up to 26 steps at 1sp intervals), pick the largest that fits.
        // Falls back to minSp if nothing fits — the caller's container should prevent
        // genuinely impossible constraints (text wider than the available box at minSp).
        // Cached: re-measure only when measurement-affecting inputs change, not on every
        // color/token recomposition (hero values fire every 100–500 ms on live Moonraker data).
        val chosenSp = remember(value, unit, unitEm, maxWidthPx, t.fs, t.dataFont) {
            var result = minSpScaled
            for (sp in maxSpScaled.roundToInt() downTo ceil(minSpScaled.toDouble()).toInt()) {
                val layout = textMeasurer.measure(
                    text = textForMeasure,
                    style = TextStyle(fontFamily = t.dataFont.family, fontWeight = role.weight, fontSize = sp.sp),
                    maxLines = 1, softWrap = false,
                )
                if (layout.size.width <= maxWidthPx) { result = sp.toFloat(); break }
            }
            result
        }

        val annotated = buildAnnotatedString {
            withStyle(SpanStyle(color = valueColor)) { append(value) }
            if (unit.isNotBlank()) {
                withStyle(SpanStyle(color = unitColor, fontSize = unitEm.em)) { append(unit) }
            }
        }

        BasicText(
            text = annotated,
            style = TextStyle(
                fontFamily = t.dataFont.family,
                fontWeight = role.weight,
                fontSize = chosenSp.sp,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Result of the [focusTextFit] size-selection walk: the chosen font size (an ALREADY fs-scaled sp
 * value, ready to hand to `.sp`) and the `maxLines` to render with ([Int.MAX_VALUE] = unbounded,
 * because the text was proven to fit fully at [sizeSp]).
 */
data class FocusTextFit(val sizeSp: Int, val maxLines: Int)

/**
 * The pure size-selection walk behind [FocusText] (measurement-based height-fit). Walks candidate
 * font sizes from [maxSp] down to [minSp] in 1sp steps and picks the FIRST (largest) size whose
 * measured full-wrap height ([fullHeightAt]) fits [maxHeightPx] (within [tolerancePx], the same
 * sub-pixel forgiveness `digestFit` uses). If even [minSp] overflows, it renders at [minSp] and caps
 * `maxLines` at `floor(maxHeightPx / singleLineHeightAt(minSp))` (≥ 1) — as-many-lines-as-fit
 * truncation whose budget reflects the ACTUAL line height at the rendered size.
 *
 * Kept as a plain (non-Composable) function with measurement injected as closures so it is unit
 * testable with no Compose runtime — precedent: `digestFit`.
 */
fun focusTextFit(
    maxHeightPx: Float,
    minSp: Int,
    maxSp: Int,
    fullHeightAt: (Int) -> Float,
    singleLineHeightAt: (Int) -> Float,
    tolerancePx: Float = 0.5f,
): FocusTextFit {
    var sp = maxSp
    while (sp >= minSp) {
        if (fullHeightAt(sp) <= maxHeightPx + tolerancePx) return FocusTextFit(sp, Int.MAX_VALUE)
        sp--
    }
    val lineHeightPx = singleLineHeightAt(minSp)
    val maxLines = if (lineHeightPx > 0f) (maxHeightPx / lineHeightPx).toInt().coerceAtLeast(1) else 1
    return FocusTextFit(minSp, maxLines)
}

/**
 * Bounded Focus-body text (Focus-text law, 2026-06-29; measurement rewrite 2026-07-02). Renders
 * [text] in [role] and SHRINKS the font (role base size → [minSp], never below) so the RENDERED
 * content fits the HEIGHT budget without overflowing the FocusFrame clip. The general (non-hero)
 * sibling of [FocusHeroText]; the sanctioned replacement for raw `Text(..., style =
 * role.toTextStyle(t))` in a Focus body.
 *
 * **Mechanism (measurement-based fit):** Inside [BoxWithConstraints], when the height is bounded, a
 * [rememberTextMeasurer] actually LAYS OUT the full soft-wrapped text (at the real resolved
 * `fontFamily`/`weight`, against `constraints.maxWidth`) at each candidate size from max down to
 * [minSp], and picks the largest whose measured height fits — see [focusTextFit]. The chosen size is
 * rendered directly (no `TextAutoSize`); the choice is now ours and deterministic. This replaces the
 * earlier estimate — a `maxLines` budget derived from the MAX font size × a conservative 2.0×
 * line-height factor — which massively under-counted the box's real capacity at the auto-chosen
 * smaller size and could truncate text that would have fit (owner UAT U2: System→About lost its
 * entire second paragraph). The walk is ≤ ~25 layout-time measures and is cached (Adreno-320 budget:
 * fine — same precedent as `DigestColumn`/`digestFit`).
 *
 * When [text] is too long to fit fully even at [minSp], it renders at [minSp] and shows as many lines
 * as the height allows (line budget from the ACTUAL single-line height at [minSp], not 2.0×max). The
 * `heightIn(max = this.maxHeight)` backstop and the unbounded-height branch (render at max size,
 * `maxLines = Int.MAX_VALUE`) are unchanged.
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
) = FocusText(AnnotatedString(text), role, t, color, modifier, maxHeightU, textAlign, minSp, maxSp)

/**
 * Bounded Focus-body text — [AnnotatedString] overload (Focus-text law, 2026-07-02). Same bounding
 * contract and measurement mechanism as the [String] overload (which delegates here); [BasicText]
 * accepts [AnnotatedString] directly. Use when the caller needs inline spans (bold run, color run,
 * etc.) inside a shrink-to-fit Focus body block.
 */
@Composable
fun FocusText(
    text: AnnotatedString,
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
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = boxMod, contentAlignment = Alignment.Center) {
        val family = if (role.role == TypeRole.Ui) t.uiFont else t.dataFont
        val maxScaledSp = fsSp(maxSp ?: role.baseSp, t.fs)
        val minScaledSp = fsSp(minSp, t.fs)
        val bounded = constraints.hasBoundedHeight
        val widthBudget = constraints.maxWidth
        val maxHeightPx = constraints.maxHeight

        // Measure-based fit, cached: re-run the walk only when a measurement-affecting input changes
        // (text/role/font/size-range/width/height), not on every color or token recomposition.
        val fit = remember(text, role, family, maxScaledSp, minScaledSp, widthBudget, maxHeightPx, bounded) {
            if (!bounded) {
                FocusTextFit(maxScaledSp.roundToInt(), Int.MAX_VALUE)
            } else {
                val measureStyle = TextStyle(fontFamily = family.family, fontWeight = role.weight)
                focusTextFit(
                    maxHeightPx = maxHeightPx.toFloat(),
                    minSp = ceil(minScaledSp.toDouble()).toInt(),
                    maxSp = maxScaledSp.roundToInt(),
                    fullHeightAt = { sp ->
                        measurer.measure(
                            text = text,
                            style = measureStyle.copy(fontSize = sp.sp),
                            softWrap = true,
                            constraints = Constraints(maxWidth = widthBudget),
                        ).size.height.toFloat()
                    },
                    singleLineHeightAt = { sp ->
                        measurer.measure(
                            text = text,
                            style = measureStyle.copy(fontSize = sp.sp),
                            softWrap = false,
                            maxLines = 1,
                            constraints = Constraints(maxWidth = widthBudget),
                        ).size.height.toFloat()
                    },
                )
            }
        }

        BasicText(
            text = text,
            style = TextStyle(
                fontFamily = family.family,
                fontWeight = role.weight,
                color = color,
                fontSize = fit.sizeSp.sp,
                textAlign = textAlign,
            ),
            softWrap = true,
            maxLines = fit.maxLines,
            // heightIn caps the BasicText at the box height as a visual backstop against any
            // residual overflow (very tall custom fonts) regardless of the chosen size.
            modifier = Modifier.fillMaxWidth().then(
                if (bounded) Modifier.heightIn(max = this.maxHeight) else Modifier
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
