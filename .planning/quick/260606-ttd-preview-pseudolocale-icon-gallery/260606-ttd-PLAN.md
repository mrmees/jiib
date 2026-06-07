---
phase: quick-260606-ttd
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt
  - app/src/main/java/works/mees/dinghy/preview/PrintStatusPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/FineTunePreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/DinghyPreviews.kt
  - docs/ui_design/PREVIEW_AND_TOKENS.md
autonomous: true
requirements: []
must_haves:
  truths:
    - "Gallery (on-device, debug) shows every DinghyIcons.all entry rendered + labeled with its alternate name, fonts at the established fsSp scale (>=13sp), never a raw .sp literal"
    - "Each of the 3 exemplar preview files exposes a dedicated en-XA pseudolocale spot-check @Preview that renders the exemplar in a pseudolocalized run"
    - "The misleading @DeviceAndLocalePreviews (unused, no-op Night-uiMode panel) is gone (or de-noised), so the harness no longer implies the annotation selects theme"
    - "PREVIEW_AND_TOKENS.md documents the pseudolocale en-XA spot-check in the per-screen preview matrix so Phases 19-21 inherit it"
  artifacts:
    - path: "app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt"
      provides: "DinghyIcons gallery section iterating DinghyIcons.all"
      contains: "DinghyIcons.all"
    - path: "app/src/main/java/works/mees/dinghy/preview/PrintStatusPreviews.kt"
      provides: "PrintStatusPseudolocaleSpotCheck @Preview(locale=en-XA)"
      contains: "en-XA"
    - path: "app/src/main/java/works/mees/dinghy/preview/FineTunePreviews.kt"
      provides: "FineTunePseudolocaleSpotCheck @Preview(locale=en-XA)"
      contains: "en-XA"
    - path: "app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt"
      provides: "SpoolPseudolocaleSpotCheck @Preview(locale=en-XA)"
      contains: "en-XA"
    - path: "docs/ui_design/PREVIEW_AND_TOKENS.md"
      provides: "documented pseudolocale spot-check convention"
      contains: "Pseudolocale"
  key_links:
    - from: "gallery/GalleryScreen.kt"
      to: "designsystem/icons/DinghyIcons.all"
      via: "for-loop rendering DinghyIconView per entry"
      pattern: "DinghyIcons\\.all"
---

<objective>
Add two preview/tokenization capabilities to Phase 18's foundation, to convention — replacing a
reverted Studio-AI attempt that broke conventions (raw 8.sp font; a misleading no-op Night-uiMode
preview panel).

The four deliverables (scope is well-specified — formalize, do NOT redesign):
1. A **DinghyIcons gallery section** in `GalleryScreen.kt` — the on-device proof that every
   registered icon resolves + shows its remap-handle (`alternate`) name, at the project's font scale.
2. A **per-exemplar pseudolocale (`en-XA`) spot-check** `@Preview` for the 3 Phase-18 exemplars,
   mirroring each file's existing `*RtlSpotCheck` — the i18n-completeness sweep (untokenized literals
   show through as plain English in a pseudolocalized run).
3. **Fix the misleading `@DeviceAndLocalePreviews`** in `DinghyPreviews.kt` — it is defined-but-unused
   and its `uiMode = NIGHT_YES` panel is a no-op (theme is tuple-driven via `PreviewBox`, per
   `PreviewTheming.kt`), so it implies the annotation selects theme.
4. **Document** the pseudolocale spot-check in `docs/ui_design/PREVIEW_AND_TOKENS.md` so Phases 19-21
   inherit it alongside the RTL spot-check.

Purpose: round out the Phase-18 preview/tokenization foundation so the convention the later feature
phases copy is complete, correct, and free of the reverted attempt's misleading panels.
Output: an icon gallery section, 3 pseudolocale spot-check previews, a de-noised preview-harness
annotation, and an updated convention doc.

Out of scope (explicit): do NOT create `GalleryPreviews.kt` — a static `@Preview` of the interactive
~3 Hz-feed `GalleryScreen` does not render meaningfully.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@CLAUDE.md
@docs/ui_design/PREVIEW_AND_TOKENS.md

# The harness this extends (read for the exact patterns to mirror)
@app/src/main/java/works/mees/dinghy/preview/DinghyPreviews.kt
@app/src/main/java/works/mees/dinghy/preview/PreviewTheming.kt
@app/src/main/java/works/mees/dinghy/preview/PrintStatusPreviews.kt
@app/src/main/java/works/mees/dinghy/preview/FineTunePreviews.kt
@app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt

# The icon registry + render primitive the gallery section consumes
@app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcon.kt
@app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIconView.kt
@app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt
</context>

<tasks>

<task type="auto">
  <name>Task 1: DinghyIcons gallery section in GalleryScreen.kt</name>
  <files>app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt</files>
  <action>
Add a new section to the existing `GalleryScreen` Column (place it after the "OutlinedControl — five
intents" section, before "SeverityToast", to keep the token/component primitives grouped). The section
renders the WHOLE icon registry so Matthew can eyeball every glyph on flox.

Structure, mirroring the existing in-file section idiom (`SectionLabel(...)` then content using the
already-in-scope `tokens`, `GeistMono`, `fsSp`):
  - `SectionLabel("DinghyIcon registry — every DinghyIcons.all entry")`
  - Iterate `works.mees.dinghy.designsystem.icons.DinghyIcons.all`. Lay the icons out in a wrapping/
    multi-row grid using a `Column` of `Row`s chunked at a fixed columns-per-row count (use
    `DinghyIcons.all.chunked(4)`), each row `Modifier.fillMaxWidth()` with
    `Arrangement.spacedBy(8.dp)`; each cell `Modifier.weight(1f)`. (A simple chunked Row layout — do
    NOT pull in `LazyVerticalGrid`; the screen is already inside a `verticalScroll`.)
  - Each cell is a `Column` (`horizontalAlignment = Alignment.CenterHorizontally`,
    `verticalArrangement = Arrangement.spacedBy(4.dp)`) containing:
      1. `DinghyIconView(icon = icon, tint = tokens.text, sizeDp = 28.dp, contentDescription = null)`
         — contentDescription is null because this is a decorative gallery (an adjacent text label
         already names each icon). Import `works.mees.dinghy.designsystem.icons.DinghyIconView`.
      2. `Text(text = icon.alternate, color = tokens.text2, fontFamily = GeistMono,
         fontSize = fsSp(13f, tokens.fs).sp, ...)` — the icon's `alternate` (the D-07 remap handle) is
         the label. MANDATORY: size via `fsSp(13f, tokens.fs).sp` matching the file's existing
         `fsSp(13f)` usage (>=13sp readable floor). NEVER a raw unscaled `.sp` literal — the reverted
         attempt used `8.sp`, which is BANNED (see CLAUDE.md font-scale LAW + the
         dinghy-font-sizes-too-small memory). Center the text (`textAlign = TextAlign.Center`).

Reuse the existing imports already in the file (`Column`, `Row`, `Arrangement`, `Alignment`,
`fillMaxWidth`, `dp`, `sp`, `Text`, `GeistMono`, `fsSp`, `LocalTokens` via `tokens`); add only
`DinghyIconView`, and `androidx.compose.ui.text.style.TextAlign` if not already imported. Cells with a
short `alternate` plus a 28.dp icon fit comfortably; if a label is long it wraps — that's fine.
  </action>
  <verify>
    <automated>/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'; echo "EXIT=${PIPESTATUS[0]}"</automated>
  </verify>
  <done>
`GalleryScreen.kt` compiles; a new gallery section iterates `DinghyIcons.all` and renders each entry
via `DinghyIconView(tint = tokens.text, sizeDp = 28.dp)` with its `alternate` as a `fsSp(13f, tokens.fs).sp`
label. `grep -n "8\.sp\|[0-9]\+\.sp" GalleryScreen.kt` shows the new code uses ONLY `fsSp(...).sp` (no raw
`.sp` literal added). Build exit code 0.
  </done>
</task>

<task type="auto">
  <name>Task 2: Pseudolocale en-XA spot-check previews + fix misleading @DeviceAndLocalePreviews</name>
  <files>app/src/main/java/works/mees/dinghy/preview/PrintStatusPreviews.kt, app/src/main/java/works/mees/dinghy/preview/FineTunePreviews.kt, app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt, app/src/main/java/works/mees/dinghy/preview/DinghyPreviews.kt</files>
  <action>
Two coordinated changes in the `preview` package.

(A) Add ONE dedicated `*PseudolocaleSpotCheck` `@Composable` to EACH of the 3 exemplar files,
mirroring that file's existing `*RtlSpotCheck` body shape (PreviewBox-wrapped exemplar with a
representative fixture state), but annotated with a SINGLE
`@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)` — NOT `@Nexus7Previews`, NOT
`@DeviceAndLocalePreviews`. Add the import `androidx.compose.ui.tooling.preview.Preview` to each file
(the exemplar files currently import only `PreviewParameter`/`PreviewParameterProvider`; `NEXUS7` is
already resolvable in-package). Keep each function `private`, place it directly after the existing
`*RtlSpotCheck`, and give it a KDoc one-liner: the pseudolocale run accordion-pads + brackets the
APP vocabulary, so any plain-English text that shows through unpseudolocalized is a still-hardcoded
literal (the i18n-completeness sweep, SC-3c).

Exemplar-specific bodies (use the SAME representative fixture each file's `*RtlSpotCheck` uses):
  - `PrintStatusPreviews.kt` → `PrintStatusPseudolocaleSpotCheck`:
    `PreviewBox(colorfulDark) { PrintStatusScreen(state = SampleFixtures.forMode(PrintStatusMode.Printing)) }`
  - `FineTunePreviews.kt` → `FineTunePseudolocaleSpotCheck`:
    `PreviewBox(colorfulDark) { ExtrusionScreen(vm = SampleFixtures.fineTuneAllPresent) }`
  - `SpoolPreviews.kt` → `SpoolPseudolocaleSpotCheck`:
    `PreviewBox(colorfulDark) { SpoolScreen(state = firstSelected) }`
The pseudolocale check needs NO `CompositionLocalProvider`/`LayoutDirection` (that's the RTL check's
mechanism) — locale comes from the annotation alone.

(B) Fix the misleading `@DeviceAndLocalePreviews` in `DinghyPreviews.kt`. PREFERRED: REMOVE the entire
`@DeviceAndLocalePreviews` annotation-class declaration (lines defining it + its three `@Preview` lines)
and its KDoc block — it is defined-but-unused (no current usage; the new per-exemplar pseudolocale
spot-checks replace its only useful panel), and its `uiMode = UI_MODE_NIGHT_YES` panel is a verified
NO-OP (theme is tuple-driven through `PreviewBox`, per `PreviewTheming.kt` — uiMode does not select the
palette). After removal, also drop the now-unused `import android.content.res.Configuration` and the
`@DeviceAndLocalePreviews` reference in the `@Nexus7Previews` KDoc cross-reference if present. Update
the `PREVIEW_AND_TOKENS.md` §2 bullet list claim is handled in Task 3 — do not touch the doc here.
FALLBACK (only if removal causes a compile/reference error you cannot cleanly resolve): keep the
annotation but strip the `Night uiMode` `@Preview(...uiMode = UI_MODE_NIGHT_YES...)` line, leaving it
locale/device-only. If you take the fallback, note WHY in the SUMMARY.

Record which path (remove vs. strip) you took in the SUMMARY.
  </action>
  <verify>
    <automated>/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'; echo "EXIT=${PIPESTATUS[0]}"</automated>
  </verify>
  <done>
Each of `PrintStatusPreviews.kt`/`FineTunePreviews.kt`/`SpoolPreviews.kt` contains exactly one new
`*PseudolocaleSpotCheck` `@Composable` annotated `@Preview(device = NEXUS7, locale = "en-XA", showBackground = true)`
(grep `locale = "en-XA"` finds 3 new sites, none using `@Nexus7Previews`/`@DeviceAndLocalePreviews`).
`@DeviceAndLocalePreviews` is removed from `DinghyPreviews.kt` (or, fallback, its Night-uiMode `@Preview`
line is gone). `grep -n "DeviceAndLocalePreviews" app/src/main/java/works/mees/dinghy/` returns nothing if
removed. Build exit code 0 (no unused-import or unresolved-reference failures).
  </done>
</task>

<task type="auto">
  <name>Task 3: Document the pseudolocale spot-check in PREVIEW_AND_TOKENS.md</name>
  <files>docs/ui_design/PREVIEW_AND_TOKENS.md</files>
  <action>
Update the convention doc so Phases 19-21 inherit the pseudolocale spot-check.

  - §3 ("The minimized matrix shape") table: add a row directly after the `*RtlSpotCheck` row:
    `| `*PseudolocaleSpotCheck` | every APP-vocabulary literal is tokenized (no plain English shows through) | `@Preview(device = NEXUS7, locale = "en-XA")` (a single dedicated panel — NOT a multipreview) |`
    Keep the table's existing column shape.
  - §7 (RTL) currently ends "The `*RtlSpotCheck` preview is the proof." Add a short sibling sentence
    (or a §7-adjacent note) stating the i18n companion: the `*PseudolocaleSpotCheck` `@Preview(locale = "en-XA")`
    is the proof that every app-vocabulary string is tokenized — plain-English text surviving a
    pseudolocalized run is a still-hardcoded literal (the SC-3c completeness sweep). Note it is its own
    dedicated single `@Preview`, NOT part of `@Nexus7Previews`.
  - §2: the `@DeviceAndLocalePreviews` bullet describing the two multipreview annotations is now stale
    (the annotation was removed/de-noised in Task 2). Reconcile: if Task 2 REMOVED the annotation, delete
    the `@DeviceAndLocalePreviews` bullet from §2's list and adjust the surrounding sentence so only
    `@Nexus7Previews` remains documented, with a pointer that the pseudolocale check is now a dedicated
    per-screen `@Preview` (per §3/§7). If Task 2 took the FALLBACK (kept it locale-only), update the
    bullet to drop the night-uiMode claim. The doc's own rule says "where this doc and the harness code
    drift, the code is authoritative and this doc is the bug" — so match the doc to whatever Task 2 left
    in the code.

Do not otherwise restructure the doc; these are targeted edits to §2, §3, §7.
  </action>
  <verify>
    <automated>grep -n "PseudolocaleSpotCheck\|en-XA" docs/ui_design/PREVIEW_AND_TOKENS.md && echo "DOC_OK"; ! grep -q "DeviceAndLocalePreviews.*night uiMode\|night uiMode" docs/ui_design/PREVIEW_AND_TOKENS.md || echo "CHECK_NIGHT_UIMODE_REF"</automated>
  </verify>
  <done>
`PREVIEW_AND_TOKENS.md` §3 table has a `*PseudolocaleSpotCheck` row; §7 documents the en-XA spot-check as
the tokenization-completeness companion to the RTL check; §2's `@DeviceAndLocalePreviews` description is
reconciled with whatever Task 2 left in `DinghyPreviews.kt` (no stale claim that a removed annotation
exists, no stale night-uiMode claim). The doc no longer implies the harness has an unused/no-op
device+locale+night multipreview.
  </done>
</task>

</tasks>

<verification>
Authoritative gate (Windows-side; `./gradlew` does NOT run from WSL; do NOT use `:app:lintDebug` — its
pre-existing AGP/JDK detector crash is neutralized via `abortOnError=false` and is orthogonal):

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'; echo "EXIT=${PIPESTATUS[0]}"
```

Exit code is authoritative. Then confirm the static facts:
- `grep -rn 'locale = "en-XA"' app/src/main/java/works/mees/dinghy/preview/` → 3 new spot-check sites.
- `grep -rn "DeviceAndLocalePreviews" app/src/main/java/works/mees/dinghy/` → empty (preferred path).
- `grep -n "DinghyIcons.all" app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt` → present.
- No raw unscaled `.sp` literal added in `GalleryScreen.kt` (only `fsSp(...).sp`).
</verification>

<success_criteria>
- Gallery has a DinghyIcons section iterating `DinghyIcons.all`, each icon rendered via `DinghyIconView`
  (tint `tokens.text`, `sizeDp` ~24-28) with its `alternate` label at `fsSp(13f, tokens.fs).sp` — no raw `.sp`.
- 3 exemplar preview files each gain one dedicated `*PseudolocaleSpotCheck` `@Preview(locale = "en-XA")`,
  mirroring the existing `*RtlSpotCheck`.
- The misleading `@DeviceAndLocalePreviews` (unused; no-op night-uiMode panel) is removed (or de-noised).
- `PREVIEW_AND_TOKENS.md` documents the pseudolocale spot-check in the per-screen matrix (§3/§7) and is
  reconciled with the harness code (§2).
- `:app:compileDebugKotlin :app:testDebugUnitTest` exit code 0.
- `GalleryPreviews.kt` was NOT created (explicitly out of scope).
</success_criteria>

<output>
Create `.planning/quick/260606-ttd-preview-pseudolocale-icon-gallery/260606-ttd-SUMMARY.md` when done.
Record in the SUMMARY: which path Task 2 took for `@DeviceAndLocalePreviews` (remove vs. strip night-uiMode).
</output>
