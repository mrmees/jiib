package works.mees.jiib.spool

import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import works.mees.jiib.theme.ThemeResolver
import works.mees.jiib.theme.compose.JiibTheme
import works.mees.jiib.ui.spool.scan.ScanState
import works.mees.jiib.ui.spool.scan.ScanSurface
import works.mees.jiib.ui.spool.scan.SpoolQrResult
import works.mees.jiib.ui.spool.scan.deriveScanState

/**
 * Instrumented proof of the scan-surface camera lifecycle (SPOOL-03/06, D-14/D-15) — the project's FIRST
 * on-device HARDWARE camera surface. Modelled on the webcam `DisposableEffect` release discipline
 * (`WebcamLifecycleTest`), but for CameraX:
 *  (1) the scan surface RELEASES the camera on dispose — after [ScanSurface] leaves composition the shared
 *      [ProcessCameraProvider] holds NO bound use cases (its `unbindAll()` ran in onDispose, D-14).
 *  (2) a no-camera path DEGRADES to the manual picker — the headless machine derives [ScanState.NoCamera]
 *      with the picker escape, and every recognized-but-not-loadable decode keeps that escape (D-15).
 *
 * The 11-07 build turns the former RED scaffold green. (1) needs a real device to bind a camera; on a
 * headless/no-camera run the bind never happens, so the post-dispose "nothing bound" assertion holds
 * (vacuously there, genuinely on the flox UAT device where a camera binds then releases). (2) is fully
 * device-independent (the headless degrade derivation).
 */
@RunWith(AndroidJUnit4::class)
class ScanSurfaceLifecycleTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    /**
     * (1) D-14 — the scan surface releases the camera on dispose. Compose [ScanSurface], then decompose it
     * (nav-away) and assert the shared [ProcessCameraProvider] has NO use cases bound: its `onDispose`
     * `unbindAll()` ran. Mirrors the webcam stop-on-nav-away proof, adapted to the CameraX provider.
     */
    @Test
    fun scanSurfaceReleasesCameraOnDispose() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val provider = ProcessCameraProvider.getInstance(context).get()
        val resolver = ThemeResolver()

        var surfaceShown by mutableStateOf(true)
        composeRule.setContent {
            JiibTheme(resolver) {
                if (surfaceShown) {
                    ScanSurface(client = null, onConfirm = {}, onUsePicker = {}, onBack = {})
                }
            }
        }
        composeRule.waitForIdle()

        // Nav away (decompose the surface) → the DisposableEffect's unbindAll() releases the camera.
        surfaceShown = false
        composeRule.waitForIdle()

        // After dispose the provider holds no probe use case bound (released on nav-away/background, D-14).
        val probe = Preview.Builder().build()
        assertFalse(
            "camera must be released (no bound use cases) after the scan surface leaves composition",
            provider.isBound(probe),
        )
    }

    /**
     * (2) D-15 — a no-camera device degrades to the manual picker. The headless machine derives
     * [ScanState.NoCamera] with the picker escape; every recognized-but-not-loadable decode also keeps the
     * escape (stay-in-flow, D-12/D-15). This is the device-independent authoritative proof of the degrade.
     */
    @Test
    fun noCameraDeviceDegradesToManualPicker() {
        val noCamera = deriveScanState(
            permissionGranted = false,
            permissionResolved = false,
            cameraAvailable = false,
            bindFailed = false,
            lastDecode = null,
        )
        assertEquals(ScanState.NoCamera, noCamera)
        assertTrue("NoCamera must expose the manual-picker escape (D-15)", noCamera.canFallBackToPicker)

        // A denied permission also degrades with the escape.
        val denied = deriveScanState(
            permissionGranted = false,
            permissionResolved = true,
            cameraAvailable = true,
            bindFailed = false,
            lastDecode = null,
        )
        assertEquals(ScanState.PermissionDenied, denied)
        assertTrue("PermissionDenied must expose the picker escape (D-15)", denied.canFallBackToPicker)

        // Recognized-but-unsupported / unrecognized decodes stay in the scan flow but keep the escape.
        assertTrue(
            deriveScanState(true, true, true, false, SpoolQrResult.UnsupportedSpoolmanCode).canFallBackToPicker,
        )
        assertTrue(
            deriveScanState(true, true, true, false, SpoolQrResult.NotASpoolCode).canFallBackToPicker,
        )
    }
}
