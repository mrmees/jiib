package works.mees.dinghy.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.ui.route.Dest

/**
 * The swipe-up full-screen **App Drawer** (D-14, SHELL-01) — the app's ONE navigation surface. It is a
 * full-bleed grid of SQUARE outline tiles (docs/ui_design/images/02-app-drawer.png): live shell
 * destinations plus the greyed "coming soon" Devices tile and the red **Power** tile, all
 * disabled/no-op until later phases wire them.
 *
 * ## Why greyed tiles are inert (T-04-07-E)
 * The red Power tile would, once wired, issue a host-power command — a destructive/elevation path. In
 * Phase 4 it (and every other unbuilt tile) renders DISABLED with a no-op `onClick`, so the drawer
 * cannot reach an unbuilt or destructive destination. `ShellPresenceTest` asserts the greyed tiles do
 * not navigate.
 *
 * ## Navigation grammar (LAYOUT.md)
 * The drawer is the one screen with NO gutter and NO other way out — its tiles ARE the navigation
 * (LAYOUT.md "the app drawer omits it because their Field tiles are the navigation"), so it carries
 * Settings + Power directly. Tapping a LIVE tile fires [onDestination] then [onDismiss] (collapse).
 * It is hosted as a full-screen [Dialog] so it floats over the active destination canvas and a tap
 * outside / system Back dismisses it (the shell also wires `BackHandler`).
 *
 * Static styling only (D-13): outline + faint fill, no looping/breathing animation (Adreno-320 floor).
 * Every color routes through [LocalTokens] — NO raw color literal (THEME-01).
 *
 * ## Runtime greyed-gating (D-08, plan 10-07)
 * Most tiles' live-vs-greyed state is a COMPILE-TIME property of [DrawerTileSpec.dest] (`null` = greyed
 * "coming soon"). The Webcam tile is the deliberate DEPARTURE (D-08, the opposite of Phase-9's hide-the-
 * tile): it is ALWAYS shown, GREYED when the current session enumerated 0 cams and LIVE when ≥1 — a
 * RUNTIME condition the static tile set cannot express. The shell threads [webcamEnabled]
 * (`AppContainer.webcamCount > 0`) in; [DrawerTile] folds it into its live-decision so the Webcam tile
 * greys/lives without any change to the (already-correct) greyed STYLING. Default `false` so an
 * idle/no-session drawer greys it.
 *
 * @param onDestination invoked with the chosen LIVE [Dest] (the shell sets it as the active route).
 * @param onDismiss     collapse the drawer (also called after a live-tile tap).
 * @param webcamEnabled D-08 runtime gate: the Webcam tile is LIVE only when this is true (≥1 cam).
 * @param spoolEnabled  D-02 capability gate: the Spool tile is LIVE only when the connected printer has
 *   the Moonraker `spoolman` component (`AppContainer.spoolmanPresent`). The SAME runtime-greying shape as
 *   [webcamEnabled] — greyed (hairline) when the component is absent, accent-outline live when present.
 * @param activeName D-03 active-printer indicator: the active profile's display name, rendered as a 15sp
 *   `t.text2` ellipsized subtitle under the **Devices** tile label (the ONLY tile that gains a subtitle).
 *   Null when no active profile (0 profiles → no subtitle; the tile routes through the same Connect flow).
 */
@Composable
fun AppDrawer(
    onDestination: (Dest) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    webcamEnabled: Boolean = false,
    spoolEnabled: Boolean = false,
    activeName: String? = null,
) {
    val t = LocalTokens.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4), // denser grid — ~8 tiles visible in landscape (was 2 huge tiles)
            modifier = modifier
                .fillMaxSize()
                .background(t.bg)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(DRAWER_TILES, key = { it.label }) { tile ->
                DrawerTile(
                    tile = tile,
                    webcamEnabled = webcamEnabled,
                    spoolEnabled = spoolEnabled,
                    // D-03: only the Devices tile carries a subtitle (the active printer's name).
                    subtitle = if (tile.dest == Dest.Devices) activeName else null,
                    onClick = {
                        // Only LIVE tiles navigate; greyed tiles are no-op (their dest is null).
                        tile.dest?.let {
                            onDestination(it)
                            onDismiss()
                        }
                    },
                )
            }
        }
    }
}

/**
 * One drawer tile description. A LIVE tile carries a [dest]; a greyed "coming soon" tile has `dest =
 * null` (no-op) and is rendered disabled. [danger] flags the red Power tile (stop-intent outline,
 * still disabled in Phase 4). [beta] flags a development/"beta" feature: when the tile is LIVE it
 * renders in the amber `--heat` "proceed-at-peril" token (THEMING.md) to signal it is still
 * experimental — currently only the Webcam tile (camera feed is an ongoing beta; see the greyed-gating
 * note below). Beta-amber applies ONLY to the LIVE styling; the greyed path is untouched.
 */
private data class DrawerTileSpec(
    val label: String,
    val symbol: String, // Material Symbols ligature name (see MaterialSymbol)
    val dest: Dest?,
    val danger: Boolean = false,
    val beta: Boolean = false,
)

/**
 * The drawer tile set (docs/ui_design/images/02-app-drawer.png). Status + Settings + Move + Temp +
 * Files + Extrude + Macros + Console + Calibration are LIVE; Devices remains greyed "coming soon"; Power is the red,
 * greyed, deliberately-inert host-power tile (T-04-07-E — wiring deferred to a later phase). The
 * Extrude tile (was the generic "Tools"/wrench) routes to the Extrude panel and is named for its
 * function.
 */
private val DRAWER_TILES: List<DrawerTileSpec> = listOf(
    DrawerTileSpec(label = "Status", symbol = "monitoring", dest = Dest.PrintStatus),
    DrawerTileSpec(label = "Move", symbol = "open_with", dest = Dest.Move),
    DrawerTileSpec(label = "Temp", symbol = "thermostat", dest = Dest.Temperature),
    DrawerTileSpec(label = "Files", symbol = "folder", dest = Dest.Files),
    DrawerTileSpec(label = "Extrude", symbol = "output_circle", dest = Dest.Extrude),
    DrawerTileSpec(label = "Macros", symbol = "code", dest = Dest.Macros),
    // `terminal` is unused elsewhere in DRAWER_TILES (icon-no-repeat law, RESEARCH Open-Q1) and distinct
    // from the Console screen's own gutter glyphs (thermostat/videocam/chat_bubble/arrow_back).
    DrawerTileSpec(label = "Console", symbol = "terminal", dest = Dest.Console),
    // `tune` is the single Calibration tile (D-14) — unique among DRAWER_TILES glyphs (icon-no-repeat
    // law). It opens the Calibration hub, which sub-routes to all five routine pages (09-07).
    DrawerTileSpec(label = "Calibration", symbol = "tune", dest = Dest.Calibration),
    // Fine-Tune (17-06, TUNE-01 / D-21) — the live-adjust panel. `instant_mix` (the sliders/mixer glyph)
    // is DISTINCT from Calibration's `tune` (icon-no-repeat law) and reads as "live adjustment". Opens the
    // Fine-Tune Hub, which sub-routes to Motion / Extrusion / FW-Retraction (a LOCAL back-stack within
    // Dest.FineTune). It is the always-reachable entry alongside the Print-Status Tune shortcut.
    DrawerTileSpec(label = "Fine-Tune", symbol = "instant_mix", dest = Dest.FineTune),
    // The Webcam tile carries a LIVE [dest] but is RUNTIME-gated (D-08): greyed when the session
    // enumerated 0 cams, live at ≥1 — the gating input is `webcamEnabled`, folded into [DrawerTile]'s
    // live-decision (NOT a compile-time `dest = null`, which would grey it permanently). `photo_camera`
    // is unique among DRAWER_TILES glyphs (icon-no-repeat law; `videocam` is a Console gutter glyph).
    // `beta = true`: when LIVE this tile renders in the amber `--heat` (`t.heat`) "proceed-at-peril"
    // token as a BETA/development indicator — the camera feed is an ongoing beta (MJPEG/snapshot only,
    // WebRTC deferred SC-4) and may later be gated behind device-performance capability. The D-08
    // greyed-gating is UNCHANGED (0 cams → still greyed hairline/`t.text3`); beta-amber is the LIVE
    // styling only.
    DrawerTileSpec(label = "Webcam", symbol = "photo_camera", dest = Dest.Webcam, beta = true),
    // The Spool tile carries a LIVE [dest] but is RUNTIME capability-gated (D-02), the SAME shape as the
    // Webcam tile: greyed when the connected printer lacks the Moonraker `spoolman` component, live when
    // it has it. The gating input is `spoolEnabled` (AppContainer.spoolmanPresent), folded into
    // [DrawerTile]'s live-decision (NOT a compile-time `dest = null`, which would grey it permanently).
    // `inventory_2` is unique among DRAWER_TILES glyphs (icon-no-repeat law). No `beta = true` — Spool is
    // not a development flag (the camera feed is).
    DrawerTileSpec(label = "Spool", symbol = "inventory_2", dest = Dest.Spool),
    // Printers (15.2-04 D-01/D-02) is LIVE — the printer switcher + connection editor (Dest.Devices, the
    // kept enum constant; only the LABEL changed Devices→"Printers"). `cable` is unique among DRAWER_TILES
    // glyphs (icon-no-repeat law). It is the ONLY tile that gains a subtitle: the active printer's name
    // (D-03), threaded in as `activeName` and rendered under the 16sp label.
    DrawerTileSpec(label = "Printers", symbol = "cable", dest = Dest.Devices),
    // Theme (15.2-04 D-03) — the per-printer look (promoted theme editor). `palette` is unique among
    // DRAWER_TILES glyphs (icon-no-repeat law).
    DrawerTileSpec(label = "Theme", symbol = "palette", dest = Dest.Theme),
    DrawerTileSpec(label = "Settings", symbol = "settings", dest = Dest.Settings),
    // About (15.2-04 D-05) — app-global items + the dev-enable toggle. `info` is unique among DRAWER_TILES
    // glyphs (icon-no-repeat law).
    DrawerTileSpec(label = "About", symbol = "info", dest = Dest.About),
    // Forward-stub tiles (D-02 / SC-1, Phase 16): Output controls (P18) and System Info (P19) are GREYED,
    // non-navigating placeholders (`dest = null`) — they appear in the drawer so the surfaces are
    // discoverable but are inert until their phases wire them. Drawer ONLY (NOT the Standby launcher grid).
    // `bolt` (Output: fans/lights/pins) and `memory` (System Info) are unique among DRAWER_TILES glyphs
    // (icon-no-repeat law).
    DrawerTileSpec(label = "Output", symbol = "bolt", dest = null),
    DrawerTileSpec(label = "System Info", symbol = "memory", dest = null),
    DrawerTileSpec(label = "Power", symbol = "power_settings_new", dest = null, danger = true),
)

/**
 * A single square outline tile. LIVE tiles use the accent outline + full text and are clickable; greyed
 * tiles use the hairline outline + dimmed text and are NOT clickable (no click action — so a UI test
 * sees the tile but it cannot navigate). The red Power tile keeps the stop outline even while disabled
 * so its destructive intent reads, but it is equally inert (T-04-07-E).
 */
@Composable
private fun DrawerTile(
    tile: DrawerTileSpec,
    webcamEnabled: Boolean,
    spoolEnabled: Boolean,
    subtitle: String?,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    // A tile is LIVE when it carries a [dest] AND (for the runtime-gated Webcam/Spool tiles) the runtime
    // enablement holds. The Webcam tile greys when `!webcamEnabled` (0 cams) and the Spool tile greys when
    // `!spoolEnabled` (no spoolman component, D-02) — both via the SAME greyed styling below; only these
    // enablement INPUTS are runtime (the styling was already correct).
    val live = tile.dest != null &&
        (tile.dest != Dest.Webcam || webcamEnabled) &&
        (tile.dest != Dest.Spool || spoolEnabled)
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = when {
        tile.danger -> t.stop          // red Power tile — destructive intent reads even while disabled.
        live && tile.beta -> t.heat    // live BETA tile (Webcam) — amber "proceed-at-peril" / development flag.
        live -> t.accentLine           // live tile — accent outline (Status/Settings).
        else -> t.hair                 // greyed "coming soon" — hairline outline.
    }

    val base = Modifier
        .fillMaxSize()
        .aspectRatio(1f)               // sacred square (LAYOUT.md NON-NEGOTIABLE 2).
        .clip(shape)
        .background(if (live) t.surface2 else t.surface)
        .border(BorderStroke(2.dp, outline), shape)

    // Live tiles get a real click action; greyed tiles are inert — no clickable, marked disabled for a11y.
    val tileModifier = if (live) {
        base.clickable(onClick = onClick)
    } else {
        base.semantics { disabled() }
    }

    val contentColor = when {
        tile.danger -> t.stop
        live && tile.beta -> t.heat    // live BETA tile (Webcam) — amber icon + label as a development flag.
        live -> t.text
        else -> t.text3
    }

    Box(tileModifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            MaterialSymbol(
                name = tile.symbol,
                tint = contentColor,
                sizeSp = fsSp(40f, t.fs),
            )
            Text(
                text = tile.label,
                color = contentColor,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(16f, t.fs).sp,
                textAlign = TextAlign.Center,
            )
            // D-03 active-printer indicator: the ONLY tile with a subtitle (the Devices tile, when an
            // active printer exists). 15sp metadata floor, Regular `t.text2`, single-line ellipsized.
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = t.text2,
                    fontFamily = Geist,
                    fontWeight = FontWeight.Normal,
                    fontSize = fsSp(15f, t.fs).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
