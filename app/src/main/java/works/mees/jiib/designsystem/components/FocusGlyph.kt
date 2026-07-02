package works.mees.jiib.designsystem.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIconView
import works.mees.jiib.theme.compose.LocalTokens

/**
 * LAW 3 — decorative/placeholder/diagram glyphs size as a FRACTION of the body's min
 * dimension, never a fixed sp. Rotation-safe by construction.
 */
fun focusGlyphSideDp(maxWidth: Dp, maxHeight: Dp, fraction: Float): Dp =
    minOf(maxWidth, maxHeight) * fraction

@Composable
fun FocusGlyph(
    icon: JiibIcon,
    modifier: Modifier = Modifier,
    fraction: Float = 0.5f,
    tint: Color? = null,
    contentDescription: String? = null,
) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        JiibIconView(
            icon = icon,
            sizeDp = focusGlyphSideDp(maxWidth, maxHeight, fraction),
            tint = tint ?: t.text3,
            contentDescription = contentDescription,
        )
    }
}
