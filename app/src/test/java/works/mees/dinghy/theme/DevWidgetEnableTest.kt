package works.mees.dinghy.theme

import org.junit.Assert.fail
import org.junit.Test

/**
 * Wave-0 RED scaffold (15.2-01 Task 1, per [[dinghy-wave0-red-scaffold-compile]]). Typed `fail()` stub
 * referencing NO not-yet-built symbol (the app-global `devEnableFlow`/`setDevEnable` land in Task 4,
 * which fleshes this out). Keeps the whole test source set compiling for the per-wave `--tests` filter.
 *
 *   - dev_enable_defaults_false_persists_app_global  (D-08 — default false, round-trips, app-global,
 *                                                     NOT BuildConfig.DEBUG)
 */
class DevWidgetEnableTest {

    @Test
    fun dev_enable_defaults_false_persists_app_global() {
        fail("not yet implemented")
    }
}
