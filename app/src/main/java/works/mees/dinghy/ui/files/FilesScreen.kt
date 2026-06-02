package works.mees.dinghy.ui.files

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.state.FileBrowserRow
import works.mees.dinghy.state.FileBrowserRowKind
import works.mees.dinghy.state.FilePreviewMetadata
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.thumbnailUrl
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

@Composable
fun FilesScreen(
    holder: FileBrowserHolder,
    printerState: PrinterState,
    httpBase: String,
    canStartPrint: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by holder.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var guard by remember { mutableStateOf<FileGuard?>(null) }
    val selected = state.selectedFile
    val printingActive = printerState.printState == PrintState.Printing || printerState.printState == PrintState.Paused
    val startEnabled = selected != null && canStartPrint && state.pendingAction == null

    LaunchedEffect(holder) {
        holder.loadRoot()
    }

    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val showFocus = maxWidth > maxHeight || selected != null
            ScreenScaffold(
                focus = if (showFocus) {
                    {
                        FilePreviewFocus(
                            selected = selected,
                            preview = state.selectedPreview,
                            httpBase = httpBase,
                            deleteEnabled = selected != null && !printingActive,
                            onDelete = { guard = FileGuard.Delete },
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                        )
                    }
                } else {
                    null
                },
                field = {
                    FileBrowserField(
                        state = state,
                        httpBase = httpBase,
                        onRowClick = { row ->
                            scope.launch {
                                when (row.kind) {
                                    FileBrowserRowKind.Up -> holder.goUp()
                                    FileBrowserRowKind.Directory -> holder.enterFolder(row)
                                    FileBrowserRowKind.File -> holder.selectFile(row)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                },
                gutter = {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FileActionControl(
                            label = "Cancel picker",
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Neutral,
                        )
                        FileActionControl(
                            label = if (state.pendingAction != null) "Starting" else "Print file",
                            onClick = { guard = FileGuard.Start },
                            modifier = Modifier.weight(1f),
                            intent = Intent.Go,
                            enabled = startEnabled,
                        )
                    }
                },
            )
        }

        when (guard) {
            FileGuard.Start -> {
                val file = selected
                ConfirmGuard(
                    title = "Print ${file?.name ?: "file"}?",
                    message = selectedFileDetails(file, state.selectedPreview),
                    confirmLabel = "Print file",
                    cancelLabel = "Keep browsing",
                    onConfirm = {
                        holder.requestStartSelected()
                        guard = null
                    },
                    onCancel = { guard = null },
                    destructive = false,
                )
            }
            FileGuard.Delete -> {
                val file = selected
                ConfirmGuard(
                    title = "Delete ${file?.name ?: "file"}?",
                    message = "Removes this file from Moonraker. This cannot be undone.\n${selectedFileDetails(file, state.selectedPreview)}",
                    confirmLabel = "Delete file",
                    cancelLabel = "Keep file",
                    onConfirm = {
                        holder.requestDeleteSelected()
                        guard = null
                    },
                    onCancel = { guard = null },
                    destructive = true,
                )
            }
            null -> Unit
        }
    }
}

private enum class FileGuard { Start, Delete }

@Composable
private fun FileBrowserField(
    state: FileBrowserState,
    httpBase: String,
    onRowClick: (FileBrowserRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PathChip(
            path = state.directory.displayPath.ifBlank { "gcodes" },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.fillMaxWidth().weight(1f)) {
            FileListView(
                directory = state.directory,
                selectedStableId = state.selectedFile?.stableId,
                httpBase = httpBase,
                onRowClick = onRowClick,
                modifier = Modifier.fillMaxSize(),
            )
            if (state.loading && state.directory.rows.isEmpty()) {
                CenterText("Loading files...", Modifier.matchParentSize())
            } else if (!state.loading && state.directory.rows.isEmpty() && state.directory.upRow == null) {
                CenterText("This folder has no printable gcode files. Add files in Moonraker, then reopen Files.", Modifier.matchParentSize())
            }
        }
        state.error?.let { msg ->
            SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
        }
        if (!state.loading && state.directory.rows.isEmpty() && state.directory.upRow != null) {
            Text(
                text = "This folder has no printable gcode files. Add files in Moonraker, then reopen Files.",
                color = t.text2,
                fontFamily = Geist,
                fontSize = fsSp(15f, t.fs).sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun PathChip(path: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(
        modifier
            .clip(RoundedCornerShape(t.rCtrl))
            .border(BorderStroke(2.dp, t.accentLine), RoundedCornerShape(t.rCtrl))
            .background(t.surface2)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = path,
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(15f, t.fs).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CenterText(text: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(modifier.background(t.bg.copy(alpha = 0.86f)).padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = t.text2,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FilePreviewFocus(
    selected: FileBrowserRow?,
    preview: FilePreviewMetadata?,
    httpBase: String,
    deleteEnabled: Boolean,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(
        modifier
            .clip(RoundedCornerShape(t.rCtrl))
            .border(BorderStroke(2.dp, if (selected != null) t.accentLine else t.hair), RoundedCornerShape(t.rCtrl))
            .background(t.surface)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PreviewThumbnail(
            selected = selected,
            preview = preview,
            httpBase = httpBase,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
        Text(
            text = selected?.name ?: "Select a file",
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(22f, t.fs).sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        PreviewDetails(selected, preview, Modifier.fillMaxWidth())
        if (selected != null) {
            FileActionControl(
                label = "Delete file",
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                intent = Intent.Danger,
                enabled = deleteEnabled,
            )
        }
    }
}

@Composable
private fun PreviewThumbnail(
    selected: FileBrowserRow?,
    preview: FilePreviewMetadata?,
    httpBase: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val url = preview?.thumbnailUrl(httpBase) ?: selected?.let {
        val filename = it.relativeFilename
        val thumb = it.thumbnailRelPath
        if (filename != null && thumb != null && httpBase.isNotBlank()) thumbnailUrl(httpBase, filename, thumb) else null
    }

    Box(
        modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(t.rCtrl))
                .background(t.surface2)
                .border(BorderStroke(2.dp, t.hair), RoundedCornerShape(t.rCtrl)),
            contentAlignment = Alignment.Center,
        ) {
            if (url != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .size(FileThumbnailLoader.ROW_THUMBNAIL_SIZE_PX * 3, FileThumbnailLoader.ROW_THUMBNAIL_SIZE_PX * 3)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                MaterialSymbol(
                    name = if (selected == null) "folder_open" else "description",
                    tint = t.text3,
                    sizeSp = fsSp(64f, t.fs),
                )
            }
        }
    }
}

@Composable
private fun PreviewDetails(
    selected: FileBrowserRow?,
    preview: FilePreviewMetadata?,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val rows = listOfNotNull(
        preview?.estimatedTime?.let { "Time ${formatDuration(it)}" },
        (preview?.sizeBytes ?: selected?.sizeBytes)?.let { "Size ${formatBytes(it)}" },
        (preview?.modifiedEpochSeconds ?: selected?.modifiedEpochSeconds)?.let { "Modified ${formatDate(it)}" },
        preview?.filamentTotal?.let { "Filament ${it.toInt()} mm" },
        preview?.filamentWeightTotal?.let { "Weight ${"%.1f".format(Locale.US, it)} g" },
        preview?.layerCount?.let { "Layers $it" },
        preview?.objectHeight?.let { "Height ${"%.1f".format(Locale.US, it)} mm" },
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (rows.isEmpty()) {
            DetailLine("Preview details unavailable", t)
        } else {
            rows.forEach { DetailLine(it, t) }
        }
    }
}

@Composable
private fun DetailLine(text: String, t: ThemeTokens) {
    Text(
        text = text,
        color = t.text2,
        fontFamily = GeistMono,
        fontSize = fsSp(13f, t.fs).sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun FileActionControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: Intent = Intent.Neutral,
    enabled: Boolean = true,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = intentColor(intent, t)
    val base = modifier
        .heightIn(min = 64.dp)
        .clip(shape)
        .border(BorderStroke(2.dp, if (enabled) outline else t.hair), shape)
        .background(if (enabled) Color.Transparent else t.surface)
        .padding(horizontal = 12.dp, vertical = 18.dp)
    val clickable = if (enabled) base.clickable(onClick = onClick) else base.semantics { disabled() }

    Box(clickable, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = if (enabled) t.text else t.text3,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun intentColor(intent: Intent, t: ThemeTokens): Color = when (intent) {
    Intent.Neutral -> t.outline
    Intent.Accent -> t.accentLine
    Intent.Warn -> t.heat
    Intent.Danger -> t.stop
    Intent.Go -> t.go
}

private fun selectedFileDetails(file: FileBrowserRow?, preview: FilePreviewMetadata?): String =
    listOfNotNull(
        file?.relativeFilename,
        preview?.estimatedTime?.let { "Time ${formatDuration(it)}" },
        (preview?.sizeBytes ?: file?.sizeBytes)?.let { "Size ${formatBytes(it)}" },
        (preview?.modifiedEpochSeconds ?: file?.modifiedEpochSeconds)?.let { "Modified ${formatDate(it)}" },
        preview?.filamentTotal?.let { "Filament ${it.toInt()} mm" },
        preview?.layerCount?.let { "Layers $it" },
    ).joinToString("\n").ifBlank { "Metadata is unavailable. The print can still start." }

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    bytes >= 1024L -> "${bytes / 1024L} KB"
    else -> "$bytes B"
}

private fun formatDate(epochSeconds: Double): String =
    SimpleDateFormat("MMM d, HH:mm", Locale.US).format(Date((epochSeconds * 1000).toLong()))

private fun formatDuration(seconds: Double): String {
    val totalMinutes = (seconds / 60.0).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
