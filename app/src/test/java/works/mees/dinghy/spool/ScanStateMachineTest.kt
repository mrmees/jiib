package works.mees.dinghy.spool

import org.junit.Assert.fail
import org.junit.Test

/**
 * RED scaffold (SPOOL-06) — headless camera permission/degrade state machine.
 *
 * Wave-0 compile-safety: pure `fail()` bodies, no reference to the unbuilt `ScanState` /
 * `CameraPermission` symbols (built in Wave 2 under `ui/spool/scan/CameraPermission.kt`, analog
 * `ui/route/TopRoute.kt` `derive()` pure-state derivation).
 *
 * Target contract (Wave 2 turns these green) — every state is a pure `when`-derived enum, host-testable.
 * All denial/no-camera/error paths route to a DEGRADE state carrying "Use picker instead" (D-15 — the
 * manual picker always works):
 *  granted      → Scanning
 *  denied       → Degraded(picker)
 *  no-camera    → Degraded(picker)
 *  busy         → Degraded(picker) / retry
 *  unreadable   → keep Scanning (transient, no-QR-yet)
 *  no-QR        → keep Scanning
 *  unsupported  → Degraded(picker)
 */
class ScanStateMachineTest {

    @Test
    fun grantedGoesToScanning() {
        fail("RED: ScanState granted -> Scanning not yet implemented")
    }

    @Test
    fun deniedDegradesToPicker() {
        fail("RED: ScanState denied -> Degraded(picker) not yet implemented")
    }

    @Test
    fun noCameraDegradesToPicker() {
        fail("RED: ScanState no-camera -> Degraded(picker) (D-15) not yet implemented")
    }

    @Test
    fun cameraBusyDegradesOrRetries() {
        fail("RED: ScanState busy -> Degraded/retry not yet implemented")
    }

    @Test
    fun unreadableAndNoQrStayScanning() {
        fail("RED: ScanState unreadable/no-QR stays Scanning not yet implemented")
    }

    @Test
    fun unsupportedDegradesToPicker() {
        fail("RED: ScanState unsupported -> Degraded(picker) not yet implemented")
    }
}
