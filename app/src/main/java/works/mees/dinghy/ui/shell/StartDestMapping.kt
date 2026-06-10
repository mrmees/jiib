package works.mees.dinghy.ui.shell

import works.mees.dinghy.ui.route.NavDest
import works.mees.dinghy.ui.route.knownNavDests

/**
 * The PURE, Activity-free `start_dest` intent-extra → [NavDest] mapping (SC-4b unit half + the V5 /
 * T-18-04-02 input-validation mitigation). This is the SECONDARY on-device verification tool (the preview
 * harness is primary): a debug-gated `am start ... --es start_dest <NavDest>` jumps the running app to a
 * named screen so the must-be-live cases the harness cannot cover (real Moonraker/Klippy state, classic-View
 * perf truth on flox per D-06) can be reached directly.
 *
 * ## Why knownNavDests instead of Dest.entries (the V5 safe-enum-parse mitigation, sealed-interface variant)
 * The extra crosses the EXPORTED-[works.mees.dinghy.MainActivity] trust boundary, so its value is UNTRUSTED
 * (any app / `am` invocation can supply arbitrary text). A bare `Dest.valueOf(raw)` previously threw
 * `IllegalArgumentException` on any unknown name. The new [NavDest] sealed interface has no `.entries` call
 * — instead we iterate [knownNavDests] and match by `simpleName`. This wrapper makes the parse TOTAL:
 * null / blank / unknown → `null` (ignore, fall through to the default screen), a recognized name → its
 * [NavDest]. It NEVER throws on arbitrary input (T-18-04-02 / RESEARCH §Security V5). The dev-gate
 * ([works.mees.dinghy.di.AppContainer.devCyclerEnabled]) is the FIRST line of defense (release-inert);
 * this safe parse is the second (tampering/DoS).
 *
 * Pure (no Activity, no I/O, no Compose) so it is host-unit-testable — see `StartDestMappingTest`.
 *
 * @param raw the raw `start_dest` extra string (`intent.getStringExtra("start_dest")`), possibly null.
 * @return the matching [NavDest], or `null` for null/blank/unrecognized input (never throws).
 */
fun parseStartDest(raw: String?): NavDest? {
    val name = raw?.trim()
    if (name.isNullOrEmpty()) return null
    return knownNavDests.firstOrNull { it::class.simpleName == name }
}
