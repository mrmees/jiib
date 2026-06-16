package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
 * The active-print Focus body (Printing/Paused): the file's thumbnail filling the frame (Fit,
 * centered) with top/bottom legibility scrims, the filename top-aligned (marquee on overflow), and
 * the condensed layer/height line bottom-aligned. No thumbnail (null metadata / inspection mode) →
 * the faint brand watermark. The clockwise perimeter progress stroke is the FocusFrame edge (caller).
 */
@Composable
private fun ActivePrintFocus(
    state: PrinterState,
    printMetadata: PrintMetadata?,
    httpBase: String,
) {
    val t = LocalTokens.current
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
        // Legibility scrims behind the text (top + bottom vertical gradients of the surface color).
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to t.surface.copy(alpha = 0.72f),
                    0.22f to Color.Transparent,
                    0.78f to Color.Transparent,
                    1f to t.surface.copy(alpha = 0.72f),
                ),
            ),
        )
        // Filename — top.
        Text(
            text = printFileBasename(state.printFilename),
            color = t.text,
            style = DinghyType.screenTitle.toTextStyle(t),
            maxLines = 1,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().basicMarquee(),
        )
        // Layer / height — bottom (Mono tabular). Prefer the live print_stats.info layer fields; when
        // the slicer didn't emit them (current/total null), fall back to the gcode metadata: total from
        // layer_count, current derived from the print height (Fluidd/Mainsail-style).
        val currentZ = state.gcodePosition?.getOrNull(2)
        val totalLayer = state.totalLayer ?: printMetadata?.layerCount
        val currentLayer = state.currentLayer ?: deriveCurrentLayer(
            currentZ = currentZ,
            firstLayerHeight = printMetadata?.firstLayerHeight,
            layerHeight = printMetadata?.layerHeight,
            totalLayer = totalLayer,
        )
        val layerHeightText = formatLayerHeight(
            currentZ = currentZ,
            objectHeight = printMetadata?.objectHeight,
            currentLayer = currentLayer,
            totalLayer = totalLayer,
        )
        // Bottom line is CENTERED and SHRINK-TO-FIT: scale the statValue size down (to a 15sp floor) so
        // the whole "<z>/<h>mm · <cur>/<tot> layers" fits the Focus width on the narrowest device (moto),
        // and center it so short strings (low layer/height numbers) stay balanced instead of stranded.
        // Same measurer-driven uniform-shrink approach as HomeDigest.
        val measurer = rememberTextMeasurer()
        val layerBase = DinghyType.statValue.toTextStyle(t)
        val maxScaled = layerBase.fontSize.value
        val minScaled = fsSp(15f, t.fs)
        val availPx = constraints.maxWidth.toFloat()
        val sizeSp = remember(layerHeightText, availPx, t.fs) {
            val w = measurer.measure(layerHeightText, layerBase, maxLines = 1, softWrap = false)
                .size.width.toFloat()
            // Target 96% of the width: the measured layout width undercounts the last glyph's side
            // bearing + sub-pixel rounding, so fitting to the full width clips the final letter.
            if (w <= 0f || availPx <= 0f) maxScaled
            else (maxScaled * (availPx * 0.96f) / w).coerceIn(minScaled, maxScaled)
        }
        Text(
            text = layerHeightText,
            color = t.text,
            style = DinghyType.statValue.toTextStyle(t, sizeSp),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
        )
    }
}
