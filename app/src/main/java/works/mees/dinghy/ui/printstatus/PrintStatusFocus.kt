package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlin.math.roundToInt
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.preview.PreviewPlaceholderBox
import works.mees.dinghy.render.ProgressRing
import works.mees.dinghy.state.PrintMetadata
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.thumbnailUrl
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.theme.seriesColor
import works.mees.dinghy.ui.spool.ActiveSpoolCardState

/**
 * Focus: the [ProgressRing] is ALWAYS drawn (gray track when idle — progress 0 shows only the
 * surface2 well; accent arc fills while printing). The ring CENTER is the "preview" slot — the live
 * % while printing, the Benchy no-job image when idle. Beneath: filename + Z/layer while printing,
 * else "Ready". Temps are NOT repeated here — they live in the field grid (Matthew, 2026-06-01).
 *
 * Extracted as a named top-level composable so each ScreenScaffold `focus` slot lambda body contains
 * ONLY a call to this function — creating an independently-restartable recomposition scope that
 * ScreenScaffold can skip (D-01/D-02 P0 fix).
 */
@Composable
internal fun PrintStatusFocus(
    state: PrinterState,
    metadata: PrintMetadata? = null,
    httpBase: String = "",
    paused: Boolean = false,
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val printing = state.printState == PrintState.Printing || state.printState == PrintState.Paused
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        // The ring is ~90% of the focus's SMALLER dimension (largest circle that fits, both orientations).
        val ringSize = minOf(maxWidth, maxHeight) * 0.9f
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(Modifier.size(ringSize)) {
                // Paused (16-06): the Printing composition DIMMED (alpha) with a static pause overlay.
                val dim = if (paused) Modifier.alpha(0.4f) else Modifier
                Box(dim.fillMaxSize()) {
                ProgressRing(
                    progress = if (printing) state.progress.toFloat() else 0f,
                    modifier = Modifier.fillMaxSize(),
                )
                // Preview slot: ~90% of the ring, circle-clipped (corners drop — preview isn't edge-to-edge).
                // Idle → the jiib brand mark (theme-accent tinted). Printing → the gcode thumbnail
                // (Coil 3) when a metadata thumbnail URL is available, else the center stays EMPTY (the
                // ring + % still read — never show the brand mark while printing).
                val thumbRel = metadata?.largestThumbRelPath
                val filename = state.printFilename
                Box(
                    Modifier.fillMaxSize(0.9f).align(Alignment.Center).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!printing) {
                        Icon(
                            painter = painterResource(R.drawable.jiib_icon),
                            contentDescription = null,
                            tint = t.accent2,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                    } else if (thumbRel != null && httpBase.isNotBlank() && filename.isNotBlank()) {
                        // D-05/D-02 preview branch: a Coil AsyncImage never loads under @Preview
                        // (LocalInspectionMode) — render the labeled placeholder so the previewed ring
                        // center is not blank, else the live cleartext-LAN thumbnail load (coil-network-
                        // okhttp on the classpath, same NSC posture as the websocket/REST).
                        if (LocalInspectionMode.current) {
                            PreviewPlaceholderBox(label = "Thumbnail", modifier = Modifier.fillMaxSize())
                        } else {
                            val thumbUrl = thumbnailUrl(httpBase, filename, thumbRel)
                            val imageRequest = remember(thumbUrl, context) {
                                ImageRequest.Builder(context)
                                    .data(thumbUrl)
                                    .build()
                            }
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    } else if (filename.isNotBlank()) {
                        // No preview thumbnail available → the filename itself lives in the ring center,
                        // marquee-scrolling if it's too long to fit on one line (Matthew, 2026-06-01).
                        Text(
                            text = filename,
                            color = t.text,
                            fontFamily = GeistMono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = fsSp(18f, t.fs).sp,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(horizontal = 10.dp).basicMarquee(),
                        )
                    }
                }
                } // end dim wrapper
                // Status label rendered OUTSIDE the dim/alpha layer: in Paused that alpha graphicsLayer
                // CLIPS to its bounds, cutting off this label's 6-o'clock overhang (2026-06-06 UAT). The
                // outer ring Box doesn't clip, so here it stays fully visible AND full-opacity (readable in
                // Paused). Centered on the ring's 6-o'clock point (box-center + R): "Ready"/"NN%"/"PAUSED".
                Text(
                    text = if (state.printState == PrintState.Printing)
                        "${(state.progress * 100).roundToInt()}%"
                    else stringResource(statusLabelRes(state.printState)),
                    color = t.text,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(30f, t.fs).sp,
                    modifier = Modifier.align(Alignment.Center).offset(y = ringSize / 2),
                )
                // Static pause overlay (NOT dimmed) centered on the ring — the Focus carries the paused
                // state (UI-SPEC Accessibility: contentDescription "Print paused"). RING-RELATIVE (sized
                // from ringSize, not a fixed sp) so it scales with the focus and can NEVER crop, in any
                // orientation (2026-06-06 UAT: fixed glyph grew/cropped strangely). The glyph fills a
                // bounded box at 40% of the ring; the font size tracks that box's dp.
                if (paused) {
                    // Render the glyph WITHOUT a fixed-size Box: a font glyph's line box is ~1.17× its
                    // fontSize, so wrapping it in a `size(fontSize)` Box capped the Text height and shaved
                    // the circle's bottom (2026-06-06 UAT). Let the glyph size itself (no height cap)
                    // and just center it on the ring — sizeDp tracks the ring so it still scales/can't crop.
                    // DinghyIconView owns the a11y semantics (the cd is the sole spoken label).
                    DinghyIconView(
                        DinghyIcons.PauseCircle,
                        tint = t.text,
                        sizeDp = (ringSize.value * 0.4f).dp,
                        contentDescription = stringResource(R.string.cd_print_paused),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            // Nothing below the ring — the ONLY focus readout is the %/READY on the ring itself.
            // Z height + layer live in the field grid (Matthew, 2026-06-01: extra lines pushed the
            // ring off the top edge; filename-when-no-thumbnail lives in the ring center).
        }
    }
}

/**
 * Standby Focus: the app-icon base + a minimal centered glance overlay (UI-SPEC Standby). The glance
 * list is intentionally short/glanceable: Nozzle · Bed · the MCU/host glance sensor (only when a real
 * `selectGlanceSensor` reading exists, else omitted — NO host-load fallback in P16) · Active spool
 * remaining (only when Spoolman is available). NO connection-state line.
 *
 * Extracted as a named top-level composable for restartability (D-01/D-02 P0 fix).
 */
@Composable
internal fun StandbyFocus(
    state: PrinterState,
    spoolmanPresent: Boolean,
    activeSpoolCardState: ActiveSpoolCardState,
    uDp: Dp,
) {
    val t = LocalTokens.current
    val nozzle = primaryHeater(state)
    val bed = state.heaters["heater_bed"]
    val glance = selectGlanceSensor(state.temperatureSensors)
    val spoolRemaining = (activeSpoolCardState as? ActiveSpoolCardState.Loaded)?.spool?.remainingWeight
    FocusFrame(
        title = stringResource(R.string.printstatus_title),
        icon = DinghyIcons.PrintStatusStandby,
        uDp = uDp,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // App-icon base (faint backdrop): the jiib brand mark. ContentScale.Fit (Focus Frame law:
            // content FITS the frame, never clips — fixes the flox half-focus crop). The leftover space
            // is now the FocusFrame's surface fill, so Fit no longer reads as empty margins (the
            // 2026-06-06 reason for Crop). Themed via accent2 tint, faint under the glance list.
            Image(
                painter = painterResource(R.drawable.jiib_icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(t.accent2),
                alpha = 0.45f,
                modifier = Modifier.fillMaxSize(),
            )
            // The centered glance list overlaid on the backdrop.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GlanceRow("nozzle-temp", stringResource(R.string.printstatus_nozzle_label), tempActive(nozzle), t.seriesColor(0))
                GlanceRow("heat-bed", stringResource(R.string.printstatus_bed_label), tempActive(bed), t.seriesColor(1))
                glance?.let { GlanceRow("glance", glanceLabel(it.name), "${fmt(it.temperature)}", t.text) }
                if (spoolmanPresent && spoolRemaining != null) {
                    GlanceRow("spool", stringResource(R.string.printstatus_spool_label), "${spoolRemaining.roundToInt()} g", t.text)
                }
            }
        }
    }
}

/** One glance line: dim caption + GeistMono value (focus-tier — "large and in charge", 2026-06-06 UAT). */
@Composable
internal fun GlanceRow(key: String, label: String, value: String, valueColor: Color) {
    val t = LocalTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.text2, fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = fsSp(23f, t.fs).sp)
        Text(value, color = valueColor, fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = fsSp(34f, t.fs).sp)
    }
}

/** Friendly glance-sensor label: strip the `temperature_sensor ` prefix, fall back to the raw key. */
internal fun glanceLabel(name: String): String =
    name.removePrefix("temperature_sensor ").ifBlank { name }

/**
 * Terminal Focus: a clean result hero — the gcode thumbnail (Coil) when available, else the app-icon
 * fallback. NO ring, NO dim, NO result-icon overlay (UI-SPEC Terminal). Complete/Cancelled/Error share
 * this treatment.
 *
 * Extracted as a named top-level composable for restartability (D-01/D-02 P0 fix).
 */
@Composable
internal fun TerminalFocus(state: PrinterState, metadata: PrintMetadata?, httpBase: String, uDp: Dp) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val thumbRel = metadata?.largestThumbRelPath
    val filename = state.printFilename
    FocusFrame(
        title = filename.ifBlank { stringResource(R.string.printstatus_title) },
        icon = DinghyIcons.PrintStatusStandby,
        uDp = uDp,
        modifier = Modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val size = minOf(maxWidth, maxHeight) * 0.9f
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                if (thumbRel != null && httpBase.isNotBlank() && filename.isNotBlank()) {
                    // D-05/D-02 preview branch: Coil doesn't load under @Preview → labeled placeholder.
                    if (LocalInspectionMode.current) {
                        PreviewPlaceholderBox(
                            label = "Thumbnail",
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(t.rCard)),
                        )
                    } else {
                        val thumbUrl = thumbnailUrl(httpBase, filename, thumbRel)
                        val imageRequest = remember(thumbUrl, context) {
                            ImageRequest.Builder(context)
                                .data(thumbUrl)
                                .build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(t.rCard)),
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        tint = t.accent2,
                        modifier = Modifier.fillMaxSize(0.7f).alpha(0.6f),
                    )
                }
                // The result label centered at the ring-bottom analog — the terminal outcome.
                Text(
                    stringResource(statusLabelRes(state.printState)),
                    color = t.text,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(22f, t.fs).sp,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
