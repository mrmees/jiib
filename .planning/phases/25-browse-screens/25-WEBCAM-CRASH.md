# 25-WEBCAM-CRASH.md — Webcam Screen Crash Diagnosis (D-17)

**Captured:** 2026-06-10 (Task 1 of Plan 25-06)
**Method:** adb logcat capture + on-device reproduction attempt + git log forensics

---

## On-Device Reproduction Result

The crash described in `.planning/todos/pending/2026-06-05-webcam-screen-crash.md` was
**already fixed in Phase 21 (commit `49f3fe2`)** before this plan ran.

On-device test on flox (LineageOS 18.1 / API 30 / Adreno 320):
1. Installed current debug APK (`app-armeabi-v7a-debug.apk`, HEAD: `85106f0`).
2. Launched app, opened App Drawer via swipe-up, tapped Webcam tile.
3. **Webcam screen opened successfully** — live feed from `c270_hd_webcam` rendered.
4. No `FATAL`/`AndroidRuntime` crash in `adb logcat -v time *:E`.
5. App process `works.mees.dinghy` (PID 10526) remained alive throughout.

The screen showed the cam-picker chrome (`c270_hd_webcam` label) and a live camera feed
in the Focus region. The only errors in logcat were OMX vendor extension queries from the
H.264 decoder (these are expected noise from `OMX.qcom.video.decoder.avc`).

---

## Original Crash (Pre-Fix)

**Original crash report:** Opened during Phase 15.2-06 app-wide conformance sweep (2026-06-05).
**Status at time of report:** Undiagnosed — no stack trace captured; deferred to Phase 20.
**Deferred resolution:** Captured and fixed during Phase 21 on-device UAT (2026-06-08).

### Stack Trace (from Phase-21 investigation — see `21-05-SUMMARY.md`)

```
java.lang.IllegalArgumentException: Invalid URL host: ""
    at okhttp3.HttpUrl.Builder.host(HttpUrl.kt)
    at works.mees.dinghy.net.WebcamUrl.resolveWebcamUrl(WebcamUrl.kt)
    ... (on Dispatchers.IO)
```

The full trace was captured during Phase-21 on-device UAT. The exception fired on
`Dispatchers.IO` inside the `WebcamHolder`'s URL-resolution path.

### Root Cause

**Empty-host `ConnectionConfig` → unparseable HTTP base → `IllegalArgumentException` crash.**

Path:
1. `AppShell.kt:212–213` reads `container.connectionStore.config` — the **write-dead Phase-14
   source** (DataStore writes stopped updating this after Phase 14 migrated to `activeConfig`).
2. An empty-host fallback (`cfg ?: ConnectionConfig(host = "")`) produced a `ConnectionConfig`
   with `host = ""`.
3. `ConnectionConfig.httpBase` with an empty host yields `"http://:7125"`.
4. `resolveWebcamUrl(raw, cfg)` called `cfg.httpBase.toHttpUrl()` — `okhttp3` threw
   `IllegalArgumentException: Invalid URL host: ""` on `Dispatchers.IO`.
5. Unhandled exception crashed the process.

**This is the WR-02 bug** — `connectionStore.config` is write-dead since Phase 14; the correct
source is `container.activeConfig` which reads the live active profile's connection config.

### Fix Applied (Phase 21, commit `49f3fe2`)

```
fix(21): resolveWebcamUrl returns null on unparseable base instead of throwing
```

Changed `resolveWebcamUrl` in `WebcamUrl.kt`:

```kotlin
// BEFORE (throws on empty host):
val base = cfg.httpBase.toHttpUrl()  // IllegalArgumentException "Invalid URL host: \"\""

// AFTER (graceful degrade — returns null, rung falls to DeadEnd):
val base = cfg.httpBase.toHttpUrlOrNull() ?: return null
```

This killed the crash: an empty-host config now produces `null` (no usable URL) rather than
throwing. The `WebcamHolder` treats `null` as no usable URL → the feed falls to the DeadEnd
rung (no player built) → the screen displays a "no feed" state without crashing.

**Regression test:** Added in `21-05` (`WebcamUrlTest.resolveWebcamUrl_emptyHostConfig_returnsNull`).

---

## WR-02 Latent Bug (Still Present at Plan 25-06 Start)

Even after the Phase-21 crash fix, `AppShell.kt:212–213` **still reads from the write-dead
`connectionStore.config`** instead of `container.activeConfig`.

```kotlin
// AppShell.kt lines 212–213 (WR-02 — write-dead source):
val cfg by container.connectionStore.config.collectAsStateWithLifecycle(initialValue = null)
val activeCfg = cfg ?: ConnectionConfig(host = "")

// CORRECT source (live active-profile config, Plan 25-06 Task 2 fix):
val activeCfg by container.activeConfig.collectAsStateWithLifecycle(initialValue = null)
```

The Phase-21 crash fix was a `resolveWebcamUrl` defensive guard (graceful-degrade), NOT a
fix of the stale-source read. The stale read means:
- A webcam URL may be resolved against an EMPTY/stale host even when a valid profile is active
- On the real device this manifests as "no feed" (DeadEnd) when a live printer has cameras
- On a device with a valid profile, it may work coincidentally if `connectionStore.config`
  happened to get updated in an earlier run

**The Plan 25-06 Task 2 fix** corrects the root cause: point the resolver at
`container.activeConfig` (the live Phase-14 source) instead of `connectionStore.config`.

---

## Summary

| Attribute | Value |
|-----------|-------|
| Original crash | `IllegalArgumentException: Invalid URL host: ""` on `Dispatchers.IO` |
| Root cause | `AppShell.kt` reads write-dead `connectionStore.config`; empty host → unparseable URL |
| Crash fix | Phase 21 `49f3fe2`: `resolveWebcamUrl` returns null on unparseable base |
| Status at 25-06 start | Screen no longer crashes; WR-02 stale-read latent bug still present |
| Task-2 fix | Replace `connectionStore.config` with `container.activeConfig` in AppShell webcam region |

**Diagnosis drives the Task-2 fix: fix WR-02 (the stale-source read) as planned.**
