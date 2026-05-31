---
status: partial
phase: 03-design-system-theming-foundation
source: [03-VERIFICATION.md]
started: 2026-05-31T00:00:00Z
updated: 2026-05-31T00:00:00Z
---

## Current Test

[awaiting human testing on flox via the debug gallery]

## Tests

### 1. Focus/Field/Gutter portrait + landscape grid fidelity
expected: Rotate device in the gallery; compare against docs/ui_design/images/*.png. Portrait: focus/field/gutter stack ~40/40/20. Landscape: Focus|Field side-by-side 50/50, gutter full-width on the same column lines; ring content renders as a true circle centered in its cell (not distorted). No hardcoded pixel gaps or mis-aligned column breaks.
result: [pending]

### 2. Cross-toolkit theme recolor (Compose + Views)
expected: Toggle Dark / Light / Custom in the gallery; every Compose surface (ring, controls, scaffolds) AND the Views GraphView repaint in the new palette simultaneously. No surface retains the previous palette after the toggle.
result: [pending]

### 3. --fs is the sole text-size authority (no OS double-apply)
expected: Set device Accessibility font size to maximum; toggle S / M / L in the gallery. Type scales with the in-app S/M/L setting only — "Large" OS font + "S" app setting produces the same size as "S" alone, not an additive product.
result: [pending]

### 4. ScrubberPage keyboard-free + WR-01 tap reliability
expected: No OS alphanumeric keyboard appears at any point. Drag sets value proportionally to bar width; steppers increment/decrement by step; Apply calls onApply with the working value; Cancel dismisses without changing caller state. WR-01: a short TAP (not a drag) on the fill bar should reliably set the value from the tap position — if taps are swallowed (dual-pointerInput race), this fails.
result: [pending]

### 5. ConfirmGuard appearance + callbacks (both variants)
expected: Destructive guard = full-screen stop-soft tint, confirm button red (Danger), cancel neutral. Positive guard = go-soft tint, confirm button green (Go), cancel neutral. Both Confirm and Cancel callbacks fire reliably on tap.
result: [pending]

### 6. SeverityToast all four severities
expected: Info/Success/Warning/Error each show a distinct badge glyph (i / ✓ / ! / ×) in the severity color, a text message, and a colored border. No two severities share a glyph; color reinforces (never substitutes for) the icon.
result: [pending]

## Summary

total: 6
passed: 0
issues: 0
pending: 6
skipped: 0
blocked: 0

## Gaps
