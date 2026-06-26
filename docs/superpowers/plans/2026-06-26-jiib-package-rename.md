# jiib Package/Symbol Rename — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the app's internal identity from `dinghy`/`works.mees.dinghy` to `jiib`/`works.mees.jiib` — package namespace, applicationId, `Dinghy*` code symbols, Moonraker-visible strings, build tooling, and live UI-law docs — with the app compiling, passing its full test suite, and launching on-device under the new identity.

**Architecture:** Ordered, case-aware replacement passes on branch `rename/works-mees-jiib`, operating over **tracked files only** (never `build/` or `__pycache__`). Each slice ends in a grep + compile checkpoint. Safety net = compiler + the ~250-file unit suite + an exemption-aware `git grep` gate + an on-device launch smoke (manifest class-loading and macrobench `setClassName` are runtime-resolved — only a real launch proves them). Spec: `docs/superpowers/specs/2026-06-26-jiib-package-rename-design.md`.

**Tech Stack:** Kotlin / Jetpack Compose, Gradle (AGP 8.7.x), Windows-side build via `E:\Android\gw.bat`, adb to two devices.

## Global Constraints

- **Branch:** all work on `rename/works-mees-jiib` (already created). Never commit the rename to `master`.
- **New package root:** `works.mees.jiib` (keep `mees` owner segment; swap only `dinghy`).
- **Brand casing:** user-facing / Moonraker-visible text is lowercase `jiib`. Kotlin types are PascalCase `Jiib*`. All-caps tokens are `JIIB`. All three are correct.
- **Replacement scope (a git pathspec — reused throughout; tracked files only):**
  `app/src macrobenchmark/src tools docs/ui_design docs/adr CLAUDE.md app/build.gradle.kts macrobenchmark/build.gradle.kts settings.gradle.kts gradle.properties gradle/libs.versions.toml app/proguard-rules.pro app/lint-baseline.xml`
  Use `git grep -lzI … | xargs -0 -r sed -i …` (the `-I` skips binaries, `-z`/`-0` are path-safe, `-r` no-ops on empty lists).
- **⚠ EXEMPTIONS — never rename; the gate excludes them. Verbatim exclude-regex:** `\[\[dinghy|dinghy-|dinghy\.js|theme_theory|dinghyboundary`
  - `[[dinghy-*]]` **and** bare `dinghy-<kebab>` → assistant **memory slugs** (e.g. `dinghy-compose-write-scope-cancellation`, bracketed or not). The `dinghy-` exclusion covers all of them.
  - `dinghy.js` / `../theme_theory/app/dinghy.js` / `theme_theory` → a real file in the **sibling repo**.
  - `dinghy-display` → the repo/dir/skill slug (CLIENT_URL, `CLAUDE.md` build-env, `sketch-findings-dinghy-display`); matched by `dinghy-`. **Exception:** `settings.gradle.kts` `rootProject.name` IS renamed (Task 5) — it is the one `dinghy-display` we change.
  - `dinghyboundary` → an arbitrary MJPEG boundary token embedded in **binary** `.bin` fixtures; renaming would desync the test data.
- **OUT OF SCOPE (left as historical/data, like `.planning`):** `docs/commands/*.json|*.jsonl` (captured Moonraker payloads), `docs/view_specific_notes/`, `docs/moonraker-capabilities.md`, `docs/request-cadence-contract.md`, `docs/top-down-audit-roadmap.md`, all of `.planning/`, and `docs/superpowers/` (the rename docs themselves).
- **Build command (Windows-side; `./gradlew` does NOT work from WSL):**
  `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>" 2>&1 | tr -d '\r' | tail -40`
  Exit code is authoritative. **One Gradle build at a time** (concurrent builds corrupt Kotlin caches on drvfs).
- **Stale-APK trap:** before any on-device check, force-rebuild (`--rerun-tasks`) and verify the APK mtime is newer than the last rename commit before `adb install`.
- **Test devices (push the matching ABI slice to BOTH):** flox = Nexus 7 2013, `armeabi-v7a`, adb `0a64b42e`. moto = Moto G Play 2024, `arm64-v8a`, adb `ZY22LBDRM9`. adb: `E:\Android\Sdk\platform-tools\adb.exe`.
- **Consequence (expected, not a bug):** new applicationId = fresh `/data/data/works.mees.jiib/`; saved settings reset on both devices and the old `works.mees.dinghy` app installs side-by-side.

---

### Task 1: Wholesale backup point

**Files:** none (git ref only).

- [ ] **Step 1: Tag the pre-rename state**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
git tag -a archive/dinghy-pre-rename -m "Wholesale backup before dinghy->jiib internal rename. Restore: git reset --hard archive/dinghy-pre-rename" HEAD
```

- [ ] **Step 2: Verify**

Run: `git show -s --oneline archive/dinghy-pre-rename`
Expected: prints the current HEAD commit (`60dd2dab …` or later).

---

### Task 2: Identity strings + prose → `jiib` (BEFORE any dir move)

Runs first so `"Dinghy Display"` becomes lowercase `"jiib"` (not `"Jiib Display"`). Pure content edits.

**Files (modify):** across the replacement scope — notably `net/ConnectionProbe.kt`, `net/MoonrakerSession.kt`, `service/MoonrakerService.kt`, `prompt/PromptModel.kt`, `prompt/PromptEngine.kt`, `prompt/PromptReducer.kt`, `prompt/PromptFixtureTest.kt`, `prompt/PromptReducerTest.kt`, `DinghyApp.kt`, `theme/StatusSlot.kt`, `theme/TokenBridge.kt`, `bench/SyntheticFeed.kt`, `tools/ws-capture.py`, `tools/spoolman-probe.py`, `tools/oklch-bake/bake_tokens.py`, `app/src/test/resources/golden/*.json`, `app/src/main/res/values/strings.xml`, `docs/ui_design/THEMING.md`.

- [ ] **Step 1: Literal/string passes (case-sensitive, exemption-safe)**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
SCOPE="app/src macrobenchmark/src tools docs/ui_design docs/adr CLAUDE.md app/build.gradle.kts settings.gradle.kts gradle.properties gradle/libs.versions.toml app/proguard-rules.pro"
# brand literal (has a space — never collides with Theme.DinghyDisplay) -> lowercase jiib
git grep -lzI -e 'Dinghy Display' -- $SCOPE | xargs -0 -r sed -i 's/Dinghy Display/jiib/g'
# CLIENT_URL host swap (keeps the dinghy-display slug); app + python tools + the DO-NOT-REGRESS comment
git grep -lzI -e 'mees.works/dinghy-display' -- $SCOPE | xargs -0 -r sed -i 's#https://mees.works/dinghy-display#https://github.com/mrmees/dinghy-display#g'
# frontendId value + bake_tokens path-component "dinghy" + test fixtures asserting frontendId
git grep -lzI -e '"dinghy"' -- $SCOPE | xargs -0 -r sed -i 's/"dinghy"/"jiib"/g'
# mDNS multicast lock
git grep -lzI -e '"dinghy-mdns"' -- $SCOPE | xargs -0 -r sed -i 's/"dinghy-mdns"/"jiib-mdns"/g'
# backtick-wrapped frontendId in KDoc (PromptEngine/PromptReducer)
git grep -lzI -e '`dinghy`' -- $SCOPE | xargs -0 -r sed -i 's/`dinghy`/`jiib`/g'
```

- [ ] **Step 2: Surgical lowercase-prose edits (explicit — exemptions sit nearby)**

Apply each exactly; **do not** touch `TokenBridge.kt` line 9 (`../theme_theory/app/dinghy.js`):

- `theme/StatusSlot.kt:13` — `the dinghy \`heat\` token (D-13: dinghy's \`heat\`` → `the jiib \`heat\` token (D-13: jiib's \`heat\``
- `theme/TokenBridge.kt:8` — `The dinghy-specific glue` → `The jiib-specific glue`
- `theme/TokenBridge.kt:10` — trailing `; dinghy` → `; jiib`
- `theme/TokenBridge.kt:147` — `dinghy's heat` → `jiib's heat`
- `bench/SyntheticFeed.kt:130` — comment `// "DINGHY"-ish, fixed` → `// fixed deterministic seed` (the hex no longer needs the dinghy pun, and avoids a false `DINGHY` straggler)
- `app/src/main/res/values/strings.xml:11` — `parallel_dinghy/` → `parallel_jiib/`
- `docs/ui_design/THEMING.md:34` — `onto dinghy's role` and `Bridge → dinghy tokens` → `…jiib's role`, `Bridge → jiib tokens`
- `docs/ui_design/THEMING.md:97` — `in dinghy, \`heat\`` → `in jiib, \`heat\``

- [ ] **Step 3: Verify functional literals are gone (scoped; ignores binaries)**

```bash
git grep -nI -e '"Dinghy Display"' -e 'mees.works/dinghy-display' -e '"dinghy"' -e '"dinghy-mdns"' -- $SCOPE; echo "exit=$?"
```
Expected: no output, `exit=1`.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "rename(jiib): identity strings + prose -> jiib"
```

---

### Task 3: Package identity `works.mees.dinghy` → `works.mees.jiib`

Moves the four source trees and rewrites both dotted and **slash** package forms (the slash form catches `FontConformanceTest.kt` and `lint-baseline.xml`, which the dotted token misses). Symbols stay `Dinghy*` (Task 4) — this slice still compiles.

**Files:** move `app/src/{main,test,androidTest}/java/works/mees/dinghy/` and `macrobenchmark/src/main/java/works/mees/dinghy/`; modify every scope file containing `works.mees.dinghy` or `works/mees/dinghy`; `app/build.gradle.kts:33,40`; `macrobenchmark/build.gradle.kts:18`.

- [ ] **Step 1: Move the four package dir trees (preserves history)**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
git mv app/src/main/java/works/mees/dinghy        app/src/main/java/works/mees/jiib
git mv app/src/test/java/works/mees/dinghy        app/src/test/java/works/mees/jiib
git mv app/src/androidTest/java/works/mees/dinghy app/src/androidTest/java/works/mees/jiib
git mv macrobenchmark/src/main/java/works/mees/dinghy macrobenchmark/src/main/java/works/mees/jiib
```

- [ ] **Step 2: Rewrite dotted + slash package tokens (covers package decls, imports, FQNs, `FontConformanceTest` srcdir, macrobench `TARGET_PACKAGE`/`BENCH_ACTIVITY`/gfxinfo, gradle namespace+applicationId, lint baseline, doc paths)**

```bash
SCOPE="app/src macrobenchmark/src tools docs/ui_design docs/adr app/build.gradle.kts macrobenchmark/build.gradle.kts app/lint-baseline.xml"
git grep -lzI -e 'works.mees.dinghy' -e 'works/mees/dinghy' -- $SCOPE \
  | xargs -0 -r sed -i -E 's#works\.mees\.dinghy#works.mees.jiib#g; s#works/mees/dinghy#works/mees/jiib#g'
```

- [ ] **Step 3: Verify the package token is gone**

```bash
git grep -nI -e 'works.mees.dinghy' -e 'works/mees/dinghy' -- $SCOPE; echo "exit=$?"
```
Expected: no output, `exit=1`. (Manifest `android:name=".DinghyApp"` is relative and still resolves — class renamed in Task 4.)

- [ ] **Step 4: Compile checkpoint (symbols still `Dinghy*`, package now `jiib`)**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon -Pkotlin.incremental=false" 2>&1 | tr -d '\r' | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "rename(jiib): package works.mees.dinghy -> works.mees.jiib (dirs + dotted/slash token + gradle)"
```

---

### Task 4: Code symbols `Dinghy*` → `Jiib*` (and `DINGHY` → `JIIB`)

**Files:** rename 10 files (now under `…/jiib/`); modify every scope file containing `Dinghy`/`DINGHY`; `AndroidManifest.xml`; `res/values/themes.xml`.

- [ ] **Step 1: Rename the 10 `Dinghy*` files**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
B=app/src/main/java/works/mees/jiib
git mv $B/DinghyApp.kt                          $B/JiibApp.kt
git mv $B/designsystem/icons/DinghyIcon.kt      $B/designsystem/icons/JiibIcon.kt
git mv $B/designsystem/icons/DinghyIcons.kt     $B/designsystem/icons/JiibIcons.kt
git mv $B/designsystem/icons/DinghyIconView.kt  $B/designsystem/icons/JiibIconView.kt
git mv $B/theme/DinghyType.kt                   $B/theme/JiibType.kt
git mv $B/theme/compose/DinghyTheme.kt          $B/theme/compose/JiibTheme.kt
git mv $B/theme/compose/DinghyTextStyle.kt      $B/theme/compose/JiibTextStyle.kt
git mv $B/preview/DinghyPreviews.kt             $B/preview/JiibPreviews.kt
git mv app/src/test/java/works/mees/jiib/designsystem/icons/DinghyIconsTest.kt app/src/test/java/works/mees/jiib/designsystem/icons/JiibIconsTest.kt
git mv app/src/test/java/works/mees/jiib/theme/DinghyTypeTest.kt               app/src/test/java/works/mees/jiib/theme/JiibTypeTest.kt
```

- [ ] **Step 2: Blanket symbol replace (Pascal + all-caps) over the scope**

```bash
SCOPE="app/src macrobenchmark/src tools docs/ui_design docs/adr CLAUDE.md app/build.gradle.kts"
git grep -lzI -e 'Dinghy' -e 'DINGHY' -- $SCOPE \
  | xargs -0 -r sed -i -E 's/Dinghy/Jiib/g; s/DINGHY/JIIB/g'
```
Covers: every `Dinghy*` symbol + `Theme.DinghyDisplay`→`Theme.JiibDisplay` + manifest `.DinghyApp`→`.JiibApp` + `FontConformanceTest` allowlist + `DinghyIcons`/`DinghyType` refs in `docs/ui_design` + `build.gradle.kts:242` comment + `verify_ligatures.py` (`DinghyIcon` regex, `DINGHY_ICONS`) + `DINGHY_YANK`→`JIIB_YANK`.

- [ ] **Step 3: Verify no `Dinghy`/`DINGHY` remains in scope**

```bash
git grep -nI -e 'Dinghy' -e 'DINGHY' -- $SCOPE; echo "exit=$?"
```
Expected: no output, `exit=1`.

- [ ] **Step 4: Spot-check manifest + theme**

```bash
grep -nE 'JiibApp|JiibDisplay' app/src/main/AndroidManifest.xml app/src/main/res/values/themes.xml
```
Expected: `android:name=".JiibApp"`, `android:theme="@style/Theme.JiibDisplay"`, `<style name="Theme.JiibDisplay" …>`.

- [ ] **Step 5: Compile checkpoint**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon -Pkotlin.incremental=false" 2>&1 | tr -d '\r' | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "rename(jiib): Dinghy*/DINGHY symbols -> Jiib*/JIIB (+ manifest, theme, docs, 10 files)"
```

---

### Task 5: `rootProject.name`, lint baseline, tools sanity

- [ ] **Step 1: rootProject.name (the one intentional `dinghy-display` rename)**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
sed -i 's/rootProject.name = "dinghy-display"/rootProject.name = "jiib"/' settings.gradle.kts
grep -n 'rootProject.name' settings.gradle.kts   # expect: rootProject.name = "jiib"
```

- [ ] **Step 2: Regenerate the lint baseline (Task 3 fixed its paths; regen clears stale symbol-bearing messages)**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:updateLintBaseline --no-daemon" 2>&1 | tr -d '\r' | tail -15`
Expected: `BUILD SUCCESSFUL`; then `grep -c 'works/mees/dinghy' app/lint-baseline.xml` → `0`.

- [ ] **Step 3: Tools sanity — the ligature gate must still find its (renamed) target**

Run: `python tools/verify_ligatures.py; echo "exit=$?"`
Expected: it reads `app/src/main/java/works/mees/jiib/designsystem/icons/JiibIcons.kt`, prints its normal summary, `exit=0` (no traceback, no "file not found").

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "rename(jiib): rootProject.name -> jiib, regenerate lint baseline"
```

---

### Task 6: Final verification gate

No new code — this is the proof. **Do not declare done until every check passes.**

- [ ] **Step 1: Exemption-aware `git grep` gate (whole-scope proof; skips binaries & build/)**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
SCOPE="app/src macrobenchmark/src tools docs/ui_design docs/adr CLAUDE.md app/build.gradle.kts macrobenchmark/build.gradle.kts settings.gradle.kts gradle.properties gradle/libs.versions.toml app/proguard-rules.pro app/lint-baseline.xml"
git grep -nI -iE 'dinghy' -- $SCOPE | grep -ivE '\[\[dinghy|dinghy-|dinghy\.js|theme_theory|dinghyboundary'
echo "exit=$?"
```
Expected: **no output**, `exit=1`. Every printed line is a missed rename → fix and re-run. (The exclusions are the documented exemptions; `git grep -I` skips the binary `.bin` fixtures and never reads `build/`/`__pycache__`.)

- [ ] **Step 2: Full static build + test + macrobench compile**

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :macrobenchmark:assembleRelease --no-daemon -Pkotlin.incremental=false --rerun-tasks" 2>&1 | tr -d '\r' | tail -40
```
Expected: `BUILD SUCCESSFUL`. A unit-test failure fails the build. **Watch-point:** the golden JSON fixtures under `app/src/test/resources/golden/` had their `Dinghy Display` client-name strings rewritten in Task 2 — if a golden-comparison test fails because a fixture is captured *input* that must stay verbatim, revert that one file (`git checkout archive/dinghy-pre-rename -- <file>`), add it to the exemptions, and re-run Step 1.

- [ ] **Step 3: On-device launch smoke — flox + moto (debug)**

```bash
ADB=/mnt/e/Android/Sdk/platform-tools/adb.exe
ls app/build/outputs/apk/debug/    # confirm exact split-ABI filenames first
$ADB -s 0a64b42e install -r app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
$ADB -s 0a64b42e shell monkey -p works.mees.jiib -c android.intent.category.LAUNCHER 1
$ADB -s ZY22LBDRM9 install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
$ADB -s ZY22LBDRM9 shell monkey -p works.mees.jiib -c android.intent.category.LAUNCHER 1
```
Expected: both launch as `works.mees.jiib` with no crash (instantiates `JiibApp`, starts `MoonrakerService`); owner confirms connect to a printer + one typed-Navigation hop. (Verify APK mtime > last commit first — stale-APK trap.)

- [ ] **Step 4: On-device install + launch smoke — release APK (R8 path)**

Debug-sign the release APK (`E:\Android\sign-release.bat <in> <out>` — signing is deferred), install on at least one device, launch, confirm no crash. R8 can break reflection/serialization the debug build hides.

- [ ] **Step 5: Codex correctness pass on the full diff**

Run Codex read-only over `git diff archive/dinghy-pre-rename..HEAD` with the spec: confirm no missed surface, no broken exemption, no behavior change. Fix agreeable findings; re-run Steps 1–2 if code changed.

- [ ] **Step 6: Final commit (if Codex/golden fixes were applied), then stop**

```bash
git add -A && git commit -m "rename(jiib): final verification fixes" || echo "nothing to commit — gate clean"
```

Hand back to the owner for the PR-to-`master` decision. **Deferred follow-ups (NOT this plan):** the repo/dir rename (`dinghy-display` → `jiib`), `.claude/skills/sketch-findings-dinghy-display`, the historical `docs/commands/`+`view_specific_notes/` data, and assistant-memory path updates.
