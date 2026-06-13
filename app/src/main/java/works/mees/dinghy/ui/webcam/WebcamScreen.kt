package works.mees.dinghy.ui.webcam

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.shape.RoundedCornerShape
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.render.Media3SurfaceHost
import works.mees.dinghy.render.Media3SurfaceProvider
import works.mees.dinghy.render.WebcamView
import works.mees.dinghy.render.WebcamViewHost
import works.mees.dinghy.state.Webcam
import works.mees.dinghy.state.selectsH264Rung
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The Webcam page (CAM-01) — the camera_feed-note design contract realized on [ScreenScaffold]
 * (Focus/Field, LAYOUT.md is LAW). All color routes through [LocalTokens] role tokens (THEME-01);
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
 * ## Back (accent, R5/R8)
 * When the cam-picker Field is shown, a [FootButtonBar] at the foot of the Field hosts the Back
 * [OutlinedControl] (the redesign grammar — D-15 / 25-06). When the Field is HIDDEN
 * (full-focus: single cam, or landscape feed on a landscape device) the screen drops the scaffold
 * for a plain feed-over-foot-bar Column so the feed takes the whole stage and the same Back bar
 * stays reachable full-width below it (CR-02 — an always-non-null field squeezed the feed to
 * ~50% of the screen).
 *
 * ## Page-visible lifecycle (D-13)
 * A [DisposableEffect] starts the holder's decode/poll/retry driver when this screen enters composition
 * and stops it on dispose (nav away) — NO wasted decode/bandwidth off-page. The shell (plan 10-07) adds
 * the spine-rebuild `cancel()` (WR-01) on top of this.
 *
 * ## H.264 rung (Phase 21)
 * For an H.264-SELECTED cam ([selectsH264Rung]) whose native stream is playing live (no Bitmap frame), the
 * Focus hosts [Media3SurfaceHost] (a raw SurfaceView + the required controls drawn as a sibling overlay
 * ABOVE it — the SurfaceView punches through, so the chrome cannot be on the surface; the spike decision).
 * The instant the composite feed falls through to MJPEG/Snapshot (a Bitmap frame arrives) the Focus
 * switches to the existing [WebcamViewHost] Bitmap path — the same `DisposableEffect` lifecycle holds.
 *
 * @param holder the Bitmap-bound orchestration holder (selected cam + frame + mode + cam list).
 * @param surfaceProvider the H.264 SurfaceView bridge shared with the composite feed (Phase 21).
 * @param onBack invoked by the accent Back foot button (R5/R8).
 */
@Composable
fun WebcamScreen(
    holder: WebcamHolder<Bitmap>,
    surfaceProvider: Media3SurfaceProvider,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val vm by holder.vm.collectAsStateWithLifecycle()

    // Page-visible lifecycle (D-13): drive while composed, stop on nav-away. WR-01 cancel() = shell (10-07).
    DisposableEffect(holder) {
        holder.start()
        onDispose { holder.stop() }
    }

    WebcamContent(
        vm = vm,
        surfaceProvider = surfaceProvider,
        onCycleCam = holder::cycleCam,
        onSelectCam = { holder.selectCam(camIdOf(it)) },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless seam for `@Preview` (D-20 / docs/ui_design/PREVIEW_AND_TOKENS.md). Drives the chrome
 * (cam-picker + FootButtonBar) from a pure [WebcamVm] fixture with no live [WebcamHolder] or socket.
 * The feed surface ([FeedFocus]) is preview-safe — [Media3SurfaceHost] and [WebcamViewHost] already
 * short-circuit to [works.mees.dinghy.preview.PreviewPlaceholderBox] under [LocalInspectionMode] (D-05).
 *
 * @param vm      the cam-picker state (fake fixture — no [WebcamHolder], no network).
 * @param surfaceProvider the H.264 bridge; in preview inspection mode the host ignores it.
 * @param onBack  callback (no-op `{}` in previews).
 */
@Composable
fun WebcamScreen(
    vm: WebcamVm<Bitmap>,
    surfaceProvider: Media3SurfaceProvider,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    WebcamContent(
        vm = vm,
        surfaceProvider = surfaceProvider,
        onCycleCam = {},
        onSelectCam = {},
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun WebcamContent(
    vm: WebcamVm<Bitmap>,
    surfaceProvider: Media3SurfaceProvider,
    onCycleCam: () -> Unit,
    onSelectCam: (Webcam) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val landscapeDevice = maxWidth > maxHeight
        val portraitFeed = isPortraitFeed(vm.selected)
        // camera_feed rule: show the Field (cam picker) only when it makes sense vs device + feed aspect
        // — AND only when there's actually more than one cam to pick between.
        val showField = vm.multiCam && when {
            landscapeDevice && portraitFeed -> true   // portrait feed leaves side room for the list
            landscapeDevice && !portraitFeed -> false  // landscape feed fills the focus → full-focus
            else -> true                               // portrait device → stack the list below (LAYOUT.md)
        }

        if (showField) {
            // CR-02: the field slot exists ONLY when the picker is shown — ScreenScaffold weights
            // focus/field 50/50 whenever BOTH slots are non-null, so an always-non-null field squeezed
            // the feed to half the screen in every full-focus case (single cam in any orientation,
            // landscape feed on a landscape device — the camera_feed rule + the KDoc above).
            ScreenScaffold(
                focus = {
                    FeedFocus(
                        vm = vm,
                        tokens = t,
                        surfaceProvider = surfaceProvider,
                        onCycle = null,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                },
                field = {
                    CamPicker(
                        cams = vm.cams,
                        selected = vm.selected,
                        onSelect = onSelectCam,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(8.dp),
                    )
                    // D-15 / 25-06: with a picker Field, Back lives in its FootButtonBar.
                    WebcamBackBar(uDp = grid.uDp, onBack = onBack)
                },
            )
        } else {
            // Full-focus (no picker): the feed takes the whole stage over a full-width foot Back
            // bar (the retired gutter strip's successor — R1) so Back stays reachable.
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    FeedFocus(
                        vm = vm,
                        tokens = t,
                        surfaceProvider = surfaceProvider,
                        // Full-focus + multiple cams → tap the feed to cycle (camera_feed note).
                        onCycle = if (vm.multiCam) onCycleCam else null,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                }
                WebcamBackBar(uDp = grid.uDp, onBack = onBack)
            }
        }
    }
}

/**
 * The shared Back [FootButtonBar] (accent, R5/R8). Hosted at the foot of the cam-picker Field
 * when the picker is shown, or as the full-width foot strip in full-focus mode (CR-02).
 */
@Composable
private fun WebcamBackBar(
    uDp: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FootButtonBar(uDp = uDp, modifier = modifier) {
        OutlinedControl(
            label = stringResource(R.string.common_back),
            onClick = onBack,
            modifier = Modifier.weight(1f),
            intent = Intent.Accent, // R5/R8 (supersedes D-10): Back = accent.
            icon = DinghyIcons.Back,
        )
    }
}

/** The Focus region: the hosted [WebcamView] feed, optionally tap-to-cycle in full-focus multi-cam. */
@Composable
private fun FeedFocus(
    vm: WebcamVm<Bitmap>,
    tokens: ThemeTokens,
    surfaceProvider: Media3SurfaceProvider,
    onCycle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val cam = vm.selected
    // The cycle overlay shows in full-focus (no Field) with multiple cams — i.e. when tap-to-cycle is wired.
    // With a Field the picker IS the cam chooser, so the in-feed overlay is suppressed.
    val showCycleOverlay = onCycle != null
    var box = modifier
    if (onCycle != null) box = box.clickable(onClick = onCycle) // tap the feed cycles cams (camera_feed)
    Box(box) {
        // H.264 rung (Phase 21): an H.264-selected cam playing LIVE (no Bitmap frame yet/at all) renders on
        // the SurfaceView via Media3SurfaceHost. The MOMENT the composite feed falls through to MJPEG/Snapshot
        // a Bitmap frame arrives → switch to the WebcamViewHost Bitmap path (which draws frame + chrome).
        val isLiveH264 = selectsH264Rung(cam ?: Webcam()) && vm.frame == null
        if (isLiveH264) {
            Media3SurfaceHost(
                surfaceProvider = surfaceProvider,
                tokens = tokens,
                mode = vm.mode,
                camName = vm.camName,
                serviceName = vm.serviceName,
                multiCam = showCycleOverlay,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            WebcamViewHost(
                tokens = tokens,
                frame = vm.frame,
                mode = vm.mode,
                camName = vm.camName,
                serviceName = vm.serviceName,
                multiCam = showCycleOverlay,
                flipHorizontal = cam?.flipHorizontal ?: false,
                flipVertical = cam?.flipVertical ?: false,
                rotation = cam?.safeRotation ?: 0,
                modifier = Modifier.fillMaxSize(),
            )
        }
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
                    text = cam.name.ifBlank { stringResource(R.string.webcam_cam_unnamed) },
                    color = if (isSelected) t.accent2 else t.text,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(20f, t.fs).sp, // R11 list-label default
                )
                if (isSelected) {
                    // Expanded Moonraker info for the selected cam (service + resolution/aspect).
                    val service = cam.service.ifBlank { stringResource(R.string.webcam_service_unknown) }
                    Text(
                        text = service,
                        color = t.text2,
                        fontFamily = GeistMono,
                        fontSize = fsSp(15f, t.fs).sp, // 15.2-06: metadata floor 15sp ([[dinghy-font-sizes-too-small]]).
                    )
                    cam.aspectRatio?.takeIf { it.isNotBlank() }?.let { aspect ->
                        Text(
                            text = stringResource(R.string.webcam_aspect_format, aspect),
                            color = t.text3,
                            fontFamily = GeistMono,
                            fontSize = fsSp(15f, t.fs).sp, // 15.2-06: metadata floor 15sp ([[dinghy-font-sizes-too-small]]).
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
