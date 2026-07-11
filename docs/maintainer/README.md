# Maintainer Docs

This directory is the current maintainer source of truth for jiib.

Use these docs when changing the app, reviewing AI-generated patches, or re-orienting after time away
from the project. Historical planning notes under `.planning/` are local-only project memory and may
contain stale package names, old navigation concepts, or retired implementation details.

Repository-wide working rules and the trust hierarchy live in [AGENTS.md](../../AGENTS.md). Read it
first. These maintainer documents provide the detail that the short root instruction file routes to.

## Reading Order

1. [Root agent guide](../../AGENTS.md) - repository-wide constraints, commands, boundaries, and trust rules.
2. [Architecture](architecture.md) - current app structure, session spine, navigation, state flow, and UI boundaries.
3. [Testing](testing.md) - CI, local verification commands, device tests, fixtures, and docs-only verification.
4. [Release](release.md) - manual release runbook, versioning, signing, split APKs, and smoke checks.
5. [Doc Maintenance](doc-maintenance.md) - rules for keeping current docs, manuals, design docs, and local evidence in sync.

## Existing Docs To Use

- [Project README](../../README.md) - user-facing overview, install notes, build-from-source basics, and screenshots.
- [User manual](../manual/README.md) - end-user screen behavior and option reference.
- [UI design system](../ui_design/README.md) - Focus/Field grammar, components, tokens, and visual law.
- [ADR 0001](../adr/0001-ui-toolkit-decision.md) - accepted hybrid Compose/Views decision.

## Local-Only Context

- `docs/commands/` - mostly local-only Moonraker/Klipper/Spoolman evidence. Public clones contain the
  tracked `catalog.json` and `printer-matrix.json` fixtures plus the generated `COMMANDS.md` reference;
  other files in the directory may be absent.
- `.planning/` - local-only historical planning/project memory; gitignored and absent from normal public clones.

## What To Read Before Changing

| Change area | Read first |
|---|---|
| Session, reconnect, or printer switching | [Architecture](architecture.md), then [MoonrakerService.kt](../../app/src/main/java/works/mees/jiib/service/MoonrakerService.kt), [SpineHandle.kt](../../app/src/main/java/works/mees/jiib/di/SpineHandle.kt), [PrinterStateStore.kt](../../app/src/main/java/works/mees/jiib/state/PrinterStateStore.kt) |
| Navigation or screen hosting | [Architecture](architecture.md), then [AppShell.kt](../../app/src/main/java/works/mees/jiib/ui/shell/AppShell.kt) and [NavDest.kt](../../app/src/main/java/works/mees/jiib/ui/route/NavDest.kt) |
| Screen behavior visible to users | [User manual](../manual/README.md), [UI design system](../ui_design/README.md), and the relevant `*Screen.kt` / `*Holder.kt` |
| Testing strategy or CI expectations | [Testing](testing.md) |
| Release packaging, signing, or version bumps | [Release](release.md) |
| Durable architecture decisions | [Doc Maintenance](doc-maintenance.md) and existing [ADRs](../adr/) |
| Command contracts | [Architecture](architecture.md), [generated command reference](../commands/COMMANDS.md), tracked `catalog.json`/`printer-matrix.json`, [CommandRegistry.kt](../../app/src/main/java/works/mees/jiib/command/CommandRegistry.kt), and the command drift tests |

## Current Source Of Truth

Current maintainer facts live here. If a tracked doc and a local-only planning note disagree, trust
`docs/maintainer/` unless the planning note is explicitly cited by a current maintainer doc.
