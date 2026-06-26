# Spec: Internal package/symbol rename `dinghy` → `jiib`

**Date:** 2026-06-26
**Status:** Approved (design) — revised after Codex review — awaiting spec re-review
**Type:** Mechanical rename / rebrand (internal identity)

## Context

The app's user-facing brand is **already `jiib`** — `app_name` and the logo
content-description were swapped in Phase 18.2 (2026-06-07). What remains as
`dinghy`/`mees` is **internal plumbing** no end user sees: the package
namespace, the `applicationId`, the `Dinghy*` code symbols, a few
Moonraker-visible strings, and the `tools/` build scripts.

Doing this now is deliberate: it lands **before** APK signing and the first
public release (Phase 29) — the only clean moment to set a permanent
`applicationId`.

### Decisions (locked with owner)

1. **Depth:** Full rename — Moonraker strings + code symbols + package identity.
2. **New package root:** `works.mees.jiib` (keep the personal `mees.works`
   reverse-domain owner segment; swap only the stale `dinghy` segment).
3. **`rootProject.name`:** `dinghy-display` → `jiib`.
4. **Docs:** update only live/forward-looking docs; **do not** rewrite the
   `.planning/` execution log.
5. **Repo dir + GitHub remote rename:** **DEFERRED** to a separate step.

### CLIENT_URL decision (RESOLVED with owner)

- The project will be **hosted on GitHub**, not the personal `mees.works` site.
  So `CLIENT_URL` / `clientUrl` changes `https://mees.works/dinghy-display` →
  **`https://github.com/mrmees/dinghy-display`** (drops the personal domain;
  `mees` survives only as the GitHub *handle* `mrmees`). Same update to
  `tools/ws-capture.py`, `tools/spoolman-probe.py`, and the `DO NOT regress`
  comment at `MoonrakerService.kt:149`. The trailing `dinghy-display` is the
  **current GitHub repo slug** — it stays until the deferred repo rename (GitHub
  auto-redirects), and is therefore a documented grep-gate exemption (§E).

## Naming convention note

The lowercase-with-dots `jiib` brand governs **user-facing text only**. Kotlin
type identifiers stay PascalCase: `DinghyApp` → `JiibApp`, `DinghyType` →
`JiibType`. Intentional, not an inconsistency.

---

## Rename inventory (authoritative what → what)

Replacements are **targeted and ordered**, never one blind `sed`. The lowercase
`dinghy` token is the dangerous one — see the **Exception Table (§E)** first.

### A. Package identity (`works.mees.dinghy` → `works.mees.jiib`)

| Item | Location | Action |
|------|----------|--------|
| `namespace` | `app/build.gradle.kts:33` | → `works.mees.jiib` |
| `applicationId` | `app/build.gradle.kts:40` | → `works.mees.jiib` |
| macrobench `namespace` | `macrobenchmark/build.gradle.kts:18` | → `works.mees.jiib.macrobenchmark` |
| `package` decls + `import`s + FQNs | all `.kt`: **main 301 / test 244 / androidTest 9 / macrobench 2** | replace token `works.mees.dinghy` → `works.mees.jiib` |
| Source dir trees (**4** — `src/debug` is gone) | `app/src/{main,test,androidTest}/java/works/mees/dinghy/`, `macrobenchmark/src/main/java/works/mees/dinghy/` | `git mv` `…/dinghy` → `…/jiib` |
| `lint-baseline.xml` paths | `app/lint-baseline.xml` | regenerate |

### B. Code symbols (PascalCase `Dinghy` → `Jiib`) — **10 files**

All `Dinghy*.kt` files (8 main + 2 test) rename, plus every `Dinghy*` symbol
reference (handled uniformly by a word-level `Dinghy` → `Jiib` pass over
`.kt`/`.xml`):

| File | Symbol |
|------|--------|
| `DinghyApp.kt` | `DinghyApp` (+ manifest `android:name=".JiibApp"`) |
| `designsystem/icons/DinghyIcon.kt` | `DinghyIcon` |
| `designsystem/icons/DinghyIcons.kt` | `DinghyIcons` |
| `designsystem/icons/DinghyIconView.kt` | `DinghyIconView` |
| `theme/DinghyType.kt` | `DinghyType` |
| `theme/compose/DinghyTheme.kt` | `DinghyTheme` |
| `theme/compose/DinghyTextStyle.kt` | `DinghyTextStyle` |
| `preview/DinghyPreviews.kt` | `DinghyPreviews` |
| `test/.../DinghyIconsTest.kt` | `DinghyIconsTest` |
| `test/.../DinghyTypeTest.kt` | `DinghyTypeTest` |

Also: `Theme.DinghyDisplay` → `Theme.JiibDisplay` (`res/values/themes.xml:8` +
`AndroidManifest.xml:76`), `DinghySpine` log tag → `JiibSpine`
(`service/MoonrakerService.kt:339`), and the `FontConformanceTest` allowlist
paths that reference renamed files.

### C. Moonraker-visible / brand strings (lowercase `jiib`)

| Current | Location | New |
|---------|----------|-----|
| `"Dinghy Display"` (CLIENT_NAME) | `net/ConnectionProbe.kt:80` | `"jiib"` |
| `"Dinghy Display"` (clientName default) | `net/MoonrakerSession.kt:86` | `"jiib"` |
| `"dinghy"` (frontendId) | `prompt/PromptModel.kt:29` | `"jiib"` |
| `"dinghy-mdns"` (multicast lock) | `DinghyApp.kt:182` (→ `JiibApp.kt`) | `"jiib-mdns"` |
| frontendId identity prose | `prompt/PromptEngine.kt:53`, `PromptReducer.kt:38`, `PromptModel.kt:24` | `Dinghy`→`Jiib`, `dinghy`→`jiib` |
| app-name prose | `theme/StatusSlot.kt:13` | `dinghy`→`jiib` |

Handled **before** the blanket `Dinghy`→`Jiib` so the client name becomes
`jiib`, not `Jiib Display`. **`CLIENT_URL`** (`ConnectionProbe.kt:82`,
`MoonrakerSession.kt:91`, `MoonrakerService.kt:149` comment) changes its host:
`https://mees.works/dinghy-display` → `https://github.com/mrmees/dinghy-display`
(per the resolved CLIENT_URL decision; the `dinghy-display` slug is retained and
exempt — §E).

### D. Build tooling — `tools/` (FUNCTIONAL, not cosmetic)

These break silently if missed (compile never sees them):

| File | Reference | Action |
|------|-----------|--------|
| `tools/verify_ligatures.py:43` | path `…/works/mees/dinghy/.../DinghyIcons.kt` + `DinghyIcons` prose | → `…/works/mees/jiib/.../JiibIcons.kt` + `JiibIcons` |
| `tools/oklch-bake/bake_tokens.py:168,226` | **emits** `package works.mees.dinghy.theme` + path components | → `works.mees.jiib.theme` / `…,"jiib","theme",…` |
| `tools/gfxinfo-parser/parse_framestats.py:29` | `dumpsys gfxinfo works.mees.dinghy` | → `works.mees.jiib` |
| `tools/oklch-ramp-oracle.mjs:10` | comment `works.mees.dinghy.theme.Palette` | → `works.mees.jiib.theme.Palette` |
| `tools/ws-capture.py:89`, `tools/spoolman-probe.py:40` | `CLIENT_URL` literal | → `https://github.com/mrmees/dinghy-display` (mirror the app) |

### E. ⚠ Lowercase `dinghy` EXCEPTION TABLE (leave these ALONE)

A blind `s/dinghy/jiib/` would corrupt these. They are **exempt** from rename
**and** from the grep-zero gate:

| Pattern | Why exempt | Example |
|---------|-----------|---------|
| `[[dinghy-*]]` wiki-links | Cross-references to **assistant memory slugs** named `dinghy-*` (files outside the repo). ~40+ occurrences across KDoc. | `[[dinghy-never-pick-icons-ask]]`, `[[dinghy-compose-write-scope-cancellation]]`, `[[dinghy-display-gradle-hang-interop]]` |
| `dinghy.js` / `../theme_theory/app/dinghy.js` | References a real file in the **sibling `theme_theory` repo** by its actual name. | `theme/TokenBridge.kt:8` |
| `dinghy-display` slug in CLIENT_URL | After the host swap to `github.com/mrmees/dinghy-display`, the trailing slug is the **current repo name** — follows the deferred repo rename. | `ConnectionProbe.kt:82`, `MoonrakerSession.kt:91`, `MoonrakerService.kt:149` (comment), 2 python tools |

The lowercase pass renames the §C identity strings/prose ONLY; everything else
lowercase is either the `works.mees.dinghy` package token (§A) or one of the
exemptions above.

### F. Macrobenchmark runtime launch constants (behavior-critical)

String-qualified — caught by the §A token replace, but call out & verify on a
real run (not just compile):

| Location | Constant |
|----------|----------|
| `RenderBenchmark.kt:75,76` | `TARGET_PACKAGE`, `BENCH_ACTIVITY = "works.mees.dinghy[.bench.BenchActivity]"` |
| `ToolkitBenchmark.kt:97,98` | same pair |
| `RenderBenchmark.kt:24` | `gfxinfo works.mees.dinghy framestats` comment |

### G. Config / doc comments (cosmetic, live files only)

`gradle.properties:1`, `gradle/libs.versions.toml:2`, `app/proguard-rules.pro:1`
(comment only — no package-keyed keep rules), `app/build.gradle.kts:242`,
`settings.gradle.kts:34` (`rootProject.name`), `strings.xml:11` & `:500`
(comments), project `CLAUDE.md` header. **Excluded:** `.planning/**` history.

---

## Execution strategy (ordered, scripted, gated)

1. **Backup point:** annotated tag `archive/dinghy-pre-rename` at `HEAD`.
2. **§C first** — replace the brand identity strings/prose (client name → `jiib`,
   frontendId → `jiib`, mdns, identity prose) **and** swap the `CLIENT_URL` host
   to `https://github.com/mrmees/dinghy-display` (app + 2 python tools + the
   `MoonrakerService.kt:149` comment), keeping the `dinghy-display` slug.
3. **§A** — `git mv` the four dir trees; token-replace `works.mees.dinghy` →
   `works.mees.jiib` across all `.kt`, **excluding** the §E exemption lines.
4. **§B** — word-replace `Dinghy` → `Jiib` over `.kt`/`.xml`; rename the 10
   `Dinghy*.kt` files; fix manifest `.JiibApp` + `Theme.JiibDisplay`; update
   `FontConformanceTest` allowlist.
5. **§D** — update the `tools/` scripts (path/package/symbol).
6. **Build config + §G** — `namespace`/`applicationId`/macrobench namespace/
   `rootProject.name`; comment sweep.
7. **Lint baseline** — regenerate.
8. **Docs** — CLAUDE.md + live identity statements.

## Verification gate (evidence before "done")

1. **Targeted grep gate:** `grep -rniE "works\.mees\.dinghy|dinghy|Dinghy"` over
   `app/src`, `macrobenchmark`, `tools`, and live config/res returns **only the
   documented §E exemptions** (`[[dinghy-*]]` memory links,
   `dinghy.js`/`theme_theory` refs, and the `dinghy-display` repo slug inside the
   GitHub `CLIENT_URL`). Anything else = fail.
2. **Static build:** clean `:app:assembleDebug` + `:app:assembleRelease` +
   `:app:testDebugUnitTest` (≈250 files) + `:macrobenchmark` compiles.
3. **Runtime smoke (REQUIRED — unit tests do NOT cover manifest class loading
   or macrobench launchability):** on **flox + moto**, install the renamed
   **debug** APK, launch it (instantiates `JiibApp` via the manifest, starts
   `MoonrakerService`), connect to a printer, drive one typed-Navigation hop.
   Then repeat an **install + launch** smoke on the **release** APK (R8 path).
4. **Optional:** `:app:connectedDebugAndroidTest` if a device is wired for it.
5. Final unified diff → **Codex** correctness pass before declaring complete.

## Known consequences (physics, not bugs)

- **Settings reset on existing installs.** New `applicationId` → new
  `/data/data/works.mees.jiib/` data dir; all 14 DataStore stores start fresh on
  flox/moto (store *names* contain no `dinghy`, so no in-app key migration —
  only the OS data dir changes). No real users yet → just a re-setup.
- **Side-by-side install.** The old `works.mees.dinghy` app stays installed
  until manually uninstalled.

## Risks & mitigations

| Risk | Mitigation |
|------|-----------|
| Blind lowercase sweep corrupts memory links / external refs | §E exception table + grep gate that *expects* the exemptions |
| `tools/` scripts silently break post-rename | §D treats them as functional, in-scope |
| Manifest `.JiibApp` / macrobench `setClassName` wrong (compile-clean, runtime-broken) | Required on-device launch smoke (debug + release) |
| R8/serialization keyed to old package | proguard confirmed comment-only; release build + tests exercise serialization |
| History lost in the move | `git mv` preserves blame |
| Regret / breakage | `archive/dinghy-pre-rename` tag = wholesale rollback |

## Out of scope / follow-ups

- **Repo dir + GitHub remote rename** (`dinghy-display` → `jiib`) — separate
  step; carries the deferred `CLIENT_URL` update with it.
- **`.claude/skills/sketch-findings-dinghy-display`** — project skill whose
  *directory name* is the project slug (invoked by name, referenced in
  CLAUDE.md). Rename with the repo/dir step, not here.
- Updating assistant memory + `/mnt/e/claude/CLAUDE.md` repo-path references —
  tied to the directory rename.
- Prior-pass deferrals: APK signing/keystore, stale GSD-enforcement section in
  CLAUDE.md, e-stop modal gap.
