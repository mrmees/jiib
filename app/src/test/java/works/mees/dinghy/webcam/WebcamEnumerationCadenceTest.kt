package works.mees.dinghy.webcam

import org.junit.Assert.fail
import org.junit.Test

/**
 * COMPILING runtime-RED scaffold — fail()s until plan 10-03 REPLACES this body with typed assertions
 * against the spine's edge-driven webcam enumeration wiring. See WebcamListParseTest's KDoc for why the
 * body is a fail() stub (the cross-wave compile rule): the production enumeration wiring does not exist
 * until 10-03, so a typed reference here would break the whole source set's compile in waves 1–2.
 *
 * What 10-03 must prove here (CAM-01 / cadence, T-10-07 — the cadence-contract Rule 3 guard, mirroring
 * the Phase-13 handshake-edge regression pattern in
 * [works.mees.dinghy.net.KlippyReadyResyncTest] over [works.mees.dinghy.net.SessionTestHarness] /
 * [works.mees.dinghy.net.FakeWebSocket]):
 *
 *  - Drive a fake session through ONE handshake edge; assert the harness's
 *    `webcamsListRequests` (the per-method hit-counter ADDED to SessionTestHarness in this plan,
 *    10-01) is EXACTLY 1 for that edge.
 *  - Advance virtual time / fire subsequent ticks → the count does NOT increase (the enumeration is a
 *    one-shot edge-driven read, NOT a subscribe/poll that wakes the FGS).
 *  - Assert the post-handshake `objects.subscribe` frame carries NO webcam objects (webcams is a
 *    one-shot read, not part of the subscription).
 *
 * CRITICAL: these are PUBLIC observable-behavior assertions (request hit-count + subscribe-frame
 * contents) — they MUST NOT reach into the private `V1_SUBSCRIBE_CORE` constant. The
 * SessionTestHarness `webcamsListResultJson` reply + `webcamsListRequests` counter (both added in
 * 10-01) are the observable seam that makes this assertable without touching spine internals.
 */
class WebcamEnumerationCadenceTest {

    @Test
    fun enumerationFiresExactlyOncePerHandshakeEdge_zeroOnSubsequentTicks_notInSubscribeFrame() {
        fail("RED until plan 10-03 builds the edge-driven webcam enumeration and replaces this scaffold body")
    }
}
