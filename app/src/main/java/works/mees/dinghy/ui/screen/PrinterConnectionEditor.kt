package works.mees.dinghy.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import works.mees.dinghy.R
import works.mees.dinghy.config.DiscoveredPrinter
import works.mees.dinghy.config.Profile
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

/** Bounded settle window for mDNS scan. */
private const val SCAN_WINDOW_MS = 6000L

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
 * The inline densified connection editor (28-06, D-12/D-15).
 *
 * Fields: Host (text) / Port (numeric keyboard) / API key (masked). Persists through
 * [AppContainer] writeScope intent helpers only (process-lifetime scope — no composition scope writes).
 * API key semantics (T-28-06-01): never pre-fills raw key; blank = preserve stored key;
 * "Clear key" button = write null via [AppContainer.resolveApiKeyEdit].
 */
@Composable
internal fun PrinterConnectionEditor(
    container: AppContainer,
    profile: Profile?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("7125") }
    var apiKey by remember { mutableStateOf("") }
    var useSecure by remember { mutableStateOf(false) }
    var keyAlreadySaved by remember { mutableStateOf(false) }
    // CR-01: an explicit "Clear key" must survive until Save — the `profile` param is a STALE
    // snapshot whose old key would otherwise resurrect through resolveApiKeyEdit's preserve path.
    var keyCleared by remember { mutableStateOf(false) }
    var hostError by remember { mutableStateOf(false) }
    var portError by remember { mutableStateOf(false) }

    // mDNS scan state — LaunchedEffect(scanRequest) triggered by button tap (write-scope law).
    var scanRequest by remember { mutableStateOf(0) }  // incremented to trigger a scan
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }

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

    // Seed from profile on open — NEVER pre-fill raw API key (T-28-06-01 / MEDIUM-5 / V7).
    LaunchedEffect(profile?.id) {
        host = profile?.host ?: ""
        port = profile?.port?.toString() ?: "7125"
        apiKey = ""
        useSecure = profile?.useSecure ?: false
        keyAlreadySaved = profile?.apiKey != null
        keyCleared = false
        hostError = false
        portError = false
        scanned = false
        discovered = emptyList()
    }

    // WR-08 (GAP-A 'All 1U' ruling): the editor is a plain Column with no grid, so derive the
    // unit here — the tappable rows below need the 1U heightIn floor like every sibling surface.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (profile != null) stringResource(R.string.printers_edit) else stringResource(R.string.printers_add),
                color = t.text,
                style = DinghyType.focusHeader.toTextStyle(t),
            )

            TokenTextField(
                value = host,
                onValueChange = { host = it; hostError = false },
                label = stringResource(R.string.printers_edit_host),
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Text,
                isError = hostError,
            )
            if (hostError) {
                Text(
                    text = stringResource(R.string.printers_error_host_required),
                    color = t.stop,
                    style = DinghyType.caption.toTextStyle(t),
                )
            }

            TokenTextField(
                value = port,
                onValueChange = { port = it; portError = false },
                label = stringResource(R.string.printers_edit_port),
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number,
                isError = portError,
            )
            if (portError) {
                Text(
                    text = stringResource(R.string.printers_error_port_range),
                    color = t.stop,
                    style = DinghyType.caption.toTextStyle(t),
                )
            }

            TokenTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = if (keyAlreadySaved) {
                    stringResource(R.string.printers_edit_key_keep_saved)
                } else {
                    stringResource(R.string.printers_edit_key)
                },
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Password,
                isPassword = true,
            )
            if (keyAlreadySaved && apiKey.isBlank()) {
                Text(
                    text = stringResource(R.string.printers_key_saved),
                    color = t.go,
                    style = DinghyType.caption.toTextStyle(t),
                )
            }

            // R7 (26.5-07): per-printer wss/https toggle.
            SecureToggleRow(
                uDp = grid.uDp,
                label = stringResource(R.string.printers_use_secure),
                subLabel = if (useSecure) {
                    stringResource(R.string.printers_use_secure_sub_on)
                } else {
                    stringResource(R.string.printers_use_secure_sub_off)
                },
                checked = useSecure,
                onToggle = { useSecure = it },
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedControl(
                    label = if (scanning) stringResource(R.string.printers_scanning) else stringResource(R.string.printers_scan_mdns),
                    onClick = {
                        if (!scanning) {
                            scanRequest++ // triggers LaunchedEffect(scanRequest)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    intent = Intent.Accent,
                )
                if (keyAlreadySaved) {
                    OutlinedControl(
                        label = stringResource(R.string.printers_clear_key),
                        onClick = {
                            profile?.let { p ->
                                container.saveProfile(
                                    p.copy(apiKey = AppContainer.resolveApiKeyEdit(p.apiKey, apiKey, cleared = true)),
                                )
                            }
                            apiKey = ""
                            keyAlreadySaved = false
                            // CR-01: remember the clear locally — the stale `profile` snapshot still
                            // carries the old key, and Save must NOT resurrect it.
                            keyCleared = true
                        },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Danger,
                    )
                }
            }

            if (scanned && discovered.isEmpty()) {
                Text(
                    text = stringResource(R.string.printers_scan_none_found),
                    color = t.text2,
                    style = DinghyType.caption.toTextStyle(t),
                )
            }
            for (printer in discovered) {
                DiscoveredPrinterRow(
                    uDp = grid.uDp,
                    printer = printer,
                    onClick = {
                        host = printer.host
                        port = printer.port.toString()
                        hostError = false
                        portError = false
                    },
                )
            }

            OutlinedControl(
                label = stringResource(R.string.common_save),
                onClick = {
                    val portInt = port.trim().toIntOrNull()
                    val blankHost = host.isBlank()
                    val badPort = portInt == null || portInt !in 1..65535
                    hostError = blankHost
                    portError = badPort
                    if (!blankHost && !badPort) {
                        // CR-01: Save-time resolution honors a prior "Clear key" — the stale snapshot's
                        // old key must never resurrect through the blank-field preserve path.
                        val resolvedKey = resolveEditorKeyOnSave(
                            storedKey = profile?.apiKey,
                            keyCleared = keyCleared,
                            fieldInput = apiKey,
                        )
                        val next = if (profile != null) {
                            profile.copy(
                                host = host.trim(),
                                port = portInt!!,
                                apiKey = resolvedKey,
                                useSecure = useSecure,
                            )
                        } else {
                            Profile(
                                id = Profile.newId(),
                                name = null,
                                host = host.trim(),
                                port = portInt!!,
                                apiKey = resolvedKey,
                                useSecure = useSecure,
                            )
                        }
                        container.saveProfile(next)
                        apiKey = ""
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                intent = Intent.Go,
            )

            OutlinedControl(
                label = stringResource(R.string.common_back),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
                intent = Intent.Neutral,
            )
        }
    }
}

/**
 * The R7 (26.5-07) useSecure toggle row — preserved from the original PrintersScreen.
 */
@Composable
private fun SecureToggleRow(
    uDp: Dp,
    label: String,
    subLabel: String?,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val outline = if (checked) t.accentLine else t.outline
    Row(
        Modifier
            .fillMaxWidth()
            // WR-08: 1U touch floor (GAP-A "All 1U" ruling) — fixed vertical padding alone fell
            // below the floor at S text size.
            .heightIn(min = uDp)
            .clip(shape)
            .border(BorderStroke(2.dp, outline), shape)
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = t.text,
                style = DinghyType.listLabel.toTextStyle(t),
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = t.text2,
                    style = DinghyType.caption.toTextStyle(t),
                )
            }
        }
        val pillShape = RoundedCornerShape(t.rPill)
        Box(
            Modifier
                .clip(pillShape)
                .border(BorderStroke(2.dp, if (checked) t.accentLine else t.outline), pillShape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = if (checked) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                color = if (checked) t.accent else t.text2,
                style = DinghyType.buttonLabel.toTextStyle(t),
            )
        }
    }
}

/** A discovered-printer row (mDNS), local to the connection editor. */
@Composable
private fun DiscoveredPrinterRow(
    uDp: Dp,
    printer: DiscoveredPrinter,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        Modifier
            .fillMaxWidth()
            // WR-08: 1U touch floor (GAP-A "All 1U" ruling).
            .heightIn(min = uDp)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = printer.name,
            color = t.text,
            style = DinghyType.listLabel.toTextStyle(t),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${printer.host}:${printer.port}",
            color = t.text2,
            style = DinghyType.dataMeta.toTextStyle(t),
        )
    }
}
