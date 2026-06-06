package works.mees.dinghy.ui.finetune

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented proof (lands GREEN in 17-06): invoking `PrintStatusControlAction.Tune` routes the shell to
 * `Dest.FineTune` (replacing the current `-> Unit` stub in PrintStatusScreen). This is the nav-wiring gate
 * for the whole Fine-Tune entry path.
 *
 * [[dinghy-wave0-red-scaffold-compile]] + REVIEW #8: the `Dest.FineTune` route does NOT exist yet (it lands
 * with the nav wiring in 17-06), and the WHOLE androidTest sourceset compiles before any `--tests` filter.
 * So this scaffold references ONLY symbols that exist today — it is a `fail("RED — 17-06: ...")` skeleton.
 * 17-06 converts it to a real `createComposeRule()` shell-harness test that drives the Tune action and
 * asserts the FineTune hub is rendered. Until then it COMPILES (proving the androidTest sourceset is clean)
 * and is RED.
 */
@RunWith(AndroidJUnit4::class)
class FineTuneNavTest {

    @Test
    fun tuneAction_navigatesToFineTuneDest() {
        // Target (17-06): PrintStatusControlAction.Tune -> onNavigate(Dest.FineTune) routes the shell to the
        //   Fine-Tune hub. Asserted via the shell test harness once Dest.FineTune + the AppShell arm land.
        fail("RED — 17-06: PrintStatusControlAction.Tune navigates to Dest.FineTune")
    }
}
