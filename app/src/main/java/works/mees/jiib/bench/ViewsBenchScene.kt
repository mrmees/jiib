package works.mees.jiib.bench

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.target

/**
 * SCENE B — classic / hybrid-Views worst-case benchmark scene (D-02/D-05).
 *
 * Renders the SAME printer-screen layout as [ComposeBenchScene] at the real 1920×1200
 * target, driven by the SAME deterministic [SyntheticFeed]:
 *   - a scrolling Files-style **RecyclerView** whose adapter decodes thumbnails through
 *     Coil's **ImageLoader** — the SAME synthetic PNG bytes, REAL decode + downsample
 *     under the SAME shared memory cache as Scene A (D-07, fairness D-02),
 *   - a live **custom View** temperature graph,
 *   - a bounded **TextView** console spew.
 *
 * Exposes [render] so [BenchActivity] can push each [FeedEvent] from the shared feed and
 * the Views scene mutates exactly like the Compose scene recomposes.
 */
class ViewsBenchScene(context: Context) : FrameLayout(context) {

    private val filesAdapter = FilesAdapter()
    private val graph = TempGraphView(context)
    private val readouts: TextView
    private val console: TextView

    init {
        setBackgroundColor(Color.parseColor("#FF101316"))

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // Left: high-churn Files list (real Coil decode through ImageLoader).
        val recycler = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = filesAdapter
            setPadding(dp(8), dp(8), dp(8), dp(8))
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        root.addView(recycler)

        // Right column: readouts + graph + console.
        val right = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
        }
        readouts = TextView(context).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        }
        right.addView(readouts)
        right.addView(
            graph,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, dp(260)).apply {
                topMargin = dp(8)
            },
        )
        console = TextView(context).apply {
            setTextColor(Color.parseColor("#FF9CCC65"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setBackgroundColor(Color.parseColor("#FF0B0E10"))
            gravity = Gravity.BOTTOM
        }
        right.addView(
            console,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(8)
            },
        )
        root.addView(right)

        addView(root)
    }

    /** Push one shared-feed event — mutate the list, graph, readouts, console. */
    fun render(event: FeedEvent, graphHistory: List<GraphSample>, consoleLines: List<String>) {
        filesAdapter.submit(event.files)
        graph.setHistory(graphHistory)
        readouts.text = "E %.1f/%.0f   B %.1f/%.0f   Z %.3f   %.0f%%".format(
            event.extruderTemp, event.extruderTarget,
            event.bedTemp, event.bedTarget,
            event.posZ, event.progress * 100,
        )
        console.text = consoleLines.joinToString("\n")
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ---- RecyclerView adapter: REAL Coil decode via ImageLoader.enqueue ----

    private class FileRow(context: Context) : LinearLayout(context) {
        val thumb: ImageView
        val name: TextView
        val size: TextView

        init {
            orientation = HORIZONTAL
            val pad = (4 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            val t = (72 * resources.displayMetrics.density).toInt()
            thumb = ImageView(context).apply {
                layoutParams = LayoutParams(t, t)
            }
            addView(thumb)
            val col = LinearLayout(context).apply {
                orientation = VERTICAL
                val m = (10 * resources.displayMetrics.density).toInt()
                setPadding(m, 0, 0, 0)
            }
            name = TextView(context).apply {
                setTextColor(Color.WHITE)
                maxLines = 1
            }
            size = TextView(context).apply {
                setTextColor(Color.parseColor("#FF8A98A6"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            col.addView(name)
            col.addView(size)
            addView(col)
        }
    }

    private class VH(val row: FileRow) : RecyclerView.ViewHolder(row)

    private class FilesAdapter : RecyclerView.Adapter<VH>() {
        private var files: List<GcodeFile> = emptyList()

        fun submit(next: List<GcodeFile>) {
            files = next
            notifyDataSetChanged() // worst-case: full rebind churn (intentional stress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = FileRow(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            return VH(row)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val file = files[position]
            holder.row.name.text = file.name
            holder.row.size.text = "${file.sizeKb} KB"
            val ctx = holder.row.context
            // Same synthetic PNG bytes + same shared loader as the Compose scene.
            val request = ImageRequest.Builder(ctx)
                .data(ThumbModel(file.thumbSeed))
                .target { img -> holder.row.thumb.setImageDrawable(img.asDrawable(ctx.resources)) }
                .build()
            BenchImageLoader.get(ctx).enqueue(request)
        }

        override fun getItemCount(): Int = files.size
    }

    // ---- Custom View temperature graph (mirrors the Compose Canvas graph) ----

    private class TempGraphView(context: Context) : View(context) {
        private var history: List<GraphSample> = emptyList()
        private val extruderPaint = Paint().apply {
            color = Color.parseColor("#FFFF7043"); strokeWidth = 2f; isAntiAlias = true
        }
        private val bedPaint = Paint().apply {
            color = Color.parseColor("#FF42A5F5"); strokeWidth = 2f; isAntiAlias = true
        }
        private val bg = Paint().apply { color = Color.parseColor("#FF181C20") }

        fun setHistory(next: List<GraphSample>) {
            history = next
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)
            val n = history.size
            if (n < 2) return
            val maxTemp = 250f
            val dx = width.toFloat() / (n - 1)

            fun line(paint: Paint, pick: (GraphSample) -> Double) {
                var px = 0f
                var py = -1f
                for (i in 0 until n) {
                    val v = pick(history[i]).toFloat().coerceIn(0f, maxTemp)
                    val x = dx * i
                    val y = height - (v / maxTemp) * height
                    if (py >= 0f) canvas.drawLine(px, py, x, y, paint)
                    px = x; py = y
                }
            }
            line(extruderPaint) { it.extruder }
            line(bedPaint) { it.bed }
        }
    }
}
