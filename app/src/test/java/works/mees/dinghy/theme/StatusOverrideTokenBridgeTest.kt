package works.mees.dinghy.theme

import org.junit.Test

/**
 * RED scaffold for the D-03 status-slot override path through TokenBridge (to land in a later
 * wave). Status colors become FULLY user-editable: the persisted override map carries the 3 status
 * slots alongside the integer pool indices, using reserved STRING keys "stop"/"caution"/"go" (which
 * cannot collide with pool-index keys "0".."63"). TokenBridge applies them after pool overrides:
 *   stop    = statusOverrides["stop"]    ?: generated
 *   caution = statusOverrides["caution"] ?: generated   (caution == the `heat` token)
 *   go      = statusOverrides["go"]      ?: generated
 *
 * Wave-0 type decision (recorded in 15.1-01-SUMMARY, consumed by plans 02/04): the tuple carries a
 * SEPARATE `statusOverrides: Map<String,Long>` field (the Int-keyed `poolOverrides` cannot hold
 * "stop"), while ONE persisted DataStore wire map (already String→Long) holds both. A junk/
 * malformed status value must FAIL SAFE to the generated color via the existing sanitize path
 * (realized in plan 04).
 *
 * CRITICAL ([[dinghy-wave0-red-scaffold-compile]]): this body MUST NOT reference the unbuilt
 * `statusOverrides` field or status-key application. Future behavior is named in PROSE only, with a
 * `fail(...)` body — mirrors the host-pure structure of TokenBridgeTest.
 */
class StatusOverrideTokenBridgeTest {

    @Test
    fun statusKeyOverride_appliedOverGeneratedColor() {
        // TODO(15.1-0N): build TokenBridge with a status override for "stop" and assert tok.stop ==
        // the override color while go/caution stay seed-derived.
        org.junit.Assert.fail("not yet implemented — wave N (status override applied)")
    }

    @Test
    fun clearedStatusOverride_fallsBackToGenerated() {
        // TODO(15.1-0N): assert that with NO "caution" override present, tok.heat (caution) equals
        // the generated status color (clear-to-generated semantics).
        org.junit.Assert.fail("not yet implemented — wave N (status override clear-to-generated)")
    }

    @Test
    fun junkStatusOverride_failsSafeToGenerated() {
        // TODO(15.1-0N): assert a malformed/out-of-range "go" override value is dropped by the
        // sanitizer and the generated go color is used (fail-safe; realized in plan 04).
        org.junit.Assert.fail("not yet implemented — wave N (status override fail-safe)")
    }
}
