# jiib Package/Symbol Rename — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the app's internal identity from `dinghy`/`works.mees.dinghy` to `jiib`/`works.mees.jiib` — package namespace, applicationId, `Dinghy*` symbols, lowercase identifiers/prose, Moonraker-visible strings, build tooling, and live UI-law docs — with the app compiling, passing its full test suite, and launching on-device under the new identity.

**Architecture:** Ordered, case-aware replacement passes on branch `rename/works-mees-jiib`, over **tracked files only**. Hard literals and Pascal/caps symbols use scoped `git grep | xargs sed`; bare-lowercase `dinghy` uses a per-file **mask-protect** sed (protect every exemption substring, rename, restore) — correct even for files that mix an exemption and a real target. Safety net = compiler + ~250-file unit suite + an exemption-aware `git grep` gate + on-device launch smoke (manifest class-loading & macrobench `setClassName` are runtime-resolved). Spec: `docs/superpowers/specs/2026-06-26-jiib-package-rename-design.md`.

**Tech Stack:** Kotlin / Jetpack Compose, Gradle (AGP 8.7.x), Windows-side build via `E:\Android\gw.bat`, adb to two devices.

## Global Constraints

- **Branch:** all work on `rename/works-mees-jiib` (already created). Never commit to `master`.
- **New package root:** `works.mees.jiib`. **Casing:** lowercase `jiib` (user/Moonraker text), PascalCase `Jiib*` (types), `JIIB` (all-caps tokens).
- **Replacement scope (git pathspec, reused; tracked files only):**
  `app/src macrobenchmark/src tools docs/ui_design docs/adr CLAUDE.md app/build.gradle.kts macrobenchmark/build.gradle.kts settings.gradle.kts gradle.properties gradle/libs.versions.toml app/proguard-rules.pro app/lint-baseline.xml`
  Always `git grep …` (tracked-only, skips `build/`/`__pycache__`). **Do NOT use `-I`** — `app/src/test/.../command/BedMeshProfileNameTest.kt` contains a literal NUL byte (test data), so `-I` would treat that Kotlin file as binary and hide it from both rename and gate. Instead, the generic-`dinghy` passes exclude the binary MJPEG fixtures by pathspec: `':!app/src/test/resources/fixtures'`. Specific-pattern passes (`works.mees.dinghy`, `Dinghy`, the `"dinghy"`/`Dinghy Display`/CLIENT_URL literals) can't match those binaries, so they need no exclusion.
- **⚠ EXEMPTIONS — never rename. Gate exclude-regex (verbatim):** `\[\[dinghy|dinghy-|dinghy\.js|theme_theory|dinghyboundary`
  - `[[dinghy-*]]` **and** bare `dinghy-<kebab>` → assistant **memory slugs** (bracketed or not, e.g. `dinghy-compose-write-scope-cancellation`). Covered by `dinghy-`.
  - `dinghy.js` / `theme_theory` → a real file in the **sibling repo**.
  - `dinghy-display` → repo/dir/skill slug (CLIENT_URL, `CLAUDE.md` build-env, `sketch-findings-dinghy-display`). Covered by `dinghy-`. **Exception:** `settings.gradle.kts` `rootProject.name` IS renamed (Task 6).
  - `dinghyboundary` → arbitrary MJPEG boundary embedded in **binary** `.bin` fixtures; renaming desyncs the test data. The fixtures dir is excluded by pathspec from the generic-`dinghy` passes.
  - **`dinghy-specific` is a RENAME target** (→ `jiib-specific`), handled in Task 5 before the gate, so the `dinghy-` exclusion only ever shields true slugs.
- **OUT OF SCOPE (left historical, like `.planning`):** `docs/commands/*`, `docs/view_specific_notes/`, `docs/moonraker-capabilities.md`, `docs/request-cadence-contract.md`, `docs/top-down-audit-roadmap.md`, `.planning/`, `docs/superpowers/`.
- **Build command (Windows-side; `./gradlew` does NOT work from WSL):**
  `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>" 2>&1 | tr -d '\r' | tail -40`. Exit code authoritative. **One Gradle build at a time.**
- **Test devices (push matching ABI to BOTH):** flox `0a64b42e` (`armeabi-v7a`), moto `ZY22LBDRM9` (`arm64-v8a`). adb: `E:\Android\Sdk\platform-tools\adb.exe`.
- **Consequence (expected):** new applicationId = fresh data dir; settings reset on both devices, old `works.mees.dinghy` app installs side-by-side.

---

### Task 1: Wholesale backup point

- [ ] **Step 1: Tag**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
git tag -a archive/dinghy-pre-rename -m "Wholesale backup before dinghy->jiib rename. Restore: git reset --hard archive/dinghy-pre-rename" HEAD
```

- [ ] **Step 2: Verify** — Run `git show -s --oneline archive/dinghy-pre-rename`. Expected: current HEAD.

---

### Task 2: Identity literals + prose seeds (BEFORE any dir move)

Runs first so `"Dinghy Display"` becomes lowercase `"jiib"`. Hard string literals only (bare-lowercase identifiers come in Task 5).

- [ ] **Step 1: Literal passes (case-sensitive, exemption-safe)**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
SCOPE="app/src macrobenchmark/src tools docs/ui_design docs/adr CLAUDE.md app/build.gradle.kts settings.gradle.kts gradle.properties gradle/libs.versions.toml app/proguard-rules.pro"
# brand literal (has a space; never collides with Theme.DinghyDisplay) -> lowercase jiib (incl golden JSON, tool headers/client names)
git grep -lz -e 'Dinghy Display' -- $SCOPE | xargs -0 -r sed -i 's/Dinghy Display/jiib/g'
# CLIENT_URL host swap (keeps the dinghy-display slug)
git grep -lz -e 'mees.works/dinghy-display' -- $SCOPE | xargs -0 -r sed -i 's#https://mees.works/dinghy-display#https://github.com/mrmees/dinghy-display#g'
# quoted frontendId value + bake_tokens path-component + test fixtures
git grep -lz -e '"dinghy"' -- $SCOPE | xargs -0 -r sed -i 's/"dinghy"/"jiib"/g'
# mDNS lock + backtick frontendId in KDoc
git grep -lz -e '"dinghy-mdns"' -- $SCOPE | xargs -0 -r sed -i 's/"dinghy-mdns"/"jiib-mdns"/g'
git grep -lz -e '`dinghy`' -- $SCOPE | xargs -0 -r sed -i 's/`dinghy`/`jiib`/g'
```

- [ ] **Step 2: Reword the one all-caps comment that must NOT become "JIIB"** — `bench/SyntheticFeed.kt:130`: change `// "DINGHY"-ish, fixed` to `// fixed deterministic seed` (the hex no longer puns "DINGHY"; prevents a false `DINGHY` straggler in Task 4).

- [ ] **Step 3: Verify** — `git grep -n -e '"Dinghy Display"' -e 'mees.works/dinghy-display' -e '"dinghy"' -e '"dinghy-mdns"' -e 'DINGHY"-ish' -- $SCOPE; echo "exit=$?"`. Expected: no output, `exit=1`.

- [ ] **Step 4: Commit** — `git add -A && git commit -m "rename(jiib): identity literals -> jiib"`

---

### Task 3: Package identity `works.mees.dinghy` → `works.mees.jiib`

Moves the four trees and rewrites **dotted + slash** package forms (slash catches `FontConformanceTest` srcdir + lint baseline). Symbols still `Dinghy*` — compiles.

- [ ] **Step 1: Move the four trees**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
git mv app/src/main/java/works/mees/dinghy        app/src/main/java/works/mees/jiib
git mv app/src/test/java/works/mees/dinghy        app/src/test/java/works/mees/jiib
git mv app/src/androidTest/java/works/mees/dinghy app/src/androidTest/java/works/mees/jiib
git mv macrobenchmark/src/main/java/works/mees/dinghy macrobenchmark/src/main/java/works/mees/jiib
```

- [ ] **Step 2: Rewrite dotted + slash tokens**

```bash
SCOPE="app/src macrobenchmark/src tools docs/ui_design docs/adr app/build.gradle.kts macrobenchmark/build.gradle.kts app/lint-baseline.xml"
git grep -lz -e 'works.mees.dinghy' -e 'works/mees/dinghy' -- $SCOPE \
  | xargs -0 -r sed -i -E 's#works\.mees\.dinghy#works.mees.jiib#g; s#works/mees/dinghy#works/mees/jiib#g'
```

- [ ] **Step 3: Verify** — `git grep -n -e 'works.mees.dinghy' -e 'works/mees/dinghy' -- $SCOPE; echo "exit=$?"`. Expected: no output, `exit=1`.

- [ ] **Step 4: Compile checkpoint** — Run `… "E:\Android\gw.bat :app:assembleDebug --no-daemon -Pkotlin.incremental=false" … | tail -20`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit** — `git add -A && git commit -m "rename(jiib): package -> works.mees.jiib (dirs + dotted/slash + gradle)"`

---

### Task 4: PascalCase + all-caps symbols `Dinghy`→`Jiib`, `DINGHY`→`JIIB`

- [ ] **Step 1: Rename the 10 `Dinghy*` files**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
B=app/src/main/java/works/mees/jiib
git mv $B/DinghyApp.kt                         $B/JiibApp.kt
git mv $B/designsystem/icons/DinghyIcon.kt     $B/designsystem/icons/JiibIcon.kt
git mv $B/designsystem/icons/DinghyIcons.kt    $B/designsystem/icons/JiibIcons.kt
git mv $B/designsystem/icons/DinghyIconView.kt $B/designsystem/icons/JiibIconView.kt
git mv $B/theme/DinghyType.kt                  $B/theme/JiibType.kt
git mv $B/theme/compose/DinghyTheme.kt         $B/theme/compose/JiibTheme.kt
git mv $B/theme/compose/DinghyTextStyle.kt     $B/theme/compose/JiibTextStyle.kt
git mv $B/preview/DinghyPreviews.kt            $B/preview/JiibPreviews.kt
git mv app/src/test/java/works/mees/jiib/designsystem/icons/DinghyIconsTest.kt app/src/test/java/works/mees/jiib/designsystem/icons/JiibIconsTest.kt
git mv app/src/test/java/works/mees/jiib/theme/DinghyTypeTest.kt               app/src/test/java/works/mees/jiib/theme/JiibTypeTest.kt
```

- [ ] **Step 2: Blanket Pascal + caps replace**

```bash
SCOPE="app/src macrobenchmark/src tools docs/ui_design docs/adr CLAUDE.md app/build.gradle.kts"
git grep -lz -e 'Dinghy' -e 'DINGHY' -- $SCOPE | xargs -0 -r sed -i -E 's/Dinghy/Jiib/g; s/DINGHY/JIIB/g'
```
Covers all `Dinghy*` symbols, `Theme.DinghyDisplay`, manifest `.DinghyApp`, `FontConformanceTest` allowlist, `docs/ui_design` symbol refs, `build.gradle.kts:242` comment, `verify_ligatures.py` (`DinghyIcon` regex, `DINGHY_ICONS`), `DINGHY_YANK`, `spoolman-probe.py` "Dinghy" prose.

- [ ] **Step 3: Verify** — `git grep -n -e 'Dinghy' -e 'DINGHY' -- $SCOPE; echo "exit=$?"`. Expected: no output, `exit=1`.

- [ ] **Step 4: Spot-check** — `grep -nE 'JiibApp|JiibDisplay' app/src/main/AndroidManifest.xml app/src/main/res/values/themes.xml`. Expected: `.JiibApp`, `@style/Theme.JiibDisplay`, `<style name="Theme.JiibDisplay" …>`.

- [ ] **Step 5: Compile checkpoint** — `… :app:assembleDebug …`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit** — `git add -A && git commit -m "rename(jiib): Dinghy*/DINGHY symbols -> Jiib*/JIIB (+ manifest, theme, docs, 10 files)"`

---

### Task 5: Bare-lowercase `dinghy` identifiers/prose (universal mask-protect)

Only lowercase `dinghy` remains (package + symbols done). Apply ONE per-file sed to **every** `dinghy`-bearing file — no skipping. It protects each exemption substring, renames the rest, then restores. This is correct even for files that **mix** an exemption and a real target (e.g. `THEMING.md` has both `theme_theory` and `dinghy tokens`; `TokenBridge.kt` has `dinghy.js` and `dinghy-specific`).

- [ ] **Step 1: Universal mask-protect rename (null-delimited file list — path-safe)**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
SCOPE="app/src macrobenchmark/src tools docs/ui_design docs/adr CLAUDE.md"
git grep -lz 'dinghy' -- $SCOPE ':!app/src/test/resources/fixtures' | while IFS= read -r -d '' f; do
  sed -i -E '
    s/dinghy-specific/jiib-specific/g;                                   # the one app-descriptive hyphenated target, BEFORE the dinghy- guard
    s/dinghy\.js/@@A@@/g; s/\[\[dinghy/@@B@@/g; s/dinghyboundary/@@D@@/g; s/dinghy-/@@C@@/g;
    s/dinghy/jiib/g;                                                     # bare prose/identifiers (dinghyOpts, val dinghy, parallel_dinghy, dinghy tokens, ; dinghy, dinghy'"'"'s)
    s/@@A@@/dinghy.js/g; s/@@B@@/[[dinghy/g; s/@@C@@/dinghy-/g; s/@@D@@/dinghyboundary/g
  ' "$f"
  echo "processed: $f"
done
```
Renames: bare `dinghy`/`dinghyOpts`/`dinghy's`/`parallel_dinghy`/`dinghy tokens`/`boundary=dinghy`/`dinghy-specific`. Preserves: `[[dinghy-*]]`, unbracketed `dinghy-<slug>`, `dinghy-display`, `dinghy.js`, `dinghyboundary` (incl. the binary-coupled fixtures, which `-I` never reads anyway).

- [ ] **Step 2: Verify (gate-equivalent over the lowercase scope)**

```bash
git grep -n -iE 'dinghy' -- $SCOPE ':!app/src/test/resources/fixtures' | grep -ivE '\[\[dinghy|dinghy-|dinghy\.js|theme_theory|dinghyboundary'; echo "exit=$?"
```
Expected: no output, `exit=1`. Any printed line = a non-exempt `dinghy` the pipeline missed → fix and re-run.

- [ ] **Step 3: Commit** — `git add -A && git commit -m "rename(jiib): bare-lowercase dinghy identifiers/prose -> jiib"`

---

### Task 6: `rootProject.name`, lint baseline, tools sanity

- [ ] **Step 1: rootProject.name (the one intentional `dinghy-display` rename)**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
sed -i 's/rootProject.name = "dinghy-display"/rootProject.name = "jiib"/' settings.gradle.kts
grep -n 'rootProject.name' settings.gradle.kts   # expect: "jiib"
```

- [ ] **Step 2: Regenerate lint baseline + confirm lint is consistent**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:updateLintBaseline :app:lintDebug --no-daemon" 2>&1 | tr -d '\r' | tail -15
grep -c 'works/mees/dinghy' app/lint-baseline.xml   # expect: 0
```
Expected: `BUILD SUCCESSFUL` (a clean `lintDebug` against the fresh baseline proves no new issues from the rename).

- [ ] **Step 3: Tools sanity** — `python tools/verify_ligatures.py; echo "exit=$?"`. Expected: reads `…/works/mees/jiib/designsystem/icons/JiibIcons.kt`, prints its summary, `exit=0`.

- [ ] **Step 4: Commit** — `git add -A && git commit -m "rename(jiib): rootProject.name -> jiib, regenerate lint baseline"`

---

### Task 7: Final verification gate

No new code — the proof. **Do not declare done until every check passes.**

- [ ] **Step 1: Exemption-aware `git grep` gate**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
SCOPE="app/src macrobenchmark/src tools docs/ui_design docs/adr CLAUDE.md app/build.gradle.kts macrobenchmark/build.gradle.kts settings.gradle.kts gradle.properties gradle/libs.versions.toml app/proguard-rules.pro app/lint-baseline.xml"
git grep -n -iE 'dinghy' -- $SCOPE ':!app/src/test/resources/fixtures' | grep -ivE '\[\[dinghy|dinghy-|dinghy\.js|theme_theory|dinghyboundary'; echo "exit=$?"
```
Expected: **no output**, `exit=1`. Any printed line = a missed rename.

- [ ] **Step 2: Eyeball the `dinghy-` exclusions (guard against the broad exemption hiding a real miss)** — `git grep -n 'dinghy-' -- $SCOPE ':!app/src/test/resources/fixtures'`. Confirm EVERY hit is a memory slug (`dinghy-<kebab>`) or the `dinghy-display` repo/skill slug — nothing app-descriptive.

- [ ] **Step 3: Full static build + test + macrobench compile**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :macrobenchmark:assembleRelease --no-daemon -Pkotlin.incremental=false --rerun-tasks" 2>&1 | tr -d '\r' | tail -40
```
Expected: `BUILD SUCCESSFUL`. **Watch-point:** Task 2 rewrote `Dinghy Display` client-name strings inside `app/src/test/resources/golden/*.json`; if a golden-comparison test fails because a fixture is captured *input* that must stay verbatim, restore that one file (`git checkout archive/dinghy-pre-rename -- <file>`), re-run Step 1.

- [ ] **Step 4: On-device launch smoke — flox + moto (debug)**

```bash
ADB=/mnt/e/Android/Sdk/platform-tools/adb.exe
ls app/build/outputs/apk/debug/    # confirm exact split-ABI filenames
# stale-APK guard: APK must be newer than the branch tip
find app/build/outputs/apk/debug -name '*.apk' -newer "$(git rev-parse --git-path refs/heads/rename/works-mees-jiib)" -printf '%f  NEWER-OK\n' || echo "STALE — rebuild with --rerun-tasks"
$ADB -s 0a64b42e install -r app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
$ADB -s 0a64b42e shell monkey -p works.mees.jiib -c android.intent.category.LAUNCHER 1
$ADB -s ZY22LBDRM9 install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
$ADB -s ZY22LBDRM9 shell monkey -p works.mees.jiib -c android.intent.category.LAUNCHER 1
```
Expected: both launch as `works.mees.jiib`, no crash (instantiates `JiibApp`, starts `MoonrakerService`); owner confirms connect + one typed-Navigation hop.

- [ ] **Step 5: Release APK install + launch smoke (R8 path)** — debug-sign the release APK (`E:\Android\sign-release.bat <in> <out>`), install on ≥1 device, launch, confirm no crash.

- [ ] **Step 6: Codex correctness pass** — read-only over `git diff archive/dinghy-pre-rename..HEAD` + the spec: confirm no missed surface, no broken exemption, no behavior change. Fix agreeable findings; re-run Steps 1 & 3 if code changed.

- [ ] **Step 7: Final commit** — `git add -A && git commit -m "rename(jiib): final verification fixes" || echo "gate clean"`. Then hand back to the owner for the PR-to-`master` decision.

**Deferred follow-ups (NOT this plan):** repo/dir rename (`dinghy-display`→`jiib`), `.claude/skills/sketch-findings-dinghy-display`, historical `docs/commands/`+`view_specific_notes/`, assistant-memory path updates.
