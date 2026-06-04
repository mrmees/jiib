package works.mees.dinghy.webcam

import org.junit.Assert.fail
import org.junit.Test

/**
 * COMPILING runtime-RED scaffold — fail()s until plan 10-06 REPLACES this body with typed assertions
 * against the holder reconnect state machine (`ui/webcam/WebcamHolder.kt`). See WebcamListParseTest's
 * KDoc for why the body is a fail() stub (the cross-wave compile rule).
 *
 * What 10-06 must prove here (D-11): on a transient stall / mid-view drop, the holder state keeps the
 * LAST GOOD FRAME visible (dimmed) with a subtle "Reconnecting…" overlay while auto-retrying — it does
 * NOT clear straight to an error state on a brief Wi-Fi hiccup (no jarring black flash on an always-on
 * panel). A genuine terminal failure (e.g. 401/403) is a separate, terminal state.
 */
class WebcamReconnectStateTest {

    @Test
    fun transientStall_keepsLastFrameDimmedReconnecting_notError() {
        fail("RED until plan 10-06 builds the WebcamHolder reconnect state machine and replaces this scaffold body")
    }
}
