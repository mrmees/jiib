package works.mees.dinghy.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Wave-0 PENDING scaffold for the active-profile survival-across-process-death gate (MULTI-01,
 * RESEARCH §Validation Architecture: persistence is NOT host-faithful — a host fake would lie about
 * cross-process survival, so this MUST be instrumented). Mirrors [ServiceSurvivesRotationTest]'s
 * real-DataStore-file + real-lifecycle shape.
 *
 * Body is a typed pending [fail] so the androidTest sourceset COMPILES day-one (MEMORY
 * [[dinghy-wave0-red-scaffold-compile]]) — a broken scaffold bricks every per-wave run. The real
 * persistence assertions (seed a profile + set active → kill the process → cold-start re-reads the
 * SAME active id off `profiles.preferences_pb` and lands on the right printer) are filled in plan 06,
 * once the AppContainer profile wiring exists. Do NOT reference unbuilt wiring here.
 */
@RunWith(AndroidJUnit4::class)
class ProfileSurvivesRestartTest {

    @Test
    fun activeIdSurvivesProcessDeath() {
        fail("filled in plan 06 — needs the real profiles.preferences_pb DataStore + cold-start path")
    }
}
