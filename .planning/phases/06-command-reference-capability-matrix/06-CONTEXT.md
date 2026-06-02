# Phase 6: Command Reference & Capability Matrix - Context

**Gathered:** 2026-06-01
**Status:** Ready for planning

<domain>
## Phase Boundary

A **reference/quality phase** — no new user-facing feature. It builds the command-knowledge
substrate that gates everything after it (phases 7–14), in three layers:

1. **Docs command/function catalog** — a committed, doc-grounded reference enumerating the command
   surface across the three named APIs, each entry citing its authoritative upstream doc (URL recorded).
2. **Canonical in-code command registry** — one definition per command the app sends, that all later
   phases register into, replacing the scattered ad-hoc method constants from phases 1–5.
3. **Per-printer availability matrix** — the catalog cross-referenced against LIVE introspection of the
   real Ender 5 Plus + Ender 3, reconciled with the existing `docs/moonraker-capabilities.md` captures,
   so later pages can ask "does THIS printer support this command/object?" instead of guessing.

**Out of scope (clarified during discussion):** every Klipper config module's internal `[section]`
options (config reference is NOT catalogued — only commands/functions the app can SEND); shipping any
baked per-printer capability data in the APK; building any new printer-control panel (those are phases
7–14, which will register into the registry produced here).
</domain>

<decisions>
## Implementation Decisions

### Catalog breadth & depth
- **D-01: Comprehensive across the 3 named APIs.** Catalogue the FULL command/function surface of:
  (a) the Klipper **G-Code command reference** (the `gcode.md` command set), (b) the **full Moonraker
  API** (every JSON-RPC method + REST endpoint), (c) the **full Spoolman API**. This is "the universe
  of commands you can actually SEND." It is NOT every Klipper config module's parameters — that edge
  keeps "comprehensive" bounded and finishable.
- **D-02: Tiered depth.** Commands the app sends or will send (the v1-roadmap subset across phases
  1–14): FULL detail — purpose, key params, AND success/error/acceptance semantics (how Klipper
  signals ok vs `!!` error vs `//` echo; how Moonraker acks). Everything else in the comprehensive
  set: lighter — purpose + key params + upstream URL, no exhaustive failure semantics. Spend the
  effort where commands actually fire.
- **D-03: Per-API files + stable catalog IDs.** Split the catalog by source API under `docs/commands/`
  (e.g. `klipper-gcode.md`, `moonraker-api.md`, `spoolman-api.md`). Each command/method gets a **stable
  catalog ID** (e.g. `KGC-SET_HEATER_TEMPERATURE`, `MR-printer.gcode.script`) that the in-code registry
  references, so catalog↔code links are explicit and greppable. Sits alongside the existing
  `docs/moonraker-capabilities.md` (live captures), which remains the matrix source.

### In-code command registry
- **D-04: Data-driven Command model.** One `Command` data class per command the app sends — fields:
  stable catalog ID (links to the docs catalog), transport (JSON-RPC method vs gcode-script), the
  builder/params, and the doc-cited success/error semantics carried as data. A registry object holds
  them all; phases 7–14 add entries. The existing `JsonRpcMethods` constants + `PrinterCommands`
  builders fold INTO these entries — registry becomes the single source of truth.
- **D-05: FULL refactor of call sites.** Phase 1–5 call sites are rewritten to dispatch via the
  registry uniformly, so every send looks identical and there is exactly one source of truth (satisfies
  success-criterion 3 fully — no two-sources-of-truth drift). ⚠ **Consequence: on-device flox
  re-verification is MANDATORY** — this touches the live-proven spine and the Phase-5 panels.
- **D-06: Registry WRAPS the existing typed builders verbatim.** Each `Command` entry that needs params
  holds a reference to the EXISTING typed builder (`jog`/`setHeater`/`extrude`/… with their named-bound
  clamps and `SAVE_GCODE_STATE`/`RESTORE_GCODE_STATE` mode-safety). The builders are kept verbatim; the
  registry just becomes their canonical home and adds ID/transport/semantics around them. The exact
  clamped gcode string is still produced by the proven builder, so the Phase-5 ASVS-V5 clamping +
  mode-safety discipline is **provably unchanged**. Do NOT re-implement clamping as generic metadata.

### Availability matrix
- **D-07: Doc reference; runtime gating stays live.** The committed `docs/` matrix is a DEV-FACING
  reference artifact (what E5/E3 actually expose, reconciled with `moonraker-capabilities.md`) — it
  documents the catalog→printer mapping for humans/planners. The app NEVER ships static per-printer
  data. Runtime gating STAYS live via `deriveCapabilities` (re-derived each reconnect), so it works on
  ANY user's printer, not just the two test rigs — preserving the "point it at any Moonraker" promise.
  This phase EXTENDS the live gating surface to cover what later phases query.
- **D-08: Each command carries a gating predicate.** Every catalog/registry command that is
  printer-conditional records WHAT proves it available — usually "object X present in `objects.list`"
  (`quad_gantry_level`, `z_tilt`, `bed_mesh`) or "`gcode_macro NAME` present". The matrix documents the
  predicate per command and confirms it against the live E5/E3 captures. Later phases gate by evaluating
  that predicate against the live `Capabilities`. This makes success-criterion 4 concrete and queryable.
- **D-09: `Capabilities` retains the raw object-name set + a `hasObject()` helper.** Grow the live
  gating surface to keep the full `objects.list` name set (immutable `Set<String>`) plus a
  `hasObject(name)` helper (alongside the existing `hasMacroIgnoreCase()`), so ANY command predicate is
  evaluable WITHOUT pre-modeling it. The existing typed convenience fields (`hasBed`/`extruderCount`/
  `fans`/`heaters`/`macros`) stay for the hot v1 paths. Later phases add predicates, not new derive code.

### Drift prevention
- **D-10: CI host test asserts registry↔catalog (one-directional).** A host unit test (CI, no device)
  asserts: every in-code registry command has a matching catalog ID, AND every command's gating
  predicate names an object/macro that appears in the committed live E5/E3 captures (or is explicitly
  marked "not on our printers"). One-directional — the comprehensive catalog MAY hold commands the
  registry doesn't (that's expected). This catches the mock-vs-reality drift class the instant code and
  docs disagree. The live re-introspection diff (re-querying real printers) is a useful MANUAL tool but
  is NOT the CI gate (printers must be powered/reachable; not CI-friendly).
- **D-11: Byte-identical-gcode builder tests.** Host tests prove the refactored dispatch produces
  byte-identical gcode strings for the same inputs as the Phase-5 builders — the regression proof that
  the full refactor (D-05) preserved behavior.

### Done-definition / verification gate
- **D-12: Artifacts + green CI + flox regression.** Phase 6 is done when: (1) the three artifacts are
  committed (catalog, registry, matrix), (2) the drift host-test (D-10) + the byte-identical-gcode
  tests (D-11) are green in CI, AND (3) a focused on-device **flox regression pass** confirms the
  refactored Phase-5 panels (Move jog/home/disable, Temp heat/cooldown, Extrude/retract/load-unload,
  e-stop→Splash) still behave identically on the real Ender 5 Plus. No new-feature UAT, but the
  on-device regression is mandatory because the full call-site refactor touched proven code.

### Claude's Discretion
- Exact `Command` data-class field names/shape and the registry object's API (how phases "register
  into" it) — pick what fits the existing Kotlin patterns, honoring D-04/D-06.
- The precise live-introspection method for enumerating "available gcode commands" (Klipper has no
  clean "list all gcode commands" API; config-object presence in `objects.list` is the practical
  signal — see RESEARCH note). Researcher to confirm the method.
- Catalog file naming/anchoring details beyond "per-API files + stable IDs."
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### In-repo (existing artifacts — the matrix/registry build directly on these)
- `docs/moonraker-capabilities.md` — LIVE-captured E5 + E3 objects/fields/components, history +
  metadata shapes, thumbnail scheme. **The matrix source of truth to reconcile against** (D-07); the
  committed E5/E3 captures the drift test (D-10) checks predicates against.
- `app/src/main/java/works/mees/dinghy/net/JsonRpc.kt` — `JsonRpcMethods` object (method-name
  constants) that folds INTO the registry (D-04).
- `app/src/main/java/works/mees/dinghy/command/PrinterCommands.kt` — pure clamped gcode builders +
  named clamp bounds + SAVE/RESTORE mode-safety; the registry WRAPS these verbatim (D-06).
- `app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt` — the shared dispatch wrapper
  (debounce/in-flight/timeout + `DispatchEvent`); registry commands flow through it.
- `app/src/main/java/works/mees/dinghy/state/Capabilities.kt` &
  `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` — the live gating model to EXTEND
  with the raw object-name set + `hasObject()` (D-09).
- `docs/adr/0001-ui-toolkit-decision.md` — toolkit-agnostic spine constraint (StateFlow to both
  Compose + Views); registry/capabilities stay headless.

### Upstream authoritative docs (catalog source — URLs to record per entry, D-01)
- Klipper **G-Code command reference** — `https://www.klipper3d.org/G-Codes.html` (the `gcode.md`
  command set: full Klipper G-Code surface).
- Klipper **status/objects reference** — `https://www.klipper3d.org/Status_Reference.html` (objects +
  fields, for the gating predicates D-08).
- Moonraker **API** — `https://moonraker.readthedocs.io/en/latest/web_api/` (every JSON-RPC method +
  REST endpoint, full surface).
- Spoolman **API** — `https://donkie.github.io/Spoolman/` (full Spoolman API surface).
- (Researcher: pin exact versions matching the test rigs — Klipper `v0.13.0-662`, Moonraker `v0.10.0`
  / api `1.5.0`, both OrcaSlicer, both Spoolman-active, per `moonraker-capabilities.md`.)

### Roadmap
- `.planning/ROADMAP.md` § "Phase 6: Command Reference & Capability Matrix" — goal, 4 success
  criteria, "Research note: DEEPER".
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`JsonRpcMethods` (net/JsonRpc.kt):** already centralizes request/notification method-name
  constants (IDENTIFY, OBJECTS_*, GCODE_SCRIPT, EMERGENCY_STOP, FIRMWARE_RESTART, RESTART,
  TEMPERATURE_STORE, FILES_METADATA, HISTORY_LIST, notify_* names). Not scattered — folds INTO registry.
- **`PrinterCommands` (command/PrinterCommands.kt):** pure, host-testable clamped gcode builders +
  `MATERIAL_PRESETS` + `scriptParams()`. Registry wraps these verbatim (D-06).
- **`CommandDispatcher` (command/CommandDispatcher.kt):** debounce + in-flight + timeout wrapper with
  benign-timeout vs hard-failure `DispatchEvent` semantics (the G4 fix). Dispatch path stays; registry
  feeds it.
- **`deriveCapabilities` (state/DeriveCapabilities.kt):** pure `List<String>` → `Capabilities`; re-run
  every reconnect. Extend (don't fork) to retain the raw object-name set (D-09).

### Established Patterns
- **Purity discipline:** `PrinterCommands` and `deriveCapabilities` are pure (no I/O, no coroutines,
  no Compose), host-testable off-hardware — the registry + extended capabilities MUST keep this.
- **Toolkit-agnostic headless spine (ADR 0001):** registry/capabilities are part of the spine
  consumed by both Compose and Views — no Compose annotations.
- **Fail-safe nullability for slicer/printer-dependent fields** (per `moonraker-capabilities.md`) —
  the same "never fabricate; mark optional" discipline applies to per-printer predicate gating.

### Integration Points
- Phase 1–5 call sites (Move/Temp/Extrude holders + the e-stop/restart Splash recovery + spine
  handshake) are rewritten to dispatch via the registry (D-05) — the refactor surface to re-verify.
- Later phases (7 Files/print-control, 8 Macros/Console, 9 Calibration, 11 Spool) register their
  commands into this registry and gate off the predicates instead of inventing ad-hoc constants.
</code_context>

<specifics>
## Specific Ideas

- Catalog IDs should be greppable and source-tagged (e.g. `KGC-…` Klipper G-Code, `MR-…` Moonraker,
  `SPM-…` Spoolman) so a registry entry's catalog link is unambiguous.
- The drift test's "marked not on our printers" escape hatch matters: the comprehensive catalog will
  include commands neither test rig exposes — those must be flaggable, not test failures.
- Live-introspection caveat (research): Klipper exposes no clean "list all gcode commands" API; the
  practical availability signal is config-object presence in `objects.list` (e.g. `quad_gantry_level`,
  `z_tilt`, `bed_mesh`) plus the `gcode_macro NAME` set — this drives the D-08 predicates.
</specifics>

<deferred>
## Deferred Ideas

- **Live re-introspection diff as CI:** a script that re-queries the real E5/E3 and diffs against the
  committed matrix is valuable but stays a MANUAL refresh tool (printers must be powered/reachable) —
  not the CI gate (D-10). Could become a scheduled local check later.
- **Klipper config-module reference catalog:** documenting every `[section]`'s parameters was
  explicitly out of scope (D-01). If a future phase needs config-driven UI, that's its own effort.
- **Cataloguing the full Spoolman surface now** is in scope (D-01), but BUILDING spool management is
  Phase 11 — the catalog/predicate entries are reference-only until then.

### Reviewed Todos (not folded)
None — no pending todos matched this phase.
</deferred>

---

*Phase: 6-Command Reference & Capability Matrix*
*Context gathered: 2026-06-01*
