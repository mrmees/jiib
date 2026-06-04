# Phase 11: Spool Management — Spoolman + Camera QR - Research

**Researched:** 2026-06-04
**Domain:** Android camera/QR-scan stack (CameraX + ZXing, GMS-free, minSdk 23) + Moonraker/Spoolman integration (already live-validated)
**Confidence:** HIGH

## Summary

The Spoolman/Moonraker half of this phase is **fully de-risked**: `docs/view_specific_notes/spoolman*.md` plus 23 verbatim live fixtures (Spoolman 0.22.1 @ `192.168.1.253:7912`; E5 active=5, E3 active=1) are the ground-truth API contract. The proxy `use_v2_response=true` envelope `{response, error, response_headers}` with `X-Total-Count` in headers, the JSON-RPC active-spool surface (`server.spoolman.status/get_spool_id/post_spool_id/proxy`), the two notifications (`notify_active_spool_set`, `notify_spoolman_status_changed`), material family-matching, the two-step color-similarity flow, and the save→change→restore safety loop are all proven. Treat those notes as locked; do not re-research them.

The **genuine unknown — the camera stack — is now resolved with two concrete minSdk-23 landmines identified and dispositioned:**

1. **CameraX version landmine.** CameraX `1.4.2` (latest 1.4.x) = minSdk 21, compileSdk 34. CameraX `1.5.0+` (incl. `1.6.1`, latest stable) moved its floor to **minSdk 23** AND requires **compileSdk 35**. `[VERIFIED: developer.android.com/jetpack/androidx/releases/camera]` Our project is already at compileSdk 35 / minSdk 23, so **CameraX 1.5.x/1.6.x is safe and is the recommended pin** — minSdk 23 == our exact floor, no transitive raise. (The widely-cited "CameraX 1.5.0-beta requires minSdk 35" GitHub title is wrong/misleading — the authoritative AndroidX changelog says minSdk 23.) The `verifyMinSdk` task will *prove* this on the merged manifest; if any camera-view transitive surprises us above 23, the deliberate fallback is the **Camera2 API** directly (no AndroidX camera deps), which is unconditionally available since API 21.

2. **ZXing decode-path Java-8 landmine (the real trap).** `com.google.zxing:core` **3.4.0+** crashes on Android **< API 24** *on the QR decode path* — `FinderPatternFinder.java:616` calls `List.sort(Comparator)`, a default interface method that doesn't exist on API 23's runtime (`NoSuchMethodError`). `[CITED: github.com/zxing/zxing/issues/1170]` Two clean fixes, **pick one and make it a locked decision**: **(A, recommended)** pin `com.google.zxing:core:3.3.3` (pure Java 7, decodes fine on API 23 with no build changes), or **(B)** keep `core:3.5.x` and enable `coreLibraryDesugaring` (`com.android.tools:desugar_jdk_libs`) which backports `List.sort`. Option A is the leaner, lower-risk choice for this project (no desugaring toolchain change, no R8 interaction surprises) and is what most GMS-free Android QR projects on old floors do.

**Primary recommendation:** Hand-roll a lean `QrCodeAnalyzer : ImageAnalysis.Analyzer` over **`com.google.zxing:core:3.3.3`** (GMS-free, no `journeyapps/zxing-android-embedded` — it bundles its own capture Activity/`DecoratedBarcodeView` that fights our Compose+Views shell and adds weight). Drive it with **CameraX 1.5.x** (`camera-core`/`camera-camera2`/`camera-lifecycle`/`camera-view`), `STRATEGY_KEEP_ONLY_LATEST`, `DEFAULT_BACK_CAMERA` first (do NOT hard-code front), continuous-autofocus, `PreviewView` hosted via `AndroidView`, released on pause/background/nav-away exactly like the Phase-10 webcam `DisposableEffect`. Everything else (card, picker, gate, notifications) follows the live-validated Spoolman contract verbatim.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions (D-01 … D-15)
- **D-01 — Print-start gate = WARN-ONLY, never block.** Every condition (incl. *no active spool*) surfaces an amber "proceed-at-peril" warning the user taps past in one action: no-spool → `Pick spool`/`Scan`/`Print anyway`/`Back`; material mismatch (family-matched per D-05) naming both sides; remaining < needed(+margin); archived active spool; non-empty `pending_reports` (remaining may be stale); fetch-failed → warn+retry/override. Missing `filament_weight_total` → *skip* the low-filament check (no length/density fallback this phase). A full pass shows the normal `Print file` confirm unchanged.
- **D-02 — New top-level `Dest.Spool`** + a capability-greyed drawer tile (mirrors Webcam D-08 gating). The Spool screen owns picker/detail/set-clear/change. The **QR scan surface is a full-screen sub-surface** launched from the Spool screen or the Status card (NOT its own drawer Dest), released on pause/background/nav-away.
- **D-03 — Compact active-spool card on Print Status** (material / color swatch / vendor·name / remaining / state) as confidence indicator + primary launcher (`Scan`/`Change`/`Clear`). NOT a database row.
- **D-04 — All four nice-to-haves IN this one-shot:** gcode-aware picker prefilter (by file `filament_type[]` + `filament_colors[]`); location shortcuts ("At this printer" + "No location" from `/v1/location`); archived-spool warning (allow-with-warning per D-09); measured gross-weight correction (`PUT /v1/spool/{id}/measure`, gross grams, UI must show `remaining`/`used` are linked: `used = initial − remaining`).
- **D-05 — Material matching = family (partial, case-insensitive).** `PLA` matches `PLA+`, `PLA Meta`, etc. Multi-family chips send comma-separated terms (`ABS,ASA`). NOT typo-tolerant fuzzy. Same policy for picker chips AND print-start mismatch checks. (Live-proven: `filament.material=PLA` → 7 rows; `PLA+PETG` → 0; `"PLA"` quoted → 0.)
- **D-06 — Color filter = Spoolman's similarity endpoint, two-step:** `GET /v1/filament?color_hex=…&color_similarity_threshold=20` → fetch spools by returned `filament.id` list. Trigger only on swatch tap (slow op). Fixed palette swatches; no local RGB matcher. Always render the spool's **actual** swatch on every result row.
- **D-07 — Moonraker proxy `use_v2_response=true`** is the inventory transport — envelope `{response, error, response_headers}`; `X-Total-Count` in `response_headers`. Direct Spoolman REST is fallback only. Dotted query keys (`filament.material`) must be URL-encoded by the client layer. **Active-spool get/set/status/clear go via the existing Moonraker JSON-RPC session** (`server.spoolman.*`), NOT the proxy.
- **D-08 — All Spoolman response fields optional / null-safe.** Color display-normalized & never trusted: accept `color_hex` with/without `#`, require 6 or 8 hex digits post-normalize else neutral unknown marker; render `multi_color_hexes` as split swatch. `extra` is `Map<String,String>` of JSON-encoded values — parse only agreed local keys, tolerate invalid JSON, never let a bad field break a card/row. (No `extra` keys agreed → badges deferred.)
- **D-09 — Archived = allow-with-warning** (not blocked) for both the print-start gate and a scanned/selected archived spool.
- **D-10 — External-change reconciliation is mandatory.** Dinghy is not the only mutator (Fluidd/Mainsail, runout macros, `spoolman_set_active_spool`). Route `notify_active_spool_set` + `notify_spoolman_status_changed`; reconcile card/picker/open-confirmation to the new id rather than overwriting with stale local state. (`params` is a 1-element array.)
- **D-11 — Pending-reports = stale-not-lost.** Non-empty `pending_reports` → "usage queued"; treat remaining as potentially stale; **never** call `/use` manually to compensate.
- **D-12 — QR is an id carrier, not a navigation target.** Parse the id, fetch via configured Moonraker/Spoolman path; never open the scanned URL or switch hosts. `web+spoolman:f-<id>` / other schemes → "unsupported Spoolman code". Manufacturer UPC/EAN → "Not a Spoolman spool code", stay in scan flow. **Confirm-first: never auto-load on decode.**
- **D-13 — Clear/unload** = `POST server.spoolman.set_spool_id {}`. Optional location-update tap (move old spool to shelf) — optional, never automatic. Location is inventory context, not active-printer truth.
- **D-14 — ZXing (pure-Java, GMS-free)** decoder (no Play Services / no ML Kit). **CameraX** for preview/analysis (verify minSdk-23; document Camera2 fallback if CameraX pulls >23). **Do NOT hard-code "front camera"** — fixture-prove on the physical device; front lens may be fixed-focus/poor at close labels, rear/autofocus may be the only reliable path. Throttle analysis frames but keep resolution high enough to decode the real label at scan distance. **Release the camera on pause/background/nav-away** (short-lived task surface, Phase-10 discipline).
- **D-15 — Camera-permission flow requested gracefully;** no-camera / permission-denied / busy / unreadable / no-QR-found / unsupported-payload all degrade to a clear state with an escape path — **manual picker always works without the camera.**

### Claude's Discretion
- Low-remaining **safety margin** added to `filament_weight_total` (small fixed buffer / %); warning-only (D-01), so pick a sensible constant. *(Research recommendation: a small percentage + a floor, e.g. `max(filament_weight_total * 1.05, filament_weight_total + 10g)`.)*
- Default picker list shape (unarchived, `sort=filament.material:asc,…,id:asc`, `limit=50`) and which secondary chips render in row 1 vs behind "refine".
- Whether to persist per-printer picker prefs vs derive each session (**lean: derive each session**; only persist if a concrete need emerges).
- Exact `SpoolmanClient` shape (small proxy client, NOT forced through `CommandDispatcher` early) and where the session-owned facade hangs off `SpineHandle` / `AppContainer`.
- Portrait vs landscape collapse of the picker Focus/Field per the UI LAW.

### Deferred Ideas (OUT OF SCOPE)
Full Spoolman CRUD admin UI; filament/vendor creation; bulk inventory intake; manufacturer UPC/EAN → inventory creation; NFC/OpenPrintTag/RFID intake; multi-material/toolchanger/AMS per-lane active-spool (single active spool only this phase); drying/calibration `extra` badges (no agreed local schema — do not invent); "use last spool for this file" from history; lot/article/`extra` server-side filters (endpoints empty on live install); WebRTC camera (Phase-10 SC-4, unrelated to QR).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SPOOL-01 | Umbrella: Spoolman spool mgmt + camera QR-scan-to-assign | All sections below; stays Pending until on-device UAT closes |
| SPOOL-02 | Active-spool card on Print Status | "Active Spool Card" §; live fixture `…-status-before-set.json` + proxy `/v1/spool/{id}`; null-safe data model |
| SPOOL-03 | Picker + filters in `Dest.Spool` | "Spool Picker And Filters" §; proxy fixtures (pla/abs-asa/color/vendors/materials/locations/recent); D-05/D-06 |
| SPOOL-04 | Set/clear/change active via Moonraker JSON-RPC | "Active-Spool Set/Restore" live proof; `server.spoolman.post_spool_id`; D-07/D-13 |
| SPOOL-05 | QR scan-to-assign, ZXing GMS-free | **Camera Stack** § (this phase's core research); D-12/D-14; parser contract |
| SPOOL-06 | Camera permission + no-camera degrade | **Camera Permission & Degrade States** §; D-15; Activity Result API |
| SPOOL-07 | Print-start warn-only gate in Files | "Print-Start Spool Gate" §; D-01 decision table; `FilePreviewMetadata` extension |
| SPOOL-08 | External-change reconciliation + notify routing + pending staleness | "Notification Routing" §; live notify fixture; D-10/D-11; JsonRpcClient |
| SPOOL-09 | Measured-weight correction + nice-to-haves | "Correction Actions" §; `PUT /v1/spool/{id}/measure`; D-04; spool-3 before/after-restore fixtures |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Active-spool truth (get/set/clear/status) | Moonraker JSON-RPC session | — | Moonraker owns "this printer has spool X"; per-printer state (E5=5, E3=1 same Spoolman server) |
| Inventory reads (spool/filament/material/vendor/location) | Moonraker `spoolman.proxy` (v2) | Direct Spoolman REST (fallback) | D-07; avoids a second base-URL config; one connection |
| Inventory writes (measure/patch location) | Moonraker proxy → Spoolman REST | Direct REST (fallback) | Spoolman owns inventory; correction is inventory bookkeeping |
| QR decode (frame → spool id) | On-device (CameraX + ZXing) | Manual picker (no-camera degrade) | Camera is local hardware; decode is pure-Java; id then routes through Moonraker |
| Capability gate (`spoolman` present) | `Capabilities.hasComponent("spoolman")` | — | Same gate Webcam used; greys the drawer tile |
| Notification fan-out | `JsonRpcClient` router | derived flows on `AppContainer`/`SpineHandle` | Push reconciliation (D-10); router already owns notify routing |

## Standard Stack

### Core (NEW for this phase — camera/QR)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.camera:camera-core` | **1.5.x** (pin a concrete patch; 1.5.0 floor moved to minSdk 23 == our floor) | CameraX core | minSdk 23 exactly; compileSdk 35 (we're there); GMS-free. `[VERIFIED: developer.android.com/jetpack/androidx/releases/camera]` |
| `androidx.camera:camera-camera2` | match core | Camera2 backend impl | Required CameraX runtime backend |
| `androidx.camera:camera-lifecycle` | match core | `bindToLifecycle` | Lifecycle-bound camera (auto-release on stop) — mirrors Phase-10 discipline |
| `androidx.camera:camera-view` | match core | `PreviewView` | Hosted via `AndroidView` in the Compose shell |
| `com.google.zxing:core` | **3.3.3** (NOT 3.4.0+) | Pure-Java QR decoder | GMS-free, zero deps; **3.4.0+ crashes the DECODE path on API 23** (`List.sort`) `[CITED: github.com/zxing/zxing/issues/1170]` |

**CameraX patch pin:** Pin a concrete patch from the 1.5.x or 1.6.x line in `libs.versions.toml` (e.g. `cameraX = "1.5.0"` or the current `1.6.1`); do NOT use `+`. Verify the chosen patch's merged-manifest minSdk via the existing `verifyMinSdk` gate before locking — that task FAILS the build if anything raises the merged floor off 23, so it is the authoritative proof, not the changelog.

### Supporting (already pinned, reuse)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| OkHttp | 4.12.0 (pinned) | Spoolman proxy/REST + existing ws | Reuse the one client; `SpoolmanClient` rides the existing transport |
| kotlinx.serialization | 1.7.3 (pinned) | All Spoolman JSON (null-safe) | `JsonElement` walk for loose/partial Spoolman payloads; `@Serializable` for the modeled parts |
| Coil 3 | 3.1.0 (pinned) | (Optional) any spool imagery | Spoolman has no spool images in scope; swatches are drawn, not loaded |
| DataStore Preferences | 1.1.7 (pinned) | (Discretion) per-printer picker prefs | Only if a concrete persist-need emerges; lean = derive each session |
| Activity Result API | via `androidx.activity` 1.9.3 (pinned) | Runtime CAMERA permission | `rememberLauncherForActivityResult(RequestPermission())` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| `zxing:core:3.3.3` (plain) | `zxing:core:3.5.x` + `coreLibraryDesugaring` | Desugaring backports `List.sort` for API 23, but adds `desugar_jdk_libs` to the toolchain + an R8 interaction surface. **3.3.3 is leaner and lower-risk** for this project. Use 3.5.x+desugar only if a 3.3.3 decode bug surfaces. |
| Hand-rolled `QrCodeAnalyzer` over `zxing:core` | `journeyapps/zxing-android-embedded` | Embedded bundles its own `DecoratedBarcodeView`/`CaptureManager`/capture Activity + a Camera2/CameraX surface — it fights our Compose+Views shell and the full-screen single-focus task-surface grammar, and adds weight. Project prefers lean GMS-free deps (CLAUDE.md). **Hand-roll.** |
| CameraX 1.5.x | CameraX 1.4.2 | 1.4.2 = minSdk 21 / compileSdk 34. Works, but compileSdk 34 ≠ our pinned 35 and 1.4.x is the older line. 1.5.x is minSdk-23-native at our compileSdk. **Pin 1.5.x.** |
| CameraX (any) | Raw Camera2 API (no AndroidX camera dep) | **The documented fallback** if `verifyMinSdk` ever shows a CameraX transitive raising the merged floor >23. Camera2 is unconditional since API 21 but is ~3× the boilerplate (manual session/surface/AF). Only if CameraX proves incompatible on the merged manifest. |
| ZXing | ML Kit barcode | ML Kit needs Google Play Services — **absent on the Nexus 7 floor**. Hard-no (D-14). |

**Installation (Gradle, version catalog):**
```toml
# gradle/libs.versions.toml  [versions]
cameraX = "1.5.0"      # pin concrete; verify via verifyMinSdk (minSdk 23 == floor)
zxingCore = "3.3.3"    # NOT 3.4.0+ — decode path uses List.sort (API 24) above 3.3.3
```
```kotlin
// app/build.gradle.kts dependencies
implementation(libs.androidx.camera.core)
implementation(libs.androidx.camera.camera2)
implementation(libs.androidx.camera.lifecycle)
implementation(libs.androidx.camera.view)
implementation(libs.zxing.core)
```
**Run `verifyMinSdkRelease` after adding the CameraX deps** — it asserts the merged-manifest minSdk is EXACTLY 23 and is the real proof the floor held (the build-logic precompiled plugin). If it fails, drop to the Camera2 fallback.

## Package Legitimacy Audit

> All packages are first-party Google/AndroidX or the canonical ZXing org, from Maven Central / Google's maven (NOT npm/PyPI). slopcheck 0.6.1 targets npm/PyPI ecosystems and does not cover Maven coordinates, so registry verification here is "is this the canonical first-party coordinate" — which is unambiguous for these.

| Package | Registry | Provenance | Source Repo | Disposition |
|---------|----------|-----------|-------------|-------------|
| `androidx.camera:camera-core` | Google Maven | First-party AndroidX (official CameraX) | github.com/androidx/androidx | Approved `[VERIFIED: developer.android.com/jetpack/androidx/releases/camera]` |
| `androidx.camera:camera-camera2` | Google Maven | First-party AndroidX | github.com/androidx/androidx | Approved `[VERIFIED]` |
| `androidx.camera:camera-lifecycle` | Google Maven | First-party AndroidX | github.com/androidx/androidx | Approved `[VERIFIED]` |
| `androidx.camera:camera-view` | Google Maven | First-party AndroidX | github.com/androidx/androidx | Approved `[VERIFIED]` |
| `com.google.zxing:core` | Maven Central | Canonical ZXing project (Google org) | github.com/zxing/zxing | Approved — **pin 3.3.3** (3.4.0+ breaks API 23 decode) `[CITED: github.com/zxing/zxing/issues/1170]` |
| `com.android.tools:desugar_jdk_libs` *(only if Option B chosen)* | Google Maven | First-party Google R8/D8 desugaring | github.com/google/desugar_jdk_libs | Approved (canonical) — only needed if keeping zxing 3.5.x |

**Packages removed due to slopcheck [SLOP] verdict:** none.
**Packages flagged [SUS]:** none. (`zxing:core` ≥3.4.0 is not "suspicious" — it's a legitimate version with a documented API-23 runtime incompatibility; pinning 3.3.3 is the correct mitigation, not a security flag.)

## Architecture Patterns

### System Architecture Diagram

```
                        ┌─────────────────────────────────────────────┐
   QR label  ──scan──▶  │  Full-screen Scan Surface (sub-surface)     │
   (printed)           │  CameraX PreviewView (AndroidView)           │
                        │   └─ ImageAnalysis(KEEP_ONLY_LATEST,         │
                        │        back cam, continuous-AF)              │
                        │        └─ QrCodeAnalyzer (zxing:core 3.3.3)  │
                        │             YUV plane[0] → PlanarYUV-        │
                        │             LuminanceSource → HybridBinarizer│
                        │             → MultiFormatReader(QR hint)     │
                        └───────────────┬─────────────────────────────┘
                                        │ decoded text
                              ┌─────────▼──────────┐
                              │ QrPayloadParser    │  web+spoolman:s-<id> (case-insens.)
                              │ (D-12)             │  | http(s)://…/spool/show/<id>
                              └─────────┬──────────┘  → spoolId  (reject f-/UPC/EAN/non-numeric)
                                        │ spoolId
   Manual picker ──pick spoolId──┐      │
   (no-camera degrade, D-15)     ▼      ▼
                        ┌──────────────────────────┐   GET /v1/spool/{id}
                        │ SpoolmanClient            │──(proxy v2)──▶ Spoolman 0.22.1
                        │  (Moonraker spoolman.proxy│◀── {response,error,response_headers}
                        │   use_v2_response=true)   │      X-Total-Count in headers
                        └─────────┬─────────────────┘
                                  │ confirm card (material/color/vendor/remaining/archived)
                                  │ user taps Load
                                  ▼
                        ┌──────────────────────────┐  server.spoolman.post_spool_id {spool_id}
   Status card ◀────────│ Active-spool facade       │──(JSON-RPC)──▶ Moonraker (per-printer)
   Picker      ◀── push─│ (SpineHandle/AppContainer)│◀── notify_active_spool_set [{spool_id}]
   Open confirm◀── push─│  reconcile (D-10)         │◀── notify_spoolman_status_changed
                        └──────────────────────────┘
   Files print-confirm ──pre-print gate (D-01 warn-only)──▶ reads active-spool detail + file metadata
```

### Recommended Project Structure (new files under existing package)
```
ui/spool/                 # Dest.Spool screen, picker, detail, set/clear/change
  SpoolScreen.kt          #   Files-style dense list (reuse FilesScreen scroll lesson)
  SpoolPicker.kt          #   chips (material family / color palette / location / vendor)
  ActiveSpoolCard.kt      #   compact Status card (D-03)
  scan/
    ScanSurface.kt        #   full-screen CameraX surface (AndroidView + DisposableEffect release)
    QrCodeAnalyzer.kt     #   ImageAnalysis.Analyzer over zxing:core
    QrPayloadParser.kt    #   D-12 parser (headless, unit-tested)
    CameraPermission.kt   #   Activity Result API + degrade states
spool/                    # headless feature layer (toolkit-agnostic)
  SpoolmanClient.kt       #   proxy v2 client (URL-encode dotted keys)
  SpoolmanModels.kt       #   null-safe data model (see spoolman.md)
  SpoolmanParsers.kt      #   v2-envelope + spool/filament/vendor parsers (fixture-golden)
  ActiveSpoolFacade.kt    #   session-owned; JSON-RPC get/set/clear/status + notify reconcile
state/PrintMetadata.kt    # EXTEND FilePreviewMetadata: filament_type[]/name[]/colors[]/weights[]
```

### Pattern 1: Lean CameraX + ZXing analyzer (GMS-free)
**What:** An `ImageAnalysis.Analyzer` converts the YUV `ImageProxy` plane[0] (luma) to a ZXing `PlanarYUVLuminanceSource`, wraps in `HybridBinarizer`, decodes with `MultiFormatReader` hinted to QR only.
**When to use:** The scan surface (SPOOL-05).
```kotlin
// Source: pattern per developer.android.com/media/camera/camerax/analyze + sasikanth.dev/qr-scanning-using-camerax
// [CITED: developer.android.com/media/camera/camerax/analyze]
class QrCodeAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }
    override fun analyze(image: ImageProxy) {
        // plane[0] = luminance (Y). YUV_420_888 is CameraX default.
        val buffer = image.planes[0].buffer
        val data = ByteArray(buffer.remaining()).also { buffer.get(it) }
        val source = PlanarYUVLuminanceSource(
            data, image.planes[0].rowStride, image.height,
            0, 0, image.width, image.height, false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        try { onResult(reader.decodeWithState(bitmap).text) }
        catch (_: NotFoundException) { /* no QR this frame — normal */ }
        catch (_: Throwable) { /* unreadable frame — ignore, keep scanning */ }
        finally { image.close() }   // MUST close, or KEEP_ONLY_LATEST stalls
    }
}
```
Pipeline wiring: `ImageAnalysis.Builder().setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)` (throttle = analyze latest, drop stale — D-14), bound with `CameraSelector.DEFAULT_BACK_CAMERA` first. Keep analysis resolution reasonably high (don't down-res below what decodes a printed label at arm's length). `rowStride` (not `width`) is the source row length — a common bug if you pass `width`.

### Pattern 2: Lifecycle release (mirror Phase-10 webcam discipline)
**What:** `bindToLifecycle` ties the camera to the surface's lifecycle; a Compose `DisposableEffect` unbinds/releases on dispose. Same shape as `WebcamScreen.kt`'s `DisposableEffect(holder){ onDispose{ holder.stop() } }`.
```kotlin
DisposableEffect(lifecycleOwner) {
    val provider = ProcessCameraProvider.getInstance(ctx).get()
    provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
    onDispose { provider.unbindAll() }   // release on pause/background/nav-away (D-14)
}
```
The camera is a short-lived task surface, NOT a persistent stream — released the instant the scan surface leaves composition. (Note: the Phase-10 webcam is network-only; this is the first on-device hardware camera, so the release discipline is the *pattern*, not the *code*, to reuse.)

### Pattern 3: Camera permission via Activity Result API
```kotlin
val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
    state = if (granted) ScanState.Active else ScanState.PermissionDenied
}
// onEnter: if checkSelfPermission(CAMERA) granted → Active; else launcher.launch(CAMERA)
```
All denial/no-camera paths route to a degrade state with `Use picker instead` (D-15).

### Anti-Patterns to Avoid
- **Hard-coding `DEFAULT_FRONT_CAMERA`** — D-14 explicitly forbids; front lens may be fixed-focus on Nexus-7-class hardware. Start back, fixture-prove on flox.
- **Forgetting `image.close()`** in the analyzer — `KEEP_ONLY_LATEST` stalls (no new frames) if the proxy isn't closed.
- **Passing `image.width` as the luminance row length** — must be `planes[0].rowStride` (padding differs).
- **`zxing:core:3.4.0+` without desugaring** — silently crashes the decode path on API 23. The mock/emulator (API 30+) will NOT catch this; only a real API-23 device or the version pin does. (6 prior mock-vs-reality strikes — this is exactly that class of bug.)
- **Auto-loading on decode** — D-12: confirm-first, always.
- **Trusting the QR host as a Spoolman base URL** — D-12: id carrier only.
- **Treating `spool.location` as active-printer truth** — inventory context only.
- **Treating proxy v2 `error: null` as "no data"** — `error` null = success; the payload is in `response`.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| QR decode | Custom finder-pattern/Reed-Solomon decoder | `com.google.zxing:core:3.3.3` | Decades-hardened; pure-Java; GMS-free |
| Camera session/surface/AF | Raw Camera2 session management | CameraX 1.5.x | Camera2 is the *fallback*, not the default — CameraX handles lifecycle/AF/rotation |
| Color similarity | Local RGB nearest-match | Spoolman `color_similarity_threshold` endpoint (D-06) | Server owns it; local matcher would diverge from Spoolman's own results |
| Material family match | Fuzzy string lib | Spoolman partial case-insensitive filter (D-05) | API already does family matching; comma-terms for multi-family |
| Pagination total | Counting rows client-side | `X-Total-Count` in proxy `response_headers` (D-07) | Live-proven header; rows are capped by `limit` |
| Usage accounting | Manual `/use` calls | Moonraker reports usage (D-11) | Double-counting risk; Dinghy is not the consumption reporter |
| Spool JSON parsing | Strict `@Serializable` everywhere | Null-safe `JsonElement` walk + optional fields (D-08) | Spoolman omits null fields; strict decode would throw |

**Key insight:** Both halves of this phase are "let the upstream own its domain." Spoolman owns inventory/color/material/usage; ZXing owns decode; CameraX owns the camera. Dinghy's job is plumbing + null-safety + the warn-only gate — not reimplementing any of it.

## Runtime State Inventory

> Not a rename/refactor phase — this section is **not applicable** (greenfield feature additions). No stored strings, OS registrations, or secret-key renames are involved. The only persistent state touched is DataStore picker prefs (discretionary, lean = derive each session). New AndroidManifest entries (CAMERA permission + `uses-feature camera required=false`) are additive, not renames.

## Common Pitfalls

### Pitfall 1: ZXing decode crash on API 23 (the headline trap)
**What goes wrong:** `zxing:core` 3.4.0+ throws `NoSuchMethodError: List.sort` mid-decode on Android < API 24.
**Why it happens:** `FinderPatternFinder.java:616` uses the Java-8 `List.sort` default method, absent from API 23's runtime. `[CITED: github.com/zxing/zxing/issues/1170]`
**How to avoid:** Pin `core:3.3.3` (Java 7, decodes clean on 23). Alternative: 3.5.x + `coreLibraryDesugaring`.
**Warning signs:** Decode works on the API-30 flox / emulator but the *true* floor (genuine API 23) would crash. Because flox is API 30, **this won't surface in normal UAT** — the version pin is the only reliable guard. Add a unit test that decodes a known QR with the pinned ZXing on the JVM to lock the version.

### Pitfall 2: CameraX silently raising the merged minSdk
**What goes wrong:** A future CameraX bump (or a beta) raises the merged-manifest minSdk above 23.
**Why it happens:** Transitive AARs can declare higher `<uses-sdk>`; AGP merges to the max.
**How to avoid:** Pin a concrete 1.5.x/1.6.x patch; **run `verifyMinSdkRelease`** (the existing build gate) — it FAILS the build off 23.
**Warning signs:** `verifyMinSdk` task failure naming the offending dep → drop to Camera2 fallback.

### Pitfall 3: `ImageAnalysis` stalls — no frames decoded
**What goes wrong:** The analyzer fires once or twice then goes silent.
**Why it happens:** `ImageProxy` not closed; with `KEEP_ONLY_LATEST` the queue stops delivering.
**How to avoid:** `image.close()` in a `finally` every frame.

### Pitfall 4: Front camera can't focus on a close label
**What goes wrong:** Scan works on a phone, fails on the tablet's front cam (fixed-focus).
**Why it happens:** Nexus-7-class front lenses are often fixed-focus, poor at close range.
**How to avoid (D-14):** Default to `DEFAULT_BACK_CAMERA` with continuous-AF; offer a lens flip; fixture-prove on flox at real scan distance. Document which lens actually decodes the printed label.

### Pitfall 5: Dotted query keys dropped by the HTTP layer
**What goes wrong:** `filament.material=PLA` filter returns everything (filter ignored).
**Why it happens:** Some clients mangle dots in query-param names.
**How to avoid (D-07):** URL-encode dotted keys in `SpoolmanClient` before handing to the proxy; assert against the live fixtures (`…-proxy-pla.json` expects `X-Total-Count: 7`).

### Pitfall 6: External active-spool change clobbered by stale local UI
**What goes wrong:** Fluidd/runout macro changes the spool; Dinghy's open picker overwrites it back.
**Why it happens:** Treating Dinghy as the sole mutator.
**How to avoid (D-10):** Route `notify_active_spool_set` (`params` is a **1-element array**), reconcile open UI to the pushed id. Live fixture `…-notify.json` carries `[{spool_id:3}]` / `[{spool_id:5}]`.

### Pitfall 7: `remaining`/`used` treated as independent in the correction UI
**What goes wrong:** User sets remaining; `used` "magically" changes; looks like a bug.
**Why it happens:** Spoolman computes `used = initial − remaining`. (Live-proven on spool 3: set 579.1 → used 420.9.)
**How to avoid (D-04):** Correction UI must show the two are linked.

## Code Examples

### AndroidManifest additions (camera, optional so no-camera devices install)
```xml
<!-- Source: developer.android.com/guide/topics/manifest/uses-feature-element [CITED] -->
<uses-feature android:name="android.hardware.camera" android:required="false" />
<uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />
<uses-permission android:name="android.permission.CAMERA" />
```
`required="false"` is load-bearing (D-15): a device with no camera still installs and runs; the scan tile/button degrades to "manual picker only".

### Moonraker active-spool set (JSON-RPC, live-proven)
```json
// Source: docs/commands/spoolman-live-ender5-status-before-set.json + live validation [VERIFIED: live capture 2026-06-04]
// set:   server.spoolman.post_spool_id  params {"spool_id": 3}   → {"result":{"spool_id":3}}
// clear: server.spoolman.post_spool_id  params {}                (D-13)
// status:server.spoolman.status         → {"spoolman_connected":true,"pending_reports":[],"spool_id":5}
```

### Proxy v2 inventory read (live-proven envelope)
```json
// Source: docs/commands/spoolman-live-ender5-proxy-pla.json [VERIFIED: live capture]
// server.spoolman.proxy params:
{ "use_v2_response": true, "request_method": "GET",
  "path": "/v1/spool",
  "query": "filament.material=PLA&allow_archived=false&limit=50&sort=filament.name:asc" }
// → response: { "response": [ …spool rows… ], "error": null,
//               "response_headers": { "X-Total-Count": "7" } }
```

### QR payload parser contract (D-12)
```kotlin
// Accept (case-insensitive): WEB+SPOOLMAN:S-<digits>  AND  http(s)://<host>/spool/show/<digits>
// Return spoolId (Int). REJECT: web+spoolman:f-<id> / other schemes → "unsupported Spoolman code";
//   UPC/EAN / non-numeric / no /spool/show/ → "Not a Spoolman spool code". NEVER use the host.
// Unit-test all four accept/reject classes (live source confirms uppercase WEB+SPOOLMAN:S-{id}).
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Camera1 / SurfaceView preview | CameraX `PreviewView` + `ImageAnalysis` | CameraX 1.0 (2021) | Lifecycle-bound, rotation-handled, less boilerplate |
| ML Kit barcode | ZXing pure-Java (GMS-free) | n/a (forced by no-GMS floor) | No Play Services dependency — required here |
| `zxing-android-embedded` capture Activity | Hand-rolled analyzer over `zxing:core` | n/a (project preference) | Fits Compose+Views shell; lean dep graph |
| Spoolman proxy v1 (direct/deprecated) | proxy `use_v2_response=true` | Moonraker recent | Distinguishes Moonraker vs Spoolman errors; headers exposed |

**Deprecated/outdated:**
- `zxing:core` ≥ 3.4.0 on minSdk 23 without desugaring — incompatible (decode crash).
- CameraX 1.4.x for *this* project — works but compileSdk 34 ≠ our 35; 1.5.x is the minSdk-23-native line.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The exact CameraX patch (1.5.0 vs 1.6.1) holds merged-minSdk == 23 | Standard Stack | LOW — `verifyMinSdk` proves it at build time; if it fails, pick another patch or Camera2 fallback. Pin is the gate, not this assumption. |
| A2 | `zxing:core:3.3.3` decodes the real printed Spoolman label on flox at scan distance | Pattern 1 / Pitfall 1 | MEDIUM — must fixture-prove on-device; if 3.3.3 has a decode gap, fall to 3.5.x+desugar (Option B). Version is recoverable. |
| A3 | flox's rear camera autofocuses adequately on a close QR label | Pitfall 4 | MEDIUM — D-14 calls this out; fixture-prove. If rear fails too, offer lens flip + document the working lens. |
| A4 | `PlanarYUVLuminanceSource` over `planes[0]` (luma) is sufficient (no full YUV→RGB) | Pattern 1 | LOW — standard ZXing-on-CameraX approach; luma-only is the documented path. |
| A5 | Hand-rolled analyzer is preferable to `zxing-android-embedded` for this shell | Alternatives | LOW — aligns with CLAUDE.md lean-dep preference; embedded remains a fallback if the hand-roll proves fiddly. |

**Recommendation:** A2/A3 are the two that MUST be closed by the on-device UAT (real lens + real label + real ZXing version). A1 is closed by `verifyMinSdk` at build time.

## Open Questions

1. **ZXing 3.3.3 decode quality on a low-res Adreno-320 frame**
   - What we know: 3.3.3 is API-23-safe; `HybridBinarizer` is ZXing's recommended binarizer.
   - What's unclear: whether the throttled analysis resolution + flox lens decodes the actual printed label reliably.
   - Recommendation: bake a flox decode check into UAT; if marginal, raise analysis resolution before changing libs.

2. **Catalog gap: the two Spoolman notifications**
   - What we know: `docs/commands/moonraker-api.md` lists the four `server.spoolman.*` methods but NOT `notify_active_spool_set` / `notify_spoolman_status_changed`.
   - Recommendation: add catalog/spec entries (with the live `…-notify.json` shape) BEFORE wiring the router's parser tests (per spoolman.md step 3).

3. **CameraX patch choice (1.5.0 vs 1.6.1)**
   - Recommendation: prefer the **lowest** 1.5.x that is minSdk-23 + compileSdk-35 (less new-API churn, matches the project's "stay off the bleeding edge" posture), unless 1.6.1 fixes a concrete bug. Either way, `verifyMinSdk` is the gate.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Spoolman server | Inventory reads | ✓ | 0.22.1 @ 192.168.1.253:7912 | — (capability-gated if absent) |
| Moonraker `spoolman` component | Active-spool + proxy | ✓ (E5 + E3) | both connected | Greys the Spool tile (D-02) |
| On-device camera (flox) | QR scan | ✓ (genuine Adreno-320 hw) | API 30 (LineageOS 18.1) | Manual picker (D-15) |
| Windows build (`E:\Android\gw.bat`) | Build/install | ✓ | JDK 21 / SDK 35 / build-tools 34 | — |
| `verifyMinSdk` gate | Floor proof | ✓ (build-logic plugin) | exact-23 assertion | — |
| Codex (gpt-5.5) | Final-plan review (MEMORY policy) | ✓ | `~/.local/bin/codex` | Manual review |

**Missing dependencies with no fallback:** none.
**Missing dependencies with fallback:** none blocking — both printers + Spoolman are live and free for the save→change→restore protocol.

## Validation Architecture

> Nyquist validation is enabled (`workflow.nyquist_validation` not false). Test seams below derive VALIDATION.md.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 (JVM unit tests) + AndroidJUnitRunner (instrumented) — both pinned |
| Config file | `app/build.gradle.kts` test/androidTest source sets (no separate config) |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| Full suite command | `… "E:\Android\gw.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest --no-daemon"` (guard with `timeout` + taskkill per MEMORY interop note) |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SPOOL-02/08 | proxy v2 envelope parser (`{response,error,response_headers}`, `X-Total-Count`) | unit (golden) | `…testDebugUnitTest --tests *SpoolmanProxyParserTest` | ❌ Wave 0 |
| SPOOL-02 | null-safe spool/filament/vendor parser (omitted fields, color normalize, multi-color) | unit (golden) | `…--tests *SpoolmanModelParserTest` | ❌ Wave 0 |
| SPOOL-04/08 | notify router fans out `notify_active_spool_set`/`…status_changed`, ignores unrelated | unit | `…--tests *SpoolmanNotifyRouterTest` | ❌ Wave 0 |
| SPOOL-05 | QR payload parser — 4 accept/reject classes (s-/URL/f-/UPC/non-numeric, case-insens.) | unit | `…--tests *QrPayloadParserTest` | ❌ Wave 0 |
| SPOOL-05 | ZXing 3.3.3 decodes a known QR on the JVM (version-lock guard, Pitfall 1) | unit | `…--tests *ZxingDecodeVersionTest` | ❌ Wave 0 |
| SPOOL-07 | warn-only gate decision table (each D-01 condition → expected warning/pass) | unit | `…--tests *PrintStartGateTest` | ❌ Wave 0 |
| SPOOL-07 | `FilePreviewMetadata` lifts `filament_type[]`/`name[]`/`colors[]`/`weights[]` | unit | `…--tests *FilePreviewMetadataTest` (extend) | ❌ Wave 0 (extend existing) |
| SPOOL-06 | camera permission/degrade state machine (granted/denied/no-camera/busy) | unit (headless state) | `…--tests *ScanStateMachineTest` | ❌ Wave 0 |
| SPOOL-03/06 | scan surface releases camera on dispose; no-camera → picker | instrumented | `connectedDebugAndroidTest *ScanSurfaceLifecycleTest` | ❌ Wave 0 |
| SPOOL-05/06 | **on-device end-to-end:** scan real label → confirm → active flips (UAT) | manual (flox + live printer) | save→change→restore protocol | UAT |

### Sampling Rate
- **Per task commit:** quick unit run (parser/gate/state-machine seams).
- **Per wave merge:** full unit suite + relevant instrumented.
- **Phase gate:** full suite green + on-device UAT (real lens, real ZXing version, real label) before `/gsd-verify-work`. Then Codex final-plan review per MEMORY policy.

### Wave 0 Gaps
- [ ] `SpoolmanProxyParserTest` + golden from `spoolman-live-ender5-proxy-pla.json` (assert `X-Total-Count == "7"`)
- [ ] `SpoolmanModelParserTest` + goldens from `…-proxy-spool3.json` (null-safety, color normalize, multi-color split)
- [ ] `SpoolmanNotifyRouterTest` + golden from `…-notify.json` (1-element array, ignore `notify_proc_stat_update`)
- [ ] `QrPayloadParserTest` (the 4 accept/reject classes incl. uppercase `WEB+SPOOLMAN:S-`)
- [ ] `ZxingDecodeVersionTest` (locks the 3.3.3 pin — decodes a fixture QR on JVM)
- [ ] `PrintStartGateTest` (D-01 decision table, all conditions)
- [ ] `ScanStateMachineTest` (headless permission/degrade states)
- [ ] `FakeSpoolmanClient` / `FakeMoonrakerSession` hardened to the live fixtures (mock-vs-reality — 7th-strike prevention)
- [ ] Wave-0 RED stubs MUST compile (fail() bodies, no refs to unbuilt symbols — MEMORY note; bad scaffold bricks the whole test sourceset)
- [ ] Catalog entries for the two notifications added before parser tests (Open Q2)

## Security Domain

> `security_enforcement` enabled, `security_asvs_level` 1, `block_on: high`.

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | partial | Moonraker optional API-key/trusted-client (existing session); no new auth surface |
| V3 Session Management | no | Reuses the existing Moonraker session |
| V4 Access Control | no | Local-network control surface; no multi-user model |
| V5 Input Validation | **yes** | QR payload is **untrusted input** — strict parse (D-12), id is `Int`-validated, host never used, scheme allow-listed; Spoolman fields null-safe (D-08) |
| V6 Cryptography | no | No new crypto; cleartext LAN posture is the existing, deliberate project decision |
| V12 Files/Resources | partial | gcode metadata arrays parsed defensively (extend `FilePreviewMetadata`); tolerate malformed |

### Known Threat Patterns for {CameraX + ZXing + LAN REST}
| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Malicious/odd QR payload (URL with hostile host, non-spoolman scheme) | Tampering / Spoofing | D-12: parse id only, never navigate/trust host, allow-list `s-`/`/spool/show/`, reject everything else |
| QR injection of a huge/garbage id → bad fetch | Tampering | Validate `Int`; `GET /spool/{id}` 404 → "spool not found" state, no crash |
| Camera permission over-grant / leak | Info disclosure | Request CAMERA only on scan entry; release on dispose; `uses-feature required=false`; no frame persistence (decode-in-memory, never stored) |
| Spoolman field with invalid JSON in `extra` | DoS (UI crash) | D-08: tolerate invalid JSON, never let a field break a row/card |
| External active-spool race clobber | Tampering (state) | D-10: reconcile to pushed notify id, don't overwrite |
| Live-test writes to real printer | Tampering (real device) | Mandatory save→change→restore protocol (CONTEXT §Live-Test Safety); no delete calls in automated UAT |

No `high`-severity blockers identified — QR untrusted-input handling (D-12) is the load-bearing control and is already a locked decision.

## Sources

### Primary (HIGH confidence)
- `developer.android.com/jetpack/androidx/releases/camera` — CameraX 1.4.x=minSdk 21; **1.5.0+=minSdk 23 + compileSdk 35**; latest stable 1.6.1 — the version landmine resolution.
- `github.com/zxing/zxing/issues/1170` — `zxing:core` 3.4.0+ decode-path crash on API < 24 (`List.sort` @ `FinderPatternFinder.java:616`); fix = downgrade or desugar.
- `developer.android.com/media/camera/camerax/analyze` + `/configuration` — `ImageAnalysis.Analyzer`, `STRATEGY_KEEP_ONLY_LATEST`, `DEFAULT_BACK_CAMERA`, `startFocusAndMetering`.
- `docs/view_specific_notes/spoolman*.md` + 23 `docs/commands/spoolman-live-*.json` fixtures — the live-validated Spoolman/Moonraker contract (ground truth, 2026-06-04).
- Codebase: `libs.versions.toml`, `app/build.gradle.kts`, `AndroidManifest.xml`, `JsonRpc.kt`, `CommandSpec.kt`, `PrintMetadata.kt`, `TopRoute.kt`, `AppDrawer.kt`, `ui/webcam/*`, `build-logic/.../verify-min-sdk.gradle.kts`.

### Secondary (MEDIUM confidence)
- `sasikanth.dev/qr-scanning-using-camerax` + `medium.com/@msasikanth/...` — the canonical CameraX+ZXing `PlanarYUVLuminanceSource` analyzer pattern.
- `mvnrepository.com/artifact/com.google.zxing/core` — `core` latest 3.5.4 (2025-11-11); 3.3.x line for API-23 safety.

### Tertiary (LOW confidence — flagged for on-device validation)
- The exact decode reliability of 3.3.3 + flox rear lens on the real printed label (A2/A3) — must be UAT-proven, not assertable from docs.

## Metadata

**Confidence breakdown:**
- Spoolman/Moonraker contract: HIGH — exhaustively live-validated, 23 fixtures, save→restore proven.
- Camera stack (versions/landmines): HIGH — CameraX minSdk + ZXing decode-crash both pinned to authoritative sources.
- On-device decode reality (lens/label/ZXing version): MEDIUM — pattern is standard; the specific flox+label decode must be UAT-proven (A2/A3).
- Architecture/integration: HIGH — codebase integration points all verified by direct read.

**Research date:** 2026-06-04
**Valid until:** ~2026-07-04 (CameraX/ZXing versions are stable; re-check only if bumping the build quartet or a new CameraX stable lands)
