# Focus Frame Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Canonize the Focus region as a universal `FocusFrame` shell (neutral edge by default, data/progress edge modes, content fit-to-frame), applied to every Focus except webcam.

**Architecture:** Rename/extend the existing `DetailCard` → `FocusFrame` (the spoolman/calibrate bounded-card look). Add a sealed `FocusEdge` (Neutral/Data/Progress). The shell self-owns its outer screen frame (`ListFrameInset`) and inner content inset (`FocusInset`), clips content to bounds, and—in Progress mode—unwraps the `Scrubber` visual language (surface3 track + accent fill + ringed thumb) around the frame perimeter. Spec: `.planning/notes/2026-06-12-focus-frame-law-design.md`.

**Tech Stack:** Kotlin, Jetpack Compose, the project's ThemeTokens role-token system. Build Windows-side via `E:\Android\gw.bat`; verify on flox (Nexus 7 2013, armeabi-v7a, id `0a64b42e`) + moto (id `ZY22LBDRM9`).

**Verification reality (read first):** This is visual Compose work. "Test" means: (1) host unit tests for PURE logic only (token values, edge→border mapping, fit-scale math); (2) `assembleDebug` compiles; (3) on-device screenshot diff on flox+moto. Do NOT invent Compose-runtime UI tests — the project verifies visuals on the real devices. Build cmd: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon" 2>&1 | tr -d '\r' | tail -6`. Install: `adb -s <id> install -r -d app/build/outputs/apk/debug/app-<abi>-debug.apk` (flox=armeabi-v7a, moto=arm64-v8a).

---

## File Structure

- `designsystem/components/DetailCard.kt` → renamed file `FocusFrame.kt` — the shell + `FocusEdge` sealed type + the pure `focusEdgeStroke()` helper. Keeps `Modifier.cardSurface` (the shared "drawing surface" seed) where it is.
- `designsystem/layout/ListBlock.kt` — add `FocusInset` constant next to `ListFrameInset` (both are the spacing source of truth for now).
- 9 existing `DetailCard` call sites → `FocusFrame` (Stage 1): CalibrationHub, FineTune, Outputs, Printers, Spool, Temperature(adjuster), Files(focus), + FloatingEStop KDoc + DesignKitComponentPreviews.
- Stage 2 screens (bring under FocusFrame): PrintStatusFocus.kt (standby hero, terminal), TemperatureScreen (graph mode).
- Stage 3: PrintStatusFocus.kt (printing/paused → Progress edge) + the perimeter-progress draw in FocusFrame.kt.
- Docs: `docs/ui_design/COMPONENTS.md` (FocusFrame entry), `docs/ui_design/LAYOUT.md` (Focus sizing-ratio law).

---

## STAGE 1 — FocusFrame foundation (rename + neutral default + content-fit + tokens)

Verifiable on flox immediately. Flips the 5 accent-default cards to neutral and fixes nothing visual on spool (data edge) — pure foundation.

### Task 1: Add the `FocusInset` spacing token

**Files:** Modify `app/src/main/java/works/mees/dinghy/designsystem/layout/ListBlock.kt`

- [ ] **Step 1: Add the constant** next to `ListFrameInset` (after its declaration):

```kotlin
/**
 * Inner content inset of a [works.mees.dinghy.designsystem.components.FocusFrame] — the gap from
 * the frame's border to its content. Distinct from [ListFrameInset] (the outer region-edge frame).
 * THE single source for Focus inner padding; edit here.
 */
val FocusInset: Dp = 16.dp
```

- [ ] **Step 2: Build** — `gw.bat :app:assembleDebug --no-daemon`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** — `git add -A && git commit -m "feat(layout): add FocusInset spacing token"`

### Task 2: Create `FocusFrame` (rename DetailCard) with the `FocusEdge` type, neutral default, self-owned frame, clip

**Files:** Rename `DetailCard.kt` → `FocusFrame.kt`; Modify the 9 call sites.

- [ ] **Step 1: Write the pure host test** for the edge→stroke mapping.
  Create `app/src/test/java/works/mees/dinghy/designsystem/components/FocusEdgeTest.kt`:

```kotlin
package works.mees.dinghy.designsystem.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusEdgeTest {
    @Test fun neutral_uses_outline_token_at_listrow_weight() {
        val s = focusEdgeStroke(FocusEdge.Neutral, outline = Color.Gray, accentLine = Color.Cyan)
        assertEquals(Color.Gray, s.color)
        assertEquals(1.5f, s.widthDp, 0.001f)
    }
    @Test fun data_uses_the_literal_data_color_heavier() {
        val red = Color.Red
        val s = focusEdgeStroke(FocusEdge.Data(red), outline = Color.Gray, accentLine = Color.Cyan)
        assertEquals(red, s.color)
        assertEquals(3f, s.widthDp, 0.001f) // data color needs weight to read
    }
    @Test fun progress_border_is_drawn_separately_not_a_plain_stroke() {
        // Progress draws a perimeter bar, not a uniform border: helper returns null stroke.
        val s = focusEdgeStroke(FocusEdge.Progress(0.5f), outline = Color.Gray, accentLine = Color.Cyan)
        assertEquals(null, s)
    }
}
```

- [ ] **Step 2: Run it, expect FAIL** (unresolved `FocusEdge`/`focusEdgeStroke`):
  `gw.bat :app:testDebugUnitTest --tests "*FocusEdgeTest" --no-daemon`

- [ ] **Step 3: Rename the file and write the component.** `git mv` the file, then replace contents:

```bash
git mv app/src/main/java/works/mees/dinghy/designsystem/components/DetailCard.kt \
       app/src/main/java/works/mees/dinghy/designsystem/components/FocusFrame.kt
```

New `FocusFrame.kt` body (keep `Modifier.cardSurface` from the old file unchanged at the bottom):

```kotlin
/** The Focus edge — the bounded surface's border, whose COLOR/FORM encodes meaning (spec law §2). */
sealed interface FocusEdge {
    /** Default resting edge: neutral t.outline at list-row weight. */
    object Neutral : FocusEdge
    /** Edge tinted by the item's literal data color (THEME-01 carve-out, e.g. spool filament). */
    data class Data(val color: Color) : FocusEdge
    /** Print-progress: a perimeter bar (Scrubber visual language), drawn specially, not a uniform border. */
    data class Progress(val fraction: Float) : FocusEdge
}

/** Resolved uniform-border stroke for an edge, or null when the edge is drawn specially (Progress).
 *  Pure (host-testable) — no Compose. */
data class EdgeStroke(val color: Color, val widthDp: Float)
fun focusEdgeStroke(edge: FocusEdge, outline: Color, accentLine: Color): EdgeStroke? = when (edge) {
    FocusEdge.Neutral -> EdgeStroke(outline, 1.5f)
    is FocusEdge.Data -> EdgeStroke(edge.color, 3f)
    is FocusEdge.Progress -> null
}

/**
 * The universal Focus container (spec: 2026-06-12-focus-frame-law-design.md). Renamed from DetailCard.
 * Self-owns its outer screen frame (ListFrameInset) + inner content inset (FocusInset); fills t.surface;
 * clips content to bounds (no overflow past the frame). Edge defaults to Neutral.
 */
@Composable
fun FocusFrame(
    modifier: Modifier = Modifier,
    edge: FocusEdge = FocusEdge.Neutral,
    content: @Composable ColumnScope.() -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val stroke = focusEdgeStroke(edge, outline = t.outline, accentLine = t.accentLine)
    Column(
        modifier = modifier
            .padding(ListFrameInset)                 // outer region-edge frame (self-owned)
            .clip(shape)                              // clip content to bounds — no overflow
            .then(if (stroke != null) Modifier.border(BorderStroke(stroke.widthDp.dp, stroke.color), shape) else Modifier)
            .background(t.surface)
            .padding(FocusInset),                     // inner content inset
        content = content,
    )
    // Progress-edge perimeter draw is added in Stage 3 (layered over the border position).
}
```

Imports to add: `androidx.compose.foundation.layout.padding`, `ListFrameInset`, `FocusInset` (from `designsystem.layout`). Keep existing imports.

- [ ] **Step 4: Run the host test, expect PASS** — `gw.bat :app:testDebugUnitTest --tests "*FocusEdgeTest" --no-daemon`

- [ ] **Step 5: Migrate the 9 call sites** — `DetailCard(` → `FocusFrame(`, and translate the old `ringColor = X` param to `edge = FocusEdge.Data(X)`; drop now-redundant outer `.padding(8.dp)` (FocusFrame self-owns it). Example (SpoolScreen.kt:358):

```kotlin
// before: DetailCard(ringColor = spoolColor, modifier = Modifier.fillMaxSize().padding(8.dp)) { … }
// after:  FocusFrame(edge = FocusEdge.Data(spoolColor), modifier = Modifier.fillMaxSize()) { … }
```
For the plain cards (CalibrationHub:116, FineTune:315, Outputs:148, Printers:175, Temperature:469, Files:368): drop `ringColor`, drop `.padding(8.dp)`, leave `edge` default (Neutral). Update FloatingEStop.kt KDoc + DesignKitComponentPreviews.kt:144.
  Find them: `grep -rn "DetailCard(" app/src/main`.

- [ ] **Step 6: Build** — `gw.bat :app:assembleDebug`. Expected: BUILD SUCCESSFUL (zero `DetailCard` references remain: `grep -rn "DetailCard" app/src/main` → only the `cardSurface` modifier doc, if any).
- [ ] **Step 7: Install + screenshot flox & moto.** Open Spool (data edge = spool color, unchanged) and CalibrationHub (now NEUTRAL edge, was accent). Confirm: spool color edge intact; calibrate edge is neutral grey; no clipping; layout unchanged.
- [ ] **Step 8: Commit** — `git add -A && git commit -m "feat(ui): DetailCard -> FocusFrame with neutral-default FocusEdge; self-owned frame"`

### Task 3: Content fit-to-frame (the flox clip fix scope check)

FocusFrame now clips. The standby logo clip is fixed in Stage 2 (the logo lives in PrintStatusFocus, not a current FocusFrame). No code here — this task is a NOTE that clip alone prevents overflow; graphical content must additionally use `Fit` sizing (applied per-screen in Stage 2). Confirm Stage 1 introduced no NEW clipping on the 7 migrated cards (their content is text columns that already fit).

- [ ] **Step 1:** On the flox screenshots from Task 2 Step 7, verify no card content is cut off. If any is, the offending screen sizes its content too large — fix that screen's content sizing (use the type ramp / `Fit`), not FocusFrame.

---

## STAGE 2 — Apply FocusFrame to the unbounded focuses

### Task 4: Standby hero → FocusFrame, logo fits

**Files:** Modify `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusFocus.kt` (standby focus, ~line 203).

- [ ] **Step 1:** Wrap the standby focus content (jiib sail + temp readouts) in `FocusFrame(edge = FocusEdge.Neutral, modifier = Modifier.fillMaxSize())`. Remove the existing bare `.padding(8.dp)` on the focus Box (FocusFrame owns it).
- [ ] **Step 2:** Size the jiib sail/logo with `ContentScale.Fit` (Image) or constrain a vector to the frame's inner box so it scales down — it must NOT clip. The temps stay a centered column, sized via the `fsSp` ramp.
- [ ] **Step 3: Build + install flox & moto.** THE acceptance check: jiib logo fully visible (not clipped) on flox AND moto; standby now reads as a bounded neutral card with the sail inside it.
- [ ] **Step 4: Commit** — `git commit -m "feat(ui): standby Focus uses FocusFrame; logo fits-to-frame (fixes flox clip)"`

### Task 5: Temperature graph mode → FocusFrame

**Files:** Modify `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt` (graph-mode focus, ~line 452).

- [ ] **Step 1:** Wrap the graph-host focus Box in `FocusFrame(edge = FocusEdge.Neutral, ...)`, dropping its bare `.padding(8.dp)`. The `GraphView` `AndroidView` fills the inner box.
- [ ] **Step 2: Build + install flox.** Confirm the graph sits inside the neutral bounded frame, no clipping, AndroidView still renders.
- [ ] **Step 3: Commit** — `git commit -m "feat(ui): Temperature graph Focus uses FocusFrame"`

### Task 6: PrintStatus Terminal → FocusFrame

**Files:** Modify `PrintStatusFocus.kt` (terminal focus, ~line 258).

- [ ] **Step 1:** Wrap the terminal result-thumbnail focus in `FocusFrame(edge = FocusEdge.Neutral, ...)`, drop bare `.padding(8.dp)`.
- [ ] **Step 2: Build + install flox.** (Terminal mode needs a finished print to see live — owner-driven; verify compile + that idle/standby unaffected.)
- [ ] **Step 3: Commit** — `git commit -m "feat(ui): Terminal Focus uses FocusFrame"`

---

## STAGE 3 — Progress-perimeter edge (printing screen)

> **⛔ DEFERRED — future enhancement (owner, 2026-06-13).** Stages 1, 2, and the docs (law) are
> done, committed, and flox-verified; the FocusFrame shell is fully in place. The `FocusEdge.Progress`
> perimeter bar is a polish enhancement, NOT required for the law to hold. Until it's built,
> `FocusEdge.Progress` renders borderless (the printing Focus simply has no edge bar yet — it is NOT
> wired to any screen, so nothing regresses). Pick this up as its own focused session: it's the one
> novel Canvas/`PathMeasure` piece and benefits from a clean start. The `FocusEdge.Progress(fraction)`
> type + the `focusEdgeStroke`→null contract already exist as the seam to build against.

### Task 7: Perimeter progress draw in FocusFrame (FocusEdge.Progress)

**Files:** Modify `FocusFrame.kt`.

- [ ] **Step 1:** In `FocusFrame`, when `edge is FocusEdge.Progress`, draw the perimeter bar with a `Modifier.drawWithContent` layered at the border position (after content), reusing Scrubber colors/rules:
  - Build the rounded-rect `Path` of the frame outline (inset to the border centre, radius `t.rCard`).
  - `PathMeasure` → total length `L`. Draw the FULL path stroked in `t.surface3` (track), stroke width = Scrubber track 6dp.
  - Draw a partial path `0..fraction*L` stroked in `t.accent` (fill). Start anchor: top-left, clockwise (confirm at UAT).
  - Compute thumb centre = `pathMeasure.getPosition(fraction*L)`; draw the ringed thumb there: 34dp `t.surface` circle + 5dp `t.accent` ring (the Scrubber thumb).
  - `fraction` clamped 0..1.

```kotlin
// sketch — refine on device
is FocusEdge.Progress -> Modifier.drawWithContent {
    drawContent()
    val r = with(density){ t.rCard.toPx() }
    val sw = with(density){ 6.dp.toPx() }
    val path = Path().apply { addRoundRect(RoundRect(sw/2, sw/2, size.width-sw/2, size.height-sw/2, r, r)) }
    val pm = PathMeasure().apply { setPath(path, false) }
    drawPath(path, t.surface3, style = Stroke(sw))            // track
    val seg = Path(); pm.getSegment(0f, edge.fraction.coerceIn(0f,1f)*pm.length, seg)
    drawPath(seg, t.accent, style = Stroke(sw))               // fill
    val p = pm.getPosition(edge.fraction.coerceIn(0f,1f)*pm.length)
    drawCircle(t.surface, with(density){17.dp.toPx()}, p)     // thumb knob
    drawCircle(t.accent, with(density){17.dp.toPx()}, p, style = Stroke(with(density){5.dp.toPx()})) // ring
}
```

(Needs `density`, `Path`, `PathMeasure`, `RoundRect`, `Stroke` imports. When Progress, suppress the uniform border — `focusEdgeStroke` already returns null.)

- [ ] **Step 2: Build.** Add a `@Preview` or the DesignKit preview at fraction 0.0/0.35/1.0 to eyeball the sweep without a live print.
- [ ] **Step 3: Wire the printing focus** — `PrintStatusFocus.kt` printing/paused: wrap in `FocusFrame(edge = FocusEdge.Progress(state.progress.toFloat()), ...)`, replacing the old standalone progress-ring treatment. The thumbnail/preview sits inside.
- [ ] **Step 4: Build + install flox + DesignKit preview check.** Live-print verification is owner-driven (next print). Confirm preview shows neutral track + accent fill + ringed thumb sweeping the perimeter.
- [ ] **Step 5: Commit** — `git commit -m "feat(ui): FocusEdge.Progress perimeter bar (Scrubber-unwrapped) on printing Focus"`

---

## STAGE 4 — Docs

### Task 8: Codify the law in the UI docs

**Files:** Modify `docs/ui_design/COMPONENTS.md`, `docs/ui_design/LAYOUT.md`.

- [ ] **Step 1:** COMPONENTS.md — add a `FocusFrame` entry (catalog row + class detail): surface fill, `FocusEdge` modes (Neutral default / Data / Progress), self-owned `ListFrameInset`+`FocusInset`, content-clip, webcam exemption. Note the rename from DetailCard.
- [ ] **Step 2:** LAYOUT.md — add the Focus sizing-ratio law (general 50/50 land · 40/60 port, overridable; list-only pages may deviate; optional Focus-foot) and that the Focus is always a `FocusFrame` (except webcam).
- [ ] **Step 3: Commit** — `git commit -m "docs(ui): codify FocusFrame + Focus sizing law in LAYOUT/COMPONENTS"`

---

## Self-review notes
- **Spec coverage:** shell rename ✓(T2), neutral default ✓(T2), data edge ✓(T2), progress edge ✓(T7), content-fit ✓(T3/T4), tokens ✓(T1), sizing law ✓(T8 docs; per-screen ratio overrides are applied as screens are touched), webcam exempt ✓(never wrapped). Standby/graph/terminal ✓(T4/5/6).
- **Deferred/UAT:** progress sweep direction (T7 step 1), data-edge stroke weight (3dp in code — tune on device), About screen Focus adoption (separate application decision, not in this plan).
- **Out of scope:** archetype internals, full spacing-scale, unified Surface primitive, color-usage pass.
- **Naming consistency:** `FocusFrame`, `FocusEdge` (Neutral/Data/Progress), `focusEdgeStroke`, `FocusInset`, `ListFrameInset` used consistently across tasks.
