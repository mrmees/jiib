---
task: 260611-cj1
slug: lock-edge-to-edge-system-bars-to-active-theme
type: quick
autonomous: true
files_modified:
  - app/src/main/java/works/mees/dinghy/theme/compose/SystemBars.kt
  - app/src/main/java/works/mees/dinghy/MainActivity.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
  - app/src/test/java/works/mees/dinghy/theme/compose/SystemBarsTest.kt
  - .planning/phases/26.5-overnight-hardening-slate-audit-r-packages/OVERNIGHT-REPORT.md
  - docs/top-down-audit-roadmap.md

must_haves:
  truths:
    - "With the app's dark theme on a system-light API-34 device, the navigation bar backdrop is the theme bg token with light icons (no white bar)"
    - "Opening the App Drawer no longer restyles the system bars to the system theme (no flicker on popup open/close)"
    - "Switching themes (dark/light/custom, incl. the dev cycler) restyles both bars live, no relaunch"
    - "All bar colors come from ThemeTokens — no raw Color literals introduced"
  artifacts:
    - path: "app/src/main/java/works/mees/dinghy/theme/compose/SystemBars.kt"
      provides: "isDarkBackdrop pure gate + SyncSystemBarsToTheme + SyncDialogWindowToTheme composables"
    - path: "app/src/test/java/works/mees/dinghy/theme/compose/SystemBarsTest.kt"
      provides: "host unit test for the isDarkBackdrop luminance gate"
  key_links:
    - from: "app/src/main/java/works/mees/dinghy/MainActivity.kt"
      to: "SyncSystemBarsToTheme"
      via: "first child inside the DinghyTheme boundary"
      pattern: "SyncSystemBarsToTheme\\(\\)"
    - from: "app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt"
      to: "SyncDialogWindowToTheme"
      via: "inside the Dialog content lambda (DialogWindowProvider window)"
      pattern: "SyncDialogWindowToTheme\\(\\)"
---

<objective>
Lock edge-to-edge system bar styling (status-bar + navigation-bar icon contrast, and the
pre-API-35 navigation bar color) to the ACTIVE app theme tokens, not the system theme.

Found at Moto G Play 2024 (Android 14 / API 34) UAT of Phase 26.5 R1: the app's dark theme shows
a WHITE navigation button bar (device is in system light mode; the no-arg `enableEdgeToEdge()` at
MainActivity.kt:87 follows the SYSTEM uiMode), and the bars restyle when the App Drawer popup
opens (its Dialog window applies default system-derived bar styling). The app deliberately
neutralizes system theming in favor of its own semantic-token system (dark + light + custom via
ThemeTokens) — the bars must follow the ACTIVE tokens.

Purpose: dark theme on a system-light phone must not wear a white nav bar; bars must track live
theme changes (settings, custom seeds, dev cycler).
Output: theme-reactive system-bar sync (activity window + the one Dialog window), host unit test,
green build gate, and 26.5 UAT bookkeeping (Moto results + R7 deferral).
</objective>

<context>

Verified facts (read during planning — trust these, re-verify only if the code moved):

- `MainActivity.kt:87` is the ONLY `enableEdgeToEdge` call site in the app. It is the no-arg
  overload, first statement of `onCreate` before `super.onCreate` (26.5-04 R1 step 2). Keep it
  there as the baseline; the fix RE-CALLS it with explicit styles from composition.
- `ThemeTokens` (`theme/ThemeTokens.kt`) has NO dark/light flag — `ThemeResolver`'s `dark` field
  is private and not exposed on the token object, and MainActivity consumes
  `container.effectiveTokens` (override-aware flow), not the resolver. The correct active-theme
  signal is therefore DERIVED from the tokens: `bg.luminance() < 0.5f`. This is also the
  semantically right contrast signal for user custom themes (a "dark"-declared theme with a light
  bg needs dark icons). `Color.luminance()` is pure host-testable math — precedent:
  `theme/BrandTint.kt` + `app/src/test/java/works/mees/dinghy/theme/BrandTintTest.kt`.
- `DinghyTheme` (`theme/compose/DinghyTheme.kt`) is the single token boundary; it collects with
  `initialValue = TokensDark`, so the first frame syncs dark then re-syncs when the persisted
  theme lands — acceptable, it is the same fail-safe default the rest of the app paints.
- Do NOT put the sync inside `DinghyTheme` itself — it is shared with the debug-only
  `GalleryActivity` (app/src/debug) and `@Preview`/benchmark hosts. Call it from MainActivity's
  composition (the production host) only.
- Popup audit (exhaustive grep of app/src/main for `Dialog(`, `AlertDialog`, `ModalBottomSheet`,
  `DropdownMenu`, `Popup(` + `androidx.compose.ui.window` imports): exactly ONE real
  window-creating popup exists — `AppDrawer.kt:109` (`androidx.compose.ui.window.Dialog`).
  `ui/prompt/PromptDialog.kt` and BedMeshScreen's `SaveNameDialog`/`LoadSelectorDialog` are
  in-app overlays (no window import) — they cannot touch system bars; nothing to do there.
  Do NOT invent a generic popup framework for one consumer.
- The root token bg already paints edge-to-edge behind the bars (MainActivity root Box:
  `.background(LocalTokens.current.bg)` BEFORE `.safeDrawingPadding()`), so on API 35+ where
  scrim colors are ignored (bars transparent), the app bg shows through and only icon contrast
  matters — the same style selection is correct on both API generations.

@app/src/main/java/works/mees/dinghy/MainActivity.kt
@app/src/main/java/works/mees/dinghy/theme/compose/DinghyTheme.kt
@app/src/main/java/works/mees/dinghy/theme/compose/LocalTokens.kt
@app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
@app/src/main/java/works/mees/dinghy/theme/BrandTint.kt
@docs/ui_design/THEMING.md
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Theme-reactive system bar styling at the activity window + host unit test</name>
  <files>app/src/main/java/works/mees/dinghy/theme/compose/SystemBars.kt, app/src/main/java/works/mees/dinghy/MainActivity.kt, app/src/test/java/works/mees/dinghy/theme/compose/SystemBarsTest.kt</files>
  <read_first>
    - app/src/main/java/works/mees/dinghy/MainActivity.kt (onCreate enableEdgeToEdge baseline at line 87; the setContent/DinghyTheme block at 124-156)
    - app/src/main/java/works/mees/dinghy/theme/compose/DinghyTheme.kt (the boundary KDoc conventions to match)
    - app/src/main/java/works/mees/dinghy/theme/BrandTint.kt (luminance usage + KDoc style precedent)
    - app/src/test/java/works/mees/dinghy/theme/BrandTintTest.kt (host-test shape for pure theme math)
  </read_first>
  <behavior>
    - Test 1: isDarkBackdrop(TokensDark.bg) returns true
    - Test 2: isDarkBackdrop(TokensLight.bg) returns false
    - Test 3: isDarkBackdrop(Color.Black) is true; isDarkBackdrop(Color.White) is false
    - Test 4 (boundary documentation): a mid-grey near the 0.5 luminance threshold resolves
      deterministically (e.g. Color(0xFF767676) — assert whichever side it lands and pin it)
  </behavior>
  <action>
    Create `theme/compose/SystemBars.kt` with three things:

    1. A pure internal gate `isDarkBackdrop(bg: Color): Boolean = bg.luminance() < 0.5f`
       (import `androidx.compose.ui.graphics.luminance`). This is the SOLE dark/light decision
       authority for system bars — KDoc must say why it derives from the bg token rather than a
       declared theme polarity (ThemeTokens carries no flag; works for custom seeds + dev
       overrides; icon contrast is a function of the actual backdrop).

    2. `@Composable fun SyncSystemBarsToTheme()` — the official reactive edge-to-edge pattern:
       read `LocalTokens.current`, compute `dark = isDarkBackdrop(t.bg)` and
       `scrim = t.bg.toArgb()`; resolve the host activity from `LocalView.current.context` by
       unwrapping the `ContextWrapper` chain to a `ComponentActivity` (return silently if none —
       previews/gallery hosts); bail early on `view.isInEditMode`. In a
       `LaunchedEffect(dark, scrim)` (keyed so it re-fires ONLY on actual style change, not every
       recomposition) re-call `activity.enableEdgeToEdge(statusBarStyle = ..., navigationBarStyle = ...)`
       where dark themes use `SystemBarStyle.dark(scrim)` and light themes use
       `SystemBarStyle.light(scrim, scrim)` (scrim AND darkScrim both the bg token — the app never
       wants a system-picked contrast color). Imports: `androidx.activity.SystemBarStyle`,
       `androidx.activity.enableEdgeToEdge`, `androidx.compose.ui.graphics.toArgb`,
       `androidx.compose.ui.platform.LocalView`. KDoc: on API <35 the scrims paint the bar colors
       (fixes the white nav bar on the Moto / API 34); on API 35+ scrims are ignored (bars
       transparent, the root Box's token bg shows through) but `isAppearanceLight*` icon contrast
       still follows the style — correct on both generations. No raw Color literals anywhere —
       every color flows from ThemeTokens.

    3. (Stub for Task 2, or add it in Task 2 — executor's choice; the file is the shared home.)

    Wire MainActivity: inside `setContent { DinghyTheme(container.effectiveTokens) { ... } }`,
    add `SyncSystemBarsToTheme()` as the FIRST child of the DinghyTheme content lambda (sibling
    before the existing LaunchedEffect/Box). Do NOT remove the no-arg `enableEdgeToEdge()` at
    line 87 — it stays as the pre-first-frame baseline (the composable refines it). Add a brief
    comment at the call site tying it to this fix (260611-cj1: bars follow ACTIVE tokens, not
    system uiMode).

    Write the tests FIRST (`SystemBarsTest.kt`, plain JUnit host test mirroring BrandTintTest's
    shape) per the behavior block, then implement. `internal` visibility is visible to the unit
    test sourceset in the same module.
  </action>
  <verify>
    <automated>/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.theme.compose.SystemBarsTest --no-daemon" | tr -d '\r'</automated>
  </verify>
  <done>SystemBars.kt exists with the pure gate + activity sync composable; MainActivity composes SyncSystemBarsToTheme inside the theme boundary; the 4 host tests pass; no raw color literals.</done>
</task>

<task type="auto">
  <name>Task 2: App Drawer Dialog window follows the active theme</name>
  <files>app/src/main/java/works/mees/dinghy/theme/compose/SystemBars.kt, app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt</files>
  <read_first>
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt (the Dialog at line 109 — content lambda starts at the LazyVerticalGrid, line 113)
  </read_first>
  <action>
    The AppDrawer's `Dialog` creates its OWN window, which re-applies default (system-theme)
    system-bar styling while open — the reported "bars restyle when popups open" flicker. This is
    the ONLY Dialog-window surface in the app (audit in context) — fix it per-window, no
    framework.

    Add `@Composable fun SyncDialogWindowToTheme()` to SystemBars.kt: resolve the dialog window
    via `(LocalView.current.parent as? DialogWindowProvider)?.window`
    (`androidx.compose.ui.window.DialogWindowProvider`); return silently if null (non-dialog
    host / previews). Read `LocalTokens.current`, compute `dark = isDarkBackdrop(t.bg)`. In a
    `SideEffect` (the window outlives recompositions; idempotent flag writes are cheap): get
    `WindowCompat.getInsetsController(window, window.decorView)` and set
    `isAppearanceLightStatusBars = !dark` and `isAppearanceLightNavigationBars = !dark`; then,
    gated `Build.VERSION.SDK_INT < 35`, set the deprecated `window.statusBarColor` and
    `window.navigationBarColor` to `t.bg.toArgb()` under `@Suppress("DEPRECATION")` with a
    comment that API 35+ ignores window bar colors (forced transparent) so the suppression is
    the intentional pre-35 path. All colors from ThemeTokens.

    In AppDrawer.kt, call `SyncDialogWindowToTheme()` as the first statement inside the Dialog
    content lambda (before the LazyVerticalGrid). One-line comment referencing 260611-cj1.
  </action>
  <verify>
    <automated>grep -c "SyncDialogWindowToTheme()" app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt | grep -qx 1 && grep -q "DialogWindowProvider" app/src/main/java/works/mees/dinghy/theme/compose/SystemBars.kt && echo WIRED</automated>
  </verify>
  <done>Opening the App Drawer applies the active theme's bar icon contrast + pre-35 bar colors to the dialog window — no system-theme restyle. PromptDialog/BedMesh overlays untouched (they create no window).</done>
</task>

<task type="auto">
  <name>Task 3: UAT bookkeeping + full build gate</name>
  <files>.planning/phases/26.5-overnight-hardening-slate-audit-r-packages/OVERNIGHT-REPORT.md, docs/top-down-audit-roadmap.md</files>
  <read_first>
    - .planning/phases/26.5-overnight-hardening-slate-audit-r-packages/OVERNIGHT-REPORT.md lines 88-170 (the Morning UAT Checklist — flox group, "S25 Ultra group", CI wrap-up)
    - docs/top-down-audit-roadmap.md lines ~248-264 (the "Status 2026-06-11" block)
  </read_first>
  <action>
    OVERNIGHT-REPORT.md — record the modern-device UAT sitting (run 2026-06-11 on a
    **Moto G Play 2024 (Android 14 / API 34, arm64)**, standing in for the planned S25 Ultra):
    under the "### S25 Ultra group" heading, add a dated note that the device used was the Moto,
    then tick/annotate the checklist items with results:
    - R1 arm64 install: `[x]` PASS (arm64-v8a APK installs and runs on the Moto)
    - R1 edge-to-edge: `[x]` **PARTIAL** — content insets correct, BUT dark theme showed a WHITE
      navigation bar (no-arg enableEdgeToEdge followed the system light theme) and bars restyled
      when popups opened → fixed by quick task 260611-cj1
      (.planning/quick/260611-cj1-lock-edge-to-edge-system-bars-to-active-theme)
    - R1 predictive back: `[x]` PASS (preview animates; no overlay-order defect observed)
    - R1 POST_NOTIFICATIONS one-shot: `[x]` PASS
    - R2 display settings (keep-awake/battery rows on this device): `[x]` PASS
    - R7 toggle-ON failure + wss connect + cert-failure items: mark **DEFERRED to Phase 29
      polish per owner** (no TLS-fronted Moonraker exercised this sitting)
    Do not alter the flox group or CI wrap-up items beyond this.

    docs/top-down-audit-roadmap.md — in the "Status 2026-06-11" block, mirror the same:
    - In the `~~R7~~ code done` entry, replace "live-wss UAT pending" with "live-wss UAT
      DEFERRED to Phase 29 polish per owner (2026-06-11)"
    - In the `~~R1~~ code done` entry, replace "(S25/flox UAT pending)" with a parenthetical:
      Moto G Play 2024 / API 34 UAT 2026-06-11 — arm64 install/predictive-back/notifications
      PASS, edge-to-edge PARTIAL → fixed in quick 260611-cj1; flox regression still pending.

    Then run the full build gate (also proves Tasks 1-2 compile together with R8 + the whole
    test suite green).
  </action>
  <verify>
    <automated>/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon" | tr -d '\r'</automated>
  </verify>
  <done>Both docs reflect the Moto UAT results + R7 Phase-29 deferral; the combined unit-test + assembleDebug gate exits 0.</done>
</task>

</tasks>

<verification>
- `:app:testDebugUnitTest :app:assembleDebug` exits 0 (process exit code is authoritative; Gradle
  CR progress is piped through `tr -d '\r'`).
- `grep -rn "enableEdgeToEdge" app/src --include="*.kt"` shows exactly two call sites: the
  MainActivity onCreate baseline and the SyncSystemBarsToTheme re-call.
- No new raw `Color(0xFF...)` literals in SystemBars.kt / MainActivity.kt / AppDrawer.kt
  production paths (test fixtures exempt).
- Owner eyeball (NOT a gate for this plan — post-merge UAT): on the Moto (API 34, system light)
  the app's dark theme shows a dark nav bar with light icons; opening the App Drawer causes no
  bar restyle; theme cycling restyles bars live. Re-check on flox (API 30) for no regression.
  Per the stale-APK trap, force-rebuild and verify APK mtime before installing.
</verification>

<success_criteria>
- System bar icon contrast and pre-API-35 bar colors are driven by the ACTIVE ThemeTokens bg
  (luminance-derived), never the system uiMode — live-reactive to every theme change.
- The single Dialog-window popup (App Drawer) carries the same styling; in-app overlays untouched.
- Host unit test pins the isDarkBackdrop gate; full build gate green.
- 26.5 OVERNIGHT-REPORT UAT checklist and top-down-audit-roadmap status both record the Moto
  results and the R7 Phase-29 deferral.
</success_criteria>

<output>
On completion create `.planning/quick/260611-cj1-lock-edge-to-edge-system-bars-to-active-theme/SUMMARY.md`
(what changed, the two enableEdgeToEdge call sites, the luminance-gate decision, bookkeeping diffs).
</output>
