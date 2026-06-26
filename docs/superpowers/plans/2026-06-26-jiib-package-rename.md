# jiib Package/Symbol Rename — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the app's internal identity from `dinghy`/`works.mees.dinghy` to `jiib`/`works.mees.jiib` — package namespace, applicationId, `Dinghy*` code symbols, Moonraker-visible strings, and build tooling — with the app compiling, passing its full test suite, and launching on-device under the new identity.

**Architecture:** Ordered, scripted rename slices on branch `rename/works-mees-jiib`. Each slice is independently verifiable (grep + compile). The safety net is the compiler + the ~250-file unit suite + an exemption-aware grep gate + an on-device launch smoke (manifest class-loading and macrobench `setClassName` are runtime-resolved, so only a real launch proves them). Spec: `docs/superpowers/specs/2026-06-26-jiib-package-rename-design.md`.

**Tech Stack:** Kotlin / Jetpack Compose, Gradle (AGP 8.7.x), Windows-side build via `E:\Android\gw.bat`, adb to two devices.

## Global Constraints

- **Branch:** all work on `rename/works-mees-jiib` (already created). Never commit the rename to `master` directly.
- **New package root:** `works.mees.jiib` (keep the `mees` owner segment; swap only `dinghy`).
- **Brand casing:** user-facing/Moonraker-visible text is lowercase `jiib`. Kotlin type identifiers are PascalCase `Jiib*` (e.g. `JiibApp`, `JiibType`). Both are correct.
- **⚠ Lowercase `dinghy` EXEMPTIONS — never rename these (they are NOT the app):**
  - `[[dinghy-*]]` wiki-links → references to assistant **memory slugs** (files outside this repo).
  - `dinghy.js` / `../theme_theory/app/dinghy.js` / `theme_theory` → a real file in the **sibling repo**.
  - `dinghy-display` **slug** wherever it appears → the current repo/dir/skill name (the GitHub `CLIENT_URL`, `CLAUDE.md` build-env paths, and `sketch-findings-dinghy-display`). All follow the deferred repo rename.
- **Build command (Windows-side; `./gradlew` does NOT work from WSL):**
  `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>" 2>&1 | tr -d '\r' | tail -40`
  Exit code is authoritative. **One Gradle build at a time** (concurrent builds corrupt Kotlin caches on drvfs).
- **Stale-APK trap:** before any on-device UAT, force-rebuild (`--rerun-tasks`) and verify the APK mtime is newer than the last rename commit; suspect a stale build before re-diagnosing.
- **Test devices (push the matching ABI slice to BOTH):** flox = Nexus 7 2013, `armeabi-v7a`, adb id `0a64b42e`. moto = Moto G Play 2024, `arm64-v8a`, adb id `ZY22LBDRM9`. adb: `E:\Android\Sdk\platform-tools\adb.exe`.
- **Consequence (expected, not a bug):** new applicationId = fresh `/data/data/works.mees.jiib/`, so saved settings reset on both devices and the old `works.mees.dinghy` app installs side-by-side.

---

### Task 1: Wholesale backup point

**Files:** none (git ref only).

- [ ] **Step 1: Tag the pre-rename state**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
git tag -a archive/dinghy-pre-rename -m "Wholesale backup before dinghy->jiib internal rename. Restore: git reset --hard archive/dinghy-pre-rename" HEAD
```

- [ ] **Step 2: Verify**

Run: `git tag -l 'archive/*' && git show -s --oneline archive/dinghy-pre-rename`
Expected: lists `archive/dinghy-pre-rename` and `archive/gallery-pre-delete`, pointing at the current HEAD commit.

---

### Task 2: §C — Moonraker-visible strings + identity prose (do FIRST)

Done before any blanket pass so `"Dinghy Display"` becomes `"jiib"`, not `"Jiib Display"`. Pure string/comment edits — no structural change.

**Files (modify):**
- `app/src/main/java/works/mees/dinghy/net/ConnectionProbe.kt:80,82`
- `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:86,91`
- `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt:149`
- `app/src/main/java/works/mees/dinghy/prompt/PromptModel.kt:24,29`
- `app/src/main/java/works/mees/dinghy/prompt/PromptEngine.kt:53`
- `app/src/main/java/works/mees/dinghy/prompt/PromptReducer.kt:38`
- `app/src/main/java/works/mees/dinghy/DinghyApp.kt:182`
- `app/src/main/java/works/mees/dinghy/theme/StatusSlot.kt:13`
- `app/src/main/java/works/mees/dinghy/theme/TokenBridge.kt:8,10` (keep line 9 `../theme_theory/app/dinghy.js`)
- `tools/ws-capture.py:89`, `tools/spoolman-probe.py:40`

- [ ] **Step 1: Functional string literals (safe targeted seds)**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
# clientName "Dinghy Display" -> "jiib"
sed -i 's#"Dinghy Display"#"jiib"#g' \
  app/src/main/java/works/mees/dinghy/net/ConnectionProbe.kt \
  app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
# CLIENT_URL host swap (app + comment + 2 python tools); keeps the dinghy-display slug
sed -i 's#https://mees.works/dinghy-display#https://github.com/mrmees/dinghy-display#g' \
  app/src/main/java/works/mees/dinghy/net/ConnectionProbe.kt \
  app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt \
  app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt \
  tools/ws-capture.py tools/spoolman-probe.py
# frontendId value (PromptModel has "dinghy" only on lines 24 & 29)
sed -i 's#"dinghy"#"jiib"#g' app/src/main/java/works/mees/dinghy/prompt/PromptModel.kt
# mDNS lock name
sed -i 's#"dinghy-mdns"#"jiib-mdns"#g' app/src/main/java/works/mees/dinghy/DinghyApp.kt
# backtick-wrapped frontendId value in two KDoc lines (no memory-links in these files)
sed -i 's#`dinghy`#`jiib`#g' \
  app/src/main/java/works/mees/dinghy/prompt/PromptEngine.kt \
  app/src/main/java/works/mees/dinghy/prompt/PromptReducer.kt
```

- [ ] **Step 2: Surgical lowercase-prose edits (explicit — exemptions nearby)**

`StatusSlot.kt:13` — replace:
`* \`Caution\` maps to the dinghy \`heat\` token (D-13: dinghy's \`heat\` IS the caution color).`
with:
`* \`Caution\` maps to the jiib \`heat\` token (D-13: jiib's \`heat\` IS the caution color).`

`TokenBridge.kt:8` — replace `The dinghy-specific glue` with `The jiib-specific glue`.
`TokenBridge.kt:10` — the line ends `… as String hexes); dinghy`; replace that trailing `; dinghy` with `; jiib`.
**DO NOT touch line 9 (`of \`../theme_theory/app/dinghy.js\`'s …`).**

(The PascalCase `Dinghy`/`Dinghy's` in `PromptModel:24`, `PromptEngine:53`, `PromptReducer:38` are intentionally left for Task 4's blanket pass.)

- [ ] **Step 3: Verify the functional swaps landed**

Run:
```bash
grep -rn '"Dinghy Display"\|mees.works/dinghy-display\|"dinghy"\|"dinghy-mdns"' app/src tools
```
Expected: **no output** (all functional literals gone; `frontendId`/clientName/CLIENT_URL/mdns all updated).

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "rename(jiib): §C Moonraker-visible strings + identity prose -> jiib"
```

---

### Task 3: §A — Package identity `works.mees.dinghy` → `works.mees.jiib`

Moves the four source trees and rewrites the package token everywhere. Symbols stay `Dinghy*` (renamed in Task 4); this slice must still compile.

**Files:**
- Move: `app/src/{main,test,androidTest}/java/works/mees/dinghy/` and `macrobenchmark/src/main/java/works/mees/dinghy/`
- Modify: every `.kt` containing `works.mees.dinghy`; `app/build.gradle.kts:33,40`; `macrobenchmark/build.gradle.kts:18`; `app/lint-baseline.xml`

- [ ] **Step 1: Move the four package dir trees (preserves history)**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
git mv app/src/main/java/works/mees/dinghy        app/src/main/java/works/mees/jiib
git mv app/src/test/java/works/mees/dinghy        app/src/test/java/works/mees/jiib
git mv app/src/androidTest/java/works/mees/dinghy app/src/androidTest/java/works/mees/jiib
git mv macrobenchmark/src/main/java/works/mees/dinghy macrobenchmark/src/main/java/works/mees/jiib
```

- [ ] **Step 2: Rewrite the package token in all source (covers package decls, imports, FQNs, the macrobench `TARGET_PACKAGE`/`BENCH_ACTIVITY` string constants, and the gfxinfo comment)**

```bash
grep -rl 'works\.mees\.dinghy' --include=*.kt app macrobenchmark \
  | xargs sed -i 's/works\.mees\.dinghy/works.mees.jiib/g'
```

- [ ] **Step 3: Build config — namespace, applicationId, macrobench namespace**

```bash
sed -i 's/works\.mees\.dinghy/works.mees.jiib/g' app/build.gradle.kts macrobenchmark/build.gradle.kts
```

- [ ] **Step 4: Lint baseline paths**

```bash
sed -i 's#works/mees/dinghy#works/mees/jiib#g' app/lint-baseline.xml
```

- [ ] **Step 5: Verify the package token is gone**

Run: `grep -rn 'works\.mees\.dinghy\|works/mees/dinghy' app macrobenchmark`
Expected: **no output.** (`android:name=".DinghyApp"` in the manifest is relative and still resolves — class renamed in Task 4.)

- [ ] **Step 6: Compile checkpoint (symbols still `Dinghy*`, package now `jiib`)**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon -Pkotlin.incremental=false" 2>&1 | tr -d '\r' | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "rename(jiib): §A package works.mees.dinghy -> works.mees.jiib (dirs + token + gradle)"
```

---

### Task 4: §B — Code symbols `Dinghy*` → `Jiib*`

**Files:**
- Rename (10, now under `…/jiib/`): `JiibApp.kt`, `designsystem/icons/{JiibIcon,JiibIcons,JiibIconView}.kt`, `theme/JiibType.kt`, `theme/compose/{JiibTheme,JiibTextStyle}.kt`, `preview/JiibPreviews.kt`, and tests `designsystem/icons/JiibIconsTest.kt`, `theme/JiibTypeTest.kt`
- Modify: all `.kt`/`.xml` referencing `Dinghy`; `app/src/main/AndroidManifest.xml:67,76`; `app/src/main/res/values/themes.xml:8`

- [ ] **Step 1: Rename the 10 files**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
git mv app/src/main/java/works/mees/jiib/DinghyApp.kt                         app/src/main/java/works/mees/jiib/JiibApp.kt
git mv app/src/main/java/works/mees/jiib/designsystem/icons/DinghyIcon.kt     app/src/main/java/works/mees/jiib/designsystem/icons/JiibIcon.kt
git mv app/src/main/java/works/mees/jiib/designsystem/icons/DinghyIcons.kt    app/src/main/java/works/mees/jiib/designsystem/icons/JiibIcons.kt
git mv app/src/main/java/works/mees/jiib/designsystem/icons/DinghyIconView.kt app/src/main/java/works/mees/jiib/designsystem/icons/JiibIconView.kt
git mv app/src/main/java/works/mees/jiib/theme/DinghyType.kt                  app/src/main/java/works/mees/jiib/theme/JiibType.kt
git mv app/src/main/java/works/mees/jiib/theme/compose/DinghyTheme.kt         app/src/main/java/works/mees/jiib/theme/compose/JiibTheme.kt
git mv app/src/main/java/works/mees/jiib/theme/compose/DinghyTextStyle.kt     app/src/main/java/works/mees/jiib/theme/compose/JiibTextStyle.kt
git mv app/src/main/java/works/mees/jiib/preview/DinghyPreviews.kt            app/src/main/java/works/mees/jiib/preview/JiibPreviews.kt
git mv app/src/test/java/works/mees/jiib/designsystem/icons/DinghyIconsTest.kt app/src/test/java/works/mees/jiib/designsystem/icons/JiibIconsTest.kt
git mv app/src/test/java/works/mees/jiib/theme/DinghyTypeTest.kt              app/src/test/java/works/mees/jiib/theme/JiibTypeTest.kt
```

- [ ] **Step 2: Blanket symbol replace (covers all `Dinghy*` symbols, `Theme.DinghyDisplay`, `DinghySpine`, manifest `.DinghyApp`, `FontConformanceTest` allowlist, and any remaining PascalCase prose)**

```bash
grep -rl 'Dinghy' --include=*.kt --include=*.xml app macrobenchmark \
  | xargs sed -i 's/Dinghy/Jiib/g'
```

- [ ] **Step 3: Verify no `Dinghy` remains (PascalCase fully gone)**

Run: `grep -rn 'Dinghy' app macrobenchmark`
Expected: **no output.**

- [ ] **Step 4: Spot-check the manifest + theme rewired**

Run: `grep -nE 'JiibApp|JiibDisplay' app/src/main/AndroidManifest.xml app/src/main/res/values/themes.xml`
Expected: `android:name=".JiibApp"`, `android:theme="@style/Theme.JiibDisplay"`, and `<style name="Theme.JiibDisplay" …>`.

- [ ] **Step 5: Compile checkpoint**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon -Pkotlin.incremental=false" 2>&1 | tr -d '\r' | tail -20`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "rename(jiib): §B Dinghy* code symbols -> Jiib* (+ theme, manifest, 10 files)"
```

---

### Task 5: §D — Build tooling (`tools/`)

These hardcode the package path / symbols and even **emit** Kotlin with the old package; they break silently if skipped.

**Files (modify):** `tools/verify_ligatures.py`, `tools/oklch-bake/bake_tokens.py`, `tools/gfxinfo-parser/parse_framestats.py`, `tools/oklch-ramp-oracle.mjs`

- [ ] **Step 1: Update the scripts**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
# path + DinghyIcon(s) symbol + the DinghyIcon( regex + "Dinghy Display" header
sed -i 's#works/mees/dinghy#works/mees/jiib#g; s/Dinghy/Jiib/g' tools/verify_ligatures.py
# emits `package works.mees.jiib.theme` and the path components list ("…","mees","jiib","theme",…)
sed -i 's/works\.mees\.dinghy/works.mees.jiib/g; s/"dinghy"/"jiib"/g' tools/oklch-bake/bake_tokens.py
# package refs in comments / shell example
sed -i 's/works\.mees\.dinghy/works.mees.jiib/g' tools/gfxinfo-parser/parse_framestats.py tools/oklch-ramp-oracle.mjs
```

- [ ] **Step 2: Verify the ligature tool still resolves its target (the renamed `JiibIcons.kt`)**

Run: `cd /mnt/e/claude/personal/github/dinghy-display && python tools/verify_ligatures.py; echo "exit=$?"`
Expected: it reads `app/src/main/java/works/mees/jiib/designsystem/icons/JiibIcons.kt` and reports its normal ligature summary (no "file not found", no Python traceback). `exit=0`.

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "rename(jiib): §D update tools/ (path, symbols, emitted package)"
```

---

### Task 6: §G — Config/doc comments + `rootProject.name`

Cosmetic, live files only. `.gradle.kts`/`.properties`/`.toml` are NOT covered by Task 4's `*.kt`/`*.xml` sweep.

**Files (modify):** `settings.gradle.kts:34`, `gradle.properties:1`, `gradle/libs.versions.toml:2`, `app/proguard-rules.pro:1`, `app/build.gradle.kts:242`, `app/src/main/res/values/strings.xml:11`, project `CLAUDE.md`

- [ ] **Step 1: rootProject.name + config comments**

```bash
cd /mnt/e/claude/personal/github/dinghy-display
sed -i 's/rootProject.name = "dinghy-display"/rootProject.name = "jiib"/' settings.gradle.kts
sed -i 's/Dinghy Display/jiib/g' gradle.properties gradle/libs.versions.toml app/proguard-rules.pro
sed -i 's/Dinghy/Jiib/g' app/build.gradle.kts   # the DinghyApp/AppContainer comment on :242
sed -i 's#parallel_dinghy/#parallel_jiib/#' app/src/main/res/values/strings.xml
```

- [ ] **Step 2: CLAUDE.md header** — replace the project line `**Dinghy Display**` (and any "Dinghy Display" in the project description) with `**jiib**`. Leave `.planning/**` untouched.

```bash
sed -i 's/Dinghy Display/jiib/g' CLAUDE.md
```

- [ ] **Step 3: Commit**

```bash
git add -A && git commit -m "rename(jiib): §G config/doc comments + rootProject.name -> jiib"
```

---

### Task 7: Regenerate the lint baseline

The Task-3 path sed kept it syntactically valid, but symbol-bearing issue messages may now be stale. Regenerate cleanly so the release lint gate is trustworthy.

- [ ] **Step 1: Regenerate**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:updateLintBaseline --no-daemon" 2>&1 | tr -d '\r' | tail -15`
Expected: `BUILD SUCCESSFUL`; `app/lint-baseline.xml` rewritten.

- [ ] **Step 2: Verify no stale package path remains in the baseline**

Run: `grep -c 'works/mees/dinghy' app/lint-baseline.xml; echo done`
Expected: `0` then `done`.

- [ ] **Step 3: Commit**

```bash
git add app/lint-baseline.xml && git commit -m "rename(jiib): regenerate lint baseline for works.mees.jiib"
```

---

### Task 8: Final verification gate

No new code — this is the proof. **Do not declare done until every check passes.**

- [ ] **Step 1: Exemption-aware grep gate (the whole-tree proof)**

Run:
```bash
cd /mnt/e/claude/personal/github/dinghy-display
grep -rniE 'dinghy' \
  app/src macrobenchmark tools settings.gradle.kts app/build.gradle.kts \
  macrobenchmark/build.gradle.kts gradle.properties gradle/libs.versions.toml \
  app/proguard-rules.pro app/lint-baseline.xml CLAUDE.md \
  | grep -vE '\[\[dinghy|dinghy\.js|theme_theory|dinghy-display'
echo "exit=$?"
```
Expected: **no output**, `exit=1` (grep found nothing after exclusions). The exclusions are the §E exemptions: memory links, the sibling `dinghy.js`, and the `dinghy-display` repo/dir/skill slug (CLIENT_URL, CLAUDE.md build-env, `sketch-findings-dinghy-display`). Any OTHER line printed = a missed rename → fix and re-run.

- [ ] **Step 2: Full static build + test + macrobench**

Run:
```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest --no-daemon -Pkotlin.incremental=false --rerun-tasks" 2>&1 | tr -d '\r' | tail -40
```
Expected: `BUILD SUCCESSFUL`. A unit-test failure fails the build (FontConformance + serialization round-trips exercised here). (The macrobenchmark module's launch constants are string-only — not compile-checked — and were already verified by Step 1's grep gate; a real macrobench perf run is deferred to perf work, not a v1-ship gate.)

- [ ] **Step 3: On-device launch smoke — flox + moto (debug)**

For each device id (`0a64b42e`, `ZY22LBDRM9`): install the matching-ABI debug APK, launch, and confirm the renamed app starts (instantiates `JiibApp`, starts `MoonrakerService`), connects to a printer, and survives one typed-Navigation hop.

```bash
ADB=/mnt/e/Android/Sdk/platform-tools/adb.exe
ls app/build/outputs/apk/debug/   # confirm exact split-ABI filenames first
$ADB -s 0a64b42e install -r app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
$ADB -s 0a64b42e shell monkey -p works.mees.jiib -c android.intent.category.LAUNCHER 1
$ADB -s ZY22LBDRM9 install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
$ADB -s ZY22LBDRM9 shell monkey -p works.mees.jiib -c android.intent.category.LAUNCHER 1
```
Expected: both launch as `works.mees.jiib` with no crash; owner confirms connect + one navigation. (Verify APK mtime > last commit first — stale-APK trap.)

- [ ] **Step 4: On-device install + launch smoke — release APK (R8 path)**

Build the release APK (debug-sign it per `E:\Android\sign-release.bat` since signing is deferred), install on at least one device, launch, confirm no crash. R8 minification can break reflection/serialization that the debug build hides.

- [ ] **Step 5: Codex correctness pass on the full diff**

Run Codex read-only over `git diff archive/dinghy-pre-rename..HEAD` with the spec, asking it to confirm no missed surface, no broken exemption, and no behavior change. Fix agreeable findings; re-run gate steps 1–2 if code changed.

- [ ] **Step 6: Final commit (if Codex/lint produced fixes) and stop**

```bash
git add -A && git commit -m "rename(jiib): final verification fixes" || echo "nothing to commit — gate clean"
```

Then hand back to the owner for the PR-to-`master` decision (the repo/dir rename + `.claude/skills/sketch-findings-dinghy-display` + assistant-memory path updates remain a separate, deferred follow-up).
