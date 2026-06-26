package works.mees.jiib.ui.files

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import works.mees.jiib.state.FileBrowserDirectory
import works.mees.jiib.state.FileBrowserRow
import works.mees.jiib.state.FileBrowserRowKind
import works.mees.jiib.state.FilePreviewMetadata
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.parseFileBrowserDirectory
import works.mees.jiib.state.parseFilePreviewMetadata

data class FileBrowserState(
    val directory: FileBrowserDirectory = emptyDirectory(),
    val selectedFile: FileBrowserRow? = null,
    val selectedPreview: FilePreviewMetadata? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val pendingAction: FileBrowserPendingAction? = null,
)

sealed interface FileBrowserPendingAction {
    data class Starting(val filename: String) : FileBrowserPendingAction
}

class FileBrowserHolder(
    scope: CoroutineScope,
    private val client: FileBrowserClient,
    private val printerState: StateFlow<PrinterState>,
) {
    private val _state = MutableStateFlow(FileBrowserState())
    val state: StateFlow<FileBrowserState> = _state.asStateFlow()

    init {
        scope.launch {
            printerState.collect { printer ->
                val pending = _state.value.pendingAction
                if (
                    pending is FileBrowserPendingAction.Starting &&
                    printer.printFilename == pending.filename &&
                    (printer.printState == PrintState.Printing || printer.printState == PrintState.Paused)
                ) {
                    _state.update { it.copy(pendingAction = null) }
                }
            }
        }
    }

    suspend fun loadRoot() {
        loadDirectory("gcodes")
    }

    suspend fun enterFolder(row: FileBrowserRow) {
        if (row.kind == FileBrowserRowKind.Directory && row.directoryPath != null) {
            loadDirectory(row.directoryPath)
        }
    }

    suspend fun goUp() {
        state.value.directory.upRow?.directoryPath?.let { loadDirectory(it) }
    }

    suspend fun selectFile(row: FileBrowserRow) {
        if (row.kind != FileBrowserRowKind.File || row.relativeFilename == null) return
        val current = state.value
        if (current.selectedFile?.relativeFilename == row.relativeFilename) return

        _state.update { it.copy(selectedFile = row, selectedPreview = null, error = null) }
        val preview = runCatching {
            client.getMetadata(row.relativeFilename)?.jsonObject?.let {
                parseFilePreviewMetadata(row.relativeFilename, it)
            }
        }.getOrNull()
        _state.update { it.copy(selectedPreview = preview) }
    }

    fun requestStartSelected() {
        val selected = state.value.selectedFile
        val filename = selected?.relativeFilename ?: return
        client.startPrint(filename)
        _state.update { it.copy(pendingAction = FileBrowserPendingAction.Starting(filename), error = null) }
    }

    fun requestDeleteSelected() {
        val selected = state.value.selectedFile ?: return
        val path = selected.rootPrefixedPath ?: return
        val printer = printerState.value
        // D-15: scope the holder gate to the ACTIVE print file, not every delete during a print. The
        // host-tested pure `deleteAllowed` predicate compares the RELATIVE, no-"gcodes/" form against
        // print_stats.filename — the SAME helper the FilesScreen gate uses, so the path-form match can't
        // silently regress. Only the currently-printing file is rejected; every other idle file proceeds.
        if (!deleteAllowed(selected.relativeFilename, printer.printFilename, printer.printState)) {
            _state.update { it.copy(error = "Cannot delete the file that is currently printing.") }
            return
        }

        // The API still needs the gcodes-prefixed path even though the gate compared the relative form.
        client.deleteFile(path)
        val currentDirectory = state.value.directory
        _state.update {
            it.copy(
                directory = currentDirectory.copy(
                    rows = currentDirectory.rows.filterNot { row -> row.stableId == selected.stableId },
                ),
                selectedFile = null,
                selectedPreview = null,
                error = null,
            )
        }
    }

    private suspend fun loadDirectory(path: String) {
        _state.update { it.copy(loading = true, error = null) }
        val directory = runCatching {
            client.getDirectory(path = path, extended = true)?.jsonObject?.let {
                parseFileBrowserDirectory(it, directoryPath = path)
            }
        }.getOrNull()
        _state.update {
            if (directory == null) {
                it.copy(
                    directory = emptyDirectory(path),
                    selectedFile = null,
                    selectedPreview = null,
                    loading = false,
                    error = "Could not load files.",
                )
            } else {
                it.copy(
                    directory = directory,
                    selectedFile = null,
                    selectedPreview = null,
                    loading = false,
                    error = null,
                )
            }
        }
    }
}

private fun emptyDirectory(path: String = "gcodes"): FileBrowserDirectory =
    parseFileBrowserDirectory(kotlinx.serialization.json.JsonObject(emptyMap()), path)
