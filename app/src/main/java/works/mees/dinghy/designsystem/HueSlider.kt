package works.mees.dinghy.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.dp
import works.mees.dinghy.theme.compose.LocalTokens

/**
 * Horizontal hue slider (0..360). The single seed axis (the generator re-derives L/C). Settle-not-stream:
 * [onHandleMove] fires cheaply per drag-frame; [onSettle] fires once on pointer-UP. Track renders the
 * literal hue gradient (data carve-out); the knob outline routes through [LocalTokens]. No looping motion.
 */
@Composable
fun HueSlider(
    hue: Float,
    onHandleMove: (Float) -> Unit,
    onSettle: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val brush = remember {
        Brush.horizontalGradient((0..12).map { i -> Color.hsv((i * 30f) % 360f, 1f, 1f) })
    }
    val onMove by rememberUpdatedState(onHandleMove)
    val onUp by rememberUpdatedState(onSettle)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(44.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = size.width.toFloat()
                    var h = (down.position.x / w).coerceIn(0f, 1f) * 360f
                    onMove(h); down.consume()
                    do {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { c ->
                            if (c.pressed && c.positionChanged()) {
                                h = (c.position.x / w).coerceIn(0f, 1f) * 360f
                                onMove(h); c.consume()
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    onUp(h)
                }
            },
    ) {
        val trackH = size.height * 0.5f
        val top = (size.height - trackH) / 2f
        drawRoundRect(brush = brush, topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(size.width, trackH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2f))
        val kx = (hue % 360f) / 360f * size.width
        drawCircle(color = Color.hsv(hue % 360f, 1f, 1f), radius = size.height * 0.42f, center = Offset(kx, size.height / 2f))
        drawCircle(color = t.outline, radius = size.height * 0.42f, center = Offset(kx, size.height / 2f),
            style = Stroke(width = 2.dp.toPx()))
    }
}
