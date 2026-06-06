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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.firstOrNull
import works.mees.dinghy.BuildConfig
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
 * ## Section order (15.2-03 D-02 — Connection MOVED OUT to Printers)
 * **Appearance** (dark/light + S/M/L + the palette-mode chip row + a live palette-reactive preview +
 * the **"Edit theme…"** forward-entry that pushes the seed/pool editor) · **Feature toggles** (Webcam
 * live; outputs/WebRTC/fine-tune greyed "Coming soon") · **System** (app version + build only — D-12).
 *
 * The **Connection** (host/port/key + mDNS) form was REMOVED in 15.2-03 (D-02): per-printer connection
 * editing now lives on the **Printers** screen (the renamed/extended Devices switcher), which owns
 * add / remove / switch AND connection — killing the Connection-redundant-with-Devices problem the owner
 * flagged in Phase-15 UAT. The earlier F1 removal already dropped the redundant profile LIST/CRUD; this
 * drops the last per-printer surface (Connection) so Settings is purely app-level (Appearance + toggles +
 * System). Plan 04 splits Appearance → a Theme dest and adds About.
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

    // ---- Active profile (drives the Appearance theme mirror only — Connection moved to Printers) --
    val activeProfile by container.activeProfile.collectAsStateWithLifecycle(null)
    val hasActive = activeProfile != null

    // ---- Theme-editor open-state (the editor seam, hosted HERE so BOTH entry paths reach it) --
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    BackHandler(editorOpen) { editorOpen = false }
    if (editorOpen) {
        ThemeEditorScreen(container = container, onBack = { editorOpen = false }, modifier = modifier)
        return // The editor owns the whole screen while open.
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

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionHeader("Settings")

        // Connection (host/port/key + mDNS) MOVED to the Printers screen in 15.2-03 (D-02) — Settings is
        // now purely app-level. Per-printer connection editing lives on PrintersScreen (add/remove/switch
        // AND connection in one place).

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
                        container.setActiveFs(hasActive, choice) // durable (CR-01: process-lifetime writeScope)
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
