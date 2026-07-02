package works.mees.jiib.designsystem.focus

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import works.mees.jiib.designsystem.layout.FocusZones
import works.mees.jiib.designsystem.layout.focusZoneInsetFor
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.TextRole
import works.mees.jiib.theme.compose.FocusText
import works.mees.jiib.theme.compose.LocalTokens

/**
 * Archetype #1 — one centered prose block (LAW 1 Body, LAW 2 shrink via FocusText).
 * Optional watermark behind the text; optional dock (pulls the Dense inset per LAW 4).
 */
@Composable
fun FocusExplainer(
    text: String,
    modifier: Modifier = Modifier,
    role: TextRole = JiibType.body,
    color: Color? = null,
    maxSp: Float? = null,
    textAlign: TextAlign = TextAlign.Center,
    watermark: (@Composable BoxScope.() -> Unit)? = null,
    dock: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val t = LocalTokens.current
    FocusZones(
        inset = focusZoneInsetFor(hasDock = dock != null),
        modifier = modifier,
        dock = dock,
        body = {
            watermark?.invoke(this)
            FocusText(
                text = text,
                role = role,
                t = t,
                color = color ?: t.text2,
                modifier = Modifier.fillMaxSize(),
                textAlign = textAlign,
                maxSp = maxSp,
            )
        },
    )
}

@Composable
fun FocusExplainer(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    role: TextRole = JiibType.body,
    color: Color? = null,
    maxSp: Float? = null,
    textAlign: TextAlign = TextAlign.Center,
    watermark: (@Composable BoxScope.() -> Unit)? = null,
    dock: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val t = LocalTokens.current
    FocusZones(
        inset = focusZoneInsetFor(hasDock = dock != null),
        modifier = modifier,
        dock = dock,
        body = {
            watermark?.invoke(this)
            FocusText(
                text = text,
                role = role,
                t = t,
                color = color ?: t.text2,
                modifier = Modifier.fillMaxSize(),
                textAlign = textAlign,
                maxSp = maxSp,
            )
        },
    )
}
