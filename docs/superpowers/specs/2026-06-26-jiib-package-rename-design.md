# Spec: Internal package/symbol rename `dinghy` → `jiib`

**Date:** 2026-06-26
**Status:** Approved (design) — awaiting spec review
**Type:** Mechanical rename / rebrand (internal identity)

## Context

The app's user-facing brand is **already `jiib`** — `app_name` and the logo
content-description were swapped to `jiib` in Phase 18.2 (2026-06-07). What
remains as `dinghy`/`mees` is entirely **internal plumbing** no end user sees:
the package namespace, the `applicationId`, a handful of `Dinghy*` code symbols,
and a few Moonraker-visible strings.

Doing this now is deliberate: it lands **before** APK signing and the first
public release (Phase 29), which is the only clean moment to set a permanent
`applicationId`. Renaming after a real install base exists would fragment it.

### Decisions (locked with owner)

1. **Depth:** Full rename — Moonraker strings + code symbols + package identity.
2. **New package root:** `works.mees.jiib` (keep the personal `mees.works`
   reverse-domain owner segment; swap only the stale `dinghy` segment).
3. **`rootProject.name`:** `dinghy-display` → `jiib`.
4. **Docs:** update only live/forward-looking docs; **do not** rewrite the
   `.planning/` execution log (it is a dated record of when the app *was*
   internally "dinghy").
5. **Repo directory + GitHub remote rename:** **DEFERRED** to a separate step
   (external blast radius — clones, the `mrmees/dinghy-display` remote, and the
   hardcoded repo path in both `CLAUDE.md` files and assistant memory).

## Goal

The app compiles, installs, and passes its full test suite as
`works.mees.jiib`, with no remaining `dinghy`/`Dinghy` reference in **live**
code, config, resources, or forward-looking docs — while git history is
preserved (`git mv`) and a wholesale rollback point exists.

## Naming convention note

The lowercase-with-dots `jiib` brand governs **user-facing text only**. Kotlin
type identifiers follow PascalCase: `DinghyApp` → `JiibApp`, `DinghyType` →
`JiibType`, etc. This is intentional, not an inconsistency.

## Rename inventory (authoritative what → what)

Replacements are **targeted**, not one blind `sed` — lowercase `dinghy`,
PascalCase `Dinghy`, and the literal `"Dinghy Display"` map to three different
results.

### A. Package identity (`works.mees.dinghy` → `works.mees.jiib`)

| Item | Location | Action |
|------|----------|--------|
| `namespace` | `app/build.gradle.kts:33` | → `works.mees.jiib` |
| `applicationId` | `app/build.gradle.kts:40` | → `works.mees.jiib` |
| macrobench `namespace` | `macrobenchmark/build.gradle.kts:18` | → `works.mees.jiib.macrobenchmark` |
| `package` decls + `import`s | all `.kt` (main 555 / test 244 / androidTest 9 / macrobench 3) | replace token `works.mees.dinghy` → `works.mees.jiib` |
| Source dir trees | `app/src/{main,test,androidTest}/java/works/mees/dinghy/`, `macrobenchmark/src/main/java/works/mees/dinghy/` | `git mv` `…/dinghy` → `…/jiib` |
| `lint-baseline.xml` paths | `app/lint-baseline.xml` | regenerate (preferred) or path-replace |

### B. Code symbols (PascalCase `Dinghy` → `Jiib`)

| Symbol → | Defined in | Notes |
|----------|------------|-------|
| `DinghyApp` → `JiibApp` | `DinghyApp.kt` → `JiibApp.kt` | also `AndroidManifest.xml:67` `android:name=".JiibApp"` |
| `DinghyIcon` → `JiibIcon` | `designsystem/icons/DinghyIcon.kt` | rename file |
| `DinghyIcons` → `JiibIcons` | `designsystem/icons/DinghyIcons.kt` | rename file |
| `DinghyType` → `JiibType` | `theme/DinghyType.kt` | rename file |
| `DinghyIconView` → `JiibIconView` | `designsystem/icons/DinghyIconView.kt` | rename file |
| `Theme.DinghyDisplay` → `Theme.JiibDisplay` | `res/values/themes.xml:8` | also `AndroidManifest.xml:76` `android:theme` |
| `DinghySpine` (log tag) → `JiibSpine` | `service/MoonrakerService.kt:339` | logcat only |

A word-level `Dinghy` → `Jiib` over `.kt`/`.xml` covers B uniformly (yields
`JiibApp`, `JiibIcons`, `JiibType`, `JiibIconView`, `JiibDisplay`, `JiibSpine`),
plus matching file renames.

### C. Moonraker-visible / brand strings (lowercase `jiib`)

| Current | Location | New |
|---------|----------|-----|
| `"Dinghy Display"` (CLIENT_NAME) | `net/ConnectionProbe.kt:80` | `"jiib"` |
| `"Dinghy Display"` (clientName default) | `net/MoonrakerSession.kt:86` | `"jiib"` |
| `"dinghy"` (frontendId) | `prompt/PromptModel.kt:24,29` | `"jiib"` |
| `"dinghy-mdns"` (multicast lock) | `DinghyApp.kt:182` (→ `JiibApp.kt`) | `"jiib-mdns"` |

These are handled **before** the blanket `Dinghy`→`Jiib` pass so the client name
becomes `jiib`, not `Jiib Display`.

### D. Config / doc comments (cosmetic, live files only)

`gradle.properties:1`, `gradle/libs.versions.toml:2`, `app/proguard-rules.pro:1`,
`app/build.gradle.kts:242`, `settings.gradle.kts:34` (`rootProject.name`),
`strings.xml:11` & `:500` (comments), project `CLAUDE.md` header, and any doc
that *states* the package/identity. **Excluded:** `.planning/**` history.

## Execution strategy (ordered, scripted, gated)

1. **Backup point:** annotated tag `archive/dinghy-pre-rename` at current `HEAD`
   (wholesale rollback: `git checkout` / `git reset` to the tag).
2. **C first** — replace the four brand strings (so client name → `jiib`).
3. **A** — `git mv` the four dir trees (main / test / androidTest / macrobench;
   `src/debug` no longer exists); then token-replace `works.mees.dinghy` →
   `works.mees.jiib` across all `.kt` (+ any FQN strings).
4. **B** — word-replace `Dinghy` → `Jiib` across `.kt`/`.xml`; rename the five
   `Dinghy*.kt` files; fix manifest `.JiibApp` and `Theme.JiibDisplay`.
5. **Build config** — `namespace`/`applicationId`/macrobench namespace/
   `rootProject.name`; comment sweep (D).
6. **Lint baseline** — regenerate.
7. **Docs** — CLAUDE.md + live identity statements.

## Verification gate (evidence before "done")

- `grep -rniE "works\.mees\.dinghy|dinghy|Dinghy"` over `app/src`,
  `macrobenchmark`, and live config/res returns **zero** (excluding
  `.planning/**`).
- Clean `:app:assembleDebug` + `:app:assembleRelease` succeed.
- Full `:app:testDebugUnitTest` suite green (≈250 files — the primary safety net
  for the symbol rename; includes `FontConformanceTest`).
- `:macrobenchmark` compiles.
- Final unified diff reviewed; hand to **Codex** for a correctness pass before
  declaring complete.

## Known consequences (physics, not bugs)

- **Settings reset on existing installs.** New `applicationId` → new
  `/data/data/works.mees.jiib/` data dir; all 13+ DataStore stores (printers,
  theme, presets…) start fresh on flox/moto. No real users yet → just a
  re-setup. (DataStore *store names* contain no `dinghy`, so no in-app data-key
  migration is needed — only the OS-level data dir changes.)
- **Side-by-side install.** The old `works.mees.dinghy` app remains installed
  until manually uninstalled; the renamed app installs as a distinct package.

## Risks & mitigations

| Risk | Mitigation |
|------|-----------|
| A blind global replace mangles a string/log/identifier | Targeted, ordered replaces (C before B); literal `"Dinghy Display"` handled explicitly |
| kotlinx.serialization / R8 keep rules keyed to old package | `proguard-rules.pro` reviewed in step 5; full release build + tests exercise serialization paths |
| Reflection / string-qualified class refs missed by compiler | Post-rename grep gate catches residual `dinghy`; release smoke on-device |
| History lost in the move | `git mv` for dir trees preserves blame/history |
| Regret / breakage | `archive/dinghy-pre-rename` tag = wholesale rollback |

## Out of scope / follow-ups

- Repo directory + GitHub remote rename (`dinghy-display` → `jiib`) — separate
  step; GitHub redirects old URLs, no urgency.
- Updating assistant memory + `/mnt/e/claude/CLAUDE.md` repo-path references —
  tied to the directory rename above; do together later.
- The deferred pre-release items from the prior cleanup pass (APK signing/
  keystore, stale GSD-enforcement section in CLAUDE.md, e-stop modal gap).
