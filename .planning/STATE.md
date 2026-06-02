---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
last_updated: "2026-06-02T05:18:56.909Z"
last_activity: 2026-06-02
progress:
  total_phases: 15
  completed_phases: 5
  total_plans: 40
  completed_plans: 36
  percent: 33
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-30)

**Core value:** Direct, reliable printer control from an old Android tablet over Moonraker — install an APK, point it at the printer, and drive a print.
**Current focus:** Phase 06 — command-reference-capability-matrix

## Current Position

Phase: 06 (command-reference-capability-matrix) — EXECUTING
Plan: 2 of 5
Status: Ready to execute
  → Task 1 (nav wiring) COMPLETE + committed (b338c8c) + test retarget (8181218). Dest += Temperature/Move/Extrude; drawer Move→Move / Temp→Temperature / Tools→Extrude live; AppShell when(dest) renders all three full-bleed off per-session holders. compileReleaseKotlin + full unit suite GREEN.
  → Release APK built + debug-signed + INSTALLED on flox (0a64b42e); confirmed it launches to the real RootController shell (NOT the Phase-1 scaffold — MainActivity wired in 04-07; the stale 04-06b scaffold blocker is now MOOT).
  → AWAITING Task 2 (D-06 multi-trace perf re-measure) + Task 3 (SC-5 end-to-end UAT) on flox + live Ender 5 Plus. Human-device + human-eyes required — perf numbers NOT fabricated, plan NOT advanced, ROADMAP NOT updated, no SUMMARY claiming on-device success.
Last activity: 2026-06-02

Progress (Phase 3): [██████████] 100% — 7/7 plans complete, ready for verification

## Performance Metrics

**Velocity:**

- Total plans completed: 31
- Average duration: — min
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 4 | - | - |
| 02 | 4 | - | - |
| 03 | 7 | - | - |
| 05 | 11 | - | - |

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
| Phase 04 P05 | 9 | 2 tasks | 4 files |
| Phase 04 P06 | 5 | 2 tasks | 3 files |
| Phase 04 P07 | 95 | 3 tasks tasks | 10 files files |
| Phase 03 P08 | 18 | 3 tasks | 5 files |
| Phase 05 P01 | 5 | 2 tasks | 6 files |
| Phase 05 P02 | 9 | 2 tasks | 5 files |
| Phase 05 P03 | 12 | 2 tasks | 10 files |
| Phase 05 P04 | 3 | 2 tasks | 3 files |
| Phase 05 P06 | 11 | 2 tasks | 3 files |
| Phase 05 P05 | 5min | 2 tasks | 3 files |
| Phase 05 P07 | 9 | 2 tasks | 3 files |
| Phase 05 P09 | 9 | 2 tasks | 2 files |
| Phase 05 P10 | 4 | 2 tasks | 2 files |
| Phase 05 P11 | 4 | 2 tasks | 4 files |
| Phase 06 P01 | 7 | 3 tasks | 8 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: Infrastructure-first horizontal structure — connection/state spine built and proven (mock socket + real printer) before any panel.
- [Roadmap revision]: Cross-AI review (Codex) applied in full. Old Phase 1 split into Platform Gate (1) + Connection & State Foundation (2); old Phase 4 split into Files/Print (5) + Job Status (6). Now 8 phases.
- [Phase 1]: UI-toolkit choice (Compose-everywhere vs. hybrid-Views) is its own gate — an on-device Nexus 7 benchmark against a SYNTHETIC 2–4 Hz source (no full connection layer needed). It gates all panel architecture. Pin to Compose 1.11 / AGP 8.7.x line regardless.
- [Phase 2]: Foundation connects via a STATIC/dev config; the user-facing connection config screen (CONN-01) moved to the Shell phase (3) where UI + DataStore live.
- [Phase 3]: Added shared command-dispatch primitive (PRIM-05: timeouts + in-flight/busy + debounce). Shell thermal dashboard proves the SHARED render/throttle primitive; the Temperature panel EXTENDS it into the full graph.
- [PHASE 6 = MATRIX-FIRST 2026-06-01]: Matthew moved **Command Reference & Capability Matrix to the TOP of the remaining work (now Phase 6)** — it gates everything after it, so building it first means Files/Macros/Calibration/etc. register commands canonically from the start instead of retrofitting. The rotation: matrix → P6, Files & Print Control → P7, Macros & Console → P8 (phases 9–14 unchanged). REQ remap: FILE/JOB → P7, MACRO/CONS → P8. **The capability-matrix half is green-lit to knock out NOW** via live printer introspection.
- [ROADMAP EXPANSION 2026-06-01 — 9 → 14 phases, the **v1 milestone**, ships once at Phase 14]: Matthew defined the full back half of v1; **the 14-phase roadmap is ONE milestone with a single release at Phase 14 (no internal mid-ship)**. A separate **v2 milestone** is seeded (first phase: **Home Assistant integration** — see Future Milestones in ROADMAP.md), to be formalized via `/gsd-new-milestone` after v1 ships. New phase order after the functional core (P6 Files&Control, P7 Macros&Console): **P8 Command Reference & Capability Matrix** (canonical in-code command registry + a live per-printer E5/E3 capability/command-availability matrix — knocked out EARLY because it gates the feature phases; also records the authoritative Klipper/Moonraker API-doc URLs); **P9 Calibration & Maintenance** (SCREWS_TILT_CALCULATE / Z_TILT_ADJUST / BED_MESH_CALIBRATE / QUAD_GANTRY_LEVEL pages, capability-gated by P8); **P10 Webcam Streaming** (printer MJPEG, WebRTC deferred); **P11 Spool Management** (Spoolman ONLY + the headline tablet-camera QR-scan-to-assign flow using ZXing — GMS-free for the Nexus 7 floor — reading Spoolman's `web+spoolman:s-<id>` labels); **P12 Macro Prompt Protocol** (interactive `action:prompt_*` dialogs from user macros, per github.com/mrmees/klipper-macro-prompt-protocol; reuses the P7 Console gcode-response stream + P3 dialog primitive); **P13 Optimization, Network Efficiency & End-to-End Reliability** (the request-cadence audit — moved AFTER all screens per "screens first, then consolidate"); **P14 Release Hardening & Ship** (deferred print-loop robustness + Doze + R8 + signed APK — ships everything at once). Reqs: promoted BEDM-01/BEDL-01/ZCAL-01→P9, CAM-01→P10, SPOOL-01→P11 from v2; added PROMPT-01→P12; PKG-01/03 remapped to P14; finer feature-req families defined at each phase's discuss. NOTE the two camera features use DIFFERENT cameras (P10 = printer MJPEG over network; P11 = tablet camera + barcode) — no shared foundation. **NEXT ACTION (Matthew green-lit): knock out the P8 capability matrix NOW** by live-introspecting both printers (objects.list, gcode help, macros, components) into a committed `docs/` reference, citing the authoritative Klipper/Moonraker API docs.
- [ROADMAP RESTRUCTURE 2026-06-01 — remaining phases 6–9]: The old **"Job Status" phase dissolved**. Its live-monitoring half (progress/temps/Z) was already delivered by the Phase-4 Print Status home + the Status quick-task enrichment (Inc 1–3, `260601-sip`/`260601-th9`), so it isn't rebuilt. Its **print controls (pause/resume/cancel/restart, JOB-01..05) fold into Phase 6**, now **"Files & Print Control — Core Print-Loop Gate"** (browse/start/delete + wire the stubbed gutter controls = the complete user-drivable print loop). Its **deep robustness (reconnect print-state resync + process-death recovery) defers to Phase 9**. Macros/Console → **Phase 7**. **NEW Phase 8 = "Backend Consolidation"** (canonical single-source command references + a Klipper command/error/acceptance reference catalog + a request-cadence audit to stop spamming the LAN — a quality/refactor phase, deliberately AFTER all primary screens so it catalogs only what's actually used, not speculative API). Phase 9 → **"Release Hardening & Ship"** (absorbs the deferred robustness + Doze/always-on + R8 + signed APK). Still 9 phases, still 55 reqs (only re-mapped: JOB→P6, MACRO/CONS→P7, PKG→P9). Rationale (Matthew): finish all primary screens/functions on the current pragmatic backend first, THEN do one driven consolidation pass — don't catalog methods/DB structure we don't need yet. DISCIPLINE carried forward: keep every screen on the central `PrinterStateStore`/single `objects.subscribe` handshake (no per-screen ad-hoc polling) so P8 is a cadence audit, not a per-screen refactor.
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
- [Phase 4]: [04-05]: Splash (SHELL-05) is a gutter-less hard override (D-06) — recovery actions derived from a RecoveryMode (FirstRun→Settings / KlippyDown[shutdown|error AND socket up]→Retry+firmware+host restart / Unreachable[socket down or klippy disconnected]→Retry+Edit, NO firmware when Klippy unreachable / Connecting→no recovery row), ALL dispatched via the narrow SessionControl (review #1, never a raw session). PrinterState gained nullable klippyStateMessage from webhooks.state_message (set when provided / clear on Ready / retain on webhooks-absent diff per STATE-01, review #8); the splash reason text prefers it, falling back to terse never-blank enum labels. Reducer adapted into its existing accumulator (var s + copy), not the plan's prev.copy() shape — identical behavior.
- [Phase 4]: [04-06]: Print Status home (SHELL-04 part 1) landed — PrintStatusHolder turns the already-throttled printerState into a bounded primary-heater RingBuffer snapshot + a flattened 6-slot PrintStatusGrid with EXPLICIT review-#9 capability fallback (extruder-prefix primary; heater_bed-or-promote-chamber secondary by object name; null placeholders never fabricated; heater target 0 → null setpoint). PrintStatusScreen is state-adaptive (D-08): ProgressRing while Printing/Paused, a live "Ready"+nozzle/bed readout idle (never a 0% ring); 2×3 GeistMono grid (StatCell renders "—" for null); a RESERVED sparkline slot (GraphView + combined-render perf gate DEFERRED to 04-06b, review #4); Stop → full-screen ConfirmGuard → dispatcher.dispatch("estop", EMERGENCY_STOP) via the per-session CommandDispatcher (review #1), never a raw transport request; firing routes klippy→shutdown→Splash automatically (D-10). Token-pure, no second throttle (consumes store's 250ms conflation). Holder host-tested across all fallback cases.
- [Phase ?]: [Phase 4][04-04]: Settings screen (SET-01) landed — TokenTextField bridges Material OutlinedTextField chrome to LocalTokens (review #7); conventional verticalScroll list exempt from Focus/Field/Gutter (D-15); Save validates host/port parity then persists ConnectionConfig to ConnectionStore (triggers service rebuild, D-03); key-saved indicator + Clear-key + no-clobber blank-save (review #10/#12); lazy mDNS Scan with bounded settle window (review #5/D-04); Dark/Light+S/M/L+accent picker dual-write live ThemeResolver + persisted ThemePrefs, accent-only delta (D-16); MoonrakerDiscovery injected into AppContainer/DinghyApp (Rule 3).
- [Phase ?]: [Phase 4][04-07]: App wired end-to-end — MainActivity starts the FGS, hosts ONE DinghyTheme boundary, delegates ALL routing to a single RootController (SOLE consumer of derive() + the one open-Settings escape, review #2/#11). Swipe-up full-screen AppDrawer (Status+Settings live; Move/Temp/Files/Tools/Macros/Devices+red Power greyed/INERT — no click action, T-04-07-E); AppShell renders the active Dest full-bleed (lean route holder, NOT Navigation-Compose, D-05) + BackHandler collapse; Settings is an in-shell Dest (no dangling onOpenSettings). ShellPresenceTest PASSES on flox. Wiring fix: SpineHandle gained a per-session PrinterStateStore so the shell builds PrintStatusHolder from the LIVE store. UNBLOCKS 04-06b's combined-render perf gate.
- [Phase 03]: [03-08 gap-closure]: G-3 ScrubberPage tap-to-set fixed by ONE awaitEachGesture (re-arming) block — awaitFirstDown sets value immediately (registers the zero-movement tap detectDragGestures swallowed), pressed-move loop tracks drag; both funnel through internal fun fractionFromX (now host-tested by ScrubberMappingTest, the coverage gap that hid WR-01). One pointerInput, one consumer — no dual-detector race. G-2 shared bar/button 16.dp inset (no fixed px). G-4 ConfirmGuard opaque t.bg layered UNDER alpha stop/go tint. G-1 token-bg roots replace bare Material3 Surface() (colorScheme never populated). Tasks 1-3 committed; Task 4 on-device re-check OPEN.
- [Phase 05]: [05-01]: Phase-5 state contract settled FIRST — gcodePosition (offsets-stripped Move source, NOT toolheadPosition/Pitfall 1); HeaterState.canExtrude (defaults false fail-safe cold-extrude gate, EXTR-04); hasMacroIgnoreCase (Moonraker lowercases macro names, Pitfall 2/EXTR-02); GCODE_SCRIPT + TEMPERATURE_STORE methods. Null-safe booleanOrNull; merge retains can_extrude on temp-only diff (STATE-01). TDD RED->GREEN, full state suite green.
- [Phase ?]: [Phase 5][05-02]: PrinterCommands is the settled action-string contract — clamp-before-format (ASVS V5, named bounds), fixed axis/heater identifiers (no free-text), jog/extrude/overrideJog wrapped in SAVE/RESTORE_GCODE_STATE; scriptParams wraps into {script:gcode}. applyPreset = primary extruder+heater_bed only (multi-tool deferred). violet 3rd trace token baked dark 0xFFAE84F2/light 0xFF7B47BF via bake_tokens.py (in-gamut, THEME-01).
- [Phase ?]: [Phase 5][05-03]: Two one-shot handshake reads (server.temperature_store backfill + configfile min_extrude_temp/max_extrude_only_distance) land on capability-like StateFlows holders OBSERVE -> connect-time graph fullness (G-1 fix) + Extrude min-temp hint driven by data arriving, not a later notify_status_update diff. Best-effort (per-read runCatching, after subscribe) -> printer lacking endpoint/field degrades, never breaks Connected. min/max from PRIMARY extruder (static); per-tool safety gate stays the LIVE can_extrude boolean. SessionTestHarness hardened to faithful Moonraker shapes.
- [Phase ?]: [Phase 5][05-04]: GraphView EXTENDED in place to N (<=3) traces (Array<Path>/Array<Paint>, nozzle=heat/bed=accent/chamber=violet) on one shared X window + a FIXED shared Y-range default 0..350 C — replaces the per-frame window min/max auto-range (Phase-4 gap G-1 fix); 0..350 (not 0..300) covers the setHeater clamp so a legal target never clips (Codex). Per-trace dashed CURRENT-setpoint line (D-04, DashPathEffect pre-allocated once in init). Area fill bounded to the primary trace only (drawArea = the 05-08 Adreno-320 fill-rate isolation lever). GraphViewHost gained a series:List<FloatArray> overload; single-snapshot Print Status sparkline path retained back-compat. onDraw allocation-free (T-05-04-D mitigated); D-06 perf re-measured on the full Temperature screen in 05-08, NOT grandfathered from 50.1ms.
- [Phase ?]: [Phase 5][05-06]: Move panel landed — MoveHolder surfaces gcode_position X/Y/Z (nullable, never fabricated) + per-axis homed gating; MoveScreen reproduces 04-move.png (3x3 jog pad value-on-glyph corners green=homed/amber=unhomed, center=homeXY G28 X Y only, amber Override=overrideJog for the unhomed axis, gutter Home/Disable/Back). Every action via dispatcher GCODE_SCRIPT+scriptParams; Disable behind ConfirmGuard(destructive=false)->M84. Distance set follows the LAW mockup 0.1/1/10/25/50/100. Live jog/override on flox = 05-08 UAT.
- [Phase ?]: Temperature panel: 3-trace cap (nozzle/bed/chamber) enforced in holder; extra heaters truncated
- [Phase ?]: [Phase 5][05-07]: Extrude panel landed — ExtrudeHolder gates Extrude/Retract on the LIVE per-tool can_extrude (fail-safe false), COMBINING printerState with the 05-03 one-shot minExtrudeTemp/maxExtrudeDistance StateFlows so the real-temp hint + distance ceiling are deterministic on connect. ExtrudeScreen mirrors MoveScreen (D-08): extrude(±dist,speed) via GCODE_SCRIPT; distance steps above max_extrude_only_distance disabled (fall back to largest enabled); Load/Unload always shown (present=dispatch, absent=Severity.Info popup, D-10/hasMacroIgnoreCase); T0/T1… selector only when extruderCount>1 (D-09), setActiveTool re-points the gate. Token-pure. Cold->hot gate + missing-macro popup = 05-08 UAT.
- [Phase ?]: [Phase 5][05-09 gap-closure]: G1 BLOCKER closed — CommandDispatcher.dispatch() now catches RpcError (peer of Exception, distinct from RpcConnectionException) alongside transport/timeout and emits a non-fatal DispatchEvent.Failure carrying the printer's rejection text (e.g. 'Move out of range'). Previously RpcError re-threw uncaught in the unsupervised scope.launch → FATAL EXCEPTION + FGS auto-restart (confirmed live on flox). TDD RED→GREEN; regression feeds the EXACT RpcError JsonRpcClient produces for a rejected gcode.script (mock-vs-reality gap closed). No catch widened to Throwable. G2/G3 (klippy_ready re-handshake + stale one-shot reads) remain OPEN, out of scope.
- [Phase ?]: [Phase 5][05-10 gap-closure]: G2 HIGH + G3 MED closed — notify_klippy_ready on the still-open socket now re-runs the FULL runHandshake() (re-objects/subscribe + re-run BOTH 05-03 one-shot reads), so the FGS-held session self-heals after a Klipper FIRMWARE_RESTART without a force-stop (G2) and edited configfile values refresh on a printer.cfg reload (G3). Gated by per-attempt handshakeComplete (no duplicate of the initial connect handshake), serialized by rehandshakeMutex, launched on the attempt scope off the frame collector, best-effort runCatching. MoonrakerService unchanged (verified against code). TDD RED->GREEN; faithful test injects a real no-id klippy_ready frame.
- [Phase 06]: Plan 06-01 is a RED-only guard wave; production CommandRegistry and Capabilities.objects/hasObject are deferred to Plan 02. — The Phase 6 plan intentionally establishes drift, gcode, capability, dispatcher, and handshake guards before implementation.
- [Phase 06]: docs/commands JSON sidecars are the enforcement source for command catalog and printer matrix guards. — Markdown command documentation remains human-facing and is not parsed by tests.

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 1] Compose-vs-Views perf on the Nexus 7 is unresolved by design — only the on-device spike (synthetic 2–4 Hz feed) answers it. Hybrid (Views for high-churn: Files list, temp graph, Console scrollback) is the named fallback.
- [Phase 2] Auth handshake edge cases (oneshot-token websocket, `X-Api-Key`, `401`) need exercising during implementation; flagged for deeper Phase 2 research. JSON-RPC `id` correlation under interleaving notifications (STATE-05) must be covered by mock-socket tests.
- [Phase 4 / 04-03] PAUSED at Task 5 — `checkpoint:human-verify` (gate="blocking"). Tasks 1–4 complete + committed; the FGS owns the spine, the instrumented `ServiceSurvivesRotationTest` PASSED on flox (sessionInstanceId continuity). AWAITING human on-device sign-off: manual rotation + screen-off with the Ender 5 Plus reachable, persistent key-free notification visual check, and `adb logcat -s DinghySpine` id-sequence capture. Resume with "approved" or report the observed id sequence / what dropped. Plan NOT advanced past the unmet checkpoint.
- [Phase 4 / 04-06b] BLOCKED at Task 2 — checkpoint:human-verify (gate=blocking). Task 1 (heater sparkline via GraphViewHost wired into the reserved Print Status Field slot, recolors on theme flip) COMPLETE + committed (6deb8d5). Combined-render perf gate NOT measured: production MainActivity is still the Phase-1 scaffold placeholder (no routing/PrintStatusScreen) and NO harness composes the real combined surface (ring+sparkline+grid+gutter). Release built/signed/installed on flox (0a64b42e) but launches to the scaffold stub. Perf numbers NOT fabricated. Plan counter NOT advanced; ROADMAP NOT updated. Unblock: wire MainActivity->TopRoute->PrintStatusScreen (or a combined-surface harness) + live Ender 5 Plus, then run the gfxinfo two-part gate.
- [Phase 3 / 03-08 gap-closure] OPEN at Task 4 — checkpoint:human-verify (gate=blocking). Tasks 1-3 complete + committed (32a613a/022e8e6/0b34a84): full unit suite green, debug APK (app-armeabi-v7a-debug.apk) reinstalled on flox (0a64b42e) + gallery launched. AWAITING on-device re-check of all four gaps: G-3 tap-to-set on the ScrubberPage fill bar, G-2 shared bar/button width, G-4 ConfirmGuard scrim opacity, G-1 gallery dark/light/custom page bg. Resume with 'approved' or describe which gap(s) still read wrong. Plan NOT advanced past the unmet checkpoint.

### Quick Tasks Completed

| # | Description | Date | Commit | Directory |
|---|-------------|------|--------|-----------|
| 260601-sip | Status Inc 2 — one-shot `server.files.metadata` (keyed on filename) lights up ring thumbnail (Coil 3) + Layer total (`layer_count`) + Z final-height (`object_height`) + Remaining (`estimated_time × (1−progress)`); strict catalog fidelity, graceful "—" degradation. compile + full release unit suite green. On-device thumbnail/ETA check Matthew-driven. | 2026-06-01 | 5c1302b | [260601-sip-status-print-metadata](./quick/260601-sip-status-print-metadata/) |
| 260601-th9 | Status Inc 3 — idle FIELD area is state-driven by `print_stats.state`: printing→StatGrid (unchanged); idle+history→`LastJobCard` (thumbnail + stats from a ONE-shot `server.history.list?limit=1&order=desc`, fetched on each printing→idle edge, never polled); idle+none→centered `file_copy_off`. Both idle surfaces are clickable `TODO(nav)` seams (past-print detail / file browser destinations NOT built). history.list+totals shape captured live → recorded in docs/moonraker-capabilities.md. compile + full release suite green; Inc 2 parse test un-regressed by the shared thumbnail-pick refactor. On-device UAT Matthew-driven. | 2026-06-01 | c8584ed | [260601-th9-status-last-job-summary](./quick/260601-th9-status-last-job-summary/) |

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| Status gutter | **Pre-v1:** wire + verify the Pause/Resume gutter button actually pauses/resumes the print (`pause_resume` is present on the E5). Currently a disabled placeholder whose LABEL flips Pause↔Resume on `print_stats.state == paused`, but it dispatches nothing. Tune button still a placeholder too. Verify both fire real Moonraker calls before v1 release. | Open — verify pre-v1 | 2026-06-01 (quick 260601-sip follow-up) |

## Session Continuity

Last session: 2026-06-02T04:33:22.652Z
Stopped at: Phase 6 context gathered
Resume file: .planning/phases/06-command-reference-capability-matrix/06-CONTEXT.md
