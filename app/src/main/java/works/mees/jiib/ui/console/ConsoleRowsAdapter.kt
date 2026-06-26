package works.mees.jiib.ui.console

import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import works.mees.jiib.R
import works.mees.jiib.theme.DinghyType
import works.mees.jiib.theme.fsSp
import works.mees.jiib.theme.views.typeface

/**
 * The View-side severity color palette for console rows. The Composable resolves role tokens
 * (`t.stop` / `t.heat` / `t.text` / `t.go` / `t.text3` / `t.surface`…) to packed ARGB ints and hands
 * them down — the RecyclerView/ViewHolder cannot read `LocalTokens` (THEME-01 bridge, mirrors
 * `FileRowPalette`). Severity → color:
 *  - [error]   `!! ` lines    → `t.stop` (red)
 *  - [warning] `// ` echoes    → `t.heat` (amber)
 *  - [normal]  plain lines     → `t.text`
 *  - [success] explicit `ok`   → `t.go` (green)
 *  - [dimmed]  `// action:` / `// debug:` tiers → `t.text3` (faint)
 */
data class ConsoleRowPalette(
    val background: Int,
    val error: Int,
    val warning: Int,
    val normal: Int,
    val success: Int,
    val dimmed: Int,
    /**
     * The active S/M/L text-size multiplier (`ThemeTokens.fs`), bridged down so the View-side row text
     * and its safety glyph honor `--fs` exactly like every Compose surface (WR-04 fix — the row was
     * previously pinned at 14sp, below the 15sp floor and blind to the S/M/L setting).
     */
    val fs: Float,
)

/**
 * RecyclerView adapter for the console scrollback (CONS-02 / D-05). Mirrors `FileRowsAdapter`'s
 * `RecyclerView.Adapter` + token-palette discipline, but the UPDATE strategy is split for the
 * Adreno-320 floor (S2):
 *
 *  - [appendLine] — the per-line LIVE hot path. Maintains a mutable backing list; `add()`s one line
 *    and calls `notifyItemInserted(size-1)` — NEVER a whole-list DiffUtil diff. When the list exceeds
 *    [scrollbackCap] it `removeAt(0)`s the oldest and calls `notifyItemRangeRemoved(0, evicted)`
 *    (incremental). This is the only path that runs while lines stream in.
 *  - [submitRows] — the REPLACE path, reserved ONLY for the backfill REPLACE on (re)connect and the
 *    filter-toggle re-render (the two cases where the whole list genuinely changes). Recomputes the
 *    backing list wholesale and `notifyDataSetChanged()`s. Not on the per-line hot path.
 *
 * STICK-TO-BOTTOM (S3): the caller ([ConsoleListView]) captures `wasAtBottom` from the OLD item count
 * BEFORE invoking either path, then scrolls in the callback only if it was at the bottom — the
 * post-update `itemCount` is never used for the bottom test (that is the S3 bug).
 */
class ConsoleRowsAdapter(
    private val scrollbackCap: Int = works.mees.jiib.state.ConsoleScrollback.DEFAULT_CAPACITY,
) : RecyclerView.Adapter<ConsoleRowsAdapter.Holder>() {

    private val items = ArrayList<ConsoleLine>()
    private var palette: ConsoleRowPalette? = null

    /** Current row count (read by the View BEFORE an update to capture stick-to-bottom — S3). */
    override fun getItemCount(): Int = items.size

    /** Update the active palette (theme swap / first bind). Repaints existing rows. */
    fun setPalette(palette: ConsoleRowPalette) {
        if (this.palette == palette) return
        this.palette = palette
        if (items.isNotEmpty()) notifyItemRangeChanged(0, items.size)
    }

    /**
     * LIVE-APPEND hot path (S2): append exactly one [line] with an incremental
     * [notifyItemInserted], evicting the oldest with [notifyItemRangeRemoved] when over [scrollbackCap].
     * NO DiffUtil. Returns the inserted position so the View can scroll-to it when `wasAtBottom`.
     */
    fun appendLine(line: ConsoleLine): Int {
        items.add(line)
        val inserted = items.size - 1
        notifyItemInserted(inserted)
        if (items.size > scrollbackCap) {
            val evicted = items.size - scrollbackCap
            repeat(evicted) { items.removeAt(0) }
            notifyItemRangeRemoved(0, evicted)
            return items.size - 1
        }
        return inserted
    }

    /**
     * REPLACE path: wholesale-replace the backing list (backfill REPLACE / filter-toggle re-render
     * ONLY). Not on the per-line hot path — a full refresh here is correct because the whole list
     * genuinely changed.
     */
    fun submitRows(rows: List<ConsoleLine>) {
        items.clear()
        items.addAll(rows)
        notifyDataSetChanged()
    }

    /** True when the adapter's backing list is exactly [other]'s items in order (identity-cheap check). */
    fun matches(other: List<ConsoleLine>): Boolean = items == other

    /**
     * STEADY-STATE incremental detection (WR-02): true when [incoming] is this adapter's contents shifted
     * left by exactly one element with a new trailing line appended — i.e. the holder evicted its oldest
     * line (the ring is full) and appended one new line. Once both the ring and the adapter sit at
     * [scrollbackCap], `incoming.size == itemCount` forever, so the size==old+1 single-append test never
     * fires and a busy console would otherwise full-reset on EVERY line — the worst case for the
     * Adreno-320 floor. Detecting this case lets the View drive [appendLine] (incremental
     * notifyItemInserted + notifyItemRangeRemoved) instead of [submitRows]'s notifyDataSetChanged.
     *
     * Cheap: only valid when both lists are full ([incoming].size == [itemCount] == [scrollbackCap]); the
     * comparison walks the overlapping window once (`incoming[0..n-2] == items[1..n-1]`).
     */
    fun isAppendEvict(incoming: List<ConsoleLine>): Boolean {
        val n = items.size
        if (n < scrollbackCap || incoming.size != n) return false
        // items[1..n-1] must equal incoming[0..n-2] — i.e. everything but the evicted head/new tail.
        for (i in 1 until n) {
            if (items[i] != incoming[i - 1]) return false
        }
        return true
    }

    /** The current last row, or null. Used by the View to detect a pure single-line append. */
    fun lastOrNull(): ConsoleLine? = items.lastOrNull()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ConsoleRowView(parent.context))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.view.bind(items[position], palette)
    }

    class Holder(val view: ConsoleRowView) : RecyclerView.ViewHolder(view)
}

/**
 * One console line: the raw response text in **Geist Mono** (UI-SPEC mandatory tabular), left-aligned,
 * full cell width (panel-text-fills-the-box). The `!! `/`// ` prefix is STRIPPED for display but
 * severity was derived from the ORIGINAL prefix upstream (kept on [ConsoleLine.severity]); the model's
 * `rawMessage` stays intact. ACTION/DEBUG tiers render dimmed.
 */
class ConsoleRowView(context: android.content.Context) : TextView(context) {
    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(4), dp(12), dp(4))
        // Geist Mono (UI-SPEC mandatory tabular for console lines) resolved from the DinghyType.consoleLine
        // role — single source of truth shared with the Compose toolkit; falls back to platform monospace.
        typeface = DinghyType.consoleLine.typeface(context)
        // consoleLine base (15sp) as the unbound fallback; bind() rescales to the active --fs (WR-04).
        textSize = DinghyType.consoleLine.baseSp
        setTextIsSelectable(false)
    }

    fun bind(line: ConsoleLine, palette: ConsoleRowPalette?) {
        text = displayText(line.rawMessage)
        val p = palette
        if (p != null) {
            // Honor the active S/M/L --fs (WR-04): consoleLine base * fs, matching fsSp() on Compose surfaces.
            // The glyph below is sized off this scaled textSize, so it tracks --fs too.
            textSize = fsSp(DinghyType.consoleLine.baseSp, p.fs)
            setBackgroundColor(p.background)
            setTextColor(
                when (line.severity) {
                    ConsoleSeverity.ERROR -> p.error
                    ConsoleSeverity.WARNING -> p.warning
                    ConsoleSeverity.NORMAL -> if (isOkLine(line.rawMessage)) p.success else p.normal
                    ConsoleSeverity.ACTION -> p.dimmed
                    ConsoleSeverity.DEBUG -> p.dimmed
                },
            )
        }
        // D-01/D-02 shape-coded safety layer: ERROR → octagon (tinted stop/red), WARNING → triangle
        // (tinted caution/amber); ALL other severities → no glyph. The shape silhouette is the
        // redundant non-color signal so the tier reads in grayscale/CVD (the safety mechanism).
        applySeverityGlyph(line.severity, p)
    }

    /**
     * Attach the leading shape glyph for ERROR/WARNING (clear it otherwise so recycled rows don't keep
     * a stale glyph). CRITICAL (Item 6 — the row-height fix): the plan-01 vectors carry a 96dp intrinsic
     * size, so the glyph is given EXPLICIT [fsSp]-scaled pixel bounds via [android.graphics.drawable.Drawable.setBounds]
     * and attached with [setCompoundDrawables] (the variant that RESPECTS the bounds we set) — NEVER
     * `…WithIntrinsicBounds`, which would inject a 96dp glyph and blow up the row height. The tint comes
     * from the resolved [ConsoleRowPalette] ARGB ints (never a raw status hex — THEME-01).
     */
    private fun applySeverityGlyph(severity: ConsoleSeverity, palette: ConsoleRowPalette?) {
        val resId: Int
        val tint: Int
        when (severity) {
            ConsoleSeverity.ERROR -> {
                resId = R.drawable.disabled_by_default
                tint = palette?.error ?: currentTextColor
            }
            ConsoleSeverity.WARNING -> {
                resId = R.drawable.warning
                tint = palette?.warning ?: currentTextColor
            }
            else -> {
                // No glyph for NORMAL/ACTION/DEBUG/success — clear any stale compound drawable.
                setCompoundDrawables(null, null, null, null)
                return
            }
        }
        val d = AppCompatResources.getDrawable(context, resId)!!.mutate()
        DrawableCompat.setTint(d, tint)
        // Size the glyph to the fsSp-scaled row text size (square box), dp→px via display density. This
        // tracks the S/M/L --fs setting that scales the row textSize the same way.
        val px = (textSize).toInt().coerceAtLeast(1)
        d.setBounds(0, 0, px, px)
        compoundDrawablePadding = dp(6)
        setCompoundDrawables(d, null, null, null)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        /** Strip the leading Klipper severity prefix for display (severity already derived upstream). */
        private fun displayText(raw: String): String = when {
            raw.startsWith("!! ") -> raw.removePrefix("!! ")
            raw.startsWith("// action:") -> raw.removePrefix("// ")
            raw.startsWith("// debug:") -> raw.removePrefix("// ")
            raw.startsWith("// ") -> raw.removePrefix("// ")
            else -> raw
        }

        /** A plain `ok`-style success line gets the positive (green) tier. */
        private fun isOkLine(raw: String): Boolean =
            raw == "ok" || raw.startsWith("ok ")
    }
}
