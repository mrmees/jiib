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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.config.ConnectionStore
import works.mees.dinghy.config.DiscoveredPrinter
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
 * ## Dependency injection — the screen OWNS nothing (Phase-4 boundary)
 * Per the GalleryScreen discipline, [SettingsScreen] ACCEPTS the [AppContainer] and CONSTRUCTS
 * nothing. It reads `container.connectionStore`, `container.themeResolver`, `container.themePrefs`,
 * and `container.discovery`; it opens no socket and starts no service.
 *
 * ## The seed path for a live connection
 * Saving the Connection form writes a validated [ConnectionConfig] to `ConnectionStore.save(...)`.
 * The [works.mees.dinghy.service.MoonrakerService] collects `ConnectionStore.config` and rebuilds
 * the spine on a new value (D-03) — so a save here is what brings the printer connection UP. This
 * screen must NEVER start the service itself; it only persists.
 *
 * ## Color discipline
 * Every color comes from `LocalTokens.current`; no raw color literal renders chrome here (THEME-01).
 * The accent-picker swatch palette (Task 2) carries ARGB *ints* passed to `TokenDelta.of(...)` — those
 * are theme DATA, not rendered-chrome color literals.
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
    val store = container.connectionStore

    // ---- Connection form state ---------------------------------------------------------------
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("7125") } // Moonraker's conventional default port.
    var apiKey by remember { mutableStateOf("") }   // ALWAYS blank on load — never the saved secret.
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

    // Seed the Appearance mirror once from the persisted theme prefs so the screen opens reflecting
    // the user's saved base / text-size / accent override.
    LaunchedEffect(Unit) {
        val resolved = container.themePrefs.flow.firstOrNull() ?: return@LaunchedEffect
        base = resolved.base
        fsChoice = FontScale.entries.firstOrNull { it.multiplier == resolved.fs } ?: FontScale.M
        accentArgb = resolved.deltas.overrides[TokenDelta.Role.Accent]?.toInt()
    }

    // Pre-fill host/port (NOT the key, review #10) from the current saved config, and remember
    // whether a key is already stored so the non-secret "Key saved" indicator can show.
    LaunchedEffect(Unit) {
        val cfg = store.config.firstOrNull()
        if (cfg != null) {
            host = cfg.host
            port = cfg.port.toString()
            keyAlreadySaved = cfg.apiKey != null
        }
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

        // API key — blank on load, masked entry; a non-secret indicator shows a key exists (review #10).
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
                        scope.launch {
                            // Re-save the existing host/port WITHOUT a key (explicit removal, review #12).
                            val existing = store.config.firstOrNull()
                            if (existing != null) {
                                store.save(existing.copy(apiKey = null))
                            }
                            apiKey = ""
                            keyAlreadySaved = false
                        }
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

        // Save — validate via the SAME sanitize parity used by the store, then persist.
        OutlinedControl(
            label = "Save",
            onClick = {
                val portInt = port.trim().toIntOrNull()
                // Client-side validation mirrors ConnectionStore.sanitize (reject blank host /
                // out-of-range port) so an invalid entry shows inline and never persists (T-04-04-T).
                val blankHost = host.isBlank()
                val badPort = portInt == null || portInt !in 1..65535
                hostError = blankHost
                portError = badPort
                if (!blankHost && !badPort) {
                    scope.launch {
                        // No-clobber (review #12): a blank key field PRESERVES the saved key; only a
                        // non-blank field sets a new key.
                        val typedKey = apiKey.takeIf { it.isNotBlank() }
                        val preservedKey =
                            if (typedKey == null && keyAlreadySaved) {
                                store.config.firstOrNull()?.apiKey
                            } else {
                                typedKey
                            }
                        val config = ConnectionConfig(
                            host = host.trim(),
                            port = portInt!!,
                            apiKey = preservedKey,
                        )
                        // Persist → MoonrakerService.collectLatest rebuilds the spine (D-03). Do NOT
                        // start the service from here.
                        store.save(config)
                        keyAlreadySaved = config.apiKey != null
                        apiKey = ""
                        onConnectionSaved()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            intent = Intent.Go,
        )

        // ============================ APPEARANCE ============================================
        // Each control drives BOTH the live ThemeResolver (immediate re-theme) AND ThemePrefs
        // (persist) so the choice survives a restart (D-16). The accent picker overrides ONLY the
        // --accent role; the full multi-role custom editor is deferred (the substrate supports it).
        SectionLabel("Appearance")

        // Dark / Light base.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedControl(
                label = "Dark",
                onClick = {
                    base = ThemeBase.Dark
                    container.themeResolver.setBase(ThemeBase.Dark)            // live
                    scope.launch { container.themePrefs.setBase(ThemeBase.Dark) } // persist
                },
                modifier = Modifier.weight(1f),
                intent = if (base == ThemeBase.Dark) Intent.Accent else Intent.Neutral,
            )
            OutlinedControl(
                label = "Light",
                onClick = {
                    base = ThemeBase.Light
                    container.themeResolver.setBase(ThemeBase.Light)            // live
                    scope.launch { container.themePrefs.setBase(ThemeBase.Light) } // persist
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
                        container.themeResolver.setFs(choice.multiplier)       // live
                        scope.launch { container.themePrefs.setFs(choice) }    // persist
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
                    container.themeResolver.setDeltas(TokenDelta.EMPTY)             // live
                    scope.launch { container.themePrefs.setDeltas(TokenDelta.EMPTY) } // persist
                },
            )
            for (argb in ACCENT_PALETTE) {
                AccentSwatch(
                    fillArgb = argb,
                    selected = accentArgb == argb,
                    onClick = {
                        accentArgb = argb
                        val delta = TokenDelta.of(TokenDelta.Role.Accent to argb)
                        container.themeResolver.setDeltas(delta)             // live
                        scope.launch { container.themePrefs.setDeltas(delta) } // persist
                    },
                )
            }
        }

        // Bottom breathing room so the last control clears the scroll edge.
        Box(Modifier.height(24.dp))
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
