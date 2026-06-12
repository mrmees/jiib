package works.mees.dinghy.ui.calibration

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.calibration.CalibrationHubHolder
import works.mees.dinghy.calibration.CalibrationRoutine
import works.mees.dinghy.calibration.RoutineEntry
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The Calibration hub (CALIB-01 / D-14). One drawer destination listing ALL five routines as square
 * outline tiles (mirrors [works.mees.dinghy.ui.shell.AppDrawer]'s `DrawerTile` grammar). Per the
 * UI-SPEC §1 OWNER OVERRIDE, EVERY routine renders — supported routines first (accent outline),
 * unsupported greyed (`--outline` on `--surface-2`) and sorted last, but STILL tappable so the owner
 * can open and inspect each page even when the printer didn't report the object (the greyed state IS
 * the message). [CalibrationHubHolder] already returns the supported-first ordering.
 *
 * Field-only [ScreenScaffold] (Focus omitted). Gutter = single green `Back` (the hub is reached from
 * the drawer; LAYOUT.md keeps an explicit exit). Drawer-swipe is NOT suppressed (tiles are a grid, not
 * a scroll list). Static styling only (D-13) — outline + faint fill, no looping animation
 * (Adreno-320 floor). Every color routes through [LocalTokens] — NO raw color literal (THEME-01).
 *
 * @param holder  the headless hub holder (supported-first routine list, live off capabilities).
 * @param onOpen  invoked with the tapped [CalibrationRoutine] — navigates to the routine's NavDest
 *                sub-route via navController.navigate(routine.toNavDest()) (D-07, Phase 27).
 * @param onBack  the neutral Back gutter exit (D-10).
 */
@Composable
fun CalibrationHubScreen(
    holder: CalibrationHubHolder,
    onOpen: (CalibrationRoutine) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routines by holder.routines.collectAsStateWithLifecycle()
    val t = LocalTokens.current

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            field = {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Calibration",
                        color = t.text,
                        fontFamily = Geist,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fsSp(24f, t.fs).sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(routines, key = { it.routine.name }) { entry ->
                            RoutineTile(entry = entry, onClick = { onOpen(entry.routine) })
                        }
                    }
                }
            },
            gutter = {
                Box(Modifier.fillMaxWidth().padding(8.dp)) {
                    HubActionControl(
                        label = "Back",
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        intent = Intent.Neutral, // D-10: plain nav spends no safety color.
                    )
                }
            },
        )
    }
}

/**
 * One routine tile. SUPPORTED → accent outline + full-strength text/glyph; UNSUPPORTED → hairline
 * outline + dimmed (`--surface-2` fill, `--text-3` content). BOTH are tappable (owner override §1 —
 * even greyed tiles navigate so the page can be inspected). Sacred square (`aspectRatio(1f)`).
 */
@Composable
private fun RoutineTile(entry: RoutineEntry, onClick: () -> Unit) {
    val t = LocalTokens.current
    val supported = entry.isSupported
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = if (supported) t.accentLine else t.hair
    val contentColor = if (supported) t.text else t.text3

    Box(
        Modifier
            .fillMaxSize()
            .aspectRatio(1f)
            .clip(shape)
            .border(BorderStroke(2.dp, outline), shape)
            .background(t.surface2)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            MaterialSymbol(name = entry.routine.glyph, tint = contentColor, sizeSp = fsSp(40f, t.fs))
            Text(
                text = entry.routine.label,
                color = contentColor,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(15f, t.fs).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * Hub tile glyph (Material Symbols ligature) — UNIQUE per routine on this screen (CLAUDE.md
 * "never the same glyph twice"): screws-tilt `architecture`, Z-tilt `vertical_align_center`, QGL
 * `crop_square`, bed-mesh `grid_on`, probe-calibrate `straighten`.
 */
private val CalibrationRoutine.glyph: String
    get() = when (this) {
        CalibrationRoutine.SCREWS_TILT -> "architecture"
        CalibrationRoutine.Z_TILT -> "vertical_align_center"
        CalibrationRoutine.QUAD_GANTRY_LEVEL -> "crop_square"
        CalibrationRoutine.BED_MESH -> "grid_on"
        CalibrationRoutine.PROBE_CALIBRATE -> "straighten"
    }

/** Human label for the tile. */
private val CalibrationRoutine.label: String
    get() = when (this) {
        CalibrationRoutine.SCREWS_TILT -> "Screws Tilt"
        CalibrationRoutine.Z_TILT -> "Z Tilt"
        CalibrationRoutine.QUAD_GANTRY_LEVEL -> "QGL"
        CalibrationRoutine.BED_MESH -> "Bed Mesh"
        CalibrationRoutine.PROBE_CALIBRATE -> "Probe Calibrate"
    }

@Composable
private fun HubActionControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: Intent = Intent.Neutral,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, intentColor(intent, t)), shape)
            .padding(horizontal = 12.dp, vertical = 18.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
        )
    }
}

internal fun intentColor(intent: Intent, t: ThemeTokens) = when (intent) {
    Intent.Neutral -> t.outline
    Intent.Accent -> t.accentLine
    Intent.Warn -> t.heat
    Intent.Danger -> t.stop
    Intent.Go -> t.go
}
