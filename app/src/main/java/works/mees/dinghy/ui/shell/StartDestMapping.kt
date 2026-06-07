package works.mees.dinghy.ui.shell

import works.mees.dinghy.ui.route.Dest

/**
 * The PURE, Activity-free `start_dest` intent-extra → [Dest] mapping (SC-4b unit half + the V5 / T-18-04-02
 * input-validation mitigation). This is the SECONDARY on-device verification tool (the preview harness is
 * primary): a debug-gated `am start ... --es start_dest <Dest>` jumps the running app to a named screen so
 * the must-be-live cases the harness cannot cover (real Moonraker/Klippy state, classic-View perf truth on
 * flox per D-06) can be reached directly.
 *
 * ## Why a wrapped [Dest.valueOf] (the V5 safe-enum-parse mitigation)
 * The extra crosses the EXPORTED-[works.mees.dinghy.MainActivity] trust boundary, so its value is UNTRUSTED
 * (any app / `am` invocation can supply arbitrary text). A bare `Dest.valueOf(raw)` throws
 * `IllegalArgumentException` on any unknown name (and NPE on null) — a crash an attacker could trigger with
 * `--es start_dest <garbage>`. This wrapper makes the parse TOTAL: null / blank / unknown → `null` (ignore,
 * fall through to the default screen), a recognized name → its [Dest]. It NEVER throws on arbitrary input
 * (T-18-04-02 / RESEARCH §Security V5). The dev-gate ([works.mees.dinghy.di.AppContainer.devCyclerEnabled])
 * is the FIRST line of defense (release-inert); this safe parse is the second (tampering/DoS).
 *
 * Pure (no Activity, no I/O, no Compose) so it is host-unit-testable — see `StartDestMappingTest`.
 *
 * @param raw the raw `start_dest` extra string (`intent.getStringExtra("start_dest")`), possibly null.
 * @return the matching [Dest], or `null` for null/blank/unrecognized input (never throws).
 */
fun parseStartDest(raw: String?): Dest? {
    val name = raw?.trim()
    if (name.isNullOrEmpty()) return null
    return Dest.entries.firstOrNull { it.name == name }
}
