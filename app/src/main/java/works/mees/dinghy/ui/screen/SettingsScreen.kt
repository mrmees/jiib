package works.mees.dinghy.ui.screen

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The **Settings** drawer destination (SET-01) — after the 15.2-04 IA dissolve this is the per-printer
 * FUNCTIONALITY surface: it owns the active profile's FEATURE TOGGLES only (D-04). The three other IA
 * destinations carry what used to be lumped in here: **Printers** owns connection/add/remove/switch
 * (15.2-03 D-02), **Theme** owns the look (seed/palette/text-size/pool, 15.2-04 D-03, promoted out of the
 * old inline Appearance section), and **About** owns app-global items + the dev-enable toggle (D-05).
 *
 * This screen is therefore intentionally SMALL now — a token-themed `Column.verticalScroll` of the
 * per-profile feature toggles. It is FFG-exempt (D-15) like the other conventional surfaces; AppShell
 * suppresses the swipe-up drawer for it and supplies an explicit gutter Back.
 *
 * ## Webcam toggle — the wired tile gate (MEDIUM-4, D-04)
 * The Webcam toggle is the one LIVE feature flag this phase. It reads the ACTIVE profile's
 * [works.mees.dinghy.config.Profile.webcamEnabled] and persists via [AppContainer.setActiveWebcamEnabled]
 * (durable writeScope, lost-update-safe). Crucially this is the SAME field [AppContainer.webcamTileEnabled]
 * gates on, so flipping it here greys/lights the **Webcam** drawer tile (not just a stored boolean) — the
 * visible behavior the on-device checkpoint verifies. Per-profile: toggling on printer A leaves B
 * independent.
 *
 * ## Layout + Back (15.2-04)
 * Settings is in AppShell's swipe-suppress set (FFG-exempt scrollable surface), so it carries an explicit
 * green gutter Back via [ScreenScaffold] (the PrintersScreen/AboutScreen template) — the swipe-up drawer
 * is not its exit.
 *
 * ## Dependency injection — the screen OWNS nothing (Phase-4 boundary)
 * [SettingsScreen] ACCEPTS the [AppContainer] and CONSTRUCTS nothing.
 *
 * @param container the process-scoped service-locator (constructs nothing here).
 * @param onBack the explicit neutral gutter Back exit (D-10).
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // The active profile drives the per-profile toggle state (and is the persist target). Null when no
    // printer is configured — the toggles fall back to the default so the screen still composes.
    val activeProfile by container.activeProfile.collectAsStateWithLifecycle(null)
    val webcamOn = activeProfile?.webcamEnabled ?: true

    // Babystep app setting (D-06) — process-scoped, connection-INDEPENDENT (not per-profile). The persisted
    // layer-count seeds a local editable field; writes route through the durable container intent helpers
    // (writeScope), never a composition scope ([[dinghy-compose-write-scope-cancellation]]).
    val babystepOn by container.babystepEnabled.collectAsStateWithLifecycle(true)
    val babystepLayers by container.babystepLayers.collectAsStateWithLifecycle(5)
    // The text field mirrors the persisted layer-count; re-seed the local buffer whenever the stored value
    // changes (key = babystepLayers) so an external write reflects, while in-progress typing is preserved.
    var layersField by remember(babystepLayers) { mutableStateOf(babystepLayers.toString()) }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            field = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SectionHeader("Settings")

                    // ============================ FEATURE TOGGLES ======================================
                    // Per-profile feature flags (D-04). Webcam is the one live toggle this phase; the rest
                    // are greyed capability-gated placeholders later phases light up.
                    SectionLabel("Feature toggles")

                    // Webcam — the SAME per-profile field webcamTileEnabled gates on (MEDIUM-4): flipping
                    // it here greys/lights the Webcam drawer tile. Durable via the writeScope intent.
                    ToggleRow(
                        label = "Webcam",
                        subLabel = if (webcamOn) "Shown in the drawer when a camera is found" else "Hidden — tile greyed out",
                        checked = webcamOn,
                        enabled = activeProfile != null,
                        onToggle = { container.setActiveWebcamEnabled(it) },
                    )

                    // Greyed capability-gated placeholders (D-11) — later phases light these up.
                    ForwardEntryRow(label = "Output controls", subLabel = "Coming soon", enabled = false, onClick = { })
                    ForwardEntryRow(label = "Camera (WebRTC)", subLabel = "Coming soon", enabled = false, onClick = { })
                    ForwardEntryRow(label = "Fine-tune", subLabel = "Coming soon", enabled = false, onClick = { })

                    // ============================ BABYSTEP (D-06) ======================================
                    // The Print-Status Z-babystep first-layer offer: an enable toggle (default ON) + the
                    // first-layer WINDOW in layers (default 5). Process-scoped, NOT per-profile — durable via
                    // the container writeScope intent helpers, never a composition scope.
                    SectionLabel("Z-Babystep (first layer)")

                    ToggleRow(
                        label = "Babystep adjust",
                        subLabel = if (babystepOn) {
                            "Offered during the first layers of a print"
                        } else {
                            "Hidden — no first-layer Z adjust"
                        },
                        checked = babystepOn,
                        enabled = true, // app-global setting, always editable (not gated on an active profile)
                        onToggle = { container.setBabystepEnabled(it) },
                    )

                    // Layer-count: the standard NUMERIC keyboard (Settings is keyboard-permitted, PRIM-02).
                    // commit-on-edit: a valid positive int writes through the durable intent helper (which
                    // also coerces >= 1); a blank/invalid buffer is allowed mid-typing and just isn't persisted.
                    LabeledRow(
                        label = "First-layer window (layers)",
                        subLabel = "Show the babystep row for the first $babystepLayers layer(s)",
                        enabled = babystepOn,
                    ) {
                        TokenTextField(
                            value = layersField,
                            onValueChange = { raw ->
                                // Digits only — the numeric keyboard already restricts, this guards paste.
                                val digits = raw.filter { it.isDigit() }
                                layersField = digits
                                digits.toIntOrNull()?.let { container.setBabystepLayers(it) }
                            },
                            label = "Layers",
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    // Bottom breathing room so the last control clears the scroll edge.
                    Box(Modifier.height(24.dp))
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

/**
 * A per-profile feature toggle row — a tappable outlined row whose right edge reads the ON/OFF state as
 * an intent-colored pill. The whole row is the touch target (≥64dp via padding + text). When [enabled] is
 * false (no active profile) the row reads greyed and is inert.
 */
@Composable
private fun ToggleRow(
    label: String,
    subLabel: String?,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val outline = if (!enabled) t.outline else if (checked) t.accentLine else t.outline
    val base = Modifier
        .fillMaxWidth()
        .clip(shape)
        .border(BorderStroke(2.dp, outline), shape)
    val rowMod = if (enabled) base.clickable { onToggle(!checked) } else base
    Row(
        rowMod.padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) t.text else t.text3,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(18f, t.fs).sp,
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
        // The ON/OFF state pill — accent-outlined ON, hairline OFF. Color is a redundant cue to the
        // ON/OFF word (THEMING — no color-only meaning).
        val pillShape = RoundedCornerShape(t.rPill)
        val pillOutline = if (enabled && checked) t.accentLine else t.outline
        Box(
            Modifier
                .clip(pillShape)
                .border(BorderStroke(2.dp, pillOutline), pillShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (checked) "ON" else "OFF",
                color = if (!enabled) t.text3 else if (checked) t.accent else t.text2,
                fontFamily = Geist,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(17f, t.fs).sp,
            )
        }
    }
}

/**
 * A forward-entry row (D-11) — a tappable row with a label + optional sub-label that either opens a
 * sub-page or, when [enabled] is false, reads as a greyed capability-gated placeholder ("Coming soon").
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
 * A label + sub-label header above an arbitrary [content] control (e.g. a numeric field). When [enabled]
 * is false the label reads greyed (the control is expected to disable itself / be ignored).
 */
@Composable
private fun LabeledRow(
    label: String,
    subLabel: String?,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val t = LocalTokens.current
    Column(Modifier.fillMaxWidth()) {
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
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        content()
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
