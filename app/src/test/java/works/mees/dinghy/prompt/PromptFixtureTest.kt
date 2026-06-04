package works.mees.dinghy.prompt

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/**
 * The 26-fixture conformance gate (PROMPT-01 / D-14) — the renderer-neutral oracle every prompt
 * frontend validates against. Ports `packages/js/test/fixtures.spec.ts`: replay each fixture's events
 * through `parseAction`/`reducePrompt`, project `promptView(state)`, and partially-compare against
 * `expected` / `expected_by_frontend` (toMatchObject semantics: absent ≠ default; `size:null` is an
 * asserted-null distinct from absent; fully-specified arrays compare exact-length; `scale:1` is a
 * numeric Int-vs-Double compare).
 *
 * WAVE-0 RED STATE ([[dinghy-wave0-red-scaffold-compile]]): the whole test sourceset compiles BEFORE
 * the `--tests` filter, so this scaffold references ZERO not-yet-built symbols. [corpusGuard] is GREEN
 * immediately (it only loads the corpus). [allFixturesConform] is a pure `fail()` awaiting the reducer
 * (`initialPromptState`/`reducePrompt`/`promptView`) + `parseAction` (Task 2) that land in 12-02; the
 * `fail()` body is replaced with the real fold-and-compare then. [assertMatchesPartial] is a typed
 * `fail()`-bodied helper for the same reason.
 *
 * Dinghy frontend identity (D-08): `frontendId = "dinghy"`, `frontendCategories = ["touch"]` — so
 * Dinghy matches `all`, `dinghy`, and `touch`. The JS driver maps `klipperscreen → ["touch"]` and
 * everything else → `["web"]`; the Kotlin port adds a `dinghy → ["touch"]` row (TODO in the failing
 * body). Because Dinghy carries category `touch`, `target-touch-only` (targets `klipperscreen,touch`)
 * must be VISIBLE for Dinghy (it behaves like `klipperscreen`, not the hidden `mainsail`/`fluidd`).
 */
class PromptFixtureTest {

    /** The 6 keys `promptView` exposes, sorted — no leaked internal fields (EXACT_VIEW_KEYS). */
    private val exactViewKeys = listOf("footer_buttons", "items", "size", "targets", "title", "visible")

    /**
     * GREEN immediately: the corpus loads, declares schema_version 1, and carries exactly 26 fixtures
     * (8 core + 18 optional). This guard does NOT touch the reducer.
     */
    @Test
    fun corpusGuard() {
        val doc = PromptFixtures.document()
        val schemaVersion = doc["schema_version"]!!.jsonPrimitive.content.toInt()
        assertEquals("fixtures.json schema_version must be 1", 1, schemaVersion)
        assertEquals("fixtures.json must carry exactly 26 fixtures", 26, PromptFixtures.fixtures().size)
    }

    /**
     * RED until the reducer lands in 12-02. Replays each fixture under the Dinghy identity
     * (`dinghy` + `["touch"]`), and for `expected_by_frontend` under each named frontend's identity
     * (mapping `klipperscreen`/`dinghy` → `["touch"]`, else → `["web"]`), folding the events with
     * `parseAction`/`disconnectEvent` into the reducer, projecting `promptView`, asserting the
     * EXACT-6-keys shape, then `assertMatchesPartial(view, expected)`.
     *
     * Deferred ([[dinghy-wave0-red-scaffold-compile]]): the body references ZERO not-yet-built symbols
     * (no `parseAction`/`reducePrompt`/`promptView`/`initialPromptState`) — only [PromptFixtures], built
     * in THIS task. 12-02 replaces this `fail()` with the real fold-and-compare loop.
     */
    @Test
    fun allFixturesConform() {
        // Prove the corpus is loadable from here (uses only THIS-task symbols).
        val count = PromptFixtures.fixtureObjects().size
        // TODO(12-02): for each fixture →
        //   - if expected_by_frontend present: for each frontendKey, replay under
        //       frontendCategories = if (frontendKey in setOf("klipperscreen","dinghy")) ["touch"] else ["web"]
        //     (add the dinghy → ["touch"] row — D-08), else replay once under dinghy + ["touch"].
        //   - fold: var s = initialPromptState(opts); for each line: parseAction(line) (or
        //       disconnectEvent() for "__disconnect__") → reducePrompt(s, ev); project promptView(s).
        //   - assert sorted view keys == exactViewKeys (no leaked internal fields).
        //   - assertMatchesPartial(view, expected).
        fail(
            "reducer not yet implemented — 12-02 (loaded $count fixtures; expected 6 view keys: " +
                "$exactViewKeys)",
        )
    }

    /**
     * The `toMatchObject` comparator (RESEARCH step 4), DEFERRED to 12-02:
     *  - objects recurse partially (only keys present in [expectedJson] are checked; absent ≠ default);
     *  - fully-specified arrays compare exact-length and element-wise;
     *  - numeric compare treats `scale:1` (Int) and `0.75` (Double) uniformly;
     *  - `size:null` is an ASSERTED null, distinct from absent (and from the `align` absent=center rule).
     *
     * Typed `fail()` stub so the wave compiles today ([[dinghy-wave0-red-scaffold-compile]]).
     */
    @Suppress("UNUSED_PARAMETER")
    private fun assertMatchesPartial(actualView: JsonObject, expectedJson: JsonElement) {
        fail("assertMatchesPartial not yet implemented — 12-02")
    }
}
