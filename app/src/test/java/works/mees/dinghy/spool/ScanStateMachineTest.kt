package works.mees.dinghy.spool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.ui.spool.scan.ScanState
import works.mees.dinghy.ui.spool.scan.SpoolQrResult
import works.mees.dinghy.ui.spool.scan.deriveScanState

/**
 * SPOOL-06 — headless camera permission/degrade state machine (D-15 + D-12 confirm-first).
 *
 * Every denial/no-camera/error path routes to a DEGRADE state carrying the picker fallback (D-15 — the
 * manual picker always works), encoded as `canFallBackToPicker == true`:
 *  granted      → Scanning
 *  denied       → PermissionDenied (picker)
 *  no-camera    → NoCamera (picker)
 *  busy         → Busy (picker / retry)
 *  unreadable   → keep Scanning (transient, no-QR-yet)
 *  no-QR        → keep Scanning
 *  unsupported  → Unsupported (picker, stay in scan flow)
 *  valid decode → AwaitingConfirm(id), NEVER auto-load (D-12)
 */
class ScanStateMachineTest {

    @Test
    fun grantedGoesToScanning() {
        val s = deriveScanState(
            permissionGranted = true,
            permissionResolved = true,
            cameraAvailable = true,
            bindFailed = false,
            lastDecode = null,
        )
        assertEquals(ScanState.Scanning, s)
        assertTrue("Scanning is the active surface, not a picker-degrade", !s.canFallBackToPicker)
    }

    @Test
    fun deniedDegradesToPicker() {
        val s = deriveScanState(
            permissionGranted = false,
            permissionResolved = true,
            cameraAvailable = true,
            bindFailed = false,
            lastDecode = null,
        )
        assertEquals(ScanState.PermissionDenied, s)
        assertTrue(s.canFallBackToPicker)
    }

    @Test
    fun noCameraDegradesToPicker() {
        val s = deriveScanState(
            permissionGranted = true,
            permissionResolved = true,
            cameraAvailable = false,
            bindFailed = false,
            lastDecode = null,
        )
        assertEquals(ScanState.NoCamera, s)
        assertTrue(s.canFallBackToPicker)
    }

    @Test
    fun cameraBusyDegradesOrRetries() {
        val s = deriveScanState(
            permissionGranted = true,
            permissionResolved = true,
            cameraAvailable = true,
            bindFailed = true,
            lastDecode = null,
        )
        assertEquals(ScanState.Busy, s)
        assertTrue(s.canFallBackToPicker)
    }

    @Test
    fun unreadableAndNoQrStayScanning() {
        // No decode yet (transient unreadable frame / no QR in view) → keep Scanning.
        val s = deriveScanState(
            permissionGranted = true,
            permissionResolved = true,
            cameraAvailable = true,
            bindFailed = false,
            lastDecode = null,
        )
        assertEquals(ScanState.Scanning, s)
    }

    @Test
    fun unsupportedDegradesToPicker() {
        val s = deriveScanState(
            permissionGranted = true,
            permissionResolved = true,
            cameraAvailable = true,
            bindFailed = false,
            lastDecode = SpoolQrResult.UnsupportedSpoolmanCode,
        )
        assertEquals(ScanState.Unsupported, s)
        assertTrue("unsupported stays in scan flow but the picker escape remains (D-15)", s.canFallBackToPicker)
    }

    @Test
    fun notRecognizedStaysInScanFlowWithPickerEscape() {
        val s = deriveScanState(
            permissionGranted = true,
            permissionResolved = true,
            cameraAvailable = true,
            bindFailed = false,
            lastDecode = SpoolQrResult.NotASpoolCode,
        )
        assertEquals(ScanState.NotRecognized, s)
        assertTrue(s.canFallBackToPicker)
    }

    @Test
    fun validDecodeAwaitsConfirmNeverAutoLoads() {
        val s = deriveScanState(
            permissionGranted = true,
            permissionResolved = true,
            cameraAvailable = true,
            bindFailed = false,
            lastDecode = SpoolQrResult.Spool(42),
        )
        assertEquals(ScanState.AwaitingConfirm(42), s)
    }

    @Test
    fun stillRequestingWhenPermissionUnresolved() {
        val s = deriveScanState(
            permissionGranted = false,
            permissionResolved = false,
            cameraAvailable = true,
            bindFailed = false,
            lastDecode = null,
        )
        assertEquals(ScanState.Idle, s)
    }
}
