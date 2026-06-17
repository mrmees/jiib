# FootButtonBar count-driven icon/label conformance

**Date:** 2026-06-17
**Status:** design — Codex-reviewed (NEEDS-REWORK → revised); 1 open owner gate (`temp_presets` glyph)
**Author:** brainstorm session (Matthew + Claude)

> **Codex review (2026-06-17):** verdict NEEDS-REWORK on the first draft. All 1 BLOCKER + 6
> MAJOR + 2 MINOR findings verified against the code and folded in: F1 (calibration_home_all
> iconless → `home_app_logo`), F2 (per-button modifier passthrough), F3 (30 call sites not
> 23), F4 (temp_presets thermostat collision → owner re-pick, OPEN), F5 (finetune_reset_all
> refresh collision → `reset_settings`), F6 (calibration_abort e-stop collision →
> `stop_circle`), F7 (blank-label/drawable-icon guards), F8 (allow-list + registration
> ordering), F9 (field-takeover bars in scope, "dialog" wording).

## Problem

Foot-of-region action buttons (`FootButtonBar`) are inconsistent about whether they
render **icon-only** or **icon + text label**. The decision is currently made *per call
site* — `OutlinedControl` renders icon-only when passed a blank `label`, icon+label when
passed both — so there is no single rule and the app has drifted.

The owner's rule:

> **A bar with more than 2 buttons (≥3) → every button is icon-only.
> A bar with 2 or fewer buttons (≤2) → every button is icon + text label.**

This rule is a **function of the bar's live child count**, and several bars vary their
count at runtime (PrintStatus by print state, the calibration screens by routine state,
Spool/Temperature by mode). A static per-call label choice cannot stay correct across those
states. Therefore the rule must live in the container, not at the call sites.

## Scope

**In scope:** every `FootButtonBar` instance in the app — **30 call sites across 23 files,
plus 1 design preview** (`DesignKitComponentPreviews`). This includes field foot bars,
focus-docked foot bars, AND field-takeover foot bars that use `FootButtonBar` (BedMesh
SaveName at `BedMeshScreen.kt:482`, Spool measure/filter). Each bar is counted independently
by its own live child count. (Codex review F3/F9: the earlier "23 screens" framing
undercounted call sites and wrongly excluded field-takeover bars.)

**Out of scope:** true modal dialogs / `ConfirmGuard` (these are NOT `FootButtonBar`),
`AdjusterPanel` steppers, `IncrementPicker`, the shell-level e-stop. No per-button
exceptions — the rule is purely count-based.

## Approach (decided)

**Container-driven list API.** Convert `FootButtonBar` from a
`content: @Composable RowScope.() -> Unit` lambda to a typed `List<FootAction>`. The bar
counts `actions.size` at render time and applies the threshold centrally. Conditional bars
get correct behavior for free because the list is rebuilt every recomposition.

The old lambda overload is **deleted**, making the rule **compile-enforced** — a call site
cannot construct a foot bar that bypasses the rule. (An audit confirmed every current
foot-bar child is an `OutlinedControl`, so no call site needs arbitrary non-button content.)

Alternatives rejected:
- *Call-site sweep* — no enforcement (drift returns; that's why this work exists) and
  conditional-count branches must be hand-reasoned and go stale.
- *Hybrid CompositionLocal compact flag* — spooky action-at-a-distance, and the bar still
  can't count a lambda's emissions without help.

## Design

### The rule, in one constant

```kotlin
const val FOOT_BAR_ICON_ONLY_THRESHOLD = 3   // size >= 3 -> icon-only; size <= 2 -> icon+label
```

### Data model

```kotlin
data class FootAction(
    val label: String,                       // resolved string (stringResource at call site)
    val icon: DinghyIcon,                     // REQUIRED — both modes carry an icon
    val onClick: () -> Unit,
    val intent: Intent = Intent.Neutral,
    val contentDescription: String? = null,  // icon-only a11y; defaults to label when null
    val onLongClick: (() -> Unit)? = null,
    val enabled: Boolean = true,
    val fill: Color? = null,
    val modifier: Modifier = Modifier,       // per-button passthrough (Codex F2) — see below
) {
    init {
        require(label.isNotBlank()) { "FootAction.label must be non-blank (Codex F7)" }
        require(icon.primary is IconRef.Ligature) {
            "FootAction.icon must be ligature-backed (OutlinedControl throws on drawable; Codex F7)"
        }
    }
}
```

`icon` is non-null AND ligature-backed: under this rule **every** foot button carries a glyph
(the ≤2 mode is icon+label, not text-only), so an iconless foot button is now impossible to
express. The `init` guards close the gap Codex flagged (F7): the list API alone didn't stop a
blank label or a drawable-backed icon (which `OutlinedControl` throws on at runtime,
`OutlinedControl.kt:214`).

**`modifier` passthrough (Codex F2).** Several existing foot buttons carry per-button
modifiers the bar must not drop:
- Files print/delete: `.alpha(0.38f)` + `Modifier.semantics { disabled() }` when the action
  is unavailable (`FilesScreen.kt:634`) — the WR-07 dim convention, distinct from `enabled`.
- Console filter toggles: `Modifier.semantics { selected = … }` with state-flipped `intent`
  (`ConsoleScreen.kt:242`).
- Macros: alpha/disabled semantics (`BookmarkedMacrosScreen.kt:540`).

The bar applies `Modifier.weight(1f).then(a.modifier)`, preserving every existing per-button
modifier verbatim. (FootAction is intentionally NOT `@Immutable` — it holds lambdas and a
`Modifier`; foot bars are tiny and rebuild each frame, so recomposition-skipping is moot.)

### New API

```kotlin
@Composable
fun FootButtonBar(
    uDp: Dp,
    actions: List<FootAction>,
    modifier: Modifier = Modifier,
)
```

Internally:

```kotlin
val iconOnly = actions.size >= FOOT_BAR_ICON_ONLY_THRESHOLD
// ... existing Row + LocalUnitDp + controlHeight(uDp) wrapper unchanged ...
actions.forEach { a ->
    OutlinedControl(
        label = if (iconOnly) "" else a.label,
        onClick = a.onClick,
        modifier = Modifier.weight(1f),
        intent = a.intent,
        icon = a.icon,
        onLongClick = a.onLongClick,
        contentDescription = a.contentDescription ?: a.label,  // icon-only still speaks a label
        enabled = a.enabled,
        fill = a.fill,
        modifier = Modifier.weight(1f).then(a.modifier),       // Codex F2: preserve per-button modifiers
    )
}
```

Note: `OutlinedControl`'s `modifier` is its own parameter, so the bar passes
`Modifier.weight(1f).then(a.modifier)` as that argument (not a separate wrapper).

The `Row`/`LocalUnitDp`/`controlHeight(uDp)`/`Arrangement.spacedBy(8.dp)` structure is
preserved verbatim — only the child-emission source changes (lambda → iterated list).

Optional convenience: `FootAction.from(spec: ControlSpec, onClick, …)` so the named-control
sites (`OutlinedControl(spec = …)`) stay terse and keep their drift-tested
`contentDescription`.

### Call-site migration (30 call sites / 23 files + 1 preview)

Including the previously-understated bars: `WebcamScreen.kt:225`, `OutputsScreen.kt:245`,
`OutputToggleControl.kt:120`, `SystemInformationScreen.kt:260`, `AppSettingsScreen.kt:268`,
`SystemPageScreen.kt:169`, `PrinterSettingsScreen.kt:293`, and both macro bars
(`BookmarkedMacrosScreen.kt:519` and `:678`). Generate the authoritative list by grep before
execution.

Each site builds a `List<FootAction>`; the conditional bars use `buildList { … }` so the
count reflects live state. Example (PrintStatusField):

```kotlin
val actions = buildList {
    if (isPrinting) {
        if (isPaused) add(FootAction(resumeLabel, DinghyIcons.FootResume, onResume, Intent.Go))
        else          add(FootAction(pauseLabel,  DinghyIcons.PauseCircle, onPause, Intent.Warn))
        add(FootAction(cancelLabel, DinghyIcons.FootCancel, { showCancelGuard = true }, Intent.Danger))
        // …
    }
    // …
}
FootButtonBar(uDp = uDp, actions = actions)
```

`stringResource` is resolved at the call site (composable context) when building the list.

## Icon assignments (LOCKED 2026-06-17)

Every foot button now needs a glyph. The audit found 28 iconless buttons (excluding
`ControlSpec`-overload false positives, which already carry icons). De-duped across screens,
the assignments are below. **All chosen by the owner** per the hard icon law
([[dinghy-never-pick-icons-ask]]).

### Reuse existing registered tokens (no new ligature)

| Action(s) | Token | Ligature |
|---|---|---|
| Back ×5 (Printers, OutputToggle, PrinterSettings, SystemPage, SystemInfo) | `Back` | `arrow_back` |
| calibration_accept, mesh_save_confirm, mesh_apply | `CheckCircle` | `check_circle` |
| calibration_run_again | `Revert` | `refresh` |
| finetune_reset_all | `ResetSettings` | `reset_settings` |
| mesh_remove, printers_delete | `Delete` | `delete` |
| printers_edit | `Edit` | `edit` |
| mesh_calibrate | `RoutineBedMesh` | `blur_linear` |
| **calibration_home_all** (ControlSpec; was `icon = null`) | `MoveHomeAll` | `home_app_logo` |

> **Codex BLOCKER F1 — fixed.** `ControlSpecs.calibrationHomeAll` (`ControlSpecs.kt:36`) had
> `icon = null` (a genuinely label-only foot button the audit missed because it's the
> `ControlSpec` overload). Owner-assigned `home_app_logo` (reuses `MoveHomeAll`). The plan
> sets `calibrationHomeAll.icon = DinghyIcons.MoveHomeAll` and adds a `contentDescriptionRes`
> (the bar renders it icon-only in ≥3 bars, so it needs a spoken label).
>
> **Codex MAJOR F5 — fixed.** `finetune_reset_all` was `refresh`/`Revert`, colliding with the
> per-field revert glyph (also `Revert`) at `FineTuneScreen.kt:318` on the same screen. Owner
> moved it to `reset_settings` (`ResetSettings`), distinct from the per-field revert.

### New ligatures (must pass `tools/verify_ligatures.py` against bundled v2.944 ttf)

| Action(s) | New token | Ligature |
|---|---|---|
| calibration_run, calibration_start | `CalibrationRun` | `play_circle` |
| calibration_running, probe_starting | `CalibrationWait` | `hourglass` |
| printers_add | `PrinterAdd` | `print_add` |
| calibration_save_config, mesh_save | `Save` | `save` |
| common_cancel (BedMesh SaveName field-takeover foot bar) | `DialogClose` | `tab_close` |
| **calibration_abort** | `CalibrationAbort` | `stop_circle` |

> **Codex MAJOR F6 — fixed.** `calibration_abort` was `disabled_by_default`/`StatusStop`,
> which is also the FocusFrame e-stop glyph (`FocusFrame.kt:320`, shown when `isPrinting`),
> and calibration screens pass `isPrinting` into `FocusFrame` — a possible same-screen
> collision. Owner moved abort to `stop_circle` (in the bucket, marked "available for
> reassignment"), eliminating the edge.

`play_circle` and `stop_circle` are in the icon bucket so they resolve; `hourglass`,
`print_add`, `save`, `tab_close` are **unverified** — if any does NOT resolve in the bundled
font, STOP and ASK the owner; do not substitute (icon law).

### Shared-ligature re-registration (allow-list in `DinghyIconsTest`, palette/settings precedent)

| Action | New token | Ligature | Shares with |
|---|---|---|---|
| temp_cooldown | `TempCooldown` | `mode_heat_off` | `HideTemps` (Temp vs Console never co-render) |

> **Codex MAJOR F4 — fixed.** `temp_presets` was `thermostat`, but the Temperature screen
> already renders `LauncherTemperature` (thermostat) in its Focus header
> (`TemperatureScreen.kt:519`) and as the sensor-icon fallback (`:1042`) while the Adjust
> footer can show `temp_presets` (`:689`) — a live same-screen collision.
> **OWNER-PENDING:** Matthew will pick a fresh glyph for `temp_presets` from the Material
> Symbols site and add it to the icon bucket. This is the **one open gate** before
> implementation — token `TempPresets`, ligature TBD by owner.

`DinghyIconsTest` current duplicate allow-list is `output_circle`, `palette`, `settings`,
`print` (`DinghyIconsTest.kt:83`); add `mode_heat_off` (and the owner's `temp_presets`
ligature only if it duplicates an existing one). Add every new `val` to `DinghyIcons.all` and
the `tools/subset-symbols` source of truth **before** migrating call sites that reference the
new tokens (ordering hazard, Codex F8).

## Verification gates (resolve during implementation)

1. **BedMesh `check_circle` co-occurrence.** `mesh_apply` (main bar) and `mesh_save_confirm`
   (SaveName field-takeover bar) both use `check_circle`. Confirm these two states are
   mutually exclusive on the BedMesh screen (the SaveName takeover replaces the main bar). If
   they can render simultaneously, `mesh_apply` needs a different owner-chosen glyph (do not
   pick one — ASK).
2. **`temp_presets` glyph (OPEN).** Owner is supplying a fresh ligature (Codex F4). Block the
   ligature-gate / call-site migration for the Temperature presets button until it lands.
3. **Other reused-token same-screen scans (Codex):** verify no further collisions for `delete`
   (mesh_remove vs printers_delete are different screens — OK), `play_circle` (calibration_run
   on Screws/Tilt vs calibration_start on Probe — different screens — OK), `hourglass`
   (running vs starting — different screens — OK). Spot-check during migration.

## Testing / enforcement

- **Primary guarantee:** deleting the lambda overload = callers must use the list API. Note
  (Codex F7): this alone does NOT prevent a blank label or drawable icon — the `FootAction`
  `init` `require`s close that gap. Together they make a rule-violating foot button
  unconstructable.
- **Unit test** on `FootButtonBar`: a 2-action list renders both labels (assert label text
  present); a 3-action list renders none (assert labels absent, `contentDescription`
  present). One bar at the boundary each side of the threshold.
- **`FootAction` guard tests:** blank label throws; drawable-backed icon throws.
- **Ligature gate:** `tools/verify_ligatures.py` must exit 0 with the 5 new ligatures.
- **`DinghyIconsTest`** uniqueness/allow-list passes with the two shared-ligature tokens.
- **`FontConformanceTest`** and the preview suite stay green; refresh the
  `DesignKitComponentPreviews` foot-bar preview to show both modes (a ≤2 icon+label bar and
  a ≥3 icon-only bar).
- Existing screen behavior unchanged except the conformed icon/label rendering.

## Out of scope / non-goals

- No change to dialog/modal button rows, steppers, pickers, or the e-stop.
- No per-button label/icon overrides — purely count-based.
- No change to intent colors, sizing, `controlHeight`, or the 8dp frame ownership.
