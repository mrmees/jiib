package works.mees.jiib.render

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import works.mees.jiib.preview.PreviewPlaceholderBox
import works.mees.jiib.theme.ThemeTokens

/**
 * The Compose↔Views interop seam that hosts the classic-Views [WebcamView] inside a Compose tree via
 * `AndroidView` — the EXACT [GraphViewHost] factory-once/update-push shape (ADR-0001 hybrid). The CALLER
 * (the webcam screen, plan 10-06) collects the active [ThemeTokens] and the latest decoded [frame] +
 * chrome state and passes them in; on every recomposition the `update` block PUSHES those into the SAME
 * [WebcamView] instance — `factory` runs once, so a dark→light/custom flip recolors all the chrome
 * (badge / reconnect overlay / cycle overlay / dead-end card) WITHOUT recreating the View (no jank).
 *
 * The host is intentionally THIN — it holds NO decode/poll/retry logic (that is the holder + the
 * `MjpegStreamDecoder`/`SnapshotPoller` services, plan 10-06). Its contract is a simple `frame: Bitmap?`
 * the screen collects from the decoder's drop-behind flow and the chrome inputs the holder resolves.
 * Everything happens in `update`:
 *  - `view.applyTokens(tokens)` recolors the pre-allocated chrome paints + `invalidate()`s (theme swap);
 *  - `view.setTransform(...)` applies the selected cam's `flip_*`/`rotation` (pixel-square draw);
 *  - `view.setFrame(frame)` hands the latest decoded frame (or `null`) + `invalidate()`s;
 *  - `view.setChrome(mode, camName, serviceName, multiCam)` sets the D-03/D-04/D-11 + cycle overlay state.
 *
 * @param tokens       the active resolved tokens (the caller collects the flow; THEME-01 — never raw).
 * @param frame        the latest decoded webcam frame, or `null` (no frame yet / hard dead-end). Owned and
 *                     recycled by the decoder's drop-behind seam; the View only references it for the blit.
 * @param mode         the chrome mode (Live / SnapshotFallback / Reconnecting / DeadEnd — D-03/D-04/D-11).
 * @param camName      the selected cam's Moonraker name (the cycle overlay label).
 * @param serviceName  the detected service string for the dead-end card body (D-04; never a tokened URL).
 * @param multiCam     whether the cycle overlay shows (full-focus + multiple cams — camera_feed note).
 * @param flipHorizontal/[flipVertical]/[rotation] the selected cam's draw transform (Webcam.safeRotation).
 * @param modifier     caller layout for the hosted View.
 */
@Composable
fun WebcamViewHost(
    tokens: ThemeTokens,
    frame: Bitmap?,
    mode: WebcamView.Mode,
    camName: String,
    serviceName: String,
    multiCam: Boolean,
    modifier: Modifier = Modifier,
    flipHorizontal: Boolean = false,
    flipVertical: Boolean = false,
    rotation: Int = 0,
) {
    // D-05/D-04: the webcam AndroidView renders blank under @Preview (no live frame, View onDraw
    // skipped) — short-circuit to a labeled stand-in so an embedding screen previews cleanly. The
    // real MJPEG feed + chrome is the on-device gate.
    if (LocalInspectionMode.current) {
        PreviewPlaceholderBox(label = "Webcam (live on device)", modifier = modifier)
        return
    }
    AndroidView(
        factory = { ctx -> WebcamView(ctx) }, // created ONCE; never recreated on a theme/frame/chrome change
        update = { view ->
            view.applyTokens(tokens) // push-tokens + invalidate (recolor chrome, no recreation)
            view.setTransform(flipHorizontal, flipVertical, rotation) // pixel-square flip/rotation
            view.setFrame(frame) // latest decoded frame (or null) → invalidate
            view.setChrome(mode, camName, serviceName, multiCam) // D-03/D-04/D-11 + cycle overlay
        },
        modifier = modifier,
    )
}
