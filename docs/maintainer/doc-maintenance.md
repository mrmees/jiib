# Maintainer Doc Maintenance

This file defines which docs own current facts and how to keep them from drifting.

## Current And Historical Sources

Current maintainer facts live in `docs/maintainer/`.

Local-only historical/project-memory material lives in ignored paths such as:

- `.planning/`
- `docs/commands/` (except the tracked `catalog.json` and `printer-matrix.json` test fixtures)
- `docs/superpowers/` plans and evidence

`docs/superpowers/` is local-only by policy: specs and plans are written there but never committed.
Treat that tree as workflow context, not as current maintainer source of truth.

Those local-only paths may be useful for archaeology, command evidence, and planning context, but they
are not guaranteed to exist in public clones and may contain stale implementation details.

## When To Update Each Doc Area

Update `docs/maintainer/architecture.md` when:

- Session ownership changes.
- Navigation structure changes.
- The service/spine boundary changes.
- A major package/layer responsibility moves.
- Compose/View interop boundaries change.

Update `docs/maintainer/testing.md` when:

- CI commands or workflow behavior changes.
- Local build-helper expectations change.
- Test source layout changes.
- New hardware/manual verification gates become routine.

Update `docs/maintainer/release.md` when:

- Versioning changes.
- Signing or keystore paths change.
- APK split strategy changes.
- Release smoke checks change.

Update `docs/maintainer/doc-maintenance.md` when:

- Doc ownership changes.
- Local-only path policy changes.
- CI docs-hygiene scope changes.
- Stale-term or duplicate-truth rules change.

Update `docs/manual/` when user-visible screen behavior changes.

Update `docs/ui_design/` when visual law, layout grammar, tokens, typography, icon policy, or control
semantics change.

Update the tracked `docs/commands/catalog.json` and `docs/commands/printer-matrix.json` fixtures,
local-only `docs/commands/` evidence, and `CommandCatalogDriftTest` when Moonraker/Klipper/Spoolman
command contracts change.

Add or update an ADR under `docs/adr/` when a durable architecture decision is made and future maintainers
need the rationale.

## Local-Only Link Rule

Tracked docs may mention local-only ignored paths, but they must label them as local-only and use
inline-code or plain-text path mentions. Do not create normal markdown links to ignored local-only paths
from tracked docs. Only link those paths from sources that are intentionally outside public-clone link checks.

## Stale-Term Checks

Before finishing maintainer docs, run any active stale-term list from an ignored/local script or current
implementation checklist. The scan should reject old package/path names, old branch references, retired
drawer names, and old route enum member names. Do not paste the literal rejected strings into tracked
maintainer docs; that makes the docs fail their own check.

Negative historical facts may name retired concepts when needed. If they do, the stale-term scan must
either allowlist those exact lines or the docs should be reworded to avoid the exact tokens.

Run the stale-phrase and dead-link dry-runs in [Testing](testing.md#docs-only-changes). The source of
truth for CI hygiene remains `.github/workflows/ci.yml`.

## Avoiding Duplicate Truth

Prefer links over duplication. If a fact already belongs to the user manual, UI design docs, ADRs, or
release runbook, link to that owner and summarize only what a maintainer needs to choose the right file.
