package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import works.mees.dinghy.R
import kotlinx.collections.immutable.ImmutableMap
import works.mees.dinghy.designsystem.components.FocusEdge
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.state.KlippyState
import works.mees.dinghy.state.PrintMetadata
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.thumbnailUrl
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.ui.spool.ActiveSpoolCardState

/**
 * The universal home Focus (was StandbyFocus). Renders for EVERY printer state in the collapsed
 * skeleton: a 1U-header FocusFrame (two-axis state title + optional printer name), a faint brand
 * watermark at 30% of the focus's smaller edge pinned bottom-end, and a top-start glance block
 * (Nozzle · Bed · glance sensor · spool remaining) at uniform focusHero sizing.
 *
 * E-stop: PrintStatus owns its e-stop via the FocusFrame header (AppShell screenOwnsEstop); the
 * header shows it only when `isPrinting && onEmergencyStop != null`. We pass both so e-stop stays
 * reachable while Printing/Paused. During Klipper Error/Shutdown the firmware is already halted, so
 * the header e-stop is intentionally absent. Named top-level composable for an independently-
 * restartable scope (D-01/D-02).
 */
@Composable
internal fun HomeFocus(
    state: PrinterState,
    printerName: String?,
    isMultiPrinter: Boolean,
    spoolmanPresent: Boolean,
    activeSpoolCardState: ActiveSpoolCardState,
    heaterColors: ImmutableMap<String, Color>,
    onEmergencyStop: (() -> Unit)?,
    uDp: Dp,
    printMetadata: PrintMetadata? = null,
    httpBase: String = "",
) {
    val t = LocalTokens.current
    val isPrinting = state.printState == PrintState.Printing || state.printState == PrintState.Paused
    val isPaused = state.printState == PrintState.Paused
    // Active-print treatment only for a LIVE print (Printing/Paused) that is NOT a Klippy fault.
    // Error/Shutdown stay the digest even if printState is a stale Printing — matches the title
    // precedence in homeStateLabelRes (R-CDX-2). isPrinting (for the e-stop) is unchanged.
    val klippyFault = state.klippyState == KlippyState.Shutdown || state.klippyState == KlippyState.Error
    val showActivePrint = isPrinting && !klippyFault

    val stateLabel = stringResource(homeStateLabelRes(state.printState, state.klippyState))
    val nameStatePart =
        if (isMultiPrinter && !printerName.isNullOrBlank()) "$printerName · $stateLabel" else stateLabel
    // Active print appends the live percent: "PRINTING · 42%" / "PAUSED · 42%".
    val title = if (showActivePrint) "$nameStatePart · ${progressPercent(state.progress)}%" else nameStatePart

    // Accent perimeter progress while printing; amber (heat) while paused — the in-frame paused signal.
    val edge = if (showActivePrint) {
        FocusEdge.Progress(state.progress.toFloat(), color = if (isPaused) t.heat else t.accent)
    } else {
        FocusEdge.Neutral
    }

    FocusFrame(
        title = title,
        icon = DinghyIcons.PrintStatusStandby,
        uDp = uDp,
        modifier = Modifier.fillMaxSize(),
        edge = edge,
        isPrinting = isPrinting,
        onEmergencyStop = onEmergencyStop,
        onPanic = onEmergencyStop,
    ) {
        if (showActivePrint) {
            ActivePrintFocus(state = state, printMetadata = printMetadata, httpBase = httpBase)
        } else {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                // Brand watermark: 30% of the smaller edge, bottom-end, faint accent2 tint.
                val markSize = minOf(maxWidth, maxHeight) * 0.30f
                Image(
                    painter = painterResource(R.drawable.jiib_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(t.accent2),
                    alpha = 0.45f,
                    modifier = Modifier.size(markSize).align(Alignment.BottomEnd),
                )
                // Non-printing (incl. Klippy Error/Shutdown): the state digest.
                HomeDigest(
                    state = state,
                    spoolmanPresent = spoolmanPresent,
                    activeSpoolCardState = activeSpoolCardState,
                    heaterColors = heaterColors,
                    modifier = Modifier.align(Alignment.TopStart),
                )
            }
        }
    }
}

/**
 * The active-print Focus body (Printing/Paused): the file thumbnail filling the frame (Fit, centered)
 * under a uniform 50% surface scrim, with a single CENTERED 6-line data block reading as one list over
 * the image — filename (slightly larger), then all-heater current temps, job time, print/estimate,
 * filament used/total, Z height, and layers (all normal Geist UI). Every line carries a surface-color drop shadow
 * so it pops on busy renders. The block uniformly shrinks (width AND height) to fit the focus, floor
 * 15sp. No thumbnail (null metadata / inspection mode) → the faint brand watermark. The clockwise
 * perimeter progress stroke is the FocusFrame edge (owned by the caller).
 */
@Composable
private fun ActivePrintFocus(
    state: PrinterState,
    printMetadata: PrintMetadata?,
    httpBase: String,
) {
    val t = LocalTokens.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val thumbUrl = remember(httpBase, state.printFilename, printMetadata?.largestThumbRelPath) {
        val rel = printMetadata?.largestThumbRelPath
        if (httpBase.isNotBlank() && state.printFilename.isNotBlank() && rel != null) {
            thumbnailUrl(httpBase, state.printFilename, rel)
        } else {
            null
        }
    }

    // --- Resolve the data lines (filename is separate; the rest are the shrinking "list"). ---
    val filename = printFileBasename(state.printFilename)
    val currentZ = state.gcodePosition?.getOrNull(2)
    val totalLayer = (state.totalLayer ?: printMetadata?.layerCount)?.takeIf { it > 0 }
    val currentLayer = state.currentLayer?.takeIf { it > 0 } ?: deriveCurrentLayer(
        currentZ = currentZ,
        firstLayerHeight = printMetadata?.firstLayerHeight,
        layerHeight = printMetadata?.layerHeight,
        totalLayer = totalLayer,
    )
    val heatersLine = formatHeatersLine(state.heaters)
    val jobLine = stringResource(R.string.printstatus_job_time, formatPrintDuration(state.totalDuration))
    val printLine = stringResource(
        R.string.printstatus_print_time,
        formatPrintVsEstimate(state.printDuration, printMetadata?.estimatedTime),
    )
    val filamentLine = stringResource(
        R.string.printstatus_filament,
        formatFilament(state.filamentUsed, printMetadata?.filamentTotal),
    )
    val zLine = stringResource(R.string.printstatus_z_height, formatZHeight(currentZ, printMetadata?.objectHeight))
    val layersLine = formatLayersLine(currentLayer, totalLayer)
    val dataLines: List<String> = buildList {
        if (heatersLine.isNotBlank()) add(heatersLine)
        add(jobLine)
        add(printLine)
        add(filamentLine)
        add(zLine)
        layersLine?.let { add(it) }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Background: thumbnail (Fit, centered) or watermark fallback.
        if (thumbUrl != null && !inspection) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(thumbUrl).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(R.drawable.jiib_icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(t.accent2),
                alpha = 0.45f,
                modifier = Modifier.fillMaxSize(0.30f).align(Alignment.BottomEnd),
            )
        }
        // Uniform ~50% scrim across the whole body so text stays legible over any render.
        Box(Modifier.fillMaxSize().background(t.surface.copy(alpha = 0.5f)))

        // --- Uniform FILL-to-fit: one scale fits the WIDEST line to the body width AND all lines
        //     (+ fixed gaps) to the body height — growing INTO spare room as well as shrinking when
        //     cramped (owner: use the moto's leftover space). Width & height scale ~linearly w/ size. ---
        val measurer = rememberTextMeasurer()
        val maxData = fsSp(26f, t.fs)                           // measurement REFERENCE (cancels out in the fill calc)
        val minData = fsSp(15f, t.fs)
        val maxCap = fsSp(46f, t.fs)                            // grow ceiling — fill the room, don't run away
        // Whole block is normal Geist UI (screenTitle role) — owner: it's the main display, make it look good.
        val nameStyleBase = DinghyType.screenTitle.toTextStyle(t, maxData * FILENAME_FACTOR)
        val dataStyleBase = DinghyType.screenTitle.toTextStyle(t, maxData)
        val gapPx = with(density) { BLOCK_LINE_GAP.toPx() }
        val availW = constraints.maxWidth.toFloat()
        val availH = constraints.maxHeight.toFloat()

        // Key on line LENGTHS (not contents): same digit-count ticks reuse the cached size, so the block
        // never resizes as live values change (owner: no jitter). Geist UI is ~proportional, so this is a
        // hair approximate on width — but holding the SIZE steady matters more here than sub-pixel width.
        val key = filename + "|" + dataLines.joinToString("¦") { it.length.toString() } + "|$availW|$availH|${t.fs}"
        val sizeFrac = remember(key) {
            val nameLayout = measurer.measure(filename, nameStyleBase, maxLines = 1, softWrap = false)
            val dataLayouts = dataLines.map { measurer.measure(it, dataStyleBase, maxLines = 1, softWrap = false) }
            // Filename is EXCLUDED from the width fit — it marquees when too long (owner) instead of
            // shrinking the whole block; the Mono data lines drive the width. Its HEIGHT still counts.
            val widestPx = dataLayouts.maxOf { it.size.width }.toFloat()
            val textHPx = (nameLayout.size.height + dataLayouts.sumOf { it.size.height }).toFloat()
            // Fixed BLOCK_LINE_GAP gaps DON'T scale with font — subtract them from the height budget
            // first (N = 1 filename + dataLines.size lines → N-1 == dataLines.size gaps), then fit text to
            // the remainder. Width fits to 96%: full-width fitting clips the last glyph's side bearing.
            val gapCount = dataLines.size
            val wFrac = if (widestPx > 0f && availW > 0f) (availW * 0.96f) / widestPx else 1f
            val hFrac = if (textHPx > 0f && availH > 0f) (availH - gapPx * gapCount).coerceAtLeast(0f) / textHPx else 1f
            minOf(wFrac, hFrac)   // no 1f cap → the block grows to fill spare room, not just shrinks
        }
        val dataSp = (maxData * sizeFrac).coerceIn(minData, maxCap)
        val nameSp = (maxData * FILENAME_FACTOR * sizeFrac).coerceIn(minData, maxCap * FILENAME_FACTOR)
        val shadow = remember(t.surface, density) {
            Shadow(color = t.surface, offset = Offset(0f, with(density) { 2.dp.toPx() }), blurRadius = with(density) { 4.dp.toPx() })
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BLOCK_LINE_GAP),
            ) {
                Text(
                    text = filename,
                    color = t.text,
                    style = DinghyType.screenTitle.toTextStyle(t, nameSp).copy(shadow = shadow),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    // Indefinite scroll (owner) — overrides the "no looping animation" design law on
                    // purpose; only animates when the name actually overflows, otherwise it sits still.
                    modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                )
                dataLines.forEach { line ->
                    Text(
                        text = line,
                        color = t.text,
                        style = DinghyType.screenTitle.toTextStyle(t, dataSp).copy(shadow = shadow),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** Filename size = data size × this (owner: "slightly bigger"). Tweakable at on-device UAT. */
private const val FILENAME_FACTOR = 1.2f

/** Vertical gap between the centered data-block lines ("reads as one list"). */
private val BLOCK_LINE_GAP = 4.dp
