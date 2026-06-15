package works.mees.dinghy.render

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import works.mees.dinghy.preview.PreviewPlaceholderBox
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.toTextStyle

/**
 * The H.264 rung's render host (Phase 21) — the `AndroidView<SurfaceView>` analog of [WebcamViewHost],
 * mirroring its factory-once / update-push contract (ADR-0001 hybrid). The ExoPlayer (built in the H.264
 * feed, [works.mees.dinghy.ui.webcam.realH264Attempt]) renders its decoded video DIRECTLY onto this raw
 * [SurfaceView] — there is NO PlayerView and no Bitmap frame hand-off for the live H.264 path.
 *
 * ## SurfaceView punches through the view hierarchy → controls MUST be a sibling overlay ABOVE it
 * The owner's recorded spike DECISION (`21-SPIKE-RESULT.md`) is **square corners** for the H.264 video
 * surface (the SurfaceView itself is unclipped — no rounded mask), BUT the spike surfaced a NON-NEGOTIABLE
 * requirement: the SurfaceView punches through the Compose tree, so the bare spike showed NO controls. The
 * required webcam chrome (the cycle overlay name, the D-11 Reconnecting state, the D-04 dead-end card) is
 * therefore drawn here as a **sibling Compose layer Z-ORDERED ABOVE the SurfaceView** (a later child of the
 * same [Box] draws on top), so it is never hidden behind the video. "Square corners" applies ONLY to the
 * video surface; the control chrome is still on top.
 *
 * ## Surface bridge + leak guard (WR-01 / T-21-04-02)
 * The feed cannot create its own SurfaceView (it has no view tree), so the host REGISTERS its SurfaceView
 * into the shared [Media3SurfaceProvider] on `AndroidView` factory, and CLEARS it in `onReset`/`onRelease`
 * (the documented Compose-AndroidView leak guard — a stale surface is never re-attached). The player itself
 * is detached (`setVideoSurfaceView(null)`) + released in the feed's `finally` (the WR-01 discipline).
 *
 * ## Preview short-circuit
 * A live RTSP feed never renders under `@Preview`, so under [LocalInspectionMode] this stands in with a
 * labeled [PreviewPlaceholderBox] — exactly like [WebcamViewHost].
 *
 * @param surfaceProvider the shared bridge the H.264 feed awaits to attach the player surface.
 * @param tokens          the active resolved tokens (THEME-01 — the overlay chrome is never a raw color).
 * @param mode            the chrome mode (Live / Reconnecting / DeadEnd — the overlay reflects it).
 * @param camName         the selected cam's name (the cycle overlay label).
 * @param serviceName     the detected service string for the dead-end card body (never a tokened URL).
 * @param multiCam        whether the cycle overlay shows (full-focus + multiple cams — camera_feed note).
 */
@Composable
fun Media3SurfaceHost(
    surfaceProvider: Media3SurfaceProvider,
    tokens: ThemeTokens,
    mode: WebcamView.Mode,
    camName: String,
    serviceName: String,
    multiCam: Boolean,
    modifier: Modifier = Modifier,
) {
    // A live H.264 feed never renders under @Preview (no SurfaceView callbacks) — stand in cleanly.
    if (LocalInspectionMode.current) {
        PreviewPlaceholderBox(label = "Webcam H.264 (live on device)", modifier = modifier)
        return
    }

    Box(modifier.fillMaxSize()) {
        // BASE LAYER: the raw SurfaceView (square corners; the player attaches its surface via the provider).
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { surfaceProvider.register(it) }
            },
            // onReset/onRelease are the Compose-AndroidView leak guard: clear the registration so the feed
            // never re-attaches a torn-down surface (the player surface is nulled+released in the feed).
            onReset = { surfaceProvider.clear() },
            onRelease = { surfaceProvider.clear() },
            modifier = Modifier.fillMaxSize(),
        )

        // TOP LAYER (Z-ORDERED ABOVE the SurfaceView — the spike requirement): the required controls.
        Media3OverlayChrome(
            tokens = tokens,
            mode = mode,
            camName = camName,
            serviceName = serviceName,
            multiCam = multiCam,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * The required webcam controls drawn as a Compose sibling ABOVE the SurfaceView (z-order = draw order in a
 * [Box]). This is the chrome the bare spike was missing (the camera-cycle name, the D-11 Reconnecting
 * state, the D-04 dead-end card) — never hidden behind the punch-through video surface.
 */
@Composable
private fun Media3OverlayChrome(
    tokens: ThemeTokens,
    mode: WebcamView.Mode,
    camName: String,
    serviceName: String,
    multiCam: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        when (mode) {
            WebcamView.Mode.Reconnecting -> OverlayPill(
                text = "Reconnecting…",
                tokens = tokens,
                modifier = Modifier.align(Alignment.Center),
            )
            WebcamView.Mode.DeadEnd -> DeadEndCard(
                serviceName = serviceName,
                tokens = tokens,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> { /* Live / SnapshotFallback: nothing centered (the cycle name shows bottom-left). */ }
        }

        // The in-feed cycle overlay (camera_feed note): full-focus + multiple cams → the cam name pill.
        // Suppressed on the dead-end card (no live feed to cycle over there).
        if (multiCam && mode != WebcamView.Mode.DeadEnd && camName.isNotBlank()) {
            OverlayPill(
                text = camName,
                tokens = tokens,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            )
        }
    }
}

/** A small rounded chip behind chrome text (the cycle-name pill / the Reconnecting line). */
@Composable
private fun OverlayPill(
    text: String,
    tokens: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    Text(
        text = text,
        color = tokens.text,
        style = DinghyType.body.toTextStyle(tokens),
        modifier = modifier
            .background(tokens.surface, shape)
            .border(2.dp, tokens.outline, shape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

/**
 * The rung-3 dead-end card (D-04): a rounded surface naming the detected service. NO browser button, and
 * the tokened URL is NEVER drawn (only the non-secret service string — Security V7 / T-21-04-03).
 */
@Composable
private fun DeadEndCard(
    serviceName: String,
    tokens: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(tokens.rCard)
    Column(
        modifier
            .background(tokens.surface, shape)
            .border(2.dp, tokens.outline, shape)
            .padding(20.dp),
    ) {
        Text(
            text = "Feed unavailable",
            color = tokens.text,
            style = DinghyType.focusHeader.toTextStyle(tokens),
        )
        Text(
            text = if (serviceName.isBlank()) "No usable stream or snapshot." else "$serviceName has no playable stream.",
            color = tokens.text2,
            style = DinghyType.caption.toTextStyle(tokens),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}
