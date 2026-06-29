package works.mees.jiib.ui.console

import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.views.typeface
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import works.mees.jiib.state.ConsoleScrollback
import works.mees.jiib.theme.compose.LocalTokens

/**
 * The console scrollback — the 3rd Views-in-Compose scroll surface (CONS-02 / D-05). It reuses the
 * `ui/files/FileListView.kt` pattern VERBATIM for the three load-bearing bits:
 *  - `Modifier.clipToBounds()` on the `AndroidView` (the hosted RecyclerView draws in the Android
 *    layer and is NOT clipped by sibling Compose layout — rows past the edge would paint over the
 *    gutter otherwise);
 *  - `ViewGroup.LayoutParams.MATCH_PARENT` (without it the RecyclerView measures UNSPECIFIED, wraps
 *    every row, overlaps the gutter, and never scrolls);
 *  - `itemAnimator = null` (Adreno-320 — NO animated insertion).
 *
 * Severity colors are bridged from role tokens to packed ARGB ints into a [ConsoleRowPalette] (Views
 * can't read `LocalTokens`).
 *
 * UPDATE strategy (S2 / S3) lives in [update]: it diffs the incoming [lines] against the adapter's
 * current contents to pick the LIVE-APPEND incremental path (one new trailing line →
 * `notifyItemInserted`) versus the REPLACE path (backfill / filter-toggle → `submitRows`), and it
 * captures `wasAtBottom` from the OLD item count BEFORE mutating the adapter, then scrolls to the new
 * last row only if it was at the bottom.
 *
 * @param lines the FILTERED lines to display (the screen applies [ConsoleFilters] before this — D-04).
 */
@Composable
fun ConsoleListView(
    lines: List<ConsoleLine>,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val palette = ConsoleRowPalette(
        background = t.surface.toArgb(),
        error = t.stop.toArgb(),
        warning = t.heat.toArgb(),
        normal = t.text.toArgb(),
        success = t.go.toArgb(),
        dimmed = t.text3.toArgb(),
        fs = t.fs,
        typeface = JiibType.consoleLine.typeface(LocalContext.current, t),
    )
    val adapter = remember { ConsoleRowsAdapter(scrollbackCap = ConsoleScrollback.DEFAULT_CAPACITY) }
    adapter.setPalette(palette)

    AndroidView(
        // Clip the embedded RecyclerView to its Compose bounds (see FileListView — same reasoning).
        modifier = modifier.clipToBounds(),
        factory = { context ->
            RecyclerView(context).apply {
                // Pin to the AndroidView's measured bounds; MATCH_PARENT forces the bounded height.
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
                setHasFixedSize(false)
                clipToPadding = false
                itemAnimator = null
                this.adapter = adapter
            }
        },
        update = { recycler ->
            val lm = recycler.layoutManager as LinearLayoutManager
            // S3 — capture stick-to-bottom from the OLD item count BEFORE the adapter mutates. The
            // post-update itemCount would be stale-wrong here (that is the S3 bug we must NOT write).
            val oldCount = adapter.itemCount
            val wasAtBottom = oldCount == 0 || lm.findLastVisibleItemPosition() >= oldCount - 1

            // Decide append vs replace WITHOUT a per-line DiffUtil (S2). A pure single-line live append
            // is "the adapter already holds the incoming list minus its last element" — detect that
            // cheaply and take the incremental notifyItemInserted path; otherwise the whole list
            // genuinely changed (backfill REPLACE or a filter-toggle), so take the submitRows path.
            val isSingleAppend = lines.size == oldCount + 1 &&
                adapter.matches(lines.subList(0, oldCount))

            // STEADY-STATE append (WR-02): once the ring AND adapter are both at cap, a new live line is
            // an append+evict (lines.size == oldCount, contents shifted left by one). Without this it
            // would fall to the full-reset branch on EVERY line — the Adreno-320 worst case. appendLine
            // already does the incremental notifyItemInserted + notifyItemRangeRemoved.
            val isAppendEvict = !isSingleAppend && adapter.isAppendEvict(lines)

            if (isSingleAppend || isAppendEvict) {
                val pos = adapter.appendLine(lines.last())
                if (wasAtBottom) recycler.scrollToPosition(pos)
            } else if (!adapter.matches(lines)) {
                adapter.submitRows(lines)
                if (wasAtBottom && lines.isNotEmpty()) {
                    // Scroll AFTER the layout settles (commit callback): post to the RecyclerView so
                    // the new last position exists before we scroll to it.
                    recycler.post { recycler.scrollToPosition(lines.size - 1) }
                }
            }
        },
    )
}
