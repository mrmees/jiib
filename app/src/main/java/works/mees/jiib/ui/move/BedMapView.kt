package works.mees.jiib.ui.move

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import works.mees.jiib.theme.compose.LocalTokens

/**
 * To-scale, no-stretch overhead bed map. [current] and [target] are bed-mm coordinates (or null).
 * Tap or drag report bed-mm via [onTapBed]/[onDragBed]; [onDragEnd] fires once on release. When
 * [travel] is true, an accent line is drawn current→target (the in-flight move indicator).
 *
 * Colors are fully token-routed (THEME-01): bed fill = [works.mees.jiib.theme.ThemeTokens.surface3],
 * border = [works.mees.jiib.theme.ThemeTokens.accent], markers/line = [works.mees.jiib.theme.ThemeTokens.accent].
 * No raw [androidx.compose.ui.graphics.Color] literals.
 */
@Composable
fun BedMapView(
    bed: BedExtent,
    current: Pair<Double, Double>?,
    target: Pair<Double, Double>?,
    travel: Boolean,
    modifier: Modifier = Modifier,
    onTapBed: ((Double, Double) -> Unit)? = null,
    onDragBed: ((Double, Double) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null,
) {
    val t = LocalTokens.current
    var rect by remember { mutableStateOf(BedRect(0f, 0f, 0f, 0f)) }

    Canvas(
        modifier
            .fillMaxSize()
            .pointerInput(bed) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (rect.width <= 0f || rect.height <= 0f) return@awaitEachGesture   // not laid out yet
                    val (bx, by) = screenToBed(bed, rect, down.position.x, down.position.y)
                    onTapBed?.invoke(bx, by)
                    onDragBed?.invoke(bx, by)
                    down.consume()
                    do {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { c ->
                            if (c.pressed) {
                                val (dx, dy) = screenToBed(bed, rect, c.position.x, c.position.y)
                                onDragBed?.invoke(dx, dy)
                                c.consume()
                            }
                        }
                    } while (ev.changes.any { it.pressed })
                    onDragEnd?.invoke()
                }
            },
    ) {
        // Guard degenerate bed (avoids NaN or div-by-zero in bedFitRect).
        if (bed.width <= 0.0 || bed.height <= 0.0) return@Canvas
        rect = bedFitRect(bed, size.width, size.height)

        // Bed plate fill + border.
        drawRect(
            color = t.surface3,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
        )
        drawRect(
            color = t.accent,
            topLeft = Offset(rect.left, rect.top),
            size = Size(rect.width, rect.height),
            style = Stroke(width = 2.dp.toPx()),
        )

        val cur = current?.let { bedToScreen(bed, rect, it.first, it.second) }
        val tgt = target?.let { bedToScreen(bed, rect, it.first, it.second) }

        // Accent travel line current→target (drawn under markers).
        if (travel && cur != null && tgt != null) {
            drawLine(
                color = t.accent,
                start = Offset(cur.x, cur.y),
                end = Offset(tgt.x, tgt.y),
                strokeWidth = 3.dp.toPx(),
            )
        }

        // Target marker — hollow ring.
        if (tgt != null) {
            drawCircle(
                color = t.accent,
                radius = 9.dp.toPx(),
                center = Offset(tgt.x, tgt.y),
                style = Stroke(width = 3.dp.toPx()),
            )
        }

        // Current-position marker — filled dot (drawn on top).
        if (cur != null) {
            drawCircle(
                color = t.accent,
                radius = 6.dp.toPx(),
                center = Offset(cur.x, cur.y),
            )
        }
    }
}
