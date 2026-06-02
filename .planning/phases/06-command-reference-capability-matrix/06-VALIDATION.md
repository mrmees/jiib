---
phase: 6
slug: command-reference-capability-matrix
status: ready
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-01
---

# Phase 6 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 host unit tests + AndroidX instrumented/manual flox regression |
| **Config file** | `app/build.gradle.kts`; command catalog fixtures under `docs/commands/` or `app/src/test/resources/command_catalog/` |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` |
| **Full suite command** | `cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest :app:compileReleaseKotlin --no-daemon"` |
| **Estimated runtime** | ~60-180 seconds for host checks; on-device flox regression is manual |

---

## Sampling Rate

- **After every task commit:** Run the targeted host test for the touched seam, or `:app:testReleaseUnitTest` when the seam spans registry + dispatch.
- **After every plan wave:** Run `:app:testReleaseUnitTest :app:compileReleaseKotlin`.
- **Before `$gsd-verify-work`:** Full host suite and compile must be green; flox regression evidence must be recorded.
- **Max feedback latency:** 180 seconds for automated checks.

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 06-W0-01 | TBD | 0 | SC-1/2/3/4 | T-06-Drift / — | registry catalog IDs and predicates cannot drift from committed catalog/matrix fixtures | unit | `gw.bat :app:testReleaseUnitTest --tests *CommandCatalogDriftTest` | ❌ W0 | ⬜ pending |
| 06-W0-02 | TBD | 0 | SC-3 | T-06-Gcode / T-06-Safety | registry gcode params are byte-identical to `PrinterCommands.*` builders | unit | `gw.bat :app:testReleaseUnitTest --tests *CommandRegistryGcodeTest` | ❌ W0 | ⬜ pending |
| 06-W0-03 | TBD | 0 | SC-4 | — | raw `objects.list` set retained and `hasObject()` works without regressing existing capability helpers | unit | `gw.bat :app:testReleaseUnitTest --tests *DeriveCapabilitiesTest` | ✅ extend | ⬜ pending |
| 06-W0-04 | TBD | 0 | SC-3 | T-06-Dispatch / T-06-I | registry dispatch preserves gcode timeout, short non-gcode timeout, RpcError handling, and no secret leakage | unit | `gw.bat :app:testReleaseUnitTest --tests *CommandDispatcherTest` | ✅ extend | ⬜ pending |
| 06-W0-05 | TBD | 0 | SC-3 | T-06-Protocol | registry request wrappers preserve identify/list/query/subscribe order and identify `url` field | unit | `gw.bat :app:testReleaseUnitTest --tests *HandshakeTest` | ✅ extend | ⬜ pending |
| 06-DOC-01 | TBD | TBD | SC-1 | — | `docs/commands/klipper-gcode.md`, `moonraker-api.md`, and `spoolman-api.md` exist with stable IDs and source URLs | source assertion | `test -f docs/commands/klipper-gcode.md && test -f docs/commands/moonraker-api.md && test -f docs/commands/spoolman-api.md` | ❌ W0 | ⬜ pending |
| 06-DOC-02 | TBD | TBD | SC-2/4 | — | `printer-matrix.json` records E5/E3 objects, macros, components, predicates, and not-on-printers escape hatch | unit | `gw.bat :app:testReleaseUnitTest --tests *CommandCatalogDriftTest` | ❌ W0 | ⬜ pending |
| 06-REG-01 | TBD | TBD | SC-3/4 | T-06-Safety | Phase 1-5 call sites dispatch/request through registry without changing builder output or command semantics | compile+unit | `gw.bat :app:testReleaseUnitTest :app:compileReleaseKotlin` | ✅ extend | ⬜ pending |
| 06-UAT-01 | TBD | final | SC-3/4 | T-06-Safety / T-06-Regression | refactored send paths behave identically on flox + live Ender 5 Plus | on-device | manual flox regression checklist | n/a | ⬜ pending (checkpoint) |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*
*Planner should replace TBD rows with concrete plan IDs while preserving these verification obligations.*

---

## Wave 0 Requirements

- [ ] `CommandCatalogDriftTest` — parses machine-readable catalog/matrix fixtures and checks every registry `catalogId` and predicate reference.
- [ ] `CommandRegistryGcodeTest` — compares registry gcode params against byte-identical `PrinterCommands.*` output, including clamp boundaries and multiline commands.
- [ ] Extend `DeriveCapabilitiesTest` — raw object-name retention and `hasObject()`, with existing macro/heater/fan behavior unchanged.
- [ ] Extend `CommandDispatcherTest` — registry overload preserves gcode timeout path, short non-gcode timeout path, RpcError behavior, and redacted failures.
- [ ] Extend `HandshakeTest` / session tests — registry request wrappers preserve method order and identify `url`.
- [ ] Machine-readable catalog/matrix fixtures — JSON preferred; markdown is human-facing only.

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Focused flox regression after full call-site refactor | SC-3/4 | Touches live-proven printer send paths; host tests cannot prove hardware behavior | On flox + live Ender 5 Plus: Move jog/home/disable; Temp set/preset/cooldown; Extrude/retract/load/unload or missing-macro popup; Stop -> ConfirmGuard -> shutdown/Splash; recovery actions if reachable |
| Live E5/E3 command availability refresh | SC-2/4 | `printer.gcode.help` / `gcode.commands` and full object lists require real printers | Re-query both printers for `server.info`, `printer.objects.list`, `printer.objects.query?gcode`, `printer.gcode.help` where available; update matrix fixtures and note unreachable printers explicitly |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies.
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify.
- [ ] Wave 0 covers all MISSING references.
- [ ] No watch-mode flags.
- [ ] Feedback latency < 180s for automated checks.
- [ ] `nyquist_compliant: true` set in frontmatter.

**Approval:** pending planner — Wave 0 must create/extend the tests above; final plan must include the flox regression checkpoint.
