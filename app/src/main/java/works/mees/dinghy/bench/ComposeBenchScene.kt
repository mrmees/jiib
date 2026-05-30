package works.mees.dinghy.bench

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * SCENE A — Compose-everywhere worst-case benchmark scene (D-02/D-05).
 *
 * Renders the SAME printer-screen layout as [ViewsBenchScene] at the real 1920×1200
 * target, driven by the SAME deterministic [SyntheticFeed]:
 *   - a scrolling Files-style **LazyColumn** with Coil 3 **AsyncImage** doing REAL PNG
 *     decode + downsample under memory-cache pressure (D-07),
 *   - a live **Canvas** temperature graph,
 *   - a bounded **console** text spew.
 *
 * Compose perf practices (CLAUDE.md § The Big Decision): immutable/stable state
 * ([ComposeSceneState], [FeedEvent] are data classes), `collectAsStateWithLifecycle`,
 * stable `key`s on the lazy list, and reading rapidly-changing temps as LOW in the tree
 * as possible (the graph/console read the event; rows read only their own file).
 */

/** Immutable snapshot the Compose scene renders from — Compose can skip on equality. */
@Immutable
data class ComposeSceneState(
    val event: FeedEvent,
    val graphHistory: List<GraphSample>,
    val console: List<String>,
)

@Composable
fun ComposeBenchScene(
    state: MutableStateFlow<ComposeSceneState>,
    modifier: Modifier = Modifier,
) {
    val scene by state.collectAsStateWithLifecycle()

    Row(modifier = modifier.fillMaxSize().background(Color(0xFF101316))) {
        // Left: the high-churn Files list (real Coil decode).
        FilesList(
            files = scene.event.files,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )

        // Right: temp readouts + live Canvas graph + console spew.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(12.dp),
        ) {
            ReadoutsBar(scene.event)
            Spacer(Modifier.height(8.dp))
            TempGraph(
                history = scene.graphHistory,
                modifier = Modifier.fillMaxWidth().height(260.dp),
            )
            Spacer(Modifier.height(8.dp))
            ConsoleSpew(
                lines = scene.console,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
private fun FilesList(files: List<GcodeFile>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val loader = remember(context) { BenchImageLoader.get(context) }
    LazyColumn(modifier = modifier.padding(8.dp)) {
        items(files, key = { it.id }) { file ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ThumbModel(file.thumbSeed))
                        .build(),
                    imageLoader = loader,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = file.name,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${file.sizeKb} KB",
                        color = Color(0xFF8A98A6),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadoutsBar(event: FeedEvent) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("E %.1f / %.0f".format(event.extruderTemp, event.extruderTarget), color = Color(0xFFFF7043))
        Text("B %.1f / %.0f".format(event.bedTemp, event.bedTarget), color = Color(0xFF42A5F5))
        Text("Z %.3f".format(event.posZ), color = Color.White)
        Text("%.0f%%".format(event.progress * 100), color = Color(0xFF66BB6A))
    }
}

/** Live temperature graph drawn directly on a Compose [Canvas] (D-07). */
@Composable
private fun TempGraph(history: List<GraphSample>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color(0xFF181C20))) {
        if (history.size < 2) return@Canvas
        val maxTemp = 250f
        val n = history.size
        val dx = size.width / (n - 1).coerceAtLeast(1)

        fun draw(color: Color, pick: (GraphSample) -> Double) {
            var prev: Offset? = null
            for (i in 0 until n) {
                val v = pick(history[i]).toFloat().coerceIn(0f, maxTemp)
                val x = dx * i
                val y = size.height - (v / maxTemp) * size.height
                val cur = Offset(x, y)
                prev?.let { drawLine(color, it, cur, strokeWidth = 2f) }
                prev = cur
            }
        }
        draw(Color(0xFFFF7043)) { it.extruder }
        draw(Color(0xFF42A5F5)) { it.bed }
    }
}

@Composable
private fun ConsoleSpew(lines: List<String>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(Color(0xFF0B0E10)).padding(6.dp)) {
        // Render only the tail; bounded by the caller (CONSOLE_MAX).
        for ((i, line) in lines.withIndex()) {
            Text(
                text = line,
                color = Color(0xFF9CCC65),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxWidth(),
            )
            if (i == lines.lastIndex) Spacer(Modifier.height(0.dp))
        }
    }
}
