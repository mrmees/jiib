package works.mees.dinghy.ui.prompt

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import works.mees.dinghy.prompt.EmphasisTag
import works.mees.dinghy.prompt.MarkupNode
import works.mees.dinghy.prompt.PromptTextSize
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.theme.fsSp

/**
 * Render a PromptMarkup AST (the renderer-neutral [MarkupNode] tree from 12-01 `parseMarkup`) to a
 * Material3 [Text] backed by an [AnnotatedString] (12-04 Task 1 — the one genuinely-new Compose piece).
 *
 * ## The D-03 author-hex carve-out
 * Chrome routes through [LocalTokens] (THEME-01). The SOLE sanctioned raw `Color(...)` in the prompt UI
 * lives HERE: `<color:#hex>` / `<bgcolor:#hex>` carry AUTHOR DATA (not chrome), the same kind of carve-out
 * the Spoolman spool-color render already makes. The hex is already validated `#rrggbb` upstream
 * (`makeTagNode` / `HEX_COLOR` in Markup.kt), so an invalid color never reaches this builder — its inner
 * text was preserved at the surrounding style. There is NO HTML/Pango/WebView surface: a span is the only
 * thing author markup can produce (T-12-13 injection mitigation).
 *
 * ## Span mapping (UI-SPEC §Typography + §Markup grammar)
 *  - `<b>`            → [FontWeight.SemiBold] (600) — the LAW's emphasis weight, NOT a 3rd weight.
 *  - `<i>`            → [FontStyle.Italic].
 *  - `<u>`            → [TextDecoration.Underline].
 *  - `<color:#hex>`   → `SpanStyle(color = Color(0xFF000000 or hex))` (the carve-out).
 *  - `<bgcolor:#hex>` → `SpanStyle(background = …)` — an INLINE background span, never a layout block.
 *  - `<size:…>`       → the 15/18/22/28 `fsSp(_, t.fs)` ladder (small/normal/large/x-large).
 *  - text node        → appended under the merged ancestor style.
 *
 * Nested tags nest spans (each node opens a [withStyle] scope around its children). The default run color
 * is `--text` at 18sp Geist Regular ([[dinghy-font-sizes-too-small]]: floor 15sp; body base 18sp).
 */
@Composable
fun PromptMarkupText(
    nodes: List<MarkupNode>,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val annotated = buildPromptAnnotatedString(nodes, t)
    Text(
        text = annotated,
        modifier = modifier,
        // Default run color/size/weight (a run with no <color>/<size>/<b> uses these): `--text`, the body
        // role (Geist 20 Med). Individual spans override color/background/size/weight/style as the AST dictates.
        color = t.text,
        style = DinghyType.body.toTextStyle(t),
    )
}

/**
 * Pure-ish (Compose-text only, no composition) AST → [AnnotatedString] fold. Exposed internal so the
 * 12-05 UI test can build and inspect the string without a full composition if desired.
 */
internal fun buildPromptAnnotatedString(
    nodes: List<MarkupNode>,
    t: ThemeTokens,
): AnnotatedString = buildAnnotatedString {
    appendNodes(this, nodes, t)
}

private fun appendNodes(
    builder: androidx.compose.ui.text.AnnotatedString.Builder,
    nodes: List<MarkupNode>,
    t: ThemeTokens,
) {
    for (node in nodes) {
        when (node) {
            is MarkupNode.Text -> builder.append(node.text)
            is MarkupNode.Emphasis -> builder.withStyle(emphasisSpan(node.tag)) {
                appendNodes(builder, node.children, t)
            }
            is MarkupNode.Color -> builder.withStyle(colorSpan(node)) {
                appendNodes(builder, node.children, t)
            }
            is MarkupNode.Size -> builder.withStyle(sizeSpan(node.value, t)) {
                appendNodes(builder, node.children, t)
            }
        }
    }
}

/** `<b>`→SemiBold(600), `<i>`→Italic, `<u>`→Underline (LAW weight policy — never a 3rd weight). */
private fun emphasisSpan(tag: EmphasisTag): SpanStyle = when (tag) {
    EmphasisTag.B -> SpanStyle(fontWeight = FontWeight.SemiBold)
    EmphasisTag.I -> SpanStyle(fontStyle = FontStyle.Italic)
    EmphasisTag.U -> SpanStyle(textDecoration = TextDecoration.Underline)
}

/**
 * The D-03 author-hex carve-out — the ONLY raw [Color] from author data in the prompt UI. [node].value
 * is a validated `#rrggbb` (upstream `HEX_COLOR` guard), so `0xFF` (opaque alpha) prepended + the 6 hex
 * digits is always a valid 32-bit ARGB. `<bgcolor>` sets `background`; `<color>` sets foreground `color`.
 */
private fun colorSpan(node: MarkupNode.Color): SpanStyle {
    val argb = ("FF" + node.value.removePrefix("#")).toLong(16)
    val color = Color(argb)
    return if (node.background) SpanStyle(background = color) else SpanStyle(color = color)
}

/** The 15/18/22/28 `--fs`-scaled size ladder (UI-SPEC); a bare `.sp` literal is never used for body type. */
private fun sizeSpan(size: PromptTextSize, t: ThemeTokens): SpanStyle {
    val baseSp = when (size) {
        PromptTextSize.SMALL -> 15f
        PromptTextSize.NORMAL -> 18f
        PromptTextSize.LARGE -> 22f
        PromptTextSize.X_LARGE -> 28f
    }
    val unit: TextUnit = fsSp(baseSp, t.fs).sp
    return SpanStyle(fontSize = unit)
}
