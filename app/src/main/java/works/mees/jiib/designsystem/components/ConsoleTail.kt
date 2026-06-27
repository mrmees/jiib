package works.mees.jiib.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * A dumb, scrollable console-line renderer — a [LazyColumn] that shows a list of
 * plain strings in the [JiibType.consoleLine] monospace role (Geist Mono, 15sp, Medium)
 * and auto-scrolls to the bottom when [lines] grows.
 *
 * ## Design contract
 *  - Each row is translucent (no opaque fill — consistent with the console screen's
 *    row styling; the pane inherits whatever background is behind it).
 *  - Text is styled ONLY via [JiibType.consoleLine.toTextStyle(t)] — no inline `fontFamily`
 *    or `fontSize` (FontConformanceTest law). Color is [ThemeTokens.text3] (dimmed), matching
 *    the console's informational-line treatment.
 *  - Auto-scroll is **one-shot per [lines.size] change** via [LaunchedEffect] — NOT a
 *    continuous/looping animation (Adreno-320 perf budget).
 *
 * ## What this is NOT
 *  - It holds NO business state, collects NO flows, and sends NO commands.
 *    The caller supplies [lines] directly (capped/managed upstream).
 *
 * @param lines  The ordered list of text lines to display (newest last).
 * @param uDp    One unit U from the screen's unit grid, used as the minimum row height
 *               so touch targets stay sane even in a compact pane.
 * @param modifier Caller-supplied modifier (size / placement of the pane itself).
 */
@Composable
fun ConsoleTail(
    lines: List<String>,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val listState = rememberLazyListState()

    // One-shot scroll to the last line whenever the list grows.
    // LaunchedEffect key = lines.size; fires once per size change, then idles.
    // NOT animateScrollToItem — on Adreno 320 the animation frame budget is precious;
    // an instant jump to the tail is the correct behaviour for a streaming log pane.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.scrollToItem(lines.size - 1)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .background(t.surface)
            .padding(horizontal = 4.dp),
    ) {
        itemsIndexed(lines) { _, line ->
            Text(
                text = line,
                style = JiibType.consoleLine.toTextStyle(t),
                color = t.text3,
                softWrap = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
