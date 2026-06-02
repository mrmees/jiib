package works.mees.dinghy.ui.files

import android.graphics.Typeface
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
import works.mees.dinghy.state.FileBrowserRow
import works.mees.dinghy.state.FileBrowserRowKind
import works.mees.dinghy.state.thumbnailUrl

data class FileRowPalette(
    val background: Int,
    val selectedBackground: Int,
    val outline: Int,
    val selectedOutline: Int,
    val text: Int,
    val textSecondary: Int,
    val textMuted: Int,
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
        thumbLabel.typeface = Typeface.DEFAULT_BOLD
        thumbLabel.textSize = 11f

        val textColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), 0, dp(8), 0)
            layoutParams = LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        title.maxLines = 1
        title.ellipsize = android.text.TextUtils.TruncateAt.END
        title.typeface = Typeface.DEFAULT_BOLD
        title.textSize = 17f
        meta.maxLines = 1
        meta.ellipsize = android.text.TextUtils.TruncateAt.END
        meta.textSize = 13f
        textColumn.addView(title)
        textColumn.addView(meta)

        selectedMark.gravity = Gravity.CENTER
        selectedMark.text = "SEL"
        selectedMark.textSize = 12f
        selectedMark.typeface = Typeface.DEFAULT_BOLD
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

private fun formatDate(epochSeconds: Double): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date((epochSeconds * 1000).toLong()))
