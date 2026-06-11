package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.dinghy.render.Media3SurfaceProvider
import works.mees.dinghy.state.Webcam
import works.mees.dinghy.ui.webcam.WebcamScreen
import works.mees.dinghy.ui.webcam.WebcamVm

/**
 * @Preview matrix for [WebcamScreen] (25-06 / D-19 / D-20).
 *
 * ## Feed surface is preview-safe (D-05)
 * Both [works.mees.dinghy.render.Media3SurfaceHost] (H.264 rung) and
 * [works.mees.dinghy.render.WebcamViewHost] (MJPEG/Snapshot rung) already guard with
 * [androidx.compose.ui.platform.LocalInspectionMode] — they render a
 * [PreviewPlaceholderBox] in preview mode and NEVER attempt a live stream. No extra
 * LocalInspectionMode gating is needed in these previews.
 *
 * ## No live Moonraker (SC-1)
 * Every preview drives the STATELESS `WebcamScreen(vm = …)` overload with fake [WebcamVm]
 * fixtures — no [works.mees.dinghy.ui.webcam.WebcamHolder], no `collectAsStateWithLifecycle`,
 * no socket. [Media3SurfaceProvider] is instantiated with its no-arg constructor; in inspection
 * mode the H.264 host never reads it.
 *
 * ## Matrix shape (docs/ui_design/PREVIEW_AND_TOKENS.md §"Minimize proliferation")
 *  - [WebcamSingleCam] + [WebcamMultiCam] — the two primary picker states (1 cam / 3 cams) on
 *    Colorful/dark via [Nexus7Previews] (portrait + landscape).
 *  - [WebcamTheme*] siblings — the full 6-theme matrix on the single-cam state.
 *  - [WebcamFsLargeOverflow] — one fs=L shot via [fsLargeSeed] (NOT @Preview fontScale — a NO-OP).
 *  - [WebcamLandscapeMultiCam] — explicit landscape `@Preview` with 2 portrait-aspect cams to
 *    exercise the portrait-feed-leaves-side-room branch of the camera_feed rule.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Fake fixtures
// ─────────────────────────────────────────────────────────────────────────────

private val fakeSingleCam = WebcamVm<android.graphics.Bitmap>(
    cams = listOf(
        Webcam(
            name = "c270_hd_webcam",
            service = "webrtc-mediamtx",
            aspectRatio = "16:9",
        )
    ),
    selected = Webcam(
        name = "c270_hd_webcam",
        service = "webrtc-mediamtx",
        aspectRatio = "16:9",
    ),
    multiCam = false,
)

private val fakeMultiCam = WebcamVm<android.graphics.Bitmap>(
    cams = listOf(
        Webcam(
            name = "nozzle_tracker",
            service = "webrtc-mediamtx",
            aspectRatio = "4:3",
        ),
        Webcam(
            name = "c270_hd_webcam",
            service = "webrtc-mediamtx",
            aspectRatio = "16:9",
        ),
        Webcam(
            name = "overhead_cam",
            service = "mjpg-streamer",
            aspectRatio = "9:16", // portrait feed → triggers portrait-feed side-room rule in landscape
        ),
    ),
    selected = Webcam(
        name = "nozzle_tracker",
        service = "webrtc-mediamtx",
        aspectRatio = "4:3",
    ),
    multiCam = true,
)

private val fakePortraitFeedMultiCam = WebcamVm<android.graphics.Bitmap>(
    cams = listOf(
        Webcam(
            name = "nozzle_cam",
            service = "webrtc-mediamtx",
            aspectRatio = "9:16", // portrait feed — leaves side room in landscape
        ),
        Webcam(
            name = "overview",
            service = "mjpg-streamer",
        ),
    ),
    selected = Webcam(
        name = "nozzle_cam",
        service = "webrtc-mediamtx",
        aspectRatio = "9:16",
    ),
    multiCam = true,
)

private val fakeProvider = Media3SurfaceProvider()

// ─────────────────────────────────────────────────────────────────────────────
// Single-cam vs multi-cam axis (Colorful/dark — portrait + landscape via @Nexus7Previews)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Single cam — no picker Field (multiCam = false, field slot = null). Focus fills the full stage;
 * the Back FootButtonBar sits in the full-width gutter strip below it (CR-02 full-focus rule).
 * FeedFocus renders a [PreviewPlaceholderBox].
 */
@Nexus7Previews
@Composable
private fun WebcamSingleCam() =
    PreviewBox(colorfulDark) {
        WebcamScreen(vm = fakeSingleCam, surfaceProvider = fakeProvider)
    }

/**
 * Multiple cams — picker Field shown (multiCam = true, standard landscape feed). Cam list in
 * Field, selected cam expanded to show service + aspect info.
 */
@Nexus7Previews
@Composable
private fun WebcamMultiCam() =
    PreviewBox(colorfulDark) {
        WebcamScreen(vm = fakeMultiCam, surfaceProvider = fakeProvider)
    }

// ─────────────────────────────────────────────────────────────────────────────
// Full 6-theme matrix (single-cam — representative minimal state)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun WebcamThemeColorfulDark() =
    PreviewBox(colorfulDark) {
        WebcamScreen(vm = fakeSingleCam, surfaceProvider = fakeProvider)
    }

@Nexus7Previews
@Composable
private fun WebcamThemeColorfulLight() =
    PreviewBox(colorfulLight) {
        WebcamScreen(vm = fakeSingleCam, surfaceProvider = fakeProvider)
    }

@Nexus7Previews
@Composable
private fun WebcamThemeSimpleDark() =
    PreviewBox(simpleDark) {
        WebcamScreen(vm = fakeSingleCam, surfaceProvider = fakeProvider)
    }

@Nexus7Previews
@Composable
private fun WebcamThemeSimpleLight() =
    PreviewBox(simpleLight) {
        WebcamScreen(vm = fakeSingleCam, surfaceProvider = fakeProvider)
    }

@Nexus7Previews
@Composable
private fun WebcamThemeHighContrastDark() =
    PreviewBox(highContrastDark) {
        WebcamScreen(vm = fakeSingleCam, surfaceProvider = fakeProvider)
    }

@Nexus7Previews
@Composable
private fun WebcamThemeHighContrastLight() =
    PreviewBox(highContrastLight) {
        WebcamScreen(vm = fakeSingleCam, surfaceProvider = fakeProvider)
    }

// ─────────────────────────────────────────────────────────────────────────────
// fs=L overflow shot — catches FootButtonBar / text clipping at LARGEST in-app text size
// ─────────────────────────────────────────────────────────────────────────────

/**
 * fs = L via [fsLargeSeed] (NOT @Preview fontScale — that is a NO-OP; see [PreviewBox] KDoc).
 * Multi-cam state shows the most text in the picker Field (cam names, service, aspect strings).
 */
@Nexus7Previews
@Composable
private fun WebcamFsLargeOverflow() =
    PreviewBox(fsLargeSeed) {
        WebcamScreen(vm = fakeMultiCam, surfaceProvider = fakeProvider)
    }

// ─────────────────────────────────────────────────────────────────────────────
// Landscape portrait-feed spot-check (camera_feed rule — portrait feed in landscape shows Field)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Landscape device + PORTRAIT feed aspect (9:16) → the camera_feed rule shows the cam picker Field
 * even in landscape (portrait feed leaves side room). Exercises the
 * `landscapeDevice && portraitFeed -> true` branch in [WebcamScreen].
 */
@Preview(
    name = "Webcam landscape portrait-feed 800×480",
    widthDp = 800,
    heightDp = 480,
    showBackground = true,
)
@Composable
private fun WebcamLandscapeMultiCam() =
    PreviewBox(colorfulDark) {
        WebcamScreen(vm = fakePortraitFeedMultiCam, surfaceProvider = fakeProvider)
    }
