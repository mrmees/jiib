package works.mees.dinghy.webcam

import org.junit.Assert.fail
import org.junit.Test

/**
 * COMPILING runtime-RED scaffold — fail()s until plan 10-02 REPLACES this body with typed assertions
 * against the webcam model parser (`state/WebcamModels.kt`).
 *
 * Why a fail()-bodied stub and not real assertions yet: Gradle compiles the ENTIRE
 * `testDebugUnitTest` source set BEFORE `--tests` filtering. A direct typed reference here to a
 * production symbol not built until 10-02 would fail to COMPILE the whole source set, breaking every
 * targeted `--tests` run in the intervening waves. So this scaffold COMPILES (no unbuilt-symbol
 * reference) and fails at RUNTIME instead — genuinely RED, yet keeping the source set green-to-compile.
 *
 * What 10-02 must prove here (CAM-01 / enumerate, T-V5): parse BOTH goldens
 * (`/golden/webcams_list_e5.json`, `/golden/webcams_list_e3.json`) → cam models with all 16 fields;
 * blank `service` tolerated; missing snapshot (`""`) and `?token=` snapshot both handled; odd-unicode
 * name (`microsoft®_…`) survives; malformed input fails safe (empty list), never crashes.
 * The replacement MUST load goldens by classpath resource (see [works.mees.dinghy.net.GoldenFixtures]),
 * not an absolute path.
 */
class WebcamListParseTest {

    @Test
    fun parsesE5AndE3Goldens_allFields_blankServiceTolerated_missingAndTokenedSnapshot() {
        fail("RED until plan 10-02 builds WebcamModels parser and replaces this scaffold body")
    }
}
