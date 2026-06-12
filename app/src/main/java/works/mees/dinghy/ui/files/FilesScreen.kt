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
import androidx.compose.ui.text.font.FontWeight
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
import works.mees.dinghy.designsystem.components.DetailCard
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
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.thumbnailUrl
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
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
 * @param sortAscending   current age/date sort direction (true = oldest first, false = newest first).
 * @param loading         whether the file list is loading.
 * @param error           error message to surface, or null when clean.
 * @param httpBase        the Moonraker base URL for thumbnail resolution (blank in previews).
 */
data class FilesScreenState(
    val fileRows: List<FileBrowserRow> = emptyList(),
    val selectedFile: FileBrowserRow? = null,
    val selectedPreview: FilePreviewMetadata? = null,
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
) {
    val holderState by holder.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var guard by remember { mutableStateOf<FileGuard?>(null) }
    var sortAscending by remember { mutableStateOf(false) }

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
    // D-06: single age/date sort — sort by modifiedEpochSeconds (null at NEGATIVE_INFINITY).
    val sortedRows = if (sortAscending) {
        fileRows.sortedBy { it.modifiedEpochSeconds ?: Double.NEGATIVE_INFINITY }
    } else {
        fileRows.sortedByDescending { it.modifiedEpochSeconds ?: Double.NEGATIVE_INFINITY }
    }

    LaunchedEffect(holder) {
        holder.loadRoot()
    }

    val screenState = FilesScreenState(
        fileRows = sortedRows,
        selectedFile = selected,
        selectedPreview = selectedPreview,
        sortAscending = sortAscending,
        loading = holderState.loading,
        error = holderState.error,
        httpBase = httpBase,
    )

    Box(modifier.fillMaxSize()) {
        FilesContent(
            state = screenState,
            deleteEnabled = deleteEnabled,
            startEnabled = startEnabled,
            onRowClick = { row ->
                scope.launch { holder.selectFile(row) }
            },
            onToggleSort = { sortAscending = !sortAscending },
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
    onToggleSort: () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        FilesContent(
            state = state,
            deleteEnabled = state.selectedFile != null,
            startEnabled = state.selectedFile != null,
            onRowClick = {},
            onToggleSort = onToggleSort,
            onBack = onBack,
            onStartPrint = onPrint,
            onDelete = onDelete,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared content scaffold (ScreenScaffold, gutter = null)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FilesContent(
    state: FilesScreenState,
    deleteEnabled: Boolean,
    startEnabled: Boolean,
    onRowClick: (FileBrowserRow) -> Unit,
    onToggleSort: () -> Unit,
    onBack: () -> Unit,
    onStartPrint: () -> Unit,
    onDelete: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // U derived once at screen root from the short edge (portrait width == landscape height).
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val t = LocalTokens.current

        // D-06 sort option (single CalendarClock option; direction toggle on re-tap).
        val sortOptions = persistentListOf(
            SortOption(
                key = Unit,
                icon = DinghyIcons.CalendarClock,
                contentDescriptionRes = R.string.cd_files_sort_date,
                directionUp = state.sortAscending,
            ),
        )

        ScreenScaffold(
            focus = {
                // Focus = image-backed DetailCard of the selected file showing FUTURE-PRINT fields.
                // NOTE: no screen-local FloatingEStop here — since Phase 24 (FIX-1) AppShell overlays
                // the app-level printing-only e-stop on EVERY destination (CR-01: the local copy was a
                // dead duplicate whose confirm dispatched nothing).
                DetailCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    FilesDetailContent(
                        state = state,
                        t = t,
                    )
                }
                // D-06: single age/date sort row (no filter; no name/size sort).
                SortRow(
                    options = sortOptions,
                    activeKey = Unit,
                    onSelect = { onToggleSort() },
                    uDp = grid.uDp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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
            gutter = null, // Redesigned screen — FootButtonBar lives in the field lambda (Pitfall 1).
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Focus: the image-backed future-print DetailCard content
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The DetailCard content: FUTURE-PRINT fields only (est time, filament, layers, height, size,
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

        // FOREGROUND — filename + future-print stats, vertically centered, left-aligned.
        Column(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = selected.name,
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(20f, t.fs).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
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
        Text(label, color = t.text2, fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = fsSp(15f, t.fs).sp)
        Text(
            value,
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(17f, t.fs).sp,
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
 * [FootButtonBar] is the LAST element here (gutter = null pattern — Pitfall 1).
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
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(18f, t.fs).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
    } else {
        ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
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
        SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
    }

    // FootButtonBar — Back · Print · Delete (D-07).
    // OutlinedControl does not have an `enabled` param; use alpha + semantics disabled to gate.
    FootButtonBar(
        uDp = uDp,
    ) {
        OutlinedControl(
            label = stringResource(R.string.common_back),
            onClick = onBack,
            modifier = Modifier.weight(1f),
            intent = Intent.Accent, // R5: Back = accent
            icon = DinghyIcons.Back,
        )
        OutlinedControl(
            label = stringResource(R.string.files_foot_print),
            onClick = { if (startEnabled) onStartPrint() },
            modifier = Modifier
                .weight(1f)
                .alpha(if (startEnabled) 1f else 0.38f)
                .then(if (!startEnabled) Modifier.semantics { disabled() } else Modifier),
            intent = Intent.Go, // R5: Print = the expected action
            icon = DinghyIcons.Print,
            contentDescription = stringResource(R.string.cd_files_print),
        )
        OutlinedControl(
            label = stringResource(R.string.files_foot_delete),
            onClick = { if (deleteEnabled) onDelete() },
            modifier = Modifier
                .weight(1f)
                .alpha(if (deleteEnabled) 1f else 0.38f)
                .then(if (!deleteEnabled) Modifier.semantics { disabled() } else Modifier),
            intent = Intent.Danger,
            icon = DinghyIcons.Delete,
            contentDescription = stringResource(R.string.cd_files_delete),
        )
    }
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
        trailingContent = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(vertical = 4.dp),
            ) {
                row.sizeBytes?.let {
                    Text(
                        text = formatBytes(it),
                        color = t.text2,
                        fontFamily = GeistMono,
                        fontSize = fsSp(15f, t.fs).sp,
                        maxLines = 1,
                    )
                }
                row.modifiedEpochSeconds?.let {
                    Text(
                        text = formatDate(it),
                        color = t.text3,
                        fontFamily = GeistMono,
                        fontSize = fsSp(15f, t.fs).sp, // metadata floor 15sp ([[dinghy-font-sizes-too-small]])
                        maxLines = 1,
                    )
                }
            }
        },
    ) {
        Text(
            text = row.name,
            color = if (selected) t.accent2 else t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(17f, t.fs).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
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
            field = {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.files_confirm_print_title, fileName),
                        color = t.text,
                        fontFamily = Geist,
                        fontWeight = FontWeight.Bold,
                        fontSize = fsSp(28f, t.fs).sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.files_spool_warning_subtitle),
                        color = t.text2,
                        fontFamily = Geist,
                        fontWeight = FontWeight.Normal,
                        fontSize = fsSp(17f, t.fs).sp,
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
                                fontFamily = Geist,
                                fontWeight = FontWeight.Medium,
                                fontSize = fsSp(18f, t.fs).sp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Text(
                        text = fileDetails,
                        color = t.text3,
                        fontFamily = Geist,
                        fontWeight = FontWeight.Normal,
                        fontSize = fsSp(15f, t.fs).sp,
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
