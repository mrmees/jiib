# jiib Agent Guide

## Project

jiib is a native Android control surface for Klipper 3D printers. It connects directly to
Moonraker over the local network and must keep the connect → monitor → control-a-print loop reliable
on old hardware.

## Trust And Context

Use current sources in this order:

1. Source code, build configuration, and tests define current behavior.
2. This file defines repository-wide working rules.
3. `docs/maintainer/` explains current architecture, testing, releases, and documentation ownership.
4. Scoped references such as `docs/ui_design/`, `docs/manual/`, `docs/commands/`, and accepted ADRs
   are authoritative only for their stated subject.

Ignored or local-only paths—including `.planning/`, `.superpowers/`, `.claude/skills/`,
`docs/superpowers/`, and untracked reports—are historical or workflow context. Never treat them as
current implementation guidance. Use them only for archaeology. When documentation conflicts with
the implementation, verify the implementation and update the authoritative document as part of the
change.

## Non-Negotiable Constraints

- Android 6.0 / API 23 is the support floor. Do not raise `minSdk` or add dependencies that do.
- The Nexus 7 2013 (Adreno 320, 2 GB RAM) is the performance floor. Profile release builds; debug
  Compose performance is not representative.
- Support both portrait and landscape and the dark, light, custom-color, and S/M/L text settings.
- The app is GMS-free and distributed as sideloaded per-ABI APKs.
- Do not change the application ID, release signing model, ABI split strategy, or version scheme
  without an explicit project decision.
- Dependency versions are pinned. Do not make opportunistic dependency or build-tool upgrades.
- Do not edit the checked-in Gradle wrapper scripts or wrapper JAR unless the task explicitly changes
  the Gradle wrapper.

## Canonical Commands

- Run the normal local/CI gate: `./scripts/check.sh`
- Run documentation hygiene only: `./scripts/check.sh --docs-only`
- Run the Gradle gate only: `./scripts/check.sh --gradle-only`
- Run a targeted host test: `./gradlew :app:testDebugUnitTest --tests <fully.qualified.Test> --no-daemon`

Under WSL, `scripts/check.sh` automatically uses the Windows-side `E:\Android\gw.bat` helper so adb
and the installed Android SDK remain reachable. CI and native Linux use the checked-in wrapper.
Read `docs/maintainer/testing.md` before device-, printer-, or release-specific verification.

## Architecture Boundaries

- `MoonrakerService` owns the live printer session and publishes one atomic `SpineHandle`.
- UI consumes the session spine, store-derived flows, or feature holders. UI must not construct
  sockets, sessions, or raw JSON-RPC clients.
- `state/` is headless state and reducers; `net/` is transport/protocol; `command/` is command specs
  and dispatch; `service/` owns the session; `di/` exposes process wiring.
- Keep feature state toolkit-agnostic where practical. Compose is the default UI toolkit; classic
  Views are reserved for measured high-churn or render-heavy surfaces.
- Put new code in the package that owns the behavior. Avoid generic dumping grounds.

Read `docs/maintainer/architecture.md` before changing session ownership, reconnect behavior,
printer switching, navigation, state flow, or major package responsibilities.

## UI Changes

Before changing screen structure, components, icons, colors, typography, layout, or control intent,
read `docs/ui_design/AGENTS.md` and follow its reading order. Never guess or auto-select a glyph; use
an existing approved asset or ask the owner when no selection exists. Update `docs/manual/` when
user-visible behavior changes.

## Testing And Completion

- Add or update tests for behavior changes; add a regression test for a bug when practical.
- Run the narrowest useful test while iterating, then the canonical gate before completion.
- Device-dependent work may require instrumented tests and manual hardware/printer verification.
- Do not grow the lint baseline to hide new violations.
- Command-registry and reference changes must keep their drift tests and tracked generated artifacts
  synchronized.

## Security And Release Safety

- Never commit API keys, printer credentials, keystores, signing passwords, or credential-bearing
  `local.properties` content.
- Do not log credentials or sensitive Moonraker payloads.
- A release build can succeed unsigned. Follow `docs/maintainer/release.md`; never publish an APK
  without explicitly verifying its signature and ABI.

## Documentation Rule

Update the owning document whenever a change alters how someone builds, tests, releases, configures,
extends, or reasons about the system. Prefer links over copied facts. Delete or correct stale guidance
immediately; do not preserve it inside an authoritative document merely for history.
