---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
last_updated: "2026-06-01T01:42:05.007Z"
last_activity: 2026-06-01
progress:
  total_phases: 9
  completed_phases: 3
  total_plans: 23
  completed_plans: 19
  percent: 33
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-30)

**Core value:** Direct, reliable printer control from an old Android tablet over Moonraker — install an APK, point it at the printer, and drive a print.
**Current focus:** Phase 04 — service-shell-settings-print-status-home

## Current Position

Phase: 04 (service-shell-settings-print-status-home) — EXECUTING
Plan: 4 of 8
Status: Ready to execute
Last activity: 2026-06-01

Progress (Phase 3): [██████████] 100% — 7/7 plans complete, ready for verification

## Performance Metrics

**Velocity:**

- Total plans completed: 15
- Average duration: — min
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 4 | - | - |
| 02 | 4 | - | - |
| 03 | 7 | - | - |

**Recent Trend:**

- Last 5 plans: —
- Trend: —

*Updated after each plan completion*
| Phase 01 P01-01 | 38 | 3 tasks | 16 files |
| Phase 01 P01-03 | 7 | 2 tasks | 8 files |
| Phase 01 P01-02 | 9 | 3 tasks | 2 files |
| Phase 01 P01-04 | 20 | 2 tasks | 5 files |
| Phase 02 P01 | 7 | 3 tasks | 15 files |
| Phase 02 P02 | 6 | 2 tasks | 5 files |
| Phase 02 P03 | 14 | 2 tasks | 6 files |
| Phase 02 P04 | 110 | 3 tasks | 11 files |
| Phase 03 P01 | 18 | 2 tasks | 12 files |
| Phase 03 P02 | 9 | 2 tasks | 9 files |
| Phase 03 P03 | 6 | 2 tasks | 4 files |
| Phase 03 P04 | 3 | 2 tasks | 4 files |
| Phase 03 P05 | 2 | 2 tasks tasks | 4 files files |
| Phase 03 P06 | 7 | 2 tasks | 4 files |
| Phase 03 P07 | 1440 | 2 tasks | 7 files |
| Phase 04 P01 | 30 | 2 tasks | 4 files |
| Phase 04 P02 | 18 | 2 tasks | 5 files |
| Phase 04 P04 | 14 | 2 tasks tasks | 5 files files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: Infrastructure-first horizontal structure — connection/state spine built and proven (mock socket + real printer) before any panel.
- [Roadmap revision]: Cross-AI review (Codex) applied in full. Old Phase 1 split into Platform Gate (1) + Connection & State Foundation (2); old Phase 4 split into Files/Print (5) + Job Status (6). Now 8 phases.
- [Phase 1]: UI-toolkit choice (Compose-everywhere vs. hybrid-Views) is its own gate — an on-device Nexus 7 benchmark against a SYNTHETIC 2–4 Hz source (no full connection layer needed). It gates all panel architecture. Pin to Compose 1.11 / AGP 8.7.x line regardless.
- [Phase 2]: Foundation connects via a STATIC/dev config; the user-facing connection config screen (CONN-01) moved to the Shell phase (3) where UI + DataStore live.
- [Phase 3]: Added shared command-dispatch primitive (PRIM-05: timeouts + in-flight/busy + debounce). Shell thermal dashboard proves the SHARED render/throttle primitive; the Temperature panel EXTENDS it into the full graph.
- [SCOPE RESTRUCTURE 2026-05-31]: Matthew delivered a full app-wide design system at `docs/ui_design/` (now the canonical UI LAW — see repo-root CLAUDE.md). Scope broadened: phones→tablets, **portrait + landscape**, **full theming** (dark+light+custom + S/M/L text size); **Nexus 7 / Adreno 320 retained as the perf FLOOR, not the only target**. Connection entry + theme + feature toggles move to a conventional **Settings screen** (keyboard allowed); printer controls stay keyboard-free (single-setting scrubber pages); nav = **swipe-up App Drawer**; Stop → full-screen **Confirm guard**. Roadmap **8 → 9 phases**: new **Phase 3 = Design System & Theming Foundation** (token theming, Focus/Field/Gutter responsive grammar, control language, Confirm/single-setting/toast/render primitives); old shell phase became **Phase 4 (Service, Shell, Settings & Print-Status Home)**; phases 4-8 shifted +1. REQUIREMENTS +5 (THEME-01/02, UI-01/02, SET-01 → 55 v1). The generated `04-UI-SPEC.md` is reduced to a pointer; `04-CONTEXT/RESEARCH/VALIDATION` carry SUPERSEDED banners (service/routing valid, UI superseded) — regenerate Phase 4 via discuss/plan before executing.
- [Roadmap]: v1 = functional core only (Connect + Temp/Move/Extrude/Files/JobStatus/Macros/Console). Fine-tune + Camera are v2.
- [Phase ?]: [Phase 1/01-01]: verifyMinSdk delivered as a precompiled build-logic script plugin (not apply(from=)) so it uses AGP SingleArtifact.MERGED_MANIFEST; PKG-02 floor proven adversarially.
- [Phase ?]: [Phase 1/01-01]: Release APK ships armeabi-v7a only via splits.abi; Compose UI resolves to 1.11.1; cleartext posture owned by the shared manifest/NSC (single-owner for Wave-2).
- [Phase ?]: [Phase 1/01-03]: Toolkit benchmark harness built — one deterministic SyntheticFeed drives a Compose scene and a hybrid-Views scene rendering identical worst-case layout with REAL Coil decode at 1920x1200; gfxinfo framestats parser is system of record, FrameTimingMetric corroboration only (no baseline-profile gate).
- [Phase 1/01-02]: CONN-05 cleartext smoke PASSED on real hardware. DEVICE-REALITY FINDING: the physical "Nexus 7 2013" (flox) runs LineageOS 18.1 / Android 11 / API 30, NOT stock Android 6 / API 23 — so the proof exercised the NSC (API-24+) cleartext path; the API-23 manifest-flag path is config-validated + deferred. minSdk 23 retained as install floor. 01-04 benchmark runs on this device with an ART caveat (API-30 runtime newer than stock-6; same Adreno 320 / 2GB / 1920x1200).
- [Phase 1/01-04]: Toolkit verdict = HYBRID (Compose shell + classic Views for Files list / temp graph / Console scrollback). On-device release benchmark on real flox showed Views ~2x lower p95/max frame time; both cleared floors (p50<16.6ms, 0 frozen). ADR: docs/adr/0001-ui-toolkit-decision.md. Gates all Phase 2+ panel architecture.
- [Phase ?]: [Phase 2/02-01]: Live Ender-5-Plus capture unavailable at execution; synthetic fallback corpus copied to golden/*.json names — fallback is the autonomous floor, GoldenFixtures.resolve() prefers live when later added.
- [Phase ?]: [Phase 2/02-02]: Pure state layer landed — reduceSnapshot/reduceDiff deep-merge (STATE-01, no field-wipe), applyKlippyMethod folds notify_klippy_* (STATE-04), deriveCapabilities + deriveSubscribeSet (v1 superset INT objects.list, A3; powerDevices empty A4). All I/O-free; 02-04 re-runs derive* on every reconnect.
- [Phase 02]: [Phase 2/02-03]: Transport seam landed — MoonrakerSocket callbackFlow bridge (injectable WebSocketFactory, FakeWebSocket-substitutable, readTimeout(0)); concrete RpcConnection binds send/close to the live socket (send-before-open unrepresentable, send-after-close throws); onFailure completes the flow normally carrying a typed Closed(cause).
- [Phase 02]: [Phase 2/02-03]: JsonRpcClient correlates by id under interleaved notify_* (STATE-05), per-request withTimeout + close(cause) fails+clears all pending (review HIGH #1, no deadlock), routes notifications by method with a SEPARATE bounded gcode flow; RpcConnectionException is a plain Exception so completeExceptionally fails (not cancels) deferreds.
- [Phase ?]: [Phase 2/02-04]: Session spine integrated and GATE-PROVEN on the real Ender 5 Plus. Critical finding AT the on-device gate: server.connection.identify REQUIRES a non-empty 'url' arg — the spine omitted it, so the live connection never reached Connected, yet the ENTIRE JVM suite was green because the FakeWebSocket mock was more lenient than the real server. Fix sends url + tightened the harness to enforce the required-field contract (regression guard). Lesson: a mock looser than the server hides protocol bugs; the real-hardware gate is the backstop.
- [Phase ?]: [Phase 2/02-04]: Reconnect supervisor lands D-01 overflow-safe uncapped backoff+jitter (no give-up ceiling) + D-02 requestReconnectNow; ordered identify->objects.list->deriveCapabilities->query->subscribe once per reconnect overwriting stale state (D-04, STATE-02); Connected gated behind Syncing (CONN-06 review HIGH #3); gentle AuthRequired quiescence (no token-fetch storm); split-plane conflation samples only high-rate numeric fields while control-plane+gcode stay immediate (STATE-03).
- [Phase ?]: [Phase 3/03-01]: Headless theme core landed — oklch baked to sRGB once (clamp-chroma, traceable Python script + drift-guard test) so no oklch reaches the renderer (API-23 Pitfall 1); ThemeResolver exposes the single StateFlow<ThemeTokens>; TokenDelta is sparse override-on-base normalized to unsigned-32-bit ARGB; ThemePrefs is the first DataStore with a PURE host-testable fail-safe read path that never throws (D-02). DataStore 1.1.7, verifyMinSdk floor held at 23.
- [Phase ?]: [Phase 3/03-02]: Type substrate + render data substrate landed. Six Geist/Geist Mono STATIC TTFs bundled (OFL 1.1, Fontsource Latin subsets ~175KB) as res/font assets; theme/Geist.kt exposes Geist+GeistMono FontFamily over R.font.geist_* — no variable fonts (API-23 floor, Pitfall 2), no fontFeatureSettings (monospace = tabular by construction). Plain-Kotlin bounded RingBuffer (D-12, cap 120, O(1) push + defensive-copy snapshot, @Synchronized) is the toolkit-agnostic rolling window both render primitives draw; TDD RED->GREEN, concurrent-stress tested.
- [Phase ?]: [Phase 3/03-03]: Compose token seam landed — LocalTokens is a staticCompositionLocalOf<ThemeTokens> (Pitfall 3: theme swap recomposes the subtree, cheaper untracked reads); DinghyTheme collects ThemeResolver.tokens via collectAsStateWithLifecycle() and pins LocalDensity(fontScale=1f) at the ONE boundary so --fs is the sole text authority (D-04, no double-apply). ScreenScaffold is the slot-based Focus/Field/Gutter primitive (BoxWithConstraints; landscape 50/50 stage + full-width gutter, portrait stack; weight/fillMax only, no px regions; caller wraps sacred squares in aspectRatio(1f) inside the region). OutlinedControl maps Intent{Neutral/Accent/Warn/Danger/Go}->outline/accentLine/heat/stop/go via LocalTokens, 2px border + heightIn(min=64.dp), static glow (D-13, no looping animation). Token-purity grep over designsystem/ = zero raw Color literals.
- [Phase ?]: [Phase 3/03-04]: Three panel-consumable primitives landed on the Wave-2 boundary — ConfirmGuard (PRIM-03, full-screen, gutter omitted, confirm=Danger/Go + cancel=Neutral, dispatches nothing/T-03-04); ScrubberPage (PRIM-01, keyboard-free fill-bar+stepper, no TextField, GeistMono live value, FIXED cancel contract: Neutral by default, Danger opt-in via destructiveDismiss); SeverityToast (PRIM-04, color+icon+text never color-alone). ThemeableView { applyTokens } = Views push-tokens seam (D-06) the 03-05 GraphView implements. Token-purity grep = zero raw color literals.
- [Phase ?]: [Phase 3/03-05]: Shared render primitives landed — ProgressRing = Compose Canvas single arc (track surface2 + accent arc, aspectRatio(1f) sacred square, ratio-only stroke, NaN/out-of-range clamped, NO animation D-13). GraphView = classic-Views custom Canvas implementing ThemeableView (D-06): ONE reused Path (rewind), pre-allocated stroke+fill Paints, line=accent.toArgb(), repaint via applyTokens+invalidate (no recreation). Pure GraphView.sanitize(snapshot,pixelWidth) filters NaN/Infinity + uniform-stride downsample-caps to pixel width ONCE in setData (bounded UI copy, allocation-free onDraw, Pitfall 4); GraphDownsampleTest proves cap/filter/empty/constant-series host-side. GraphViewHost = AndroidView(factory once, update pushes tokens+snapshot) so a theme flip recolors the Canvas. Token-purity grep over render/ = zero raw colors. Cross-toolkit recolor + gfxinfo perf are on-device gates (03-06/03-07).
- [Phase ?]: [Phase 3/03-06]: In-APK component gallery (D-07) = pure-DI Compose GalleryScreen (accepts ThemeResolver + nullable PrinterStateStore? + hoisted feed-source selector, constructs none); GalleryActivity (src/debug) is sole assembler. Debug-only LAUNCHER via app/src/debug/AndroidManifest.xml (manifest-merge seam, NOT a BuildConfig.DEBUG runtime guard); automated tools/check-release-no-gallery.sh proves via aapt badging that release APK has only MainActivity (D-08). Ring/graph fed from BOTH SyntheticFeed AND injected connection-less PrinterStateStore.printerState (D-14); GraphViewHost gets current tokens so theme flip recolors the Views Canvas. Manual matrix sign-off #1-#4 hosted here on flox; 03-07 = perf proof.
- [Phase ?]: [Phase 3 / criterion #5]: Closed PASS on the A-variant two-part gate (liveness: allocation-free/no-loop/zero-frozen + sparse-redraw latency p95<=~66ms, met at 50.1ms on flox), NOT the original p50<<16.6ms — that target was a Phase-1 dp(260) spike, invalid generalized to full-screen composition on Adreno 320 (~24ms native-res window-composite floor is physics).
- [Phase ?]: [Phase 6 mandate]: Temperature panel (multi-trace extension of this graph) MUST re-measure the full real screen against the same two-part gate; three re-open conditions recorded in 03-PERF-RESULTS.md.
- [Phase 4]: [04-01]: ConnectionStore takes an INJECTED DataStore (no delegate); DinghyApp (04-03) owns a SEPARATE connection.preferences_pb from theme.preferences_pb for a cleaner API-key redaction boundary (T-04-01-I).
- [Phase 4]: [04-01]: MoonrakerDiscovery is FULLY LAZY (ctor takes provider lambdas, touches neither NsdManager nor MulticastLock; machinery acquired on collect, released on awaitClose, review #5); best-effort, never auto-connects/blocks/throws.
- [Phase 4]: [04-01]: DataStore round-trip is not reliably host-testable on the Windows build host (back-to-back writes/second instance fail the atomic rename); clear→null proven with clear() as the single write plus round-trip proving save persists. Product clear() correct on Android.
- [Phase ?]: [Phase 4][04-02]: CommandDispatcher (PRIM-05/D-18) wraps JsonRpcClient.request() adding ONLY debounce + in-flight/busy Set + a UI withTimeout; typed catches surface a REDACTED DispatchEvent.Failure (never the cause message, T-04-02-I). Host-testable via substitutable request lambda + injectable timeSource on runTest virtual clock.
- [Phase ?]: [Phase 4][04-02]: TopRoute.derive() is the SINGLE pure routing authority (review HIGH #2) — !cfg→Connect, klippy!=Ready→Splash, else Shell(Dest.PrintStatus). Routes off klippyState ONLY; NEVER reads s.connection (D-05, proven by socketStateDoesNotRoute + clean grep); printing/idle share one PrintStatus surface (no Dest.Job, D-06).
- [Phase ?]: [Phase 4][04-04]: Settings screen (SET-01) landed — TokenTextField bridges Material OutlinedTextField chrome to LocalTokens (review #7); conventional verticalScroll list exempt from Focus/Field/Gutter (D-15); Save validates host/port parity then persists ConnectionConfig to ConnectionStore (triggers service rebuild, D-03); key-saved indicator + Clear-key + no-clobber blank-save (review #10/#12); lazy mDNS Scan with bounded settle window (review #5/D-04); Dark/Light+S/M/L+accent picker dual-write live ThemeResolver + persisted ThemePrefs, accent-only delta (D-16); MoonrakerDiscovery injected into AppContainer/DinghyApp (Rule 3).

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 1] Compose-vs-Views perf on the Nexus 7 is unresolved by design — only the on-device spike (synthetic 2–4 Hz feed) answers it. Hybrid (Views for high-churn: Files list, temp graph, Console scrollback) is the named fallback.
- [Phase 2] Auth handshake edge cases (oneshot-token websocket, `X-Api-Key`, `401`) need exercising during implementation; flagged for deeper Phase 2 research. JSON-RPC `id` correlation under interleaving notifications (STATE-05) must be covered by mock-socket tests.
- [Phase 4 / 04-03] PAUSED at Task 5 — `checkpoint:human-verify` (gate="blocking"). Tasks 1–4 complete + committed; the FGS owns the spine, the instrumented `ServiceSurvivesRotationTest` PASSED on flox (sessionInstanceId continuity). AWAITING human on-device sign-off: manual rotation + screen-off with the Ender 5 Plus reachable, persistent key-free notification visual check, and `adb logcat -s DinghySpine` id-sequence capture. Resume with "approved" or report the observed id sequence / what dropped. Plan NOT advanced past the unmet checkpoint.

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| *(none)* | | | |

## Session Continuity

Last session: 2026-06-01T01:42:04.972Z
Stopped at: Completed 04-04-PLAN.md
Resume file: None
