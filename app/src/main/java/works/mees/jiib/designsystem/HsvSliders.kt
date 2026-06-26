package works.mees.jiib.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import works.mees.jiib.theme.DinghyType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * Three settle-not-stream sliders (Hue 0..360, Saturation 0..1, Value 0..1) reporting the FULL picked
 * color. Used by the swatch editor where the stored override is a literal ARGB. Track gradients render
 * the literal colors (data carve-out); labels/knob outline route through [LocalTokens]. No looping motion.
 */
@Composable
fun HsvSliders(
    hue: Float,
    sat: Float,
    value: Float,
    onMove: (Float, Float, Float) -> Unit,
    onSettle: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    white: Float? = null,
    onWhiteMove: (Float) -> Unit = {},
    onWhiteSettle: (Float) -> Unit = {},
    enabled: Boolean = true,
) {
    val pure = Color.hsv(((hue % 360f) + 360f) % 360f, 1f, 1f)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Track("H", hue / 360f,
            Brush.horizontalGradient((0..12).map { Color.hsv((it * 30f) % 360f, 1f, 1f) }),
            onMove = { onMove(it * 360f, sat, value) }, onSettle = { onSettle(it * 360f, sat, value) }, enabled = enabled)
        Track("S", sat,
            Brush.horizontalGradient(listOf(Color.hsv(((hue % 360f) + 360f) % 360f, 0f, value.coerceAtLeast(0.2f)), pure)),
            onMove = { onMove(hue, it, value) }, onSettle = { onSettle(hue, it, value) }, enabled = enabled)
        Track("V", value,
            Brush.horizontalGradient(listOf(Color.Black, pure)),
            onMove = { onMove(hue, sat, it) }, onSettle = { onSettle(hue, sat, it) }, enabled = enabled)
        if (white != null) {
            // RGBW: independent White channel (black→white). Renders only when supplied.
            Track("W", white,
                Brush.horizontalGradient(listOf(Color.Black, Color.White)),
                onMove = onWhiteMove, onSettle = onWhiteSettle, enabled = enabled)
        }
    }
}

@Composable
private fun Track(
    label: String,
    fraction: Float,
    brush: Brush,
    onMove: (Float) -> Unit,
    onSettle: (Float) -> Unit,
    enabled: Boolean = true,
) {
    val t = LocalTokens.current
    val move by rememberUpdatedState(onMove)
    val up by rememberUpdatedState(onSettle)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = DinghyType.caption.toTextStyle(t), color = t.text2,
            modifier = Modifier.width(18.dp).padding(end = 6.dp))
        Canvas(
            Modifier.fillMaxWidth().height(40.dp).pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    var f = (down.position.x / w).coerceIn(0f, 1f)
                    move(f); down.consume()
                    do {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { c ->
                            if (c.pressed && c.positionChanged()) {
                                f = (c.position.x / w).coerceIn(0f, 1f); move(f); c.consume()
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    up(f)
                }
            },
        ) {
            val th = size.height * 0.5f
            val top = (size.height - th) / 2f
            drawRoundRect(brush = brush, topLeft = Offset(0f, top), size = Size(size.width, th),
                cornerRadius = CornerRadius(th / 2f))
            val kx = fraction.coerceIn(0f, 1f) * size.width
            drawCircle(color = Color.White, radius = size.height * 0.40f, center = Offset(kx, size.height / 2f))
            drawCircle(color = t.outline, radius = size.height * 0.40f, center = Offset(kx, size.height / 2f),
                style = Stroke(width = 2.dp.toPx()))
        }
    }
}
