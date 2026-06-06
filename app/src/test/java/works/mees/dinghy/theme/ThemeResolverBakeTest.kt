package works.mees.dinghy.theme

import org.junit.Assert.fail
import org.junit.Test

/**
 * Wave-0 RED scaffold (15.2-01 Task 1, per [[dinghy-wave0-red-scaffold-compile]]). Typed `fail()` stubs
 * that reference NO not-yet-built symbol (the pure `ThemeResolver.bake(tuple)` lands in Task 2, which
 * fleshes these out). Keeps the whole test source set compiling for the per-wave `--tests` filter.
 *
 *   - bake_is_pure_leaves_resolver_tokens_untouched   (HIGH-2 — bake mutates no field, emits nothing)
 *   - bake_matches_apply_for_same_tuple               (bake reuses the same compute()/generate path)
 */
class ThemeResolverBakeTest {

    @Test
    fun bake_is_pure_leaves_resolver_tokens_untouched() {
        fail("not yet implemented")
    }

    @Test
    fun bake_matches_apply_for_same_tuple() {
        fail("not yet implemented")
    }
}
