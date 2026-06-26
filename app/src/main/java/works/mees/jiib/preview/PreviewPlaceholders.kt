package works.mees.jiib.preview

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.jiib.theme.compose.LocalTokens

/**
 * The D-05 "labeled stand-in, not a blank region" placeholder. The three classic-View interop hosts
 * ([works.mees.jiib.render.GraphViewHost] / `BedMeshHeatmapHost` / `WebcamViewHost`) wrap a View in
 * `AndroidView`, which renders as an EMPTY/broken region under `@Preview` (the View's `onDraw` never
 * runs in inspection mode). So under `LocalInspectionMode.current` each host short-circuits to this
 * box instead — a token-colored outline + centered label — so a screen that EMBEDS such a View still
 * previews cleanly (D-05 / RESEARCH Q5 flavor 2).
 *
 * ⚠ D-04: those classic Views get NO dedicated Compose preview of their own (they are not Compose) —
 * this placeholder is the documented limitation. Their real rendering is the on-device gate.
 *
 * Reads `LocalTokens.current` for its colors (the `MaterialSymbol` `tint = LocalTokens.current.text`
 * idiom) so the stand-in is themed by the SAME [PreviewBox] seed wrapping the screen — never a raw color.
 */
@Composable
fun PreviewPlaceholderBox(
    label: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Box(
        modifier = modifier
            .border(width = 2.dp, color = t.outline),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = t.text2,
            fontSize = 14.sp,
            modifier = Modifier.padding(8.dp),
        )
    }
}
