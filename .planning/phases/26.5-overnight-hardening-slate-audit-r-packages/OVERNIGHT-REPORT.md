# Phase 26.5 — Overnight Hardening Report

**Run started:** 2026-06-11 (overnight, unattended)
**Branch:** `gsd/phase-26.5-overnight-hardening`
**Work order:** `docs/top-down-audit-roadmap.md` Part 4 (overnight-safe subset)
**Finalized:** 2026-06-11 ~07:55Z by plan 26.5-07 (the last plan of the night)

## Run Summary

**All 7 plans landed — zero failures, zero reverts, zero fail-forward triggers.** Waves 0–4
executed in order (R5a → R9 → R10 → R1+R2 → R4+R7), every per-plan build gate green
(`:app:testDebugUnitTest :app:assembleDebug`, plus `:app:assembleRelease verifyMinSdkRelease`
for R5a/R1), full host suite at 1054+ tests with zero failures throughout. Codex deep-reviewed
every plan: 5 of 7 drew findings, all agreeable show-stoppers fixed same-night (see the codex
table below); 26.5-05 was approved clean. Protected surfaces (PrinterStateStore cadence, render
hot paths, M112, dispatcher debounce SEMANTICS, R9 protected list) were never touched —
CommandCatalogDriftTest 6/6 green unmodified. The phase gate is the morning UAT sitting below;
the phase is NOT marked verified tonight per CONTEXT.

## Per-Package Status

| Package | Scope tonight | Status | Plan | Commits |
|---|---|---|---|---|
| R5a | Guardrail infra: gradlew +x, CI, lint baseline enforced, StrictMode | **done** — full LOCAL lint enforcement preserved (one inert crasher detector disabled, stronger than the planned CI-only fallback) | 26.5-01 | 3ec6b7e, af75fcb + codex fix 332c0ff |
| R9 | Memory/docs delint steps 1–3 + 5 (step 4 = script only) | **done** — active memory trued up + stamped; hygiene CI live; archive script written, NEVER run | 26.5-02 | 841a981, 27039a0, cbe1963 + codex fix c92aa03 |
| R10 | Touch responsiveness steps 2–4 + debug-flag instrumentation | **done with one descope** — steps 2 (true disablement + rejection feedback) and 3 (swipe accumulation) shipped; step 4 (indication immediacy / Part-5 cause #4) DEFERRED to morning instrumentation after codex showed the draft was a no-op + perf pessimization | 26.5-03 | 4c2aa99, bb19217, 4d9d81a, d9c0b3e, fe705c8 + codex fix 2dd4bc7 |
| R1 | Install & display correctness (code-only) | **done** — both per-ABI release APKs (v7a vc=1, arm64 vc=2), edge-to-edge + safeDrawingPadding, predictive-back opt-in, POST_NOTIFICATIONS one-shot; codex also fixed a latent pre-existing API<26 launch crash | 26.5-04 | be72f29, 34ea1ff + codex fix 0ae62c4 |
| R2 | Always-on (code-only): keep-screen-on + battery exemption | **done** — DisplayPrefs store (TDD), Settings "Display" section, live window flag at AppShell root, exemption state surface + deep-link; codex approved clean | 26.5-05 | 5d4573c, a910a26, 64d9970 |
| R4 | Build-time command map (CommandMap.kt + rewires) | **done** — six sites rewired (audit said four; research found six), symbolic propagation tests, M112 explicitly non-remappable | 26.5-06 | 35a31b8, 6c8f2ba + codex fix 1114670 |
| R7 | Network posture: useSecure wss/https plumbing (option B) | **done** — scheme plumbing end-to-end (config → profile → editor toggle → socket), TLS cert-failure surfaces a distinct message on the existing Splash Unreachable surface, NSC decision record written; live-TLS connect = morning UAT | 26.5-07 | 31ad990, e508f6c, b38f132 |

## Codex Findings — Acted / Deferred

| Plan | Verdict | Acted (fixed same-night) | Deferred |
|---|---|---|---|
| 26.5-01 | REVISE | CI runs `lintDebug` so the baseline is actually enforced in CI; honest lint-JDK comments (332c0ff) | — |
| 26.5-02 | REVISE | Archive script now guards unshipped phases 26.5/27/28/29 (self-test PASS); link-check pipefail guard (c92aa03) | — |
| 26.5-03 | REVISE | Plain clickable paths RESTORED — the explicit `interactionSource + LocalIndication` draft did not deliver cause-#4 immediacy and eagerly allocated on the perf floor; `rejectedKey` collection made lifecycle-aware (`repeatOnLifecycle(STARTED)`) (2dd4bc7) | **R10 step 4 / Part-5 cause #4** — fix only after morning instrumentation implicates it (real fix = custom press detection / manual Press emission) |
| 26.5-04 | REVISE | `ContextCompat.startForegroundService` (latent API<26 launch crash, pre-existing since Phase 4); onVariants versionCode rewrite scoped to release (0ae62c4) | **MEDIUM:** POST_NOTIFICATIONS "asked once" is in-memory — denial + relaunch re-asks at next first-Connected (Android 13+ hard-suppresses after 2 denials; persist in DisplayPrefs if it annoys at S25 UAT). **MEDIUM:** BackHandler-vs-predictive-back composition order — S25-only verifiable; if the preview pops the NavHost under overlays, register overlay handlers after NavHost |
| 26.5-05 | APPROVED | — | — |
| 26.5-06 | REVISE | Macro-hint strings (`extrude_no_*_macro`) are format args fed from `CommandMap.*.macro`; CommandMapTest gained REAL registry/wire assertions (1114670) | — |
| 26.5-07 | REVISE | **HIGH:** TLS trust failure during the keyed-auth token fetch was collapsing into NetworkUnavailable — now classified (`isTlsTrustFailure`) and routed to `ConnectAttempt.TlsTrust`. **MEDIUM:** HLS derive preserved the source stream_url scheme instead of hardcoding http (1a79761) | **LOW:** the Splash TLS message is hardcoded — tokenize WITH the whole Splash string cluster (the known Phase-28/R6 remainder), not solo |

**Phase verification (opus gsd-verifier, ~04:45):** `human_needed` — 7/7 success criteria
code-complete and verified against the codebase; protected-surface audit CLEAN (cadence, render
hot paths, M112, debounce timing, R9 protected list all untouched); host suite green incl. the
D-10 drift guard. Morning UAT is the phase gate — do NOT mark the phase complete before it.

## DECISIONS-NEEDED

### 1. LICENSE choice — ✅ RESOLVED 2026-06-11: owner chose GPLv3; canonical gpl-3.0.txt committed as LICENSE (R5b unblocked)

*(original decision text retained below)*

### ~~1. LICENSE choice (R5a step 2 — recommend-only; NO file was created)~~

**GPLv3** is the Klipper-ecosystem norm — Klipper, Moonraker, and Fluidd are all GPL, so a GPLv3
jiib signals ecosystem citizenship and guarantees that any fork of the fork-and-edit `CommandMap`
customization point stays open. **MIT/Apache-2.0** maximizes adoption and lets anyone (including
commercial printer vendors) embed or redistribute without copyleft obligations — Apache-2.0 adds
an explicit patent grant over MIT. **Recommendation: GPLv3** — this app exists inside and because
of the GPL Klipper ecosystem, the sideload-an-APK audience loses nothing to copyleft, and
"vendor embeds it in a closed product" is exactly the case you'd probably want to prevent.
No LICENSE file exists in the repo; R5b (README/CONTRIBUTING, the go-public gate) is blocked on
this decision.

### 2. R9 step-4 archive script — review then run (or decline)

Owner-gated by the CONTEXT lock; **nothing was moved tonight.** See "R9 Archive Script" below
for the pointer + numbers. Run order: `--self-test` → dry-run → `--execute`.

### 3. `macrobenchmark-module-wiring` todo placement

R9's delint established that Baseline Profiles DO apply on flox (LineageOS 18.1 / API 30) and all
modern devices — the old "NO-OP on the target" claim was scoped to stock API 23 only, so this
todo's priority was RAISED. Decide whether it lands in Phase 29 (recommended — alongside the
release-build packaging work) or earlier.

## R9 Archive Script

- **Path:** `.planning/phases/26.5-overnight-hardening-slate-audit-r-packages/26.5-r9-archive-script.sh`
- Would DELETE: **31** `*DISCUSSION-LOG*` files under `.planning/phases/` (§R9 said ~26; the count
  grew as phases accrued since the audit).
- Would MOVE: **113** shipped-phase CONTEXT/VALIDATION/VERIFICATION/REVIEW files →
  `.planning/archive/<phase>/`. PLAN/SUMMARY pairs stay put (protected pattern), as do the
  phases/01+13 captures, 15.2-AUDIT.md, PATTERNS 02/03/18, and the spoolman fixtures.
- Dry-run by default; `--execute` is the owner gate; `--self-test` asserts every protected path is
  blocked with zero mutations. Codex-hardened to also guard the unshipped 26.5/27/28/29 dirs.
- ⚠ drvfs caveat: on `/mnt/e` the filesystem shows mode 0777 and chmod is a no-op — the
  authoritative non-executable state is the git index mode `100644` (verified; `core.filemode false`).

## Morning UAT Checklist

Ordered for ONE sitting — flox (Nexus 7, LineageOS 18.1/API 30) first, then S25 Ultra, then CI.

**Before anything:** force-rebuild (`--rerun-tasks` or clean) and **check the APK mtime is newer
than commit b38f132** before any install — the stale-APK trap has burned UAT before
([[dinghy-stale-apk-uat-gate]]). Release APKs are unsigned: debug-sign via
`E:\Android\sign-release.bat <in.apk> <out.apk>` before `adb install`. Debug builds
(`installDebug`) need no signing and carry the R10 instrumentation.

### flox group (use a DEBUG build — R10 instrumentation is `BuildConfig.DEBUG`-gated, no flag to flip)

> **2026-06-11 sitting #3 (flox, at-device):** owner ran the flox group on the real Nexus 7.
> R2 doze, R10 drawer swipe, and R10 disabled-stepper PASS (marked below). Overall tap feel
> **improved**, BUT the rejection-flash UX is **REJECTED by owner** — after a few stepper taps
> the value flashes amber then the controls lock out ~5s (markPending-per-tap busy churn).
> Owner direction: "batch commands by waiting for a user to not press for half a second before
> sending the current value." → Superseded by quick task **260611-rmr** (stepper trailing-commit
> batching); stepper items get a re-test on the new build. Side remark captured as a deferred
> todo: swipe-up nav may be retired entirely in favor of the new navigation method.

Logcat filter for the R10 items: `adb logcat -s Dispatcher SwipeDetector`
(`Dispatcher` → `reject: key=… reason=in_flight|debounce remaining=…ms`;
`SwipeDetector` → `drag: amt=<delta> total=<runningTotal> fired=<bool>`).

- [ ] **R10 tap torture:** 20 rapid taps per control class (stepper +/− on AdjusterPanel, ListRow,
      OutlinedControl command buttons) — ≥95% register with visible same-frame feedback; every tap
      either acts or shows visible rejection feedback; zero silent swallows. Cross-check logcat:
      a tap with a visible indicator but NO log line = real event loss, not an intentional rejection.
      *2026-06-11 flox sitting: overall tap feel IMPROVED; final stepper verdict deferred to the
      post-260611-rmr re-test (trailing-commit batching changes the stepper tap model).*
- [ ] **R10 rejection flash:** rapid-tap a stepper during a busy window (e.g. while a heater command
      is settling) — the adjuster's hero value flashes amber (one-shot ~200ms), NOT nothing.
      (Note: the Temperature heater stepper's old local pre-check was removed so the dispatcher owns
      dedup exclusively — worth a glance that heater behavior is unchanged.)
      *2026-06-11 flox sitting: **REJECTED by owner** — the flash itself works, but a tap burst
      flashes amber then busy-locks the controls ~5s (markPending-per-tap churn). **Superseded by
      quick task 260611-rmr** (trailing-commit batching: taps accumulate locally, ONE dispatch per
      500ms quiet window; rejection flash retained as fallback signal only). Kept as historical
      record — do not re-verify against the per-tap model.*
- [x] **R10 disabled stepper = no ripple:** with a dimmed (busy-locked) stepper, a tap produces NO
      ripple at all (the clickable is absent, not guarded). — **PASS (2026-06-11 flox sitting).**
- [x] **R10 drawer swipe:** swipe-up opens the App Drawer reliably with slow AND fast gestures
      (per-gesture drag accumulation); no more per-event 80px threshold misses. — **PASS
      (2026-06-11 flox sitting: slow deliberate swipe opens reliably).**
- [ ] **R10 cause #4 verdict (instrumentation):** if taps still feel laggy in scrollables despite
      the above, the deferred indication-immediacy fix is implicated — log it for a follow-up phase
      (the overnight draft was reverted as a no-op; a real fix needs custom press detection).
- [ ] **R10 on-device FineTuneNavTest:** run the instrumented test — swipe accumulation should also
      clear the harness defect ([[dinghy-instrumented-swipe-threshold]]).
- [ ] **R2 keep-awake toggle:** Settings → Display: ON keeps the screen awake on the print surface;
      OFF lets the system timeout apply (live, no relaunch); default is ON.
- [ ] **R2 exemption flow:** Battery row reads "Optimized — tap…" → tap → system dialog (on
      LineageOS the fallback may open the battery-optimization LIST — find jiib there; expected,
      not a bug) → grant → row reads "Exempt…" on return (ON_RESUME re-check).
- [x] **R2 doze survival:** unplugged, screen off, 20+ minutes with the exemption granted —
      reconnect is alive or resyncs cleanly on wake. — **PASS (2026-06-11 flox sitting: forced
      deep idle, websocket survived, double-checked).**
- [ ] **R1 Nexus-7-unchanged regression:** install the **armeabi-v7a** APK
      (`app/build/outputs/apk/release/app-armeabi-v7a-release-unsigned.apk`, versionCode 1) — app
      behaves exactly as before (no inset regressions on API 30; safeDrawing resolves to ~0 in the
      bar-less kiosk layout; no permission-prompt surprises).
- [ ] **R7 plain-ws regression (BOTH printers):** with the new build, 192.168.1.120 (E5+) and
      192.168.1.121 (E3) both still connect over plain ws:// with the Use HTTPS/WSS toggle OFF
      (the default — old profiles must decode to OFF).

### S25 Ultra group

> **2026-06-11 sitting #1:** run on a **Moto G Play 2024 (Android 14 / API 34, arm64)** standing in
> for the planned S25 Ultra.
> **2026-06-11 sitting #2 (remote, the ACTUAL S25 Ultra):** owner ran the full remote checklist on
> the S25 Ultra (Android 15/16 — the forced-edge-to-edge case) via the `uat-26.5-overnight` GitHub
> prerelease APK + Tailscale to both printers (set up this morning: ts-ender5plus-1
> 100.119.234.19, ts-ender3pro 100.74.197.18; E3's moonraker.conf gained 100.0.0.0/8 trust).
> **ALL ITEMS PASS**, specifically:
> - R1 arm64 install on the S25 itself ✅ (the audit's C1 headline device)
> - R1 edge-to-edge under FORCED edge-to-edge (targetSdk 35 on Android 15+) ✅ — incl. the
>   260611-cj1 system-bar theme lock (bars follow the app theme, no drawer flicker, live theme
>   cycling) — the quick fix is owner-eyeball CONFIRMED
> - R1 predictive back on the S25 ✅ — the codex BackHandler-ordering deferred-MEDIUM is RESOLVED
>   (no overlay-order defect on the named device)
> - R1 POST_NOTIFICATIONS one-shot ✅
> - R10 rejection feedback on a live printer ✅ (rapid-tap stepper shows the flash, no silent
>   swallows; disabled controls don't ripple)
> - R2 keep-awake toggle + battery-exemption flow ✅
> - **Cross-printer switching ✅** (two live printers, profiles switch cleanly). NOTE: this does
>   NOT close Phase-21's deferred SC8 item — that one is specifically the per-printer CAMERA
>   selection holding across a switch, and webcams were unavailable over Tailscale (LAN URLs).
>   SC8 camera-hold still needs an at-home pass with feeds visible.
> - R5a CI: every branch push green on GitHub runners (test+lintDebug+hygiene) ✅
> Remaining open: the flox group below (floor-device tap-torture, doze, v7a regression,
> system-bar eyeball on API 30) + R7 wss (Phase-29 deferral). Webcam-over-Tailscale "unavailable"
> is EXPECTED (LAN camera URLs, no subnet routing) — not a defect.

- [x] **R1 arm64 install:** debug-sign + sideload
      `app/build/outputs/apk/release/app-arm64-v8a-release-unsigned.apk` (versionCode 2) — installs
      and runs (previously impossible: no arm64 slice existed). **PASS** — the arm64-v8a APK
      installs and runs on the Moto.
- [x] **R1 edge-to-edge:** both orientations — content respects status bar / nav bar / cutout
      (safeDrawingPadding); the app background still paints edge-to-edge behind the bars; nothing
      drawn-under or clipped. **PARTIAL** — content insets correct, BUT dark theme showed a WHITE
      navigation bar (the no-arg enableEdgeToEdge followed the system light theme) and bars
      restyled when popups opened → fixed by quick task 260611-cj1
      (`.planning/quick/260611-cj1-lock-edge-to-edge-system-bars-to-active-theme`).
- [x] **R1 predictive back:** back-gesture preview animates (enableOnBackInvokedCallback). ⚠ codex
      deferred-MEDIUM: if the preview pops the NavHost UNDER the drawer/sub-stack overlays, note it —
      the fix is overlay-handler registration order. **PASS** — preview animates; no overlay-order
      defect observed.
- [x] **R1 POST_NOTIFICATIONS one-shot:** first connect triggers exactly one permission dialog;
      DENY → app fully functional, FGS notification simply absent. ⚠ codex deferred-MEDIUM: the
      asked-once latch is in-memory — a relaunch may re-ask once; if that annoys, the fix is
      persisting the flag in DisplayPrefs. **PASS**
- [x] **R2 display settings (keep-awake/battery rows on this device):** **PASS** — added at the
      2026-06-11 Moto sitting (the R2 rows were exercised on this device alongside the R1 items).
- [ ] **R7 toggle-ON failure is comprehensible + reversible:** **DEFERRED to Phase 29 polish per
      owner (2026-06-11 — no TLS-fronted Moonraker exercised this sitting).** Printers → edit a
      printer → toggle "Use HTTPS/WSS" ON against a printer with NO TLS proxy → Save. Connection
      fails with a readable error on the Splash (not a hang/crash); toggle back OFF + Save
      reconnects cleanly.
- [ ] **R7 wss connect (best-effort, only if a TLS-fronted Moonraker is available):** **DEFERRED to
      Phase 29 polish per owner (2026-06-11).** Toggle ON against it → connects over wss/https
      (webcam + thumbnails ride the same scheme).
- [ ] **R7 cert-failure message (best-effort, self-signed endpoint if handy):** **DEFERRED to
      Phase 29 polish per owner (2026-06-11).** The Splash shows "TLS certificate not trusted —
      check the Moonraker reverse-proxy certificate" — the distinct trust message, not the generic
      "Can't reach the printer". Retry + Edit connection both offered.
      Note: roadmap §R7's "cleartext to a public IP refused on API 24+" half was DESCOPED — NSC
      cannot express RFC-1918 CIDR; the decision record lives in
      `app/src/main/res/xml/network_security_config.xml` (confirm the comment tells that story).

### CI wrap-up

- [ ] **R5a CI green:** the GitHub Actions run on the final `gsd/phase-26.5-overnight-hardening`
      push is GREEN — unit tests + assembleDebug + lintDebug (baseline-enforced) + hygiene job
      (stale-phrase grep + dead-link check). This also proves the fresh-clone `./gradlew` acceptance
      (the ubuntu runner IS a fresh clone exercising the +x gradlew). If red: triage the failing
      step (likely action-version or SDK-presence assumptions, RESEARCH A2/A3).
