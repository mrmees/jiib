package works.mees.dinghy.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.config.Profile
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
 * The **Devices switcher** (D-01) — the one genuinely-new Phase-14 surface (no hi-fi mockup; it follows
 * LAYOUT.md/THEMING.md grammar, UI-SPEC §New surface). A full-screen **Field of square printer tiles**:
 * one tile per saved [Profile] (name + `host:port`, the ACTIVE one accent-emphasised, D-03) plus a final
 * **"Add printer"** tile that jumps to Settings (D-01). Tapping a profile tile PERSISTS it active and the
 * existing rebind seam does the rest — there is NO confirm guard (switching is non-destructive, D-02) and
 * NO disconnect/publishSpine/rebind code here (T-14-11): the tap only calls
 * [works.mees.dinghy.config.ProfileStore.setActive] + signals [onSwitched].
 *
 * ## The D-02 Status-landing (the FIX-4 gate)
 * After a switch tap the screen calls [onSwitched], which the shell maps to `navigateTo(Dest.PrintStatus)`.
 * This is LOAD-BEARING: `ShellNavState.dest` is PRESERVED across the recovery Splash, so WITHOUT the
 * explicit nav the preserved `dest` would return to **Devices** after the rebind Splash — contradicting
 * D-02 (which lands on the new printer's Status). It is fine to call [onSwitched] even when tapping the
 * already-active tile (a no-op switch still lands on Status).
 *
 * ## Grammar (UI-SPEC §New surface)
 * - [ScreenScaffold] with **Field only** (the tile grid) + **Gutter** (the green Back). Focus omitted (no
 *   single primary item) — mirrors the Files scroll-Field + gutter-Back pattern.
 * - The tiles reuse the AppDrawer/`DrawerTile` SQUARE-tile grammar verbatim: `GridCells.Fixed(4)`,
 *   `aspectRatio(1f)` sacred squares, 12dp gaps, 16dp outer pad, `t.rCtrl` radius, 2dp outline, `t.bg` bg.
 * - The scrollable Field fights the global swipe-up drawer, so `Dest.Devices` joins the swipe-suppress set
 *   (AppShell) and this screen carries an explicit green `Intent.Go` gutter **Back** as its exit (D-05).
 * - Static outline + glow ONLY — NO looping/breathing animation (Adreno-320 floor). Every color routes
 *   through [LocalTokens] / role tokens — never a raw color literal (THEME-01).
 *
 * @param container   the process-scoped service-locator (the `profileStore` source of profiles/active-id).
 * @param onAddPrinter the "Add printer" tile tap → navigate to Settings (D-01).
 * @param onSwitched  the D-02 navigation hook — invoked after `setActive`; the shell maps it to
 *                    `navigateTo(Dest.PrintStatus)` so the recovery Splash lands on the new printer's Status.
 * @param onBack      the explicit green gutter Back exit (the swipe-drawer is suppressed for this Field).
 */
@Composable
fun DevicesScreen(
    container: AppContainer,
    onAddPrinter: () -> Unit,
    onSwitched: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val profiles by container.profileStore.profiles.collectAsStateWithLifecycle(emptyList())
    val activeId by container.profileStore.activeId.collectAsStateWithLifecycle(null)

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
                                // D-02: persist active → the runConfigLoop seam tears down + rebinds →
                                // RootController's recovery Splash. NO confirm guard, NO disconnect/
                                // publishSpine/manual rebind here (T-14-11) — the seam does it. onSwitched()
                                // is the explicit D-02 nav hook (→ Dest.PrintStatus in AppShell).
                                //
                                // The write goes through the container's PROCESS-scoped writeScope, NOT a
                                // rememberCoroutineScope(): onSwitched() navigates away in the SAME frame, so
                                // a composition-scoped write would be cancelled mid-`.tmp`→rename and the
                                // active-id would silently never persist (the "switch reverts to the old
                                // printer / Devices marker doesn't update" bug). setActiveProfile() outlives
                                // this composition.
                                container.setActiveProfile(profile.id)
                                onSwitched()
                            },
                        )
                    }
                    item(key = "__add_printer__") {
                        AddPrinterTile(onClick = onAddPrinter)
                    }
                }
            },
            gutter = {
                OutlinedControl(
                    label = "Back",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    intent = Intent.Go, // backing out changes nothing — Back is green (THEMING).
                    symbol = "arrow_back",
                )
            },
        )
    }
}

/**
 * One saved-printer tile. Reuses the `DrawerTile` square-tile grammar verbatim. The ACTIVE tile (D-03)
 * gets accent emphasis — accent outline + a faint `accentSoft` fill tint + a unique accent marker glyph
 * (`bolt`) — distinct from inactive saved tiles which carry the ordinary `accentLine`/`surface2` live
 * styling. The device glyph is `dns` (not the gutter's `arrow_back` nor the active marker `bolt` —
 * icon-no-repeat law).
 */
@Composable
private fun PrinterTile(
    profile: Profile,
    active: Boolean,
    onClick: () -> Unit,
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
        // The active marker (D-03): a unique accent glyph in the top-end corner.
        if (active) {
            MaterialSymbol(
                name = "bolt",
                tint = t.accent,
                sizeSp = fsSp(20f, t.fs),
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
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
 * The "Add printer" tile (D-01) — a live accent tile with an `add` glyph that jumps to Settings to add a
 * printer. `add` is unique on this screen (not `dns`/`bolt`/`arrow_back` — icon-no-repeat law).
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
