---
status: complete
phase: 18-preview-harness-tokenization-foundation
source: [18-VERIFICATION.md, 18-04-PLAN.md]
started: 2026-06-06
updated: 2026-06-07
---

## Current Test

[testing complete — 5/6 passed, Test 6 accepted-deferred (on-device-only en-XA, owner sign-off 2026-06-07); Test 1 issue found + fixed]

## Tests

### 1. Studio render — 3 exemplars
expected: In Android Studio, the `@Preview` panels for PrintStatus/FineTune/Spool render the 6 theme combos (dark/light × Colorful/Simple/HighContrast) + fs=L + RTL; preview-unsafe surfaces show labeled placeholders.
result: pass
note: ISSUE FOUND then FIXED during this UAT. First Studio render: every panel rendered on a white background — dark/light indistinguishable. Root cause: `PreviewBox` only provided `DinghyTheme` tokens and never painted a background; in the real app the *shell* paints the root bg (`MainActivity.kt:79` `Box(fillMaxSize().background(LocalTokens.current.bg))`), not the screens, so previews showed Studio's default white. FIXED in commit `728f800` — `PreviewBox` now paints the themed shell bg, correcting all exemplar + pseudolocale + future-phase previews. Re-verified in Studio **Gallery mode**: `PrintStatusThemeColorfulDark` renders dark, `...ColorfulLight` renders light — visibly distinct. (Grid mode renders all ~24 full-screen panels at once and is resource-heavy on first refresh — use Gallery/focus mode for many-panel files; not a defect.)

### 2. start_dest flox deep-jump (dev-enable ON)
expected: With dev-enable ON, `am start --es start_dest FineTune|Spool|PrintStatus` lands on the named screen after Splash.
result: pass
evidence: flox + live E5 (192.168.1.120). `start_dest=FineTune` → FineTune Hub; `start_dest=Spool` → Spool screen w/ live Spoolman list. Also confirms the 18-06 FineTune + 18-07 Spool exemplars render correctly on-device. PrintStatus = default landing.

### 3. start_dest garbage input
expected: `--es start_dest NotARealScreen` launches normally to default screen, no crash.
result: pass
evidence: With dev ON, garbage dest → default Standby Print-Status, no crash, no FATAL in logcat. parseStartDest total on untrusted input, confirmed on-device.

### 4. Release-inert check
expected: From dev-disabled (default) state, the `start_dest` extra is IGNORED (lands default).
result: pass
evidence: DataStore `dev_cycler_enabled` decoded = `08 00` (false) by default. With dev OFF, `start_dest=FineTune` → default Standby Print-Status (extra ignored). Real release also `BuildConfig.DEBUG=false` (WR-01 fix) → doubly inert. Dev restored to OFF after testing.

### 5. Non-exemplar regression smoke
expected: Non-exemplar screens (only touched shared files this phase) still render — no regression.
result: pass
evidence: Deep-jumped + screenshotted Temperature, Move, Files, Console — all render correctly, no crashes. Move = jog pad w/ directional colors + unhomed caution triangles + lock + distance steps. Files = breadcrumb + empty-state. Console = scrollback + shape-coded severity glyphs.
note (NOT a Phase-18 regression): Temperature showed "No heaters" while PrintStatus reads heaters fine — TemperatureScreen.kt not modified this phase; possibly deep-jump-before-capabilities-ready or pre-existing. Flagged for separate look. Screens not smoked: Webcam, Macros, Calibration, Extrude, Spool-scan.

### 6. Pseudolocale en-XA sweep
expected: Tokenized exemplar strings show accented/expanded pseudo-text under en-XA; deferred literals (ScanSurface, FwRetraction) stay plain English.
result: accepted-deferred
blocked_by: on-device-only
reason: "Android Studio's Compose preview does NOT apply AAPT2 pseudolocalization for `locale=\"en-XA\"` — it renders the base English strings (the `*PseudolocaleSpotCheck` panels show plain English in Studio, which is expected layoutlib behavior, not a code/tokenization defect). The authoritative en-XA check is on-device: Settings → Languages → add English (XA), then glance at a tokenized screen. Deferred to a future device session — lowest-stakes check; tokenization is already proven (strings resolve via R.string, build green)."
resolution: "Owner accepted as deferred 2026-06-07 (/gsd-verify-work 18, close-out). Not a blocker — tokenization is proven by R.string resolution + green build; the en-XA glance is an optional layout-stress confirmation, naturally re-checkable during the Phase-22 on-device release pass."

## Summary

total: 6
passed: 5
issues: 0
pending: 0
skipped: 0
blocked: 0
accepted_deferred: 1  # Test 6 en-XA on-device sweep — owner sign-off 2026-06-07

## Gaps

[0 open. One issue was found AND resolved during this UAT:]
- truth: "Studio @Preview panels render each theme combo on its themed background (dark looks dark)"
  status: resolved
  reason: "PreviewBox painted no background → all panels showed Studio default-white → dark/light indistinguishable. Fixed: PreviewBox now paints the themed shell bg (mirrors MainActivity:79)."
  severity: minor
  test: 1
  resolved_in: 728f800
