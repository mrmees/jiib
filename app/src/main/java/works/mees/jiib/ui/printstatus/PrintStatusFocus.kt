package works.mees.jiib.ui.printstatus

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import kotlinx.collections.immutable.ImmutableMap
import works.mees.jiib.R
import works.mees.jiib.designsystem.components.FocusEdge
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.focus.FocusDigest
import works.mees.jiib.designsystem.focus.FocusInfoCard
import works.mees.jiib.designsystem.focus.InfoCardStyle
import works.mees.jiib.designsystem.focus.InfoStat
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.state.KlippyState
import works.mees.jiib.state.PrintMetadata
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.state.thumbnailUrl
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.ui.spool.ActiveSpoolCardState

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
    val isComplete = state.printState == PrintState.Complete
    // Active-print treatment only for a LIVE print (Printing/Paused) that is NOT a Klippy fault.
    // Error/Shutdown stay the digest even if printState is a stale Printing — matches the title
    // precedence in homeStateLabelRes (R-CDX-2). isPrinting (for the e-stop) is unchanged.
    val klippyFault = state.klippyState == KlippyState.Shutdown || state.klippyState == KlippyState.Error
    val showActivePrint = isPrinting && !klippyFault
    // Complete reuses the SAME data block; the only deltas are the header icon, a pinned 100% title,
    // and a pinned full accent ring (a finished job's live progress may have reset).
    val showComplete = isComplete && !klippyFault
    val showDataBlock = showActivePrint || showComplete

    val stateLabel = stringResource(homeStateLabelRes(state.printState, state.klippyState))
    val nameStatePart =
        if (isMultiPrinter && !printerName.isNullOrBlank()) "$printerName · $stateLabel" else stateLabel
    // Active print appends the live percent ("PRINTING · 42%"); Complete pins "· 100%".
    val title = when {
        showComplete -> "$nameStatePart · 100%"
        showActivePrint -> "$nameStatePart · ${progressPercent(state.progress)}%"
        else -> nameStatePart
    }

    // Accent perimeter progress while printing; amber (heat) while paused; full accent ring (pinned)
    // for Complete.
    val edge = when {
        showComplete -> FocusEdge.Progress(1f, t.accent)
        showActivePrint -> FocusEdge.Progress(state.progress.toFloat(), color = if (isPaused) t.heat else t.accent)
        else -> FocusEdge.Neutral
    }

    FocusFrame(
        title = title,
        icon = if (showComplete) JiibIcons.CheckCircle else JiibIcons.PrintStatusStandby,
        uDp = uDp,
        modifier = Modifier.fillMaxSize(),
        edge = edge,
        isPrinting = isPrinting,
        onEmergencyStop = onEmergencyStop,
        onPanic = onEmergencyStop,
    ) {
        if (showDataBlock) {
            ActivePrintFocus(
                state = state,
                printMetadata = printMetadata,
                httpBase = httpBase,
                isComplete = showComplete,
            )
        } else {
            FocusDigest(
                rows = homeDigestRows(
                    state = state,
                    spoolmanPresent = spoolmanPresent,
                    activeSpoolCardState = activeSpoolCardState,
                    heaterColors = heaterColors,
                    t = t,
                ),
                horizontalAlignment = Alignment.Start,
                watermark = {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val markSize = minOf(maxWidth, maxHeight) * 0.30f
                        Image(
                            painter = painterResource(R.drawable.jiib_icon),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            colorFilter = ColorFilter.tint(t.accent2),
                            alpha = 0.45f,
                            modifier = Modifier.size(markSize).align(Alignment.BottomEnd),
                        )
                    }
                },
                maxScale = 1.5f,
            )
        }
    }
}

/**
 * The active-print Focus body (Printing/Paused/Complete): the file thumbnail filling the frame
 * (Fit, centered) under a uniform 50% surface scrim via [FocusInfoCard]'s scrim flag, with a
 * single CENTERED data block (filename headline + data lines). The block uniformly grows/shrinks
 * to fit the focus via the [InfoCardStyle.CenteredBlock] archetype — floor 15sp, no measurer
 * in this file. No thumbnail (null metadata / inspection mode) → the faint brand watermark.
 * The clockwise perimeter progress stroke is the FocusFrame edge (owned by the caller).
 */
@Composable
private fun ActivePrintFocus(
    state: PrinterState,
    printMetadata: PrintMetadata?,
    httpBase: String,
    // Complete reuses this data block but shows TOTALS, not live values: on a finished job the
    // current Z / current layer are meaningless (parked toolhead), and the totals never change
    // during a print — so Complete shows total object height + total layer count only (owner 2026-07-05).
    isComplete: Boolean = false,
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
    // Complete → total object height + total layers (never change during a print); active → live values.
    val zLine = stringResource(
        R.string.printstatus_z_height,
        if (isComplete) formatTotalZHeight(printMetadata?.objectHeight)
        else formatZHeight(currentZ, printMetadata?.objectHeight),
    )
    val layersLine =
        if (isComplete) formatTotalLayersLine(totalLayer) else formatLayersLine(currentLayer, totalLayer)
    val dataLines: List<String> = buildList {
        if (heatersLine.isNotBlank()) add(heatersLine)
        add(jobLine)
        add(printLine)
        add(filamentLine)
        add(zLine)
        layersLine?.let { add(it) }
    }

    FocusInfoCard(
        stats = dataLines.map { InfoStat(icon = null, label = "", value = it) },
        style = InfoCardStyle.CenteredBlock,
        headline = filename,
        scrim = thumbUrl != null && !inspection,
        background = {
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
        },
    )
}
