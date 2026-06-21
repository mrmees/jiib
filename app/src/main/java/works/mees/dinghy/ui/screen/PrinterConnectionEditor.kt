package works.mees.dinghy.ui.screen

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.config.ConnectionUrls
import works.mees.dinghy.config.DiscoveredPrinter
import works.mees.dinghy.config.HostResult
import works.mees.dinghy.config.Profile
import works.mees.dinghy.config.buildConnectionUrls
import works.mees.dinghy.config.normalizeHost
import works.mees.dinghy.config.resolveAutoSeededOnSave
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.net.ProbeFailure
import works.mees.dinghy.net.ProbeResult
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

/** Bounded settle window for mDNS scan. */
private const val SCAN_WINDOW_MS = 6000L

/** The tappable Field rows; selecting one swaps the Focus into that field's editor. */
private enum class ConnRow { Name, Host, Port, ApiKey, Find, Advanced }

/**
 * Save-time API-key resolution for the connection editor (CR-01).
 *
 * The editor holds a STALE `profile` snapshot (captured at row-tap time), so after an explicit
 * "Clear key" the snapshot still carries the OLD key. Feeding that stale key straight into
 * [AppContainer.resolveApiKeyEdit] silently resurrected a credential the user removed.
 * This helper is the single Save-time source of truth:
 *  - [keyCleared] + blank [fieldInput] → `null` (the clear sticks — nothing resurrects);
 *  - [keyCleared] + non-blank [fieldInput] → the typed key (user cleared, then entered a new one);
 *  - not cleared + blank → [storedKey] (PRESERVE — a save without retyping keeps it);
 *  - not cleared + non-blank → the typed key (REPLACE).
 *
 * Pure + package-level so [works.mees.dinghy.ui.screen] host tests cover it without Compose.
 */
fun resolveEditorKeyOnSave(storedKey: String?, keyCleared: Boolean, fieldInput: String): String? =
    AppContainer.resolveApiKeyEdit(
        existing = if (keyCleared) null else storedKey,
        fieldInput = fieldInput,
        cleared = keyCleared && fieldInput.isBlank(),
    )

/**
 * Pure save-time profile builder for the connection editor (Task 7 — called by Task 8's UI).
 *
 * Centralises all field-resolution logic so the editor UI and tests share one code path:
 *  - trims and blanks-to-null the name and advancedUrl;
 *  - resolves the API key via [resolveEditorKeyOnSave] (CR-01 stale-snapshot guard);
 *  - ALWAYS writes [Profile.useSecure] = false (the toggle is retired — TLS lives in advancedUrl);
 *  - delegates [Profile.nameAutoSeeded] to [resolveAutoSeededOnSave].
 *
 * Pure + package-internal so [works.mees.dinghy.ui.screen] host tests can cover it without Compose.
 */
internal fun buildProfileFromConnectionEditorSave(
    existing: Profile?,
    nameInput: String,
    host: String,
    port: Int,
    apiKeyInput: String,
    keyCleared: Boolean,
    advancedUrlInput: String,
): Profile {
    val cleanName = nameInput.trim().ifBlank { null }
    val resolvedKey = resolveEditorKeyOnSave(
        storedKey = existing?.apiKey,
        keyCleared = keyCleared,
        fieldInput = apiKeyInput,
    )
    val cleanAdvancedUrl = advancedUrlInput.trim().ifBlank { null }
    return if (existing != null) {
        existing.copy(
            name = cleanName,
            host = host.trim(),
            port = port,
            apiKey = resolvedKey,
            useSecure = false,
            advancedUrl = cleanAdvancedUrl,
            nameAutoSeeded = resolveAutoSeededOnSave(cleanName, existing.nameAutoSeeded, existing.name),
        )
    } else {
        Profile(
            id = Profile.newId(),
            name = cleanName,
            host = host.trim(),
            port = port,
            apiKey = resolvedKey,
            useSecure = false,
            advancedUrl = cleanAdvancedUrl,
            nameAutoSeeded = resolveAutoSeededOnSave(cleanName, prior = false, priorName = null),
        )
    }
}

/**
 * The connection editor, rebuilt into the Focus/Field tap-row-to-edit grammar (Connection Editor
 * Redesign, Task 8).
 *
 * The Field lists every connection field as a selectable [ListRow]; tapping one swaps the Focus from
 * a live endpoint/Test summary into that field's inline editor. The foot bar carries Back (contextual:
 * leaves the editor or returns to the row list), Test (runs a dual HTTP+WS probe — [container.runConnectionProbe]),
 * and Save (always enabled; relabels to "Save anyway" after a failed probe).
 *
 * API key semantics (CR-01): never pre-fills the raw key; blank = preserve stored key; "Clear key"
 * writes null. Save routes through [buildProfileFromConnectionEditorSave] so the UI and host tests
 * share one resolution path. The legacy useSecure toggle is RETIRED — TLS lives in the advanced URL.
 */
@Composable
internal fun PrinterConnectionEditor(
    container: AppContainer,
    profile: Profile?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val printerState by container.printerState.collectAsStateWithLifecycle(PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(null)
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val estop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit); Unit }
    val back = stringResource(R.string.common_back)

    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("7125") }
    var apiKey by remember { mutableStateOf("") }
    var advancedUrl by remember { mutableStateOf("") }
    var keyAlreadySaved by remember { mutableStateOf(false) }
    var keyCleared by remember { mutableStateOf(false) }
    var hostError by remember { mutableStateOf<String?>(null) }
    var portError by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<ConnRow?>(null) }
    var probe by remember { mutableStateOf<ProbeResult?>(null) }
    var probing by remember { mutableStateOf(false) }
    var probeJob by remember { mutableStateOf<Job?>(null) }
    var scanRequest by remember { mutableStateOf(0) }
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }

    // Seed from profile on open — NEVER pre-fill raw API key (T-28-06-01 / MEDIUM-5 / V7).
    LaunchedEffect(profile?.id) {
        name = profile?.name ?: ""
        host = profile?.host ?: ""
        port = profile?.port?.toString() ?: "7125"
        apiKey = ""
        advancedUrl = profile?.advancedUrl.orEmpty()
        keyAlreadySaved = profile?.apiKey != null
        keyCleared = false
        hostError = null
        portError = false
        selected = null
        probe = null
        probing = false
        scanned = false
        discovered = emptyList()
    }

    // mDNS scan — bounded LaunchedEffect(scanRequest); no persistence, safe to cancel on nav.
    LaunchedEffect(scanRequest) {
        if (scanRequest == 0) return@LaunchedEffect
        scanning = true
        scanned = false
        discovered = emptyList()
        try {
            withTimeoutOrNull(SCAN_WINDOW_MS) {
                container.discovery.discover().collect { printer ->
                    if (discovered.none { it.host == printer.host && it.port == printer.port }) {
                        discovered = discovered + printer
                    }
                }
            }
        } finally {
            scanning = false
            scanned = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { probeJob?.cancel() }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val uDp = grid.uDp
        val previewPort = port.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: 7125
        val urls = runCatching {
            buildConnectionUrls(
                host = host.trim().ifBlank { "host" },
                port = previewPort,
                advancedUrl = advancedUrl.ifBlank { null },
                useSecure = false,
            )
        }.getOrElse { ConnectionUrls(httpBase = "", wsUrl = "") }

        // Foot actions — Back (contextual) · Test · Save.
        val failedProbe = probe?.let { !it.http.ok || !it.ws.ok } == true
        val saveLabel = if (failedProbe) stringResource(R.string.conn_save_anyway) else stringResource(R.string.common_save)
        val footActions = listOf(
            FootAction(
                label = back,
                icon = DinghyIcons.Back,
                onClick = {
                    when {
                        selected != null -> selected = null
                        else -> onDone()
                    }
                },
                intent = Intent.Accent,
                contentDescription = stringResource(R.string.cd_back),
            ),
            FootAction(
                label = stringResource(R.string.conn_test),
                icon = DinghyIcons.NetworkPing,
                onClick = {
                    val portInt = port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
                    portError = portInt == null
                    when (val normalized = normalizeHost(host)) {
                        is HostResult.Rejected -> hostError = normalized.reason
                        is HostResult.Clean -> if (portInt != null) {
                            host = normalized.host
                            normalized.portOverride?.let { port = it.toString() }
                            hostError = null
                            probing = true
                            probeJob?.cancel()
                            val config = ConnectionConfig(
                                host = normalized.host,
                                port = normalized.portOverride ?: portInt,
                                apiKey = resolveEditorKeyOnSave(profile?.apiKey, keyCleared, apiKey),
                                useSecure = false,
                                advancedUrl = advancedUrl.ifBlank { null },
                            )
                            probeJob = container.runConnectionProbe(config) { result ->
                                probe = result
                                probing = false
                                probeJob = null
                            }
                        }
                    }
                },
                intent = Intent.Accent,
                enabled = host.isNotBlank() && !probing,
            ),
            FootAction(
                label = saveLabel,
                icon = DinghyIcons.CheckCircle,
                onClick = {
                    val portInt = port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
                    portError = portInt == null
                    when (val normalized = normalizeHost(host)) {
                        is HostResult.Rejected -> hostError = normalized.reason
                        is HostResult.Clean -> if (portInt != null) {
                            host = normalized.host
                            normalized.portOverride?.let { port = it.toString() }
                            val saved = buildProfileFromConnectionEditorSave(
                                existing = profile,
                                nameInput = name,
                                host = normalized.host,
                                port = normalized.portOverride ?: portInt,
                                apiKeyInput = apiKey,
                                keyCleared = keyCleared,
                                advancedUrlInput = advancedUrl,
                            )
                            container.saveProfile(saved)
                            apiKey = ""
                            onDone()
                        }
                    }
                },
                intent = Intent.Go,
            ),
        )

        ScreenScaffold(
            focus = {
                ConnFocus(
                    profile = profile,
                    selected = selected,
                    urls = urls,
                    probe = probe,
                    probing = probing,
                    name = name,
                    host = host,
                    port = port,
                    apiKey = apiKey,
                    advancedUrl = advancedUrl,
                    hostError = hostError,
                    portError = portError,
                    keyAlreadySaved = keyAlreadySaved,
                    keyCleared = keyCleared,
                    discovered = discovered,
                    scanning = scanning,
                    scanned = scanned,
                    uDp = uDp,
                    isPrinting = isPrinting,
                    estop = estop,
                    onName = { name = it },
                    onHost = { host = it; hostError = null },
                    onPort = { port = it.filter(Char::isDigit); portError = false },
                    onApiKey = { apiKey = it },
                    onAdvancedUrl = { advancedUrl = it },
                    onClearKey = {
                        apiKey = ""
                        keyAlreadySaved = false
                        keyCleared = true
                    },
                    onScan = { if (!scanning) scanRequest++ },
                    onPick = {
                        host = it.host
                        port = it.port.toString()
                        hostError = null
                        portError = false
                        selected = null
                    },
                    onCommitHost = {
                        when (val normalized = normalizeHost(host)) {
                            is HostResult.Clean -> {
                                host = normalized.host
                                normalized.portOverride?.let { port = it.toString() }
                                hostError = null
                                selected = null
                            }
                            is HostResult.Rejected -> hostError = normalized.reason
                        }
                    },
                    onDone = { selected = null },
                )
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    item {
                        ConnListRow(ConnRow.Name, DinghyIcons.TextFields, stringResource(R.string.conn_row_name), name.ifBlank { stringResource(R.string.conn_row_name) }, selected, uDp) {
                            selected = ConnRow.Name
                        }
                    }
                    item {
                        ConnListRow(ConnRow.Host, DinghyIcons.SysInfoCpu, stringResource(R.string.conn_row_host), host.ifBlank { "-" }, selected, uDp) {
                            selected = ConnRow.Host
                        }
                    }
                    item {
                        ConnListRow(ConnRow.Port, DinghyIcons.Numbers, stringResource(R.string.conn_row_port), port, selected, uDp) {
                            selected = ConnRow.Port
                        }
                    }
                    item {
                        ConnListRow(
                            ConnRow.ApiKey,
                            DinghyIcons.VpnKey,
                            stringResource(R.string.conn_row_apikey),
                            if (keyAlreadySaved && !keyCleared) stringResource(R.string.conn_apikey_set) else stringResource(R.string.conn_apikey_unset),
                            selected,
                            uDp,
                        ) {
                            selected = ConnRow.ApiKey
                        }
                    }
                    item {
                        ConnListRow(ConnRow.Find, DinghyIcons.Search, stringResource(R.string.conn_row_find), "", selected, uDp) {
                            selected = ConnRow.Find
                        }
                    }
                    item {
                        ConnListRow(ConnRow.Advanced, DinghyIcons.LauncherCalibration, stringResource(R.string.conn_row_advanced), advancedUrl.ifBlank { "-" }, selected, uDp) {
                            selected = ConnRow.Advanced
                        }
                    }
                }
                FootButtonBar(uDp = grid.uDp, actions = footActions)
            },
        )
    }
}

/** A selectable Field row that surfaces a connection field + its current value. */
@Composable
private fun ConnListRow(
    row: ConnRow,
    icon: DinghyIcon,
    label: String,
    value: String,
    selected: ConnRow?,
    uDp: Dp,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    ListRow(
        selected = selected == row,
        onClick = onClick,
        uDp = uDp,
        leadingContent = { ListRowIcon(icon, uDp, t.text) },
        trailingContent = {
            Text(
                text = value,
                color = t.text2,
                style = DinghyType.caption.toTextStyle(t),
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
        },
    ) {
        Text(
            text = label,
            color = t.text,
            style = DinghyType.listLabel.toTextStyle(t),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
        )
    }
}

/** The Focus: an endpoint/Test summary at rest, or the selected field's inline editor. */
@Composable
private fun ConnFocus(
    profile: Profile?,
    selected: ConnRow?,
    urls: ConnectionUrls,
    probe: ProbeResult?,
    probing: Boolean,
    name: String,
    host: String,
    port: String,
    apiKey: String,
    advancedUrl: String,
    hostError: String?,
    portError: Boolean,
    keyAlreadySaved: Boolean,
    keyCleared: Boolean,
    discovered: List<DiscoveredPrinter>,
    scanning: Boolean,
    scanned: Boolean,
    uDp: Dp,
    isPrinting: Boolean,
    estop: () -> Unit,
    onName: (String) -> Unit,
    onHost: (String) -> Unit,
    onPort: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onAdvancedUrl: (String) -> Unit,
    onClearKey: () -> Unit,
    onScan: () -> Unit,
    onPick: (DiscoveredPrinter) -> Unit,
    onCommitHost: () -> Unit,
    onDone: () -> Unit,
) {
    FocusFrame(
        title = profile?.let { stringResource(R.string.conn_title_edit, it.displayName()) }
            ?: stringResource(R.string.conn_title_add),
        icon = DinghyIcons.SystemRowPrinters,
        uDp = uDp,
        modifier = Modifier.fillMaxSize(),
        isPrinting = isPrinting,
        onEmergencyStop = estop,
        onPanic = estop,
    ) {
        when (selected) {
            null -> ConnSummary(urls = urls, probe = probe, probing = probing, uDp = uDp)
            ConnRow.Name -> ConnTextEditor(
                value = name,
                onChange = onName,
                label = stringResource(R.string.conn_row_name),
                keyboard = KeyboardType.Text,
                onDone = onDone,
            )
            ConnRow.Host -> ConnTextEditor(
                value = host,
                onChange = onHost,
                label = stringResource(R.string.conn_row_host),
                keyboard = KeyboardType.Text,
                warning = hostError ?: if (host.trim().endsWith(".local")) stringResource(R.string.conn_local_warning) else null,
                isError = hostError != null,
                onDone = onCommitHost,
            )
            ConnRow.Port -> ConnTextEditor(
                value = port,
                onChange = onPort,
                label = stringResource(R.string.conn_row_port),
                keyboard = KeyboardType.Number,
                isError = portError,
                warning = if (portError) stringResource(R.string.printers_error_port_range) else null,
                onDone = onDone,
            )
            ConnRow.ApiKey -> ConnTextEditor(
                value = apiKey,
                onChange = onApiKey,
                label = stringResource(R.string.conn_row_apikey),
                keyboard = KeyboardType.Password,
                isPassword = true,
                warning = if (keyAlreadySaved && !keyCleared && apiKey.isBlank()) stringResource(R.string.conn_apikey_set) else stringResource(R.string.conn_apikey_hint),
                secondaryAction = if (keyAlreadySaved && !keyCleared) {
                    { OutlinedControl(label = stringResource(R.string.printers_clear_key), onClick = onClearKey, icon = null, intent = Intent.Danger, modifier = Modifier.fillMaxWidth()) }
                } else {
                    null
                },
                onDone = onDone,
            )
            ConnRow.Advanced -> ConnTextEditor(
                value = advancedUrl,
                onChange = onAdvancedUrl,
                label = stringResource(R.string.conn_row_advanced),
                keyboard = KeyboardType.Uri,
                onDone = onDone,
            )
            ConnRow.Find -> ConnFindPanel(
                discovered = discovered,
                scanning = scanning,
                scanned = scanned,
                uDp = uDp,
                onScan = onScan,
                onPick = onPick,
            )
        }
    }
}

/** A single-field inline editor: a [TokenTextField], an optional warning + secondary action, a Done button. */
@Composable
private fun ConnTextEditor(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    warning: String? = null,
    isError: Boolean = false,
    secondaryAction: (@Composable () -> Unit)? = null,
    onDone: () -> Unit,
) {
    val t = LocalTokens.current
    Column(modifier.fillMaxSize()) {
        TokenTextField(
            value = value,
            onValueChange = onChange,
            label = label,
            keyboardType = keyboard,
            isPassword = isPassword,
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
        )
        if (warning != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = warning,
                color = if (isError) t.stop else t.text2,
                style = DinghyType.caption.toTextStyle(t),
            )
        }
        secondaryAction?.let {
            Spacer(Modifier.height(8.dp))
            it()
        }
        Spacer(Modifier.weight(1f))
        OutlinedControl(
            label = stringResource(R.string.common_done),
            onClick = onDone,
            icon = DinghyIcons.CheckCircle,
            intent = Intent.Go,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The Find panel: a Scan button + a list of discovered printers (tap to fill host/port). */
@Composable
private fun ConnFindPanel(
    discovered: List<DiscoveredPrinter>,
    scanning: Boolean,
    scanned: Boolean,
    uDp: Dp,
    onScan: () -> Unit,
    onPick: (DiscoveredPrinter) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedControl(
            label = if (scanning) stringResource(R.string.printers_scanning) else stringResource(R.string.conn_scan),
            onClick = onScan,
            icon = null,
            intent = Intent.Accent,
            enabled = !scanning,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (scanned && !scanning && discovered.isEmpty()) {
            Text(
                text = stringResource(R.string.conn_scan_empty),
                color = LocalTokens.current.text2,
                style = DinghyType.caption.toTextStyle(LocalTokens.current),
            )
        } else {
            ListBlock(modifier = Modifier.weight(1f)) {
                items(discovered, key = { "${it.host}:${it.port}" }) { printer ->
                    ConnListRow(
                        row = ConnRow.Find,
                        icon = DinghyIcons.SysInfoCpu,
                        label = printer.host,
                        value = printer.port.toString(),
                        selected = null,
                        uDp = uDp,
                    ) {
                        onPick(printer)
                    }
                }
            }
        }
    }
}

/** The at-rest Focus: the live endpoint preview + the most recent Test (probe) result. */
@Composable
private fun ConnSummary(
    urls: ConnectionUrls,
    probe: ProbeResult?,
    probing: Boolean,
    uDp: Dp,
) {
    val t = LocalTokens.current
    Column(Modifier.fillMaxSize()) {
        Text(
            text = urls.wsUrl.ifBlank { "-" },
            color = t.text,
            style = DinghyType.dataMeta.toTextStyle(t),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
        )
        Spacer(Modifier.height(12.dp))
        when {
            probing -> Text(text = stringResource(R.string.conn_test), color = t.text2, style = DinghyType.caption.toTextStyle(t))
            probe == null -> Text(text = stringResource(R.string.conn_test_untested), color = t.text2, style = DinghyType.caption.toTextStyle(t))
            else -> {
                ProbeLine(stringResource(R.string.conn_test_http), probe.http.ok, probe.http.failure, uDp)
                ProbeLine(stringResource(R.string.conn_test_ws), probe.ws.ok, probe.ws.failure, uDp)
            }
        }
    }
}

/** One transport's probe result line: a pass/fail glyph + a labelled outcome. */
@Composable
private fun ProbeLine(label: String, ok: Boolean, failure: ProbeFailure?, uDp: Dp) {
    val t = LocalTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().height(uDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DinghyIconView(
            icon = if (ok) DinghyIcons.CheckCircle else DinghyIcons.XCircle,
            tint = if (ok) t.go else t.stop,
            sizeDp = uDp * 0.5f,
            contentDescription = label,
        )
        Text(
            text = if (ok) label else "$label: ${stringResource(failure.toMessageRes())}",
            color = if (ok) t.text else t.stop,
            style = DinghyType.caption.toTextStyle(t),
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp).basicMarquee(),
        )
    }
}

private fun ProbeFailure?.toMessageRes(): Int = when (this) {
    ProbeFailure.Timeout -> R.string.conn_fail_timeout
    ProbeFailure.Refused -> R.string.conn_fail_refused
    ProbeFailure.Unauthorized -> R.string.conn_fail_unauthorized
    ProbeFailure.Certificate -> R.string.conn_fail_cert
    ProbeFailure.Unknown, null -> R.string.conn_fail_unknown
}
