package works.mees.dinghy.webcam

import org.junit.Assert.fail
import org.junit.Test

/**
 * COMPILING runtime-RED scaffold — fail()s until plan 10-04 REPLACES this body with typed assertions
 * against the decoder's drop-behind frame hand-off. See WebcamListParseTest's KDoc for why the body is
 * a fail() stub (the cross-wave compile rule).
 *
 * What 10-04 must prove here (SC-1 / drop-behind, D-06): a SLOW consumer never backs up the decoder —
 * the hand-off is conflated/latest-wins (a `CONFLATED` channel or `AtomicReference<Bitmap?>`), so a
 * backed-up View on the Adreno-320 floor never grows latency or memory; the newest frame always wins.
 */
class FrameDropBehindTest {

    @Test
    fun slowConsumerNeverBacksUpDecoder_conflatedLatestWins() {
        fail("RED until plan 10-04 builds the drop-behind frame hand-off and replaces this scaffold body")
    }
}
