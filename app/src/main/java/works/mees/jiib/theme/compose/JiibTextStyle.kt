package works.mees.jiib.theme.compose

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
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
 * Render an arbitrary catalog [AppFont]'s own name in its OWN face, at the list-label tier, for the
 * font picker. The ONE sanctioned inline-family site outside role plumbing (FontConformanceTest
 * allowlists this file) — keeps the picker screen itself conformant.
 */
fun AppFont.previewTextStyle(t: ThemeTokens): TextStyle = TextStyle(
    fontFamily = this.family,
    fontSize = fsSp(JiibType.listLabel.baseSp, t.fs).sp,
    fontWeight = JiibType.listLabel.weight,
)
