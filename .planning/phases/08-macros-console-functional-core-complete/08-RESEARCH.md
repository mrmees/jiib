# Phase 8: Macros & Console — Functional-Core Complete — Research

**Researched:** 2026-06-02
**Domain:** Moonraker `server.gcode_store` / `notify_gcode_response` console feed + Klipper macro discovery & gcode-body param parsing, rendered in the existing Android/Kotlin/Compose+Views hybrid
**Confidence:** HIGH (the two unconfirmed API surfaces are now confirmed against official Moonraker docs AND the Mainsail fork AND already-present codebase plumbing)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Console — read-only monitor**
- **D-01:** Console is **read-only this phase.** No on-screen keyboard, no command send. Passive feed.
- **D-02:** Content = command/response history with **severity coloring** (`!!` → error, `//` → echo/warning, plain → normal), **backfilled from `server.gcode_store`** on connect AND reconnect (do not silently drop lines that arrived while disconnected), **live via `notify_gcode_response`**, **bounded scrollback** (~1000 lines default, stick-to-bottom unless scrolled up — planner's call).
- **D-03:** **Opt-in noise filters** (default OFF), mirroring the Mainsail fork: **Hide temperatures** (`^(?:ok\s+)?(B|C|T\d*):`), **Hide Timelapse** (the fork's rule set), **Hide prompt commands** (`^(?:// )?action:prompt`).
- **D-04:** **Architecture constraint:** the display filter is a VIEW-layer concern only. Raw `notify_gcode_response` + `gcode_store` stream stays **upstream of / independent of** the console display filter (so Phase-12 prompt engine + backfill never starved by what the console hides). Mirrors fork commit `76fcbd2`.
- **D-05:** Console scrollback is the **3rd Views-in-Compose scroll surface**. Apply the Files scroll lesson: pinned height (`BoxWithConstraints` → `.height(maxHeight)`) + `Modifier.clipToBounds()`; suppress the swipe-up drawer gesture on the scroll-Field.

**Macros — three screens**
- **D-06:** **System macro list** — ALL available macros (from `printer.objects.list` / config), check/select per macro choosing which surface. **Underscore-prefixed macros hidden by default** (real printers carry 96/60 macros, mostly internal). User can reveal/select them.
- **D-07:** **Bookmarked macro list** — ONLY user-selected macros; the everyday launcher. The **drawer "Macros" tile opens this screen**; a control reaches the System list.
- **D-08:** **Macro execution popup** — macro name, **auto-detected parameter fields**, **Execute / Cancel** in the gutter. The popup **IS the deliberate action gate** — no separate ConfirmGuard on top.
- **D-09:** **Parameter detection = parse the macro's gcode body** for `params.X` + `|default(...)`, one field per param. Mainsail does the same — use as reference. Heuristic; acceptable to miss edge cases.
- **D-10:** Macro execution popup is the **ONE place the on-screen system keyboard is allowed** in printer controls — for string params. **Numeric params use the existing `NumpadPage`.** Resolution of the PRIM-02 keyboard-triage deferral.

**Layout**
- **D-11:** No console/macros hi-fi mockup — designed fresh within the Focus/Field/Gutter grammar and `docs/ui_design/` laws. (UI-SPEC already authored + approved 2026-06-02 — see `08-UI-SPEC.md`.)

### Claude's Discretion
- Exact scrollback bound, auto-scroll behavior, timestamp display on console lines.
- Precise Focus/Field/Gutter composition of each of the 3 macro screens + the console screen.
- Gutter button intent-colors for Execute/Cancel (UI-SPEC resolved: Execute = accent/blue default, amber if macro statically known to drive heat/motion; Cancel = green safe-dismiss).
- Whether the console is its own drawer tile or shares structure — likely a new tile.

### Deferred Ideas (OUT OF SCOPE)
- **Console text input / send arbitrary G-code (CONS-01)** — deferred (console is read-only). ACTION already done: CONS-01 moved to deferred in REQUIREMENTS.md.
- **User-defined custom regex console filters** (the fork's `consolefilters`) — power-user, keyboard-heavy; ship built-in toggles only.
- **Macro Prompt Protocol** (`action:prompt_*` interactive dialogs) — Phase 12. This phase only keeps the raw stream clean (D-04) so it slots in.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MACRO-01 | List and run printer `gcode_macros` | `Capabilities.macros` already enumerates macro names (`gcode_macro NAME` → `NAME`). Run via existing `CommandDispatcher.dispatch(key, GCODE_SCRIPT, scriptParams(...))`. New: bookmarked/system list UI + execution popup. |
| MACRO-02 | Enter parameters for macros that declare them | Parse the macro gcode body (from `configfile.settings["gcode_macro <name>"].gcode`) with the confirmed Mainsail regex (see Code Examples). New: a one-shot `configfile` read scoped to macro sections; a pure `MacroParamParser`. |
| MACRO-03 | Hide/show which macros appear | Underscore-default-hide rule + per-macro select persisted to DataStore. New: a `MacroPrefs` DataStore + the System list select UI. |
| CONS-02 | Command/response history, severity coloring, backfilled from `server.gcode_store`, live via `notify_gcode_response`, read-only w/ opt-in filters | Live stream ALREADY plumbed end-to-end (`store.gcodeResponses`). New: the one-shot `server.gcode_store` backfill read + the Console screen (Views-in-Compose scrollback) + severity classifier + filter toggles. |
</phase_requirements>

---

## Summary

This phase is **architecturally simple and largely a UI + two-small-reads phase**, because the connection spine built in Phases 2–7 already carries most of what it needs. The single biggest finding: **the live `notify_gcode_response` path is already wired end-to-end** — `JsonRpcClient.gcodeResponses` (a bounded, un-throttled `SharedFlow<String>`) → `MoonrakerSession` routes it to `store.onGcodeLine(it)` → `PrinterStateStore.gcodeResponses` (the consumer-facing `SharedFlow<String>`). A console holder just needs to *collect that flow*. No transport work.

The two genuinely new backend pieces are both **one-shot JSON-RPC reads done exactly like the existing handshake reads**: (1) `server.gcode_store` (params `{count}`, returns `{gcode_store:[{message,time,type}]}`) to backfill history on connect/reconnect, and (2) a scoped `printer.objects.query {configfile}` read whose `result.status.configfile.settings["gcode_macro <name>"].gcode` field is the macro body the parameter parser walks. **Both API surfaces are now CONFIRMED** against official Moonraker docs, the Mainsail fork (commit `76fcbd2`), and the already-present `configfile.settings.extruder` read in `MoonrakerSession.runHandshake()` — which proves the exact path works on the real printers.

The exact Mainsail filter regexes and the macro-param regex are extracted verbatim below. The one real risk area is **security: macro string params are user free-text concatenated into a gcode line** (D-10 allows the alpha keyboard), which breaks the `PrinterCommands` invariant of "never concatenate user strings into a script." This needs an explicit sanitizer (see Security Domain).

**Primary recommendation:** Two new one-shot reads in the handshake (`server.gcode_store` backfill + a macro-scoped `configfile` query), a `ConsoleHolder` collecting the existing `gcodeResponses` flow into a bounded ring of *structured line objects* (NOT the float-only `RingBuffer`), a pure `MacroParamParser` (Mainsail regex), a `MacroPrefs` DataStore, and four new screens wired through `AppShell`/`Dest`. Reuse the `FileListView` Views-in-Compose pattern verbatim for console scrollback. Treat macro string-param gcode injection as the security gate.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Live console line ingestion | Connection spine (`JsonRpcClient`/`PrinterStateStore`) | — | Already owns `notify_gcode_response` routing; console is a pure consumer. |
| Console history backfill | Connection spine (handshake one-shot read) | — | Must run on every (re)connect alongside the existing `temperature_store`/`configfile` reads, NOT from the UI. |
| Console display filtering | UI / view layer ONLY (D-04) | — | LOAD-BEARING: filters must NOT touch the upstream stream (Phase-12 prompt engine taps the raw stream). |
| Console scrollback rendering | Classic Views (RecyclerView-in-`AndroidView`) | Compose shell host | ADR-0001 hybrid: high-churn scroll surface → Views (3rd such surface, D-05). |
| Macro enumeration | Connection spine (`Capabilities.macros`) | — | Already derived from `objects.list` on every reconnect. |
| Macro body / param source | Connection spine (one-shot `configfile` read) | Pure parser (headless) | Same `configfile.settings` path the extruder-config read already uses. |
| Macro param parsing | Pure headless function (`MacroParamParser`) | — | Host-testable, no I/O — mirrors `PrinterCommands`/`deriveCapabilities` discipline. |
| Macro bookmark/visibility prefs | DataStore (Preferences) | — | Same pattern as `ConnectionStore`/`ThemePrefs`. |
| Macro execution | `CommandDispatcher` → `GCODE_SCRIPT` | — | Reuses the Phase 5/7 pending-state dispatch; macro run is just another gcode. |
| Macro param entry (numeric) | Compose (`NumpadPage`) | — | Keyboard-free numeric per PRIM-01/D-10. |
| Macro param entry (string) | Compose (system keyboard, popup ONLY) | — | The single sanctioned alpha-keyboard site (D-10) — security-gated. |

---

## Standard Stack

**No new external dependencies.** Every piece is already in the pinned `libs.versions.toml` and proven on-device. This phase adds zero `npm`/Gradle dependencies — it is composition over the existing stack.

### Core (already in-stack — reused)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| OkHttp + JSON-RPC layer | 4.12.x | `notify_gcode_response` + `server.gcode_store` reads | Already carries both; one socket. `[VERIFIED: codebase]` |
| kotlinx.serialization | 1.7.x | Walk the loose `gcode_store`/`configfile` JSON | The handshake already walks `configfile.settings` this way. `[VERIFIED: codebase]` |
| Coroutines + Flow | 1.9.x | `ConsoleHolder`/`MacroHolder` `StateFlow`s; collect existing `gcodeResponses` `SharedFlow` | Phase-5/7 holder pattern. `[VERIFIED: codebase]` |
| DataStore (Preferences) | 1.1.x | `MacroPrefs` (bookmarks + reveal-hidden + per-macro select) | Same as `ConnectionStore`/`ThemePrefs`. `[VERIFIED: codebase]` |
| Compose + classic Views (RecyclerView) | BOM 2026.05 / Compose 1.11 | Console scrollback (Views) + macro/popup screens (Compose) | ADR-0001 hybrid; Files is the proven precedent. `[VERIFIED: codebase]` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Backing console scrollback with a new structured ring class | Reuse `render/RingBuffer.kt` | ❌ `RingBuffer` is a `FloatArray` (graph samples only) — it CANNOT hold structured `ConsoleLine` objects. Do NOT shoehorn console lines into it. Write a small generic `BoundedRing<ConsoleLine>` (or a `synchronized ArrayDeque` capped at ~1000) — same O(1) eviction idiom, object-typed. `[VERIFIED: codebase]` |
| One-shot `configfile` read scoped to macros | The full `configfile` object | The handshake already queries `configfile` for the extruder; you MAY extend that same read to also pull the macro sections rather than issuing a second query (see Pitfall 3). `[VERIFIED: codebase]` |
| Compose `LazyColumn` for console | — | ❌ Violates ADR-0001 (high-churn scroll = Views) AND D-05. Use RecyclerView-in-`AndroidView` like Files. |

**Installation:** None — `./gradlew` change-free for dependencies. (Build via the WSL helper `E:\Android\gw.bat` per CLAUDE.md.)

---

## Package Legitimacy Audit

> Not applicable — this phase installs **zero** external packages. All functionality composes over the existing, already-audited, pinned `libs.versions.toml`. No registry lookups, no new transitive deps, no slopcheck surface.

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

---

## Confirmed API Surfaces (the heart of this research)

### 1. `server.gcode_store` — console history backfill (CONS-02 / D-02)

**Method:** `server.gcode_store` (JSON-RPC over the websocket). `[VERIFIED: codebase docs/commands/catalog.json `MR-server.gcode_store` + CITED: moonraker.readthedocs.io/en/latest/external_api/server/]`

**Request params:** `{ "count": <int> }` — number of cached responses to return; **optional**, defaults to the full store size. `[CITED: moonraker.readthedocs.io]`

**Response shape (verbatim from Moonraker docs):** `[CITED: moonraker.readthedocs.io]`
```json
{
    "gcode_store": [
        {
            "message": "FIRMWARE_RESTART",
            "time": 1615832299.1167388,
            "type": "command"
        },
        {
            "message": "// Klipper state: Ready",
            "time": 1615832309.9977088,
            "type": "response"
        }
    ]
}
```

**Per-entry fields:**
- `message` (string) — the raw line, INCLUDING its `!!` / `//` severity prefix (note `"// Klipper state: Ready"` above).
- `time` (float) — Unix epoch seconds (for optional timestamp display + the dedup/cleared-since cut).
- `type` (string) — **exactly two values: `"command"`** (a gcode command Moonraker received via its API) **and `"response"`** (a reply from the printer). `[CITED: moonraker.readthedocs.io]` Mainsail further reclassifies a `"response"` whose message starts with `// action:` → `action` and `// debug:` → `debug` at render time. `[VERIFIED: mainsail src/store/server/actions.ts:addEvent]`

**Retention (capped FIFO cache — NOT deduplicated):** Moonraker's `gcode_store` is a plain capped FIFO array (`gcode_store_size` config default **1000 entries**) — it does NOT deduplicate entries. `[CITED: moonraker.readthedocs.io]` So a `count` of ~1000 backfills the whole server-side buffer; the project's ~1000-line client scrollback (D-02) aligns 1:1. (N1: the word "dedup" below refers to the CLIENT-side strategy for avoiding double-display on reconnect — the server cache itself does no dedup; full replace is what avoids client-side append duplicates.)

**Reconnect strategy — capped FIFO cache; full replace avoids client-side append duplicates (D-02 — "do not silently drop lines that arrived while disconnected"):**

The critical insight from the Mainsail fork: on **every** (re)connect, Moonraker's `gcode_store` returns the *entire* retained server-side buffer — including any lines emitted while the client was disconnected (Moonraker keeps caching server-side regardless of client presence). Mainsail therefore does **`clearGcodeStore()` then `setGcodeStore(snapshot)`** on each connect — a **full replace**, NOT an append-merge. `[VERIFIED: mainsail src/store/server/actions.ts:getGcodeStore + src/store/printer/actions.ts:init]`

Recommended approach for dinghy-display (two viable, planner's call):
- **Option A (Mainsail parity, simplest, RECOMMENDED):** On each connect/reconnect, fetch `server.gcode_store` and **replace** the scrollback contents with the snapshot (capped to the ring bound). This inherently recovers disconnect-window lines (they're in the server buffer) and needs no dedup logic. The brief window where live `notify_gcode_response` lines might arrive *between* subscribe and the backfill read is covered because the backfill snapshot is authoritative and replaces. Tradeoff: a line delivered live a few ms before the replace is momentarily shown then replaced by the identical store entry — visually identical, no user-visible dup.
- **Option B (append + dedup by `(time, message)`):** Keep live lines, append only store entries newer than the newest already-held `time`. More code; the only reason to prefer it is to avoid a flicker on a very long scrollback. Given the ~1000-line cap and Adreno-320 budget, **A is recommended** — it is exactly what the reference implementation ships.

**Backfill is upstream of the display filter (D-04):** store the raw snapshot in the holder's raw buffer; apply filters only at the view layer. Do NOT filter before storing.

**Registration:** Add a `gcodeStore` `CommandSpec` to `CommandRegistry` (it's already catalogued as `planned_v1`, `registered:false`) + a `GCODE_STORE = "server.gcode_store"` const in `JsonRpcMethods`, then call it as a best-effort `runCatching` one-shot in `runHandshake()` (step 7 alongside `temperature_store`). `[VERIFIED: codebase]`

### 2. `notify_gcode_response` — live console lines (CONS-02 / D-02 / D-04)

**Method:** `notify_gcode_response` (JSON-RPC notification, no `id`). `[VERIFIED: codebase JsonRpcMethods.NOTIFY_GCODE_RESPONSE + CITED: moonraker.readthedocs.io/en/latest/external_api/jsonrpc_notifications/]`

**Params shape:** a **1-element array containing a single string**: `params: ["response message"]`. `[CITED: moonraker.readthedocs.io]` This is exactly how `JsonRpcClient.gcodeLine()` already parses it (`params[0]` → string). `[VERIFIED: codebase]`

**Already plumbed (NO new transport work):**
```
JsonRpcClient (params[0] → String)
  → _gcodeResponses: SharedFlow<String>  (bounded buffer 256, un-throttled)
  → MoonrakerSession.routing: rpc.gcodeResponses.collect { store.onGcodeLine(it) }
  → PrinterStateStore.gcodeResponses: SharedFlow<String>   ← the console holder collects THIS
```
`[VERIFIED: codebase JsonRpcClient.kt:68-74,184-185; MoonrakerSession.kt:228; PrinterStateStore.kt:57-59,144-146]`

**Severity encoding (in the text, per D-02):** Klipper prefixes the message string itself: `"!! "` → error; `"// "` → echo/notice/warning; plain → normal. `[VERIFIED: mainsail src/plugins/helpers.ts:formatConsoleMessage + src/components/console/ConsoleTableEntry.vue:messageClass]` The official Moonraker notification doc does NOT formally enumerate the prefixes (it only says "Klippy's gcode responses are forwarded"), so the prefix→severity mapping is a Klipper-output convention confirmed via the fork, not the Moonraker spec — treat as `[VERIFIED: mainsail]`, robustly handled (a missing prefix → normal tier, never throws).

**Severity classifier (mirror the fork exactly):**
- `message.startsWith("!! ")` → **error** tier (`--stop` red).
- `message.startsWith("// action:")` → action tier (dimmed; this is the Phase-12 hook — keep raw, D-04).
- `message.startsWith("// debug:")` → debug tier (dimmed).
- `message.startsWith("// ")` (other) → **echo/warning** tier (`--heat` amber per UI-SPEC).
- else → **normal** tier (`--text`; UI-SPEC promotes explicit `ok`/success to `--go` green at discretion).

`[VERIFIED: mainsail ConsoleTableEntry.vue:25-43, actions.ts:addEvent:264-267]`

> Keep the raw prefix in the stored model; the *display* may strip it (Mainsail's `formatConsoleMessage` strips `!! `/`// `/`echo:`/`debug:`). For a printer-side read-only monitor, stripping the prefix and coloring instead is the cleaner UX (UI-SPEC: "color + the line text, never color alone"). Planner's call whether to strip; if stripped, the SEVERITY must still be derived from the ORIGINAL prefix before stripping.

### 3. Macro discovery + gcode-body introspection for param detection (MACRO-01/02/03 / D-06/D-09)

**Enumeration — ALREADY DONE:** `Capabilities.macros` is the `NAME` part of every `gcode_macro NAME` object from `printer.objects.list`, re-derived on every reconnect. `Capabilities.hasMacroIgnoreCase(name)` handles Moonraker's lowercase-name quirk. `[VERIFIED: codebase DeriveCapabilities.kt:22, Capabilities.kt:49]` Underscore-prefixed macros are PRESENT in this list (Mainsail filters them out *at render time* with `if (name.startsWith('_')) return`) — the System list applies the same default-hide + reveal toggle. `[VERIFIED: mainsail getters.ts:getMacros:161]`

**Macro BODY source — CONFIRMED path:** the macro's raw gcode lives at
```
result.status.configfile.settings["gcode_macro <lowercased name>"].gcode   (a string)
```
This is **confirmed two ways**: (a) Mainsail builds its macro list from `state.configfile.settings` keyed by `gcode_macro ` prefix and reads `.gcode` per section (via `getMacroParams(propSettings)` where `propSettings.gcode` is the body); `[VERIFIED: mainsail getters.ts:getMacros:147-173, helpers.ts:getMacroParams:246]` and (b) `MoonrakerSession.runHandshake()` ALREADY successfully reads `configfile.settings.extruder` on the real printers — same object, same `settings` sub-path. `[VERIFIED: codebase MoonrakerSession.kt:355-361]`

**How to fetch it:** `printer.objects.query` with `{"objects": {"configfile": null}}` returns `result.status.configfile.settings` containing ALL config sections, including every `gcode_macro <name>` with its `gcode:` body. The macro keys are **lowercased** in `settings` (e.g. `"gcode_macro start_print"`). This is a one-shot read (config is static; refresh on `notify_klippy_ready` re-handshake which already re-runs the config read). `[VERIFIED: codebase + mainsail]`

> ⚠ **NEEDS ON-DEVICE CONFIRMATION (low risk):** The exact JSON nesting (`settings["gcode_macro name"].gcode` as a single newline-joined string vs. an array) should be confirmed against a live `printer.objects.query?configfile` on the Ender 5 (`192.168.1.120:7125`) during planning/execution — it is NOT yet captured in `docs/moonraker-capabilities.md`. Evidence strongly indicates a single string (Mainsail's regex runs `paramRegex.exec(macro.gcode)` directly on a string). A planning-time probe `curl -s "http://192.168.1.120:7125/printer/objects/query?configfile" | python -m json.tool | grep -A3 gcode_macro` settles it and should be added to the capability catalog (mock-vs-reality discipline — this is exactly the class of gap that bit Phases 2 and 5).

**Param parsing — the EXACT Mainsail algorithm (D-09):** see Code Examples below.

---

## Architecture Patterns

### System Architecture Diagram

```
                       ┌─────────────────────── Moonraker (LAN) ───────────────────────┐
                       │  websocket: notify_gcode_response  ·  server.gcode_store (RPC) │
                       │  printer.objects.query{configfile}  ·  printer.gcode.script    │
                       └───────────────┬──────────────────────────────┬────────────────┘
                                       │ (already wired)               │ (already wired)
                          live lines   │                  one-shot reads│
                                       ▼                                ▼
                    JsonRpcClient.gcodeResponses          MoonrakerSession.runHandshake()
                    (SharedFlow<String>, raw, bounded)     ├─ server.gcode_store  ──► raw backfill
                                       │                    ├─ objects.query{configfile} ─► macro bodies
                    store.onGcodeLine  │                    └─ (existing temp/extruder reads)
                                       ▼                                │
                    PrinterStateStore.gcodeResponses                    │
                    (SharedFlow<String>)  ───┐                          │
                                             │                          ▼
                  ┌──────────────────────────┴──────────┐   MacroParamParser (PURE)
                  ▼ (D-04: RAW, upstream of filter)      │   parse params.X + |default()
       ConsoleHolder                                     │          │
       · BoundedRing<ConsoleLine> (raw, ~1000)           │          ▼
       · severity classify (prefix → tier)               │   MacroHolder (StateFlow<MacroVm>)
       · backfill replace on (re)connect ◄───────────────┘   · Capabilities.macros (enum)
       · expose StateFlow<List<ConsoleLine>> (raw)           · MacroPrefs (DataStore: bookmarks/reveal)
                  │                                           · combine(state, prefs, parsedParams)
   VIEW LAYER ─── │ ── filter applied HERE ONLY (D-04) ──     │
                  ▼                                           ▼
       ConsoleScreen (Compose shell)                 Bookmarked / System / Execution-popup screens
       · RecyclerView-in-AndroidView (Views, D-05)   · OutlinedControl tiles, NumpadPage (numeric),
       · pinned height + clipToBounds                  system keyboard (string param, popup ONLY)
       · drawer swipe suppressed                      · Execute → CommandDispatcher.dispatch(GCODE_SCRIPT)
       · filter toggles (Gutter)
```

### Recommended Project Structure (new files)
```
ui/console/
├── ConsoleHolder.kt        # collects store.gcodeResponses + raw backfill; StateFlow<List<ConsoleLine>>
├── ConsoleLine.kt          # data class { rawMessage, severity, timeEpoch? }  (headless)
├── ConsoleSeverity.kt      # enum Error/Warning/Normal/Action/Debug + classify(prefix) (pure)
├── ConsoleFilters.kt       # the 3 built-in filter regexes + apply (VIEW layer, pure)
├── ConsoleListView.kt      # RecyclerView-in-AndroidView (copy FileListView pattern)
├── ConsoleRowsAdapter.kt   # ListAdapter (copy FileRowsAdapter)
└── ConsoleScreen.kt        # ScreenScaffold Field-only + Gutter filter toggles + Back
ui/macros/
├── MacroParamParser.kt     # PURE: parse gcode body → List<MacroParam> (Mainsail regex)
├── MacroModels.kt          # MacroParam { name, type, default }, MacroVm
├── MacroHolder.kt          # combine(Capabilities.macros, MacroPrefs, parsed bodies)
├── MacroPrefs.kt           # DataStore (bookmarks Set<String>, revealHidden Bool)
├── BookmarkedMacrosScreen.kt
├── SystemMacrosScreen.kt
└── MacroExecutionPopup.kt  # name + param fields (NumpadPage | keyboard) + Execute/Cancel
state/  (additions)
└── PrinterStateStore.kt    # + setGcodeBackfill(...) and/or a rawConsole holder seam
command/
└── CommandRegistry.kt      # + gcodeStore CommandSpec; reuse GCODE_SCRIPT for macro run
ui/route/TopRoute.kt        # Dest enum += Macros, Console
ui/shell/AppDrawer.kt       # wire Macros tile (dest=Dest.Macros); add Console tile
```

### Pattern 1: One-shot best-effort handshake read (gcode_store backfill)
**What:** Add the backfill read to `runHandshake()` step 7, wrapped in its own `runCatching`, after subscribe.
**When to use:** Any static/snapshot read that must refresh on every (re)connect but must never break `Connected`.
**Example:**
```kotlin
// Source: app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt (existing temperature_store pattern, lines 344-362)
runCatching {
    val storeResult = rpc.request(CommandRegistry.gcodeStore, GcodeStoreArgs(count = 1000))
    val lines = parseGcodeStore(storeResult.jsonObject)   // List<ConsoleLine>, raw, prefix-preserved
    store.setGcodeBackfill(lines)                          // REPLACE the raw scrollback (Mainsail parity)
}
```
This runs identically on first connect and on every reconnect/`notify_klippy_ready` re-handshake (which already re-runs `runHandshake()` — see Pitfall 4), so disconnect-window lines are recovered automatically.

### Pattern 2: Macro run via the existing dispatch path
**What:** A macro execution is just a gcode script — reuse `CommandDispatcher` + `GCODE_SCRIPT`.
**Example:**
```kotlin
// macro name + assembled params → one gcode line, dispatched through the proven pending-state path
val gcode = buildMacroInvocation(macroName, paramValues)   // see Security Domain for sanitization
dispatcher.dispatch(
    key = "macro_$macroName",                              // per-macro busy key (PRIM-05)
    method = JsonRpcMethods.GCODE_SCRIPT,
    params = PrinterCommands.scriptParams(gcode),          // {"script": "<NAME> P1=.. P2=.."}
)
```
A `!!` rejection surfaces as `DispatchEvent.Failure` → `SeverityToast` AND appears in the console history (same line arrives via `notify_gcode_response`). `[VERIFIED: codebase CommandDispatcher.kt:108-157, PrinterCommands.kt:148-149]`

### Pattern 3: Views-in-Compose scroll surface (console scrollback)
Copy `FileListView.kt` verbatim — the load-bearing bits are `Modifier.clipToBounds()` on the `AndroidView`, `ViewGroup.LayoutParams.MATCH_PARENT` on the RecyclerView, `itemAnimator = null` (no animated insertion — Adreno-320), and the Compose host pinning height via `BoxWithConstraints` → `.height(maxHeight)`. Suppress the swipe-up drawer gesture on this screen (D-05). `[VERIFIED: codebase FileListView.kt:49-78]`

### Anti-Patterns to Avoid
- **Filtering the upstream stream.** Filters are a VIEW concern (D-04). Never drop a line before it reaches the raw holder buffer — the Phase-12 prompt engine and backfill depend on the complete raw stream.
- **Reusing `RingBuffer` (float) for console lines.** It stores `FloatArray` only. Use an object-typed bounded ring.
- **Concatenating raw user string params into gcode.** Injection vector — sanitize (Security Domain).
- **A second `objects.subscribe` or per-screen polling for macros/console.** Everything rides the single existing handshake/subscription (project DISCIPLINE per STATE.md — Phase 13 is a cadence audit, not a per-screen refactor).
- **Compose `LazyColumn` for the console feed.** Violates ADR-0001 + D-05.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Live gcode-line transport | A new websocket subscription for console | `store.gcodeResponses` (already wired) | End-to-end plumbing exists; just collect it. |
| Macro param regex | A bespoke parser from scratch | The exact Mainsail `paramRegex` (Code Examples) | Battle-tested against thousands of real Klipper macros; matches `params.X`, `|int/string/double`, `|default(...)`, and `'x' in params` guard forms. |
| Bounded console scrollback eviction | Manual array-shifting | A small `synchronized ArrayDeque` capped at the bound (the `RingBuffer` idiom, object-typed) | O(1) eviction; the float `RingBuffer` is the proven shape. |
| Scroll surface in Compose | A Compose-from-scratch scroll list | `FileListView` RecyclerView-in-`AndroidView` pattern | The pinned-height + clipToBounds + drawer-suppress lesson is already paid for (and was a real Phase-7 bug). |
| Pending/busy/timeout on macro run | New dispatch logic | `CommandDispatcher` | Already gives debounce + in-flight + 120s gcode timeout + redacted failure toast. |
| Severity prefix handling | Guess the prefixes | Mainsail's `!!` / `// action:` / `// debug:` / `// ` classification | Confirmed convention; robust to absent prefix. |
| Macro bookmark persistence | A custom prefs file | DataStore (Preferences) | `ConnectionStore`/`ThemePrefs` precedent; coroutine-safe, no ANR. |

**Key insight:** This phase is ~80% reuse. The connection spine, dispatch path, capability derivation, DataStore pattern, and Views-in-Compose scroll surface all exist and are on-device-proven. The genuinely new code is two small reads, a pure parser, a severity classifier, and four screens.

---

## Runtime State Inventory

> This is NOT a rename/refactor/migration phase — it is additive feature work. Section included for completeness with explicit "nothing found" answers.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | **None new of concern.** New persisted state = a `MacroPrefs` DataStore (`macros.preferences_pb` — bookmarks `Set<String>`, revealHidden bool). It is additive and self-contained; no migration of existing data. | Create new DataStore (separate `.preferences_pb`, per the `ConnectionStore`/`ThemePrefs` boundary discipline). |
| Live service config | None — read-only console + macro reads; this phase issues no server-side config writes. | None. |
| OS-registered state | None. | None. |
| Secrets/env vars | None. | None. |
| Build artifacts | None — no dependency changes, no package renames. | None. |

**Nothing found in categories: live service config, OS-registered state, secrets, build artifacts** — verified by: no new deps (composition only), no renames, no server-side writes (console is read-only; macro run uses the existing `GCODE_SCRIPT` path).

---

## Common Pitfalls

### Pitfall 1: Treating `RingBuffer` as the console backing store
**What goes wrong:** `render/RingBuffer.kt` is a `FloatArray` ring for graph samples; it physically cannot hold `ConsoleLine` objects. A planner copying the CONTEXT phrase "candidate bounded scrollback backing store" literally will hit a type wall.
**Why it happens:** CONTEXT lists `RingBuffer.kt` as a *candidate* — research found it does not fit.
**How to avoid:** Write a tiny generic object-typed bounded ring (or a `@Synchronized ArrayDeque<ConsoleLine>` with `removeFirst()` when over cap). Same O(1) idiom, correct type.
**Warning signs:** Trying to `push(Float)` a console line; a compile error against `RingBuffer.push`.

### Pitfall 2: Filtering before the raw buffer (D-04 violation)
**What goes wrong:** Applying the Hide-temperatures/Timelapse/prompt filters before lines land in the holder starves the Phase-12 prompt engine and corrupts backfill semantics.
**Why it happens:** It's tempting to filter once at ingest for "efficiency."
**How to avoid:** Holder stores the RAW stream; the view layer applies the active filter set on render (exactly as Mainsail's `getConsolefilterRules` runs at the getter/view layer). Filters are reversible toggles, so the raw data must survive a toggle-off.
**Warning signs:** A toggled-off filter cannot re-reveal previously-hidden lines.

### Pitfall 3: Issuing a duplicate `configfile` query
**What goes wrong:** The handshake ALREADY does `objects.query {configfile}` for the extruder min-temp read. A naive macro-body fetch adds a SECOND identical query — wasteful on the LAN (the exact thing Phase 13 will audit).
**Why it happens:** Not noticing the existing read at `MoonrakerSession.kt:355`.
**How to avoid:** Extend the existing `configfile` read to ALSO extract the `gcode_macro *` sections (one query, two consumers), OR have a single config holder both the extruder-config and the macro-body parser read from. Prefer one query.
**Warning signs:** Two `printer.objects.query` calls with `configfile` in the same handshake.

### Pitfall 4: Forgetting that reconnect/`klippy_ready` re-runs the backfill
**What goes wrong:** If you wire `gcode_store` only on first connect (not inside `runHandshake()`), a `notify_klippy_ready` re-handshake or a socket reconnect leaves stale console history.
**Why it happens:** `runHandshake()` is re-invoked on every reconnect AND on `notify_klippy_ready` (G2/G3 self-heal, MoonrakerSession.kt:217-226). Reads placed inside it inherit that for free.
**How to avoid:** Put the `gcode_store` read in `runHandshake()` step 7 alongside the existing one-shot reads. Replace-on-fetch (Pattern 1) then satisfies D-02's "don't drop disconnect-window lines."
**Warning signs:** Console history not refreshing after a `FIRMWARE_RESTART`.

### Pitfall 5: Macro string-param gcode injection (SECURITY — see Security Domain)
**What goes wrong:** D-10 allows the alpha keyboard for string params; a raw `param=user"text\nM112` concatenated into the gcode line could inject extra gcode (e.g. an emergency stop, or a heater command) — breaking the `PrinterCommands` "never concatenate user strings" invariant.
**Why it happens:** Macro params are inherently free-text; the existing builders only ever interpolated clamped numerics and fixed identifiers.
**How to avoid:** Sanitize string params — strip newlines/control chars, reject or quote per Klipper's `KEY=VALUE` macro-arg grammar. See Security Domain.
**Warning signs:** A param value containing a newline or `M112`/`G`-code reaching the dispatched script.

### Pitfall 6: Mock-vs-reality on the `configfile` macro-body shape
**What goes wrong:** Building the param parser against an assumed JSON shape that differs from the live printer (the recurring project bug class — Phases 2 & 5 both shipped green unit suites that hid live protocol bugs).
**Why it happens:** `configfile.settings["gcode_macro ..."].gcode` is NOT yet in `docs/moonraker-capabilities.md`.
**How to avoid:** Probe a real printer during planning (read-only `curl`), record the exact shape in the capability catalog, and harden the parser test fixture to the real shape — NOT a lenient mock. Gate the param feature behind a live read.
**Warning signs:** Param fields render in unit tests but are empty/wrong on flox.

---

## Code Examples

### Macro parameter parsing — the EXACT Mainsail regex (D-09)
```kotlin
// Source (verbatim algorithm): mainsail @76fcbd2 src/plugins/helpers.ts:getMacroParams (lines 246-291)
// Two passes over the macro's gcode body string.

// Pass 1 — `params.X` with optional |int/string/double type and |default(...) value:
//   {%?.*?params\.([A-Za-z_0-9]+)(?:\|(int|string|double))?(?:\|default\('?"?(.*?)"?'?\))?(?:\|(int|string))?.*?%?}
// group 1 = param name; group 2 OR group 4 = type; group 3 = default value (quotes stripped).
val PARAM_REGEX = Regex(
    """\{%?.*?params\.([A-Za-z_0-9]+)(?:\|(int|string|double))?(?:\|default\('?"?(.*?)"?'?\))?(?:\|(int|string))?.*?%?\}"""
)

// Pass 2 — the `'NAME' in params` / `'NAME' not in params` guard form (adds names with null type/default):
//   {%?.*?if.*?'([A-Za-z_0-9]+)' (?:not )?in params.*?%?}
val PARAM_IN_REGEX = Regex("""\{%?.*?if.*?'([A-Za-z_0-9]+)' (?:not )?in params.*?%?\}""")

data class MacroParam(val name: String, val type: String?, val default: String?)

fun parseMacroParams(gcodeBody: String): List<MacroParam> {
    val out = linkedMapOf<String, MacroParam>()                 // preserve first-seen order, dedup by name
    for (m in PARAM_REGEX.findAll(gcodeBody)) {
        val name = m.groupValues[1]
        val type = m.groupValues[2].ifEmpty { m.groupValues[4] }.ifEmpty { null }
        val default = m.groupValues[3].ifEmpty { null }
        out.putIfAbsent(name, MacroParam(name, type, default))
    }
    for (m in PARAM_IN_REGEX.findAll(gcodeBody)) {
        val name = m.groupValues[1]
        out.putIfAbsent(name, MacroParam(name, type = null, default = null))
    }
    return out.values.toList()
}
// Heuristic by design (D-09): missed edge cases degrade — render whatever was detected, never block Execute.
// type drives the entry widget: int/double → NumpadPage; string/null → (string popup with keyboard).
```
`[VERIFIED: mainsail src/plugins/helpers.ts:246-291]`

### Built-in console filter rules — verbatim from the fork (D-03)
```kotlin
// Source: mainsail @76fcbd2 src/store/gui/console/getters.ts:getConsolefilterRules + src/store/variables.ts:137
// All default OFF (opt-in). Applied at the VIEW layer only (D-04). A line is HIDDEN if any active rule matches.

// Hide temperatures (the M105 temp-report echo):
val HIDE_TEMPERATURES = Regex("""^(?:ok\s+)?(B|C|T\d*):""")

// Hide Timelapse (the fork's full rule set — note these are gcode_store "command" lines):
val HIDE_TIMELAPSE = listOf(
    Regex("""^_TIMELAPSE_NEW_FRAME"""),
    Regex("""^TIMELAPSE_TAKE_FRAME"""),
    Regex("""^TIMELAPSE_RENDER"""),
    Regex("""^_SET_TIMELAPSE_SETUP"""),
    Regex("""^HYPERLAPSE ACTION="""),
    Regex("""^SET_GCODE_VARIABLE MACRO=TIMELAPSE_"""),
)

// Hide prompt commands (matches BOTH the raw gcode_store "// action:prompt…" form and the
// already-"// "-stripped live form — Phase-12 prompt protocol lines):
val HIDE_PROMPT_COMMANDS = Regex("""^(?:// )?action:prompt""")
```
`[VERIFIED: mainsail getters.ts:21-30, variables.ts:137-144]`

### Severity classification from the line prefix (D-02)
```kotlin
// Source: mainsail @76fcbd2 src/components/console/ConsoleTableEntry.vue:37-43 + src/store/server/actions.ts:264-267
enum class ConsoleSeverity { ERROR, WARNING, NORMAL, ACTION, DEBUG }

fun classify(rawMessage: String): ConsoleSeverity = when {
    rawMessage.startsWith("!! ")        -> ConsoleSeverity.ERROR     // → --stop (red)
    rawMessage.startsWith("// action:") -> ConsoleSeverity.ACTION    // dimmed; Phase-12 hook (keep raw)
    rawMessage.startsWith("// debug:")  -> ConsoleSeverity.DEBUG     // dimmed
    rawMessage.startsWith("// ")        -> ConsoleSeverity.WARNING   // → --heat (amber)
    else                                -> ConsoleSeverity.NORMAL    // → --text (or --go for ok)
}
```
`[VERIFIED: mainsail]`

### gcode_store entry → ConsoleLine (null-safe wire walk)
```kotlin
// Mirror the handshake's null-safe JSON walking (MoonrakerSession.parseStatus / objectOrNull / floatOrNullAt).
fun parseGcodeStore(result: JsonObject): List<ConsoleLine> = runCatching {
    (result["gcode_store"] as? JsonArray).orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val message = o["message"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val time = o["time"]?.jsonPrimitive?.doubleOrNull
        ConsoleLine(rawMessage = message, severity = classify(message), timeEpoch = time)
    }
}.getOrDefault(emptyList())   // a malformed store → empty, never fatal (house rule)
```
`[VERIFIED: codebase null-safe-walk discipline + CITED: moonraker.readthedocs.io response shape]`

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Per-screen websocket subscription | Single shared handshake + `objects.subscribe`; console rides `notify_gcode_response` already routed to the store | Phase 2/5 | No new transport for the console — collect the existing flow. |
| Manual macro param schemas | Parse the gcode body for `params.X` + `|default()` (no formal Klipper schema exists) | Mainsail-era convention | D-09's "best we can do" is the actual ecosystem standard. |
| Hand-rolled scroll lists in Compose | RecyclerView-in-`AndroidView` for high-churn surfaces (ADR-0001) | Phase 1/7 | Console is the 3rd such surface; copy Files. |

**Deprecated/outdated:** none relevant — the Moonraker `server.gcode_store` / `notify_gcode_response` API has been stable since 2021 (`time` epochs in the docs example are from March 2021). `[CITED: moonraker.readthedocs.io]`

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `configfile.settings["gcode_macro <name>"].gcode` is a single newline-joined string (not an array) | Confirmed API Surfaces §3 | LOW — Mainsail's regex runs on a string; but confirm live (Pitfall 6) before building the parser fixture. |
| A2 | `!! `/`// ` severity prefixes are Klipper output convention (not formally in the Moonraker notification spec) | Confirmed API Surfaces §2 | LOW — verified via the fork + real-world ubiquity; classifier degrades to NORMAL on absent prefix. |
| A3 | Replace-on-reconnect (not append-dedup) satisfies D-02 because Moonraker keeps the full server-side `gcode_store` across a client disconnect | §1 dedup strategy | LOW-MED — this is exactly Mainsail's behavior; if the disconnect outlives the 1000-entry server buffer, the oldest disconnect-window lines are genuinely gone (Moonraker dropped them, not the client) — acceptable per "bounded scrollback." |
| A4 | `server.gcode_store` is available on both real printers | §1 | LOW — catalogued `planned_v1`, `availability: always`; Moonraker core endpoint since 2021. Confirm with the same planning-time probe as A1. |

**These four assumptions all resolve with ONE read-only planning-time probe** against `192.168.1.120:7125` (`server.gcode_store` + `objects/query?configfile`). Recommend a `checkpoint:human-verify` or an automated live-probe task early in the plan, recording results into `docs/moonraker-capabilities.md` (mock-vs-reality discipline).

---

## Open Questions (RESOLVED)

1. **Console as its own `Dest` tile vs. shared structure**
   - What we know: UI-SPEC leans "likely a new Console tile" with a Material Symbol not already on the drawer (icon-no-repeat law). Existing symbols used: monitoring, open_with, thermostat, folder, output_circle, code, cable, settings, power_settings_new.
   - What's unclear: exact glyph for Console (candidates: `terminal`, `chat`, `notes`, `article` — `terminal` reads best and is unused).
   - Recommendation: add `Dest.Console` + a `terminal`-glyph tile; wire `Dest.Macros` to the Macros `code` tile (Bookmarked launcher).
   - **RESOLVED:** Console gets its own `Dest.Console` with the `terminal` glyph (unused on the drawer — icon-no-repeat law); `Dest.Macros` keeps the `code` tile and opens the Bookmarked launcher (D-07) — both wired in **08-07 Task 1** (TopRoute.kt Dest enum += Macros/Console, AppDrawer.kt tiles, AppShell.kt when-branches).

2. **Strip vs. keep the `!!`/`// ` prefix in the displayed line**
   - What we know: Mainsail strips and colors instead. UI-SPEC says "color + the line text, never color alone."
   - What's unclear: whether keeping the prefix aids a printer-side glance (it's redundant with color).
   - Recommendation: strip for display, derive severity from the original prefix first; keep the raw (prefixed) form in the holder for Phase-12 + filters.
   - **RESOLVED:** Strip the `!! `/`// ` prefix for display and color the line instead; severity is derived from the ORIGINAL prefix BEFORE stripping (via the `ConsoleSeverity.classify` classifier built in **08-02 Task 1**) and the raw prefixed form is kept intact in the holder model for Phase-12 + filters. Display stripping + severity-from-original-prefix is applied in the row adapter — **08-05 Task 2** (ConsoleRowsAdapter).

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Live Moonraker (Ender 5 `192.168.1.120:7125`) | A1/A4 planning-time probe + on-device UAT | ✓ (per capability catalog, refreshed 2026-06-02) | Moonraker v0.10.0 / Klipper v0.13.0 / API 1.5.0 | Synthetic fixture for unit tests (GoldenFixtures precedent) |
| Physical flox device (LineageOS 18.1) | On-device console scroll perf + macro UAT | ✓ | Adreno 320 / 2GB / armeabi-v7a | none — the perf floor MUST be measured on real hardware (emulators lie) |
| WSL→Windows build helper (`E:\Android\gw.bat`) | Build/install | ✓ | JDK 21 / SDK android-35 | none |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** unit-test fixtures stand in for the live printer for host-side tests, but the **live probe (A1/A4) and on-device UAT are mandatory gates** (mock-vs-reality lesson — two prior strikes).

---

## Validation Architecture

> nyquist_validation is enabled (config.json `workflow.nyquist_validation: true`).

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 (host) + kotlinx-coroutines-test (runTest) + Compose ui-test-junit4 / androidx instrumented (on-device) |
| Config file | `gradle/libs.versions.toml` (pinned); no separate test runner config |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` |
| Full suite command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:assembleRelease --no-daemon"` (+ `connectedAndroidTest` for instrumented gates on flox) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| MACRO-01 | `Capabilities.macros` enumerates; underscore-default-hide applied | unit | `...testReleaseUnitTest --tests *MacroHolderTest*` | ❌ Wave 0 |
| MACRO-02 | `parseMacroParams` extracts name/type/default from real macro bodies (incl. `|default`, `'x' in params` forms, no-param macros) | unit (pure) | `...testReleaseUnitTest --tests *MacroParamParserTest*` | ❌ Wave 0 |
| MACRO-02 | String-param sanitization rejects newline/control-char injection | unit (security) | `...testReleaseUnitTest --tests *MacroInvocationTest*` | ❌ Wave 0 |
| MACRO-03 | Bookmark/reveal prefs persist + round-trip via DataStore | unit | `...testReleaseUnitTest --tests *MacroPrefsTest*` | ❌ Wave 0 |
| CONS-02 | `classify()` maps `!!`/`// action:`/`// debug:`/`// `/plain → correct tier | unit (pure) | `...testReleaseUnitTest --tests *ConsoleSeverityTest*` | ❌ Wave 0 |
| CONS-02 | `parseGcodeStore` walks the confirmed shape; malformed entry skipped not fatal | unit | `...testReleaseUnitTest --tests *GcodeStoreParseTest*` | ❌ Wave 0 |
| CONS-02 | Filter rules (temps/timelapse/prompt) match the right lines; raw stream unfiltered (D-04) | unit (pure) | `...testReleaseUnitTest --tests *ConsoleFiltersTest*` | ❌ Wave 0 |
| CONS-02 | Reconnect replace-backfill recovers disconnect-window lines; bounded ring evicts at cap | unit | `...testReleaseUnitTest --tests *ConsoleHolderTest*` | ❌ Wave 0 |
| CONS-02 | Console scroll p95 frame time on flox (3rd Views-in-Compose surface) | instrumented / on-device gfxinfo | `connectedAndroidTest` + manual gfxinfo on flox | ❌ Wave 0 |
| MACRO-01/CONS-02 | Live probe: `server.gcode_store` + `configfile` macro shape confirmed on real printer | manual (read-only curl) + recorded | `curl` probe → update `docs/moonraker-capabilities.md` | ❌ Wave 0 |

### Sampling Rate
- **Per task commit:** `:app:testReleaseUnitTest` (pure parsers/classifiers/holders are all host-testable).
- **Per wave merge:** full unit suite + `:app:assembleRelease`.
- **Phase gate:** full suite green + on-device UAT on flox + live Ender 5 (console populates from real `gcode_store`, a real macro with params executes, scroll is smooth) before `/gsd-verify-work`. **Two prior phases shipped green unit suites that hid live bugs — the live/on-device gate is non-negotiable here.**

### Wave 0 Gaps
- [ ] `MacroParamParserTest.kt` — REQ-MACRO-02; fixtures from REAL macro bodies (probe-captured), not invented
- [ ] `MacroInvocationTest.kt` — REQ-MACRO-02 string-param injection sanitization
- [ ] `ConsoleSeverityTest.kt` / `ConsoleFiltersTest.kt` — REQ-CONS-02 pure classifiers/filters
- [ ] `GcodeStoreParseTest.kt` — REQ-CONS-02 wire-shape walk (fixture = the confirmed docs/probe shape)
- [ ] `ConsoleHolderTest.kt` / `MacroHolderTest.kt` / `MacroPrefsTest.kt` — holders + DataStore round-trip
- [ ] Live read-only probe of `server.gcode_store` + `objects/query?configfile` recorded into `docs/moonraker-capabilities.md`
- [ ] Instrumented console-scroll perf harness on flox (extend the existing benchmark approach; FrameTimingMetric may not work on API 23 — fall back to `gfxinfo framestats` per CLAUDE.md)

*Framework already present — no install needed.*

---

## Security Domain

> security_enforcement enabled, ASVS level 1, block_on: high (config.json).

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no (reuses existing Moonraker API-key/oneshot-token; no new auth surface) | existing `MoonrakerAuth` |
| V3 Session Management | no | existing spine |
| V4 Access Control | no | LAN-trusted client model (project scope) |
| **V5 Input Validation** | **YES (the phase's core security concern)** | Sanitize macro string params before gcode assembly; clamp numeric params before formatting (existing `PrinterCommands` discipline). |
| V6 Cryptography | no | none — no crypto introduced |
| V7 Error Handling / Logging | yes (light) | Failure toasts must NOT embed the API key / `?token=` URL — reuse the existing `DispatchEvent.Failure` redaction (built from `method`/`key` only, never `e.message`). `[VERIFIED: codebase CommandDispatcher.kt:135-149]` |

### Known Threat Patterns for {macro execution with user params}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| **G-code injection via a macro string param** (e.g. `name=val\nM112` or an embedded `SET_HEATER_TEMPERATURE`) | Tampering / Elevation | Sanitize string params: strip `\r`/`\n`/control chars; constrain to Klipper's `KEY=VALUE` macro-arg grammar (Klipper passes the remainder of the line as params — a newline or a second command token is the injection). Assemble as `MACRO KEY="<sanitized>"` and reject values that, after sanitization, still contain a line break or a leading gcode token. NEVER pass raw keyboard text straight into `scriptParams`. This is the explicit exception to "no user string in a script" the existing `PrinterCommands` invariant is built on (PrinterCommands.kt:14-18). |
| Numeric param out-of-range | Tampering | Clamp via the `NumpadPage` `range` (PRIM-01) before formatting — same as every other numeric control. |
| Failure message leaking the API key / token | Information Disclosure | Already mitigated — `DispatchEvent.Failure` composes from non-secret `method`/`key` only (guarded by `failureMessageNeverEmbedsApiKeyOrToken`). Macro failures route through the same path. |
| Filter regex DoS (only if user-regex shipped) | DoS | OUT OF SCOPE — user-defined custom regex filters are DEFERRED; only the 3 fixed built-in regexes ship (no user-supplied regex this phase). |
| Console rendering untrusted printer text | Tampering | Render as plain text in Geist Mono (no HTML/markup interpretation — unlike Mainsail's web `<br>`/`<a>` injection, the native Views path has no markup surface). The Phase-12 prompt protocol is the controlled exception, not this phase. |

**Net security posture:** one genuinely new validation surface — **macro string-param sanitization (V5)** — which is a `block_on: high` concern because a successful injection could issue arbitrary gcode (including `M112` emergency stop or a heater command). It MUST have a dedicated sanitizer + a host unit test (`MacroInvocationTest`) proving newline/second-command injection is neutralized. Everything else reuses already-hardened paths.

---

## Sources

### Primary (HIGH confidence)
- **Codebase (`/mnt/e/claude/personal/github/dinghy-display`)** — `JsonRpcClient.kt`, `JsonRpc.kt`, `MoonrakerSession.kt`, `PrinterStateStore.kt`, `Capabilities.kt`, `DeriveCapabilities.kt`, `CommandDispatcher.kt`, `PrinterCommands.kt`, `CommandRegistry.kt`, `FileListView.kt`, `RingBuffer.kt`, `AppDrawer.kt`, `NumpadPage.kt`, `TopRoute.kt`, `docs/commands/catalog.json` — confirmed the live `notify_gcode_response` plumbing, the existing `configfile` read path, the dispatch/registry pattern, the Views-in-Compose precedent, and `server.gcode_store` already catalogued `planned_v1`.
- **Mainsail fork @ `76fcbd2`** (`/mnt/e/claude/personal/github/mainsail`) — `src/store/gui/console/getters.ts` + `types.ts` (filter regexes/flags), `src/store/variables.ts` (timelapse rule set), `src/plugins/helpers.ts` (`getMacroParams` regex + `formatConsoleMessage`), `src/store/printer/getters.ts` (`getMacros` body source), `src/store/server/actions.ts` (gcode_store backfill replace + live addEvent severity), `src/components/console/ConsoleTableEntry.vue` (severity class) — the exact, verbatim reference patterns.
- **Moonraker official docs** — `moonraker.readthedocs.io/en/latest/external_api/server/` (`server.gcode_store` method/params/response/`type` enum/1000-default) and `.../jsonrpc_notifications/` (`notify_gcode_response` 1-element-array shape).
- **CONTEXT.md (D-01..D-11), UI-SPEC.md (approved), REQUIREMENTS.md, STATE.md, docs/moonraker-capabilities.md** — locked scope + the real-printer field catalog.

### Secondary (MEDIUM confidence)
- The `!!`/`// ` severity-prefix → tier mapping (Klipper output convention, confirmed via the fork; not formally in the Moonraker notification spec → see Assumption A2).

### Tertiary (LOW confidence)
- None — every load-bearing claim is verified against the codebase, the fork, or official docs.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — zero new deps; all reuse, on-device-proven.
- API surfaces (`gcode_store`, `notify_gcode_response`, macro-body path): HIGH — triple-confirmed (codebase plumbing + Mainsail + Moonraker docs), with ONE low-risk live-probe item (config macro JSON nesting, A1).
- Filter regexes + param-parse algorithm: HIGH — extracted verbatim from the fork.
- Architecture/patterns: HIGH — direct reuse of existing, named, proven patterns.
- Security: HIGH on the analysis, with macro string-param sanitization (V5) flagged as the one new control requiring a dedicated test.

**Research date:** 2026-06-02
**Valid until:** ~2026-07-02 (stable APIs; Moonraker `gcode_store` unchanged since 2021). Re-verify only the A1/A4 live-printer shape if the printers' Klipper/Moonraker are updated.
