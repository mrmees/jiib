---
created: 2026-06-05T00:00:00Z
title: Phase 14 UAT item 4 — mid-print switch (D-04) left open
area: testing
target_phase: 14
source: .planning/phases/14-multi-printer-switching/14-UAT.md
---

## Open item

The Phase-14 live two-printer UAT passed 5/6. **Item 4 — mid-print switch (D-04)** was
left OPEN by owner choice (Matthew, 2026-06-05): he declined to start a live print
solely to test it during the UAT session.

**The check:** with a print actively running on one printer, open Devices, switch to the
other printer and back — the running print must continue untouched throughout.

**Why low-risk:** the switch teardown→rebind path is identical to the one proven every
time in UAT items 1/2 (the Devices switch); the only added assertion is "the other
printer's print keeps running." `14-VERIFICATION.md` is therefore `human_needed` on this
single item (not `gaps_found`).

## How to close

Opportunistically, next time a print is genuinely running on either the Ender 5 Plus
(192.168.1.120:7125) or Ender 3 Pro (192.168.1.121:7125): do the switch+back, confirm
the print continues, record PASS in `14-UAT.md` item 4, and flip `14-VERIFICATION.md`
status `human_needed` → `passed`. NOT tagged `resolves_phase` so phase completion does
not auto-close it — it must be closed by a real observation.
