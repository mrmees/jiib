package works.mees.dinghy.spool

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented RED scaffold (SPOOL-03/06, VALIDATION line 52) — scan-surface camera lifecycle.
 *
 * Wave-0 compile-safety (CRITICAL — a bad androidTest scaffold bricks the WHOLE androidTest sourceset,
 * which Gradle compiles before applying `--tests`): pure `fail()` bodies, ZERO reference to the unbuilt
 * `ScanSurface.kt` symbols (ScanSurface lands in Wave 2 / plan 11-07). This stub is COMPILED in Wave 0
 * (`:app:compileDebugAndroidTestKotlin`) but RUN only later via `connectedDebugAndroidTest` once
 * ScanSurface exists.
 *
 * Target assertions (11-07 turns these green) — modelled on the webcam `DisposableEffect` release
 * discipline (`WebcamLifecycleTest`), but for the project's first on-device HARDWARE camera (D-14):
 *  (1) the scan surface RELEASES the camera on dispose (CameraX `unbindAll()` on nav-away/background —
 *      no camera held after leaving the surface)
 *  (2) a no-camera device DEGRADES to the manual picker (D-15 — picker always works)
 */
@RunWith(AndroidJUnit4::class)
class ScanSurfaceLifecycleTest {

    @Test
    fun scanSurfaceReleasesCameraOnDispose() {
        fail("RED: scan surface camera release on dispose (CameraX unbindAll) not yet implemented (11-07)")
    }

    @Test
    fun noCameraDeviceDegradesToManualPicker() {
        fail("RED: no-camera device degrades to manual picker (D-15) not yet implemented (11-07)")
    }
}
