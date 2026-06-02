package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import works.mees.dinghy.R
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.render.ProgressRing
import works.mees.dinghy.state.HeaterState
import works.mees.dinghy.state.LastJob
import works.mees.dinghy.state.PrintMetadata
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.thumbnailUrl
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The Print Status home (SHELL-04) — the primary monitor surface (≈90% of interaction). Built on
 * [ScreenScaffold]; all color via [LocalTokens] (THEME-01); live numbers in GeistMono tabular numerals.
 *
 * Data is read STRICTLY from fields confirmed present in docs/moonraker-capabilities.md (real Ender 5 +
 * Ender 3) — no assumed fields. Layer info is nullable (slicer/state-dependent) → "—" fallback.
 *
 * ## Inc 1 (this pass — on-the-wire data only, NO new networking)
 *  - **Focus** (printing): [ProgressRing] with the % centered, filename + state, and a Z-height / layer
 *    line. (idle → a "Ready" temp readout.)
 *  - **Field**: a 3×2 stat grid — LAYER (cur/total) · FILAMENT (used) · NOZZLE (cur/target) · BED
 *    (cur/target) · ELAPSED (print_duration) · REMAINING (— until Inc 2 brings the slicer ETA).
 *  - **Gutter**: Stop (wired e-stop + [ConfirmGuard]); Tune/Pause are disabled placeholders.
 *
 * ## Inc 2 (this pass — ONE cached `server.files.metadata` read, keyed on the active filename)
 *  - **Ring center**: the gcode thumbnail (Coil 3 [AsyncImage]) while printing; idle → Benchy.
 *  - **Layer**: total = live `print_stats.info.total_layer`, falling back to metadata `layer_count`.
 *  - **Z cell**: live Z stays the active value; metadata `object_height` is the inactive (final-height) line.
 *  - **Remaining**: slicer-file ETA = `estimated_time × (1 − progress)`, formatted H:MM, "—" when unknown.
 *
 * ## Inc 3 (this pass — the FIELD area is state-driven by `print_stats.state`)
 *  - **(A) printing/paused** → the existing [StatGrid] (UNCHANGED).
 *  - **(B) idle + a last job exists** → a [LastJobCard] (gcode thumbnail + stat list), fed by ONE
 *    one-shot `server.history.list?limit=1&order=desc` read fetched on entering a not-printing state
 *    (and refreshed when a print completes), never polled.
 *  - **(C) idle + no history** → a centered `file_copy_off` [LastJobEmpty].
 *  - Both idle surfaces are clickable nav seams with no-op `TODO(nav)` onClicks (destinations unbuilt).
 *
 * ## Deferred
 *  - tap a temp cell → its setting page; wire the mid-print Tune button; B → past-print detail and
 *    C → file browser destination screens.
 *
 * @param container the service-locator (live `printerState` + the session dispatcher).
 */
@Composable
fun PrintStatusScreen(
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val state by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val metadata by container.printMetadata.collectAsStateWithLifecycle(initialValue = null)
    val lastJob by container.lastJob.collectAsStateWithLifecycle(initialValue = null)
    val httpBase by container.httpBase.collectAsStateWithLifecycle(initialValue = "")

    var showEstopGuard by remember { mutableStateOf(false) }
    var failureText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dispatcher) {
        failureText = null
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event ->
            when (event) {
                is DispatchEvent.Failure -> failureText = event.message
            }
        }
    }
    LaunchedEffect(failureText) {
        if (failureText != null) {
            delay(4_000)
            failureText = null
        }
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            focus = { PrintStatusFocus(state = state, metadata = metadata, httpBase = httpBase) },
            field = {
                val printing = state.printState == PrintState.Printing || state.printState == PrintState.Paused
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // State-driven field (Inc 3): printing → StatGrid (UNCHANGED); idle + history →
                    // last-job card; idle + no history → file_copy_off empty state.
                    val contentModifier = Modifier.fillMaxWidth().weight(1f)
                    when {
                        printing -> StatGrid(state = state, metadata = metadata, modifier = contentModifier)
                        lastJob != null -> LastJobCard(
                            job = lastJob!!,
                            httpBase = httpBase,
                            onClick = { /* TODO(nav): open past-print detail */ },
                            modifier = contentModifier,
                        )
                        else -> LastJobEmpty(
                            onClick = { /* TODO(nav): open file browser */ },
                            modifier = contentModifier,
                        )
                    }
                    // The estop-failure toast stays reachable in ALL branches (even idle) so a failed
                    // command still surfaces.
                    failureText?.let { msg ->
                        SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
                    }
                }
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Tune/Pause are placeholders until Inc 3 / Phase 7; Stop is wired.
                    // Pause flips to Resume while the print is paused (Matthew, 2026-06-01).
                    DisabledTile(label = "Tune", modifier = Modifier.weight(1f))
                    DisabledTile(
                        label = if (state.printState == PrintState.Paused) "Resume" else "Pause",
                        modifier = Modifier.weight(1f),
                    )
                    StopButton(
                        onTap = { showEstopGuard = true },
                        onHold = { dispatcher?.dispatch("estop", JsonRpcMethods.EMERGENCY_STOP) },
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        )

        if (showEstopGuard) {
            ConfirmGuard(
                title = "Emergency stop?",
                message = "This halts the printer.",
                confirmLabel = "STOP",
                onConfirm = {
                    dispatcher?.dispatch("estop", JsonRpcMethods.EMERGENCY_STOP)
                    showEstopGuard = false
                },
                onCancel = { showEstopGuard = false },
                destructive = true,
            )
        }
    }
}

/**
 * Focus: the [ProgressRing] is ALWAYS drawn (gray track when idle — progress 0 shows only the
 * surface2 well; accent arc fills while printing). The ring CENTER is the "preview" slot — the live
 * % while printing, the Benchy no-job image when idle. Beneath: filename + Z/layer while printing,
 * else "Ready". Temps are NOT repeated here — they live in the field grid (Matthew, 2026-06-01).
 */
@Composable
private fun PrintStatusFocus(
    state: PrinterState,
    metadata: PrintMetadata? = null,
    httpBase: String = "",
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
                ProgressRing(
                    progress = if (printing) state.progress.toFloat() else 0f,
                    modifier = Modifier.fillMaxSize(),
                )
                // Preview slot: ~90% of the ring, circle-clipped (corners drop — preview isn't edge-to-edge).
                // Idle → the Benchy no-job image (theme-accent tinted). Printing → the gcode thumbnail
                // (Coil 3) when a metadata thumbnail URL is available, else the center stays EMPTY (the
                // ring + % still read — never show Benchy while printing).
                val thumbRel = metadata?.largestThumbRelPath
                val filename = state.printFilename
                Box(
                    Modifier.fillMaxSize(0.9f).align(Alignment.Center).clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!printing) {
                        Icon(
                            painter = painterResource(R.drawable.benchy),
                            contentDescription = null,
                            tint = t.accent2,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1600f / 900f),
                        )
                    } else if (thumbRel != null && httpBase.isNotBlank() && filename.isNotBlank()) {
                        // Default Coil loader (coil-network-okhttp on the classpath) — cleartext to the
                        // LAN printer rides the same NSC posture as the websocket/REST.
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(thumbnailUrl(httpBase, filename, thumbRel))
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
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
                // Status text CENTERED ON the bottom of the circle (its center at box-center + R, R =
                // ringSize/2 = the 6-o'clock point of the drawn ring): "Ready" idle → "NN%" printing.
                Text(
                    text = if (state.printState == PrintState.Printing)
                        "${(state.progress * 100).roundToInt()}%"
                    else statusLabel(state.printState),
                    color = t.text,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(30f, t.fs).sp,
                    modifier = Modifier.align(Alignment.Center).offset(y = ringSize / 2),
                )
            }
            // Nothing below the ring — the ONLY focus readout is the %/READY on the ring itself.
            // Z height + layer live in the field grid (Matthew, 2026-06-01: extra lines pushed the
            // ring off the top edge; filename-when-no-thumbnail lives in the ring center).
        }
    }
}

/**
 * The 3×2 icon-led stat grid (mockup 03-print-status.png) — glanceable from across the room. Reads only
 * catalog-confirmed fields; a missing source shows "—" (never fabricated). Two cell shapes:
 *  - [IconTwoRowCell] (icon | active-over-inactive): Z height (altitude), Layer (layers), Nozzle/Bed temp.
 *  - [IconValueCell] (icon | single value): Elapsed (timer_arrow_up), Remaining (timer_arrow_down).
 * The "final height" (Z) and Remaining (ETA) need file metadata → "—" until Inc 2.
 */
@Composable
private fun StatGrid(state: PrinterState, metadata: PrintMetadata? = null, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val nozzle = primaryHeater(state)
    val bed = state.heaters["heater_bed"]
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTwoRowCell(
                icon = { sp -> MaterialSymbol("altitude", tint = t.text2, sizeSp = sp) },
                // Active = live Z; inactive = metadata object_height (final print height context), "—" when absent.
                active = fmtZ(state), inactive = metadata?.objectHeight?.let { fmt(it) } ?: "—", activeColor = t.text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            IconTwoRowCell(
                icon = { sp -> MaterialSymbol("layers", tint = t.text2, sizeSp = sp) },
                active = state.currentLayer?.toString() ?: "—",
                // Total = live slicer value preferred, metadata layer_count as the reliable fallback.
                inactive = totalLayers(state, metadata),
                activeColor = t.text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTwoRowCell(
                icon = { sp -> DrawableIcon(R.drawable.nozzle, t.heat, sp) },
                active = tempActive(nozzle), inactive = tempInactive(nozzle), activeColor = t.heat,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            IconTwoRowCell(
                icon = { sp -> DrawableIcon(R.drawable.heat_bed, t.heat, sp) },
                active = tempActive(bed), inactive = tempInactive(bed), activeColor = t.heat,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconValueCell(
                icon = { sp -> MaterialSymbol("timer_arrow_up", tint = t.text2, sizeSp = sp) },
                value = fmtDuration(state.printDuration), valueColor = t.text,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            // Remaining = slicer-file estimate × (1 − live progress) → H:MM; "—" when estimate unknown.
            val remainingSeconds = metadata?.estimatedTime?.let { it * (1.0 - state.progress.coerceIn(0.0, 1.0)) }
            val remaining = remainingSeconds?.takeIf { it > 0.0 }?.let { fmtDuration(it) } ?: "—"
            IconValueCell(
                icon = { sp -> MaterialSymbol("timer_arrow_down", tint = t.text2, sizeSp = sp) },
                value = remaining, valueColor = if (remaining != "—") t.text else t.text3,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}

/**
 * The idle "last completed job" card (Inc 3, mockup-grammar Field surface) — a clickable token surface
 * filling the field: the gcode thumbnail is the BACKGROUND (Coil 3 [AsyncImage], dimmed to 60% so text
 * reads; shown only when the source file still exists AND a thumbnail relative-path is known), with a
 * LEFT-ALIGNED paragraph of GeistMono stats overlaid on top. Every value reads from the [job] catalog
 * fields; an absent metadata field shows "—" (never fabricated). The whole surface is the ONE nav seam —
 * [onClick] is a one-liner to wire later (TODO(nav): past-print detail). All color via [LocalTokens].
 */
@Composable
private fun LastJobCard(
    job: LastJob,
    httpBase: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(t.rCard)
    val showThumb = job.exists && httpBase.isNotBlank() && job.largestThumbRelPath != null
    Box(
        modifier
            .clip(shape)
            .border(BorderStroke(2.dp, t.hair), shape)
            .clickable(onClick = onClick),
    ) {
        // BACKGROUND — the gcode thumbnail fills the whole card, dimmed to 60% so the overlaid text reads.
        if (showThumb) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumbnailUrl(httpBase, job.filename, job.largestThumbRelPath!!))
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.3f, // fainter background so the (larger) overlaid text reads (Matthew)
                modifier = Modifier.matchParentSize(),
            )
        }
        // FOREGROUND — larger left-aligned text. The METRICS block (the short rows) is the width ruler;
        // the filename and the Material line lock to that width and marquee-scroll when longer, so a long
        // material name no longer dictates the box width or caps the font size (Matthew). Each metadata-
        // derived row shows only when its field is present.
        val density = LocalDensity.current
        var statsWidthPx by remember(job.filename) { mutableStateOf(0) }
        val locked =
            if (statsWidthPx > 0) Modifier.width(with(density) { statsWidthPx.toDp() })
            else Modifier.wrapContentWidth()
        Column(
            Modifier.align(Alignment.CenterStart).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // Filename — scrolls within the metrics-block width.
            Text(
                text = job.filename.substringAfterLast('/').ifBlank { "—" },
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(22f, t.fs).sp,
                maxLines = 1,
                softWrap = false,
                modifier = locked.basicMarquee(),
            )
            // Material — type · name + optional swatch; scrolls within the same width (shown if present).
            if (job.filamentType != null || job.filamentName != null) {
                LastJobScrollRow(
                    symbol = "palette",
                    text = listOfNotNull(job.filamentType, job.filamentName).joinToString(" · "),
                    swatch = job.filamentColor?.let { parseHexColor(it) },
                    widthModifier = locked,
                )
            }
            // Metrics — the short rows; their widest line sizes the box (and the scrollers above).
            Column(
                Modifier.onSizeChanged { statsWidthPx = it.width },
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                LastJobStatRow("check_circle", "Status", job.status.ifBlank { "—" })
                job.endTime?.let { LastJobStatRow("event_available", "Finished", fmtFinished(it)) }
                LastJobStatRow("timer_arrow_up", "Elapsed", fmtDuration(job.printDuration))
                LastJobStatRow(
                    "hourglass_empty",
                    "Est / actual",
                    job.estimatedTime?.let { "${fmtDuration(it)} / ${fmtDuration(job.printDuration)}" } ?: "—",
                )
                LastJobStatRow(
                    "straighten",
                    "Filament",
                    "${job.filamentUsed.roundToInt()} mm" +
                        (job.filamentWeightTotal?.let { " · ${fmt(it)} g" }.orEmpty()),
                )
                LastJobStatRow("schedule", "Total", fmtDuration(job.totalDuration))
                // Slicer provenance — shown only if present.
                job.slicer?.let { s ->
                    LastJobStatRow("build", "Slicer", s + (job.slicerVersion?.let { " $it" }.orEmpty()))
                }
            }
        }
    }
}

/**
 * One icon-led stat line in the last-job card: glyph + dim label + (optional color [swatch]) + GeistMono
 * value ("—" when absent). Wrap-content so the widest line sizes the card's text box; a value longer than
 * the card ellipsizes rather than overflowing.
 */
@Composable
private fun LastJobStatRow(symbol: String, label: String, value: String, swatch: Color? = null) {
    val t = LocalTokens.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(symbol, tint = t.text2, sizeSp = fsSp(20f, t.fs))
        Text(label, color = t.text2, fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = fsSp(16f, t.fs).sp)
        if (swatch != null) {
            Box(
                Modifier.size(fsSp(16f, t.fs).dp).clip(CircleShape)
                    .background(swatch).border(BorderStroke(1.dp, t.hair), CircleShape),
            )
        }
        Text(
            value,
            color = if (value == "—") t.text3 else t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(20f, t.fs).sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A scrolling header line in the last-job card (filename-style): glyph + optional color [swatch] + a
 * single marquee value, locked to the metrics-block [widthModifier] so a long line scrolls instead of
 * widening the card or capping the font size.
 */
@Composable
private fun LastJobScrollRow(symbol: String, text: String, swatch: Color?, widthModifier: Modifier) {
    val t = LocalTokens.current
    Row(
        widthModifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(symbol, tint = t.text2, sizeSp = fsSp(18f, t.fs))
        if (swatch != null) {
            Box(
                Modifier.size(fsSp(16f, t.fs).dp).clip(CircleShape)
                    .background(swatch).border(BorderStroke(1.dp, t.hair), CircleShape),
            )
        }
        Text(
            text,
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.weight(1f).basicMarquee(),
        )
    }
}

/**
 * The idle "no print history" empty state (Inc 3) — a clickable token surface filling the field with a
 * centered `file_copy_off` Material Symbol sized by RATIO of the field's smaller dimension (no hardcoded
 * px). The whole surface is the nav seam — [onClick] is a one-liner to wire later (TODO(nav): file
 * browser). All color via [LocalTokens].
 */
@Composable
private fun LastJobEmpty(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    BoxWithConstraints(
        modifier
            .clip(shape)
            .border(BorderStroke(2.dp, t.hair), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Glyph ≈ 40% of the smaller box dimension — scales with the field, never a fixed px.
        val glyphSp = minOf(maxWidth, maxHeight).value * 0.4f
        MaterialSymbol("file_copy_off", tint = t.text3, sizeSp = glyphSp)
    }
}

/** A bespoke vector glyph (nozzle / heat_bed) tinted to a token, sized to the cell (dp ≈ the icon sp). */
@Composable
private fun DrawableIcon(resId: Int, tint: Color, sizeSp: Float) {
    Icon(painter = painterResource(resId), contentDescription = null, tint = tint, modifier = Modifier.size(sizeSp.dp))
}

/** Icon font size as a fraction of the cell height — kept SMALL so the icon is a quiet indicator and
 * the reading is the hero (Matthew: big icons distract from the values). */
private const val CELL_ICON_FRACTION = 0.45f

/** Icon span = 25% of the cell width; the reading gets the remaining 75% (Matthew, 2026-06-01). */
private const val CELL_ICON_WEIGHT = 0.25f

/**
 * Icon (LEFT, scaled to the cell height) | active-over-inactive value CENTERED in the cell. Active =
 * bold/bright, inactive = dim/smaller. The icon is pinned to the start edge while the reading sits in
 * the cell's center (Matthew, 2026-06-01 — icons left-justified, measurements centered).
 */
@Composable
private fun IconTwoRowCell(
    icon: @Composable (sizeSp: Float) -> Unit,
    active: String,
    inactive: String,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier) {
        val iconSp = maxHeight.value * CELL_ICON_FRACTION
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Icon span = 25% of the cell width, glyph centered within it.
            Box(Modifier.weight(CELL_ICON_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
                icon(iconSp)
            }
            // Value span = the remaining 75%, reading centered within it.
            Column(
                Modifier.weight(1f - CELL_ICON_WEIGHT),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(active, color = activeColor, fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = fsSp(26f, t.fs).sp)
                Text(inactive, color = t.text3, fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = fsSp(17f, t.fs).sp)
            }
        }
    }
}

/** Icon (LEFT, pinned to the start edge) | single value CENTERED in the cell — the time cells. */
@Composable
private fun IconValueCell(
    icon: @Composable (sizeSp: Float) -> Unit,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier) {
        val iconSp = maxHeight.value * CELL_ICON_FRACTION
        Row(
            Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(CELL_ICON_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
                icon(iconSp)
            }
            Box(Modifier.weight(1f - CELL_ICON_WEIGHT).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Text(value, color = valueColor, fontFamily = GeistMono, fontWeight = FontWeight.Bold, fontSize = fsSp(26f, t.fs).sp)
            }
        }
    }
}

/**
 * The gutter Stop: a red `crisis_alert` glyph (no label). TAP opens the e-stop [ConfirmGuard]; HOLD
 * (>~½ s, the system long-press) fires the e-stop IMMEDIATELY (the panic path, with haptic) — Matthew.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StopButton(onTap: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.stop), shape)
            .combinedClickable(onClick = onTap, onLongClick = onHold),
        contentAlignment = Alignment.Center,
    ) {
        MaterialSymbol("crisis_alert", tint = t.stop, sizeSp = fsSp(32f, t.fs))
    }
}

/** A greyed, disabled gutter placeholder (D-07) — neutral outline + faint label, no-op. */
@Composable
private fun DisabledTile(label: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(t.rCtrl))
            .border(BorderStroke(2.dp, t.hair), RoundedCornerShape(t.rCtrl)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = t.text3, fontFamily = GeistMono, fontWeight = FontWeight.SemiBold, fontSize = fsSp(18f, t.fs).sp)
    }
}

// --- formatters / resolution (catalog-aligned) -----------------------------------------------------

/** Primary nozzle heater: `extruder`, else the first `extruder`-prefixed heater (multi-tool naming). */
private fun primaryHeater(state: PrinterState): HeaterState? =
    state.heaters["extruder"] ?: state.heaters.entries.firstOrNull { it.key.startsWith("extruder") }?.value

/** Active (current) temp; "—" when the heater is absent. No degree symbol (saves space — Matthew). */
private fun tempActive(h: HeaterState?): String = h?.let { fmt(it.temperature) } ?: "—"

/** Inactive (target) temp; "—" when off (target 0) or absent. */
private fun tempInactive(h: HeaterState?): String =
    h?.takeIf { it.target > 0.0 }?.let { fmt(it.target) } ?: "—"

/** Live Z height (mm, 1 decimal) from gcode_position[2]; "—" until a position is known. */
private fun fmtZ(state: PrinterState): String =
    state.gcodePosition?.getOrNull(2)?.let { fmt(it) } ?: "—"

/**
 * Total layers: live slicer value (`print_stats.info.total_layer`) preferred, metadata `layer_count`
 * as the reliable fallback (catalog), else "—". Never fabricated (docs/moonraker-capabilities.md).
 */
private fun totalLayers(state: PrinterState, metadata: PrintMetadata?): String =
    (state.totalLayer ?: metadata?.layerCount)?.toString() ?: "—"

/** Duration as H:MM (≥1h) or M:SS (<1h); "—" when zero/none. */
private fun fmtDuration(seconds: Double): String {
    if (seconds <= 0.0) return "—"
    val total = seconds.roundToInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h >= 1) "$h:${m.toString().padStart(2, '0')}" else "$m:${s.toString().padStart(2, '0')}"
}

/** Tabular-friendly one-decimal formatting, rounded (not truncated). */
private fun fmt(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()

/** Format an epoch-seconds instant as a short local "Finished" stamp, e.g. "Jun 1, 9:48 PM" (java.time
 *  via core-library desugaring); "—" if the value is unparseable. */
private fun fmtFinished(epochSeconds: Double): String =
    runCatching {
        java.time.Instant.ofEpochSecond(epochSeconds.toLong())
            .atZone(java.time.ZoneId.systemDefault())
            .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a"))
    }.getOrNull() ?: "—"

/** Parse a "#RRGGBB"/"#AARRGGBB" hex color (the slicer's `filament_colors[]`) to a Compose [Color], or
 *  null when malformed — the swatch is then simply omitted. */
private fun parseHexColor(hex: String): Color? =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrNull()

/** The printer's current print state as a short uppercase label for the ring center (idle/finished
 *  states); the Printing case is rendered as the live % instead. */
private fun statusLabel(s: PrintState): String = when (s) {
    PrintState.Standby -> "STANDBY"
    PrintState.Printing -> "PRINTING"
    PrintState.Paused -> "PAUSED"
    PrintState.Complete -> "COMPLETE"
    PrintState.Cancelled -> "CANCELLED"
    PrintState.Error -> "ERROR"
}
