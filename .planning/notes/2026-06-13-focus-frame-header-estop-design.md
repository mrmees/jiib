# FocusFrame Header + Docked E-Stop — Design

**Date:** 2026-06-13
**Status:** DESIGN — approved direction, pending spec review → writing-plans
**Supersedes/extends:** `.planning/notes/2026-06-12-focus-frame-law-design.md` (the original Focus
Frame law). The `FocusEdge` sealed type and `FocusFrame` shell from that law are unchanged; this adds
a **mandatory header** and **docks the e-stop into it**.
**Related law:** `docs/ui_design/COMPONENTS.md §FocusFrame`, `docs/ui_design/LAYOUT.md` (Focus region),
UAT-4 (top-left e-stop reservation — to be *retired* by this change).

---

## 1. Motivation

The emergency-stop today is a `FloatingEStop` — a printing-only button that **floats over** the Focus
content in the top-`start` corner. It works, but it's a hack:

- It overlaps content, forcing UAT-4 to permanently reserve the Focus top-left corner.
- It's a separate overlay element / code path (every screen wires its own `ConfirmGuard` +
  `showEstopGuard` boolean alongside it).
- It's layout-fragile (the "overlay-sibling positioning rule, Pitfall 7" in `FloatingEStop` KDoc).

**The fix:** give every `FocusFrame` a structural **header** — a `start` icon + centered title. The
icon is the screen's identity glyph when idle; during a print it **morphs in place into the e-stop
button**. No overlay, no new element, no reserved corner. The e-stop gets an honest, consistent home,
and the per-screen guard boilerplate collapses into the component.

This is a uniformity play: the e-stop lives in the **same place on every screen**, always, which is
exactly what you want for a safety control on a printer-side surface.

---

## 2. Decisions locked (this session)

| # | Decision | Choice |
|---|----------|--------|
| D1 | E-stop home | **Replace `FloatingEStop` entirely** with the header icon slot (except webcam — D5). |
| D2 | Icon placement | **`start`** (RTL-aware), title **centered** across full width. |
| D3 | Webcam | **Keeps `FloatingEStop`** as a one-screen exception (full-bleed media, `SurfaceView` + Adreno H.264 rotation limitation — can't take a header strip). |
| D4 | When non-Focus screens get a header | **Always present** — every screen carries a Focus header, idle or printing (max uniformity; accepted ~1U vertical cost on the 5U floor). |
| D5 | Exemptions | **Webcam** (keeps float) and **Splash** (transient startup, no print possible) are the only screens without a header e-stop. |
| D6 | Guard ownership | **`FocusFrame` internalizes** the e-stop button + its `ConfirmGuard` + long-press panic. Callers pass `isPrinting` + `onEmergencyStop` only. |
| D7 | Idle icon | **Inert identity glyph** — decorative wayfinding, NOT tappable (so muscle memory never trains a tap on the spot that becomes a live e-stop). |
| D8 | Empty-Focus content | When a Focus frame is created **from nothing** (no natural hero), its body is a **general description of what the screen presents** — a short informational blurb of the screen's purpose/content. Simple, uniform rule (not bespoke-per-screen). |
| D9 | Icon + title source | **Default: reuse the icon + label of the nav entry point that opens the screen** (the home list-row / button — "use whatever button got them there"). That glyph is already owner-assigned, so it respects the icon law AND ties the destination header to the affordance that reached it. Per `[[dinghy-never-pick-icons-ask]]`, Claude ASKS the owner **only** when a screen has no entry button (or its entry lacks an icon) — never auto-pick. |

---

## 3. The `FocusFrame` header contract

`FocusFrame` gains a **required** header rendered inside the frame, above the content slot.

### New / changed params

```kotlin
@Composable
fun FocusFrame(
    title: String,                        // NEW (required) — centered header title
    icon: DinghyIcon,                     // NEW (required) — start identity glyph (idle)
    modifier: Modifier = Modifier,
    edge: FocusEdge = FocusEdge.Neutral,  // unchanged
    // E-stop wiring (D6 — FocusFrame owns the whole thing):
    isPrinting: Boolean = false,          // when true, the icon slot becomes the e-stop button
    onEmergencyStop: (() -> Unit)? = null,// guarded tap → halt; null = no e-stop (e.g. Splash)
    onPanic: (() -> Unit)? = null,        // optional long-press = instant halt, no guard (float's onHold)
    uDp: Dp,                              // required — sizes the e-stop (0.7U) and header (1U); from rememberUnitGrid
    content: @Composable ColumnScope.() -> Unit,
)
```

- `title` + `icon` are **required** → the compiler forces every call site to supply both. No screen
  can silently ship without a header. This is the enforcement mechanism for "all focus frames have a
  title/icon."
- `isPrinting`/`onEmergencyStop`/`onPanic`/`uDp` are the e-stop seam. A `FocusFrame` with
  `onEmergencyStop == null` simply never shows an e-stop (Splash, previews).

### Layout

```
+--------------------------------------------+  <- FocusFrame (t.surface, FocusEdge border)
| (icon)            Title                     |  <- header: 1U tall, icon/e-stop 0.7U start, title centered
|--------------------------------------------|
|                                            |
|   content slot (the existing ColumnScope)  |
|                                            |
+--------------------------------------------+

Printing:
| [X]               Title                     |  <- icon slot swapped to red e-stop (Intent.Danger, StatusStop)
```

- **Header height = 1U** (one grid unit — clean integer-U, satisfies the control cap). Icon/e-stop
  sized **0.7U** (matching the retired float, `(uDp*0.7f).coerceAtLeast(64.dp)`), vertically centered.
- Title **centered across the full frame width** (not centered in the leftover space) — the icon
  overlaps the left end of the title's track, exactly like a Material top-app-bar nav icon. Title is
  `start`-icon-agnostic: it does not shift when the icon morphs to the e-stop.
- Header is **self-owned** by `FocusFrame` (like the existing `ListFrameInset`/`FocusInset` it already
  owns). Callers never lay out the header.
- Header is **orthogonal to `FocusEdge`** — Neutral/Data/Progress edges are unchanged and coexist
  with the header.

### Title typography

Centered, **Geist SemiBold ~20sp via `fsSp(20f, t.fs)`** (the list/title default; scales with S/M/L).
`t.text` color. (Open to GeistMono if the owner prefers the tabular look — minor, confirm at build.)

**Title text source (D9):** the label of the nav entry that opens the screen ("use whatever button got
them there") — same source as the icon, so header and affordance stay in lockstep.

---

## 4. Idle → printing morph (the e-stop)

- **Idle** (`isPrinting == false`): icon slot renders the screen's **inert identity glyph** (`icon`).
  Not clickable. Pure wayfinding.
- **Printing** (`isPrinting == true` AND `onEmergencyStop != null`): the **same slot** renders the
  e-stop button — `Intent.Danger`, `DinghyIcons.StatusStop` (the canonical owner-assigned halt glyph,
  reused exactly as `FloatingEStop` does today), 0.7U, `cd_emergency_stop` content description.
  - **Tap** → `FocusFrame`'s **internal `ConfirmGuard`** (D6) → on confirm calls `onEmergencyStop`.
  - **Long-press** → `onPanic` if supplied (instant halt, no guard — the float's `onHold` semantics:
    tap = guarded, hold ≈ ½s = immediate halt).
  - Title stays put across the morph (wayfinding survives).

This deletes, per screen: the `showEstopGuard` boolean, the screen-level `ConfirmGuard` Box sibling,
the `FloatingEStop` Box-sibling wiring, and the `Box { Focus + FloatingEStop }` overlay scaffolding.

---

## 5. `FloatingEStop` — reduced, not deleted

`FloatingEStop` survives **only** for `WebcamScreen` (D3/D5). Its KDoc gets a note that it is now a
single-screen exception, not the general pattern. Everywhere else its call sites are removed in the
migration (§7).

---

## 6. The audit (D4/D8 — per-screen)

"Always present" means **every screen** (except the two exemptions) must provide a `FocusFrame` in its
`ScreenScaffold` `focus` slot. The audit's job: for each screen, decide the **header icon** (ASK owner
— D9) and the **Focus body content** (per-screen — D8).

### Group A — already have a `FocusFrame` (8 screens / 9 files; Outputs spans two): retrofit header only

Temperature, Spool, Files, Outputs, Calibration Hub, FineTune, PrintStatus, Printers.

- Add `title` + `icon` to existing `FocusFrame` call sites.
- Wire `isPrinting`/`onEmergencyStop`/`onPanic` from the screen's existing print-state + e-stop
  dispatch; **delete** the screen's own `FloatingEStop` + `ConfirmGuard`/`showEstopGuard`.
- ⚠ Morphing/multi-`FocusFrame` screens (e.g. Temperature graph↔adjuster — both are `FocusFrame`s but
  only one shows at a time; PrintStatus modes): each `FocusFrame` carries the header; only the visible
  one is on screen, so exactly one e-stop shows. Confirm per screen.

### Group B — have a Focus/Scaffold region, no `FocusFrame` (11): wrap + header + content decision

BedMesh, ProbeCalibrate, ScrewsTilt, Tilt (the 4 Calibration sub-screens), Console, Extrude, Macros
(BookmarkedMacros), About, Settings, SystemPage, SystemInformation.

- Wrap the `focus` slot content in a `FocusFrame` (or, if the screen currently has no `focus` content,
  add one).
- **Focus body content (D8):** if the screen has no natural hero, the body is a **general
  description of what the screen presents** — a short informational blurb (e.g. Console: "Live printer
  console output"; Settings: "App and printer settings"). Where a screen *does* have natural Focus
  content (e.g. a Calibration sub-screen's readout/diagram), use that instead of a blurb.
- **Icon (D9):** ASK owner for the glyph.

### Group C — no Scaffold at all (1): most work

Move (custom layout, no `ScreenScaffold`).

- Restructure onto `ScreenScaffold` + `FocusFrame`, or add a `FocusFrame` header to its custom layout.
- Per-screen content + icon decisions as Group B.

### Exemptions (2)

- **Webcam** — keeps `FloatingEStop`, no header (full-bleed).
- **Splash** — no header / no e-stop (transient, no print state).

> NOTE: overlays (PromptDialog, scan surfaces, ConfirmGuard, DevThemeCycler) are not screens and are
> out of scope.

---

## 7. Migration impact

- **`FocusFrame.kt`** — add header params + layout + internal e-stop + internal `ConfirmGuard`.
- **9 Group-A call sites** — add title/icon, wire e-stop, delete local float+guard.
- **~12 Group-B/C screens** — add `FocusFrame`, icon, content; delete local float+guard if present.
- **`FloatingEStop.kt`** — reduce to webcam-only; KDoc note.
- **UAT-4** (top-left e-stop reservation) — **retired** in `docs/ui_design/LAYOUT.md`; replaced by the
  header dock. Update COMPONENTS/LAYOUT law.
- **Previews** — every `FocusFrame`/screen preview needs the new required title/icon args.
- **Icon registry** — any new owner-assigned glyphs registered in `DinghyIcons` +
  `material-icon-bucket.json` (+ catalog drift guards if applicable).

### Icon/title resolution (D9 — "use whatever button got them there")

**Most screens auto-resolve** their header icon + title from the nav entry that opens them — no ASK
needed (glyph is already owner-assigned at the entry):

- **From `HomeAction.kt`** (the home/launcher registry — `icon = DinghyIcons.Launcher*` + `labelRes`):
  Spool, Files, Move, Extrude, Macros, Calibration Hub, Temperature, Console, FineTune, Outputs
  (`OutputSection`), Webcam *(exempt)*.
- **From parent list-rows** (sub-screens inherit the row that opens them):
  Calibration sub-screens (BedMesh, ProbeCalibrate, ScrewsTilt, Tilt) ← Calibration Hub rows;
  About / SystemInformation / Printers ← their Settings/System parent rows.

**Genuine orphans — MUST ASK owner** (reached by state/programmatically, no button with an icon):

- [ ] **PrintStatus** — the conditional-waterfall print root, reached by print state, not a tap.
- [ ] **Settings** / **SystemPage** — confirm their entry affordance; if no icon-bearing entry, ASK.

Planning resolves each precisely; default is always "reuse the entry's glyph + label," ASK only for a
confirmed orphan.

---

## 8. Sizing & floor cost (honest note)

Permanent 1U header = **15-20% of vertical** gone on the 5U Nexus 7 floor, on every screen, always
(D4 accepted this). Mitigations baked in: header capped at 1U, e-stop at 0.7U, title scales with `--fs`
rather than forcing a tall bar. Watch list-row counts on Console/Files/Settings at 5U during UAT; if it
bites, revisit D4 (conditional/print-driven header) as a fallback.

---

## 9. Testing

- **Host:** `focusEdgeStroke` unchanged; add pure tests for header layout invariants where testable
  (title centered, icon→e-stop swap keyed on `isPrinting && onEmergencyStop != null`). The morph is a
  pure `when`-style branch — host-testable like the existing `FocusEdgeTest`.
- **On-device (flox + moto, both ABIs — `[[dinghy-test-devices]]`):** e-stop reachable + fires on every
  screen during a live print; idle icon inert; guard + long-press panic behave; 5U row counts
  acceptable. Live-print gate is owner-driven (`[[dinghy-display-ondevice-iteration]]`).
- Beware the stale-APK trap (`[[dinghy-stale-apk-uat-gate]]`): force-rebuild before UAT.

---

## 10. Out of scope / deferred

- `FocusEdge.Progress` perimeter bar — still deferred (its own session, per the original law).
- The paused spacing-scale pass — unaffected.
- Conditional/print-driven header (the D4 alternative) — fallback only if the floor cost bites.
