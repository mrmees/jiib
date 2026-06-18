# Preheat⇄Cooldown button + switch-style ToggleRow — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the idle home foot button flip Preheat→Cooldown when any heater is on (firing the existing `TURN_OFF_HEATERS` command), and restyle the canonical `ToggleRow` from a text pill to a switch widget, migrating Macros manage-mode onto it.

**Architecture:** Two independent changes. (A) A state-driven foot button: `PrintStatusScreen` computes `anyHeaterOn` from observed `PrinterState.heaters` and threads it + an `onCooldown` lambda through `PrintStatusContent` → `HomeField`, which swaps the idle FootAction. (B) A component restyle: the shared `ToggleRow` composable replaces its trailing "On/Off" text pill with a tokenized sliding switch; all six canonical consumers inherit it; Macros manage-mode migrates from `ListRow`+icon to `ToggleRow`.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit host tests, role tokens (`LocalTokens`/`ThemeTokens`), DinghyIcons registry. Build is Windows-side via `E:\Android\gw.bat` (see Build Notes at end).

**Spec:** `docs/superpowers/specs/2026-06-17-preheat-cooldown-toggle-switch-design.md`

---

## File Structure

| File | Responsibility | Change |
|------|----------------|--------|
| `app/.../designsystem/components/ToggleRow.kt` | The canonical labeled toggle row | Replace text pill with a switch; add private `ToggleSwitch`; drop dead `toggleStateLabelRes` |
| `app/.../designsystem/components/ToggleRowTest.kt` | Pure-helper contract tests | Drop the 3 pill-text tests; keep the 2 `toggleWantsAccent` tests |
| `app/.../designsystem/icons/DinghyIcons.kt` | Icon-token registry | Add `FootCooldown` (mode_heat_off) + register in `all` |
| `app/.../res/values/strings.xml` | Strings | Add `home_foot_cooldown` |
| `app/.../ui/printstatus/HomeDigest.kt` | Home digest + pure helpers | Add `anyHeaterOn(heaters)` pure helper |
| `app/.../ui/printstatus/HomeDigestTest.kt` (or existing) | Pure-helper tests | Add `anyHeaterOn` tests |
| `app/.../ui/printstatus/PrintStatusField.kt` | The idle/printing/paused/complete Field + foot bar | Add `anyHeaterOn`/`onCooldown` params; swap Preheat→Cooldown |
| `app/.../ui/printstatus/PrintStatusScreen.kt` | Live container + preview overload + shared `PrintStatusContent` | Compute `anyHeaterOn`, wire `onCooldown` (dispatch `CommandRegistry.cooldown`), thread through |
| `app/.../ui/macros/BookmarkedMacrosScreen.kt` | Macros screens | Migrate `MacroManageField` rows `ListRow`→`ToggleRow` |
| `docs/ui_design/COMPONENTS.md` | UI design law | Update ToggleRow section (pill→switch) |

**Scope guard:** Do NOT touch `SecureToggleRow` (PrintersScreen.kt, PrinterConnectionEditor.kt). It is a separate, non-canonical class explicitly OUT of scope (owner "macros only").

---

## Task 1: Switch-style ToggleRow (component restyle)

The canonical `ToggleRow` currently draws a right-hand "On/Off" TEXT pill. Replace it with a tokenized sliding switch. The pure helper `toggleStateLabelRes` (pill text) becomes dead and is removed; `toggleWantsAccent` stays (it drives the accent treatment of both the row border and the switch).

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/components/ToggleRow.kt`
- Test: `app/src/test/java/works/mees/dinghy/designsystem/components/ToggleRowTest.kt`

- [ ] **Step 1: Update the test to the new switch contract (TDD — write first, expect compile failure)**

Replace the entire body of `ToggleRowTest.kt` with the version below. The 3 `toggleStateLabelRes` tests are removed (the switch has no text); the `toggleWantsAccent` tests stay as the contract anchor (it now drives the row border + switch accent, not pill text). `R` and `assertNotEquals` imports drop out (now unused).

```kotlin
package works.mees.dinghy.designsystem.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host tests for the pure helper backing [ToggleRow] (control baseline audit, Phase 5; switch
 * restyle 2026-06-17).
 *
 * Post-restyle the trailing affordance is a SWITCH (sliding knob), not a text pill — the
 * `toggleStateLabelRes` pill-text helper is gone. The surviving pure contract is "wants accent":
 * the checked state drives BOTH the row border (accentLine vs outline) AND the switch colors
 * (accent knob/accentSoft track vs text3 knob/outline track).
 */
class ToggleRowTest {

    @Test
    fun wantsAccent_whenChecked() {
        assertTrue(toggleWantsAccent(true))
    }

    @Test
    fun doesNotWantAccent_whenUnchecked() {
        assertFalse(toggleWantsAccent(false))
    }
}
```

- [ ] **Step 2: Run the test — expect COMPILE FAILURE (it still compiles, but the source change in Step 3 is what this guards)**

Run:
```bash
timeout 600 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.designsystem.components.ToggleRowTest --no-daemon" 2>&1 | tr -d '\r'; echo "EXIT=${PIPESTATUS[0]}"
```
Expected: PASS (the test only references `toggleWantsAccent`, which still exists). This step confirms the new test is green BEFORE the source edit removes `toggleStateLabelRes`.

- [ ] **Step 3: Restyle the ToggleRow source**

In `ToggleRow.kt`:

(a) **Remove** the `toggleStateLabelRes` helper (lines ~35–41 — the whole KDoc + `internal fun toggleStateLabelRes(...)`).

(b) **Update imports.** Remove `import androidx.compose.ui.res.stringResource`. Add:
```kotlin
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
```
(`getValue` is REQUIRED for the `val knobX by animateDpAsState(...)` property delegate — without it the `by` won't compile. Keep all existing imports — `Text` is still used for the label/subLabel, `RoundedCornerShape`/`border`/`padding`/`clip` all stay.)

(c) **Replace the trailing pill** — the `val pillShape = ...` + `Box(... ) { Text(...) }` block at the end of the `Row` (lines ~155–170) — with a single call:
```kotlin
        ToggleSwitch(checked = checked, uDp = uDp)
```

(d) **Add the private switch composable** at the end of the file (after the `ToggleRow` function):
```kotlin
// ─────────────────────────────────────────────────────────────────────────────
// ToggleSwitch — the trailing on/off affordance (switch restyle 2026-06-17)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The canonical on/off SWITCH: a tokenized rounded-capsule track + a sliding knob, replacing the
 * former "On/Off" text pill. State is carried by knob position AND color (no text):
 *  - ON  → `accentSoft` track fill, `accentLine` 2dp border, solid `accent` knob at the trailing edge.
 *  - OFF → transparent track, `outline` 2dp border, faint `text3` knob at the leading edge.
 * The knob slides via a single one-shot `animateDpAsState` (cheap — respects the no-looping-animation
 * rule + the Adreno-320 floor). Sized U-relative so it fits inside a 1U row. All colors are role
 * tokens (THEME-01 — never raw). a11y is owned by the parent row's `toggleable(role = Role.Switch)`.
 */
@Composable
private fun ToggleSwitch(checked: Boolean, uDp: Dp) {
    val t = LocalTokens.current
    val trackH = uDp * 0.5f
    val trackW = uDp * 0.95f
    val pad = 3.dp
    val knob = trackH - pad * 2
    val knobX by animateDpAsState(
        targetValue = if (checked) trackW - knob - pad else pad,
        label = "toggleKnobX",
    )
    val trackShape = RoundedCornerShape(percent = 50)
    Box(
        Modifier
            .width(trackW)
            .height(trackH)
            .clip(trackShape)
            .background(if (checked) t.accentSoft else Color.Transparent)
            .border(BorderStroke(2.dp, if (toggleWantsAccent(checked)) t.accentLine else t.outline), trackShape),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .offset(x = knobX)
                .size(knob)
                .clip(CircleShape)
                .background(if (toggleWantsAccent(checked)) t.accent else t.text3),
        )
    }
}
```

(e) **Update the `ToggleRow` KDoc anatomy** (the `## Anatomy` block, lines ~70–78): replace the "Right pill `Box` … TEXT pill — NEVER a glyph" bullet with:
```
 *  - Right: [ToggleSwitch] — a tokenized rounded-capsule track + sliding knob (switch restyle
 *    2026-06-17). ON = accentSoft track / accentLine border / solid accent knob (trailing); OFF =
 *    transparent track / outline border / faint text3 knob (leading). State is position + color,
 *    no text. The row border still flips accentLine↔outline with [toggleWantsAccent] (kept — extra
 *    glanceability on a printer screen).
```

- [ ] **Step 4: Run the test — expect PASS**

Run:
```bash
timeout 600 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.designsystem.components.ToggleRowTest --no-daemon" 2>&1 | tr -d '\r'; echo "EXIT=${PIPESTATUS[0]}"
```
Expected: `BUILD SUCCESSFUL`, EXIT=0. (If it fails to compile, the dead `toggleStateLabelRes` is still referenced somewhere — re-grep `toggleStateLabelRes` across `app/src`.)

- [ ] **Step 5: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" add app/src/main/java/works/mees/dinghy/designsystem/components/ToggleRow.kt app/src/test/java/works/mees/dinghy/designsystem/components/ToggleRowTest.kt
"/mnt/c/Program Files/Git/cmd/git.exe" commit -m "feat(ui): ToggleRow trailing affordance pill→switch

Canonical labeled toggle row now renders a tokenized sliding switch
(accentSoft track + accent knob ON, outline track + text3 knob OFF)
instead of an On/Off text pill. One-shot knob slide. Dead pill-text
helper removed; toggleWantsAccent kept. All six canonical consumers
(Extrude/Move/About/PrinterConnectionEditor/AppSettings/Macros) inherit.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 2: FootCooldown icon + Cooldown string

The `mode_heat_off` ligature is already in the bundled font (used by `HideTemps` + `TempCooldown`) and is already allow-listed by name in `DinghyIconsTest`, so a third token sharing it needs NO test change — only a unique `alternate` + an `all` entry.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the `FootCooldown` token**

In `DinghyIcons.kt`, in the "Morphing-root idle foot-bar glyphs" section, immediately after the `FootResume`/`FootCancel`/`FootDismiss` block (after line 115), add:
```kotlin
    // Idle foot-bar Cooldown (owner-chosen 2026-06-17): shown in place of Preheat when any heater
    // is on; fires TURN_OFF_HEATERS. `mode_heat_off` is shared with HideTemps/TempCooldown (none
    // co-render with the home foot bar) → already allow-listed in DinghyIconsTest.
    val FootCooldown = DinghyIcon(IconRef.Ligature("mode_heat_off"), alternate = "home_foot_cooldown")
```

- [ ] **Step 2: Register it in `all`**

In the `all` list, change the foot-bar line:
```kotlin
        FootPreheat, FootSystem, FootResume, FootCancel, FootDismiss,
```
to:
```kotlin
        FootPreheat, FootCooldown, FootSystem, FootResume, FootCancel, FootDismiss,
```

- [ ] **Step 3: Add the string**

In `app/src/main/res/values/strings.xml`, immediately after line 143 (`<string name="home_foot_preheat">Preheat</string>`), add:
```xml
    <string name="home_foot_cooldown">Cooldown</string>
```

- [ ] **Step 4: Run the icon-registry + ligature gates — expect PASS**

```bash
timeout 600 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.designsystem.icons.DinghyIconsTest --no-daemon" 2>&1 | tr -d '\r'; echo "EXIT=${PIPESTATUS[0]}"
python3 tools/verify_ligatures.py 2>&1 | tail -5; echo "EXIT=${PIPESTATUS[0]}"
```
Expected: DinghyIconsTest `BUILD SUCCESSFUL`; `verify_ligatures.py` exits 0 (it scrapes the whole registry — `mode_heat_off` already resolves, so the new token is covered). If `verify_ligatures.py` lives elsewhere or needs a different interpreter, find it: `ls tools/verify_ligatures.py`.

- [ ] **Step 5: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt app/src/main/res/values/strings.xml
"/mnt/c/Program Files/Git/cmd/git.exe" commit -m "feat(icons): add FootCooldown (mode_heat_off) + home_foot_cooldown string

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 3: Preheat⇄Cooldown wiring on the idle foot bar

Add a host-testable `anyHeaterOn` predicate, then thread it + an `onCooldown` dispatch lambda from the live container through `PrintStatusContent` to `HomeField`, which swaps the idle FootAction.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/HomeDigest.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/printstatus/HomeDigestTest.kt` (create if absent)
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt`

- [ ] **Step 1: Write the failing test for the `anyHeaterOn` predicate**

First confirm where existing `activeHeaterKeys` tests live:
```bash
grep -rln "activeHeaterKeys\|orderedHeaterKeys" app/src/test/java
```
Add the following test to that existing file (likely `HomeDigestTest.kt`); if no such file exists, create `app/src/test/java/works/mees/dinghy/ui/printstatus/HomeDigestTest.kt` with the package + this class:

```kotlin
package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.state.HeaterState

class AnyHeaterOnTest {
    @Test
    fun noHeaters_isOff() {
        assertFalse(anyHeaterOn(emptyMap()))
    }

    @Test
    fun allTargetsZero_isOff() {
        assertFalse(
            anyHeaterOn(
                mapOf(
                    "extruder" to HeaterState(temperature = 25.0, target = 0.0),
                    "heater_bed" to HeaterState(temperature = 24.0, target = 0.0),
                ),
            ),
        )
    }

    @Test
    fun anyTargetAboveZero_isOn() {
        assertTrue(
            anyHeaterOn(
                mapOf(
                    "extruder" to HeaterState(temperature = 25.0, target = 0.0),
                    "heater_bed" to HeaterState(temperature = 24.0, target = 60.0),
                ),
            ),
        )
    }
}
```

- [ ] **Step 2: Run it — expect FAIL (unresolved reference `anyHeaterOn`)**

```bash
timeout 600 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.printstatus.AnyHeaterOnTest --no-daemon" 2>&1 | tr -d '\r'; echo "EXIT=${PIPESTATUS[0]}"
```
Expected: compile failure — `Unresolved reference: anyHeaterOn`.

- [ ] **Step 3: Add the `anyHeaterOn` pure helper**

In `HomeDigest.kt`, immediately after `activeHeaterKeys` (after line 64), add:
```kotlin
/** True when ANY heater is actively heating (target > 0) — drives the idle foot bar's
 *  Preheat→Cooldown swap. Pure (host-testable); reuses the canonical [activeHeaterKeys] "on" rule. */
internal fun anyHeaterOn(heaters: Map<String, HeaterState>): Boolean =
    activeHeaterKeys(heaters).isNotEmpty()
```

- [ ] **Step 4: Run it — expect PASS**

```bash
timeout 600 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.printstatus.AnyHeaterOnTest --no-daemon" 2>&1 | tr -d '\r'; echo "EXIT=${PIPESTATUS[0]}"
```
Expected: `BUILD SUCCESSFUL`, EXIT=0.

- [ ] **Step 5: Add `anyHeaterOn` + `onCooldown` params to `HomeField` and swap the idle FootAction**

In `PrintStatusField.kt`:

(a) Add two params to `HomeField` (after `onPreheat: () -> Unit,`):
```kotlin
    onPreheat: () -> Unit,
    anyHeaterOn: Boolean = false,
    onCooldown: () -> Unit = {},
```

(b) Replace the idle `else` branch (lines ~143–148) of the `buildList` with:
```kotlin
                } else {
                    // Preheat ⇄ Cooldown: when any heater is on, offer Cooldown (TURN_OFF_HEATERS)
                    // instead of Preheat. Flips back automatically once all targets reach 0.
                    if (anyHeaterOn) {
                        add(FootAction(stringResource(R.string.home_foot_cooldown),
                            DinghyIcons.FootCooldown, onCooldown, Intent.Accent)) // R5: removes the heat hazard → neutral
                    } else {
                        add(FootAction(stringResource(R.string.home_foot_preheat),
                            DinghyIcons.FootPreheat, onPreheat, Intent.Warn)) // R5: heats nozzle/bed — hazard-in-process
                    }
                    add(FootAction(stringResource(R.string.home_foot_system),
                        DinghyIcons.FootSystem, { onNavigate(NavDest.System) }, Intent.Accent)) // R5: plain navigation = accent
                }
```

(c) Update the `HomeField` KDoc foot-bar bullet (lines ~124–125): change the `Idle` line to note "Idle → Preheat (warn) **or Cooldown (accent, when any heater is on)** + System (accent nav)."

- [ ] **Step 6: Thread the params through `PrintStatusContent` and both `PrintStatusScreen` overloads**

In `PrintStatusScreen.kt`:

(a) **`PrintStatusContent` signature** — add after `onPreheat: () -> Unit,`:
```kotlin
    onPreheat: () -> Unit,
    anyHeaterOn: Boolean,
    onCooldown: () -> Unit,
```

(b) **`PrintStatusContent` body** — in the `HomeField(...)` call (the `field = { ... }` slot), add after `onPreheat = onPreheat,`:
```kotlin
                onPreheat = onPreheat,
                anyHeaterOn = anyHeaterOn,
                onCooldown = onCooldown,
```

(c) **Live container overload** — in the `PrintStatusContent(...)` call (after `onPreheat = ::runPreheat,`), add:
```kotlin
            onPreheat = ::runPreheat,
            anyHeaterOn = anyHeaterOn(state.heaters),
            onCooldown = { dispatcher?.dispatch(CommandRegistry.cooldown, Unit); Unit },
```
(`anyHeaterOn` and `CommandRegistry` are already in scope — same package for the former, already imported for the latter.)

(d) **Preview overload** — in its `PrintStatusContent(...)` call (after `onPreheat = {},`), add:
```kotlin
            onPreheat = {},
            anyHeaterOn = anyHeaterOn(state.heaters),
            onCooldown = {},
```

- [ ] **Step 7: Build the debug variant — expect SUCCESS**

```bash
timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'; echo "EXIT=${PIPESTATUS[0]}"
```
Expected: `BUILD SUCCESSFUL`, EXIT=0.

- [ ] **Step 8: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" add app/src/main/java/works/mees/dinghy/ui/printstatus/HomeDigest.kt app/src/test/java/works/mees/dinghy/ui/printstatus/ app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
"/mnt/c/Program Files/Git/cmd/git.exe" commit -m "feat(printstatus): idle foot bar flips Preheat→Cooldown when any heater is on

anyHeaterOn(heaters) pure helper (target>0) gates the swap; Cooldown
dispatches the existing TURN_OFF_HEATERS spec (CommandRegistry.cooldown),
accent intent. Flips back to Preheat once all targets reach 0.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 4: Migrate Macros manage-mode to ToggleRow

`MacroManageField` currently renders each macro as a `ListRow` with a trailing CheckCircle/UnbookmarkedMacro icon. Replace with the (now switch-styled) `ToggleRow`. The launcher-list `ListRow` (line ~534) is a different surface — leave it.

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt`

- [ ] **Step 1: Add the ToggleRow import**

After `import works.mees.dinghy.designsystem.components.ListRow` (line 58), add:
```kotlin
import works.mees.dinghy.designsystem.components.ToggleRow
```

- [ ] **Step 2: Replace the manage-mode row**

In `MacroManageField`, replace the `items(state.visibleMacros, ...) { macro -> ... }` body (lines ~662–690 — the whole `ListRow(...) { Text(...) }` block) with:
```kotlin
            items(state.visibleMacros, key = { it.name }) { macro ->
                ToggleRow(
                    label = macro.name,
                    checked = macro.isBookmarked,
                    onToggle = { onToggleBookmark(macro.name) },
                    uDp = uDp,
                    contentDescription = stringResource(
                        if (macro.isBookmarked) R.string.cd_macros_bookmarked
                        else R.string.cd_macros_unbookmarked,
                    ),
                )
            }
```
(`onToggle` hands back the new Boolean, which we ignore — `onToggleBookmark` just flips the macro's bookmark state by name. The `DinghyIconView` + CheckCircle/UnbookmarkedMacro trailing content is gone — the switch now shows state.)

Then **remove the now-unused imports** `import works.mees.dinghy.designsystem.icons.DinghyIconView` and `import works.mees.dinghy.theme.fsSp` (Codex review confirmed both were used ONLY by this manage row). If a later build still references either elsewhere, keep that one — the build/`assembleDebug` in Step 3 is the authority.

- [ ] **Step 3: Build — expect SUCCESS**

```bash
timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'; echo "EXIT=${PIPESTATUS[0]}"
```
Expected: `BUILD SUCCESSFUL`. (`ListRow`/`DinghyIconView` imports stay — `ListRow` is still used at the launcher list ~line 534. `UnbookmarkedMacro` token becomes UI-unused but stays registered, harmless.)

- [ ] **Step 4: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" add app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt
"/mnt/c/Program Files/Git/cmd/git.exe" commit -m "feat(macros): manage-mode rows ListRow+icon → canonical ToggleRow (switch)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 5: Update COMPONENTS.md (design law)

**Files:**
- Modify: `docs/ui_design/COMPONENTS.md`

- [ ] **Step 1: Update the ToggleRow section**

Find the ToggleRow section (search `ToggleRow` — around lines 61–173). Update the anatomy description so the canonical trailing affordance is a **switch** (rounded-capsule track + sliding knob; ON = accentSoft track / accent knob, OFF = outline track / text3 knob; state = position+color, no text; one-shot knob slide) rather than an "On/Off TEXT pill." Add a line noting Macros manage-mode as a current consumer, and that `SecureToggleRow` (Printers/ConnectionEditor) remains the out-of-scope straggler. Keep the a11y note (`toggleable(role = Role.Switch)`).

- [ ] **Step 2: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" add docs/ui_design/COMPONENTS.md
"/mnt/c/Program Files/Git/cmd/git.exe" commit -m "docs(ui): ToggleRow canonical affordance is now a switch, not a text pill

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

## Task 6: Full suite + on-device verification

- [ ] **Step 1: Run the full debug unit-test suite — expect PASS**

```bash
timeout 1200 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'; echo "EXIT=${PIPESTATUS[0]}"
```
Expected: `BUILD SUCCESSFUL`, EXIT=0. If anything red, fix before proceeding (suspect a stale `toggleStateLabelRes` reference, a missing `all` registration, or a font-conformance/ligature gate).

- [ ] **Step 2: Install on BOTH test devices**

Confirm both devices are attached, then install the matching ABI slice on each (split-ABI debug build emits both; flox = armeabi-v7a, moto = arm64-v8a). The debug APK is auto-signed.
```bash
E:\Android\Sdk\platform-tools\adb.exe devices
# install the freshly-built debug APK on each device id (flox 0a64b42e, moto ZY22LBDRM9):
# adb -s <id> install -r <path-to-the-device's-ABI debug apk>
```
⚠ Per the stale-APK trap: verify the installed APK's build time is AFTER the Task 4 commit before handing to UAT — force a rebuild (`--rerun-tasks`) if Gradle reports `UP-TO-DATE`.

- [ ] **Step 3: Owner UAT (Matthew drives)**

Verify on flox + moto:
1. **Preheat⇄Cooldown:** Idle home, all heaters off → foot button reads **Preheat** (amber). Set a heater target (e.g. preheat, or set bed from Temperature) → the foot button becomes **Cooldown** (accent, snowflake-off glyph). Tap **Cooldown** → all targets drop to 0 and the button returns to **Preheat**.
2. **Switch look:** Extrude (runout-sensor + macro-pin rows), Macros manage-mode, and the About/Move/Settings toggles all render the SAME switch and toggle correctly. Bookmarking a macro in manage-mode flips its switch and it appears/disappears from the launcher list as before.
3. **Glyph renders** (not literal text "mode_heat_off") on both devices.

---

## Build Notes

- Builds run Windows-side; `./gradlew` does NOT work from WSL. Use the helper: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"`. Pipe through `tr -d '\r'`; the process exit code is authoritative.
- Gradle-via-interop can outlive WSL Bash-tool timeouts — every long invocation above is wrapped in `timeout` and uses `--no-daemon`. If a run wedges, `taskkill /F /T` the java.exe.
- Commits use Windows git.exe (Linux git fails `chmod` on the `/mnt/e` drvfs `.git/config.lock`). Do NOT push — owner gates pushes.

---

## Self-Review

- **Spec coverage:** Change A (Preheat⇄Cooldown) → Tasks 2+3. Change B (switch ToggleRow) → Task 1. Macros migration → Task 4. Docs → Task 5. Cooldown intent=accent → Task 3 Step 5. Switch-only/no-text → Task 1. Scope guard (SecureToggleRow untouched) → noted in File Structure + Task 5. Verify (flox+moto, ligature/font gates) → Tasks 2/6. ✅ All spec requirements mapped.
- **Placeholder scan:** No TBD/TODO; every code step shows full code. ✅
- **Type consistency:** `anyHeaterOn(heaters: Map<String, HeaterState>): Boolean` defined in Task 3 Step 3, used in Task 3 Step 6 (c)/(d). `ToggleSwitch(checked, uDp)` defined + called in Task 1. `FootCooldown` defined Task 2, used Task 3 Step 5. `home_foot_cooldown` defined Task 2, used Task 3. `CommandRegistry.cooldown` is the existing spec (verified at CommandRegistry.kt:527). `onToggle: (Boolean) -> Unit` matches ToggleRow's real signature; `onToggleBookmark(String)` adapter in Task 4 Step 2. ✅
