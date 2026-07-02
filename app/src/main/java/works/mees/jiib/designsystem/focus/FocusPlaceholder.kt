package works.mees.jiib.designsystem.focus

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import works.mees.jiib.designsystem.components.FocusGlyph
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.layout.FocusZoneInset
import works.mees.jiib.designsystem.layout.FocusZones
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.FocusText
import works.mees.jiib.theme.compose.LocalTokens

/**
 * Archetype #2 — empty/placeholder state: proportional glyph (LAW 3) + optional headline/prompt.
 * All parts optional so it covers glyph-only (Files) and text-only (PrinterSettings empty).
 *
 * When [icon] is null the body Box of FocusZones (contentAlignment = Center) centers the
 * headline+text Column as a block — no explicit centering needed in the Column itself.
 */
@Composable
fun FocusPlaceholder(
    icon: JiibIcon? = null,
    modifier: Modifier = Modifier,
    headline: String? = null,
    text: String? = null,
    tint: Color? = null,
    fraction: Float = 0.4f,
    contentDescription: String? = null,
) {
    val t = LocalTokens.current
    FocusZones(inset = FocusZoneInset.Default, modifier = modifier, body = {
        Column(
            modifier = if (icon != null) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (icon != null) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    FocusGlyph(
                        icon = icon,
                        modifier = Modifier.fillMaxSize(),
                        fraction = fraction,
                        tint = tint,
                        contentDescription = contentDescription,
                    )
                }
            }
            if (headline != null) {
                FocusText(headline, JiibType.focusHeader, t, t.text, Modifier.fillMaxWidth(), maxHeightU = 1f)
            }
            if (text != null) {
                FocusText(
                    text,
                    JiibType.caption,
                    t,
                    t.text2,
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    maxHeightU = 2f,
                )
            }
        }
    })
}
