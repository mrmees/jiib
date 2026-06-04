package works.mees.dinghy.ui.webcam

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.render.WebcamView
import works.mees.dinghy.render.WebcamViewHost
import works.mees.dinghy.state.Webcam
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The Webcam page (CAM-01) — the camera_feed-note design contract realized on [ScreenScaffold]
 * (Focus/Field/Gutter, LAYOUT.md is LAW). All color routes through [LocalTokens] role tokens (THEME-01);
 * NO alphanumeric keyboard lives on this control surface (the connection/host config is Settings' job).
 *
 * ## Focus — the live feed ([WebcamViewHost])
 * The [WebcamView] raster surface (pixel-square never-stretch + rounded cutout + token chrome). The
 * screen feeds it the holder VM's latest frame + chrome mode (D-03 snapshot badge / D-04 dead-end /
 * D-11 reconnect / the camera_feed cycle overlay) + the selected cam's flip/rotation. In FULL-FOCUS
 * (no Field) with multiple cams, a tap anywhere on the feed cycles the cam (the View paints the in-feed
 * burst-glyph + cam-name overlay; the screen owns the tap → [WebcamHolder.cycleCam]).
 *
 * ## Field — the cam-list picker (aspect-aware show/hide, camera_feed rule)
 * Shown ONLY when it makes sense vs DEVICE orientation AND FEED aspect:
 *  - landscape device + landscape feed → NO Field (full-focus, cycle-on-tap);
 *  - landscape device + portrait feed → SHOW Field (the portrait feed leaves side room for the list);
 *  - portrait device → stack per LAYOUT.md (the list reads naturally below the feed) when >1 cam.
 * When shown, the cams list with the SELECTED one expanded to show its Moonraker info (name / service /
 * resolution). A single cam never needs the picker (full-focus).
 *
 * ## Gutter — Back ONLY (red intent, THEME-04 back=red)
 * One full-width Back tile wired `onClick = onBack`, exactly as MoveScreen wires its Back.
 *
 * ## Page-visible lifecycle (D-13)
 * A [DisposableEffect] starts the holder's decode/poll/retry driver when this screen enters composition
 * and stops it on dispose (nav away) — NO wasted decode/bandwidth off-page. The shell (plan 10-07) adds
 * the spine-rebuild `cancel()` (WR-01) on top of this.
 *
 * @param holder the Bitmap-bound orchestration holder (selected cam + frame + mode + cam list).
 * @param onBack invoked by the red Back gutter tile.
 */
@Composable
fun WebcamScreen(
    holder: WebcamHolder<Bitmap>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm by holder.vm.collectAsStateWithLifecycle()
    val t = LocalTokens.current

    // Page-visible lifecycle (D-13): drive while composed, stop on nav-away. WR-01 cancel() = shell (10-07).
    DisposableEffect(holder) {
        holder.start()
        onDispose { holder.stop() }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val landscapeDevice = maxWidth > maxHeight
        val portraitFeed = isPortraitFeed(vm.selected)
        // camera_feed rule: show the Field (cam picker) only when it makes sense vs device + feed aspect
        // — AND only when there's actually more than one cam to pick between.
        val showField = vm.multiCam && when {
            landscapeDevice && portraitFeed -> true   // portrait feed leaves side room for the list
            landscapeDevice && !portraitFeed -> false  // landscape feed fills the focus → full-focus
            else -> true                               // portrait device → stack the list below (LAYOUT.md)
        }

        ScreenScaffold(
            focus = {
                FeedFocus(
                    vm = vm,
                    tokens = t,
                    // Full-focus (no Field) + multiple cams → tap the feed to cycle (camera_feed note).
                    onCycle = if (!showField && vm.multiCam) holder::cycleCam else null,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            },
            field = if (showField) {
                {
                    CamPicker(
                        cams = vm.cams,
                        selected = vm.selected,
                        onSelect = { holder.selectCam(camIdOf(it)) },
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                }
            } else {
                null
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedControl(
                        label = "Back",
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        intent = Intent.Danger, // back = red (THEME-04 / camera_feed: gutter is Back only).
                    )
                }
            },
        )
    }
}

/** The Focus region: the hosted [WebcamView] feed, optionally tap-to-cycle in full-focus multi-cam. */
@Composable
private fun FeedFocus(
    vm: WebcamVm<Bitmap>,
    tokens: ThemeTokens,
    onCycle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cam = vm.selected
    var box = modifier
    if (onCycle != null) box = box.clickable(onClick = onCycle) // tap the feed cycles cams (camera_feed)
    Box(box) {
        WebcamViewHost(
            tokens = tokens,
            frame = vm.frame,
            mode = vm.mode,
            camName = vm.camName,
            serviceName = vm.serviceName,
            // The cycle overlay shows in full-focus (no Field) with multiple cams — i.e. when tap-to-cycle
            // is wired. With a Field the picker IS the cam chooser, so the in-feed overlay is suppressed.
            multiCam = onCycle != null,
            flipHorizontal = cam?.flipHorizontal ?: false,
            flipVertical = cam?.flipVertical ?: false,
            rotation = cam?.safeRotation ?: 0,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The Field cam picker: a scrollable list of cams, the SELECTED one expanded to show its Moonraker info
 * (name / service / resolution). Tapping a row selects+persists that cam (D-10, via the holder).
 */
@Composable
private fun CamPicker(
    cams: List<Webcam>,
    selected: Webcam?,
    onSelect: (Webcam) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(
        modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (cam in cams) {
            val isSelected = camIdOf(cam) == selected?.let { camIdOf(it) }
            val shape = RoundedCornerShape(t.rCard)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .border(BorderStroke(2.dp, if (isSelected) t.accentLine else t.outline), shape)
                    .clickable { onSelect(cam) }
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = cam.name.ifBlank { "(unnamed)" },
                    color = if (isSelected) t.accent2 else t.text,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(16f, t.fs).sp,
                )
                if (isSelected) {
                    // Expanded Moonraker info for the selected cam (service + resolution/aspect).
                    val service = cam.service.ifBlank { "unknown service" }
                    Text(
                        text = service,
                        color = t.text2,
                        fontFamily = GeistMono,
                        fontSize = fsSp(13f, t.fs).sp,
                    )
                    cam.aspectRatio?.takeIf { it.isNotBlank() }?.let { aspect ->
                        Text(
                            text = "aspect $aspect",
                            color = t.text3,
                            fontFamily = GeistMono,
                            fontSize = fsSp(12f, t.fs).sp,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Detect whether the selected cam's feed is PORTRAIT from its `aspect_ratio` (`"W:H"` string). Defaults
 * to landscape (false) when the ratio is absent/garbage — the common camera case (16:9 / 4:3). The
 * camera_feed Field-show rule keys off this.
 */
internal fun isPortraitFeed(cam: Webcam?): Boolean {
    val ratio = cam?.aspectRatio?.trim() ?: return false
    val parts = ratio.split(':', 'x', 'X')
    if (parts.size != 2) return false
    val w = parts[0].trim().toDoubleOrNull() ?: return false
    val h = parts[1].trim().toDoubleOrNull() ?: return false
    if (w <= 0.0 || h <= 0.0) return false
    return h > w
}
