---
phase: 01-platform-gate-toolkit-benchmark-cleartext-smoke-test-scaffol
reviewed: 2026-05-30T00:00:00Z
depth: standard
files_reviewed: 21
files_reviewed_list:
  - app/build.gradle.kts
  - app/proguard-rules.pro
  - app/src/androidTest/java/works/mees/dinghy/smoke/CleartextMoonrakerSmokeTest.kt
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/works/mees/dinghy/MainActivity.kt
  - app/src/main/java/works/mees/dinghy/bench/BenchActivity.kt
  - app/src/main/java/works/mees/dinghy/bench/ComposeBenchScene.kt
  - app/src/main/java/works/mees/dinghy/bench/SyntheticFeed.kt
  - app/src/main/java/works/mees/dinghy/bench/SyntheticThumbnail.kt
  - app/src/main/java/works/mees/dinghy/bench/ViewsBenchScene.kt
  - app/src/main/res/values/themes.xml
  - app/src/main/res/xml/network_security_config.xml
  - build-logic/build.gradle.kts
  - build-logic/settings.gradle.kts
  - build-logic/src/main/kotlin/verify-min-sdk.gradle.kts
  - gradle/libs.versions.toml
  - macrobenchmark/build.gradle.kts
  - macrobenchmark/src/main/AndroidManifest.xml
  - macrobenchmark/src/main/java/works/mees/dinghy/macrobenchmark/ToolkitBenchmark.kt
  - tools/gfxinfo-parser/parse_framestats.py
findings:
  critical: 2
  warning: 7
  info: 5
  total: 14
status: issues_found
---

# Phase 1: Code Review Report

**Reviewed:** 2026-05-30
**Depth:** standard
**Files Reviewed:** 21
**Status:** issues_found

## Summary

This phase stands up the build scaffold: a minSdk-23 platform gate (`verifyMinSdk`), a Compose-vs-Views benchmark harness, a throwaway cleartext Moonraker smoke probe, and a Python framestats parser. The version-pinning / minSdk-gate machinery is well thought-out and correct. The cleartext NSC posture is a documented, accepted trusted-LAN decision (D-11) and is treated as Info per the phase brief, not a vuln.

The real defects cluster in the **benchmark harness fairness and validity** — the thing this phase exists to produce. Two findings are BLOCKER-class because they silently invalidate the toolkit verdict the whole phase is meant to decide: (1) the `:app` release variant is non-debuggable and non-profileable, so Macrobenchmark cannot actually measure it; (2) the Compose and Views scenes apply the graph/console rolling-window logic differently from each other on the first frame, breaking the byte-for-byte fairness the harness claims (D-02). There are also several leaks and a couple of harness correctness bugs.

## Critical Issues

### CR-01: Release `:app` is non-debuggable and non-profileable — Macrobenchmark cannot measure it

**File:** `app/build.gradle.kts:39-49`, `macrobenchmark/build.gradle.kts:38-61`

**Issue:** The macrobenchmark module declares its `benchmark` and `release` build types with `matchingFallbacks += listOf("release")`, intending to measure `:app`'s **release** variant (the comments say so repeatedly, and `ToolkitBenchmark` uses `FrameTimingMetric`). But `:app`'s `release` build type sets `isDebuggable = false` and declares **no** `<profileable android:shell="true"/>` manifest tag and **no** `isProfileable = true` in the build type. AndroidX Macrobenchmark **requires the target app to be either debuggable or profileable** to collect frame metrics; against a plain non-debuggable release it throws at runtime (`The androidx.benchmark.macro library requires the target application to be profileable or debuggable`). The harness as wired will not run against the artifact it claims to measure — the phase's entire toolkit decision rests on a benchmark that cannot execute.

**Fix:** Add a profileable manifest entry gated to the release-measurement path, or mark the app build type profileable. Minimal manifest approach:
```xml
<!-- app/src/main/AndroidManifest.xml, inside <application> -->
<profileable android:shell="true" tools:targetApi="29" />
```
Note `<profileable android:shell>` requires API 29+, so on the actual API-23 Nexus 7 it is a no-op and Macrobenchmark's `FrameTimingMetric` cannot work there anyway — which is exactly why the class header says gfxinfo CSV is the system of record. That is fine for the *on-device* path, but it means the Gradle-driven `:macrobenchmark` run can only ever succeed on an API-29+ device/emulator. Decide explicitly: either (a) make `:app` profileable so the macro harness runs on a modern device for corroboration, or (b) drop the `release`/`benchmark` matchingFallback to `release` and benchmark a `profileable`/`debuggable` variant, and document that the macro module is a no-op on the target. As written the wiring is internally contradictory.

### CR-02: Compose and Views scenes diverge on the first frame — fairness contract (D-02) is broken

**File:** `app/src/main/java/works/mees/dinghy/bench/BenchActivity.kt:57-110`

**Issue:** The two scenes are supposed to see byte-identical state so the only difference measured is the toolkit. They do not. `mountComposeScene()` seeds the `MutableStateFlow` with an **initial** `ComposeSceneState` built from `feed.replay().first()` (event index 0) *before* the collecting coroutine starts, and that same coroutine then **also** collects event index 0 again from `feed.events()` (which calls `replay()` a second time and emits from the start). So the Compose scene processes event 0 twice: once as the seed, once as the first collected event. `mountViewsScene()` has **no** such pre-seed — it only renders events as they are collected. Net effect:
- The Compose graph/console `ArrayDeque` is fed event 0 once (the seed snapshot is overwritten by the first collected value, but the seed's `graphHistory`/`console` differ in length from the deque-built ones), while the Views deque is fed event 0 once with no pre-seed.
- More concretely, the Compose initial `console` uses `initial.consoleLines.takeLast(CONSOLE_MAX)` while the deque path appends every line and trims — different windowing semantics on the same data.

The first measured frames therefore render different content per toolkit. Since the benchmark's `INITIAL_DWELL_MS` captures exactly those early frames, this contaminates the comparison the phase exists to make.

**Fix:** Drive both scenes through one shared state-derivation function fed only by `feed.events()`, with no separate pre-seed for Compose. E.g. extract the deque/window loop into a single `suspend fun drive(emit: (FeedEvent, List<GraphSample>, List<String>) -> Unit)` and have both `mountComposeScene` and `mountViewsScene` call it, so the rolling-window logic is literally the same code:
```kotlin
private fun CoroutineScope.driveFeed(
    feed: SyntheticFeed,
    onState: (FeedEvent, List<GraphSample>, List<String>) -> Unit,
) = launch {
    val graph = ArrayDeque<GraphSample>()
    val console = ArrayDeque<String>()
    feed.events().collect { event ->
        graph.addLast(event.graphSample)
        while (graph.size > GRAPH_MAX) graph.removeFirst()
        event.consoleLines.forEach { console.addLast(it) }
        while (console.size > CONSOLE_MAX) console.removeFirst()
        onState(event, graph.toList(), console.toList())
    }
}
```
Seed the Compose `StateFlow` with an empty/placeholder state, not with `replay().first()`.

## Warnings

### WR-01: `BenchImageLoader` pins an Activity `Context` in a process-lifetime static field (leak)

**File:** `app/src/main/java/works/mees/dinghy/bench/SyntheticThumbnail.kt:140-156`

**Issue:** `BenchImageLoader` is an `object` with a `@Volatile var instance: ImageLoader?` built from whatever `context` first calls `get()`. Both callers pass an Activity-derived context (`LocalContext.current` in Compose, `holder.row.context` in the RecyclerView). Coil's `ImageLoader.Builder(context)` retains that context. Because the singleton lives for the whole process and is never cleared, the **first** `BenchActivity` instance leaks for the life of the process across activity recreation (rotation, scene relaunch between benchmark iterations — and `MacrobenchmarkScope` cold-launches `BenchActivity` 5× per scene). On the 2 GB target this is exactly the memory pressure the benchmark is sensitive to, so the leak also biases later iterations.

**Fix:** Use the application context: `ImageLoader.Builder(context.applicationContext)` — or since these are Coil `PlatformContext`, resolve the application context before building. At minimum build from `context.applicationContext` so no Activity is retained.

### WR-02: Smoke test leaks the OkHttp connection pool / dispatcher idle threads

**File:** `app/src/androidTest/java/works/mees/dinghy/smoke/CleartextMoonrakerSmokeTest.kt:174-177`

**Issue:** The `finally` block shuts down `client.dispatcher.executorService` but never evicts the connection pool (`client.connectionPool.evictAll()`) and the websocket `close(1000, …)` only initiates a graceful close — the socket may not be torn down before the test process moves on. For a single throwaway test this rarely matters, but the REST calls in `assertRestInfo` open pooled connections that linger with keep-alive threads. Instrumented test runs that reuse the process across tests can accumulate these.

**Fix:** In `finally` also call `client.connectionPool.evictAll()` and `client.cache?.close()` (no cache here, but harmless). Belt-and-suspenders for a probe.

### WR-03: Smoke test step-3 race — subscribe ack and the deterministic update can be missed if they arrive before `send()` returns

**File:** `app/src/androidTest/java/works/mees/dinghy/smoke/CleartextMoonrakerSmokeTest.kt:99-167`

**Issue:** This is correctly handled (latches are created before the listener is attached, and the websocket is created with the listener already wired), so the ack/update cannot be lost to a registration gap — good. The genuine fragility is different: `subscribeAcked` and `deterministicUpdate` are **also** counted down in `onFailure`. If the socket fails *after* a legitimate ack but the failure fires before `deterministicUpdate.await`, the code counts down `deterministicUpdate`, then `await` returns `true`, then the `socketFailure.get()?.let { fail(...) }` check catches it — so order matters. As written the `fail` check runs *after* the `await`, so a failure is still surfaced. This is OK but brittle: a failure that arrives between the two `await` calls' `fail` checks could let a `true` slip through if the second `socketFailure` read races the `set`. Low probability, but the latch-as-both-success-and-failure-signal pattern is a footgun.

**Fix:** Use a dedicated failure latch or check `socketFailure.get()` *before* trusting any `await` result, and prefer a single `CountDownLatch` per phase whose meaning is unambiguous. Not a blocker for a throwaway probe, but flag for the real Phase-2 socket so the pattern isn't copied.

### WR-04: `FilesAdapter.submit` uses `notifyDataSetChanged()` while the Compose scene uses keyed diffing — unfair toolkit comparison

**File:** `app/src/main/java/works/mees/dinghy/bench/ViewsBenchScene.kt:149-152` vs `app/src/main/java/works/mees/dinghy/bench/ComposeBenchScene.kt:104`

**Issue:** The Compose `LazyColumn` uses `items(files, key = { it.id })`, so Compose only rebinds/redecodes rows whose identity changed — stable keys let it skip unchanged rows. The Views `FilesAdapter.submit` calls `notifyDataSetChanged()` ("worst-case: full rebind churn (intentional stress)"), forcing **every** visible row to rebind and **re-enqueue a Coil decode every frame**. The comment frames this as intentional, but it makes the two scenes do structurally different work: Compose decodes only new thumbnails, Views re-decodes all visible thumbnails on every feed tick. That is not a toolkit difference — it is a harness-authored asymmetry that will make Views look worse for reasons unrelated to the rendering toolkit. This undermines the fairness premise (D-02) just like CR-02.

**Fix:** Either give the Compose side an equally naive full-invalidation path, or (better) give Views a `DiffUtil`/keyed update so both do identity-stable diffing. The fair comparison is "same diffing strategy, different toolkit," not "keyed Compose vs nuke-everything Views."

### WR-05: `SyntheticThumbnail.cache` grows unbounded across the whole run

**File:** `app/src/main/java/works/mees/dinghy/bench/SyntheticThumbnail.kt:79-83`

**Issue:** `private val cache = HashMap<Int, ByteArray>()` keyed by seed, never evicted. Each entry is an encoded PNG (the comment sizes the in-flight bitmap at ~1 MB; the PNG bytes are smaller but non-trivial). `thumbSeed` is `rng.nextInt()`, and the file list churns (`FILE_MUTATE_EVERY = 7` inserts a new file with a fresh random seed up to `MAX_FILE_COUNT = 80`, then evicts old ones from the *list* but never from this cache). Over a 600-event run that is up to ~600/7 ≈ 85 distinct new seeds plus the initial 60 — ~145 cached PNG byte arrays held for the process lifetime. On a 2 GB device under a benchmark specifically probing memory pressure, an unbounded harness-side cache pollutes the measurement.

**Fix:** Bound the cache (LRU `object : LinkedHashMap(…, true) { removeEldestEntry }` capped at, say, `MAX_FILE_COUNT + small slack`), or drop the cache entirely and re-encode (the comment says encode is the slow part, so a small bounded LRU is the right call).

### WR-06: `verifyMinSdk` regex matches the *first* `minSdkVersion` in the merged manifest, which may not be the effective floor

**File:** `build-logic/src/main/kotlin/verify-min-sdk.gradle.kts:54-67`

**Issue:** The assertion does `Regex("""android:minSdkVersion="(\d+)"""").find(text)` and takes the first match. A merged manifest normally has a single `<uses-sdk>`, but manifests can legally contain `minSdkVersion` attributes elsewhere (e.g. inside `<uses-sdk>` plus tooling artifacts, or `tools:overrideLibrary` comments, or a `<uses-library>`-adjacent attribute injected by a dependency). More importantly, `minSdkVersion` can be a **named API level string** (e.g. `"M"` for 23 on preview SDKs) rather than digits — `(\d+)` would then **fail to match** and throw "could not find android:minSdkVersion," a false failure. For the merged release manifest with compileSdk 35 this is unlikely, but the parser is the load-bearing PKG-02 control and should be robust.

**Fix:** Prefer parsing the XML (`XmlSlurper`/`groovy.xml` or `javax.xml`) and read the `uses-sdk/@minSdkVersion` attribute specifically, mapping codename letters to integers, rather than a greedy first-regex-match over raw text.

### WR-07: `ConsoleSpew` renders all lines in a plain `Column`, not a scrolling/clipped container — can overflow and drop frames inconsistently vs Views

**File:** `app/src/main/java/works/mees/dinghy/bench/ComposeBenchScene.kt:177-192`

**Issue:** `ConsoleSpew` lays out up to `CONSOLE_MAX = 40` `Text` lines in a `Column` with `weight(1f)`. If the 40 lines exceed the available height they are simply clipped by the parent, and the `if (i == lines.lastIndex) Spacer(Modifier.height(0.dp))` line is dead code (a zero-height spacer does nothing). Meanwhile the Views console is a single `TextView` with `gravity = BOTTOM` showing the joined string — it bottom-aligns and the OS handles overflow differently. Two different overflow behaviors again make the scenes render differently for the same data.

**Fix:** Remove the no-op `Spacer` (dead code). For fairness, make both consoles use the same overflow discipline — e.g. both bottom-anchored, both clipped identically — or accept the difference but document it. The zero-height spacer should go regardless.

## Info

### IN-01: App-wide cleartext NSC is over-broad (accepted/deferred risk D-11)

**File:** `app/src/main/res/xml/network_security_config.xml:16-18`, `app/src/main/AndroidManifest.xml:24-25`

**Issue:** `base-config cleartextTrafficPermitted="true"` permits cleartext to **any** host on API 24+, and `android:usesCleartextTraffic="true"` does the same on API 23. Per the phase brief this is a deliberate, documented trusted-LAN decision (CONN-05) and an explicitly deferred-tightening risk (D-11) — recorded here as Info, not a vuln. No action this phase.

**Fix:** As already noted in the file's own comment, a later phase should scope this to a `<domain-config>` for the Moonraker host(s) once the connection layer knows the host.

### IN-02: Smoke test default host `192.168.1.50` is a placeholder committed in source

**File:** `app/src/androidTest/java/works/mees/dinghy/smoke/CleartextMoonrakerSmokeTest.kt:58-61`

**Issue:** A LAN IP placeholder is committed as the default. It is overridden by instrumentation args and documented as a placeholder, and it is in a throwaway test — not a secret. Noted only because a committed default IP can mask a missing-arg misconfiguration (the test would silently target the wrong host and "fail" for the wrong reason rather than erroring on a missing required arg).

**Fix:** Consider failing fast if `moonrakerHost` is absent (`requireNotNull(arg)`) so a forgotten arg is an obvious config error, not a confusing connect timeout.

### IN-03: `percentile` import of `math` inside the function body

**File:** `tools/gfxinfo-parser/parse_framestats.py:108-118`

**Issue:** `import math` sits inside `percentile()`. Harmless (Python caches the import) but unusual; move it to the module top with the other imports for clarity. Not a bug.

**Fix:** Hoist `import math` to the top of the file.

### IN-04: `parse_framestats.py` excludes flagged frames by default, which silently drops the very janky frames the gate cares about

**File:** `tools/gfxinfo-parser/parse_framestats.py:94-99, 47-50`

**Issue:** Frames with non-zero `Flags` are excluded unless `--include-flagged`. The docstring justifies this (first-draw/layout-changed), and the warmup window also trims leading frames. But a frame that **janks badly** can carry platform flags (e.g. `FLAG_WINDOW_LAYOUT_CHANGED`), so the default could undercount the `frames > 700 ms` frozen-frame metric that is the whole point of the gate. This is a defensible default, but worth a conscious check: confirm that genuinely-janky frames on the Nexus 7 don't carry Flags that would hide them. Not a code bug — a methodology note.

**Fix:** When reporting the verdict, also run with `--include-flagged` and confirm the frozen-frame count doesn't materially change; document which run is authoritative.

### IN-05: Empty/whitespace-only input is handled, but a CSV with frames that all fail the `completed > intended` guard reports "no rows parsed" ambiguously

**File:** `tools/gfxinfo-parser/parse_framestats.py:97-99, 181-184`

**Issue:** Rows where `intended <= 0` or `completed <= intended` are skipped (correct — guards against malformed/zero rows and avoids negative durations). But if *every* row is skipped for that reason, `main` prints "no framestats rows parsed (is this a 'gfxinfo … framestats' dump?)" — which misdiagnoses a valid-but-degenerate dump as a wrong-input error. The percentile/`compute_stats` path correctly returns `nan`/handles empty, so no crash. Division-by-zero is also avoided (`percentile` checks empty, `compute_stats` guards `times[-1] if times else nan`). Minor UX wrinkle only.

**Fix:** Distinguish "found CSV rows but all were filtered out" from "found no CSV rows at all" in the error message.

---

_Reviewed: 2026-05-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
