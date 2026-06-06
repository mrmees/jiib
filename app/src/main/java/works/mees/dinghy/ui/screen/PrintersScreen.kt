package works.mees.dinghy.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import works.mees.dinghy.config.DiscoveredPrinter
import works.mees.dinghy.config.Profile
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The **Printers** surface (15.2-03, D-02) — the SINGLE per-printer management screen. It is the
 * RENAMED, EXTENDED former `DevicesScreen` (LOW-1: rename-in-place, NOT a divergent copy): the printer
 * switcher tile-grid is carried VERBATIM, and it now also ABSORBS the Connection (host/port/key) editing
 * that used to live in `SettingsScreen` (D-02 — Connection moves OUT of Settings). Printers thus owns
 * add / remove / switch AND connection, killing the Connection-redundant-with-Devices problem the owner
 * flagged in Phase-15 UAT.
 *
 * Two modes, owned by [editingTarget] (a local back-stack like SettingsScreen's editor seam):
 *  - **Grid mode** (default) — the full-screen Field of square printer tiles (one per saved [Profile], the
 *    ACTIVE one accent-emphasised) + an "Add printer" tile. Tapping a tile PERSISTS it active and signals
 *    [onSwitched]; tapping the active tile's edit affordance (or "Add printer") enters editor mode.
 *  - **Editor mode** — the absorbed Connection form (host/port/apiKey + mDNS scan) for ONE profile
 *    (`Some(profile)` edits it, `New` is a blank new-printer form). A [BackHandler] returns to the grid.
 *
 * ## Write-scope law ([[dinghy-compose-write-scope-cancellation]])
 * EVERY persist routes through the process-lifetime [AppContainer] intents ([AppContainer.setActiveProfile]
 * / [AppContainer.saveProfile] / [AppContainer.deleteProfile]) — NEVER a `rememberCoroutineScope().launch
 * { profileStore… }`, which a same-frame navigation would cancel mid-`.tmp`→rename on slow flash.
 *
 * ## apiKey semantics (MEDIUM-5 / V7)
 * The apiKey field NEVER pre-fills the raw stored key (it starts blank, with a "Key saved" hint when one
 * exists). On save the key is resolved by the pure [AppContainer.resolveApiKeyEdit]: a blank field PRESERVES
 * the stored key, an explicit **Clear key** writes null, a non-blank entry REPLACES it. [Profile.toString]
 * keeps masking the key to `***` (V7).
 *
 * ## Grammar (carried from DevicesScreen)
 * [ScreenScaffold] Field-only grid + a green gutter Back; sacred square tiles; static outline + glow only
 * (no looping animation — Adreno-320 floor); every color routes through [LocalTokens] (THEME-01). The
 * scrollable Field keeps `Dest.Devices` in the swipe-suppress set (AppShell).
 *
 * @param container    the process-scoped service-locator (the `profileStore` source + the durable intents).
 * @param onAddPrinter retained for API compatibility with the former Devices call site; the Add-printer
 *                     tile now opens the in-screen editor, so the shell hook is a no-op fallback (plan 04
 *                     repoints the call site / drawer label).
 * @param onSwitched   the D-02 navigation hook — invoked after `setActive`; the shell maps it to
 *                     `navigateTo(Dest.PrintStatus)` so the recovery Splash lands on the new printer's Status.
 * @param onBack       the explicit neutral gutter Back exit (D-10; the swipe-drawer is suppressed for this Field).
 */
@Composable
fun PrintersScreen(
    container: AppContainer,
    onAddPrinter: () -> Unit,
    onSwitched: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val profiles by container.profileStore.profiles.collectAsStateWithLifecycle(emptyList())
    val activeId by container.profileStore.activeId.collectAsStateWithLifecycle(null)

    // The Connection editor target: null = grid mode; an EditorTarget = editing that profile (or new).
    var editingTarget by remember { mutableStateOf<EditorTarget?>(null) }
    BackHandler(editingTarget != null) { editingTarget = null }

    val target = editingTarget
    if (target != null) {
        ConnectionEditor(
            container = container,
            profile = if (target is EditorTarget.Edit) target.profile else null,
            onDone = { editingTarget = null },
            modifier = modifier,
        )
        return
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            field = {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxSize()
                        .background(t.bg)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(profiles, key = { it.id }) { profile ->
                        PrinterTile(
                            profile = profile,
                            active = profile.id == activeId,
                            onClick = {
                                // D-02: persist active (PROCESS-scoped writeScope, NOT a composition scope —
                                // onSwitched() navigates away the same frame) → the runConfigLoop seam rebinds.
                                container.setActiveProfile(profile.id)
                                onSwitched()
                            },
                            onEdit = { editingTarget = EditorTarget.Edit(profile) },
                        )
                    }
                    item(key = "__add_printer__") {
                        AddPrinterTile(onClick = { editingTarget = EditorTarget.New })
                    }
                }
            },
            gutter = {
                OutlinedControl(
                    label = "Back",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    intent = Intent.Neutral, // D-10: plain nav spends no safety color (matches Move).
                    symbol = "arrow_back",
                )
            },
        )
    }
}

/** Which profile the Connection editor targets — an existing one to [Edit], or a blank [New] printer. */
private sealed interface EditorTarget {
    data class Edit(val profile: Profile) : EditorTarget
    data object New : EditorTarget
}

/**
 * The absorbed Connection editor (D-02) — moved VERBATIM from `SettingsScreen` (host/port/apiKey + mDNS
 * scan + the host/port validators, V5). Edits [profile] (null = a blank new-printer form). Persists through
 * the durable [AppContainer] intents only; resolves the apiKey via [AppContainer.resolveApiKeyEdit].
 */
@Composable
private fun ConnectionEditor(
    container: AppContainer,
    profile: Profile?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val scope = rememberCoroutineScope() // mDNS scan ONLY — never a profile persist (write-scope law).

    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("7125") }
    var apiKey by remember { mutableStateOf("") }
    var keyAlreadySaved by remember { mutableStateOf(false) }
    var hostError by remember { mutableStateOf(false) }
    var portError by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }

    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }

    // Seed the form from the target — NEVER pre-fill the raw apiKey into the field (MEDIUM-5/V7); show a
    // "Key saved" hint instead. Re-seed when the target id changes.
    LaunchedEffect(profile?.id) {
        host = profile?.host ?: ""
        port = profile?.port?.toString() ?: "7125"
        apiKey = ""
        keyAlreadySaved = profile?.apiKey != null
        hostError = false
        portError = false
        scanned = false
        discovered = emptyList()
    }

    pendingDelete?.let { victim ->
        ConfirmGuard(
            title = "Delete printer?",
            message = "This removes ${victim.displayName()} and its saved theme. This can't be undone.",
            confirmLabel = "Delete",
            cancelLabel = "Keep",
            onConfirm = {
                container.deleteProfile(victim.id)
                pendingDelete = null
                onDone()
            },
            onCancel = { pendingDelete = null },
            destructive = true,
        )
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        EditorSectionHeader(if (profile != null) "Edit printer" else "Add printer")

        if (profile != null) {
            Text(
                text = "Editing ${profile.displayName()}",
                color = t.text2,
                fontFamily = GeistMono,
                fontSize = fsSp(15f, t.fs).sp,
            )
        }

        TokenTextField(
            value = host,
            onValueChange = { host = it; hostError = false },
            label = "Host (IP or hostname)",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Text,
            isError = hostError,
        )
        if (hostError) EditorFieldError("Host is required.")

        TokenTextField(
            value = port,
            onValueChange = { port = it; portError = false },
            label = "Port",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Number,
            isError = portError,
        )
        if (portError) EditorFieldError("Port must be 1–65535.")

        TokenTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = if (keyAlreadySaved) "API key (leave blank to keep saved key)" else "API key (optional)",
            modifier = Modifier.fillMaxWidth(),
            keyboardType = KeyboardType.Password,
            isPassword = true,
        )
        if (keyAlreadySaved && apiKey.isBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Key saved",
                    color = t.go,
                    fontFamily = GeistMono,
                    fontSize = fsSp(15f, t.fs).sp,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedControl(
                label = if (scanning) "Scanning…" else "Scan (mDNS)",
                onClick = {
                    if (!scanning) {
                        scanning = true
                        scanned = false
                        discovered = emptyList()
                        scope.launch {
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
                    }
                },
                modifier = Modifier.weight(1f),
                intent = Intent.Accent,
            )
            if (keyAlreadySaved) {
                OutlinedControl(
                    label = "Clear key",
                    onClick = {
                        // Explicit Clear → resolveApiKeyEdit(cleared=true) returns null. Persist via the
                        // durable intent (saveProfile), NOT a composition scope.
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
                    // MEDIUM-5: blank field PRESERVES the stored key; a non-blank entry REPLACES it (no
                    // explicit Clear on the Save path — Clear is its own button above).
                    val resolvedKey = AppContainer.resolveApiKeyEdit(
                        existing = profile?.apiKey,
                        fieldInput = apiKey,
                        cleared = false,
                    )
                    val next =
                        if (profile != null) {
                            // EDIT — preserve the stable id + theme tuple + the per-profile toggle.
                            profile.copy(host = host.trim(), port = portInt!!, apiKey = resolvedKey)
                        } else {
                            // NEW — a fresh printer at the validated default theme tuple (D-05 fresh-start).
                            Profile(
                                id = Profile.newId(),
                                name = null,
                                host = host.trim(),
                                port = portInt!!,
                                apiKey = resolvedKey,
                            )
                        }
                    // Durable container scope (D-11 auto-selects the FIRST profile active). NEVER a
                    // rememberCoroutineScope() ([[dinghy-compose-write-scope-cancellation]]).
                    container.saveProfile(next)
                    apiKey = ""
                    onDone()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            intent = Intent.Go,
        )

        if (profile != null) {
            OutlinedControl(
                label = "Delete this printer",
                onClick = { pendingDelete = profile },
                modifier = Modifier.fillMaxWidth(),
                intent = Intent.Danger,
            )
        }

        OutlinedControl(
            label = "Back",
            onClick = onDone,
            modifier = Modifier.fillMaxWidth(),
            intent = Intent.Neutral,
        )

        Box(Modifier.height(24.dp))
    }
}

/** Bounded settle window for an mDNS scan — long enough to resolve LAN printers, short enough to end. */
private const val SCAN_WINDOW_MS = 6000L

/**
 * One saved-printer tile. Reuses the `DrawerTile` square-tile grammar verbatim. The ACTIVE tile (D-03)
 * gets accent emphasis. A small top-start `edit` glyph opens the Connection editor for this profile
 * (distinct from the tap-to-switch body and the active `bolt` marker — icon-no-repeat law).
 */
@Composable
private fun PrinterTile(
    profile: Profile,
    active: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val fill = if (active) t.accentSoft else t.surface2
    Box(
        Modifier
            .fillMaxSize()
            .aspectRatio(1f) // sacred square (LAYOUT.md NON-NEGOTIABLE 2).
            .clip(shape)
            .background(fill)
            .border(BorderStroke(2.dp, t.accentLine), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            MaterialSymbol(
                name = "bolt",
                tint = t.accent,
                sizeSp = fsSp(20f, t.fs),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
        // Edit affordance (top-start) — opens the absorbed Connection editor for this printer.
        MaterialSymbol(
            name = "edit",
            tint = t.text2,
            sizeSp = fsSp(20f, t.fs),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clickable(onClick = onEdit),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            MaterialSymbol(
                name = "dns",
                tint = t.text,
                sizeSp = fsSp(40f, t.fs),
            )
            Text(
                text = profile.displayName(),
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(20f, t.fs).sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${profile.host}:${profile.port}",
                color = t.text2,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Normal,
                fontSize = fsSp(17f, t.fs).sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The "Add printer" tile (D-01) — a live accent tile with an `add` glyph that opens the blank Connection
 * editor. `add` is unique on this screen (not `dns`/`bolt`/`edit`/`arrow_back` — icon-no-repeat law).
 */
@Composable
private fun AddPrinterTile(onClick: () -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        Modifier
            .fillMaxSize()
            .aspectRatio(1f)
            .clip(shape)
            .background(t.surface2)
            .border(BorderStroke(2.dp, t.accentLine), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            MaterialSymbol(
                name = "add",
                tint = t.text,
                sizeSp = fsSp(40f, t.fs),
            )
            Text(
                text = "Add printer",
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(16f, t.fs).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A discovered-printer row (mDNS), local to the absorbed editor (mirrors the former SettingsScreen row). */
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

@Composable
private fun EditorFieldError(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.stop,
        fontFamily = GeistMono,
        fontSize = fsSp(15f, t.fs).sp,
    )
}

@Composable
private fun EditorSectionHeader(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text,
        fontFamily = Geist,
        fontWeight = FontWeight.Bold,
        fontSize = fsSp(22f, t.fs).sp,
    )
}
