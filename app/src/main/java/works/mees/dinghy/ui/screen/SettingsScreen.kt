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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import works.mees.dinghy.BuildConfig
import works.mees.dinghy.config.DiscoveredPrinter
import works.mees.dinghy.config.Profile
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.FontScale
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The conventional Settings HUB (SET-01) — the ONE screen exempt from the Focus/Field/Gutter grammar
 * (D-15) and the ONLY place the system keyboard is allowed (PRIM-02). A plain
 * `Column.verticalScroll(rememberScrollState())` of token-themed sections (15-06 D-10/D-11).
 *
 * ## Section order (F1 — Printers section REMOVED)
 * **Connection** (host/port/key + mDNS form for the ACTIVE printer, with an "Add another printer" entry)
 * · **Appearance** (dark/light + S/M/L + the NEW palette-mode chip row + a live palette-reactive preview +
 * the **"Edit theme…"** forward-entry that pushes the seed/pool editor) · **Feature toggles** (Webcam
 * live; outputs/WebRTC/fine-tune greyed "Coming soon") · **System** (app version + build only — D-12).
 *
 * The old **Printers** profile-list/CRUD section was REMOVED (F1): printer management is owned by the
 * Devices switcher screen and the list was redundant here. The Connection form remains — it edits the
 * ACTIVE profile's connection and can still create a printer (first run + the Devices "Add printer"
 * jump), but the saved-profile LIST and its active markers no longer live in the Settings hub. The old
 * single-accent picker is also gone (D-04 retires per-role chrome overrides; accent derives from the seed).
 *
 * ## The editor seam (D-10, CONFIRMED Codex finding — RootController dual-path)
 * The theme-editor open-state is hosted INSIDE this screen (a local [editorOpen] back-stack) — NOT at
 * AppShell level. Because BOTH [works.mees.dinghy.ui.shell.RootController] (first-run/Connect/settings-
 * escape) AND [works.mees.dinghy.ui.shell.AppShell]'s `Dest.Settings` route render `SettingsScreen` the
 * same way, owning the flag in the screen makes the editor reachable from BOTH paths with no routing
 * divergence (hoisting it to AppShell would strand the RootController first-run path). The "Edit theme…"
 * row sets [editorOpen] true; a [BackHandler] backs out.
 *
 * ## Appearance persistence (D-09) — durable, via AppContainer intents
 * Every Appearance control drives the LIVE theme AND persists via the durable [AppContainer] intents
 * (writeScope + mutateActiveProfile / global idle, [[dinghy-compose-write-scope-cancellation]]) —
 * dark/light → [AppContainer.setActiveDark], palette mode → [AppContainer.setActiveMode], S/M/L → an
 * atomic `fsChoice` mutate. The active profile is the persist target when one exists, else the global
 * theme prefs (the idle / new-profile default). The seed live-retheme rides the seedTheme reactive flow.
 *
 * ## Dependency injection — the screen OWNS nothing (Phase-4 boundary)
 * [SettingsScreen] ACCEPTS the [AppContainer] and CONSTRUCTS nothing.
 *
 * @param container the process-scoped service-locator (constructs nothing here).
 * @param onConnectionSaved invoked after a successful connection save (the host routes onward, D-13).
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onConnectionSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val scope = rememberCoroutineScope()
    val profileStore = container.profileStore

    // ---- Profile set + active selection ------------------------------------------------------
    // `profiles` is still needed to tell first-run (empty) from "add another printer" (F1); the active
    // PROFILE drives the Connection form target + the Appearance theme mirror.
    val profiles by profileStore.profiles.collectAsStateWithLifecycle(emptyList())
    val activeProfile by container.activeProfile.collectAsStateWithLifecycle(null)
    val hasActive = activeProfile != null

    // ---- Theme-editor open-state (the editor seam, hosted HERE so BOTH entry paths reach it) --
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(editorOpen) { editorOpen = false }
    if (editorOpen) {
        ThemeEditorScreen(container = container, onBack = { editorOpen = false }, modifier = modifier)
        return // The editor owns the whole screen while open.
    }

    // ---- Connection editing target (F1) ------------------------------------------------------
    // The Connection form edits the ACTIVE profile by default. When the user taps "Add another printer"
    // (or on first run with no profiles) it switches to a blank NEW-profile form. `null` while no active
    // profile exists means an implicit first-run new-printer form.
    var addingNew by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }

    // The profile the Connection form currently targets: the explicit "add" form (null profile) or the
    // active profile. Re-derived as the active profile changes.
    val connectionTarget: Profile? = if (addingNew) null else activeProfile

    // ---- Connection form state ---------------------------------------------------------------
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("7125") }
    var apiKey by remember { mutableStateOf("") }
    var keyAlreadySaved by remember { mutableStateOf(false) }
    var hostError by remember { mutableStateOf(false) }
    var portError by remember { mutableStateOf(false) }

    // ---- mDNS scan state ---------------------------------------------------------------------
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }

    // Seed the Connection form from the target profile whenever the target changes (active switch, or the
    // user toggling "Add another printer"). A null target = a blank new-printer form.
    LaunchedEffect(connectionTarget?.id, addingNew) {
        val p = connectionTarget
        host = p?.host ?: ""
        port = p?.port?.toString() ?: "7125"
        apiKey = ""
        keyAlreadySaved = p?.apiKey != null
        hostError = false
        portError = false
        scanned = false
        discovered = emptyList()
    }

    // ---- Appearance (theme) control mirror state ---------------------------------------------
    // Mirrors the persisted picks so the chips can show the current selection; the live resolver + the
    // persisted prefs/profile are the authority — these vars only drive the active-chip emphasis.
    var dark by remember { mutableStateOf(true) }
    var fsChoice by remember { mutableStateOf(FontScale.M) }
    var paletteMode by remember { mutableStateOf(ThemeResolver.MODE_COLORFUL) }

    // Seed the Appearance mirror from the ACTIVE profile's theme tuple when one exists (so the screen
    // opens reflecting the active printer's look, D-09), else from the global theme tuple (the idle /
    // new-profile default). Re-seeds whenever the active profile changes (e.g. after a switch/delete).
    LaunchedEffect(activeProfile?.id) {
        val tuple = activeProfile?.toThemeTuple() ?: container.themePrefs.tupleFlow.firstOrNull()
        if (tuple != null) {
            dark = tuple.dark
            fsChoice = FontScale.entries.firstOrNull { it.multiplier == tuple.fs } ?: FontScale.M
            paletteMode = tuple.paletteMode
        }
    }

    // Full-screen Delete confirm guard (D-14) — hoisted over the whole screen, mirrors FilesScreen.
    pendingDelete?.let { victim ->
        ConfirmGuard(
            title = "Delete printer?",
            message = "This removes ${victim.displayName()} and its saved theme. This can't be undone.",
            confirmLabel = "Delete",
            cancelLabel = "Keep",
            onConfirm = {
                container.deleteProfile(victim.id)
                pendingDelete = null
                addingNew = false
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
        SectionHeader("Settings")

        // ============================ CONNECTION ============================================
        // F1: the Printers profile-LIST/CRUD section was removed (Devices owns that). What remains is the
        // host/port/key + mDNS form for the ACTIVE printer, plus an "Add another printer" entry that blanks
        // the form into new-profile mode (so the Devices "Add printer" jump still has a place to land).
        SectionLabel("Connection")
        run {
            val existing = connectionTarget // null = the explicit add / first-run new-printer form

            // Show whose connection is being edited (or that this is a new printer), so removing the list
            // doesn't lose the "which printer" context. An accent "Add another printer" entry blanks the
            // form; while adding, a way back to the active printer's form.
            if (existing != null) {
                Text(
                    text = "Editing ${existing.displayName()}",
                    color = t.text2,
                    fontFamily = GeistMono,
                    fontSize = fsSp(15f, t.fs).sp,
                )
                ForwardEntryRow(
                    label = "+ Add another printer",
                    subLabel = "Set up a new Moonraker connection",
                    enabled = true,
                    onClick = { addingNew = true },
                )
            } else if (profiles.isNotEmpty()) {
                Text(
                    text = "New printer",
                    color = t.accent,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.SemiBold,
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
            if (hostError) {
                FieldError("Host is required.")
            }

            TokenTextField(
                value = port,
                onValueChange = { port = it; portError = false },
                label = "Port",
                modifier = Modifier.fillMaxWidth(),
                keyboardType = KeyboardType.Number,
                isError = portError,
            )
            if (portError) {
                FieldError("Port must be 1–65535.")
            }

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
                            existing?.let { e ->
                                container.saveProfile(e.copy(apiKey = null))
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
                        val typedKey = apiKey.takeIf { it.isNotBlank() }
                        val preservedKey =
                            if (typedKey == null && keyAlreadySaved) existing?.apiKey else typedKey
                        val profile =
                            if (existing != null) {
                                // EDIT — preserve the stable id + theme tuple; change connection only.
                                existing.copy(
                                    host = host.trim(),
                                    port = portInt!!,
                                    apiKey = preservedKey,
                                )
                            } else {
                                // NEW — a fresh printer starts at the validated default theme tuple
                                // (D-05 fresh-start). The user tunes it later via Edit theme…
                                Profile(
                                    id = Profile.newId(),
                                    name = null,
                                    host = host.trim(),
                                    port = portInt!!,
                                    apiKey = preservedKey,
                                )
                            }
                        // Durable container scope → MoonrakerService rebuilds the spine on the active
                        // config. ProfileStore.upsert auto-selects the FIRST profile active (D-11). Do
                        // NOT use rememberCoroutineScope() here (it would be cancelled mid-write by the
                        // same-frame navigation, [[dinghy-compose-write-scope-cancellation]]).
                        container.saveProfile(profile)
                        addingNew = false // a new printer just saved is now active → fall back to its form.
                        apiKey = ""
                        onConnectionSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                intent = Intent.Go,
            )

            // Secondary actions: while ADDING a new printer (and one already exists), allow backing out to
            // the active printer's form. For the active printer, expose Delete (guarded). On first run
            // (no profiles, implicit new form) there is nothing to cancel back to and nothing to delete.
            if (addingNew && profiles.isNotEmpty()) {
                OutlinedControl(
                    label = "Cancel",
                    onClick = { addingNew = false },
                    modifier = Modifier.fillMaxWidth(),
                    intent = Intent.Neutral,
                )
            } else if (existing != null) {
                OutlinedControl(
                    label = "Delete this printer",
                    onClick = { pendingDelete = existing },
                    modifier = Modifier.fillMaxWidth(),
                    intent = Intent.Danger,
                )
            }
        }

        // ============================ APPEARANCE ============================================
        // Each quick control drives the LIVE theme AND persists via the durable AppContainer intents
        // (D-09): the ACTIVE profile when one exists, else the global ThemePrefs idle/new-profile look.
        // The seed/pool editor is the pushed "Edit theme…" sub-page (D-10).
        SectionLabel("Appearance")

        // F4 — a PROMINENT live palette-reactive preview so switching Colorful/Simple/High-contrast is
        // OBVIOUSLY different at a glance. The generated accent + the contrast-ranked pool strip render
        // their LITERAL live colors (the same data-color carve-out as the theme editor's preview strip);
        // because [LocalTokens] re-emits on every palette-mode/seed change, this row re-paints instantly
        // when the mode flips. The accent swatch is wider (the headline of the palette).
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PaletteSwatch(t.accent, Modifier.weight(2f)) // the accent — the palette headline.
            for (c in t.pool.take(6)) PaletteSwatch(c, Modifier.weight(1f))
        }

        // Dark / Light — chrome derives from the seed; this only flips polarity.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedControl(
                label = "Dark",
                onClick = {
                    dark = true
                    container.themeResolver.setDark(true) // live
                    container.setActiveDark(hasActive, true) // durable
                },
                modifier = Modifier.weight(1f),
                intent = if (dark) Intent.Accent else Intent.Neutral,
            )
            OutlinedControl(
                label = "Light",
                onClick = {
                    dark = false
                    container.themeResolver.setDark(false) // live
                    container.setActiveDark(hasActive, false) // durable
                },
                modifier = Modifier.weight(1f),
                intent = if (!dark) Intent.Accent else Intent.Neutral,
            )
        }

        // S / M / L text size (the --fs authority). F4: each segment is filled with a POOL ("traffic-light")
        // color from the active palette so the selector visibly reflects the current mode — S/M/L pick up
        // pool[0]/pool[1]/pool[2] and recolor when the palette mode flips. The selected segment gets the
        // accent ring + a soft accent backing; the fill itself stays the pool color (data carve-out).
        SectionLabel("Text size")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FontScale.entries.forEachIndexed { i, choice ->
                val poolColor = if (t.pool.isEmpty()) t.accent else t.pool[i % t.pool.size]
                PoolSizeSegment(
                    label = choice.name,
                    fill = poolColor,
                    selected = fsChoice == choice,
                    onClick = {
                        fsChoice = choice
                        container.themeResolver.setFs(choice.multiplier) // live
                        persistFs(scope, container, hasActive, choice) // durable
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Palette mode (D-15) — Colorful (default) / Simple / High contrast. Mirrors the dark/light +
        // S/M/L chip rows: active = Intent.Accent, inactive = Intent.Neutral.
        SectionLabel("Palette mode")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((mode, label) in PALETTE_MODES) {
                OutlinedControl(
                    label = label,
                    onClick = {
                        paletteMode = mode
                        container.themeResolver.setMode(mode) // live
                        container.setActiveMode(hasActive, mode) // durable
                    },
                    modifier = Modifier.weight(1f),
                    intent = if (paletteMode == mode) Intent.Accent else Intent.Neutral,
                )
            }
        }

        // The "Edit theme…" forward-entry — pushes the seed/pool editor sub-page (D-10). The trailing
        // ellipsis signals it pushes; reachable from BOTH Settings entry paths (the editor open-state
        // is owned by THIS screen).
        ForwardEntryRow(
            label = "Edit theme…",
            subLabel = "Seed color, palette & pool",
            enabled = true,
            onClick = { editorOpen = true },
        )

        // ============================ FEATURE TOGGLES ======================================
        SectionLabel("Feature toggles")
        // Webcam — the one live toggle target this phase (the actual toggle UI/persist is owned by the
        // webcam surface; here it is the live forward entry, distinct from the greyed placeholders).
        ForwardEntryRow(label = "Webcam", subLabel = null, enabled = true, onClick = { })
        // Greyed capability-gated placeholders (D-11) — later phases light these up.
        ForwardEntryRow(label = "Output controls", subLabel = "Coming soon", enabled = false, onClick = { })
        ForwardEntryRow(label = "Camera (WebRTC)", subLabel = "Coming soon", enabled = false, onClick = { })
        ForwardEntryRow(label = "Fine-tune", subLabel = "Coming soon", enabled = false, onClick = { })

        // ============================ SYSTEM ===============================================
        // App version + build ONLY this phase (D-12). No printer/Klipper/Moonraker info (Phase 19); no
        // restart (that stays on the Splash recovery surface).
        SectionLabel("System")
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Version",
                color = t.text,
                fontFamily = Geist,
                fontSize = fsSp(17f, t.fs).sp,
            )
            Text(
                text = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                color = t.text2,
                fontFamily = GeistMono,
                fontSize = fsSp(15f, t.fs).sp,
            )
        }

        // Bottom breathing room so the last control clears the scroll edge.
        Box(Modifier.height(24.dp))
    }
}

/** The palette-mode chips (D-15) — `ThemeResolver` mode name → display label. Colorful is the default. */
private val PALETTE_MODES: List<Pair<String, String>> = listOf(
    ThemeResolver.MODE_COLORFUL to "Colorful",
    ThemeResolver.MODE_SIMPLE to "Simple",
    ThemeResolver.MODE_HIGH_CONTRAST to "High contrast",
)

/** Persist a text-size pick (D-09) — active profile's fsChoice, else global themePrefs. Durable. */
private fun persistFs(
    scope: kotlinx.coroutines.CoroutineScope,
    container: AppContainer,
    hasActive: Boolean,
    next: FontScale,
) {
    if (hasActive) {
        container.mutateActiveProfile { it.copy(fsChoice = next.name) }
    } else {
        scope.launch { container.themePrefs.setFs(next) }
    }
}

/** Bounded settle window for an mDNS scan — long enough to resolve LAN printers, short enough to end. */
private const val SCAN_WINDOW_MS = 6000L

/**
 * A forward-entry row (D-11) — a tappable row with a label + optional sub-label that either opens a
 * sub-page (e.g. "Edit theme…") or, when [enabled] is false, reads as a greyed capability-gated
 * placeholder ("Coming soon"). The established forward-entry pattern; later phases light placeholders up.
 */
@Composable
private fun ForwardEntryRow(
    label: String,
    subLabel: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val outline = if (enabled) t.accentLine else t.outline
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .border(BorderStroke(2.dp, outline), shape)
    val clickable = if (enabled) base.clickable(onClick = onClick) else base
    Row(
        clickable.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) t.text else t.text3,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(17f, t.fs).sp,
            )
            if (subLabel != null) {
                Text(
                    text = subLabel,
                    color = t.text3,
                    fontFamily = Geist,
                    fontSize = fsSp(15f, t.fs).sp,
                )
            }
        }
    }
}

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

/**
 * F4 — a live palette PREVIEW swatch (the accent + each pool color), rendering its LITERAL generated
 * color (the data-color carve-out, like the theme editor's preview strip). Display-only; recolors when the
 * palette mode/seed changes because [LocalTokens] re-emits. The chrome (the 2dp outline) routes through
 * tokens (THEME-01).
 */
@Composable
private fun PaletteSwatch(fill: Color, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(fill)
            .border(BorderStroke(2.dp, t.outline), shape),
    )
}

/**
 * F4 — a text-size segment whose FILL is a pool ("traffic-light") color from the active palette, so the
 * S/M/L selector visibly reflects the current palette mode (and recolors when the mode flips). The fill is
 * the literal pool color (data carve-out); the SELECTED segment gets an accent ring so the pick is still
 * unambiguous. The label color is contrast-picked against the fill (white on dark fills, ink on light).
 */
@Composable
private fun PoolSizeSegment(
    label: String,
    fill: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val ink = if (fill.luminance() > 0.5f) Color(0xFF101010) else Color(0xFFF5F5F5)
    val base = modifier
        .height(64.dp) // ≥64dp touch floor (UI-02).
        .clip(shape)
        .background(fill)
    val outlined =
        if (selected) base.border(BorderStroke(3.dp, t.accentLine), shape)
        else base.border(BorderStroke(2.dp, t.outline), shape)
    Box(outlined.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = ink,
            fontFamily = Geist,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
        )
    }
}

@Composable
private fun FieldError(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.stop,
        fontFamily = GeistMono,
        fontSize = fsSp(15f, t.fs).sp,
    )
}

@Composable
private fun SectionHeader(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text,
        fontFamily = Geist,
        fontWeight = FontWeight.Bold,
        fontSize = fsSp(22f, t.fs).sp,
    )
}

@Composable
private fun SectionLabel(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text2,
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = fsSp(20f, t.fs).sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}
