package works.mees.dinghy.net

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrinterStateStore

/**
 * 13-03 GREEN GATE (NEW): the cadence fix in plan 13-03 REMOVES `MoonrakerSession.refreshProbeZOffset()`
 * (the on-Probe-Calibrate-open re-read) on the premise that the re-handshake already keeps
 * `probe.z_offset` fresh after a SAVE_CONFIG. This test is the CONCRETE PROOF of that premise — it
 * replaces a "13-02-SUMMARY says config stays fresh" prose gate with an executable assertion.
 *
 * Flow: a normal connect seeds probe.z_offset from the configfile one-shot (handshake step 7,
 * MoonrakerSession.kt:406 store.setProbeZOffset). Then a printer.cfg / SAVE_CONFIG edit changes the
 * saved z_offset; the captured klippy restart drives a re-handshake whose configfile reply now carries
 * the NEW value. The store's `probeZOffset` StateFlow must reflect the NEW value after the re-handshake.
 *
 * WAVE-0 COLOR: GREEN if current production already re-reads configfile on the re-handshake path (it
 * does — the existing notify_klippy_ready handler re-runs runHandshake() which includes the configfile
 * one-shot). Plan 13-03 GATES its refreshProbeZOffset removal on THIS test being GREEN — not on a
 * sentence. Documented in 13-01-SUMMARY.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProbeZOffsetFreshnessTest {

    private fun configWithZOffset(z: Double): String = """
        {"eventtime":100002.0,"status":{"configfile":{"settings":{
          "extruder":{"min_extrude_temp":170.0,"max_extrude_only_distance":50.0},
          "probe":{"z_offset":$z}}}}}
    """.trimIndent()

    @Test
    fun reHandshakeAfterRestart_refreshesProbeZOffsetToNewSavedValue() =
        runTest(UnconfinedTestDispatcher()) {
            val store = PrinterStateStore(scope = backgroundScope, sampleMillis = 250L)
            val rpc = JsonRpcClient(defaultTimeoutMs = 5_000L)
            val harness = SessionTestHarness()
            // Initial saved z_offset = 1.250 (read by the handshake's configfile one-shot).
            harness.configfileResultJson = configWithZOffset(1.250)
            val session = MoonrakerSession(store, rpc, harness.socketEvents())

            val run = launch { session.run() }
            session.connectionState.first { it is ConnectionState.Connected }

            // The initial handshake seeded the saved z_offset.
            assertEquals(1.250f, store.probeZOffset.value)

            // A SAVE_CONFIG edits the saved z_offset to 1.475; the post-restart configfile read returns it.
            harness.configfileResultJson = configWithZOffset(1.475)

            // Drive the captured klippy restart (drop → ready) — the re-handshake re-reads configfile.
            // Use runCurrent (NOT advanceUntilIdle) between drop and ready, matching KlippyReadyResyncTest's
            // discipline (WR-02): the drop arms a 30s escalate-watchdog, and advancing virtual time past it
            // would fire a spurious FULL reconnect — so the test would assert the RECONNECT configfile
            // re-read, NOT the intended SAME-SOCKET re-handshake (the gate the 13-03 refreshProbeZOffset
            // removal actually depends on). runCurrent drains tasks due NOW and leaves the watchdog pending,
            // so notify_klippy_ready lands the same-socket re-handshake before the timeout.
            harness.injectKlippyDrop()
            runCurrent()
            harness.injectKlippyReady()
            runCurrent()

            assertEquals(
                "the re-handshake must refresh probe.z_offset to the NEW saved value (the green gate the " +
                    "13-03 refreshProbeZOffset removal depends on)",
                1.475f,
                store.probeZOffset.value,
            )

            run.cancelAndJoin()
        }
}
