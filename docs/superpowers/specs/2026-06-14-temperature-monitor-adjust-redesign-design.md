# Temperature Screen — Monitoring / Adjust Redesign

**Date:** 2026-06-14
**Branch:** `temperature-monitor-adjust` (fork/feature-isolated)
**Status:** Approved design — ready for planning
**Supersedes:** the merged single-morph Temperature screen (Phase 26 / D-10..D-14)

---

## 1. Intent

The Temperature screen has two jobs that today are tangled into one morphing Focus:

1. **Monitor** current temperatures across every sensor in the system.
2. **Adjust** the target of individual heaters.

Heating is now driven by **presets on the home screen**, so the screen's default
arrival state is the **monitoring** function. This redesign splits the screen into two
explicit, footer-toggled modes and adds two capabilities that don't exist today:
arbitrary `temperature_sensor` display, and a target scrubber scaled to each heater's
real Moonraker-reported limits.

---

## 2. Mode model

A single screen with `screenMode: Monitoring | Adjust`. **Monitoring is the default on
entry.** One toggling footer slot swaps the modes — there is no separate "adjust screen".

| | **Monitoring** (default) | **Adjust** |
|---|---|---|
| **Footer** | `Back · Settings · ModeToggle` | `Presets · Cooldown · ModeToggle` |
| **ModeToggle glyph** | `mode_heat` (→ Adjust) | `format_list_bulleted` (→ Monitoring) |
| **Back** | exits the screen | **absent** — the ModeToggle is the way back to Monitoring |
| **Field list** | **all** monitored sensors | **adjustable only** (heaters) |
| **Tap a row →** | Monitoring popup (appearance) | Adjust popup (control) |

The mode toggle is **one button in one footer slot** that changes glyph + action by
mode. `Back` only exists in Monitoring; from Adjust you return to Monitoring via the
toggle, then `Back` exits. This enforces "monitoring is home".

---

## 3. The monitored set

The **monitored set** is what the Field list shows in monitoring mode and what the
graph can draw:

```
monitored = heaters (always)  ∪  user-selected temperature_sensors
```

- **Heaters** (`heater_bed`, `extruder*`, `heater_generic *`) are **always** monitored
  and **always** appear — they cannot be removed via Settings.
- **`temperature_sensor <name>`** objects are **opt-in**: off by default, added/removed
  through the Settings sensor-picker. The selection **persists** across restarts.
- The old hard **3-trace cap is dropped.** The monitored set is unbounded; the user
  manages graph clutter/perf via per-sensor **visibility** (see §6).

### Per-sensor attributes
Each monitored sensor carries:
- **color** — user-chosen from the Colorful-8 pool (existing `traceColors`).
- **visibility** — whether it draws on the **graph** (existing `traceVisibility`).
  Visibility does **NOT** affect list membership — a hidden sensor still shows in the
  list with its live temperature.
- **adjustable** — `true` for heaters, `false` for `temperature_sensor`s. Drives the
  Adjust-mode list filter and whether the Adjust popup shows temp controls.

---

## 4. Focus region (morphs)

The Focus always mounts the graph by default and **never** uses `AnimatedContent` around
`GraphViewHost` (Phase-22 D-12 guard: the AndroidView factory must run once). The morph
is plain `if/else` on the active sub-state.

### 4.1 Default — live graph
The existing multi-trace `GraphViewHost` inside a `FocusFrame`, visibility-filtered.
Unchanged rendering. Present in both modes when no row/gear is selected.

### 4.2 Settings sensor-picker (Monitoring only)
Tapping the **Settings** gear replaces the Focus with a **sensor-picker list**: every
`temperature_sensor <name>` object known from `Capabilities.objects`, each a `ListRow`
with an add/remove toggle (in-monitored-set state). Heaters are **not** listed here
(always monitored). This is a **Focus morph** — the Field list stays on the monitored
sensors; only the Focus changes. A bottom `Done` button returns the Focus to the graph.

### 4.3 Monitoring popup — appearance (tap a row in Monitoring)
`FocusFrame(title = sensor.label, icon = iconForSensor(...))` containing **only**:
- A **4×2 color grid** (the Colorful-8 pool, `seedHex`/`dark`-derived — finding 5, NOT
  `t.pool.take(8)`). **Larger dots** than today's single row; the grid fills the leftover
  vertical space between the title and the bottom buttons. Two rows of four is the safe
  layout for all devices (down to the 5U floor).
- A bottom button row: **`Visibility · Done`**. Visibility toggles graph-drawing only;
  Done returns the Focus to the graph.
- **No temperature controls.** Even for heaters — temperature is an Adjust-mode concern.

### 4.4 Adjust popup — control (tap a row in Adjust)
`FocusFrame(title = heater.label, icon = iconForSensor(...))` containing **only**:
- A current-value readout (the working/live target).
- A **scrubber scaled to the heater's real limits**: range `0 .. max_temp` (0 = off).
  The 004 ringed-thumb `Scrubber` (R9), horizontal, track+thumb ≤ 1U (UAT-3).
- The ± **stepper + `IncrementPicker`** (existing `AdjusterPanel`, `TEMP_STEPS`).
- A bottom button row: **`Done · Off`**. Done returns to graph; Off = `Intent.Warn`,
  target 0 (routed through the batcher's guaranteed-delivery `tap(0.0)` — undroppable-Off
  invariant, quick-rmr post-review fix 1).
- **No color/visibility.** Appearance is a Monitoring-mode concern.

Scrubber + stepper both drive **one** working target through the existing
`TrailingCommitBatcher`: scrubber drag and stepper taps update the displayed working
target live; one `setHeater` fires per ~500ms quiet window. `min_temp` is informational
only — the scrubber floor stays 0 so you can scrub a heater to off.

---

## 5. Data layer changes

### 5.1 Holder (`TemperatureHolder`) — extend beyond 3 heaters
- **Drawn/monitored resolution:** replace `resolveDrawn` (≤3 heaters) with
  `heaters (always) ∪ persisted selected temperature_sensors`, in a deterministic order:
  **two groups — all heaters first, then selected `temperature_sensor`s — alphabetical
  (by object name) within each group.**
- **Rings:** one `RingBuffer` per monitored member, rebuilt when the monitored set
  changes (a sensor added/removed via Settings re-resolves the set). Existing rings for
  unchanged members are preserved where practical; added members seed from backfill if
  available, else start empty (acceptable — history fills live).
- **Live values:** heater members push `state.heaters[name]?.temperature`; sensor members
  push `state.temperatureSensors[name]`.
- **Legend:** `SensorReadout` per monitored member. `isAdjustable = name in heaters`.
  Sensors have `target = null` always.
- **No second throttle** (Phase-4 lesson) — still consume the store's conflated flow.

### 5.2 Backfill — widen the request set
`server.temperature_store` already returns heaters **and** `temperature_sensor`s, and
`parseTemperatureStore(result, sensors)` already maps any requested name. The handshake
must request the **full monitored set** (or simply all temperature objects) rather than
only the 3 drawn heaters. Added-after-connect sensors backfill on the next set change if
their history is still in the one-shot snapshot; otherwise they fill live.

### 5.3 New persistence — selected sensors
A new persisted field beside `TraceStylePrefs` (trace colors/visibility): the set of
user-selected `temperature_sensor` names. Same discipline:
- DataStore-backed, seeded into a holder `StateFlow` on first emission.
- **Writes route through the process-lifetime `writeScope`** via an `AppContainer` intent
  method — never `rememberCoroutineScope()` (P14 write-scope-cancellation trap).

### 5.4 New backend — heater limits
Per-heater `max_temp` / `min_temp` do not exist in the app today (only a fixed
`MAX_TEMP_C = 350` clamp). Add a **one-shot `configfile` query at handshake**, mirroring
the `temperatureBackfill` pattern:
- Query `printer.objects.query` for `configfile` (the parsed `settings`/`config` carries
  `<heater>.max_temp` / `min_temp` as numbers).
- Parse into a pure, host-testable mapper (mirror `parseTemperatureStore` shape) →
  `Map<String, HeaterLimits>` keyed by heater object name.
- Store on `PrinterState` (or a sibling capability map landed at handshake like the
  backfill StateFlow). Expose to the screen.
- **Scrubber range** = `0 .. (limits[name]?.maxTemp ?: MAX_TEMP_C)`. The global
  `clampHeaterTarget` 0..350 stays as the safety net; optionally tighten it per-heater
  when limits are known (decide in planning — not required for v1 of this pass).

---

## 6. Visibility vs. list membership (explicit)

| Action | Affects graph trace? | Affects list row? |
|---|---|---|
| Settings: add/remove a `temperature_sensor` | yes (joins/leaves monitored set) | yes |
| Monitoring popup: Visibility toggle | **yes** | **no** (row always stays) |
| A heater (always monitored) | drawn iff visibility-ON | always in the list |

---

## 7. Icons (registry-only, owner-chosen)

| Use | Glyph | Registry |
|---|---|---|
| Enter Adjust (Monitoring footer toggle) | `mode_heat` | existing `DinghyIcons.OutputHeater` |
| Return to Monitoring (Adjust footer toggle) | `format_list_bulleted` | **NEW** registry entry (owner-chosen) |
| Settings gear (Monitoring footer) | `settings` | existing `settings` ligature (reuse / semantic alias) |
| Sensor-picker rows, Presets, Cooldown | existing / text | no new glyphs |

No glyph is auto-picked. The one new entry (`format_list_bulleted`) was chosen by the
owner; register it in `DinghyIcons.kt` + the icon bucket per the registry law.

---

## 8. Out of scope (YAGNI)

- `GraphViewHost` rendering internals — unchanged.
- The internal `PresetSelector` scrim — retained for PrintStatus Preheat reuse (16-06).
- Multi-extruder beyond existing naming support.
- Any per-heater clamp tightening beyond the scrubber range (optional; deferred to
  planning if cheap).

---

## 9. Testing notes

- **Pure/host-testable** (JVM suite, no Android): the new `configfile`-limits mapper
  (mirror `parseTemperatureStore` tests — order, selection, absence, garbled fields);
  the monitored-set resolution (heaters-always ∪ selected sensors, deterministic order);
  scrubber-range derivation from limits with the 350 fallback.
- **Holder behavior:** rings rebuild on set change; sensor members push from
  `temperatureSensors`; legend `isAdjustable` correctness.
- **Persistence:** selected-sensors writes route through `writeScope` (not composition);
  survive a simulated process restart.
- **On-device (flox + moto):** mode toggle round-trip; Settings add/remove a real
  `temperature_sensor`; monitoring popup color-grid 4×2 fill at 5U; adjust popup scrubber
  scaled to a real heater `max_temp`; Off undroppable; no graph thrash on morph.

---

## 10. Decisions log (this brainstorm)

1. **Heater limits:** fetch **real** per-heater `max_temp`/`min_temp` from Moonraker
   `configfile` now (not interim 350, not hardcoded defaults).
2. **Mode toggle:** a **single toggling footer button** (glyph + action swap by mode);
   `Back` exits only from Monitoring.
3. **Presets + Cooldown:** both move into **Adjust** mode's footer.
4. **Adjust footer:** `Presets · Cooldown · Monitor` (three buttons, **no Back**).
5. **Trace count:** **no cap** — trust per-sensor visibility.
6. **Added-sensor selection:** **persisted** (DataStore, like trace colors).
7. **Monitor-toggle glyph:** `format_list_bulleted` (owner).
8. **Scrubber:** shares the trailing-commit batcher with the stepper; floor 0 (= off);
   `min_temp` informational.
9. **Monitored ordering:** two groups — heaters first, then selected sensors —
   **alphabetical by object name within each group** (owner).
