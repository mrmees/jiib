package works.mees.dinghy.ui.spool.scan

/**
 * PURE, headless camera-scan permission/degrade state machine (SPOOL-06 / D-15). Mirrors the
 * `when`-derived pure-state idiom of [works.mees.dinghy.ui.route.derive] — no CameraX, no Context, no
 * `rememberLauncherForActivityResult` (that Activity-Result/Compose glue is net-new and lands in
 * 11-06's CameraPermission wiring). Host-testable ([works.mees.dinghy.spool.ScanStateMachineTest]).
 *
 * D-15 INVARIANT: every NON-scanning state is a DEGRADE state that exposes a path back to the manual
 * picker — the manual picker always works without the camera. [ScanState.canFallBackToPicker] encodes
 * this; it is `true` for every degrade/terminal state and `false` only while actively Scanning (the
 * picker affordance is the gutter escape, not the active-scan surface).
 *
 * D-12 INVARIANT: a valid decode NEVER auto-loads — it transitions to [ScanState.AwaitingConfirm],
 * confirm-first, always. There is deliberately no auto-set/auto-load state.
 */
sealed interface ScanState {
    /** True when this state offers the "Use picker instead" escape (D-15). */
    val canFallBackToPicker: Boolean

    /** Pre-permission idle (the surface hasn't requested the camera yet). */
    data object Idle : ScanState {
        override val canFallBackToPicker: Boolean = true
    }

    /** Permission granted + a camera present + bind OK → actively scanning for a QR. */
    data object Scanning : ScanState {
        override val canFallBackToPicker: Boolean = false
    }

    /** Camera permission denied → degrade to the picker (D-15). */
    data object PermissionDenied : ScanState {
        override val canFallBackToPicker: Boolean = true
    }

    /** No camera hardware (or `required=false` and absent) → degrade to the picker (D-15). */
    data object NoCamera : ScanState {
        override val canFallBackToPicker: Boolean = true
    }

    /** Camera busy / bind failure → degrade (with retry) to the picker (D-15). */
    data object Busy : ScanState {
        override val canFallBackToPicker: Boolean = true
    }

    /** Decoded a recognized-but-unsupported Spoolman code (the `f-` scheme); STAY in the scan flow. */
    data object Unsupported : ScanState {
        override val canFallBackToPicker: Boolean = true
    }

    /** Decoded something that isn't a Spoolman spool code (UPC/EAN/garbage); STAY in the scan flow. */
    data object NotRecognized : ScanState {
        override val canFallBackToPicker: Boolean = true
    }

    /** A valid spool id decoded → await user confirmation; NEVER auto-loads (D-12 confirm-first). */
    data class AwaitingConfirm(val id: Int) : ScanState {
        override val canFallBackToPicker: Boolean = true
    }
}

/**
 * PURE derivation of the current [ScanState] from the headless inputs (mirrors
 * [works.mees.dinghy.ui.route.derive]). Arm order is load-bearing — permission/hardware gates win
 * before any decode is considered.
 *
 * @param permissionGranted CAMERA runtime permission outcome (false = denied or not-yet-granted).
 * @param permissionResolved whether the permission flow has produced a definitive answer; when
 *        false-and-not-granted we are still [ScanState.Idle] (requesting), not [ScanState.PermissionDenied].
 * @param cameraAvailable whether the device actually has a usable camera (D-15 no-camera degrade).
 * @param bindFailed whether CameraX bind / camera-open failed (busy / in-use).
 * @param lastDecode the most recent [SpoolQrResult] from the analyzer, or null when none yet
 *        (transient unreadable / no-QR-found → keep Scanning).
 */
fun deriveScanState(
    permissionGranted: Boolean,
    permissionResolved: Boolean,
    cameraAvailable: Boolean,
    bindFailed: Boolean,
    lastDecode: SpoolQrResult?,
): ScanState = when {
    // Hardware/permission gates first — they win over any pending decode.
    !cameraAvailable -> ScanState.NoCamera
    !permissionGranted && permissionResolved -> ScanState.PermissionDenied
    !permissionGranted -> ScanState.Idle // still requesting permission
    bindFailed -> ScanState.Busy

    // Granted + camera + bound: route on the latest decode (null = unreadable/no-QR-yet → keep scanning).
    else -> when (lastDecode) {
        null -> ScanState.Scanning
        is SpoolQrResult.Spool -> ScanState.AwaitingConfirm(lastDecode.id) // confirm-first, never auto-load
        SpoolQrResult.UnsupportedSpoolmanCode -> ScanState.Unsupported
        SpoolQrResult.NotASpoolCode -> ScanState.NotRecognized
    }
}
