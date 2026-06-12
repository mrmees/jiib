package works.mees.dinghy.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.withTimeoutOrNull
import works.mees.dinghy.R
import works.mees.dinghy.config.DiscoveredPrinter
import works.mees.dinghy.config.Profile
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.components.DetailCard
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

// =============================================================================
// Pure mode-toggle state machine (28-06, D-13)
//
// These are package-level symbols — importable by PrintersModeToggleTest without
// pulling in any Compose/Android runtime. The composable below consumes them via
// `remember { mutableStateOf(PrinterMode.Normal) }` only.
// =============================================================================

/**
 * The three operating modes of the Printers screen foot bar (D-13).
 *
 *  - [Normal]      — row tap switches the active printer; Edit+Delete are outline (unarmed).
 *  - [EditArmed]   — row tap opens the inline connection editor; Edit button is highlighted.
 *  - [DeleteArmed] — row tap raises a [ConfirmGuard]; Delete button is filled stop (C4 rule).
 */
enum class PrinterMode { Normal, EditArmed, DeleteArmed }

/**
 * The effect produced when the user taps a profile row.  Pure — no composable symbols.
 */
enum class RowTapEffect { SwitchActive, OpenEditor, RequestDelete }

/** Arm Edit (or disarm if already EditArmed, or switch from DeleteArmed). */
fun armEdit(current: PrinterMode): PrinterMode = when (current) {
    PrinterMode.EditArmed -> PrinterMode.Normal   // tap again → disarm
    else -> PrinterMode.EditArmed
}

/** Arm Delete (or disarm if already DeleteArmed, or switch from EditArmed). */
fun armDelete(current: PrinterMode): PrinterMode = when (current) {
    PrinterMode.DeleteArmed -> PrinterMode.Normal // tap again → disarm
    else -> PrinterMode.DeleteArmed
}

/** Disarm to Normal (Back key handler). */
fun disarm(): PrinterMode = PrinterMode.Normal

/** Map a row tap under [mode] to one of three outcomes. */
fun rowTapEffect(mode: PrinterMode): RowTapEffect = when (mode) {
    PrinterMode.Normal      -> RowTapEffect.SwitchActive
    PrinterMode.EditArmed   -> RowTapEffect.OpenEditor
    PrinterMode.DeleteArmed -> RowTapEffect.RequestDelete
}

// =============================================================================
// Stateless content seam (WARNING-5 preview convention) — @Preview targets this.
// =============================================================================

/**
 * Stateless Printers layout — the `@Preview` matrix targets this composable (no VM, no live
 * Moonraker). Drives all four preview axes: Normal / Edit-armed / Delete-armed / Empty.
 *
 * @param profiles       the printer profile list to display.
 * @param activeId       the id of the currently-active profile (null = no active profile).
 * @param connectionState the current [ConnectionState] for the active printer.
 * @param printerMode    the current [PrinterMode] for the foot bar (preview injects specific modes).
 * @param onRowClick     row click handler.
 * @param onAdd          Add button click handler.
 * @param onArmEdit      Edit foot button click handler.
 * @param onArmDelete    Delete foot button click handler.
 * @param onBack         Back foot button click handler.
 * @param modifier       caller-supplied modifier.
 */
@Composable
fun PrintersContent(
    profiles: List<works.mees.dinghy.config.Profile>,
    activeId: String?,
    connectionState: ConnectionState,
    printerMode: PrinterMode,
    onRowClick: (works.mees.dinghy.config.Profile) -> Unit,
    onAdd: () -> Unit,
    onArmEdit: () -> Unit,
    onArmDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    val ringColor: Color? = when (connectionState) {
        ConnectionState.Connected    -> t.accent
        ConnectionState.Connecting   -> t.heat
        ConnectionState.Syncing      -> t.heat
        is ConnectionState.Error     -> t.stop
        ConnectionState.Disconnected -> null
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val activeProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

        ScreenScaffold(
            focus = {
                if (activeProfile != null) {
                    DetailCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        ringColor = ringColor,
                    ) {
                        Text(
                            text = activeProfile.displayName(),
                            color = t.text,
                            fontFamily = Geist,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = fsSp(20f, t.fs).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${activeProfile.host}:${activeProfile.port}",
                            color = t.text2,
                            fontFamily = GeistMono,
                            fontSize = fsSp(15f, t.fs).sp,
                        )
                        Text(
                            text = connectionState.label(),
                            color = ringColor ?: t.text2,
                            fontFamily = Geist,
                            fontSize = fsSp(15f, t.fs).sp,
                        )
                    }
                }
            },
            field = {
                if (profiles.isEmpty()) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.printers_empty_headline),
                                color = t.text,
                                fontFamily = Geist,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = fsSp(17f, t.fs).sp,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.printers_empty_body),
                                color = t.text2,
                                fontFamily = Geist,
                                fontSize = fsSp(15f, t.fs).sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                } else {
                    ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        items(profiles, key = { it.id }) { profile ->
                            ListRow(
                                selected = profile.id == activeId,
                                onClick = { onRowClick(profile) },
                                uDp = grid.uDp,
                            ) {
                                Text(
                                    text = profile.displayName(),
                                    color = t.text,
                                    fontFamily = Geist,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = fsSp(17f, t.fs).sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${profile.host}:${profile.port}",
                                    color = t.text2,
                                    fontFamily = GeistMono,
                                    fontSize = fsSp(15f, t.fs).sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                FootButtonBar(
                    uDp = grid.uDp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    OutlinedControl(
                        label = stringResource(R.string.printers_add),
                        onClick = onAdd,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent,
                    )
                    OutlinedControl(
                        label = stringResource(R.string.printers_edit),
                        onClick = onArmEdit,
                        modifier = Modifier.weight(1f),
                        intent = if (printerMode == PrinterMode.EditArmed) Intent.Accent else Intent.Neutral,
                    )
                    OutlinedControl(
                        label = stringResource(R.string.printers_delete),
                        onClick = onArmDelete,
                        modifier = Modifier.weight(1f),
                        intent = if (printerMode == PrinterMode.DeleteArmed) Intent.Danger else Intent.Neutral,
                    )
                    OutlinedControl(
                        label = stringResource(R.string.common_back),
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Neutral,
                    )
                }
            },
            gutter = null,
        )
    }
}

// =============================================================================
// Printers screen (rebuilt 28-06, D-13/D-15)
// =============================================================================

/**
 * The **Printers** surface — rebuilt on the jiib kit (28-06) with the R4 Edit/Delete mode-toggle
 * foot bar (D-13) and the D-15 Focus/Field/Foot layout.
 *
 * - **Focus** = active printer [DetailCard] (name, host:port, connection-state ring, Klippy state).
 * - **Field** = dense profile-row [ListBlock]; active row accent-tinted; row tap dispatched by mode.
 * - **Foot** = [FootButtonBar] (1U): Add(Accent) / Edit(Neutral↔highlighted) / Delete(Neutral↔filled
 *   stop, C4) / Back(Neutral).
 *
 * ## Mode-toggle (D-13)
 *  - Edit armed: row tap opens the inline connection editor; Edit foot highlighted.
 *  - Delete armed: row tap raises [ConfirmGuard]; Delete foot filled stop.
 *  - Tap armed toggle again or Back → Normal.
 *
 * ## Write-scope law ([[dinghy-compose-write-scope-cancellation]])
 * EVERY persist routes through [AppContainer] intent helpers — process-scoped writeScope only.
 *
 * ## Security (T-28-06-01)
 * The API key field NEVER pre-fills the raw stored key. Blank = preserve stored key;
 * explicit Clear = write null ([AppContainer.resolveApiKeyEdit]).
 */
@Composable
fun PrintersScreen(
    container: AppContainer,
    onAddPrinter: () -> Unit = {},
    onSwitched: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val profiles by container.profileStore.profiles.collectAsStateWithLifecycle(emptyList())
    val activeId by container.profileStore.activeId.collectAsStateWithLifecycle(null)
    val connectionState by container.connectionState.collectAsStateWithLifecycle(ConnectionState.Disconnected)

    // Mode-toggle state machine (28-06 D-13) — pure PrinterMode driven by foot bar buttons + BackHandler.
    var printerMode by remember { mutableStateOf(PrinterMode.Normal) }
    // Inline editor target: null = list view; Some(profile) = editing; EditorTarget.New = adding.
    var editingTarget by remember { mutableStateOf<EditorTarget?>(null) }
    // Pending delete for ConfirmGuard.
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }

    // BackHandler: disarm mode if armed; else dismiss editor/confirm if open.
    BackHandler(printerMode != PrinterMode.Normal || editingTarget != null) {
        when {
            editingTarget != null -> editingTarget = null
            else -> printerMode = disarm()
        }
    }

    // ConfirmGuard for Delete-mode row tap.
    pendingDelete?.let { victim ->
        ConfirmGuard(
            title = stringResource(R.string.printers_delete_confirm_title),
            message = stringResource(R.string.printers_delete_confirm_body),
            confirmLabel = stringResource(R.string.printers_delete),
            cancelLabel = stringResource(R.string.common_back),
            onConfirm = {
                container.deleteProfile(victim.id)
                pendingDelete = null
                printerMode = disarm()
            },
            onCancel = { pendingDelete = null },
            destructive = true,
        )
        return
    }

    // Inline connection editor (Edit mode row tap or Add).
    val target = editingTarget
    if (target != null) {
        PrinterConnectionEditor(
            container = container,
            profile = if (target is EditorTarget.Edit) target.profile else null,
            onDone = {
                editingTarget = null
                // Stay in EditArmed after saving so the user can continue editing other printers.
            },
            modifier = modifier,
        )
        return
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        // Active-printer data for the Focus card.
        val activeProfile = profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()

        // Connection-state ring color (THEME-01 data carve-out — raw color not brandTint-clamped).
        val ringColor: Color? = when (connectionState) {
            ConnectionState.Connected    -> t.accent
            ConnectionState.Connecting   -> t.heat
            ConnectionState.Syncing      -> t.heat
            is ConnectionState.Error     -> t.stop
            ConnectionState.Disconnected -> null
        }

        ScreenScaffold(
            focus = {
                if (activeProfile != null) {
                    DetailCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        ringColor = ringColor,
                    ) {
                        Text(
                            text = activeProfile.displayName(),
                            color = t.text,
                            fontFamily = Geist,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = fsSp(20f, t.fs).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${activeProfile.host}:${activeProfile.port}",
                            color = t.text2,
                            fontFamily = GeistMono,
                            fontSize = fsSp(15f, t.fs).sp,
                        )
                        Text(
                            text = connectionState.label(),
                            color = ringColor ?: t.text2,
                            fontFamily = Geist,
                            fontSize = fsSp(15f, t.fs).sp,
                        )
                    }
                }
            },
            field = {
                if (profiles.isEmpty()) {
                    // Empty state.
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(R.string.printers_empty_headline),
                                color = t.text,
                                fontFamily = Geist,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = fsSp(17f, t.fs).sp,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.printers_empty_body),
                                color = t.text2,
                                fontFamily = Geist,
                                fontSize = fsSp(15f, t.fs).sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                } else {
                    ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        items(profiles, key = { it.id }) { profile ->
                            ListRow(
                                selected = profile.id == activeId,
                                onClick = {
                                    when (rowTapEffect(printerMode)) {
                                        RowTapEffect.SwitchActive -> {
                                            container.setActiveProfile(profile.id)
                                            onSwitched()
                                        }
                                        RowTapEffect.OpenEditor -> {
                                            editingTarget = EditorTarget.Edit(profile)
                                        }
                                        RowTapEffect.RequestDelete -> {
                                            pendingDelete = profile
                                        }
                                    }
                                },
                                uDp = grid.uDp,
                            ) {
                                Text(
                                    text = profile.displayName(),
                                    color = t.text,
                                    fontFamily = Geist,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = fsSp(17f, t.fs).sp,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${profile.host}:${profile.port}",
                                    color = t.text2,
                                    fontFamily = GeistMono,
                                    fontSize = fsSp(15f, t.fs).sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
                FootButtonBar(
                    uDp = grid.uDp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    OutlinedControl(
                        label = stringResource(R.string.printers_add),
                        onClick = { editingTarget = EditorTarget.New },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent,
                    )
                    OutlinedControl(
                        label = stringResource(R.string.printers_edit),
                        onClick = { printerMode = armEdit(printerMode) },
                        modifier = Modifier.weight(1f),
                        intent = if (printerMode == PrinterMode.EditArmed) Intent.Accent else Intent.Neutral,
                    )
                    OutlinedControl(
                        label = stringResource(R.string.printers_delete),
                        onClick = { printerMode = armDelete(printerMode) },
                        modifier = Modifier.weight(1f),
                        intent = if (printerMode == PrinterMode.DeleteArmed) Intent.Danger else Intent.Neutral,
                    )
                    OutlinedControl(
                        label = stringResource(R.string.common_back),
                        onClick = {
                            if (printerMode != PrinterMode.Normal) {
                                printerMode = disarm()
                            } else {
                                onBack()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Neutral,
                    )
                }
            },
            gutter = null,
        )
    }
}

// =============================================================================
// Connection editor (inline — opened from Edit mode or Add)
// =============================================================================

/** Which profile the Connection editor targets — an existing one to [Edit], or a blank [New] printer. */
private sealed interface EditorTarget {
    data class Edit(val profile: Profile) : EditorTarget
    data object New : EditorTarget
}

/**
 * The inline densified connection editor (28-06, D-12/D-15).
 *
 * Fields: Host (text) / Port (numeric keyboard) / API key (masked). Persists through
 * [AppContainer] writeScope intent helpers only (process-lifetime scope — no composition scope writes).
 * API key semantics (T-28-06-01): never pre-fills raw key; blank = preserve stored key;
 * "Clear key" button = write null via [AppContainer.resolveApiKeyEdit].
 */
@Composable
private fun PrinterConnectionEditor(
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
        hostError = false
        portError = false
        scanned = false
        discovered = emptyList()
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (profile != null) stringResource(R.string.printers_edit) else stringResource(R.string.printers_add),
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(20f, t.fs).sp,
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
                text = "Host is required.",
                color = t.stop,
                fontFamily = GeistMono,
                fontSize = fsSp(15f, t.fs).sp,
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
                text = "Port must be 1–65535.",
                color = t.stop,
                fontFamily = GeistMono,
                fontSize = fsSp(15f, t.fs).sp,
            )
        }

        TokenTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = if (keyAlreadySaved)
                "API key (leave blank to keep saved key)"
            else
                stringResource(R.string.printers_edit_key),
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Password,
            isPassword = true,
        )
        if (keyAlreadySaved && apiKey.isBlank()) {
            Text(
                text = "Key saved",
                color = t.go,
                fontFamily = GeistMono,
                fontSize = fsSp(15f, t.fs).sp,
            )
        }

        // R7 (26.5-07): per-printer wss/https toggle.
        SecureToggleRow(
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
                label = if (scanning) "Scanning…" else "Scan (mDNS)",
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
                    label = "Clear key",
                    onClick = {
                        profile?.let { p ->
                            container.saveProfile(
                                p.copy(apiKey = AppContainer.resolveApiKeyEdit(p.apiKey, apiKey, cleared = true)),
                            )
                        }
                        apiKey = ""
                        keyAlreadySaved = false
                    },
                    modifier = Modifier.weight(1f),
                    intent = Intent.Danger,
                )
            }
        }

        if (scanned && discovered.isEmpty()) {
            Text(
                text = "No printers found — enter the host manually.",
                color = t.text2,
                fontFamily = GeistMono,
                fontSize = fsSp(15f, t.fs).sp,
            )
        }
        for (printer in discovered) {
            DiscoveredPrinterRow(
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
            label = "Save",
            onClick = {
                val portInt = port.trim().toIntOrNull()
                val blankHost = host.isBlank()
                val badPort = portInt == null || portInt !in 1..65535
                hostError = blankHost
                portError = badPort
                if (!blankHost && !badPort) {
                    val resolvedKey = AppContainer.resolveApiKeyEdit(
                        existing = profile?.apiKey,
                        fieldInput = apiKey,
                        cleared = false,
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

/** Bounded settle window for mDNS scan. */
private const val SCAN_WINDOW_MS = 6000L

/**
 * Returns a human-readable label for the connection state (used in the Focus DetailCard).
 */
private fun ConnectionState.label(): String = when (this) {
    ConnectionState.Connected    -> "Connected"
    ConnectionState.Connecting   -> "Connecting…"
    ConnectionState.Syncing      -> "Syncing…"
    is ConnectionState.Error     -> "Error"
    ConnectionState.Disconnected -> "Disconnected"
}

/**
 * The R7 (26.5-07) useSecure toggle row — preserved from the original PrintersScreen.
 */
@Composable
private fun SecureToggleRow(
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
            .clip(shape)
            .border(BorderStroke(2.dp, outline), shape)
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(17f, t.fs).sp,
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = t.text2,
                    fontFamily = Geist,
                    fontSize = fsSp(15f, t.fs).sp,
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
                text = if (checked) "ON" else "OFF",
                color = if (checked) t.accent else t.text2,
                fontFamily = Geist,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(17f, t.fs).sp,
            )
        }
    }
}

/** A discovered-printer row (mDNS), local to the connection editor. */
@Composable
private fun DiscoveredPrinterRow(
    printer: DiscoveredPrinter,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = printer.name,
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(17f, t.fs).sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${printer.host}:${printer.port}",
            color = t.text2,
            fontFamily = GeistMono,
            fontSize = fsSp(15f, t.fs).sp,
        )
    }
}
