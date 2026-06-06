package works.mees.dinghy.theme

import org.junit.Assert.fail
import org.junit.Test

/**
 * Wave-0 RED scaffold (15.2-01 Task 1, per [[dinghy-wave0-red-scaffold-compile]]). These bodies are
 * typed `fail()` stubs that reference NO symbol introduced later in this plan (ThemeOverride,
 * effectiveTokens, setThemeOverride, updateThemeOverride, activeThemeTuple, bake) — so the WHOLE test
 * source set compiles on day one and the per-wave `--tests` filter works. Task 3 fleshes these out.
 *
 * Names map to the VALIDATION rows + the cross-AI review rows (HIGH-1/HIGH-3/HIGH-5 + atomic stepping):
 *   - override_null_passes_through_persisted                       (the persisted/normal path bakes the base)
 *   - override_nonnull_applies_baked_tokens                        (the override path bakes the merged tuple)
 *   - dismiss_restores_persisted_no_drift                          (clearing snaps back, no field drift)
 *   - override_writes_no_persistence                               (apply/clear writes ZERO to DataStore)
 *   - override_idle_no_profile_bakes_from_themeprefs_tuple         (HIGH-1 idle source = ThemePrefs.tupleFlow)
 *   - persisted_change_propagates_through_effectivetokens          (HIGH-1 no-staleness — pure bake re-emits)
 *   - override_carries_full_tuple_status_override_survives_round_trip (HIGH-3 base.copy passthrough)
 *   - update_theme_override_steps_atomically                       (MEDIUM atomic stepping under rapid taps)
 *   - disable_dev_clears_or_ignores_override                       (HIGH-5 never stranded)
 */
class ThemeOverrideTest {

    @Test
    fun override_null_passes_through_persisted() {
        fail("not yet implemented")
    }

    @Test
    fun override_nonnull_applies_baked_tokens() {
        fail("not yet implemented")
    }

    @Test
    fun dismiss_restores_persisted_no_drift() {
        fail("not yet implemented")
    }

    @Test
    fun override_writes_no_persistence() {
        fail("not yet implemented")
    }

    @Test
    fun override_idle_no_profile_bakes_from_themeprefs_tuple() {
        fail("not yet implemented")
    }

    @Test
    fun persisted_change_propagates_through_effectivetokens() {
        fail("not yet implemented")
    }

    @Test
    fun override_carries_full_tuple_status_override_survives_round_trip() {
        fail("not yet implemented")
    }

    @Test
    fun update_theme_override_steps_atomically() {
        fail("not yet implemented")
    }

    @Test
    fun disable_dev_clears_or_ignores_override() {
        fail("not yet implemented")
    }
}
