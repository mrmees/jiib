---
status: complete
phase: 03-design-system-theming-foundation
source: [03-VERIFICATION.md]
started: 2026-05-31T00:00:00Z
updated: 2026-05-31T00:00:00Z
---

## Current Test

[complete — 4 passed, 2 issues; all 4 gaps (G-1..G-4) FIXED in plan 03-08 and re-verified on flox 2026-05-31]

## Tests

### 1. Focus/Field/Gutter portrait + landscape grid fidelity
expected: Rotate device in the gallery; compare against docs/ui_design/images/*.png. Portrait: focus/field/gutter stack ~40/40/20. Landscape: Focus|Field side-by-side 50/50, gutter full-width on the same column lines; ring content renders as a true circle centered in its cell (not distorted). No hardcoded pixel gaps or mis-aligned column breaks.
result: pass
note: grid + ring fidelity good. Two unrelated issues surfaced during this test, logged as gaps G-1 (light-mode control text contrast) and G-2 (ScrubberPage width alignment).

### 2. Cross-toolkit theme recolor (Compose + Views)
expected: Toggle Dark / Light / Custom in the gallery; every Compose surface (ring, controls, scaffolds) AND the Views GraphView repaint in the new palette simultaneously. No surface retains the previous palette after the toggle.
result: pass
note: cross-toolkit recolor contract HOLDS — Compose components + the Views GraphView recolor together. Sole exception is the gallery PAGE's own root background not recoloring (G-1, debug-only). Production screens are unaffected (each paints t.bg). G-1 supersedes the earlier "light-mode unreadable" framing — that symptom was dark-theme text on the gallery's stuck-light backdrop.

### 3. --fs is the sole text-size authority (no OS double-apply)
expected: Set device Accessibility font size to maximum; toggle S / M / L in the gallery. Type scales with the in-app S/M/L setting only — "Large" OS font + "S" app setting produces the same size as "S" alone, not an additive product.
result: pass

### 4. ScrubberPage keyboard-free + WR-01 tap reliability
expected: No OS alphanumeric keyboard appears at any point. Drag sets value proportionally to bar width; steppers increment/decrement by step; Apply calls onApply with the working value; Cancel dismisses without changing caller state. WR-01: a short TAP (not a drag) on the fill bar should reliably set the value from the tap position — if taps are swallowed (dual-pointerInput race), this fails.
result: issue → RESOLVED (03-08)
reported: "single tap does not get captured, but if i start dragging it immediately picks up the location and starts scrubbing from there"
severity: major
note: FIXED in plan 03-08 via a single `awaitEachGesture` (sets value on touch-down, below slop threshold so a tap registers; tap & drag funnel through host-tested `fractionFromX`, one pointerInput, no WR-01 race). Re-verified on flox 2026-05-31: tap-to-set passes, drag still scrubs. Tracked as G-3 (resolved).

### 5. ConfirmGuard appearance + callbacks (both variants)
expected: Destructive guard = full-screen stop-soft tint, confirm button red (Danger), cancel neutral. Positive guard = go-soft tint, confirm button green (Go), cancel neutral. Both Confirm and Cancel callbacks fire reliably on tap.
result: issue → RESOLVED (03-08)
reported: "Does not show full screen in the gallery, but does do full screen in the actual app (though it needs to apply a much stronger opacity filter to the material behind it). buttons work as expected"
severity: minor
note: buttons fire correctly; full-screen works in PRODUCTION. G-4 (weak scrim) FIXED in 03-08: opaque `t.bg` token layered under the stop-soft/go-soft tint so the backdrop firmly obscures content. Re-verified on flox 2026-05-31. Gallery-not-fullscreen was gallery-inline demo (debug-only, not a defect).

### 6. SeverityToast all four severities
expected: Info/Success/Warning/Error each show a distinct badge glyph (i / ✓ / ! / ×) in the severity color, a text message, and a colored border. No two severities share a glyph; color reinforces (never substitutes for) the icon.
result: pass
note: all four severities display distinct + correct ("they look good"). Gallery shows them statically (no trigger affordance) — presentation only, not a defect.

## Summary

total: 6
passed: 4
issues: 2
pending: 0
skipped: 0
blocked: 0

## Gaps

- truth: "The debug gallery page background recolors with the active theme"
  status: resolved
  resolved_by: 03-08 (re-verified on flox 2026-05-31)
  reason: "User clarified the 'can't read controls' symptom is specifically the GALLERY PAGE: its root background does NOT switch to dark, so dark-theme controls (light text) sit on a stuck-light backdrop and are unreadable. Root cause: GalleryActivity.kt:62 uses a bare Material3 Surface(modifier=...) with no color → defaults to MaterialTheme.colorScheme.surface, which this app never populates (color is driven by LocalTokens, not Material colorScheme), so the gallery root ignores dark/light. GalleryScreen also never paints its own .background(LocalTokens.current.bg). DEBUG-ONLY (gallery is excluded from the release APK); production screens (AppShell/SplashScreen/AppDrawer) each paint .background(t.bg) and are unaffected. Fix: paint the gallery root with the token bg (one line, mirror the production pattern). Optional same-class hardening: give MainActivity's bare Surface() an explicit token color even though screens cover it."
  severity: minor
  test: 2
  id: G-1
  artifacts: ["app/src/debug/java/works/mees/dinghy/gallery/GalleryActivity.kt:62", "app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt"]
  missing: ["themed root background on the gallery page"]

- truth: "ScrubberPage internal controls share a consistent width/alignment"
  status: resolved
  resolved_by: 03-08 (re-verified on flox 2026-05-31)
  reason: "User reported: the scrubber bar is not the same width as the −/+/Cancel/Apply button group; both also differ from adjacent gallery sections. Bar-vs-own-button-group is production-relevant; section-to-section is partly the debug-only gallery scaffolding."
  severity: cosmetic
  test: 1
  id: G-2
  artifacts: ["app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt"]
  missing: []

- truth: "A short tap on the ScrubberPage fill bar reliably sets the value from the tap position (WR-01)"
  status: resolved
  resolved_by: 03-08 (re-verified on flox 2026-05-31)
  reason: "User reported: 'single tap does not get captured, but if i start dragging it immediately picks up the location and starts scrubbing.' WR-01 (the Phase-3 dual-pointerInput tap-race) is NOT actually resolved on-device — a zero-movement tap is swallowed; only drag-with-movement registers. Likely fix: add detectTapGestures (or an onPress initial-set) alongside detectDragGestures so a no-move tap sets the value."
  severity: major
  test: 4
  id: G-3
  artifacts: ["app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt"]
  missing: ["tap-to-set gesture handling on the fill bar"]

- truth: "The full-screen ConfirmGuard scrim firmly obscures the content behind it"
  status: resolved
  resolved_by: 03-08 (re-verified on flox 2026-05-31)
  reason: "User reported the production full-screen ConfirmGuard 'needs to apply a much stronger opacity filter to the material behind it' — the backdrop is too transparent and content bleeds through, weakening the safety-gate clarity for the emergency-stop confirm. Buttons + full-screen presentation otherwise correct. Fix: increase the ConfirmGuard scrim alpha / backdrop opacity."
  severity: minor
  test: 5
  id: G-4
  artifacts: ["app/src/main/java/works/mees/dinghy/designsystem/ConfirmGuard.kt"]
  missing: ["stronger scrim/backdrop opacity behind the guard"]
