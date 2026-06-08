---
phase: 21
slug: webrtc-camera-streaming
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-08
---

# Phase 21 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `21-RESEARCH.md` § Validation Architecture: the load-bearing
> gates for this phase are **on-device UAT on both printers** (latency, decode,
> no-leak). Green host unit tests CANNOT close this phase — they cover only the
> pure logic (rung selection, URL derive-from-convention, lifecycle state,
> outcome mapping).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 + Robolectric (host unit tests, `app/src/test`) |
| **Config file** | `app/build.gradle.kts` (existing test sourceset) |
| **Quick run command** | `E:\Android\gw.bat :app:testDebugUnitTest --tests "*Webcam*" --no-daemon` |
| **Full suite command** | `E:\Android\gw.bat :app:testDebugUnitTest --no-daemon` |
| **Estimated runtime** | ~{N} seconds (planner to confirm) |

*(Build runs Windows-side via `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat …"` — see CLAUDE.md Local Build Environment.)*

---

## Sampling Rate

- **After every task commit:** Run quick command (`*Webcam*` filter)
- **After every plan wave:** Run full unit suite
- **Before `/gsd-verify-work`:** Full host suite green + on-device UAT on both printers (flox)
- **Max feedback latency:** {N} seconds (planner to set)

---

## Per-Task Verification Map

*Planner populates this from PLAN tasks. Mark UAT-only criteria (latency, decode, no-leak, "proven on real printer") in the Manual-Only table below, not here.*

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 21-01-01 | 01 | 1 | CAM-{XX} | — | {expected} | unit | `{command}` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] H.264 rung-selection test stubs (heuristic-selects, decoder-verifies — D-10)
- [ ] URL derive-from-convention test stubs (`:8889`→`:8554`/`:8888`, parse path segment from stream_url NOT name — RESEARCH landmine)
- [ ] ExoPlayer lifecycle/release state stubs (leak-free release on screen exit — mirror `WebcamHolder.cancel()`)

*Wave-0 RED stubs MUST compile day-one (see project memory: wave-0 RED scaffold compile rule).*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Glass-to-glass latency meets ~1–2 s bar (D-05) | CAM-{XX} | Inherently on-device; no verified source pins it (D-02 spike settles) | Run spike on flox vs both printers; measure per spike harness |
| Live H.264 renders with correct aspect, no frozen frames | CAM-{XX} | Requires real MediaMTX camera + hardware decode on Adreno 320 | On-device UAT on Ender 5 Plus + Ender 3 |
| Clean codec/player release on screen exit (no leaked PeerConnection/codec) | CAM-{XX} | Leak observable only at runtime | Enter/exit webcam screen repeatedly; confirm no codec leak |
| Falls back to MJPEG/snapshot rungs when H.264 not offered | CAM-{XX} | Cross-camera behavior | UAT on a non-H.264 cam |
| No-crash regression on the MediaMTX cams that previously crashed the screen (D-20) | CAM-{XX} | Reproduces only against the real cams | On-device UAT both printers |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < {N}s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
