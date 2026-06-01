package works.mees.dinghy.ui.shell

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.ui.route.Dest

/**
 * The swipe-up full-screen **App Drawer** (D-14, SHELL-01) — the app's ONE navigation surface. It is a
 * full-bleed grid of SQUARE outline tiles (docs/ui_design/images/02-app-drawer.png): the two LIVE
 * destinations — **Status** ([Dest.PrintStatus]) and **Settings** ([Dest.Settings]) — plus the greyed
 * "coming soon" tiles (Move / Temp / Files / Tools / Macros / Devices) and the red **Power** tile, all
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
 * @param onDestination invoked with the chosen LIVE [Dest] (the shell sets it as the active route).
 * @param onDismiss     collapse the drawer (also called after a live-tile tap).
 */
@Composable
fun AppDrawer(
    onDestination: (Dest) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
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
 * still disabled in Phase 4).
 */
private data class DrawerTileSpec(
    val label: String,
    val dest: Dest?,
    val danger: Boolean = false,
)

/**
 * The drawer tile set (docs/ui_design/images/02-app-drawer.png). Status + Settings + Move + Temp +
 * Tools (→ Extrude) are LIVE; Files, Macros, Devices remain greyed "coming soon"; Power is the red,
 * greyed, deliberately-inert host-power tile (T-04-07-E — wiring deferred to a later phase). The
 * "Tools" tile routes to the Extrude panel (Phase-5 tile→panel mapping: Move→Move, Temp→Temperature,
 * Tools→Extrude); its label is kept per the LAW mockup rather than renamed.
 */
private val DRAWER_TILES: List<DrawerTileSpec> = listOf(
    DrawerTileSpec(label = "Status", dest = Dest.PrintStatus),
    DrawerTileSpec(label = "Move", dest = Dest.Move),
    DrawerTileSpec(label = "Temp", dest = Dest.Temperature),
    DrawerTileSpec(label = "Files", dest = null),
    DrawerTileSpec(label = "Tools", dest = Dest.Extrude),
    DrawerTileSpec(label = "Macros", dest = null),
    DrawerTileSpec(label = "Devices", dest = null),
    DrawerTileSpec(label = "Settings", dest = Dest.Settings),
    DrawerTileSpec(label = "Power", dest = null, danger = true),
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
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    val live = tile.dest != null
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = when {
        tile.danger -> t.stop          // red Power tile — destructive intent reads even while disabled.
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

    Box(tileModifier, contentAlignment = Alignment.Center) {
        Text(
            text = tile.label,
            color = if (live) t.text else t.text3,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(8.dp),
        )
    }
}
