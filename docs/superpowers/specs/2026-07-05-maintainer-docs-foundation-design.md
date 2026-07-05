# Maintainer docs foundation - design

**Date:** 2026-07-05  
**Status:** approved design, written spec pending owner review  
**Scope:** solo-maintainer docs-first foundation

## Goal

Create a current, concise maintainer documentation surface for jiib so future work can start
from stable docs instead of stale planning notes or phase-history comments.

The first pass is deliberately docs-first. It should make `docs/maintainer/` the current
maintainer source of truth, while treating `.planning/` as historical project memory.

## Owner decisions

1. Start with a solo-maintainer/internal foundation, not public contributor process.
2. Make `docs/` the current maintainer source of truth.
3. Leave `.planning/codebase/*` content intact, but add archive/stale banners that point to the new docs.
4. Defer public-facing files such as `CONTRIBUTING.md`, issue templates, `SECURITY.md`, and changelog policy.
5. Defer automation/tooling changes such as detekt, ktlint, Dokka, Kover, dependency bots, and large-file refactors.

## Deliverables

Phase 0 prerequisite:

- Fix the GitHub Actions workflow so the existing CI/hygiene gate actually runs on `main`.

Add a small maintainer doc set:

```text
docs/maintainer/
  README.md
  architecture.md
  testing.md
  release.md
  doc-maintenance.md
```

Add archive banners to active-looking `.planning/codebase/*.md` files, without rewriting their
historical content.

## Phase 0 prerequisite: revive CI

The existing workflow still targets `master`, while the repository branch is now `main`
(`origin/HEAD -> origin/main`). The last recorded GitHub Actions CI run was on `master`
at `2026-06-26T21:58:17Z`; it failed in the build job. Therefore, `testing.md` must not
document CI as an active gate until the workflow trigger is corrected.

Before or as the first implementation step:

- Change `.github/workflows/ci.yml` push and pull-request branch filters from `master` to `main`.
- Update workflow comments that describe the branch.
- Prune or guard references to gitignored `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`,
  `.planning/ROADMAP.md`, and `.planning/STATE.md` in the hygiene job. Those paths are local-only
  and missing in normal clones; under the current `if grep ...` shell form, missing files can
  pass as "no match" rather than failing loudly.
- Extend the internal dead-link check to cover `docs/maintainer/*.md` explicitly, or replace the
  non-recursive `docs/*.md` glob with a `find docs -name '*.md'` loop that intentionally excludes
  local-only ignored docs when needed.
- Check the old failed build job enough to know whether it still represents a current failure.

This is not a build-logic change. It is a workflow hygiene prerequisite for documenting CI truthfully.

## Maintainer docs

### `docs/maintainer/README.md`

The front door for maintainers and AI coding agents. It states that `docs/maintainer/` is the
current source of truth, gives a short reading order, links to existing user/design/API docs,
and includes a "what to read before changing X" table.

It should link outward rather than duplicate everything. Useful targets include:

- `README.md` for user-facing project overview and build-from-source basics.
- `docs/manual/README.md` for end-user screen behavior.
- `docs/ui_design/README.md` for the visual system and interaction grammar.
- `docs/adr/0001-ui-toolkit-decision.md` for the hybrid Compose/Views decision.

Local-only references may be mentioned, but must be labeled explicitly:

- `.planning/` is gitignored historical project memory, not present in public clones.
- `docs/commands/` is gitignored local command evidence, not present in public clones.
- `docs/superpowers/` is gitignored local spec/plan material, except for files intentionally
  force-added to git.

Avoid ordinary markdown links to local-only ignored paths from tracked public docs unless the link text
clearly says the target is local-only and may be absent in clones.

### `docs/maintainer/architecture.md`

Current architecture only. This file must describe the app as it exists now, not as it existed
in older phase notes.

Required current facts:

- Package namespace is `works.mees.jiib`.
- `JiibApp` owns process setup and DataStore creation.
- `MainActivity` starts the foreground service and hosts the Compose root.
- `AppContainer` is the process-scoped service locator and spine publication surface.
- `MoonrakerService` owns the live Moonraker session and publishes a `SpineHandle`.
- `SpineHandle` is the atomic per-session snapshot exposed to UI.
- `PrinterStateStore` is the live state accumulator.
- `AppShell` uses Navigation-Compose with `NavDest` routes.
- The swipe-up app drawer is gone.
- `ScreenScaffold` exposes Focus/Field regions only; the old gutter region is retired.
- High-churn/render-heavy surfaces still use classic Views through Compose interop where appropriate.

### `docs/maintainer/testing.md`

The verification guide. It should document:

- CI gate: unit tests, Android lint, debug assemble, and hygiene checks.
- Local WSL/Windows build-helper reality.
- Standard host-test command patterns.
- Instrumented/device-test expectations.
- Fixture and golden-resource strategy.
- Manual/device-only checks that are not fully automatable.
- When a docs-only change does not need the Android test suite.

This document should be current and practical rather than exhaustive. Link to deeper test
fixtures or existing docs when needed.

### `docs/maintainer/release.md`

The manual release runbook. It should cover:

- Version fields in `app/build.gradle.kts`.
- Version-name/version-code scheme.
- Release signing via gitignored `local.properties` keys.
- Local keystore location, currently `E:/Android/keystores/jiib-release.jks` if present on the
  maintainer machine. The runbook must never print passwords or commit credential values.
- Release keystore-loss warning: the public release key is intentionally outside git; losing the
  keystore or its credentials orphans installed copies because users would need to uninstall/reinstall.
- A checklist item to confirm the release keystore has a real backup before publishing.
- Per-ABI release APK splits.
- Release build command.
- Install/smoke expectations for release APKs.
- Screenshot/manual freshness checks.
- GitHub release checklist.

This does not automate releases yet. It records the current safe manual procedure.

### `docs/maintainer/doc-maintenance.md`

Rules for keeping docs sane:

- Current maintainer facts live in `docs/maintainer/`.
- `.planning/` is historical project memory unless explicitly linked from a current doc.
- Gitignored local-only docs and evidence (`.planning/`, `docs/commands/`, most of
  `docs/superpowers/`) must be labeled as local-only when mentioned from tracked maintainer docs.
- Add or update ADRs for durable architectural decisions.
- Update `docs/manual/` when user-visible screen behavior changes.
- Update `docs/ui_design/` when design-system law changes.
- Update local-only `docs/commands/` evidence and related drift tests when command contracts change.
- Run stale-term checks manually for now.

## Archive banners

Prepend an archive warning to `.planning/codebase/*.md` files that still read like current docs.
Do not rewrite the historical content in this pass.

Suggested banner shape:

```md
> Archived project-memory note.
> This file may contain stale package names, old navigation concepts, and historical implementation details.
> Current maintainer docs live in `docs/maintainer/`.
```

Closest current-doc targets:

- `.planning/codebase/ARCHITECTURE.md` -> `docs/maintainer/architecture.md`
- `.planning/codebase/TESTING.md` -> `docs/maintainer/testing.md`
- `.planning/codebase/CONVENTIONS.md` -> `docs/maintainer/doc-maintenance.md`
- `.planning/codebase/STRUCTURE.md` -> `docs/maintainer/architecture.md`
- `.planning/codebase/INTEGRATIONS.md` -> `docs/maintainer/architecture.md`
- `.planning/codebase/STACK.md` -> `docs/maintainer/README.md` and `gradle/libs.versions.toml`
- `.planning/codebase/CONCERNS.md` -> `docs/maintainer/README.md`
- `.planning/codebase/UI-REVIEW.md` -> `docs/ui_design/README.md` and `docs/maintainer/README.md`

## Non-goals

- No public contributor docs in this pass.
- No static-analysis, formatting, generated-docs, or coverage tooling changes.
- No build logic changes. The GitHub Actions branch/hygiene fix is allowed as a Phase 0 prerequisite
  because it makes existing verification truthful; it should not touch Gradle or source code.
- No source-code refactors.
- No attempt to make `.planning/codebase/*` current again.

## Verification

This is a docs-only change, so verification should be lightweight:

1. Check internal links in `docs/maintainer/*.md`.
2. Run a targeted stale-term scan on `docs/maintainer/*.md` for old current-fact terms:
   `works.mees.dinghy`, `works/mees/dinghy`, `DinghyApp`, `DinghyTheme`, `DinghyIcon`,
   `DinghyIcons`, `DinghyPreviews`, `AppDrawer`, branch-reference `master`, and old
   `Dest`/gutter-as-current language.
3. Ensure the new docs do not contain the existing hygiene-job stale phrases
   `compileSdk 35` or `Architecture not yet mapped`.
4. Confirm `.planning/codebase/*.md` banners point to valid current docs.
5. Confirm any references to ignored local-only directories are labeled local-only.
6. After the workflow fix, confirm CI would cover the new maintainer docs in its internal-link check.
7. Do not run the full Android suite unless the implementation touches build or source files.

## Success criteria

- A maintainer can answer "how does the app fit together?", "how do I verify a change?",
  and "how do I cut a release?" without reading `.planning/codebase/*`.
- `.planning/codebase/*` no longer presents itself as current truth.
- The new docs are concise enough to maintain and link into detailed existing docs instead of
  copying them wholesale.
- Future public contributor docs can be derived from this maintainer foundation.
