# Phase 5 — Toggle consolidation (TDD task plan)

**Date:** 2026-06-15  **Branch:** `control-baseline-audit`  **Flow:** writing-plans → subagent-driven (GSD OFF)
**Spec:** `.planning/notes/2026-06-14-control-baseline-audit-design.md` §4 (Toggle class), §5 (state/a11y)
**Master list:** `.planning/notes/2026-06-14-control-master-list.md` (Toggle table; §f#6 Printers OUT)
**Owner ruling 2026-06-15:** *"New ToggleRow class, kill the 2 rogues only."* — do NOT churn the
already-clean icon-toggles; do NOT touch the OUT toggles.

---

## Goal

Extract ONE canonical **`ToggleRow`** component class — the full-width **labeled toggle row** shape
(label [+ optional sub-label] on the left, an **"On/Off" pill** on the right) — from the existing,
owner-shipped `DevEnableRow` look (`AboutScreen.kt:204-258`). Migrate the **two in-scope rogues**
onto it:
1. **About `DevEnableRow`** (custom pill) → `ToggleRow`.
2. **Move save-dialog Material `Checkbox`** (`MoveScreen.kt:640-661`) → `ToggleRow`.

Net effect: zero new look invented (the On/Off pill already ships on About + Printers), the rogue MUI
`Checkbox` dies, and Settings/Printers gain a class to adopt when those OUT areas are reworked.

### Explicitly NOT in this phase (owner ruling + spec §2 / §f#6)
- The clean icon-toggle BUTTONS stay as-is: Console hide-filters (×3), Output On/Off pair, Macros
  show-hidden, Temp mode (modal), Temp trace-visibility. They are clean `OutlinedControl` toggles —
  not rogue, not migrated.
- **OUT:** Printers `SecureToggleRow` pill (§f#6), Settings `DenseToggleRow` Material `Switch` ×3
  (Settings menu is OUT, spec §2), Temp sensor-picker `ListRow` (list-row class — separate audit).

### Icon law
`ToggleRow` uses **"On"/"Off" TEXT** in the pill (the `common_on`/`common_off` resources) — **no
glyph**. No icon ASK is required for this phase.

---

## Task 1 (TDD) — the `ToggleRow` class + pure helpers

**New file:** `app/src/main/java/works/mees/dinghy/designsystem/components/ToggleRow.kt`

### 1a — RED: host test first
**New file:** `app/src/test/java/works/mees/dinghy/designsystem/components/ToggleRowTest.kt`

Test the PURE, host-testable style-resolution helpers (mirrors the `StepperRow` /
`SelectorRow` `internal fun` + `*Test` pattern — no Compose runtime):
- `toggleStateLabelRes(checked: Boolean): Int` → `R.string.common_on` when true, `R.string.common_off`
  when false. (Assert the two distinct resource ids.)
- `toggleWantsAccent(checked: Boolean): Boolean` → `checked` (drives both the row border accentLine
  and the pill text=`t.accent`/`t.text2` decisions). Assert true↔true, false↔false.
- (If a third decision is non-trivial, add a helper + assertion; otherwise these two suffice.)

Scaffold must COMPILE day-one (typed assertions, no refs to unbuilt symbols) — `[[dinghy-wave0-red-scaffold-compile]]`.

### 1b — GREEN: implement `ToggleRow`
Signature:
```kotlin
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    contentDescription: String? = null,   // a11y label; defaults to `label` when null
    enabled: Boolean = true,
)
```
Anatomy — extract VERBATIM from `DevEnableRow` (the owner-approved look), generalized:
- Outer `Row`: `fillMaxWidth().heightIn(min = uDp)` (1U floor — Phase-28 ruling), `clip(RoundedCornerShape(t.rCard))`,
  `border(BorderStroke(2.dp, if (checked) t.accentLine else t.outline), shape)`, `padding(horizontal = 16.dp)`,
  `verticalAlignment = CenterVertically`.
- **a11y — use `Modifier.toggleable`, NOT `clickable`** (the audit's a11y goal): apply
  `Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle)`
  plus `semantics { this.contentDescription = contentDescription ?: label }`. This gives TalkBack the
  correct switch role + checked state (the raw `DevEnableRow` `clickable` did not). `onValueChange`
  receives the NEW value, so call sites pass `onToggle` directly (it already takes `Boolean`).
- Left `Column(Modifier.weight(1f))`: `label` (Geist SemiBold `fsSp(17)`), optional `subLabel`
  (Geist `fsSp(15)`, `t.text3`) — only emit the sub-label `Text` when `subLabel != null`.
- Right pill `Box`: `clip(RoundedCornerShape(t.rPill))`, `border(BorderStroke(2.dp, if (checked) t.accentLine else t.outline), pillShape)`,
  `padding(horizontal = 16.dp, vertical = 8.dp)`; `Text(stringResource(toggleStateLabelRes(checked)),`
  `color = if (toggleWantsAccent(checked)) t.accent else t.text2, Geist Bold fsSp(17))`.
- **`enabled = false`:** dim the row to `alpha 0.38` + `semantics { disabled() }` (the StepperRow/WR-07
  convention) — `toggleable(enabled=false)` already installs no toggle action. (No in-scope call site
  passes `enabled=false`, but the param exists for parity with the OUT toggles' future adoption.)

KDoc: note it canonicalizes the `DevEnableRow`/`SecureToggleRow`/`DenseToggleRow` rogue family; the
Settings/Printers ones are OUT this pass but should adopt this class when those areas are reworked.

---

## Task 2 — migrate About `DevEnableRow` → `ToggleRow`

**File:** `app/src/main/java/works/mees/dinghy/ui/screen/AboutScreen.kt`

Keep the `DevEnableRow` private composable as a **thin About-specific preset** (it owns the
About-specific strings + the "OFF restores theme" sub-label semantics, D-08) but replace its BODY to
delegate to `ToggleRow`:
```kotlin
@Composable
private fun DevEnableRow(checked: Boolean, onToggle: (Boolean) -> Unit, uDp: Dp) {
    ToggleRow(
        label = stringResource(R.string.about_dev_widgets),
        checked = checked,
        onToggle = onToggle,
        uDp = uDp,
        subLabel = if (checked) stringResource(R.string.about_dev_widgets_on)
                   else stringResource(R.string.about_dev_widgets_off),
        contentDescription = stringResource(R.string.about_dev_widgets),
    )
}
```
Remove the now-unused imports in AboutScreen (`BorderStroke`, `RoundedCornerShape`, `clickable`,
`border`, `clip`, `Box`, etc.) **only if** no other AboutScreen composable still uses them — grep
before deleting; leave shared imports. Stage explicit paths.

Persistence path is UNCHANGED (`onToggle` still routes to `AppContainer.setDevCyclerEnabled` via the
writeScope intent helper — `[[dinghy-compose-write-scope-cancellation]]`). This task is render-only.

---

## Task 3 — migrate Move save-dialog `Checkbox` → `ToggleRow`

**File:** `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` (`MoveMode.SaveDialog`, ~640-661)

Replace the `Row { Checkbox(...) + Text(...) }` block with:
```kotlin
ToggleRow(
    label = stringResource(R.string.move_include_z, fmt1(vm.z)),   // see string note below
    checked = includeZ,
    onToggle = { includeZ = it },
    uDp = grid.uDp,                                                // grid is in scope (MoveScreen.kt:209)
    modifier = Modifier.fillMaxWidth(),
)
```
- **String:** the current label is a literal `"Include Z height (Z = ${fmt1(vm.z)})"`. Add
  `R.string.move_include_z` = `"Include Z height (Z = %1$s)"` (`%1$s` formatted with `fmt1(vm.z)`) so
  the label routes through resources (master-list §c i18n cleanup). If a format-arg string proves
  awkward, fall back to passing the composed literal as `label` — do NOT block on the resource.
- Remove the now-unused `Checkbox` / `CheckboxDefaults` imports from MoveScreen **iff** unused
  elsewhere (grep first).
- The bottom-dock `Spacer(weight(1f))` + Cancel/Save row are UNCHANGED.
- `includeZ` default (`true`) and the `SavedLocation(z = if (includeZ) vm.z else null)` wiring are
  UNCHANGED — render-only swap.
- **`includeZ` is INTENTIONALLY EPHEMERAL** (`remember(mode) { mutableStateOf(true) }`, reset each
  time the dialog opens). Do **NOT** add DataStore persistence for it — this is purely a UI swap
  (Codex review 2026-06-15 flagged the absence of persistence; confirmed intended).
- **Order matters:** apply `Modifier.toggleable(...)` BEFORE the `semantics { contentDescription }`
  block so the Switch role + checked/enabled state survive and the description merges (Codex Q1).

> ⚠ This changes the include-Z affordance from a small checkbox to a full-width 1U labeled On/Off
> row. That is an intentional visual change (kills the MUI rogue) — flagged for owner UAT.

---

## Verification (controller runs after the subagent)
- **Host tests:** `:app:testDebugUnitTest --tests *ToggleRowTest` GREEN (unquoted glob through cmd.exe).
- **Build:** `:app:assembleDebug` GREEN, Windows-side (`E:\Android\gw.bat`), `| tr -d '\r'`.
- **Diff review:** confirm (a) `ToggleRow` extracted cleanly, (b) About delegates, (c) Move `Checkbox`
  gone (`grep -n 'Checkbox' MoveScreen.kt` → none), (d) `toggleable`/`Role.Switch` a11y present,
  (e) no `git add -A` — explicit paths only.
- **UAT on BOTH** flox (`0a64b42e`) + moto (`ZY22LBDRM9`): About dev-widgets toggle + Move save-dialog
  include-Z both render as the On/Off pill row and toggle correctly. Push only after UAT ✓.

## Subagent rules
- Points at the 3 governing docs + this plan. TDD: write `ToggleRowTest` (RED) first, then `ToggleRow`.
- Stage **explicit paths**, never `git add -A`. Do **not** commit or push (controller does).
- **Never pick an icon** — ToggleRow is text-only ("On"/"Off"); if any glyph seems needed, STOP + ASK.
