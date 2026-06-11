---
created: 2026-06-09T00:00:00Z
title: SpoolScreen MFG (vendor) multi-select filter — verify/fix at release polish
area: bugfix
target_phase: 29
source: .planning/phases/23-design-language-foundation/23-06-PLAN.md
---

## Open item

Phase 23 pilot (23-06, the SpoolScreen rebuild onto the new component kit) passed owner
on-device UAT on flox EXCEPT the **manufacturer (MFG/vendor) multi-select filter**. Owner
deferred this to the final pre-release polish/bug-fixing pass (Matthew, 2026-06-09) rather
than block Phase 23 close-out.

**The check:** in the Spool filter, tap the MFG filter tile → tap two different
manufacturers → BOTH should stay selected (toggle, picker stays open), the spool list
OR-filters to either vendor, **Done** returns to the list, **Clear** resets the facet.
Behavior must match the **TYPE / polymer-family** filter, which DOES multi-select correctly.

**Status at deferral:**
- POLYMER/TYPE multi-select: **works on-device** (owner-confirmed).
- MFG/vendor multi-select: owner reported "mfg isn't [working]" on the build tested.
- A continuation executor claimed the vendor code was already correct (`2b6c92a`,
  `vendor: String? → vendors: List<String>`) and blamed a STALE APK
  (Gradle `UP-TO-DATE` → installed an old build); it force-rebuilt + reinstalled a fresh
  APK (`--rerun-tasks`, 18:55 build) and added `SpoolHolderVendorTest` (`42a5ec2`, 11 tests
  asserting toggle/OR-match/no-auto-close). **That fresh build was NOT re-tested on-device
  by the owner** before deferral — so it is UNCONFIRMED whether the issue is fully resolved
  or whether a real MFG-specific bug remains. See [[dinghy-stale-apk-uat-gate]].

**Action at Phase 29 (Release Hardening):**
1. On a build VERIFIED fresh (APK mtime after `2b6c92a`/`42a5ec2`; force-rebuild — do NOT
   trust a bare `UP-TO-DATE` assembleDebug), re-test MFG multi-select on flox in BOTH
   orientations.
2. If it works → close this item (the earlier failure was the stale-APK trap).
3. If MFG still diverges from TYPE → it's a real bug; trace where vendor differs from the
   working material-family path (toggle handler, `isSelected` read source, OR-filter
   predicate, state plumbing through SpoolHolder/SpoolScreen/SpoolPicker) and fix.

COLOR filter is intentionally single-select + auto-close (Spoolman color-similarity endpoint
takes one color) — that is NOT a defect, leave it.
