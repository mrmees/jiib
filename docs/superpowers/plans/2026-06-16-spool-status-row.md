# Spool Status Row Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the home/status idle-list "Spool" row into a data-rich row — a filament-color-tinted `ev_shadow` icon + the loaded filament's `name / material / vendor` text (marquee on overflow), falling back to an uncolored icon + "No Spool Loaded" when nothing is loaded.

**Architecture:** A pure `spoolRowText(filament)` helper (unit-tested) builds the text; a private `SpoolStatusRow` composable in `HomeField` renders the tinted icon + marquee text; `HomeField` gains the `activeSpoolCardState` it already has upstream, and special-cases the `NavDest.Spool` row in its existing row loop. Reuses `parseNormalizedHex` (SpoolScreen) and `basicMarquee` (FocusFrame).

**Tech Stack:** Kotlin, Jetpack Compose, JUnit (host unit tests), Gradle (Windows-side helper).

**Spec:** `docs/superpowers/specs/2026-06-16-spool-status-row-design.md`

---

## Build / test commands (this repo builds Windows-side)

`./gradlew` does NOT run from WSL. Pipe through `tr -d '\r'`; the process EXIT CODE is authoritative. NOTE: the `--tests 'fully.qualified.Name'` filter is unreliable here — use the `--tests '*ClassName'` wildcard form.

- **Unit test (one class):**
  ```bash
  timeout 600 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests '*SpoolRowTextTest' --no-daemon" 2>&1 | tr -d '\r'
  ```
- **Full suite + debug build:**
  ```bash
  timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'
  ```
- **Debug build only:**
  ```bash
  timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'
  ```

Hang past the timeout → `/mnt/c/Windows/System32/taskkill.exe /F /IM java.exe`, then re-run.

---

## File structure

- `ui/printstatus/PrintStatusField.kt` — add the pure `spoolRowText` helper + the private `SpoolStatusRow` composable; add `activeSpoolCardState` param to `HomeField`; special-case the Spool row.
- `ui/printstatus/PrintStatusScreen.kt` — pass `activeSpoolCardState` into the `HomeField(...)` call.
- `res/values/strings.xml` — one new string.
- `app/src/test/java/works/mees/dinghy/ui/printstatus/SpoolRowTextTest.kt` — new unit test.

Verified facts (don't re-derive):
- `SpoolmanFilament` (`spool/SpoolmanModels.kt:56`): `name: String?`, `material: String?`, `vendor: SpoolmanVendor?` (`.name: String?`), `colorSwatches: List<String>` (computed).
- `ActiveSpoolCardState` (`ui/spool/ActiveSpoolCard.kt:43`): sealed; `Loaded(val spool: SpoolmanSpool, …)` is the only variant carrying a spool.
- `parseNormalizedHex(hex: String): Color?` is `internal` in `ui/spool/SpoolScreen.kt:1023` → reachable from the printstatus package (same module) via import.
- `ListRowIcon(icon: DinghyIcon, uDp: Dp, tint: Color, contentDescription: String? = null)` renders at 0.6U.
- `DinghyIcons.SpoolFilament` = `IconRef.Ligature("ev_shadow")` (`DinghyIcons.kt:96`), already registered.
- `DinghyType.listLabel` is the canonical list-label style; `basicMarquee()` is `androidx.compose.foundation.basicMarquee` (the motion-law overflow exception used at `FocusFrame.kt:244`).
- `HomeField` currently renders every row generically (`PrintStatusField.kt:64-88`); `PrintStatusContent` already holds `activeSpoolCardState` and passes it to `HomeFocus`.

---

## Task 1: Pure `spoolRowText` helper

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt` (add a top-level `internal fun`)
- Test: `app/src/test/java/works/mees/dinghy/ui/printstatus/SpoolRowTextTest.kt` (create)

- [ ] **Step 1: Write the failing test.** Create `SpoolRowTextTest.kt`:

```kotlin
package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.spool.SpoolmanFilament
import works.mees.dinghy.spool.SpoolmanVendor

class SpoolRowTextTest {

    @Test
    fun allThreeFields_joinedWithSlash() {
        val f = SpoolmanFilament(name = "Galaxy Black", material = "PLA", vendor = SpoolmanVendor(name = "Hatchbox"))
        assertEquals("Galaxy Black / PLA / Hatchbox", spoolRowText(f))
    }

    @Test
    fun missingFields_dropOut() {
        assertEquals("PLA / Hatchbox", spoolRowText(SpoolmanFilament(material = "PLA", vendor = SpoolmanVendor(name = "Hatchbox"))))
        assertEquals("Galaxy Black", spoolRowText(SpoolmanFilament(name = "Galaxy Black")))
    }

    @Test
    fun blankFields_areTreatedAsAbsent() {
        val f = SpoolmanFilament(name = "  ", material = "PETG", vendor = SpoolmanVendor(name = ""))
        assertEquals("PETG", spoolRowText(f))
    }

    @Test
    fun noUsableFields_returnsNull() {
        assertNull(spoolRowText(null))
        assertNull(spoolRowText(SpoolmanFilament()))
        assertNull(spoolRowText(SpoolmanFilament(name = "   ")))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails.**

Run: `timeout 600 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests '*SpoolRowTextTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — `spoolRowText` unresolved.

- [ ] **Step 3: Implement the helper.** In `PrintStatusField.kt`, add a top-level function (below `HomeField`, above any other top-level decls) plus the `SpoolmanFilament` import:

```kotlin
/**
 * The home Spool row's identity text: the loaded filament's `name / material / vendor` (color /
 * type / mfg), non-null/non-blank fields joined with " / ". Null when no usable field exists
 * (the caller then shows "No Spool Loaded" or the generic "Spool" label). Pure — unit-tested.
 */
internal fun spoolRowText(filament: works.mees.dinghy.spool.SpoolmanFilament?): String? =
    listOfNotNull(filament?.name, filament?.material, filament?.vendor?.name)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
        .ifBlank { null }
```

- [ ] **Step 4: Run the test to verify it passes.**

Run: `timeout 600 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests '*SpoolRowTextTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt app/src/test/java/works/mees/dinghy/ui/printstatus/SpoolRowTextTest.kt
git commit -m "feat(printstatus): spoolRowText helper for the home Spool row"
```

---

## Task 2: `SpoolStatusRow` + wire it into `HomeField`

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt`

Compose work — verified by build-green + the existing PrintStatus previews. One commit.

- [ ] **Step 1: Add the string.** In `strings.xml`, after `<string name="printstatus_status_shutdown">SHUTDOWN</string>` (or anywhere in the `printstatus_*` block), add:

```xml
    <string name="printstatus_spool_none">No Spool Loaded</string>
```

- [ ] **Step 2: Add the `activeSpoolCardState` param to `HomeField`.** Change the `HomeField` signature (`PrintStatusField.kt:49-56`) to add the param (after `idleActions`):

```kotlin
@Composable
internal fun HomeField(
    idleActions: List<HomeAction>,
    activeSpoolCardState: works.mees.dinghy.ui.spool.ActiveSpoolCardState,
    failureText: String?,
    onNavigate: (NavDest) -> Unit,
    onPreheat: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
```

- [ ] **Step 3: Special-case the Spool row in the list loop.** Inside `HomeField`, replace the `items(...) { action -> ListRow(...) { ListRowLabel(...) } }` block (`PrintStatusField.kt:65-87`) with one that branches on the Spool destination. First, just above the `ListBlock(...)`, extract the loaded spool:

```kotlin
        val loadedSpool = (activeSpoolCardState as? works.mees.dinghy.ui.spool.ActiveSpoolCardState.Loaded)?.spool
        ListBlock(modifier = Modifier.weight(1f)) {
            items(
                items = idleActions.filterIsInstance<HomeAction.Destination>(),
                key = { it.dest::class.simpleName ?: it.dest.toString() },
            ) { action ->
                if (action.dest == NavDest.Spool) {
                    SpoolStatusRow(
                        spool = loadedSpool,
                        uDp = uDp,
                        onClick = { onNavigate(NavDest.Spool) },
                    )
                } else {
                    ListRow(
                        selected = false,
                        onClick = { onNavigate(action.dest) },
                        uDp = uDp,
                        leadingContent = {
                            ListRowIcon(
                                icon = action.icon,
                                uDp = uDp,
                                tint = LocalTokens.current.text2,
                            )
                        },
                    ) {
                        ListRowLabel(stringResource(action.labelRes))
                    }
                }
            }
        }
```

- [ ] **Step 4: Add the `SpoolStatusRow` private composable.** Add below `HomeField` in the same file:

```kotlin
/**
 * The home Spool row (data-rich, R-2026-06-16): leading [DinghyIcons.SpoolFilament] (`ev_shadow`)
 * tinted to the loaded filament's color (THEME-01 data carve-out — same derivation as the Spool
 * screen header), and the `name / material / vendor` identity text scrolling on overflow.
 * Uncolored icon + "No Spool Loaded" when nothing is loaded; "Spool" when a spool is loaded but
 * carries none of the identity fields. Taps through to [NavDest.Spool] in every state.
 */
@Composable
private fun SpoolStatusRow(
    spool: works.mees.dinghy.spool.SpoolmanSpool?,
    uDp: Dp,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    val spoolColor = spool?.filament?.colorSwatches?.firstNotNullOfOrNull {
        works.mees.dinghy.ui.spool.parseNormalizedHex(it)
    }
    val text = spoolRowText(spool?.filament)
    val label = when {
        text != null -> text
        spool != null -> stringResource(R.string.cd_launcher_spool) // loaded but no identity fields
        else -> stringResource(R.string.printstatus_spool_none)     // nothing loaded
    }
    ListRow(
        selected = false,
        onClick = onClick,
        uDp = uDp,
        leadingContent = {
            ListRowIcon(
                icon = DinghyIcons.SpoolFilament,
                uDp = uDp,
                tint = spoolColor ?: t.text2,
            )
        },
    ) {
        androidx.compose.material3.Text(
            text = label,
            color = t.text,
            style = works.mees.dinghy.theme.DinghyType.listLabel.toTextStyle(t),
            maxLines = 1,
            modifier = Modifier.basicMarquee(),
        )
    }
}
```

Add the imports `androidx.compose.foundation.basicMarquee` and `works.mees.dinghy.theme.compose.toTextStyle` (and `works.mees.dinghy.theme.DinghyType` if you prefer the short form over the fully-qualified reference used above). `DinghyIcons`, `ListRow`, `ListRowIcon`, `LocalTokens`, `R`, `stringResource`, `Modifier`, `Dp` are already imported.

- [ ] **Step 5: Pass `activeSpoolCardState` into the `HomeField` call.** In `PrintStatusScreen.kt`, find the single `HomeField(...)` call (inside `PrintStatusContent`'s `field = { … }` slot) and add the argument:

```kotlin
            HomeField(
                idleActions = idleActions,
                activeSpoolCardState = activeSpoolCardState,
                failureText = failureText,
                onNavigate = onNavigate,
                onPreheat = onPreheat,
                uDp = grid.uDp,
            )
```

`PrintStatusContent` already has `activeSpoolCardState` in scope (it's passed to `HomeFocus`), so no signature change is needed there, and the stateless preview overload already supplies it.

- [ ] **Step 6: Build + full suite.**

Run: `timeout 900 /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL; all tests pass. If it fails on an unresolved import (e.g. `parseNormalizedHex` not visible), confirm the import path `works.mees.dinghy.ui.spool.parseNormalizedHex` and that it's `internal` (same module) — if genuinely inaccessible, hoist it to a shared util and import that instead, then rebuild.

- [ ] **Step 7: Commit.**

```bash
git add app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt app/src/main/res/values/strings.xml
git commit -m "feat(printstatus): data-rich color-coded Spool status row"
```

---

## Task 3: On-device UAT

**Files:** none.

- [ ] **Step 1: Build + install on both devices** (flox `0a64b42e` armeabi-v7a, moto `ZY22LBDRM9` arm64-v8a). Build a fresh `:app:assembleDebug`, verify the APK mtime is after the Task 2 commit (stale-APK guard), then `adb -s <id> install -r app/build/outputs/apk/debug/app-<abi>-debug.apk` per device.

- [ ] **Step 2: Owner UAT** (Matthew):
  - Spoolman printer with a spool loaded → home Spool row shows the `ev_shadow` icon tinted to the filament color + `name / material / vendor`; long text scrolls.
  - Clear the active spool (or a printer with none loaded) → uncolored icon + "No Spool Loaded".
  - Tap the row in both states → opens the Spool screen.
  - Multi-color filament → icon tints to the first swatch; check it reads sensibly.
  - Rotate portrait/landscape; confirm the marquee + icon look right on the Nexus 7.

---

## Self-review notes

- **Spec coverage:** icon swap + tint (Task 2 SpoolStatusRow), text `name/material/vendor` + marquee (Tasks 1+2), no-spool "No Spool Loaded" + uncolored icon (Task 2 `label`/`tint`), loaded-but-no-fields → "Spool" (Task 2 `label`), capability gate + nav unchanged (the row is still built by `buildIdleActions` and taps `NavDest.Spool`), `spoolRowText` unit-tested (Task 1). All success criteria mapped.
- **Type consistency:** `spoolRowText(filament: SpoolmanFilament?): String?` used identically in Task 1 (def/test) and Task 2 (call). `SpoolStatusRow(spool: SpoolmanSpool?, uDp, onClick)` consistent. `activeSpoolCardState: ActiveSpoolCardState` added to `HomeField` and passed from `PrintStatusContent`.
- **No placeholders.** Every code step is complete.
