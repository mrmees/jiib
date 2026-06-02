package works.mees.dinghy.ui.files

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import works.mees.dinghy.state.FileBrowserDirectory
import works.mees.dinghy.state.FileBrowserRow
import works.mees.dinghy.theme.compose.LocalTokens

@Composable
fun FileListView(
    directory: FileBrowserDirectory,
    selectedStableId: String?,
    httpBase: String,
    onRowClick: (FileBrowserRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val latestClick = rememberUpdatedState(onRowClick)
    val rows = remember(directory) {
        buildList {
            directory.upRow?.let(::add)
            addAll(directory.rows)
        }
    }
    val palette = FileRowPalette(
        background = t.surface.toArgb(),
        selectedBackground = t.surface2.toArgb(),
        outline = t.hair.toArgb(),
        selectedOutline = t.accentLine.toArgb(),
        text = t.text.toArgb(),
        textSecondary = t.text2.toArgb(),
        textMuted = t.text3.toArgb(),
    )
    val adapter = remember {
        FileRowsAdapter(FileThumbnailLoader.get(context))
    }
    adapter.onRowClick = { latestClick.value(it) }

    AndroidView(
        // Clip the embedded RecyclerView to its Compose bounds. AndroidView-hosted views draw in the
        // Android layer and are NOT clipped by sibling Compose layout, so rows scrolling past the top/
        // bottom edge would paint over the path chip and gutter without this.
        modifier = modifier.clipToBounds(),
        factory = { context ->
            RecyclerView(context).apply {
                // Pin to the AndroidView's measured bounds. Without explicit MATCH_PARENT params a
                // RecyclerView hosted in AndroidView can be measured UNSPECIFIED, grow to wrap ALL
                // rows, overlap the gutter, and never scroll. MATCH_PARENT forces the bounded height.
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                layoutManager = LinearLayoutManager(context)
                setHasFixedSize(true)
                clipToPadding = false
                itemAnimator = null
                this.adapter = adapter
            }
        },
        update = {
            adapter.submitRows(
                rows = rows,
                selectedStableId = selectedStableId,
                httpBase = httpBase,
                palette = palette,
            )
        },
    )
}
