package works.mees.dinghy.ui.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FocusFrame

import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.SortOption
import works.mees.dinghy.designsystem.components.SortRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.preview.PreviewPlaceholderBox
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.spool.SpoolmanStatus
import works.mees.dinghy.spool.parseSpoolmanSpools
import works.mees.dinghy.state.FileBrowserRow
import works.mees.dinghy.state.FileBrowserRowKind
import works.mees.dinghy.state.FilePreviewMetadata
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.thumbnailUrl
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.ui.spool.SpoolWarning
import works.mees.dinghy.ui.spool.evaluatePrintStartGate

// ─────────────────────────────────────────────────────────────────────────────
// State model for the stateless preview seam
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The pure view-state for the stateless [FilesScreen] overload (preview seam). No holder, no
 * dispatcher, no Moonraker required — use fake data to drive the @Preview matrix.
 *
 * @param fileRows        the FLAT list of File rows (D-05 — Dir/Up rows are NEVER in this list).
 * @param selectedFile    the currently-selected file row (null = nothing selected).
 * @param selectedPreview metadata for the selected file (loaded asynchronously in the live overload).
 * @param sortField      the active sort dimension (Date or Size).
 * @param sortAscending  direction of the active field (true = ascending).
 * @param loading         whether the file list is loading.
 * @param error           error message to surface, or null when clean.
 * @param httpBase        the Moonraker base URL for thumbnail resolution (blank in previews).
 */
data class FilesScreenState(
    val fileRows: List<FileBrowserRow> = emptyList(),
    val selectedFile: FileBrowserRow? = null,
    val selectedPreview: FilePreviewMetadata? = null,
    val sortField: FileSortField = FileSortField.Date,
    val sortAscending: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val httpBase: String = "",
)

// ─────────────────────────────────────────────────────────────────────────────
// Live overload (AppShell entry point — signature unchanged)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Live [FilesScreen] overload: collects [FileBrowserHolder.state] and delegates rendering to the
 * stateless [FilesScreen] + [FilesContent] seam.
 *
 * The D-15 delete-scoping predicate, the SpoolWarningGuard, and both ConfirmGuards are ALL
 * implemented here — they involve live guards and are therefore in the live overload (they are
 * exercised by the unit-test anchor: [FilesDeleteGateTest]).
 *
 * @param spoolmanPresent    D-02 capability gate — true only on a printer with the Moonraker
 *                           `spoolman` component. When false the warn-only print-start gate is
 *                           SKIPPED ENTIRELY.
 * @param activeSpoolStatus  the D-10-reconciled active-spool status; null while idle/unavailable.
 * @param spoolmanClient     the session inventory reader the gate resolves the active-spool detail
 *                           through (best-effort getSpool — rejection surfaces a "could not verify"
 *                           amber warning, never a crash). Null while idle.
 * @param onPickSpoolForFile D-04 gcode-aware prefilter: opens the Spool picker seeded by the
 *                           selected file's `filament_type[]` + `filament_colors[]`.
 * @param onScanSpool        opens the QR scan sub-surface.
 */
@Composable
fun FilesScreen(
    holder: FileBrowserHolder,
    printerState: PrinterState,
    httpBase: String,
    canStartPrint: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    spoolmanPresent: Boolean = false,
    activeSpoolStatus: SpoolmanStatus? = null,
    spoolmanClient: SpoolmanClient? = null,
    onPickSpoolForFile: (filamentType: List<String>, filamentColors: List<String>) -> Unit = { _, _ -> },
    onScanSpool: () -> Unit = {},
    onEmergencyStop: () -> Unit = {},
) {
    val holderState by holder.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var guard by remember { mutableStateOf<FileGuard?>(null) }
    var sortField by remember { mutableStateOf(FileSortField.Date) }
    var sortAscending by remember { mutableStateOf(FileSortField.Date.defaultAscending) }

    // ---- D-01 warn-only print-start gate (SPOOL-07) ------------------------------------------------
    val activeSpoolId = activeSpoolStatus?.activeSpoolId
    var spoolDetail by remember { mutableStateOf<SpoolmanSpool?>(null) }
    var spoolFetchFailed by remember { mutableStateOf(false) }
    LaunchedEffect(activeSpoolId, spoolmanPresent, spoolmanClient) {
        val id = activeSpoolId
        if (!spoolmanPresent || id == null) {
            spoolDetail = null
            spoolFetchFailed = false
        } else {
            val client = spoolmanClient
            if (client == null) {
                spoolDetail = null
                spoolFetchFailed = false
            } else {
                val envelope = runCatching { client.getSpool(id) }.getOrNull()
                val resolved = parseSpoolmanSpools(envelope).rows.firstOrNull { it.id == id }
                spoolDetail = resolved
                spoolFetchFailed = resolved == null
            }
        }
    }

    val selected = holderState.selectedFile
    val selectedPreview = holderState.selectedPreview
    val spoolWarnings: List<SpoolWarning> =
        if (spoolmanPresent && activeSpoolStatus != null && selectedPreview != null) {
            evaluatePrintStartGate(
                activeSpool = spoolDetail,
                status = activeSpoolStatus,
                fetchFailed = spoolFetchFailed,
                file = selectedPreview,
            )
        } else {
            emptyList()
        }

    // D-15: delete is scoped to the ACTIVE print file, not idle-only. Host-tested via
    // FilesDeleteGateTest — the predicate must not regress.
    val deleteEnabled = deleteAllowed(
        selectedPath = selected?.relativeFilename,
        activePrintFilename = printerState.printFilename,
        printState = printerState.printState,
    )

    val startEnabled = selected != null && canStartPrint && holderState.pendingAction == null

    // D-05: flat file list — only File rows; never Up or Directory.
    val fileRows = holderState.directory.rows.filter { it.kind == FileBrowserRowKind.File }
    // D-06: one active sort field at a time (date | size); direction toggles on re-tap.
    val sortedRows = sortFileRows(fileRows, sortField, sortAscending)

    LaunchedEffect(holder) {
        holder.loadRoot()
    }

    val screenState = FilesScreenState(
        fileRows = sortedRows,
        selectedFile = selected,
        selectedPreview = selectedPreview,
        sortField = sortField,
        sortAscending = sortAscending,
        loading = holderState.loading,
        error = holderState.error,
        httpBase = httpBase,
    )

    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused

    Box(modifier.fillMaxSize()) {
        FilesContent(
            state = screenState,
            deleteEnabled = deleteEnabled,
            startEnabled = startEnabled,
            isPrinting = isPrinting,
            onEmergencyStop = onEmergencyStop,
            onRowClick = { row ->
                scope.launch { holder.selectFile(row) }
            },
            onSelectSort = { field ->
                if (field == sortField) {
                    sortAscending = !sortAscending
                } else {
                    sortField = field
                    sortAscending = field.defaultAscending
                }
            },
            onBack = onBack,
            onStartPrint = { guard = FileGuard.Start },
            onDelete = { guard = FileGuard.Delete },
        )

        when (guard) {
            FileGuard.Start -> {
                val file = selected
                if (spoolWarnings.isEmpty()) {
                    // CLEAN PASS (D-01): no spool warnings → the existing `Print file` confirm, UNCHANGED.
                    ConfirmGuard(
                        title = stringResource(
                            R.string.files_confirm_print_title,
                            file?.name ?: stringResource(R.string.files_fallback_filename),
                        ),
                        message = selectedFileDetails(file, selectedPreview),
                        confirmLabel = stringResource(R.string.files_confirm_print_confirm),
                        cancelLabel = stringResource(R.string.files_confirm_print_cancel),
                        onConfirm = {
                            holder.requestStartSelected()
                            guard = null
                        },
                        onCancel = { guard = null },
                        destructive = false,
                    )
                } else {
                    // WARN-ONLY GATE (D-01): amber proceed-at-peril — NEVER blocks.
                    SpoolWarningGuard(
                        fileName = file?.name ?: stringResource(R.string.files_fallback_filename),
                        warnings = spoolWarnings,
                        fileDetails = selectedFileDetails(file, selectedPreview),
                        onPickSpool = {
                            onPickSpoolForFile(
                                selectedPreview?.filamentType.orEmpty(),
                                selectedPreview?.filamentColors.orEmpty(),
                            )
                            guard = null
                        },
                        onScan = {
                            onScanSpool()
                            guard = null
                        },
                        onPrintAnyway = {
                            holder.requestStartSelected()
                            guard = null
                        },
                        onBack = { guard = null },
                    )
                }
            }
            FileGuard.Delete -> {
                val file = selected
                ConfirmGuard(
                    title = stringResource(
                        R.string.files_confirm_delete_title,
                        file?.name ?: stringResource(R.string.files_fallback_filename),
                    ),
                    message = stringResource(R.string.files_confirm_delete_message) +
                        "\n" + selectedFileDetails(file, selectedPreview),
                    confirmLabel = stringResource(R.string.files_confirm_delete_confirm),
                    cancelLabel = stringResource(R.string.files_confirm_delete_cancel),
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

// ─────────────────────────────────────────────────────────────────────────────
// Stateless preview seam (no holder, no Moonraker — drive from FilesScreenState)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stateless [FilesScreen] overload — the @Preview matrix and tests drive this overload.
 * No holder, no dispatcher, no Moonraker. All callbacks are no-ops by default.
 */
@Composable
fun FilesScreen(
    state: FilesScreenState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
    onPrint: () -> Unit = {},
    onDelete: () -> Unit = {},
    onSelectSort: (FileSortField) -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        FilesContent(
            state = state,
            deleteEnabled = state.selectedFile != null,
            startEnabled = state.selectedFile != null,
            isPrinting = false,
            onEmergencyStop = {},
            onRowClick = {},
            onSelectSort = onSelectSort,
            onBack = onBack,
            onStartPrint = onPrint,
            onDelete = onDelete,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared content scaffold (ScreenScaffold, foot-of-list)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FilesContent(
    state: FilesScreenState,
    deleteEnabled: Boolean,
    startEnabled: Boolean,
    isPrinting: Boolean,
    onEmergencyStop: () -> Unit,
    onRowClick: (FileBrowserRow) -> Unit,
    onSelectSort: (FileSortField) -> Unit,
    onBack: () -> Unit,
    onStartPrint: () -> Unit,
    onDelete: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // U derived once at screen root from the short edge (portrait width == landscape height).
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val t = LocalTokens.current

        // D-06 sort: date (CalendarClock) + size (LineWeight). One active at a time; the active
        // tile shows its direction arrow (SortRow renders the arrow only on the active key).
        val sortOptions = persistentListOf(
            SortOption(
                key = FileSortField.Date,
                icon = DinghyIcons.CalendarClock,
                contentDescriptionRes = R.string.cd_files_sort_date,
                directionUp = if (state.sortField == FileSortField.Date) state.sortAscending else null,
            ),
            SortOption(
                key = FileSortField.Size,
                icon = DinghyIcons.LineWeight,
                contentDescriptionRes = R.string.cd_files_sort_size,
                directionUp = if (state.sortField == FileSortField.Size) state.sortAscending else null,
            ),
        )

        ScreenScaffold(
            focus = {
                // Focus = image-backed FocusFrame of the selected file showing FUTURE-PRINT fields.
                // E-stop now docks into the FocusFrame header (header slot morphs when isPrinting).
                FocusFrame(
                    title = state.selectedFile?.name ?: stringResource(R.string.cd_launcher_files),
                    icon = DinghyIcons.LauncherFiles,
                    uDp = grid.uDp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    FilesDetailContent(
                        state = state,
                        t = t,
                    )
                }
                // D-06: date + size sort row (one active at a time; re-tap flips direction).
                SortRow(
                    options = sortOptions,
                    activeKey = state.sortField,
                    onSelect = { onSelectSort(it) },
                    uDp = grid.uDp,
                )
            },
            field = {
                // Field = flat file list (D-05: File rows only, never Up/Directory).
                FilesListField(
                    state = state,
                    uDp = grid.uDp,
                    deleteEnabled = deleteEnabled,
                    startEnabled = startEnabled,
                    onRowClick = onRowClick,
                    onBack = onBack,
                    onStartPrint = onStartPrint,
                    onDelete = onDelete,
                    t = t,
                )
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Focus: the image-backed future-print FocusFrame content
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The FocusFrame content: FUTURE-PRINT fields only (est time, filament, layers, height, size,
 * modified). NEVER shows elapsed/finished/status (Files Focus = future-print grammar).
 *
 * The gcode thumbnail is the dimmed BACKGROUND (ContentScale.Fit, alpha 0.3f) with GeistMono
 * stats overlaid — matching the LastJobCard / FilePreviewFocus precedent.
 */
@Composable
private fun FilesDetailContent(
    state: FilesScreenState,
    t: ThemeTokens,
) {
    val selected = state.selectedFile
    val preview = state.selectedPreview
    val context = LocalContext.current
    val inInspection = LocalInspectionMode.current

    // Prefer the LARGE preview thumbnail; fall back to the row's small thumb until metadata loads.
    val url = if (!inInspection) {
        preview?.thumbnailUrl(state.httpBase) ?: selected?.let { row ->
            val filename = row.relativeFilename
            val thumb = row.thumbnailRelPath
            if (filename != null && thumb != null && state.httpBase.isNotBlank())
                thumbnailUrl(state.httpBase, filename, thumb)
            else null
        }
    } else null

    if (selected == null) {
        // Nothing selected — empty-state glyph centered in the card.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            DinghyIconView(
                icon = DinghyIcons.LauncherFiles,
                tint = t.text3,
                sizeDp = fsSp(64f, t.fs).dp,
                contentDescription = null,
            )
        }
        return
    }

    // Thumbnail background (dimmed — the same ContentScale.Fit/alpha 0.3f pattern as LastJobCard).
    Box(Modifier.fillMaxSize()) {
        if (url != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(url)
                    .size(FileThumbnailLoader.ROW_THUMBNAIL_SIZE_PX * 3, FileThumbnailLoader.ROW_THUMBNAIL_SIZE_PX * 3)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alpha = 0.3f,
                modifier = Modifier.matchParentSize(),
            )
        } else if (inInspection) {
            // D-05 preview-safe placeholder — Coil AsyncImage is unsafe in inspection mode.
            PreviewPlaceholderBox(
                label = "Thumbnail",
                modifier = Modifier.matchParentSize(),
            )
        }

        // FOREGROUND — future-print stats, top-aligned, left-aligned. Top-anchored (not centered)
        // so the stats sit directly under the header; with the filename removed, centering left a
        // large gap below the title bar.
        // Filename is now shown in the FocusFrame header title; no need to repeat it here.
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                // top=0 so the stats sit flush under the header (the FocusFrame top inset is already
                // 0); 8dp side/bottom only. Was padding(8.dp) all-round, which re-added a top gap.
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            preview?.let { p ->
                p.estimatedTime?.let { FileStatRow(DinghyIcons.TimerDown, stringResource(R.string.files_stat_est_time), formatDuration(it), t) }
                p.filamentTotal?.let { ft ->
                    val w = p.filamentWeightTotal?.let { g -> " · ${"%.1f".format(Locale.US, g)} g" }.orEmpty()
                    FileStatRow(DinghyIcons.Layers, stringResource(R.string.files_stat_filament), "${ft.toInt()} mm$w", t)
                }
                p.layerCount?.let { FileStatRow(DinghyIcons.Layers, stringResource(R.string.files_stat_layers), it.toString(), t) }
                p.objectHeight?.let { FileStatRow(DinghyIcons.Altitude, stringResource(R.string.files_stat_height), "${"%.1f".format(Locale.US, it)} mm", t) }
            }
            (preview?.sizeBytes ?: selected.sizeBytes)?.let {
                FileStatRow(DinghyIcons.Scale, stringResource(R.string.files_stat_size), formatBytes(it), t)
            }
            (preview?.modifiedEpochSeconds ?: selected.modifiedEpochSeconds)?.let {
                FileStatRow(DinghyIcons.CalendarClock, stringResource(R.string.files_stat_modified), formatDate(it), t)
            }
            if (preview == null) {
                FileStatRow(DinghyIcons.TimerDown, stringResource(R.string.files_stat_preview), stringResource(R.string.files_stat_preview_loading), t)
            }
        }
    }
}

/** One icon-led stat line in the future-print card: icon + dim label + GeistMono value. */
@Composable
private fun FileStatRow(
    icon: works.mees.dinghy.designsystem.icons.DinghyIcon,
    label: String,
    value: String,
    t: ThemeTokens,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DinghyIconView(icon, tint = t.text2, sizeDp = fsSp(18f, t.fs).dp, contentDescription = null)
        Text(label, color = t.text2, style = DinghyType.caption.toTextStyle(t))
        Text(
            value,
            color = t.text,
            style = DinghyType.dataInline.toTextStyle(t),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Field: flat file list + FootButtonBar
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The flat file-list Field (D-05 — only [FileBrowserRowKind.File] rows render; never Up/Directory).
 * [FootButtonBar] is the LAST element here (foot-of-list pattern — Pitfall 1).
 *
 * Foot buttons (D-07): Back (Neutral) · Print (Accent) · Delete (Danger).
 */
@Composable
private fun androidx.compose.foundation.layout.ColumnScope.FilesListField(
    state: FilesScreenState,
    uDp: Dp,
    deleteEnabled: Boolean,
    startEnabled: Boolean,
    onRowClick: (FileBrowserRow) -> Unit,
    onBack: () -> Unit,
    onStartPrint: () -> Unit,
    onDelete: () -> Unit,
    t: ThemeTokens,
) {
    if (state.fileRows.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    state.loading -> stringResource(R.string.files_loading)
                    state.error != null -> stringResource(R.string.files_error_load)
                    else -> stringResource(R.string.files_empty)
                },
                color = t.text2,
                style = DinghyType.body.toTextStyle(t),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        ListBlock(modifier = Modifier.weight(1f)) {
            items(state.fileRows, key = { it.stableId }) { row ->
                FilesListRow(
                    row = row,
                    selected = row.stableId == state.selectedFile?.stableId,
                    uDp = uDp,
                    httpBase = state.httpBase,
                    onRowClick = onRowClick,
                    t = t,
                )
            }
        }
    }

    state.error?.let { msg ->
        SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
    }

    // FootButtonBar — Back · Print · Delete (D-07).
    // OutlinedControl does not have an `enabled` param; use alpha + semantics disabled to gate.
    // 3 actions → icon-only; per-button alpha/disabled modifiers pass through FootAction.modifier.
    FootButtonBar(
        uDp = uDp,
        actions = listOf(
            FootAction(
                label = stringResource(R.string.common_back),
                icon = DinghyIcons.Back,
                onClick = onBack,
                intent = Intent.Accent, // R5: Back = accent
            ),
            FootAction(
                label = stringResource(R.string.files_foot_print),
                icon = DinghyIcons.Print,
                onClick = { if (startEnabled) onStartPrint() },
                intent = Intent.Go, // R5: Print = the expected action
                contentDescription = stringResource(R.string.cd_files_print),
                modifier = Modifier
                    .alpha(if (startEnabled) 1f else 0.38f)
                    .then(if (!startEnabled) Modifier.semantics { disabled() } else Modifier),
            ),
            FootAction(
                label = stringResource(R.string.files_foot_delete),
                icon = DinghyIcons.Delete,
                onClick = { if (deleteEnabled) onDelete() },
                intent = Intent.Danger,
                contentDescription = stringResource(R.string.cd_files_delete),
                modifier = Modifier
                    .alpha(if (deleteEnabled) 1f else 0.38f)
                    .then(if (!deleteEnabled) Modifier.semantics { disabled() } else Modifier),
            ),
        ),
    )
}

/** A single file row in the flat list — thumbnail leading, size+modified trailing. */
@Composable
private fun FilesListRow(
    row: FileBrowserRow,
    selected: Boolean,
    uDp: Dp,
    httpBase: String,
    onRowClick: (FileBrowserRow) -> Unit,
    t: ThemeTokens,
) {
    val context = LocalContext.current
    val inInspection = LocalInspectionMode.current

    // Thumbnail URL (small row size, 96px). Skip in inspection mode (Coil is preview-unsafe).
    val thumbUrl = if (!inInspection && httpBase.isNotBlank()) {
        val filename = row.relativeFilename
        val thumb = row.thumbnailRelPath
        if (filename != null && thumb != null) thumbnailUrl(httpBase, filename, thumb) else null
    } else null

    ListRow(
        selected = selected,
        onClick = { onRowClick(row) },
        uDp = uDp,
        leadingContent = {
            if (thumbUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumbUrl)
                        .size(FileThumbnailLoader.ROW_THUMBNAIL_SIZE_PX, FileThumbnailLoader.ROW_THUMBNAIL_SIZE_PX)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(fsSp(40f, t.fs).dp)
                        .padding(end = 8.dp),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // Line 1 — filename. One line, ellipsized, so the row never grows past 1U (R23).
            Text(
                text = row.name,
                color = if (selected) t.accent2 else t.text,
                style = DinghyType.dataInline.toTextStyle(t),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            // Line 2 — date (start) … size (end). SpaceBetween anchors each to its edge even
            // when one is null (the absent slot collapses but the present one keeps its edge).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.modifiedEpochSeconds?.let { formatDate(it) }.orEmpty(),
                    color = t.text2,
                    style = DinghyType.dataMeta.toTextStyle(t),
                    maxLines = 1,
                )
                Text(
                    text = row.sizeBytes?.let { formatBytes(it) }.orEmpty(),
                    color = t.text2,
                    style = DinghyType.dataMeta.toTextStyle(t),
                    maxLines = 1,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SpoolWarningGuard (D-01 warn-only gate — preserved verbatim from the previous screen)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The D-01 warn-only print-start gate surface (SPOOL-07). Full-screen amber overlay. NEVER blocks.
 *
 * Four actions:
 *  - **Pick spool** (accent) — opens the Spool picker with the gcode-aware prefilter seed.
 *  - **Scan** (accent) — opens the QR scan surface.
 *  - **Print anyway** (warn/amber) — proceeds in ONE tap; gate NEVER blocks.
 *  - **Back** (neutral) — safe dismiss.
 */
@Composable
private fun SpoolWarningGuard(
    fileName: String,
    warnings: List<SpoolWarning>,
    fileDetails: String,
    onPickSpool: () -> Unit,
    onScan: () -> Unit,
    onPrintAnyway: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Box(modifier.fillMaxSize().background(t.bg).background(t.heatSoft)) {
        ScreenScaffold(
            focusFramed = false,
            fieldFramed = false,
            field = {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.files_confirm_print_title, fileName),
                        color = t.text,
                        style = DinghyType.screenTitle.toTextStyle(t),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.files_spool_warning_subtitle),
                        color = t.text2,
                        style = DinghyType.body.toTextStyle(t),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                    warnings.forEach { w ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The original SpoolWarningGuard glyph (preserved, not newly chosen) —
                            // now routed through the registry token (WR-07: raw ligature strings
                            // bypass the planned font subset + the verify_ligatures gate).
                            DinghyIconView(
                                icon = DinghyIcons.Warning,
                                tint = t.heat,
                                sizeDp = fsSp(18f, t.fs).dp,
                                contentDescription = null,
                            )
                            Text(
                                text = w.message,
                                color = t.heat,
                                style = DinghyType.body.toTextStyle(t),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Text(
                        text = fileDetails,
                        color = t.text3,
                        style = DinghyType.caption.toTextStyle(t),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedControl(
                            label = stringResource(R.string.files_spool_warning_pick_spool),
                            onClick = onPickSpool,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Accent,
                            icon = DinghyIcons.Inventory,
                        )
                        OutlinedControl(
                            label = stringResource(R.string.files_spool_warning_scan),
                            onClick = onScan,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Accent,
                            icon = DinghyIcons.QrCode,
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedControl(
                            label = stringResource(R.string.common_back),
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Neutral,
                        )
                        OutlinedControl(
                            label = stringResource(R.string.files_spool_warning_print_anyway),
                            onClick = onPrintAnyway,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Warn,
                        )
                    }
                }
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal guards enum
// ─────────────────────────────────────────────────────────────────────────────

private enum class FileGuard { Start, Delete }

// ─────────────────────────────────────────────────────────────────────────────
// Utility helpers
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun selectedFileDetails(file: FileBrowserRow?, preview: FilePreviewMetadata?): String =
    listOfNotNull(
        file?.relativeFilename,
        preview?.estimatedTime?.let { stringResource(R.string.files_detail_time, formatDuration(it)) },
        (preview?.sizeBytes ?: file?.sizeBytes)?.let { stringResource(R.string.files_detail_size, formatBytes(it)) },
        (preview?.modifiedEpochSeconds ?: file?.modifiedEpochSeconds)?.let {
            stringResource(R.string.files_detail_modified, formatDate(it))
        },
        preview?.filamentTotal?.let { stringResource(R.string.files_detail_filament, it.toInt()) },
        preview?.layerCount?.let { stringResource(R.string.files_detail_layers, it) },
    ).joinToString("\n").ifBlank { stringResource(R.string.files_detail_unavailable) }

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

// ─────────────────────────────────────────────────────────────────────────────
// Sort helpers (pure — host-testable, no Compose/Android deps)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The two sort dimensions on the Files screen. One is active at a time (SpoolScreen precedent).
 * [defaultAscending] is the direction applied when the field is freshly selected; re-tapping the
 * active field flips it. Date defaults newest-first; Size defaults largest-first (owner, 2026-06-13).
 */
enum class FileSortField(val defaultAscending: Boolean) {
    Date(defaultAscending = false),
    Size(defaultAscending = false),
}

/**
 * Pure, host-testable sort over the flat file list. Null sort keys sink to the bottom regardless
 * of direction (NEGATIVE_INFINITY / MIN_VALUE), matching the prior date-sort behavior.
 */
internal fun sortFileRows(
    rows: List<FileBrowserRow>,
    field: FileSortField,
    ascending: Boolean,
): List<FileBrowserRow> = when (field) {
    FileSortField.Date ->
        if (ascending) rows.sortedBy { it.modifiedEpochSeconds ?: Double.NEGATIVE_INFINITY }
        else rows.sortedByDescending { it.modifiedEpochSeconds ?: Double.NEGATIVE_INFINITY }
    FileSortField.Size ->
        if (ascending) rows.sortedBy { it.sizeBytes ?: Long.MIN_VALUE }
        else rows.sortedByDescending { it.sizeBytes ?: Long.MIN_VALUE }
}
