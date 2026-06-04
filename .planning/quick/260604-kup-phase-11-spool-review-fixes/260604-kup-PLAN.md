---
phase: quick-260604-kup
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/works/mees/dinghy/ui/spool/MeasuredWeightPage.kt
  - app/src/main/java/works/mees/dinghy/spool/SpoolmanClient.kt
  - app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanSurface.kt
autonomous: true
requirements: [SPOOL-REVIEW-FIX]

must_haves:
  truths:
    - "A failed measure write (null client OR null result) keeps the MeasuredWeightPage open instead of silently dismissing it as success"
    - "SpoolmanClient.measureSpool KDoc accurately states the gross grams are sent in the request body"
    - "ScanSurface releases the camera even when onDispose runs before the async camera-provider listener fires (no bind-after-dispose leak)"
  artifacts:
    - path: "app/src/main/java/works/mees/dinghy/ui/spool/MeasuredWeightPage.kt"
      provides: "Set button only dismisses on a non-null measureSpool result"
      contains: "measureSpool"
    - path: "app/src/main/java/works/mees/dinghy/spool/SpoolmanClient.kt"
      provides: "Corrected measureSpool KDoc"
      contains: "request body"
    - path: "app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanSurface.kt"
      provides: "disposed-flag guard in the camera DisposableEffect"
      contains: "disposed"
  key_links:
    - from: "MeasuredWeightPage Set onClick"
      to: "onMeasured()"
      via: "guarded by non-null measureSpool result"
      pattern: "measureSpool"
---

<objective>
Apply three independently-verified fixes from the deferred Codex review of the Phase 11 Spoolman feature. All three are surgical, single-file changes with no new abstractions.

Purpose: close one HIGH silent-success bug (failed measure writes dismiss the page as if they succeeded), one HIGH camera-leak race (bind-after-dispose), and one NIT stale comment — without touching the HTTP method (the Codex CRITICAL about PUT-vs-POST was a confirmed FALSE POSITIVE; `PUT /v1/spool/{id}/measure` is correct per Spoolman 0.22.1 and was validated live).

Output: three atomic commits, one per fix; the spool unit suite stays GREEN and the release build still compiles.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@./CLAUDE.md
</context>

<tasks>

<task type="auto">
  <name>Task 1: Stop MeasuredWeightPage from dismissing on a failed measure write (FIX 1, HIGH)</name>
  <files>app/src/main/java/works/mees/dinghy/ui/spool/MeasuredWeightPage.kt</files>
  <action>In the "Set" OutlinedControl onClick (around lines 137-143), the coroutine currently does `client?.measureSpool(spool.id, grams); onMeasured()` — `onMeasured()` runs even when `client` is null or `measureSpool()` returns null (a failed write), silently dismissing the page as if it succeeded. Change the body so `onMeasured()` is only called when `measureSpool` returns a NON-NULL JsonElement. Because `grams` is the nullable parsed value, capture the validated value inside the `if (valid)` branch (e.g. a local non-null `g`) before the launch, then: `val result = client?.measureSpool(spool.id, g)` and `if (result != null) onMeasured()`. On null (no session OR failed write) do nothing — leave the page open so the user can retry; do NOT call onCancel.

    Do NOT invent a new error-plumbing abstraction or thread a new parameter through callers. Before adding any toast, check whether an error/toast channel (e.g. SeverityToast or an onError callback) is ALREADY plumbed into this composable or its immediate caller — only reuse one if it is readily available with no new wiring. If none exists at hand, the minimal acceptable fix is simply keeping the page open on failure (skip onMeasured); do not add new surface. Keep the existing `if (valid)` guard and the system-numeric-keyboard input untouched.</action>
  <verify>
    <automated>/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests 'works.mees.dinghy.spool.*' --no-daemon" 2>&1 | tr -d '\r'</automated>
  </verify>
  <done>onMeasured() is reached only when measureSpool returns non-null; null client or null result leaves the page open (no dismiss). Spool unit suite GREEN; release unit test task compiles MeasuredWeightPage. Commit: `fix(11): measured-weight keeps page open on failed/no-op write`.</done>
</task>

<task type="auto">
  <name>Task 2: Correct the measureSpool KDoc to say "request body" (FIX 2, NIT)</name>
  <files>app/src/main/java/works/mees/dinghy/spool/SpoolmanClient.kt</files>
  <action>The KDoc on `measureSpool` (lines 84-90) says the gross grams are carried "in the query", but the implementation (commit fec00e9) sends them in the request BODY via `proxy(..., body = buildJsonObject { put("weight", grossGrams) })`. Edit only the comment text: change the phrase describing where the gross grams travel from "in the query" to "in the request body" (and adjust the parenthetical so it no longer says the proxy forwards the *query* — it forwards the body). Leave the `PUT` method, the path `/v1/spool/$id/measure`, the buildJsonObject body, and everything else exactly as-is — PUT is correct per Spoolman 0.22.1 (docs/view_specific_notes/spoolman.md:551) and was validated live. Comment-only change; no behavior change.</action>
  <verify>
    <automated>/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests 'works.mees.dinghy.spool.*' --no-daemon" 2>&1 | tr -d '\r'</automated>
  </verify>
  <done>KDoc states the gross grams are sent in the request body; PUT method and body unchanged. Spool unit suite GREEN. Commit: `docs(11): measureSpool KDoc — gross weight is in the body, not the query`.</done>
</task>

<task type="auto">
  <name>Task 3: Close the camera bind-after-dispose race in ScanSurface (FIX 3, HIGH)</name>
  <files>app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanSurface.kt</files>
  <action>In the `DisposableEffect(lifecycleOwner, lensSelector)` (lines 216-258), `providerFuture.addListener` is async: if `onDispose` runs before the listener fires, `boundProvider` is still null so onDispose unbinds nothing, then the listener proceeds to `bindToLifecycle` and leaks a camera that is never released. Fix the race for BOTH orderings:
    1. Declare `var disposed = false` in the effect scope alongside `boundProvider`.
    2. At the TOP of the `addListener` callback, immediately after resolving `val provider = runCatching { providerFuture.get() }.getOrNull()`: if `disposed` is true, call `provider?.unbindAll()` on the just-resolved provider and `return@addListener` WITHOUT building preview/analysis or binding. (Place this check after the provider resolve so a late-resolving provider still gets released; the existing `if (provider == null)` early-return stays.)
    3. In `onDispose`, set `disposed = true` IN ADDITION TO the existing `boundProvider?.unbindAll()` and `analyzerExecutor.shutdown()`.
    Keep the lens-keying, FILL_CENTER/COMPATIBLE PreviewView, analyzer wiring, and onBindFailed calls unchanged.</action>
  <verify>
    <automated>/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests 'works.mees.dinghy.spool.*' --no-daemon" 2>&1 | tr -d '\r'</automated>
  </verify>
  <done>`disposed` flag guards the addListener callback (early provider release + return on late firing) and is set in onDispose. Spool unit suite GREEN; release unit test task compiles ScanSurface. Commit: `fix(11): scan camera — release provider on dispose-before-bind race`.</done>
</task>

</tasks>

<verification>
- All three files compile in the release variant (the `:app:testReleaseUnitTest` task compiles the full main + test sourceset before running the filtered tests; a compile error fails the task).
- `works.mees.dinghy.spool.*` unit suite stays GREEN (process exit code authoritative; pipe through `tr -d '\r'`).
- MeasuredWeightPage and ScanSurface are UI-callback-level with no direct unit test — expected; correctness is by compile + code review against the fix spec.
- HTTP method on measureSpool is UNCHANGED (`PUT`).
</verification>

<success_criteria>
- FIX 1: a failed/no-op measure write leaves MeasuredWeightPage open (onMeasured only on non-null result).
- FIX 2: measureSpool KDoc says the gross grams are in the request body; PUT + body untouched.
- FIX 3: ScanSurface releases the camera on the dispose-before-bind ordering via a `disposed` flag.
- Three atomic commits; spool unit suite GREEN; release build compiles.
</success_criteria>

<output>
Create `.planning/quick/260604-kup-phase-11-spool-review-fixes/260604-kup-SUMMARY.md` when done.
</output>
