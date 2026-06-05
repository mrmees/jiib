package works.mees.dinghy.ui.screen

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import works.mees.dinghy.config.DiscoveredPrinter
import works.mees.dinghy.config.Profile
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.FontScale
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeBase
import works.mees.dinghy.theme.TokenDelta
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The conventional Settings screen (SET-01) — the ONE screen exempt from the Focus/Field/Gutter
 * grammar (D-15) and the ONLY place the system keyboard is allowed (PRIM-02). It is built like
 * [works.mees.dinghy.gallery.GalleryScreen]: a plain `Column.verticalScroll(rememberScrollState())`
 * conventional Android list of token-themed sections — the Focus/Field/Gutter scaffold is exempted
 * here (D-15).
 *
 * ## Phase 14 — profile CRUD (D-13)
 * The "Connection" section is no longer ONE form: it is a LIST of saved [Profile] rows (name +
 * `host:port`, the active one accent-marked), each tappable to EDIT, plus an "Add printer" row that
 * opens the existing host/port/key + mDNS form BLANK. The form is reused 1:1 — only the Save handler
 * changed from `ConnectionStore.save` to `container.profileStore.upsert(...)`. Adding the FIRST
 * profile (empty store) makes it active via [works.mees.dinghy.config.ProfileStore.upsert]'s
 * first-add-active rule (D-11), so `hasConfig` flips true and the root controller routes into the
 * Shell — the existing [onConnectionSaved] callback then lands the user on Print-Status, NOT a dead
 * Connect/Settings screen. Each editable profile carries a Delete affordance routed through the
 * full-screen [ConfirmGuard] (D-14 → [works.mees.dinghy.config.ProfileStore.delete], whose D-12
 * auto-pick fires in the writer).
 *
 * ## Phase 14 — Appearance retarget (D-09)
 * The Appearance controls (dark/light, S/M/L, accent) keep their LIVE retheme
 * (`container.themeResolver.set*`) unchanged; only the PERSIST target moves from the global
 * `themePrefs` to the ACTIVE profile's theme fields (`profileStore.upsert(active.copy(...))`). When
 * NO profile is active (idle/first-run) the persist falls back to the global `themePrefs` — which is
 * ALSO the new-profile default look — so `themePrefs` is RETAINED, never deleted.
 *
 * ## Dependency injection — the screen OWNS nothing (Phase-4 boundary)
 * Per the GalleryScreen discipline, [SettingsScreen] ACCEPTS the [AppContainer] and CONSTRUCTS
 * nothing. It reads `container.profileStore`, `container.activeProfile`, `container.themeResolver`,
 * `container.themePrefs`, and `container.discovery`; it opens no socket and starts no service.
 *
 * ## The seed path for a live connection
 * Saving the Connection form writes a validated [Profile] to `ProfileStore.upsert(...)`. The
 * [works.mees.dinghy.service.MoonrakerService] collects `container.activeConfig` and rebuilds the
 * spine on a new value (D-02/D-03) — so a save here is what brings the printer connection UP. This
 * screen must NEVER start the service itself; it only persists.
 *
 * ## Color discipline
 * Every color comes from `LocalTokens.current`; no raw color literal renders chrome here (THEME-01).
 * The accent-picker swatch palette carries ARGB *ints* passed to `TokenDelta.of(...)` — those are
 * theme DATA, not rendered-chrome color literals.
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

    // ---- Profile set + active selection (the CRUD list source) -------------------------------
    val profiles by profileStore.profiles.collectAsStateWithLifecycle(emptyList())
    val activeId by profileStore.activeId.collectAsStateWithLifecycle(null)
    val activeProfile by container.activeProfile.collectAsStateWithLifecycle(null)

    // ---- Editing state -----------------------------------------------------------------------
    // null = list mode (show profile rows + Add). non-null = the form is open: NewProfile to add a
    // blank profile, or EditProfile(existing) to edit a saved one (D-13).
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    // The profile pending delete — drives the full-screen ConfirmGuard overlay (D-14), mirroring the
    // FilesScreen state-gated guard.
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }

    // ---- Connection form state (only meaningful while [editing] != null) ---------------------
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("7125") } // Moonraker's conventional default port.
    var apiKey by remember { mutableStateOf("") }   // ALWAYS blank on open — never the saved secret.
    var keyAlreadySaved by remember { mutableStateOf(false) }
    var hostError by remember { mutableStateOf(false) }
    var portError by remember { mutableStateOf(false) }

    // ---- mDNS scan state ---------------------------------------------------------------------
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) } // true once a scan has completed at least once.
    var discovered by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }

    // ---- Appearance (theme) control mirror state ---------------------------------------------
    // Mirrors the persisted pick so the toggles can show the current selection; the live resolver
    // and the persisted prefs are BOTH the authority — these vars only drive the checkmarks.
    var base by remember { mutableStateOf(ThemeBase.Dark) }
    var fsChoice by remember { mutableStateOf(FontScale.M) }
    var accentArgb by remember { mutableStateOf<Int?>(null) } // null = base accent (no override).

    // Seed the Appearance mirror from the ACTIVE profile's theme when one exists (so the screen opens
    // reflecting the active printer's look, D-09), else from the global theme prefs (the idle /
    // new-profile default). Re-seeds whenever the active profile changes (e.g. after a switch/delete).
    LaunchedEffect(activeProfile?.id) {
        val active = activeProfile
        val resolved = active?.toThemeResolved() ?: container.themePrefs.flow.firstOrNull()
        if (resolved != null) {
            base = resolved.base
            fsChoice = FontScale.entries.firstOrNull { it.multiplier == resolved.fs } ?: FontScale.M
            accentArgb = resolved.deltas.overrides[TokenDelta.Role.Accent]?.toInt()
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
                // D-12 auto-pick (select another active / clear if last) fires in the store writer.
                // Durable container scope: dismissing the guard + a delete-of-active that re-routes can
                // tear this composition down before the write lands (see AppContainer.writeScope).
                container.deleteProfile(victim.id)
                pendingDelete = null
                editing = null
            },
            onCancel = { pendingDelete = null },
            destructive = true,
        )
        return // The guard owns the whole screen while visible — don't render the list underneath.
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
        SectionLabel("Connection")

        val target = editing
        if (target == null) {
            // ---- LIST MODE: one row per saved profile + an "Add printer" row (D-13) ----------
            for (profile in profiles) {
                ProfileRow(
                    profile = profile,
                    active = profile.id == activeId,
                    onClick = {
                        // Open the EDIT form pre-filled with this profile's host/port/key (D-13).
                        editing = EditTarget.EditProfile(profile)
                        host = profile.host
                        port = profile.port.toString()
                        apiKey = ""                       // never echo the saved secret.
                        keyAlreadySaved = profile.apiKey != null
                        hostError = false
                        portError = false
                        scanned = false
                        discovered = emptyList()
                    },
                )
            }
            AddPrinterRow(
                onClick = {
                    // Open the form BLANK to add a new printer (D-13).
                    editing = EditTarget.NewProfile
                    host = ""
                    port = "7125"
                    apiKey = ""
                    keyAlreadySaved = false
                    hostError = false
                    portError = false
                    scanned = false
                    discovered = emptyList()
                },
            )
        } else {
            // ---- FORM MODE: the existing host/port/key + mDNS form, reused 1:1 ----------------
            val existing = (target as? EditTarget.EditProfile)?.profile

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

            // API key — blank on open, masked entry; a non-secret indicator shows a key exists.
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
                        fontSize = fsSp(13f, t.fs).sp,
                    )
                }
            }

            // Scan + Clear-key actions row.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedControl(
                    label = if (scanning) "Scanning…" else "Scan (mDNS)",
                    onClick = {
                        if (!scanning) {
                            scanning = true
                            scanned = false
                            discovered = emptyList()
                            // Collect the FULLY LAZY discovery flow ONLY on tap (04-01); machinery is
                            // acquired on collect and released on cancel. A bounded settle window keeps
                            // it from running forever — an empty scan is normal (D-04), never blocking.
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
                            // Re-save the existing profile WITHOUT a key (explicit removal). Preserves
                            // the profile id + theme; only the key is dropped.
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

            // Discovered-printer rows: tappable, fill host+port. An empty scan is a normal outcome.
            if (scanned && discovered.isEmpty()) {
                Text(
                    text = "No printers found — enter the host manually.",
                    color = t.text2,
                    fontFamily = GeistMono,
                    fontSize = fsSp(13f, t.fs).sp,
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

            // Save — validate via the SAME sanitize parity used by the store, then upsert.
            OutlinedControl(
                label = "Save",
                onClick = {
                    val portInt = port.trim().toIntOrNull()
                    // Client-side validation mirrors ConnectionStore.sanitize (reject blank host /
                    // out-of-range port) so an invalid entry shows inline and never persists
                    // (T-14-08); the store sanitize is the second gate.
                    val blankHost = host.isBlank()
                    val badPort = portInt == null || portInt !in 1..65535
                    hostError = blankHost
                    portError = badPort
                    if (!blankHost && !badPort) {
                        // No-clobber: a blank key field PRESERVES the saved key (edit only); only a
                        // non-blank field sets a new key.
                        val typedKey = apiKey.takeIf { it.isNotBlank() }
                        val preservedKey =
                            if (typedKey == null && keyAlreadySaved) existing?.apiKey else typedKey
                        val profile =
                            if (existing != null) {
                                // EDIT — preserve the stable id + theme fields; change connection only.
                                existing.copy(
                                    host = host.trim(),
                                    port = portInt!!,
                                    apiKey = preservedKey,
                                )
                            } else {
                                // NEW — stable id, inherit the user's CURRENT global look so a new
                                // printer starts with the idle/default theme (RESEARCH Pattern 3).
                                val seedDelta = accentArgb
                                    ?.let { TokenDelta.of(TokenDelta.Role.Accent to it).toPersistedArgb() }
                                    ?: emptyMap()
                                Profile(
                                    id = Profile.newId(),
                                    name = null, // optional; displayName() falls back to host (D-10).
                                    host = host.trim(),
                                    port = portInt!!,
                                    apiKey = preservedKey,
                                    themeBase = base.name,
                                    fsChoice = fsChoice.name,
                                    themeDeltaArgb = seedDelta,
                                )
                            }
                        // Persist on the DURABLE container scope → MoonrakerService.collectLatest rebuilds
                        // the spine on the active config (D-02/D-03). ProfileStore.upsert auto-selects the
                        // FIRST profile active (D-11), so a first-ever add flips hasConfig true and routes
                        // into the Shell. onConnectionSaved() navigates away in the same frame, so this MUST
                        // NOT be a rememberCoroutineScope().launch (it would be cancelled mid-write — the
                        // first printer would silently never persist). Do NOT start the service from here,
                        // and do NOT call setActive — the writer owns active-id (a later add never steals it).
                        container.saveProfile(profile)
                        editing = null
                        apiKey = ""
                        onConnectionSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                intent = Intent.Go,
            )

            // Cancel + (edit-only) Delete row — back out of the form, or delete behind the guard (D-14).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedControl(
                    label = "Cancel",
                    onClick = { editing = null },
                    modifier = Modifier.weight(1f),
                    intent = Intent.Neutral,
                )
                if (existing != null) {
                    OutlinedControl(
                        label = "Delete",
                        onClick = { pendingDelete = existing },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Danger,
                    )
                }
            }
        }

        // ============================ APPEARANCE ============================================
        // Each control drives BOTH the live ThemeResolver (immediate re-theme) AND the persist
        // target (D-09): the ACTIVE profile's theme when one exists, else the global ThemePrefs (the
        // idle / new-profile default). The accent picker overrides ONLY the --accent role; the full
        // multi-role custom editor is deferred (the substrate supports it).
        SectionLabel("Appearance")

        // Dark / Light base.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedControl(
                label = "Dark",
                onClick = {
                    base = ThemeBase.Dark
                    container.themeResolver.setBase(ThemeBase.Dark) // live
                    persistBase(scope, container, activeProfile, ThemeBase.Dark)
                },
                modifier = Modifier.weight(1f),
                intent = if (base == ThemeBase.Dark) Intent.Accent else Intent.Neutral,
            )
            OutlinedControl(
                label = "Light",
                onClick = {
                    base = ThemeBase.Light
                    container.themeResolver.setBase(ThemeBase.Light) // live
                    persistBase(scope, container, activeProfile, ThemeBase.Light)
                },
                modifier = Modifier.weight(1f),
                intent = if (base == ThemeBase.Light) Intent.Accent else Intent.Neutral,
            )
        }

        // S / M / L text size (the --fs authority).
        SectionLabel("Text size")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (choice in FontScale.entries) {
                OutlinedControl(
                    label = choice.name,
                    onClick = {
                        fsChoice = choice
                        container.themeResolver.setFs(choice.multiplier) // live
                        persistFs(scope, container, activeProfile, choice)
                    },
                    modifier = Modifier.weight(1f),
                    intent = if (fsChoice == choice) Intent.Accent else Intent.Neutral,
                )
            }
        }

        // Accent-color picker (D-16) — overrides the --accent role ONLY. Tapping writes a single-role
        // TokenDelta both live and persisted. "Default" clears the override (inherit the base accent).
        SectionLabel("Accent color")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // "Default" swatch — clears the accent override.
            AccentSwatch(
                fillArgb = null,
                selected = accentArgb == null,
                onClick = {
                    accentArgb = null
                    container.themeResolver.setDeltas(TokenDelta.EMPTY) // live
                    persistDeltas(scope, container, activeProfile, TokenDelta.EMPTY)
                },
            )
            for (argb in ACCENT_PALETTE) {
                AccentSwatch(
                    fillArgb = argb,
                    selected = accentArgb == argb,
                    onClick = {
                        accentArgb = argb
                        val delta = TokenDelta.of(TokenDelta.Role.Accent to argb)
                        container.themeResolver.setDeltas(delta) // live
                        persistDeltas(scope, container, activeProfile, delta)
                    },
                )
            }
        }

        // Bottom breathing room so the last control clears the scroll edge.
        Box(Modifier.height(24.dp))
    }
}

/**
 * Which profile (if any) the Connection form is editing. [NewProfile] = the blank add form;
 * [EditProfile] = the form pre-filled from an existing saved profile (D-13).
 */
private sealed interface EditTarget {
    data object NewProfile : EditTarget
    data class EditProfile(val profile: Profile) : EditTarget
}

/**
 * Build the persisted `themeDeltaArgb` map (Role-name string → ARGB Long) from a runtime [TokenDelta]
 * by stringifying its Role keys — the exact shape [Profile.themeDeltaArgb] / `ThemePrefs` persist.
 * There is NO `TokenDelta.toArgbMap()`; [TokenDelta.overrides] is `Map<TokenDelta.Role, Long>`.
 */
private fun TokenDelta.toPersistedArgb(): Map<String, Long> = overrides.mapKeys { it.key.name }

/**
 * Persist a base-theme pick (D-09): write the ACTIVE profile's [Profile.themeBase] when one exists,
 * else fall back to the global [works.mees.dinghy.theme.ThemePrefs] (the idle / new-profile default —
 * themePrefs is RETAINED, never deleted). The LIVE retheme already happened at the call site.
 */
private fun persistBase(
    scope: kotlinx.coroutines.CoroutineScope,
    container: AppContainer,
    active: Profile?,
    next: ThemeBase,
) {
    if (active != null) {
        // Durable + lost-update-safe (WR-01): atomic read-modify-write of just this field inside the
        // store's single edit, so a fast base-then-accent tap pair doesn't drop one change.
        container.mutateActiveProfile { it.copy(themeBase = next.name) }
    } else {
        scope.launch { container.themePrefs.setBase(next) }
    }
}

/** Persist a text-size pick (D-09) — active profile's [Profile.fsChoice], else global themePrefs. */
private fun persistFs(
    scope: kotlinx.coroutines.CoroutineScope,
    container: AppContainer,
    active: Profile?,
    next: FontScale,
) {
    if (active != null) {
        container.mutateActiveProfile { it.copy(fsChoice = next.name) }
    } else {
        scope.launch { container.themePrefs.setFs(next) }
    }
}

/**
 * Persist an accent/delta pick (D-09) — the active profile's [Profile.themeDeltaArgb] built from
 * [TokenDelta.overrides] via [toPersistedArgb] (NOT a non-existent `toArgbMap`), else global themePrefs.
 */
private fun persistDeltas(
    scope: kotlinx.coroutines.CoroutineScope,
    container: AppContainer,
    active: Profile?,
    delta: TokenDelta,
) {
    if (active != null) {
        container.mutateActiveProfile { it.copy(themeDeltaArgb = delta.toPersistedArgb()) }
    } else {
        scope.launch { container.themePrefs.setDeltas(delta) }
    }
}

/**
 * The accent-picker palette (D-16) — a small fixed set of role-appropriate accents. These are theme
 * DATA (ARGB ints handed to [TokenDelta.of]), NOT rendered-chrome color literals: the swatch fill is
 * the candidate accent the user is choosing, so it is intrinsically a value, not a token. The default
 * (cool signature blue) is the base accent and is offered via the separate "Default" swatch (no override).
 */
private val ACCENT_PALETTE: List<Int> = listOf(
    0xFF4DA3FF.toInt(), // brighter blue
    0xFF22C3A6.toInt(), // teal
    0xFF8B5CF6.toInt(), // violet
    0xFFFF8A3D.toInt(), // warm orange
    0xFFFF5DA2.toInt(), // pink
)

/**
 * A tappable accent swatch. The outline + selection ring derive from [LocalTokens] (THEME-01); only
 * the swatch FILL carries the candidate accent value (theme data). [fillArgb] null = the "Default"
 * swatch, which shows the surface role (a neutral chip) since "default" means "inherit base accent".
 */
@Composable
private fun AccentSwatch(
    fillArgb: Int?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    // The ONLY non-token Color in this file: the swatch FILL materializes the candidate accent the
    // user is choosing (palette DATA, D-16) — it is the value being picked, not rendered chrome. Every
    // other color (outline, ring, text, the "Default" chip) routes through LocalTokens (THEME-01).
    val fill = if (fillArgb != null) androidx.compose.ui.graphics.Color(fillArgb) else t.surface2
    Box(
        Modifier
            .size(64.dp) // ≥64dp touch floor (UI-02) — a sanctioned fixed value.
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(if (selected) 6.dp else 0.dp)
                .clip(shape)
                .background(fill),
        )
        // Selection ring uses the accent-line token (no raw color), set ON TOP of the fill.
        if (selected) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(BorderStroke(3.dp, t.accentLine), shape),
            )
        }
        if (fillArgb == null) {
            Text(
                text = "Def",
                color = t.text2,
                fontFamily = GeistMono,
                fontSize = fsSp(12f, t.fs).sp,
            )
        }
    }
}

/** Bounded settle window for an mDNS scan — long enough to resolve LAN printers, short enough to end. */
private const val SCAN_WINDOW_MS = 6000L

/**
 * A saved-profile row (D-13) in the Connection list — name (20sp SemiBold Geist) + `host:port`
 * (17sp Geist Mono, muted). The ACTIVE profile's row gets the accent outline + a faint accent-soft
 * fill tint + an accent "active" marker glyph (UI-SPEC — accent-marker, must not repeat the icon
 * grammar of an Add row). Tapping the row opens the EDIT form (D-13). Font floors honored
 * (MEMORY [[dinghy-font-sizes-too-small]]).
 */
@Composable
private fun ProfileRow(
    profile: Profile,
    active: Boolean,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .background(if (active) t.accentSoft else t.surface)
    val outlined = if (active) base.border(BorderStroke(2.dp, t.accentLine), shape) else base
    Row(
        outlined
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = profile.displayName(),
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(20f, t.fs).sp,
            )
            Text(
                text = "${profile.host}:${profile.port}",
                color = t.text2,
                fontFamily = GeistMono,
                fontSize = fsSp(17f, t.fs).sp,
            )
        }
        if (active) {
            // Accent "active" marker — the at-a-glance "this is the printer you're driving" signal.
            Text(
                text = "ACTIVE",
                color = t.accent,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(15f, t.fs).sp,
            )
        }
    }
}

/**
 * The "Add printer" row (D-13) — opens the host/port/key + mDNS form BLANK. A live accent-outlined
 * row distinct from the saved-profile rows; the `+` prefix reads as "add" without an icon glyph that
 * could collide with the active marker.
 */
@Composable
private fun AddPrinterRow(
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BorderStroke(2.dp, t.accentLine), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "+ Add printer",
            color = t.accent,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(20f, t.fs).sp,
        )
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
            fontSize = fsSp(16f, t.fs).sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${printer.host}:${printer.port}",
            color = t.text2,
            fontFamily = GeistMono,
            fontSize = fsSp(14f, t.fs).sp,
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
        fontSize = fsSp(13f, t.fs).sp,
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
        fontFamily = GeistMono,
        fontWeight = FontWeight.Medium,
        fontSize = fsSp(13f, t.fs).sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}
