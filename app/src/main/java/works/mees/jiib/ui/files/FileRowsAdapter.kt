package works.mees.jiib.ui.files

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.Image
import coil3.ImageLoader
import coil3.asDrawable
import coil3.target.Target
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import works.mees.jiib.state.FileBrowserRow
import works.mees.jiib.state.FileBrowserRowKind
import works.mees.jiib.state.thumbnailUrl
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.fsSp
import works.mees.jiib.theme.views.typeface

data class FileRowPalette(
    val background: Int,
    val selectedBackground: Int,
    val outline: Int,
    val selectedOutline: Int,
    val text: Int,
    val textSecondary: Int,
    val textMuted: Int,
    /**
     * The active S/M/L text-size multiplier (`ThemeTokens.fs`), bridged down so the View-side row text
     * honors `--fs` exactly like every Compose surface. `FileRowsAdapter` is a classic RecyclerView
     * adapter that cannot read `LocalTokens`, so `FileListView` reads `LocalTokens.current.fs` and packs
     * it here (mirrors `ConsoleRowPalette.fs`).
     */
    val fs: Float,
)

class FileRowsAdapter(
    private val imageLoader: ImageLoader,
) : ListAdapter<FileRowsAdapter.RowItem, FileRowsAdapter.Holder>(Diff) {
    var onRowClick: (FileBrowserRow) -> Unit = {}

    init {
        setHasStableIds(true)
    }

    fun submitRows(
        rows: List<FileBrowserRow>,
        selectedStableId: String?,
        httpBase: String,
        palette: FileRowPalette,
    ) {
        submitList(rows.map { RowItem(it, selected = it.stableId == selectedStableId, httpBase = httpBase, palette = palette) })
    }

    override fun getItemId(position: Int): Long =
        getItem(position).row.stableId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(FileRowView(parent.context))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.view.bind(getItem(position), imageLoader, onRowClick)
    }

    override fun onViewRecycled(holder: Holder) {
        holder.view.clearThumbnail()
    }

    class Holder(val view: FileRowView) : RecyclerView.ViewHolder(view)

    data class RowItem(
        val row: FileBrowserRow,
        val selected: Boolean,
        val httpBase: String,
        val palette: FileRowPalette,
    )

    private object Diff : DiffUtil.ItemCallback<RowItem>() {
        override fun areItemsTheSame(oldItem: RowItem, newItem: RowItem): Boolean =
            oldItem.row.stableId == newItem.row.stableId

        override fun areContentsTheSame(oldItem: RowItem, newItem: RowItem): Boolean =
            oldItem == newItem
    }
}

class FileRowView(context: android.content.Context) : LinearLayout(context) {
    private val thumbFrame = FrameLayout(context)
    private val thumbImage = ImageView(context)
    private val thumbLabel = TextView(context)
    private val title = TextView(context)
    private val meta = TextView(context)
    private val selectedMark = TextView(context)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(72)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )

        thumbFrame.layoutParams = LayoutParams(dp(48), dp(48))
        thumbFrame.addView(
            thumbImage,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        thumbFrame.addView(
            thumbLabel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        thumbImage.scaleType = ImageView.ScaleType.CENTER_CROP
        thumbLabel.gravity = Gravity.CENTER
        // dataMeta role (Data Mono 15) — single source of truth; --fs-scaled per-bind from the palette.
        thumbLabel.typeface = JiibType.dataMeta.typeface(context)

        val textColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        // The FILENAME is printer data → dataInline role (Data Mono 20). Was Typeface.DEFAULT_BOLD/17sp.
        title.typeface = JiibType.dataInline.typeface(context)
        meta.maxLines = 1
        meta.ellipsize = android.text.TextUtils.TruncateAt.END
        // Size/date metadata → dataMeta role (Data 15).
        meta.typeface = JiibType.dataMeta.typeface(context)
        textColumn.addView(title)
        textColumn.addView(meta)

        selectedMark.gravity = Gravity.CENTER
        selectedMark.text = "SEL"
        // caption role (Ui 15).
        selectedMark.typeface = JiibType.caption.typeface(context)
        selectedMark.layoutParams = LayoutParams(dp(32), dp(48))

        addView(thumbFrame)
        addView(textColumn)
        addView(selectedMark)
    }

    fun bind(
        item: FileRowsAdapter.RowItem,
        imageLoader: ImageLoader,
        onClick: (FileBrowserRow) -> Unit,
    ) {
        val row = item.row
        val palette = item.palette
        background = roundedRect(
            fill = if (item.selected) palette.selectedBackground else palette.background,
            stroke = if (item.selected) palette.selectedOutline else palette.outline,
        )
        // Apply each role's base size scaled by the active --fs (the palette carries it; the adapter
        // cannot read LocalTokens — mirrors ConsoleRowPalette.fs). Family/weight are fixed in init.
        val fs = palette.fs
        title.textSize = fsSp(JiibType.dataInline.baseSp, fs)
        meta.textSize = fsSp(JiibType.dataMeta.baseSp, fs)
        thumbLabel.textSize = fsSp(JiibType.dataMeta.baseSp, fs)
        selectedMark.textSize = fsSp(JiibType.caption.baseSp, fs)

        title.text = row.name
        title.setTextColor(palette.text)
        meta.text = row.metaText()
        meta.setTextColor(palette.textSecondary)
        selectedMark.visibility = if (item.selected) View.VISIBLE else View.INVISIBLE
        selectedMark.setTextColor(palette.selectedOutline)
        thumbLabel.setTextColor(palette.textMuted)
        thumbFrame.background = roundedRect(fill = palette.background, stroke = palette.outline)

        val thumbUrl = row.thumbnailUrlOrNull(item.httpBase)
        if (thumbUrl == null) {
            showPlaceholder(row.placeholderLabel())
        } else {
            loadThumbnail(thumbUrl, imageLoader)
        }

        setOnClickListener { onClick(row) }
    }

    fun clearThumbnail() {
        thumbImage.tag = null
        thumbImage.setImageDrawable(null)
    }

    private fun loadThumbnail(url: String, imageLoader: ImageLoader) {
        thumbImage.tag = url
        thumbImage.visibility = View.VISIBLE
        thumbLabel.visibility = View.GONE
        val request = FileThumbnailLoader.rowRequest(
            context = context,
            url = url,
            target = object : Target {
                override fun onSuccess(result: Image) {
                    if (thumbImage.tag == url) {
                        thumbImage.setImageDrawable(result.asDrawable(resources))
                    }
                }

                override fun onError(error: Image?) {
                    if (thumbImage.tag == url) {
                        showPlaceholder("GCO")
                    }
                }
            },
        )
        imageLoader.enqueue(request)
    }

    private fun showPlaceholder(label: String) {
        thumbImage.tag = null
        thumbImage.setImageDrawable(null)
        thumbImage.visibility = View.GONE
        thumbLabel.visibility = View.VISIBLE
        thumbLabel.text = label
    }

    private fun FileBrowserRow.thumbnailUrlOrNull(httpBase: String): String? {
        val relative = relativeFilename ?: return null
        val thumb = thumbnailRelPath ?: return null
        if (httpBase.isBlank()) return null
        return thumbnailUrl(httpBase, relative, thumb)
    }

    private fun FileBrowserRow.placeholderLabel(): String = when (kind) {
        FileBrowserRowKind.Up -> "UP"
        FileBrowserRowKind.Directory -> "DIR"
        FileBrowserRowKind.File -> "GCO"
    }

    private fun FileBrowserRow.metaText(): String = when (kind) {
        FileBrowserRowKind.Up -> "Parent folder"
        FileBrowserRowKind.Directory -> "Folder"
        FileBrowserRowKind.File -> listOfNotNull(
            sizeBytes?.let(::formatBytes),
            modifiedEpochSeconds?.let(::formatDate),
        ).joinToString("  ").ifBlank { "Gcode file" }
    }

    private fun roundedRect(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(8).toFloat()
            setColor(fill)
            setStroke(dp(2), stroke)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}

/**
 * File modification date formatter — allocated ONCE and reused for all row binds (D-03 P3).
 * `SimpleDateFormat` is NOT thread-safe, but file-row binding runs exclusively on the main thread
 * (RecyclerView binds on the UI thread) so a single shared instance is correct here. Do NOT move
 * binding off the main thread without replacing this with a thread-local or `DateTimeFormatter`.
 */
private val FILE_DATE_FORMAT = SimpleDateFormat("MMM d, HH:mm", Locale.US)

private fun formatDate(epochSeconds: Double): String =
    FILE_DATE_FORMAT.format(Date((epochSeconds * 1000).toLong()))
