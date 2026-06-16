package works.mees.dinghy.theme.compose

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.TextRole
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.TypeRole
import works.mees.dinghy.theme.fsSp

/**
 * Bake a [TextRole] into a Compose [TextStyle] for the active theme. The ONLY place outside the role
 * plumbing that a text `fontFamily`/`fontSize` is set (FontConformanceTest enforces this). For the
 * shrink-to-fit [works.mees.dinghy.theme.DinghyType.focusHero], use [FocusHeroText] instead.
 */
fun TextRole.toTextStyle(t: ThemeTokens): TextStyle = TextStyle(
    fontFamily = if (role == TypeRole.Ui) Geist else GeistMono,
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
            fontFamily = if (role.role == TypeRole.Ui) Geist else GeistMono,
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
