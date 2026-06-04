package works.mees.dinghy.ui.spool.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * SPOOL-06 / D-15 — the project's FIRST runtime hardware-permission flow. The Compose/Context glue ONLY:
 * it wires the Activity Result CAMERA permission to the HEADLESS [deriveScanState] machine (11-03) and
 * feeds the derived [ScanState] back to the caller. The pure granted/denied/no-camera/busy → state
 * mapping lives in [deriveScanState]; this file does NOT re-implement it — it only supplies the
 * `permissionGranted`/`permissionResolved`/`cameraAvailable` inputs from the live Android runtime.
 *
 * ## Permission discipline (D-15 / T-11-07-03)
 *  - CAMERA is requested ONLY on scan entry — a [LaunchedEffect] runs once when this surface enters
 *    composition: if [ContextCompat.checkSelfPermission] already GRANTED it skips the prompt (no
 *    re-ask), otherwise it `launch`es the Activity Result request. The permission is NEVER requested
 *    outside the scan flow (no eager prompt at app start).
 *  - Camera HARDWARE presence is read once via `packageManager.hasSystemFeature(FEATURE_CAMERA_ANY)`
 *    (the manifest declares the camera `required=false`, 11-05 — a camera-less device still installs);
 *    a missing camera derives [ScanState.NoCamera].
 *  - Every denial / no-camera path derives a DEGRADE state ([ScanState.PermissionDenied] /
 *    [ScanState.NoCamera]) that exposes the "Use picker instead" escape — the manual picker (11-06)
 *    always works without the camera.
 *
 * The caller passes [bindFailed] (CameraX open/bind failure → [ScanState.Busy]) and [lastDecode] (the
 * latest analyzer result → confirm-first routing); this composable owns ONLY the permission/hardware
 * inputs. The derived state is delivered via [onState] so the scan surface can react.
 *
 * @param bindFailed whether CameraX bind/open failed (caller-owned; busy/in-use → Busy).
 * @param lastDecode the latest [SpoolQrResult] from the analyzer, or null (no QR yet → keep Scanning).
 * @param onState invoked whenever the derived [ScanState] changes (the surface renders it).
 * @param onRequestPermission optional hook fired when the runtime permission prompt is launched (so a
 *        caller can surface a rationale); pass null to skip.
 */
@Composable
fun CameraPermissionGate(
    bindFailed: Boolean = false,
    lastDecode: SpoolQrResult? = null,
    onState: (ScanState) -> Unit,
    onRequestPermission: (() -> Unit)? = null,
) {
    val context = LocalContext.current

    // Camera HARDWARE presence — read once. The manifest declares an OPTIONAL camera (11-05), so a
    // camera-less device installs and degrades here to NoCamera rather than crashing.
    val cameraAvailable = remember {
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    // Has the permission flow produced a definitive answer yet? Seeded true when already granted (no
    // prompt needed); set true by the launcher callback on a denial so we leave Idle for PermissionDenied.
    val alreadyGranted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
    }
    var permissionGranted by remember { mutableStateOf(alreadyGranted) }
    var permissionResolved by remember { mutableStateOf(alreadyGranted) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        permissionResolved = true // the user has now answered (granted OR denied → leave Idle)
    }

    // Request CAMERA ONLY on scan entry (D-15 / RESEARCH security): a prior grant skips the prompt; only
    // a camera-equipped, not-yet-granted device launches the Activity Result request.
    LaunchedEffect(Unit) {
        if (cameraAvailable && !alreadyGranted) {
            onRequestPermission?.invoke()
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    // The derived state is the HEADLESS machine's (11-03) — this glue never re-implements the mapping.
    val state = deriveScanState(
        permissionGranted = permissionGranted,
        permissionResolved = permissionResolved,
        cameraAvailable = cameraAvailable,
        bindFailed = bindFailed,
        lastDecode = lastDecode,
    )
    LaunchedEffect(state) { onState(state) }
}
