# Phase 11: Spool Management — Spoolman + Camera QR - Context

**Gathered:** 2026-06-04
**Status:** Ready for planning

> **Execution directive (from Matthew):** Build this entire view **one-shot**.
> Run the normal GSD workflow (discuss → plan → execute), but execution is
> **one-and-done** — do NOT surface incremental checkpoints during execution.
> Present **UAT only when EVERYTHING is done**. Image/icon placeholders are fine
> (handled at UAT). Both printers (E5 Plus `192.168.1.120:7125`, E3 Pro
> `192.168.1.121:7125`) and the Spoolman server (`192.168.1.253:7912`) are free
> for live control — delete/change/reweight spools freely **provided a reliable
> save→change→restore protocol is used** (see Live-Test Safety below). "Spools
> are cheap" — being wrong is recoverable.

<domain>
## Phase Boundary

Bring **real spool management** to the printer-side screen via Moonraker's
Spoolman integration (both target printers run it). Deliver:

1. **Active-spool card** on Print Status — what's loaded, glanceable, with state
   variants (unavailable / disconnected / no-active / loading / loaded / stale /
   pending-usage / changed-externally).
2. **A dedicated `Dest.Spool` screen** (drawer tile + Status-card launch) hosting
   the **picker** (Files-style dense list, material/color/vendor/location filters
   + recent/low-remaining sorts), **set/clear active spool**, **change-during-print**,
   and **spool detail**.
3. **QR scan-to-assign** — full-screen camera surface, **ZXing (GMS-free)**, parse
   `web+spoolman:s-<id>` (case-insensitive, incl. uppercase) **and** full
   `http(s)://…/spool/show/<id>` URLs (id-only; never navigate/trust the host),
   **confirm-first** → set active. Graceful camera-permission + no-camera/degrade.
4. **Print-start spool gate** folded into the existing Files print-confirm flow
   (warn-only — see D-01).
5. **Nice-to-haves IN this one-shot:** gcode-aware picker prefilter, location
   shortcuts, archived-spool warning, **measured gross-weight correction**.

Capability-gated on the Moonraker `spoolman` component being present. Active-spool
state is **Moonraker-owned**; inventory data is **Spoolman-owned** (read via
Moonraker proxy `use_v2_response=true`). Provable end-to-end on a real printer:
load filament → scan its label → active spool flips in Spoolman.

**Out of scope (Non-Goals — defer to later phases):** full Spoolman CRUD admin UI,
filament/vendor creation, bulk inventory intake, manufacturer UPC/EAN barcode
intake, NFC/OpenPrintTag/RFID, multi-lane/per-tool spool assignment (single active
spool only), arbitrary `extra`-field administration, manual `/use` consumption
reporting (Moonraker already reports usage), drying/calibration `extra` badges
(no agreed local schema — do not invent).
</domain>

<decisions>
## Implementation Decisions

### Print-Start Spool Gate (strictness)
- **D-01:** **Warn-only, never block.** Every gate condition — including **no
  active spool** — surfaces an **amber "proceed-at-peril" warning** in the existing
  Files confirm flow that the user can tap past in one action. Spoolman tracking is
  effectively opt-in per print on a mounted control surface. Concretely:
  - no active spool → amber warn ("No active spool selected for Spoolman tracking")
    with `Pick spool` / `Scan` / `Print anyway` / `Back`. **Does NOT hard-block.**
  - material mismatch (family-matched, see D-05) → amber warn naming both sides
    ("File wants PLA. Active spool is PETG.").
  - remaining < needed (+ margin) → amber warn ("File needs ~84 g. Spool reports
    42 g.").
  - active spool archived → amber warn (archived-spool warning is IN scope, D-09).
  - `pending_reports` non-empty → amber warn that remaining may be **stale**.
  - file lacks `filament_weight_total` → **skip** the low-filament check (no
    length/density fallback this phase).
  - active-spool detail fetch failed → warn; allow retry/override.
  - A **pass** (all clear) shows the normal `Print file` confirm unchanged.

### Navigation / Information Architecture
- **D-02:** **New top-level `Dest.Spool`** added to the `Dest` enum + a
  **drawer tile** (mirrors the Webcam-tile precedent; capability-greyed when the
  `spoolman` component is absent, exactly like Webcam's D-08 gating). The
  `Dest.Spool` screen owns picker / detail / set-clear / change. The **QR scan
  surface is a full-screen sub-surface launched from the Spool screen or the
  Status card** (not its own drawer Dest), released on pause/background/nav-away.
- **D-03:** A **compact active-spool card on Print Status** answers "what's
  loaded?" without leaving Status, and is the primary launch point (`Scan` /
  `Change` / `Clear` quick actions). Card is a confidence indicator + launcher,
  NOT a database row — keep fields to material / color swatch / vendor·name /
  remaining / state.

### One-Shot Scope (nice-to-haves folded IN)
- **D-04:** **All four** nice-to-haves are IN this one-shot:
  - **Gcode-aware picker prefilter** — opening the picker from a Files warning
    pre-filters by the selected file's `filament_type[]` (material) and
    `filament_colors[]` (color-similarity hint, warning not strict).
  - **Location shortcuts** — "At this printer" + "No location" chips from
    `/v1/location`.
  - **Archived-spool warning** — badge/warn on archived active or scanned spool
    (allow with warning, do not silently accept; see D-09).
  - **Measured gross-weight correction** — `PUT /v1/spool/{id}/measure` with gross
    grams via the single-setting numeric primitive; UI must show that
    `remaining_weight` and `used_weight` are **linked** (`used = initial − remaining`).

### Spoolman / Moonraker API discipline
- **D-05:** **Material matching = family (partial, case-insensitive).** `PLA`
  matches `PLA+`, `PLA Meta`, etc. Multi-family chips send **comma-separated
  terms** (`ABS,ASA`). NOT typo-tolerant fuzzy. Same policy for picker chips AND
  print-start mismatch checks. (Live-proven: `filament.material=PLA` → 7 rows
  incl. `PLA+ 2.0`; `PLA+PETG` → 0.)
- **D-06:** **Color filter = Spoolman's own similarity endpoint**, two-step:
  `GET /v1/filament?color_hex=…&color_similarity_threshold=20` → fetch spools by
  returned `filament.id` list. Trigger **only on swatch tap** (OpenAPI marks it
  slow). Fixed palette swatches (black/white/gray/clear/red/orange/yellow/green/
  blue/purple/pink/brown/metallic·silk/multi-color/other). No local RGB matcher.
  Always render the spool's **actual** swatch on every result row (endpoint can
  match unexpectedly).
- **D-07:** **Moonraker proxy with `use_v2_response=true`** is the inventory
  transport — envelope `{response, error, response_headers}`; `X-Total-Count` lives
  in `response_headers`. Direct Spoolman REST is a fallback only if proxy proves
  insufficient. Dotted query keys (`filament.material`) must be URL-encoded by the
  client layer. **Active-spool get/set/status/clear go via the existing
  Moonraker JSON-RPC session** (`server.spoolman.*`), NOT the proxy.
- **D-08:** **All Spoolman response fields are optional / null-safe.** Color is
  display-normalized and never trusted: accept `color_hex` with/without `#`,
  require 6 or 8 hex digits post-normalize else neutral unknown marker; render
  `multi_color_hexes` as a split swatch. `extra` is `Map<String,String>` of
  JSON-encoded values — parse only agreed local keys, tolerate invalid JSON, never
  let a bad field break a card/row. (No `extra` keys are agreed this phase →
  badges deferred.)
- **D-09:** **Archived = allow-with-warning** (not blocked) for both the
  print-start gate and a scanned/selected archived spool.
- **D-10:** **External-change reconciliation is mandatory.** Dinghy is NOT the
  only active-spool mutator (Fluidd/Mainsail, runout macros, `spoolman_set_active_spool`).
  Route `notify_active_spool_set` and `notify_spoolman_status_changed`, and
  reconcile the card / picker / open confirmation to the new id rather than
  overwriting with stale local state. (Moonraker sends `params` as a 1-element
  array.)
- **D-11:** **Pending-reports = stale-not-lost.** Non-empty `pending_reports`
  means future usage is assigned but prior usage isn't fully synced; phrase as
  "usage queued", treat remaining as potentially stale, **never** call `/use`
  manually to compensate.
- **D-12:** **QR is an id carrier, not a navigation target.** Parse the id and
  fetch via the configured Moonraker/Spoolman path; never open the scanned URL or
  switch hosts. `web+spoolman:f-<id>` / other schemes → "unsupported Spoolman
  code". Manufacturer UPC/EAN → "Not a Spoolman spool code", stay in scan flow.
  **Confirm-first: never auto-load on decode.**
- **D-13:** **Clear/unload** = `POST server.spoolman.set_spool_id {}`. Optionally
  offer a location-update tap (move old spool back to a shelf) — **optional,
  never automatic**. Location is inventory context, not active-printer truth.

### Camera / Scanner
- **D-14:** **ZXing (pure-Java, GMS-free)** decoder — the Nexus 7 floor has no
  Play Services for ML Kit. **CameraX** for the preview/analysis pipeline (verify
  minSdk-23 compatibility during research; document a deliberate Camera2 fallback
  if CameraX pulls a >23 floor). **Do NOT hard-code "front camera"** — fixture-
  prove on the physical device; front lens may be fixed-focus and poor at close
  labels, rear/autofocus may be the only reliable path. Throttle analysis frames
  but keep resolution high enough to decode the real printed label at scan
  distance. **Release the camera on pause/background/nav-away** (short-lived task
  surface, not a persistent stream — same discipline as the Phase-10 webcam).
- **D-15:** Camera-permission flow requested gracefully; no-camera / permission-
  denied / busy / unreadable / no-QR-found / unsupported-payload all degrade to
  a clear state with an escape path — **manual picker always works without the
  camera.**

### Claude's Discretion (decide during plan/execute per the brief's recommendations)
- Low-remaining **safety margin** added to `filament_weight_total` (small fixed
  buffer / %); it only drives a warning (D-01), so pick a sensible constant.
- Default picker list shape (unarchived, `sort=filament.material:asc,...,id:asc`,
  `limit=50`) and which secondary chips render in row 1 vs behind "refine".
- Whether to persist any per-printer picker prefs vs derive each session
  (**lean: derive each session**; only persist if a concrete need emerges).
- Exact `SpoolmanClient` shape (small proxy client, NOT forced through
  `CommandDispatcher` early) and where the session-owned facade hangs off
  `SpineHandle` / `AppContainer`.
- Portrait vs landscape collapse of the picker Focus/Field per the UI LAW.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Spoolman product/design + API (THE primary spec set for this phase)
- `docs/view_specific_notes/spoolman.md` — full view/workflow spec: architecture
  rule (Moonraker=active, Spoolman=inventory), all proxy/REST queries, active-spool
  card fields, picker filters, QR parser contract, print-start gate checks,
  change-during-print, correction actions, **suggested null-safe Kotlin data model**,
  recommended 12-step implementation order, non-goals.
- `docs/view_specific_notes/spoolman_planning_brief.md` — product framing, user
  journeys, the decision tables, layout guidance (card/picker/scanner/warning),
  data-ownership rules, the full error/empty-state list, the 12 open product
  decisions (resolved above), and the Recommended First Cut (must/nice/defer).
- `docs/view_specific_notes/spoolman_live_validation.md` — **live-captured truth**
  (2026-06-04) from the real install: proxy v2 envelope shape, `X-Total-Count` in
  `response_headers`, material family-match evidence, color-similarity flow, the
  save→change→**restore** loop proof, QR source confirmation, per-printer active
  spool (E5=5, E3=1). Treat as ground-truth contract for parser/fake hardening.
- `docs/view_specific_notes/spoolman_live_probe.md` — the read-only probe tool
  contract + what each summary field means.
- `tools/spoolman-probe.py` — read-only probe (re-runnable for fresh fixtures /
  notification capture; `--listen` for push payloads).
- `docs/commands/spoolman-api.md` — local Spoolman API command catalog.
- `docs/commands/moonraker-api.md` — Moonraker command catalog. **Known gap:** lists
  the four `server.spoolman.*` methods but NOT `notify_active_spool_set` /
  `notify_spoolman_status_changed` — add catalog/spec entries before parser tests.
- `docs/commands/spoolman-live-*.json` (23 fixtures) — verbatim live captures
  (ender5/ender3 probes, notify, status before/after, proxy pla/spool3/color/
  materials/vendors/locations/no-location/recent/abs-asa/exact/lot/article/fields,
  direct spool3 before/after-restore). **Source-of-truth fixtures for Wave-0 goldens.**
- `/mnt/e/claude/personal/github/spoolman/DEVICE_CONTROL_API_CATALOG.md` —
  expanded upstream Spoolman API catalog (referenced by the notes).

### UI design system (LAW — reproduce, do not re-litigate)
- `docs/ui_design/CLAUDE.md`, `docs/ui_design/LAYOUT.md` (Focus/Field/Gutter),
  `docs/ui_design/THEMING.md` (semantic tokens, button-intent colors: amber=`--heat`
  proceed-at-peril, red=stop, green=accept, accent=physical, white=setting), and
  `reference/hifi.css` (canonical token/component values).
- **Layout precedents to mirror** (per Matthew: "reference our previous layouts"):
  - Files dense-list (scroll/overflow/thumb handling): `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt` — the picker is "Files-like".
  - Confirm-guard language + the Files print-confirm flow (where the gate hooks in).
  - Webcam phase as the precedent for: capability-greyed drawer tile + beta amber
    (`app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` `DrawerTileSpec`),
    full-screen single-focus task surface + camera lifecycle release
    (`docs/view_specific_notes/camera_feed`, `ui/webcam/*`).
  - Single-setting numeric primitive (`NumpadPage`/`ScrubberPage`) for measured-
    weight entry.

### Roadmap / project
- `.planning/ROADMAP.md` §"Phase 11" — goal + the 4 success criteria.
- `CLAUDE.md` (project) — stack (Kotlin/Compose+Views hybrid, OkHttp+Retrofit,
  kotlinx.serialization), minSdk-23 floor discipline, build-env (`E:\Android\gw.bat`).
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `CommandTransport.SpoolmanRest` (`command/CommandSpec.kt`) already exists — but
  the current dispatcher path is JSON-RPC-only; keep inventory lookup in a small
  `SpoolmanClient`/proxy client rather than forcing it through `CommandDispatcher`
  too early.
- `AvailabilityPredicate.ComponentPresent("spoolman")` + `Capabilities.hasComponent`
  — the correct capability gate (same pattern Webcam used).
- `JsonRpc.kt` / `JsonRpcClient` — currently routes status/Klippy/gcode
  notifications; add routing for `notify_active_spool_set` +
  `notify_spoolman_status_changed` (or a generic notification flow).
- `state/PrintMetadata.kt` `FilePreviewMetadata` — currently lifts only
  `filament_total` + `filament_weight_total`; **extend** to parse `filament_type[]`,
  `filament_name[]`, `filament_colors[]`, `filament_weights[]` for the gate +
  gcode-aware prefilter.
- `ui/files/FilesScreen.kt` / `FileBrowserClient.startPrint` — the print-start
  gate hooks **before** `printer.print.start` dispatch; the picker reuses the
  dense-list pattern (incl. the Views-in-Compose scroll lesson from Phase 7).
- `ui/shell/AppDrawer.kt` `DrawerTileSpec(beta=…, danger=…)` + greyed-gating — add
  the Spool tile; `ui/route/TopRoute.kt` `Dest` enum — add `Dest.Spool`.
- Single-setting numeric primitive (`NumpadPage`/`ScrubberPage`) for measured-weight.

### Established Patterns
- `SpineHandle` exposes session-owned facades; `AppContainer` exposes derived flows
  (`fileBrowser`, `printMetadata`, `lastJob`) — add `spoolman` / `activeSpool`
  derived flows the same way.
- One-shot command specs + cadence-compliant edge-driven StateFlows (Phase-10
  webcam enumeration is the closest template).
- Wave-0 RED discipline: stubs must compile; goldens captured from the live `*.json`
  fixtures; **hardened fakes to the real server contract** (mock-vs-reality lesson —
  6 prior strikes; the live validation doc exists precisely to prevent a 7th).

### Integration Points
- Active-spool get/set/status/clear → existing Moonraker JSON-RPC session.
- Inventory reads → new `SpoolmanClient` via `server.spoolman.proxy` (`use_v2_response=true`).
- Print-status card → new derived flow on `AppContainer`/`SpineHandle`.
- Print-start gate → Files confirm flow before `startPrint`.
- Notifications → `JsonRpcClient` router fan-out to Spoolman feature state.
- New camera/permission stack (ZXing + CameraX) — **not currently present**; add
  deliberately, fixture/lens-prove on the real device.
</code_context>

<specifics>
## Specific Ideas

- "Develop this entire view one-shot; only present the UAT when EVERYTHING is done."
- "Reference our previous layouts for similar concepts" — explicitly mirror Files
  (picker list), the print-confirm guard, and the Webcam phase (drawer tile +
  full-screen camera surface + lifecycle release).
- "Don't worry too much about images/icon placeholders — we'll handle later in UAT."
- Gate is intentionally the **least naggy** choice (warn-only, never block) — a
  mounted printer screen should not stop a print over inventory bookkeeping.

## Live-Test Safety Protocol (mandatory for the end-to-end UAT)
Matthew authorized free live control of both printers + Spoolman **provided a
reliable restore method**. The validation doc already proved the loop; bake it in:
1. **Before any write:** capture the printer's current `server.spoolman.status`
   (active `spool_id`) AND a full `GET /api/v1/spool/{id}` of any spool to be
   modified → save to a `*-before.json` fixture (pattern already used:
   `spoolman-live-ender5-status-before-set.json`, `…-direct-spool3-before.json`).
2. **After the test:** restore active spool via `POST server.spoolman.set_spool_id`
   to the saved id, and `PATCH` any modified spool field back to its saved value;
   re-`GET` to confirm (`…-after-restore.json`). E5 baseline active = **5**,
   E3 baseline active = **1** (per live validation 2026-06-04).
3. Measured-weight / reweight tests use spool **3** as the sacrificial subject
   (already has before/after-restore fixtures), or any spool, restoring after.
4. Never issue delete calls in the automated UAT; "spools are cheap" covers
   accidents, but the restore protocol is the safety net.
</specifics>

<deferred>
## Deferred Ideas

These came up in the research/brief and are explicitly **out of this phase**:
- Full Spoolman CRUD admin UI; filament/vendor creation; bulk inventory intake.
- Manufacturer UPC/EAN barcode → inventory creation (if the tablet ever becomes an
  inventory station).
- NFC / OpenPrintTag / RFID intake.
- Multi-material / toolchanger / AMS-MMU per-tool/lane active-spool model (this
  phase is **single active spool** only).
- Drying/calibration **`extra`-field badges** — no agreed local `extra` schema; do
  not invent one in code.
- "Use last spool for this file" from Moonraker history — depends on history shape;
  fixture-prove later.
- Lot-number / article-number / `extra` server-side filters — endpoints exist but
  are empty on the live install; low priority until populated.
- WebRTC camera (separate, already deferred by Phase 10 SC-4) — unrelated to QR.

None of these block the phase; captured so they aren't lost.
</deferred>

---

*Phase: 11-spool-management-spoolman-camera-qr*
*Context gathered: 2026-06-04*
