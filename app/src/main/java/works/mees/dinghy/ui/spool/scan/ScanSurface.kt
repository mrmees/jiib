package works.mees.dinghy.ui.spool.scan

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * SPOOL-05/06 — the project's FIRST on-device hardware-camera surface (D-14/D-15). A full-screen scan
 * sub-surface (NOT a drawer `Dest`) launched from the Spool screen / active-spool card `onScan` seam
 * (11-06). It hosts a CameraX [PreviewView] via [AndroidView], binds a [Preview] + an [ImageAnalysis]
 * (KEEP_ONLY_LATEST throttle) carrying the [QrCodeAnalyzer] (11-05), and — load-bearing — RELEASES the
 * camera on dispose via `ProcessCameraProvider.unbindAll()` in a [DisposableEffect] so the camera is
 * freed on pause/background/nav-away (D-14, mirroring the Phase-10 webcam DisposableEffect discipline —
 * the *pattern* transfers, the network-MJPEG *code* does not).
 *
 * ## D-14 camera discipline
 *  - [CameraSelector.DEFAULT_BACK_CAMERA] — NEVER hard-code front (front lenses are often fixed-focus on
 *    Nexus-7-class hardware; T-11-07-05).
 *  - [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST] — analyze the newest frame, drop stale (the analyzer
 *    closes each proxy in `finally`, 11-05, so the pipeline never stalls).
 *  - `unbindAll()` on dispose — the camera is a SHORT-LIVED task surface, released the instant the
 *    surface leaves composition.
 *
 * ## D-15 permission + degrade
 * [CameraPermissionGate] owns the Activity-Result CAMERA request (only on entry) and derives the
 * [ScanState]; every denial / no-camera / busy path renders a degrade panel with a "Use picker instead"
 * escape ([onUsePicker]) — the manual picker (11-06) always works without the camera. CameraX is bound
 * ONLY while [ScanState.Scanning].
 *
 * ## D-12 confirm-first
 * A decoded frame's raw string is parsed id-ONLY by [parseSpoolId] (11-03, V5 boundary — the scanned
 * host is never read/navigated). A [SpoolQrResult.Spool] drives the state to [ScanState.AwaitingConfirm]
 * (NEVER auto-loads); unsupported/unrecognized stay in the scan flow. The confirm card ([ScanConfirmCard])
 * is the SOLE set-active trigger.
 *
 * @param client the session Spoolman reader (resolves the confirm-card detail); null → pending shape.
 * @param onConfirm invoked with the spool id ONLY on the green confirm (the caller dispatches set-active).
 * @param onUsePicker the "Use picker instead" degrade escape (D-15) — returns to the manual picker.
 * @param onBack the red Back gutter exit.
 */
@Composable
fun ScanSurface(
    client: SpoolmanClient?,
    onConfirm: (Int) -> Unit,
    onUsePicker: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // The latest analyzer decode (parsed id-only) and any CameraX bind failure — the two caller-owned
    // inputs to the headless state machine. The permission gate supplies the rest.
    var lastDecode by remember { mutableStateOf<SpoolQrResult?>(null) }
    var bindFailed by remember { mutableStateOf(false) }
    var state by remember { mutableStateOf<ScanState>(ScanState.Idle) }
    // Lens selection (Matthew 2026-06-04): default BACK (D-14 — never *hard-code* front; this is a
    // user-driven toggle, the supported way to reach the front lens), flip rebinds CameraX.
    var useBackCamera by remember { mutableStateOf(true) }
    val lensSelector = if (useBackCamera) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

    // Activity-Result CAMERA glue → derives the ScanState (request only on entry; degrade on denial).
    CameraPermissionGate(
        bindFailed = bindFailed,
        lastDecode = lastDecode,
        onState = { state = it },
    )

    Column(
        modifier.fillMaxSize().background(t.bg).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when (val s = state) {
                is ScanState.AwaitingConfirm -> ScanConfirmCard(
                    spoolId = s.id,
                    client = client,
                    onConfirm = onConfirm,
                    // Cancel returns to scanning: clear the decode so derive() leaves AwaitingConfirm.
                    onCancel = { lastDecode = null },
                    modifier = Modifier.align(Alignment.Center).padding(8.dp),
                )

                ScanState.PermissionDenied -> DegradePanel(
                    symbol = "no_photography",
                    title = "Camera permission denied",
                    body = "Grant camera access to scan a QR, or use the manual picker instead.",
                    onUsePicker = onUsePicker,
                    t = t,
                )

                ScanState.NoCamera -> DegradePanel(
                    symbol = "videocam_off",
                    title = "No camera available",
                    body = "This device has no usable camera. Use the manual picker instead.",
                    onUsePicker = onUsePicker,
                    t = t,
                )

                ScanState.Busy -> DegradePanel(
                    symbol = "error",
                    title = "Camera unavailable",
                    body = "The camera couldn't be opened (in use by another app). Try again, or use the picker.",
                    onUsePicker = onUsePicker,
                    t = t,
                )

                ScanState.Unsupported -> {
                    CameraPreview(lensSelector, onDecode = { lastDecode = it }, onBindFailed = { bindFailed = it })
                    ScanHint("Recognized, but not a spool code — keep aiming at a spool QR.", t)
                }

                ScanState.NotRecognized -> {
                    CameraPreview(lensSelector, onDecode = { lastDecode = it }, onBindFailed = { bindFailed = it })
                    ScanHint("That isn't a Spoolman spool QR — aim at the spool's QR label.", t)
                }

                ScanState.Scanning -> {
                    CameraPreview(lensSelector, onDecode = { lastDecode = it }, onBindFailed = { bindFailed = it })
                    ScanHint("Point the camera at the spool's QR label.", t)
                }

                ScanState.Idle -> ScanHint("Requesting camera…", t)
            }
        }

        // Gutter — red Back (THEME-04), plus a camera-flip toggle while a live preview is showing.
        val livePreview = state is ScanState.Scanning ||
            state is ScanState.Unsupported ||
            state is ScanState.NotRecognized
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedControl(
                label = "Back",
                onClick = onBack,
                modifier = Modifier.weight(1f),
                intent = Intent.Danger,
                symbol = "arrow_back",
            )
            if (livePreview) {
                OutlinedControl(
                    label = if (useBackCamera) "Front cam" else "Rear cam",
                    onClick = { useBackCamera = !useBackCamera },
                    modifier = Modifier.weight(1f),
                    intent = Intent.Accent,
                    symbol = "cameraswitch",
                )
            }
        }
    }
}

/**
 * The CameraX [PreviewView] hosted in [AndroidView], bound in a [DisposableEffect] keyed on the lifecycle
 * owner; `unbindAll()` in `onDispose` RELEASES the camera (D-14). The [QrCodeAnalyzer]'s raw decode is
 * parsed id-only by [parseSpoolId] and surfaced via [onDecode]; a provider/bind failure → [onBindFailed].
 */
@Composable
private fun CameraPreview(
    lensSelector: CameraSelector,
    onDecode: (SpoolQrResult) -> Unit,
    onBindFailed: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            // FILL_CENTER = crop-to-fill (never letterbox); COMPATIBLE (TextureView) re-applies its
            // transform when a different-aspect lens binds, so flipping to the front cam re-fills the
            // screen — SurfaceView-backed PERFORMANCE mode doesn't always re-transform on switch,
            // especially on old hardware (Matthew, 2026-06-04).
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier.fillMaxSize())

    // Keyed on the lens too: flipping back↔front re-runs the effect, unbinding the old lens and rebinding
    // the new one (Matthew 2026-06-04).
    DisposableEffect(lifecycleOwner, lensSelector) {
        val executor = ContextCompat.getMainExecutor(context)
        val analyzerExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var boundProvider: ProcessCameraProvider? = null

        providerFuture.addListener({
            val provider = runCatching { providerFuture.get() }.getOrNull()
            if (provider == null) {
                onBindFailed(true)
                return@addListener
            }
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(analyzerExecutor, QrCodeAnalyzer { raw -> onDecode(parseSpoolId(raw)) }) }

            val bound = runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    lensSelector, // back by default; user-flippable to front (D-14 — not hard-coded).
                    preview,
                    analysis,
                )
            }.isSuccess
            if (bound) {
                boundProvider = provider
                onBindFailed(false)
            } else {
                onBindFailed(true)
            }
        }, executor)

        onDispose {
            // Release the camera the instant the surface leaves composition (D-14).
            boundProvider?.unbindAll()
            analyzerExecutor.shutdown()
        }
    }
}

/** A centered, low-chrome aiming hint over the preview (≥17sp scan-status text, D-16). */
@Composable
private fun ScanHint(text: String, t: ThemeTokens, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Text(
            text = text,
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(17f, t.fs).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

/**
 * A degrade panel (D-15): an icon + title + body + the load-bearing "Use picker instead" escape. Every
 * non-scanning, non-confirm state that can't show the camera routes here — the manual picker always works.
 */
@Composable
private fun DegradePanel(
    symbol: String,
    title: String,
    body: String,
    onUsePicker: () -> Unit,
    t: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MaterialSymbol(symbol, tint = t.text3, sizeSp = fsSp(64f, t.fs))
        Text(
            text = title,
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(22f, t.fs).sp,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            color = t.text2,
            fontFamily = GeistMono,
            fontSize = fsSp(17f, t.fs).sp,
            textAlign = TextAlign.Center,
        )
        OutlinedControl(
            label = "Use picker instead",
            onClick = onUsePicker,
            modifier = Modifier.fillMaxWidth(),
            intent = Intent.Accent,
            symbol = "list",
        )
    }
}
