---
status: resolved
trigger: "Spool screen MFG/vendor filter broken (P23 UAT item, re-confirmed failed on fresh build 2026-06-10). Two defects: (1) multi-select doesn't accumulate — always filters to only the last item selected; (2) vendor option list built from Spoolman's manufacturers/vendors table instead of being derived from the FILAMENTS list walked back to manufacturer."
created: 2026-06-11T03:05:00Z
updated: 2026-06-11T04:30:00Z
---

## Symptoms

DATA_START
- expected: Tapping multiple vendor chips in the Spool screen's MFG filter picker selects ALL of them (multi-select accumulates) and the spool list OR-matches any selected vendor. The vendor option list should contain ONLY manufacturers that currently have filaments — derived by listing Spoolman FILAMENTS and walking each back to its manufacturer — not the raw vendors/manufacturers table (which lists manufacturers with zero current filaments).
- actual: (1) selection does not accumulate — the filter always applies ONLY the last item tapped; (2) the option list is built from Spoolman's manufacturers/vendors table, so manufacturers with no filaments appear as options.
- errors: none — silent misbehavior, no crash.
- timeline: First flagged at Phase-23 UAT (2026-06-09) and deferred under a stale-APK hypothesis. Re-tested 2026-06-10 on a CONFIRMED-fresh build (installed same day, mtime-verified) — still broken, so the stale-APK hypothesis is REFUTED; this is a real code defect that has never worked.
- reproduction: flox → Spool screen → filter control → MFG/vendor picker → tap 2+ vendor chips → observe list filters to last-tapped vendor only; observe option list includes filament-less manufacturers.
DATA_END

## Context

- P23 commit 2b6c92a migrated the vendor filter field String? → List<String> + OR-match; host test SpoolHolderVendorTest is GREEN — so the holder-level OR-match logic passes on host, yet device behavior filters to last-selected-only. Suspect the UI selection-state plumbing (chip tap handler replacing instead of toggling/accumulating, or a takeover rebuild resetting prior selections) rather than the holder match logic — verify, don't assume.
- Defect 2 is a data-source fix: option list should derive from Spoolman filaments (GET /v1/filament) → vendor, deduped, instead of GET /v1/vendor (or whatever the manufacturers-table source is). Check SpoolmanClient for which endpoint feeds the vendor options.
- Likely files: app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt, SpoolHolder.kt, designsystem SortFilterControlRow / Field-takeover picker, spool/SpoolmanClient.kt.
- Phase 26-07 touched SpoolScreen (measured-weight Field-takeover) — unrelated area but read current file state, don't assume P23-era line numbers.
- Build/test: Gradle runs Windows-side via /mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>" (./gradlew does NOT work from WSL); pipe through `tr -d '\r'`; exit code authoritative. Device flox is connected via adb (E:\Android\Sdk\platform-tools\adb.exe).

## Current Focus

status: fixing — DEFECT 1 owner-CONFIRMED on flox (do not touch comma-join). DEFECT 2 re-spec'd by owner: derive vendor universe from SPOOLS, not filaments.

reasoning_checkpoint:
  hypothesis: "Defect 2 (corrected) — Spoolman classification is mfg → filament → spool. The owner has filaments from manufacturers for which he owns ZERO physical spools. The first defect-2 fix derived the vendor universe from the FILAMENT list (filament → vendor.name), so those spool-less manufacturers STILL appeared. The correct universe is derived from the SPOOL list (spool → filament → vendor.name), which excludes any manufacturer with a filament definition but no physical spool."
  confirming_evidence:
    - "Owner on-device verdict (2026-06-10, fresh mtime-verified build): the option list still showed manufacturers he has filaments-but-no-spools for. Direct observation, not inference."
    - "Spoolman data model: SpoolmanSpool.filament: SpoolmanFilament? → vendor: SpoolmanVendor? → name: String? — the nested walk exists in the model (SpoolmanModels.kt:100,56,45)."
    - "Live golden spoolman-live-ender5-proxy-pla.json: a /v1/spool list row carries the FULL nested filament.vendor object inline (vendor:{id:1,name:'Sunlu',...}). parseSpoolmanSpools decodes it via SpoolmanSpool.serializer(). So NO model change and NO extra endpoint are needed — the spool list already carries vendor."
    - "Defect 1 (comma-join in buildSpoolQuery) was owner-CONFIRMED WORKING on flox — leave it and its tests untouched."
  falsification_test: "If, after deriving the vendor universe from the spool list, a manufacturer the owner has filaments-but-no-spools for STILL appears as an option, the spool-derived hypothesis is wrong. Conversely, the new host test asserts a 'Ghost' vendor (a filament definition + a /v1/vendor row, but ZERO spools) is EXCLUDED — it FAILS against the old filament-derived code."
  fix_rationale: "loadChips must source the vendor universe from the spool list (same allow_archived=false base the screen lists), walking spool.filament?.vendor?.name → trim → case-insensitive dedup → sort. This is the SPOOL set, so a manufacturer represented only by a filament definition (no physical spool) is structurally excluded — addressing the owner's exact corrected requirement, not a symptom."
  blind_spots: "Vendor-universe stability: derive from a facet-UNFILTERED spool read (allow_archived=false only), NOT the currently-filtered list, so toggling a vendor doesn't shrink the option universe. Archived inclusion: the screen lists allow_archived=false, so the universe uses the same — archived-only manufacturers won't appear, matching the listed rows (owner: 'match whatever spool set the screen lists'). Page-size cap: use the same generous limit; on a hobby inventory the spool count is small. Have NOT device-verified the corrected universe yet — that is the next on-device checkpoint."

next_action: re-implement loadChips vendor universe as spools-derived (one extra facet-unfiltered listSpools read, walk filament.vendor.name); rewrite the defect-2 tests to encode SPOOLS-derived options; leave buildSpoolQuery + its defect-1 tests untouched; run host suite + assembleDebug.

## Evidence

- timestamp: 2026-06-11T03:20:00Z
  checked: SpoolHolder.toggleVendor + SpoolHolderVendorTest
  found: The holder's toggle logic is CORRECT — `toggleVendor` accumulates into `filters.vendors: List<String>` (add-if-absent / remove-if-present), and the green host tests assert this accumulation. So the IN-APP selection STATE is multi-select. The defect is NOT in the toggle state machine.
  implication: Defect 1 is NOT a UI selection-replace bug. The state holds all selected vendors. The filter-to-last-only behavior must come from how that List is serialized into the Spoolman query (buildSpoolQuery) vs what Spoolman actually does with it.

- timestamp: 2026-06-11T03:22:00Z
  checked: buildSpoolQuery (SpoolHolder.kt:577) + SpoolmanClient.encodeQuery
  found: buildSpoolQuery emits ONE REPEATED param per vendor — `filters.vendors.forEach { parts += "filament.vendor.name=$it" }` → `filament.vendor.name=Polymaker&filament.vendor.name=Sunlu`. The in-code comment ASSERTS "Spoolman supports repeated params for OR queries" — this assumption was never verified against the server; the host test only asserts the repeated params are PRESENT in the string, never that Spoolman ORs them.
  implication: The "Spoolman ORs repeated params" assumption is the prime suspect for defect 1. Verify against Spoolman source.

- timestamp: 2026-06-11T03:30:00Z
  checked: Spoolman source — api/v1/spool.py `find` endpoint (master) + database/spool.py `find` + database/utils.py `add_where_clause_str`
  found: CONFIRMED ROOT CAUSE (defect 1). Spoolman declares `filament.vendor.name` as a SINGLE `str | None` query param (spool.py:202), NOT a list. FastAPI binds a repeated query param to a scalar `str` by taking the LAST occurrence. The CORRECT multi-value mechanism is comma-separated terms WITHIN ONE param: the param description literally says "Separate multiple terms with a comma", and `add_where_clause_str` (utils.py:65) does `for value_part in value.split(",") … sqlalchemy.or_(*conditions)` — it splits the single string on commas and ORs the parts. So `filament.vendor.name=Polymaker,Sunlu` (ONE param, comma-joined) ORs; `filament.vendor.name=Polymaker&filament.vendor.name=Sunlu` (repeated) filters to the LAST = "Sunlu" only. This matches the symptom EXACTLY ("filters to only the last item selected").
  implication: Defect 1 fix = change buildSpoolQuery to emit ONE comma-joined param: `parts += "filament.vendor.name=" + filters.vendors.joinToString(",")`. Note: vendor names could contain commas in theory — Spoolman has no escape mechanism, but real manufacturer names don't; acceptable. (filament.material already uses this exact comma-join pattern at line 573, so it's the established convention.)

- timestamp: 2026-06-11T03:32:00Z
  checked: SpoolHolder.loadChips (SpoolHolder.kt:536-542)
  found: CONFIRMED ROOT CAUSE (defect 2, FIRST diagnosis). The vendor OPTION list is built from `client.listVendors()` → `GET /v1/vendor` (the manufacturers/vendors TABLE), then `.mapNotNull { it.name }`. This lists every manufacturer record regardless of whether any current filament references it — so filament-less manufacturers appear as options.
  implication: FIRST fix derived the universe from FILAMENTS (`GET /v1/filament` → vendor.name). This was INSUFFICIENT — see the next entry.

- timestamp: 2026-06-10 (owner on-device verdict, flox, fresh mtime-verified build)
  checked: Defect 2 first fix (filament-derived universe) on real hardware against live Spoolman.
  found: STILL WRONG. The owner corrected the spec: Spoolman classification is mfg → filament → spool. He has FILAMENT definitions for manufacturers he owns ZERO physical spools of, and those were the ones still showing. The universe must be derived from the SPOOL list (spool → filament → vendor.name), not the filament list. Defect 1 (comma-join) was confirmed WORKING — do not touch it.
  implication: Defect 2 corrected fix = derive the vendor universe from a facet-unfiltered SPOOL read (allow_archived=false, same as the listed rows), walking spool.filament?.vendor?.name → dedup → sort. Verified the model + live data already carry this: SpoolmanSpool.filament?.vendor?.name exists (SpoolmanModels.kt), and the live spool-list golden (spoolman-live-ender5-proxy-pla.json) carries the full nested filament.vendor object inline — so NO model change, NO extra endpoint type, just one more listSpools read in loadChips.

- timestamp: 2026-06-11 (re-implementation + host verification)
  checked: loadChips spools-derived universe + rewritten defect-2 tests + host suite + assembleDebug.
  found: loadChips now sources vendors from parseSpoolmanSpools(client.listSpools("allow_archived=false&limit=$SPOOL_LIMIT")).rows.mapNotNull { it.filament?.vendor?.name }, case-insensitive dedup, sorted. Defect-2 tests rewritten to a spool-derived fake (a "Ghost" vendor with a filament definition AND a /v1/vendor row but ZERO physical spools is EXCLUDED; "Sunlu"/"sunlu" across two spools collapses to one). Defect-1 comma-join + its tests UNTOUCHED. Host: 70 spool tests GREEN (incl. the 2 rewritten defect-2 tests + all defect-1 comma-join guards); assembleDebug BUILD SUCCESSFUL.
  implication: Ready for the on-device re-verification checkpoint. Host can't observe the real Spoolman spool inventory, so the owner must confirm on flox that only manufacturers with physical spools now appear AND that multi-select OR still works.

## Eliminated

- hypothesis: Stale APK masked a working fix (P23 theory) — REFUTED 2026-06-10: fresh mtime-verified build still exhibits both defects.

## Resolution

root_cause: |
  TWO independent defects, both confirmed against Spoolman source (master).

  Defect 1 (multi-select filters to last only): buildSpoolQuery emitted REPEATED query params
  — `filament.vendor.name=A&filament.vendor.name=B` — on the false assumption (stated in an in-code
  comment, never verified) that Spoolman ORs repeated params. Spoolman's `find` endpoint
  (spoolman/api/v1/spool.py) declares `filament.vendor.name` as a SINGLE scalar `str | None`. FastAPI
  binds a repeated scalar param to its LAST occurrence, so only the last-tapped vendor ever reached the
  query → "filters to only the last item selected". The in-app selection STATE was correct all along
  (toggleVendor accumulates, host-tested green) — the loss was purely in query serialization. Spoolman's
  documented OR mechanism is comma-separated terms WITHIN ONE param value: database/utils.py
  add_where_clause_str does `value.split(",")` → `sqlalchemy.or_(*conditions)`.

  Defect 2 (spool-less manufacturers appear as options): SpoolHolder.loadChips originally sourced the
  vendor option universe from `client.listVendors()` = GET /v1/vendor (the manufacturers TABLE). The
  first fix switched it to the FILAMENT list, but the owner's on-device verdict (2026-06-10) showed that
  was still wrong: Spoolman classification is mfg → filament → spool, and he has filament definitions for
  manufacturers he owns ZERO physical spools of, which the filament-derived universe still surfaced. The
  CORRECT universe is derived from the SPOOL list (spool → filament → vendor.name), excluding any
  manufacturer without a physical spool.

fix: |
  Defect 1 (owner-CONFIRMED WORKING on flox — UNTOUCHED) — SpoolHolder.buildSpoolQuery: a single
  comma-joined param `parts += "filament.vendor.name=" + filters.vendors.joinToString(",")` (mirrors
  filament.material; matches Spoolman's OR contract). Left exactly as-is.

  Defect 2 (RE-IMPLEMENTED per owner's corrected spec) — SpoolHolder.loadChips: the vendor option
  universe is now derived from the SPOOL list, not the filament list. Spoolman classification is
  mfg → filament → spool; the owner has filament definitions for manufacturers he owns zero physical
  spools of, and the filament-derived universe still surfaced those. Now:
  `parseSpoolmanSpools(client.listSpools("allow_archived=false&limit=$SPOOL_LIMIT")).rows.mapNotNull
  { it.filament?.vendor?.name }`, trimmed, deduped case-insensitively, sorted. The facet-unfiltered
  base read (allow_archived=false, no vendor/material/color filters) keeps the universe stable across
  toggles and matches the spool set the screen lists. No model change and no new endpoint were needed —
  SpoolmanSpool.filament?.vendor?.name already exists and the live /v1/spool list carries the nested
  filament.vendor object inline (confirmed against spoolman-live-ender5-proxy-pla.json).

  Tests: SpoolHolderVendorTest defect-2 block rewritten from a filament-derived fake to a SPOOL-derived
  fake — a "Ghost" manufacturer with a filament definition AND a /v1/vendor row but ZERO physical spools
  is asserted EXCLUDED (fails against the old filament-derived code); "Sunlu"/"sunlu" across two spools
  collapses to one (case-insensitive dedup). All defect-1 comma-join tests (SpoolHolderVendorTest +
  SpoolPickerStateTest) left UNTOUCHED.

verification: |
  HOST: :app:testDebugUnitTest --tests works.mees.dinghy.ui.spool.* --tests works.mees.dinghy.spool.* —
  BUILD SUCCESSFUL, all 70 spool tests pass (incl. the rewritten spools-derived defect-2 guards AND the
  untouched defect-1 comma-join guards). :app:assembleDebug — BUILD SUCCESSFUL.
  APK: app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk (mtime 2026-06-10 21:12, fresh build).
  DEVICE: owner-CONFIRMED on flox 2026-06-10 (fresh mtime-verified build, two checkpoint rounds):
  round 1 confirmed multi-select OR works; round 2 confirmed the spools-derived universe — ghost
  (filament-but-no-spool) manufacturers gone, OR-match regression intact. Fix committed as 0b2daaf.

files_changed:
  - app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt (loadChips vendor universe → spools-derived; buildSpoolQuery defect-1 comma-join UNTOUCHED)
  - app/src/test/java/works/mees/dinghy/ui/spool/SpoolHolderVendorTest.kt (defect-2 tests → spools-derived; defect-1 comma-join tests untouched)
