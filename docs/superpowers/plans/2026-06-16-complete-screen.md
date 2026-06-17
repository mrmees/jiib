# Complete (print-finished) home screen — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When `printState == Complete`, render the active-print data block as a "Complete" home — `check_circle` header, `COMPLETE · 100%` (pinned), full accent ring, and a Dismiss(`clear_all`)/System foot bar where Dismiss clears the job via the existing `dismissPrint`. Plus a bundled fix: the home list-row icons go accent.

**Architecture:** All work is in `ui/printstatus/` (HomeFocus branch, HomeField foot branch, screen plumbing) + one new registered glyph + one string. The data-block body (`ActivePrintFocus`), the `dismissPrint` command (`SDCARD_RESET_FILE`), `CheckCircle` glyph, and the foot-bar branching infrastructure all already exist. No new Moonraker calls, no networking, no FocusFrame change.

**Tech Stack:** Kotlin, Jetpack Compose, existing `CommandRegistry.dismissPrint`, `DinghyIcons` registry, `FocusEdge.Progress`.

**Spec:** `docs/superpowers/specs/2026-06-16-complete-screen-design.md`

---

## File structure

| File | Responsibility | Change |
|------|----------------|--------|
| `designsystem/icons/DinghyIcons.kt` | Register `FootDismiss` (`clear_all`); `CheckCircle` already exists | Modify |
| `img/material-icon-bucket.json` | Curation entry for `clear_all` | Modify |
| `app/src/main/res/values/strings.xml` | `printstatus_foot_dismiss` = `Dismiss` | Modify |
| `ui/printstatus/PrintStatusFocus.kt` | `HomeFocus`: Complete branch — `check_circle` icon, pinned `· 100%` title, pinned full accent ring, data-block body | Modify |
| `ui/printstatus/PrintStatusField.kt` | `HomeField`: Complete foot branch (Dismiss + System); **bundled** nav-row + Spool-fallback icon tint → accent | Modify |
| `ui/printstatus/PrintStatusScreen.kt` | Thread `isComplete` + `onDismiss` through `PrintStatusContent` → `HomeField` (both overloads) | Modify |
| `preview/SampleFixtures.kt` | Populate the `Complete` fixture's job fields so the Complete preview is representative | Modify |

**Reference facts (verified against HEAD 2026-06-16):**
- `PrintState` = `Standby, Printing, Paused, Complete, Error, Cancelled` (`state/PrinterState.kt:359`).
- `CommandRegistry.dismissPrint: CommandSpec<Unit>` (`SDCARD_RESET_FILE`, gated on `virtual_sdcard`; `CommandRegistry.kt:656`). Dispatch `dispatcher?.dispatch(CommandRegistry.dismissPrint, Unit)`.
- `DinghyIcons.CheckCircle` already registered (`DinghyIcons.kt:44`, ligature `check_circle`). `FootSystem` (`bottom_panel_open`), `FootResume`/`FootCancel`/`FootPreheat` are the foot block (`:104-110`); `DinghyIcons.all` foot row at `:329`.
- `HomeFocus` derives `isPrinting`/`isPaused` from `state` itself (`PrintStatusFocus.kt:77-78`) — so it can derive `isComplete` internally; no new HomeFocus param needed. `showActivePrint = isPrinting && !klippyFault` at `:83`; title `:89`; edge `:92-96`; `FocusFrame(icon = DinghyIcons.PrintStatusStandby …)` `:100`; body `if (showActivePrint) ActivePrintFocus(...) else digest` `:108`.
- `HomeField` already takes `isPrinting/isPaused/onPause/onResume/onCancel` (defaults) — foot bar `if (isPrinting) {…} else {…idle…}` (`PrintStatusField.kt:125-167`); nav-row icon `tint = LocalTokens.current.text2` (`:104`); Spool row `tint = spoolColor ?: t.text2` (`:232`).
- `PrintStatusContent` (`PrintStatusScreen.kt:328`) threads to HomeFocus/HomeField; two call sites — container (`:224`) and stateless preview (`:293`). `Intent` enum has `Go`, `Accent`, `Warn`, `Danger`, `Neutral`.

---

## Task 1: Register the Dismiss glyph + string

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`
- Modify: `img/material-icon-bucket.json`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Register `FootDismiss`**

In `DinghyIcons.kt`, after `FootCancel` (line ~110):

```kotlin
    val FootDismiss = DinghyIcon(IconRef.Ligature("clear_all"), alternate = "printstatus_foot_dismiss")
```

Add `FootDismiss` to the `DinghyIcons.all` foot row (line ~329):

```kotlin
        FootPreheat, FootSystem, FootResume, FootCancel, FootDismiss,
```

(`CheckCircle` is already in `all` — do NOT add it again.)

- [ ] **Step 2: Curation entry for `clear_all`**

In `img/material-icon-bucket.json`, add an object to the array (mirror the `cancel` entry's shape):

```json
    {
      "family": "Material Symbols Outlined",
      "iconName": "clear_all",
      "label": "Clear all",
      "axes": { "FILL": "0", "wght": "400", "GRAD": "0", "opsz": "24" },
      "color": "#1f1f1f",
      "size": "24",
      "notes": "Complete-screen Dismiss foot button (owner 2026-06-16)",
      "createdAt": 1780707263707,
      "updatedAt": 1780707263707,
      "key": "Material Symbols Outlined::clear_all",
      "url": "https://fonts.google.com/icons?selected=Material+Symbols+Outlined%3Aclear_all%3AFILL%400%3Bwght%40400%3BGRAD%400%3Bopsz%4024&icon.size=24&icon.color=%231f1f1f"
    }
```

(Place it as a well-formed sibling object — add a comma after the previous closing `}` if needed. The unit suite does NOT read this file; it is curation only, but keep it valid JSON.)

- [ ] **Step 3: Confirm the ligature resolves in the bundled font**

`printstatus_foot_dismiss` (= "Dismiss") ALREADY exists in `strings.xml` — do NOT add it (Codex SS1).
Instead, prove `clear_all` renders. Add `"clear_all",` to the `NEEDED` set in `tools/verify_ligatures.py`
(near `delete_sweep`) and run:

```bash
python3 tools/verify_ligatures.py
```
Expected: `… missing: []` (clear_all resolves in `material_symbols_outlined.ttf`). A non-empty `missing`
means the glyph is NOT in the bundled font — STOP and ask the owner for a different glyph.

- [ ] **Step 4: Build + the icon drift test**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileReleaseKotlin :app:testReleaseUnitTest --tests works.mees.dinghy.designsystem.icons.DinghyIconsTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; `DinghyIconsTest` green (the new `clear_all` IconRef + `printstatus_foot_dismiss` alternate are unique). (If the build hangs past ~8 min, stop and report DONE_WITH_CONCERNS — the controller will verify.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt \
        img/material-icon-bucket.json tools/verify_ligatures.py
git commit -m "feat(icons): register clear_all Dismiss foot glyph (string already present)"
```

---

## Task 2: `HomeFocus` — the Complete branch

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt`

Visual composable — verified by compile + preview + on-device.

- [ ] **Step 1: Add the Complete derivations + branch the title/edge/icon/body**

Replace the block from `val isPaused = …` (line ~78) through the start of the `FocusFrame(` call's body branch. Specifically:

Change the derivations (after `val isPrinting = …`):

```kotlin
    val isPaused = state.printState == PrintState.Paused
    val isComplete = state.printState == PrintState.Complete
    // Active-print treatment for a LIVE print (Printing/Paused) that is NOT a Klippy fault.
    val klippyFault = state.klippyState == KlippyState.Shutdown || state.klippyState == KlippyState.Error
    val showActivePrint = isPrinting && !klippyFault
    // Complete reuses the SAME data block; the only deltas are the header icon, a pinned 100% title,
    // and a pinned full accent ring (a finished job's live progress may have reset).
    val showComplete = isComplete && !klippyFault
    val showDataBlock = showActivePrint || showComplete

    val stateLabel = stringResource(homeStateLabelRes(state.printState, state.klippyState))
    val nameStatePart =
        if (isMultiPrinter && !printerName.isNullOrBlank()) "$printerName · $stateLabel" else stateLabel
    val title = when {
        showComplete -> "$nameStatePart · 100%"
        showActivePrint -> "$nameStatePart · ${progressPercent(state.progress)}%"
        else -> nameStatePart
    }

    val edge = when {
        showComplete -> FocusEdge.Progress(1f, t.accent)
        showActivePrint -> FocusEdge.Progress(state.progress.toFloat(), color = if (isPaused) t.heat else t.accent)
        else -> FocusEdge.Neutral
    }
```

Change the `FocusFrame(` icon line (line ~100):

```kotlin
        icon = if (showComplete) DinghyIcons.CheckCircle else DinghyIcons.PrintStatusStandby,
```

(Leave `isPrinting = isPrinting` and `onEmergencyStop = onEmergencyStop` unchanged — `isPrinting` is false for Complete, so `FocusFrame` shows the static `check_circle`, no e-stop.)

Change the body branch (line ~108):

```kotlin
        if (showDataBlock) {
            ActivePrintFocus(state = state, printMetadata = printMetadata, httpBase = httpBase)
        } else {
```

(The `else { BoxWithConstraints … digest … }` block is unchanged.)

- [ ] **Step 2: Build to verify**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileReleaseKotlin --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt
git commit -m "feat(printstatus): Complete home — check_circle, pinned 100% title + ring, data block"
```

---

## Task 3: `HomeField` — Complete foot bar + bundled icon-accent fix

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt`

- [ ] **Step 1: Extend the `HomeField` signature** (defaults so it compiles before Task 4 wires real values)

Add to the `HomeField(` param list (after `onCancel`):

```kotlin
    isComplete: Boolean = false,
    onDismiss: () -> Unit = {},
```

- [ ] **Step 2: Add the Complete foot branch**

In the `FootButtonBar(uDp = uDp) { … }` body, change the top-level `if (isPrinting) { … } else { …idle… }` to insert a Complete branch between them:

```kotlin
            if (isPrinting) {
                // … existing Pause/Resume + Cancel block UNCHANGED …
            } else if (isComplete) {
                OutlinedControl(
                    label = stringResource(R.string.printstatus_foot_dismiss),
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    icon = DinghyIcons.FootDismiss,
                    intent = Intent.Go, // R5: the expected action — clears the finished job to standby
                )
                OutlinedControl(
                    label = stringResource(R.string.home_foot_system),
                    onClick = { onNavigate(NavDest.System) },
                    modifier = Modifier.weight(1f),
                    icon = DinghyIcons.FootSystem,
                    intent = Intent.Accent, // R5: plain navigation = accent
                )
            } else {
                // … existing Preheat + System idle block UNCHANGED …
            }
```

(Keep the existing `if (isPrinting)` and `else` blocks byte-for-byte; only insert the `else if (isComplete)` middle branch.)

- [ ] **Step 3: Bundled — home list-row icons → accent**

The nav-destination row icon (line ~104):

```kotlin
                            ListRowIcon(
                                icon = action.icon,
                                uDp = uDp,
                                tint = LocalTokens.current.accent,
                            )
```

The Spool row icon fallback (line ~232) — keep the filament-color data tint, flip only the fallback:

```kotlin
                tint = spoolColor ?: t.accent,
```

- [ ] **Step 4: Build to verify**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileReleaseKotlin --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
git commit -m "feat(printstatus): Complete foot bar (Dismiss+System); home list icons → accent"
```

---

## Task 4: Plumbing — thread `isComplete` + `onDismiss`

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt`

- [ ] **Step 1: Container overload — derive `isComplete` + build `onDismiss`**

After the `onCancel` lambda (line ~163). **`isComplete` MUST be Klippy-fault gated** to match
`HomeFocus.showComplete` (Codex SS2) — otherwise Complete + a Klippy fault would show the digest/fault
Focus but a Dismiss/System foot bar:

```kotlin
    val isComplete = state.printState == PrintState.Complete &&
        state.klippyState != KlippyState.Shutdown && state.klippyState != KlippyState.Error
    val onDismiss: () -> Unit = { dispatcher?.dispatch(CommandRegistry.dismissPrint, Unit); Unit }
```

(Ensure `KlippyState` is imported in `PrintStatusScreen.kt`.)

- [ ] **Step 2: Add the two params to `PrintStatusContent`**

In `private fun PrintStatusContent(` (line ~328), after `isPaused: Boolean,`:

```kotlin
    isComplete: Boolean,
    onDismiss: () -> Unit,
```

Pass them into `HomeField(` (line ~373), after `onCancel = onCancel,`:

```kotlin
                isComplete = isComplete,
                onDismiss = onDismiss,
```

(HomeFocus needs nothing new — it derives `isComplete` from `state`.)

- [ ] **Step 3: Update BOTH call sites**

Container call (line ~224), after `isPaused = isPaused,`:

```kotlin
            isComplete = isComplete,
            onDismiss = onDismiss,
```

Stateless preview call (line ~293), after `isPaused = …,` — same Klippy-fault gate as the container:

```kotlin
            isComplete = state.printState == PrintState.Complete &&
                state.klippyState != KlippyState.Shutdown && state.klippyState != KlippyState.Error,
            onDismiss = {},
```

- [ ] **Step 4: Build + full unit suite**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL, suite green.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
git commit -m "feat(printstatus): wire isComplete + onDismiss (dismissPrint) into the home"
```

---

## Task 5: Complete preview fixture + final gates

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt`

- [ ] **Step 1: Populate the Complete fixture's job fields**

In `forState`, widen the job-field gate so a finished job's data renders in the Complete preview. Change `val printing = …` and the gated fields to use a `hasJob` flag:

```kotlin
    fun forState(s: PrintState): PrinterState {
        val printing = s == PrintState.Printing || s == PrintState.Paused
        val hasJob = printing || s == PrintState.Complete   // Complete shows the finished job's block too
        return PrinterState(
            printState = s,
            printFilename = if (hasJob) "benchy.gcode" else "",
            klippyState = KlippyState.Ready,
            progress = if (printing) 0.42 else if (s == PrintState.Complete) 1.0 else 0.0,
            printDuration = if (hasJob) 2700.0 else 0.0,    // 45m
            totalDuration = if (hasJob) 3120.0 else 0.0,    // 52m
            currentLayer = if (hasJob) (if (s == PrintState.Complete) 220 else 5) else null,
            totalLayer = if (hasJob) 220 else null,
            filamentUsed = if (hasJob) 4200.0 else 0.0,    // 4.2m
            gcodePosition = if (hasJob) persistentListOf(0.0, 0.0, 1.2, 0.0) else null,
            heaters = if (hasJob) {
                persistentMapOf(
                    "extruder" to HeaterState(temperature = 229.6, target = 230.0),
                    "heater_bed" to HeaterState(temperature = 75.2, target = 75.0),
                )
            } else {
                persistentMapOf()
            },
        )
    }
```

(The Complete title/ring are PINNED in `HomeFocus`, not read from `state.progress`; the fixture's
`1.0` for Complete is only cosmetic for any consumer that reads progress directly.)

- [ ] **Step 2: Full unit suite + R8 release**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:assembleRelease --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; suite green (incl. `FontConformanceTest`, `DinghyIconsTest`); both ABI-split APKs emitted.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
git commit -m "test(printstatus): representative Complete fixture for the Complete-home preview"
```

---

## On-device UAT (after the gates)

Per `[[dinghy-test-devices]]`: build `assembleDebug` (flox armeabi-v7a, id `0a64b42e`) + sign the arm64 release for moto (`ZY22LBDRM9`); confirm APK mtime is post-edit (`[[dinghy-stale-apk-uat-gate]]`). The E3 (`192.168.1.121`) is mid-job — let a print finish (or it already completed) to land in `Complete`, and verify:
1. Complete shows the data block + `check_circle` header + `COMPLETE · 100%` + full accent ring, NO e-stop.
2. Foot bar = **Dismiss** (green, `clear_all`) + **System** (accent); no System row in the list.
3. Tapping **Dismiss** clears the job (printer → standby, home reverts to the digest).
4. The home list-row icons (Files/Outputs/Webcam/System, and the Spool row's no-color fallback) now render in accent.
5. Cancelled/Error/idle/printing unchanged.

---

## Codex review (2026-06-16) — EXECUTE WITH FIXES → all folded in

- **#1 [SHOW-STOPPER]** `printstatus_foot_dismiss` already exists in strings.xml — Task 1 no longer
  adds it (would duplicate); Task 1 now verifies the `clear_all` ligature via `tools/verify_ligatures.py`
  instead (confirmed `missing: []`).
- **#2 [SHOW-STOPPER]** The threaded `isComplete` is now Klippy-fault gated in BOTH call sites, matching
  `HomeFocus.showComplete` — Complete + a Klippy fault no longer mixes a fault Focus with a Dismiss foot bar.
- **[SHOULD-FIX]** `clear_all` added to `verify_ligatures.py` NEEDED and the check run (resolves).
- **[NIT]** Fixture progress comment reworded.
- Codex confirmed the rest aligned: `showDataBlock`, pinned 100%/ring, no e-stop on Complete, both
  `PrintStatusContent` call sites, `FootDismiss` uniqueness, `CheckCircle` not re-added, `t.accent`.

## Self-review notes (author)

- **Spec coverage:** Complete trigger + body reuse (T2 `showDataBlock`) ✓; check_circle header (T2) ✓; pinned `COMPLETE · 100%` + pinned full accent ring (T2) ✓; no e-stop (T2 — `isPrinting=false`) ✓; Dismiss(green, clear_all)=dismissPrint + System(accent) foot (T1 glyph/string + T3 branch + T4 onDismiss) ✓; no System list row on Complete (`isPrinting=false` → existing `buildIdleActions`) ✓; bundled list-icon accent (T3) ✓; Cancelled/Error untouched (only `Complete` is branched) ✓; preview (T5) ✓; gates incl. FontConformance/DinghyIcons (T5) ✓.
- **Type consistency:** `isComplete: Boolean` + `onDismiss: () -> Unit` defined once in HomeField (T3), PrintStatusContent (T4), threaded from both call sites (T4). `DinghyIcons.FootDismiss`/`CheckCircle`, `CommandRegistry.dismissPrint`, `Intent.Go/Accent` all match the codebase.
- **No new formatters/tests beyond the icon drift + existing suite** — Complete reuses the printing formatters verbatim (YAGNI).
- **Build env:** Windows-side via `E:\Android\gw.bat`; never `./gradlew` from WSL (`[[dinghy-display-build-env]]`).
