---
phase: quick-260608-m7b
plan: 01
subsystem: ui-printstatus
tags: [branding, jiib, drawable, print-status]
requires:
  - app/src/main/res/drawable/ic_jiib_foreground.xml (path geometry source)
provides:
  - app/src/main/res/drawable/jiib_icon.xml (tintable full-bleed jiib mark)
  - "Print Status home surface wears the jiib brand mark (both former benchy use-sites)"
affects:
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
tech-stack:
  added: []
  patterns:
    - "Single-fill white VectorDrawable + call-site tint (Compose Icon tint / ColorFilter.tint)"
key-files:
  created:
    - app/src/main/res/drawable/jiib_icon.xml
  modified:
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
decisions:
  - "Reused ic_jiib_foreground.xml's six paths verbatim; no hand-conversion of raw SVG circles"
  - "Dropped the launcher safe-zone group inset; 480x480 square viewport for full-bleed 1:1"
  - "benchy.xml / img/benchy.svg left untouched (still the per-printer brandable default)"
metrics:
  duration: ~5 min
  completed: 2026-06-08
  tasks: 2
  files: 2
---

# Quick 260608-m7b: Replace Benchy Mascot with jiib Icon Mark Summary

Swapped the benchy mascot for the jiib brand mark on the Print Status home screen — added a tintable full-bleed `jiib_icon.xml` VectorDrawable and re-pointed both former benchy use-sites (idle ring-center Icon + faint Focus backdrop Image), carrying the Phase-18.2 jiib rebrand onto the home surface.

## What Was Built

**Task 1 — `jiib_icon.xml` drawable (commit `ce49397`):**
- New tintable single-fill VectorDrawable of the jiib mark.
- Reused the six `<path>` strings from `ic_jiib_foreground.xml` verbatim (3 sail paths + 3 two-arc circle paths — the SVG-circle → pathData conversion was already done during the 18.2 rebrand).
- Dropped the launcher safe-zone `<group scaleX/Y=0.16 translate...>` inset so the paths draw at native coordinates and fill the viewport.
- Square `480x480` viewport (`width/height=480dp`) keeps the mark's 1:1 aspect full-bleed (native span ~x:66.5–471, y:20.1–382.4).
- All six paths carry a single solid `#FFFFFFFF` fill so a Compose `Icon` tint / `ColorFilter.tint` recolors the whole mark at the call-site (same pattern as benchy.xml).

**Task 2 — PrintStatusScreen.kt swaps (commit `2d9bd83`):**
- Site A (idle no-job Icon, ring center): `R.drawable.benchy` → `R.drawable.jiib_icon`; `aspectRatio(1600f / 900f)` → `aspectRatio(1f)` (square mark, was 16:9). Kept `tint = t.accent2` and `contentDescription = null`.
- Site B (faint Focus backdrop Image): `R.drawable.benchy` → `R.drawable.jiib_icon`. Kept `ContentScale.Crop`, `ColorFilter.tint(t.accent2)`, `alpha = 0.45f`, `fillMaxSize()`.
- Refreshed the two stale "Benchy" code comments to name the jiib brand mark (optional polish).

## Verification

- `test -f jiib_icon.xml` → EXISTS; `grep -c 'android:fillColor="#FFFFFFFF"'` → **6**; no real `<group>` element; viewport `480x480`. ✅
- `grep -c "R.drawable.benchy" PrintStatusScreen.kt` → **0**; `grep -c "R.drawable.jiib_icon"` → **2**; site A uses `aspectRatio(1f)`. ✅
- `:app:assembleDebug --no-daemon` → **BUILD SUCCESSFUL, exit 0**; new drawable resolved with no aapt resource-linking error. ✅ (hard gate)
- `:app:installDebug` on flox (0a64b42e / Nexus 7 - 11) → **BUILD SUCCESSFUL, Installed on 1 device.** ✅

## Deviations from Plan

None — plan executed exactly as written.

## Pending Owner Step

**Visual confirmation on flox (owner):** Open the idle (no-job) Print Status screen and confirm:
- Ring center shows the accent2-tinted jiib mark at 1:1 (square, not stretched 16:9).
- Focus backdrop shows the accent2-tinted jiib mark, Crop-filled at 0.45 alpha.

The debug APK is installed on flox; only the owner's eyeball confirmation remains.

## Self-Check: PASSED

- `app/src/main/res/drawable/jiib_icon.xml` — FOUND
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` — FOUND (modified)
- Commit `ce49397` — FOUND
- Commit `2d9bd83` — FOUND
