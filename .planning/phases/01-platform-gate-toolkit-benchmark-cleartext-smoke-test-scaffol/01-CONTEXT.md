# Phase 1: Platform Gate — Toolkit Benchmark, Cleartext Smoke Test & Scaffold - Context

**Gathered:** 2026-05-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Settle the two most consequential go/no-go questions and lay the build foundation **before any
architecture is committed**. This phase delivers exactly three things and nothing more:

1. A recorded **Compose-everywhere vs. hybrid-Views** toolkit decision, produced by an on-device
   release-mode benchmark on a real Nexus 7 2013 against a SYNTHETIC 2–4 Hz source.
2. A passing **cleartext `ws://`/`http://` smoke test** on the real API-23 device, proving the
   Marshmallow cleartext network-security path reaches a real Moonraker on the LAN.
3. A pinned `gradle/libs.versions.toml` version catalog and a **multi-module build scaffold** that
   compiles and installs on the Nexus 7.

**Explicitly NOT in this phase:** no resilient websocket/state machine, no connection layer, no
`PrinterState`, no capability gating, no panels, no auth. Those start in Phase 2. This is a gate —
prove toolkit + build + cleartext, then stop.

</domain>

<decisions>
## Implementation Decisions

### Target Hardware (the load-bearing reality)
- **D-01:** The gate runs against **real target hardware** — a Nexus 7 2013 (API 23) is in hand,
  and a **live Ender 5 Plus Moonraker** is reachable on the LAN. No emulator, no fake ws server.
  Both the benchmark (criterion 1) and the cleartext smoke test (criterion 2) execute on the actual
  device against the actual printer. If the printer is powered down during a test run, the cleartext
  smoke test is the part that needs it live; the benchmark uses a synthetic feed and does not.

### Toolkit Go/No-Go Bar
- **D-02:** The decision is gated by a **recorded objective `gfxinfo` metric**, not just a gut call.
  Capture via `adb shell dumpsys gfxinfo <pkg> framestats` in **release mode with the Baseline
  Profile installed** (debug Compose is 5–10× slower and lies about jank).
- **D-03:** Proposed pass bar (planner may refine, but this is the intended rigor):
  on the worst-case sim, **95th-percentile frame time ≤ 16.6 ms**, **jank frames < 10%**, and
  **zero frames over ~700 ms**. Clears the bar → **Compose-everywhere**. Misses it → **hybrid Views**
  for the high-churn surfaces (Files list, temperature graph, Console scrollback — the named fallback
  set from STATE.md). The Compose 1.11 / AGP 8.7.x pin holds regardless of which way the toolkit
  decision goes.
- **D-04:** The decision is **recorded as an ADR** (with the captured gfxinfo numbers as evidence) so
  Phase 2+ panel architecture builds on a documented, defensible choice rather than a vibe.

### Benchmark Fidelity
- **D-05:** The synthetic benchmark must **mimic the app's worst-case panels**, not a generic
  list+counter. It renders, simultaneously, driven by ONE synthetic 2–4 Hz source (no connection layer):
  - a **scrolling Files-style list** with decoded/downsampled thumbnails (the OOM/scroll-jank risk),
  - a **live temperature graph** drawn on a Compose `Canvas` (the sustained-redraw risk),
  - a **console-style text spew** with bounded scrollback (the rapid-append risk).
  Rationale: a clean result on a toy benchmark wouldn't predict how the real thumbnail/graph/console
  surfaces behave on Tegra-era hardware — the whole point is a trustworthy go/no-go.

### Scaffold Shape
- **D-06:** **Multi-module from day one:** `:app` + `:macrobenchmark`. The Baseline Profile is
  mandatory for acceptable Compose startup/scroll on this hardware (per the locked stack), and the
  benchmark + profile generation need the Macrobenchmark harness anyway — build it once, here.
- **D-07:** A pinned `gradle/libs.versions.toml` governs **every** dependency; the minSdk 23 floor is
  protected and auditable (this project's central trap is a lib silently raising the floor). Pin to
  the **Compose 1.11 / AGP 8.7.x line** (BOM 2026.05, compileSdk 35) — do NOT jump to Compose 1.12 /
  AGP 9 / compileSdk 37. Kotlin 2.1.x. These are LOCKED by the stack docs, not re-decided here.

### Cleartext Smoke Test Definition
- **D-08:** "Passes" = with `res/xml/network_security_config.xml` permitting cleartext, the minimal
  build (a) opens a `ws://` websocket to the live Moonraker, (b) performs at least one REST `GET`
  (e.g. `printer.info` / `server.info`), and (c) receives at least one `notify_*` frame — all on the
  Nexus 7 at the **shipping `targetSdk`** (targetSdk 35), not a modern phone and not a relaxed
  targetSdk. This proves the OkHttp/Marshmallow cleartext path on the only hardware that matters.

### Claude's Discretion
- Exact module/package layout, Gradle plugin wiring, and how the synthetic 2–4 Hz feed is generated
  are implementation details for the planner/executor.
- The planner may tighten or loosen the D-03 threshold numbers if research surfaces a more appropriate
  Nexus-7-specific bar — but the *form* (recorded gfxinfo metric in release mode) is fixed.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope & requirements
- `.planning/ROADMAP.md` § "Phase 1: Platform Gate" — the three success criteria this gate must close.
- `.planning/REQUIREMENTS.md` — **PKG-02** (pinned version catalog protecting minSdk 23) and
  **CONN-05** (cleartext `ws://`/`http://` on API 23) are the two requirements mapped to this phase.
- `.planning/PROJECT.md` § Constraints & Key Decisions — minSdk 23 floor, performance-on-Tegra
  rationale, sideloaded-APK distribution.

### Locked technology stack (do not re-decide)
- `CLAUDE.md` (repo root) § "Technology Stack" / "TL;DR — The Prescriptive Stack" — the full pinned
  stack: Kotlin 2.1.x, Compose BOM 2026.05 (Compose 1.11), AGP 8.7.x, compileSdk 35, OkHttp 4.12,
  Retrofit 2.11, kotlinx.serialization 1.7, Coil 3 (minSdk 23), DataStore, Baseline Profile via
  Macrobenchmark, R8. Also the "Big Decision: Compose vs Views" guidance (Baseline Profile from day
  one, profile in release mode, hybrid-Views fallback for high-churn surfaces) that this benchmark
  is designed to settle empirically.

### Reference only (NOT to port; informs worst-case panel sim)
- `E:\claude\personal\github\gtk4_klipperscreen\docs\Screen_Catalog.md` — authoritative inventory of
  the eventual panels. Relevant here only as the source of *what the real Files/Temp/Console surfaces
  look like*, so the worst-case benchmark sim (D-05) resembles them. Reference, not a dependency.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **None — greenfield.** The repo currently contains only `CLAUDE.md` and `.planning/`. No Kotlin
  source, no Gradle scaffold, no modules yet. This phase creates the first code.

### Established Patterns
- No code patterns exist yet. The pattern this phase *establishes* is the pinned version catalog +
  multi-module (`:app` + `:macrobenchmark`) structure every later phase inherits.

### Integration Points
- N/A — nothing to integrate with. The git repo was initialized with Windows git (filemode false)
  per the workspace `CLAUDE.md`; Linux git operates on it via the `safe.directory` exception.

</code_context>

<specifics>
## Specific Ideas

- The hybrid-Views fallback set, if Compose misses the bar, is explicitly **Files list, temperature
  graph, and Console scrollback** — the three high-churn surfaces named in STATE.md blockers. The
  benchmark deliberately exercises exactly those three (D-05) so the go/no-go directly maps to the
  fallback decision.
- Benchmark and profiling run in **release mode** on the physical Nexus 7 — emulators and debug
  builds are explicitly distrusted for this hardware class.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope. (The connection layer, `PrinterState`, panels, and auth
that naturally came up are already scoped to Phases 2+ by the roadmap and were not pulled forward.)

</deferred>

---

*Phase: 1-platform-gate-toolkit-benchmark-cleartext-smoke-test-scaffol*
*Context gathered: 2026-05-30*
