# Testing Patterns

**Analysis Date:** 2026-06-08

## Test Framework

**Runner:**
- JUnit 4 (`@RunWith(AndroidJUnit4::class)` for instrumented; bare `@Test` for host)
- Config: no standalone config file — standard AGP test sourcesets (`app/src/test/`, `app/src/androidTest/`)

**Coroutine testing:**
- `kotlinx-coroutines-test` — `runTest`, `UnconfinedTestDispatcher`, `advanceTimeBy`, `runCurrent`, `advanceUntilIdle`, `backgroundScope`

**Assertion library:**
- `org.junit.Assert.*` — `assertEquals`, `assertTrue`, `assertFalse`, `assertNotNull`, `assertNull`
- No third-party assertion library (no Truth, no AssertJ)

**Instrumented Compose testing:**
- `androidx.compose.ui.test.junit4.createComposeRule()`
- `onNodeWithText`, `performClick`, `performTouchInput { swipeUp() }`, `assertIsDisplayed`

**Run Commands:**
```bash
# All host unit tests (via WSL → Windows build helper)
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:test --no-daemon"

# Single test class
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:test --tests works.mees.dinghy.net.HandshakeTest --no-daemon"

# Instrumented tests on connected device
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:connectedAndroidTest --no-daemon"
```

---

## Test File Organization

**Location:** Separate from production source — standard Android sourcesets:
- Host tests: `app/src/test/java/works/mees/dinghy/`
- Instrumented tests: `app/src/androidTest/java/works/mees/dinghy/`
- Debug-only source: `app/src/debug/java/works/mees/dinghy/` (GalleryActivity only)

**Naming convention:** `<ProductionClass>Test.kt` mirrors the production package path exactly:
- `works/mees/dinghy/net/JsonRpcClientTest.kt` tests `JsonRpcClient.kt`
- `works/mees/dinghy/ui/finetune/FineTuneHolderTest.kt` tests `FineTuneHolder.kt`

**Shared test infrastructure files** (not `*Test.kt` — no `@Test` methods):
- `app/src/test/java/works/mees/dinghy/net/FakeWebSocket.kt` — OkHttp fake
- `app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt` — session harness
- `app/src/test/java/works/mees/dinghy/net/GoldenFixtures.kt` — fixture loader
- `app/src/test/java/works/mees/dinghy/spool/FakeMoonrakerSpoolmanSession.kt`
- `app/src/test/java/works/mees/dinghy/spool/FakeSpoolmanClient.kt`
- `app/src/test/java/works/mees/dinghy/webcam/FakeMjpegStream.kt`
- `app/src/test/java/works/mees/dinghy/webcam/FakeWebcamHttp.kt`
- `app/src/test/java/works/mees/dinghy/prompt/PromptFixtures.kt`

---

## Test Resources

**Fixture directories under `app/src/test/resources/`:**
```
fixtures/           — captured E5/E3 Moonraker JSON payloads (bed_mesh, proc_stats, etc.)
                     + binary MJPEG stream fixtures
golden/             — golden frames used by GoldenFixtures loader
  fallback_*.json   — synthetic fallbacks when live goldens are absent (autonomous mode)
  adversarial_*.json — error-path / race-condition golden frames
  objects_list.json / objects_query_snapshot.json — primary session fixtures
  spoolman-live-*.json — Spoolman API capture fixtures
outputs/            — OutputsHolder test fixtures
prompt/             — PromptEngine test fixtures
```

**`GoldenFixtures` loader** (`app/src/test/java/works/mees/dinghy/net/GoldenFixtures.kt`):
- `raw(name)` — returns raw string from `/golden/<name>`
- `resolve(liveName)` — uses live capture if present, falls back to `fallback_<liveName>` (autonomous build)
- Tests that depend on live printer goldens work both with and without the captured JSON files

---

## Test Structure

**Host test suite organization:**
```kotlin
class FineTuneHolderTest {
    // helper factory for Capabilities (no @Before setup class)
    private fun caps(vararg objects: String): Capabilities = Capabilities(objects = objects.toSet())

    @Test
    fun vm_scales_ratio_to_percent() = runTest(UnconfinedTestDispatcher()) {
        val store = PrinterStateStore(backgroundScope)
        val holder = FineTuneHolder(backgroundScope, store)
        store.setCapabilities(caps("gcode_move"))
        store.seed(PrinterState(speedFactor = 1.05, ...))
        runCurrent()

        val vm = holder.vm.value
        assertEquals("speedFactor 1.05 -> speedPct 105", 105, vm.speedPct)
    }
}
```

**Key structural patterns:**
- `runTest(UnconfinedTestDispatcher())` is the standard for all coroutine-driven holder/session tests
- `backgroundScope` provided by `runTest` is passed to holders and stores (mirrors production DI)
- `runCurrent()` after seeding state to allow coroutines to process before asserting
- `advanceTimeBy(ms)` for time-dependent tests (reconnect backoff, temperature store conflation)
- No `@Before`/`@After` setup class — test data constructed inline in each test function

**Instrumented test structure:**
```kotlin
@RunWith(AndroidJUnit4::class)
class ShellPresenceTest {
    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private val app: DinghyApp = ApplicationProvider.getApplicationContext()
    private val container get() = app.container

    @After fun tearDown() { ... } // clean up spine publication
}
```

---

## Mocking

**Primary network fake:** `FakeWebSocket` (open class, extends `okhttp3.WebSocket`):
```kotlin
open class FakeWebSocket(
    private val listener: WebSocketListener,
    private val request: Request = ...
) : WebSocket {
    val sentFrames: MutableList<String> = mutableListOf()
    var subscriptionActive: Boolean = false  // D-10 subscription gate

    fun open(response: Response? = null) { listener.onOpen(...) }
    fun replay(frames: List<String>) { frames.forEach { listener.onMessage(this, it) } }
    fun inject(frame: String) { /* subscription-gated delivery */ }
    fun simulateFailure(t: Throwable) { listener.onFailure(...) }
}
```
Source: `app/src/test/java/works/mees/dinghy/net/FakeWebSocket.kt`

**`RespondingFakeWebSocket`** — subclass that auto-replies to every outbound send by looking up a canned reply in `SessionTestHarness.replyFor(raw)`:
```kotlin
class RespondingFakeWebSocket(listener, request, harness) : FakeWebSocket(listener, request) {
    override fun send(text: String): Boolean {
        val accepted = super.send(text)
        if (accepted) { harness.replyFor(text)?.let { inject(it) } }
        return accepted
    }
}
```
Source: `app/src/test/java/works/mees/dinghy/net/SessionTestHarness.kt`

**Faithful-mock discipline** — The harness enforces that the fake is **never more lenient than the real server**:
- `missingIdentifyArg(requestObj)` validates all four required `identify` fields (client_name, version, type, url) — catches the real `url`-less bug that slipped past unit tests on flox
- `invalidObjectsSubset(requestObj)` validates `objects.subscribe`/`objects.query` against the actual objects-list fixture — catches subscribing to undefined objects
- `klippyDown` flag: while true, `objects.subscribe` and `objects.query` (full-subset) return the real Moonraker `503 Klippy Not Connected` error; subscribe success ONLY flips `subscriptionActive = true`
- `subscriptionActive` gate on `inject()`: `notify_status_update` frames are dropped until a successful subscribe has been produced

**Spoolman fakes:**
- `FakeMoonrakerSpoolmanSession` — protocol fake for Spoolman Moonraker session
- `FakeSpoolmanClient` — HTTP client fake

**MJPEG / webcam fakes:**
- `FakeMjpegStream` — serves multipart MJPEG frames in tests
- `FakeWebcamHttp` — OkHttp MockWebServer-based HTTP fake for webcam probe tests

**What NOT to mock:**
- `PrinterStateStore` — always constructed real with `backgroundScope` (tests use the real conflation/throttling logic)
- `CommandDispatcher` — constructed real in most tests; command output assertions use `sentFrames` from `FakeWebSocket`
- `ThemeResolver` — constructed real with no-arg constructor in preview seam tests

---

## Key Test Categories

### 1. Network / Session tests (`app/src/test/.../net/`)

The highest-value tests. Cover:
- `HandshakeTest` — `identify → server.info → objects.list → query → subscribe` order, once-each, state seeding
- `JsonRpcClientTest` — id-correlation under interleaved notifications (adversarial STATE-05), error-path, timeout
- `MoonrakerSocketClientTest` / `MoonrakerSocketTest` — socket lifecycle, reconnect supervisor
- `ReconnectSupervisorTest` — exponential backoff, klippy-drop + re-subscribe cycle
- `KlippyReadyResyncTest` / `KlippyRecoveryStateTest` — the Phase-17 keystone: status diffs blocked until re-subscribe succeeds after a klippy drop

### 2. Holder tests (`app/src/test/.../ui/<screen>/`)

Each feature holder has a test file:
- `FineTuneHolderTest` — 17-05; state-flip busy-lock, clamp authority, off-grid precision (WR-01..04)
- `PrintStatusHolderTest` / `PrintStatusUiModelTest` / `PrintStatusControlModelTest`
- `FileBrowserHolderTest` / `FileBrowserHolderDeleteTest`
- `ExtrudeHolderTest`, `MoveHolderTest`, `TemperatureHolderTest`, etc.
- `OutputsHolderTest`, `OutputLedCommandTest`, `OutputScrubberSettleTest`

Pattern: holder constructed with `PrinterStateStore(backgroundScope)`, capabilities seeded, state seeded, `runCurrent()`, assertions on the holder's `.vm.value` or `.state.value`.

### 3. Pure / math tests

Isolated pure function tests — no coroutines, no Android:
- `ScrubberMappingTest` — `fractionFromX` tap-equals-drag invariant (WR-01/G-3)
- `OutputScrubberSettleTest` — `settleDispatchCount` one-dispatch-per-gesture
- `FontScaleTest` — `fsSp` and `FontScale` multiplier values
- `BedMeshModelTest` — bed mesh interpolation math
- `ScrewsTiltResultTest` / `TiltResultTest` — calibration result parsing
- `GraphDownsampleTest` / `RingBufferHolderTest` — temperature graph logic

### 4. State reducer tests (`app/src/test/.../state/`)

- `PrinterStateReducerTest` / `PrinterStateReducerOutputsTest` — diff-merge correctness
- `ConflationTest` — conflation/throttling behavior
- `DeriveCapabilitiesTest` — capability derivation from objects.list

### 5. Theme tests (`app/src/test/.../theme/`)

Large suite — 14+ test files:
- `ThemeResolverTest` / `ThemeResolverBakeTest` — bake-seam for all 6 combos
- `PaletteGoldenTest` / `PaletteMathTest` — OKLCH math, golden snapshots
- `BakedTokenTableTest` — pre-baked token consistency
- `BrandTintTest` — WCAG 3.0:1 floor for brandTint helper
- `TokenBridgeTest` — token pool/directional derivation
- `FontScaleTest` / `TextSizeCyclerTest` / `StyleCyclerTest`
- `SeriesColorTest` — data pool N-series selection per palette mode

### 6. Design-system tests (`app/src/test/.../designsystem/`)

- `DinghyIconsTest` — non-blank alternates, unique alternates, unique primary IconRefs, drawable-only-sanctioned-customs
- `ScrubberMappingTest` — pure mapping
- `HsvToRgbTest` — color conversion

### 7. Command catalog drift guard (`app/src/test/.../command/`)

- `CommandCatalogDriftTest` — D-10: asserts that every `CommandRegistry` entry exists in `docs/commands/catalog.json` and `printer-matrix.json`. This test WILL fail on any new phase that adds a `CommandSpec` without updating the JSON sidecars.

### 8. Preview seam tests (`app/src/test/.../preview/`)

- `PreviewBoxSmokeTest` — proves `ThemeResolver().bake(tuple)` compiles and produces distinct tokens for all 6 combos + fsLargeSeed. A pure JVM test — no Compose runtime.
- `SampleFixturesTest` — validates the preview placeholder data shapes

### 9. Instrumented tests (`app/src/androidTest/`)

**Very limited** — only 9 test files; most behavioral proof is host-side:
- `ShellPresenceTest` — shell routing, drawer navigation, Splash hard-override (requires real device or emulator)
- `FineTuneNavTest` — Fine-Tune hub routing, same-dest re-entry reset (requires real device)
- `LiveReconnectYankTest` / `LiveSocketReconnectTest` — live network smoke (requires real printer, NOT autonomous)
- `CleartextMoonrakerSmokeTest` — network security config smoke
- `ServiceSurvivesRotationTest` / `ProfileSurvivesRestartTest` — FGS lifecycle
- `ScanSurfaceLifecycleTest` — camera scan lifecycle
- `DrawerWebcamGatingTest` / `WebcamLifecycleTest` / `WebcamUnsupportedCardTest` — webcam gating

**Known limitation:** `FineTuneNavTest` drawer swipe (`swipeUp()`) fails on the real flox device — the per-event delta never clears `SWIPE_UP_THRESHOLD_PX=80f`. Manual flox eyeball used as the authoritative gate for that test. See `[[dinghy-instrumented-swipe-threshold]]`.

---

## Coverage Gaps

### High-priority gaps

**1. Wave-0 RED scaffold compile rule** — any `fail()` stub in a test must reference only already-built symbols. A bad scaffold bricks all per-wave test runs via Gradle compiling the whole test sourceset before `--tests` filtering. Codex catches this during plan review.

**2. FineTuneNavTest swipe** — instrumented drawer-open test cannot run headlessly on flox. The behavioral contract (hub reset on re-entry) is host-tested via `ShellNavStateTest`. The Compose-UI proof remains manual.

**3. PrintStatusScreen.kt** — the 1584-line primary screen has NO dedicated Compose UI test. Behavioral contracts (mode classification, gutter buttons, confirm guard wiring) are split across `PrintStatusHolderTest`, `PrintStatusControlModelTest`, `PrintStatusUiModelTest`, and `PrintStatusModeTest` (all host-side). No Compose render test.

**4. Calibration screens** — `CalibrationHubScreen`, `ProbeCalibrateScreen`, `BedMeshScreen`, `ScrewsTiltScreen`, `TiltScreen` have no Compose UI tests. Holder and gate logic is host-tested but no rendering proof.

**5. FwRetraction build-blind** — firmware retraction capability (`firmware_retraction` Klipper object) is absent on both E5 and E3. `hasFwRetraction` tests are host-only; the rendered screen is never on-device verified.

**6. CommandCatalogDriftTest and Phase-20 MR-* specs** — every phase that adds a `CommandSpec` to `CommandRegistry.all` MUST also add rows to `docs/commands/catalog.json` and `printer-matrix.json` or `CommandCatalogDriftTest` fails. This is a known recurring trap (`[[dinghy-command-catalog-drift]]`).

**7. Screen font-size floor** — no automated test enforces the 15sp metadata floor. `[[dinghy-font-sizes-too-small]]` is a known recurring issue caught only at on-device review.

**8. Token purity sweep** — no automated test enforces THEME-01 (no raw Color literals in UI code). The `intentColor` duplications and any future regressions are visible only via code review.

### Medium-priority gaps

**9. Output channel capability gating** — `OutputLedDetail` LED vs white-channel gating is tested in `OutputLedCommandTest` (host) but the rendered RGB vs white-only UI selector difference is on-device only.

**10. Webcam URL schema** — the ravens-perch nested `extra_data.ravens_perch.streams.<proto>.url` contract is host-tested in `WebcamUrlResolverTest` but the end-to-end URL → Media3 feed path is instrumented-test-only (`WebcamLifecycleTest`).

**11. H.264 rotation regression** — rotation while playing blanks the feed (known limitation). No automated test; deferred to Phase 22 pre-release fix.

---

## Testing Anti-Patterns to Avoid

**Do NOT use a lenient fake.** The `FakeWebSocket` / `SessionTestHarness` hardening (D-10) was added specifically because too-lenient fakes let the real `url`-less identify bug and the klippy-drop subscription bypass slip past green tests to flox. Every fake must enforce the same constraints the real server does.

**Do NOT use `rememberCoroutineScope` for DataStore writes in tests.** Composition scope is cancelled on navigation; writes silently drop. Route through `AppContainer.writeScope`.

**Do NOT poll/sleep in coroutine tests.** Use `advanceTimeBy`, `runCurrent`, or `advanceUntilIdle` from `kotlinx-coroutines-test`. Polling starves the virtual-time dispatcher.

**Do NOT write Wave-0 RED stubs that reference unbuilt symbols.** Stubs must use `fail()` bodies with no unresolvable type references — Gradle compiles the whole test sourceset before `--tests` filtering.

---

*Testing analysis: 2026-06-08*
