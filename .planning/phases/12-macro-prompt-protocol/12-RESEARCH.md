# Phase 12: Macro Prompt Protocol — Research

**Researched:** 2026-06-04
**Domain:** Line-oriented protocol parser/reducer + Compose overlay renderer (native Android/Kotlin)
**Confidence:** HIGH — the entire requirements contract (SPEC.md, the 26 fixtures, and a complete clean-room JS reference engine) is in-repo and was read in full. Almost nothing here is "training data"; it is extracted from the authoritative source.

## Summary

This phase ports the project owner's own **Macro Prompt Protocol v1** clean-room JS reference engine
(`packages/js/src/`) to Kotlin and renders its output as a full-screen Compose overlay. The protocol is a
line grammar: each `// action:prompt_*` gcode-response line is parsed into a `PromptEvent`, fed through a
pure reducer that maintains a `PromptStateData` machine, and projected to an immutable `PromptView`
(the conformance shape). The 26 fixtures in `fixtures/fixtures.json` replay event streams and assert the
resulting `PromptView` — that is the acceptance gate.

The good news for the planner: **the engine is small, total (never throws), and already fully specified
by working TypeScript.** The Kotlin port is a near-mechanical translation of 7 tiny modules
(`parse-action`, `reducer`, `markup`, `button`, `image`, `style`, `view` — together ~350 LOC). The
fixtures are a drop-in golden corpus; Dinghy already has the exact loader pattern (`GoldenFixtures` +
`src/test/resources/`) and a parameterized-test idiom. The genuinely *new* Dinghy work is: (1) the
markup AST → Compose `AnnotatedString` builder with the author-hex carve-out, (2) the `PromptDialog`
overlay assembled from existing primitives, (3) wiring the headless holder to `store.gcodeResponses` /
`CommandDispatcher` / `AppShell` / connection state, and (4) Coil-bounded image rendering.

**Primary recommendation:** Port the JS engine module-for-module to a pure, I/O-free `prompt/` package
with a single `PromptEngine` holder (the calibration-holder shape), expose `PromptView` as the render
contract, and gate it with a data-driven JUnit test that loads `fixtures.json` from test resources and
asserts `promptView == expected` (partial / `toMatchObject`-equivalent). Do NOT improvise reducer
semantics — match the reference byte-for-byte, because the fixtures will catch any deviation and the
owner intends Dinghy to be the canonical first native renderer.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| `// action:prompt_*` line → `PromptEvent` parse | App / pure logic (`prompt/ParseAction`) | — | Total function, no I/O; host-unit-testable; mirror JS `parse-action.ts` |
| Event → normalized state (reducer state machine) | App / pure logic (`prompt/PromptReducer`) | — | Pure immutable reducer; the conformance core; mirror `reducer.ts` |
| PromptMarkup tokenize/parse → AST + plain_text | App / pure logic (`prompt/Markup`) | — | Pure; `markupToPlainText` feeds the reducer's `markup` event; mirror `markup.ts` |
| Subscribe raw gcode stream, hold view, observe disconnect | App / state holder (`PromptEngine`) | Coroutines/Flow | Long-lived spine-level holder; subscribes `store.gcodeResponses` + connection state |
| Button/close gcode dispatch | App / command (`CommandDispatcher`) | Moonraker REST/WS | Reuse the existing busy-keyed dispatcher → `printer.gcode.script` |
| Markup AST → `AnnotatedString` | UI / Compose (`PromptMarkupText`) | theme tokens | Render-tier; the hex carve-out lives here |
| Full-screen overlay assembly | UI / Compose (`PromptDialog` in AppShell) | designsystem primitives | Overlay hoisted outside `when(dest)`, like `MacroExecutionPopup` |
| Image decode + hard bounding | UI / Compose (Coil 3) | Moonraker `config/...` HTTP | Coil downscale discipline; path safety enforced in pure layer before load |

## Standard Stack

Everything needed is **already a project dependency** — this phase adds **zero new libraries**.

### Core (all present)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| kotlinx.serialization json | 1.7.3 | Load `fixtures.json` in tests; (no runtime JSON needed — the protocol is line-oriented) | Already the project JSON lib; `JsonElement` walking matches the fixtures' loose shape `[VERIFIED: gradle/libs.versions.toml]` |
| Jetpack Compose (BOM 2026.05) | Compose 1.11 | `PromptDialog` overlay, `AnnotatedString` markup | Project UI toolkit (CLAUDE.md TL;DR) `[CITED: CLAUDE.md]` |
| Coil 3 | 3.1.0 | `prompt_image` decode + downscale | minSdk-23 image lib already used in Phase 10/Files `[VERIFIED: gradle/libs.versions.toml]` |
| Coroutines + Flow | 1.9.x | `PromptEngine` subscribes `store.gcodeResponses`, observes connection state | Project reactive plumbing `[CITED: CLAUDE.md]` |
| JUnit4 | 4.13.2 | Fixture conformance gate + unit tests | Project host-test framework `[VERIFIED: gradle/libs.versions.toml]` |

### Supporting (all present, reuse)
| Component | Source | Use |
|-----------|--------|-----|
| `CommandDispatcher.dispatch(key, method, params)` | `command/CommandDispatcher.kt` | button + close → `printer.gcode.script`; busy-keyed, debounced, redacted-failure→`SeverityToast` `[VERIFIED: read]` |
| `PrinterStateStore.gcodeResponses: SharedFlow<String>` | `state/PrinterStateStore.kt:194` | the un-throttled raw line stream the engine subscribes `[VERIFIED: read]` |
| `PrinterState.connection: ConnectionState` (`Connected/Disconnected/Error/Connecting/Syncing`) | `state/PrinterState.kt:227` | observe for D-10 local close `[VERIFIED: read]` |
| `OutlinedControl` / `Intent` (5-value) | `designsystem/control/` | content/footer button language; `info` resolved via a separate style→color map, NOT a 6th Intent |
| `SeverityToast` | `designsystem/SeverityToast.kt` | rejection/in-flight toasts |
| `LocalTokens` / `fsSp(baseSp, t.fs)` / Geist | `theme/` | tokens + font scale (mind the fonts-too-small lesson) |
| `GoldenFixtures` loader idiom + `src/test/resources/` | `net/GoldenFixtures.kt` | the exact pattern for loading `fixtures.json` in a host test `[VERIFIED: read]` |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Mechanical JS→Kotlin port | "Reimplement from the SPEC prose" | The JS reference resolves ambiguities the prose doesn't; the fixtures grade against JS behavior. Porting is lower-risk and faster. **Port.** |
| `AnnotatedString` for markup | Custom `Canvas` text | Markup needs nested spans/colors/sizes — `AnnotatedString` + `SpanStyle` is built for exactly this and cheap. Canvas would be reinventing text layout. **AnnotatedString.** |
| kotlinx.serialization `@Serializable` fixture models | `JsonElement` tree-walk asserts | The fixtures use *partial* assertion (`toMatchObject`) and `absent ≠ default` — a tree-walk comparator is the faithful port. Strict `@Serializable` decode would fight the additive/optional schema. **Tree-walk (see Validation).** |

**Installation:** None. No `npm`/Gradle dependency change. (This is Android — the JS engine is *reference only*, never bundled.)

## Package Legitimacy Audit

> Not applicable — this phase installs **no external packages**. All libraries are pre-existing,
> pinned project dependencies (Compose BOM, Coil 3.1.0, kotlinx.serialization 1.7.3, JUnit 4.13.2),
> already vetted in prior phases. The JS reference engine in `packages/js/` is read-as-spec, never a
> runtime dependency. slopcheck N/A.

## The Reducer State Machine (the single most valuable extract)

This is a faithful description of the JS reference engine (`packages/js/src/`). **Mirror it exactly.**
Confidence: HIGH (read in full; cross-checked against fixtures + hardening tests). `[CITED: packages/js/src/reducer.ts, parse-action.ts]`

### Normalized state shape (internal — `PromptStateData`)
The reducer carries MORE than the view exposes. Internal fields (Kotlin equivalents):

```
PromptStateData:
  lifecycle:        Idle | Building | Shown | Suppressed    // drives behavior; NOT in the view
  epoch:            Int                                     // bumps on each begin; render-key hint, NOT conformance
  title:            String                                  // "" when idle
  size:             PromptSize? (small|normal|large|x-large|full-screen|null)
  activeTargets:    List<String>                            // ["all"] when untargeted
  items:            List<PromptItem>
  footerButtons:    List<FooterButton>
  activeContainer:  Row | ButtonGroup | null                // currently-open container
  pendingTargets:   List<String>? (null = none pending)     // consumed at begin
  pendingSize:      PromptSize? (null = none pending)        // consumed at begin
  currentAlign:     left | center | right                   // sticky; reset to center at begin
  opts:             { frontendId, frontendCategories, liveAppend }
```

### The view projection (`promptView` — THE conformance shape, 6 keys EXACTLY)
```
PromptView { visible, title, targets, size, items, footer_buttons }
  visible       = (lifecycle == Shown)
  title         = title
  targets       = activeTargets
  size          = size
  items         = items          (deep-cloned — immutable snapshot boundary)
  footer_buttons= footerButtons  (deep-cloned)
```
The fixtures test asserts `Object.keys(view).sort() == [footer_buttons, items, size, targets, title, visible]`
— i.e. **no internal field may leak into the view.** epoch/lifecycle/pending*/activeContainer/opts are
NOT exposed. (`EXACT_VIEW_KEYS` in `fixtures.spec.ts`.)

### Initial state
`freshIdle(epoch=0)`: lifecycle=Idle, title="", size=null, activeTargets=["all"], items=[], footerButtons=[],
activeContainer=null, pendingTargets=null, pendingSize=null, currentAlign=center.

### Per-event semantics (the switch, in order)

**Metadata / lifecycle events (handled BEFORE the "requires active prompt" guard):**

| Event | Effect |
|-------|--------|
| `target` | `pendingTargets = event.targets` (replaces; even mid-prompt — applies to *next* begin) |
| `size` | `pendingSize = event.size` (replaces; `null` for empty/unknown token, which CLEARS) |
| `end` / `disconnect` | If already idle AND no pending target/size → return state unchanged. Else → `freshIdle(epoch)` (epoch NOT bumped). This clears the active prompt, pending metadata, and any open container. **`end` and `disconnect` are identical in the reducer** — the *only* difference is in the holder: `disconnect` must NOT emit `prompt_end` gcode (D-10). |
| `begin` | `targets = pendingTargets ?? ["all"]`; `matched = targetsMatch(targets)`; start from `freshIdle(epoch+1)` then set lifecycle = `Building` (matched) or `Suppressed` (not matched), title = event.title, size = pendingSize, activeTargets = targets, currentAlign = center. **Crucially: pendingTargets/pendingSize are consumed (reset to null via freshIdle) even when suppressed** — `size-consumed-when-target-suppressed` fixture proves this. |
| `show` | If lifecycle == Building → Shown. Else (idle/shown/suppressed) → no-op. (Repeated show = no-op; show-before-begin = no-op.) |
| `align` | If `event.align != null` → `currentAlign = align`. If null (unknown/empty) → **no-op, alignment preserved** (differs from size, which clears on bad value). |

**Content guard:** after the above, if `lifecycle ∈ {Idle, Suppressed}` → return state unchanged
(content before begin is dropped; content for a suppressed prompt is dropped). **If `!liveAppend && lifecycle==Shown` → return unchanged (snapshot-at-show).** Dinghy sets `liveAppend=true` (D-02), so shown prompts keep appending.

**Content events (only when Building, or Shown+liveAppend):**

| Event | Effect (routed through `appendContent`) |
|-------|------|
| `text` | append `{type:text, text}` |
| `markup` | append `{type:markup, markup, plain_text}` (plain_text computed at PARSE time, not in reducer) |
| `button` | append `{type:button, label, gcode, style}` |
| `image` | `if isValidImagePath(path)` → append image item; `else if alt == ""` → drop (no-op); `else` → append `{type:text, text:alt}` (alt-as-text fallback happens IN THE REDUCER) |
| `footer_button` | always appends to `footerButtons` (NOT subject to container routing) |
| `row_start` / `button_group_start` | `openContainer` |
| `row_end` / `button_group_end` | `closeContainer` |

### `appendContent` routing (container rules — subtle, fixture-graded)
- **No open container:** append `stampAlign(item, currentAlign)` to `items`. `stampAlign`: if align==center, item unchanged (NO `align` field); else add `align="left"|"right"`. (Center omits the field — additive schema.)
- **activeContainer == row:** if item is row/button_group → drop (no nesting). Else append the item to the **last** item's `children` (the open row), WITHOUT an align stamp (children are never individually aligned).
- **activeContainer == button_group:** if item is NOT a button → drop. Else append to the last item's children.
- `appendToLastContainer` defensively checks the last item really is a row/button_group; if not, no-op.

### `openContainer` / `closeContainer` (malformed-tolerance)
- `openContainer(kind)`: if a container is **already open** → ignore (the nested-start is dropped, but its inner content still appends to the already-open container — proven by `hardening.spec.ts` test 3). Else push an empty `{type:kind, children:[]}` item (align-stamped) and set activeContainer=kind.
- `closeContainer(kind)`: if activeContainer != kind (stray/mismatched end) → ignore. Else activeContainer=null. (`stray-row-end-ignored` fixture.)

### Ordering / precedence rules to NOT get wrong
1. **Last `prompt_target` before begin wins** (it just overwrites pendingTargets). (`target-last-wins`)
2. **Target/size consumed at begin even if suppressed** — pending never leaks to a later prompt. (`size-consumed-when-target-suppressed`)
3. **`prompt_size` mid-prompt applies to NEXT begin, not current.** (`size-applies-to-next-begin-not-current`)
4. **`prompt_end` clears pending target AND size** → clean baseline. (`size-end-clears-pending`)
5. **Empty/unknown size → `null` (clears)**, but **unknown/empty align → preserved (no clear).** (Asymmetry is deliberate, spec §Prompt Alignment.)
6. **Empty/whitespace target list → no match** → Suppressed (per spec; `targets.filter(len>0)` can yield `[]`, and `targetsMatch([])` is false). Note: to target everyone you OMIT `prompt_target`, you don't send an empty one.
7. **alignment resets to center at every begin** (no bleed-through). (`alignment-resets-at-begin`)
8. **image alt-as-text fallback is reducer-side**, so `invalid-image-paths` fixture expects `{type:text}` items where paths were rejected — the renderer never sees a rejected image item.
9. **suppressed content isolation**: a Suppressed prompt accumulates nothing; a subsequent matched `begin` starts clean. (`hardening` test 5.)

### `targetsMatch(targets)`
`true` if targets contains `"all"`, OR contains `frontendId.lowercase()`, OR contains any of `frontendCategories` (lowercased). For Dinghy: **frontendId = `"dinghy"`, frontendCategories = `["touch"]`** (D-08). So Dinghy matches `all`, `dinghy`, `touch`.

### Parse layer (`parse-action.ts` → Kotlin `parseAction`)
- `PREFIX = "// action:prompt_"`. `parseAction(line)`: if not startsWith PREFIX → `null`. Else `rest = line.removePrefix(PREFIX)`, split on FIRST space → `cmd` + `arg` (arg = "" if no space).
- **Dinghy raw-stream form confirmed:** `store.gcodeResponses` emits the verbatim `notify_gcode_response` param (`JsonRpcClient.gcodeLine` = `params[0]` untouched), and Moonraker delivers prompt lines as `// action:prompt_begin …` — the `// ` IS retained. So the JS `PREFIX` matches the Dinghy stream **as-is**; no stripping needed. `[VERIFIED: read JsonRpcClient.gcodeLine + ConsoleSeverity which keys on "// action:"]`
- command dispatch table → events; **unknown `prompt_*` command → `null` (dropped)** (`unknown-commands-ignored`). A button/footer_button with empty label → `parseButtonFields` returns null → event is null → dropped.
- `disconnectEvent()` is a manufactured `{kind:disconnect}` (NOT from a line) — the holder feeds it on connection loss.

### Sub-parsers
- **`parseButtonFields(arg)`** (`button.ts`): `split("|")`; `label = parts[0].trim()`; if empty → null. `gcode = parts[1].trim()` if non-empty else label. `style = normalizeStyle(parts[2])`.
- **`normalizeStyle`** (`style.ts`): trim+lowercase; if in {primary,secondary,info,warning,error,success} → that, else `secondary`.
- **`parseImageScale(raw)`** (`image.ts`): undefined→null; trim; empty or contains `,`→null; regex `^[0-9]*\.?[0-9]+$` (no sign/exp/Infinity); `Number()`; if not finite or ≤0 → null; else the number.
- **`isValidImagePath(path)`** (`image.ts`): false if empty / starts `/` / starts `~` / not startsWith `config/` / contains `\`. Split on `/`; reject any empty segment, `.`, `..`, or segment containing `:`. Else true.
- **image event parse:** `parts = arg.split("|")`; path=`parts[0].trim()`, alt=`parts[1].trim()`, scale=`parseImageScale(parts[2])`.

### PromptMarkup parser (`markup.ts`) — see dedicated section below.

## The Fixture Pack (the conformance gate)

`fixtures/fixtures.json`: `{schema_version:1, protocol, event_prefix:"// action:", fixtures:[...]}`. **26 fixtures**
(8 `core` + 18 `optional`). `[VERIFIED: read fixtures.json — counted]`

Each fixture: `{ id, level: core|optional, description, events: string[], + one of: expected | expected_by_frontend, optional: notes/fallback_expected/legacy_behavior/skip/extension }`.

- **`events`**: ordered raw action lines to replay. The sentinel `"__disconnect__"` (not a real line) → feed `disconnectEvent()` instead of `parseAction`. (`disconnect-clears-active-prompt`.)
- **`expected`**: the normalized `PromptView` — asserted **partially** (`toMatchObject` semantics: only the keys present in `expected` are checked; **absent ≠ default**, per README "treat absent fields as not asserted").
- **`expected_by_frontend`**: a map `{frontendKey → expectedView}` used by the targeting/suppression fixtures (`target-touch-only`, `target-last-wins`, `size-consumed-when-target-suppressed`). Each key replays the SAME events under that frontend's identity. **For Dinghy (D-08), evaluate under identity `dinghy` + categories `["touch"]`.** Because Dinghy carries category `touch`, `target-touch-only` (targets `klipperscreen,touch`) must be **VISIBLE** for Dinghy (matches `touch`) — i.e. Dinghy behaves like `klipperscreen` in that fixture, not like the hidden `mainsail`/`fluidd`. The JS test maps `klipperscreen→["touch"]` and everything else→`["web"]`; the Kotlin port must add a `dinghy→["touch"]` row.
- **Exact-shape guard:** the view must have EXACTLY the 6 keys (no leaked internal fields). Port this as an assertion too.

### Concrete Kotlin porting approach (data-driven, the project's own idiom)
1. **Place `fixtures.json` on the test classpath:** copy to `app/src/test/resources/prompt/fixtures.json`
   (mirrors `src/test/resources/golden/`, `src/test/resources/fixtures/`). A tiny copy step or a
   committed copy; prefer committing a copy so the test is hermetic (the protocol repo is a sibling, not
   a Gradle dependency). Note the source-of-truth tension: add a comment + a checksum/`schema_version`
   assertion so a stale copy is caught.
2. **Load with kotlinx.serialization** exactly like `GoldenFixtures`:
   `Json.parseToJsonElement(resourceText)` → walk `["fixtures"].jsonArray`.
3. **Parameterized JUnit4** (`@RunWith(Parameterized::class)`) — one case per fixture (and per
   frontendKey for `expected_by_frontend`). Each case: `initialPromptState(dinghyOpts)`, fold the events
   (`__disconnect__` → disconnect event, else `parseAction`; skip nulls), `promptView(state)`, then
   **partial-match** the view against the `expected` JsonObject.
4. **Write a `assertMatchesPartial(actualView, expectedJson)` comparator** — the faithful port of
   `toMatchObject`: for each key in `expected`, deep-compare (objects recurse partially, arrays compare
   element-for-element AND length where the array is fully specified — the fixtures always fully specify
   `items`/`footer_buttons`/`children`, so arrays are exact-length here; scalars equal). Numbers: tolerate
   `0.75` vs `0.75` (JSON double); `scale:1` is an int in JSON but a Double in Kotlin — compare numerically.
   `size:null` must distinguish "asserted null" from "absent". Reuse the `align` rule (absent = center).
5. **Also port the exact-6-keys guard** as a structural assertion on the produced view object.
6. **Sanity:** mirror the JS `fixtures.spec.ts` (read it — it's the canonical driver) and the
   `hardening.spec.ts` edge cases (re-port those 8 as additional unit tests; they cover view-immutability,
   container-open-at-show, group-rejects-non-button, suppressed-isolation, empty-label-drop, markup
   case-sensitivity — several aren't in the fixture JSON).

**Immutability boundary (port from `view.ts` + hardening tests 1/1b/8):** `promptView` deep-clones items/footer_buttons so a consumer mutating returned lists can't corrupt reducer state. In Kotlin this is FREE if the model is immutable `data class` + `List` (Kotlin lists returned from `copy()` are not the same instances you'd mutate, and you never expose mutable collections). Use `@Immutable`/`data class` and never hand out a `MutableList`. Add a regression test that mutating a returned view (where possible) doesn't change a re-projected view — or simply assert the model is immutable by construction.

## PromptMarkup Grammar → AnnotatedString

Port `markup.ts` precisely (read in full). `[CITED: packages/js/src/markup.ts]`

### Tokenizer/parser rules (single left-to-right pass, builds an AST)
- Scan `raw`. On `<`: find the next `>`. If none → treat `<` as a literal text char and continue (unterminated tag is literal text).
- `inner = raw[i+1 .. close-1]`.
  - If `inner` starts with `/` → **closing tag**: flush text buffer; `name = inner[1:].trim()`; if the
    top-of-stack open tag's name matches → pop. Mismatched/extra close → ignore (content already at
    current level).
  - Else → **opening tag** via `makeTagNode(inner)`: flush buffer; if a valid node → push it as a child
    AND onto the stack. **If invalid/unknown tag → skip the tag markup itself but keep accumulating
    inner text at the current level** (so `<B>x</B>` → text "x"; the tag vanishes, text survives).
- Else accumulate the char into the text buffer.
- At end: flush. **Unclosed tags simply keep whatever children they collected** (no error).

### `makeTagNode(inner)`
- If `inner ∈ {b,i,u}` (CASE-SENSITIVE — `<B>` is unknown) → simple tag node.
- Else find first `:`; none → null (unknown). `name=before`, `value=after`.
  - `color`/`bgcolor`: value must match `^#[0-9a-fA-F]{6}$` (exactly 6 hex, case-insensitive digits) else null.
  - `size`: `value.lowercase()` ∈ {small,normal,large,x-large} else null. (value case-insensitive; tag name not.)
  - else null.

### Entity decode (`decodeEntities`, applied to TEXT runs only, single pass, never re-scanned)
`&lt;`→`<`, `&gt;`→`>`, `&amp;`→`&`, `\n` (backslash-n, two chars) → newline. Decoded once; output never
re-parsed as markup (so `&amp;lt;` → literal `&lt;`). **In the events JSON the `\n` appears as `\\n`**
(JSON-escaped backslash-n) — the decoder turns the two-character sequence backslash+`n` into a real LF.

### `markupToPlainText` = flatten the AST (concatenate all text nodes, drop tags). This is what the
parse layer stores as `plain_text` on the markup event. **Compute it at parse time** (matches JS).

### Compose `AnnotatedString` builder (NEW — the render-side translation, Claude's discretion D-04)
Walk the AST; maintain a `SpanStyle` context stack:
- `b` → `SpanStyle(fontWeight = SemiBold)` (LAW: bold = SemiBold 600, not a 3rd weight).
- `i` → `SpanStyle(fontStyle = Italic)`.
- `u` → `SpanStyle(textDecoration = Underline)`.
- `color:#hex` → `SpanStyle(color = Color(0xFF<hex>))` — **the author-hex carve-out (D-03)**, the only raw color in the app outside spool colors.
- `bgcolor:#hex` → `SpanStyle(background = Color(...))` (inline run background, never a layout block).
- `size:small|normal|large|x-large` → `SpanStyle(fontSize = fsSp(15|18|22|28, t.fs).sp)` — the four protocol sizes ARE the four UI-SPEC sizes.
- text node → `append(text)` under the current merged style (default `--text` token color, 18sp).
Nesting = nested `pushStyle`/`pop` (or `withStyle`). Invalid color/size never reach the builder (the
parser already dropped them, inner text preserved). **Recommendation:** build the AST in the pure
`prompt/Markup` layer (host-testable, port `parseMarkup`); the `AnnotatedString` builder is the only
Compose-coupled piece and consumes that AST. Keep them separate so the parser stays unit-tested without Compose.

## Wiring (holder ↔ store ↔ dispatcher ↔ AppShell ↔ connection)

**The calibration holder is the template** (`ProbeCalibrateHolder` subscribes `store.gcodeResponses` and
folds `CommandDispatcher.events`). `[VERIFIED: read]`

1. **`PromptEngine` holder** (spine-level, long-lived, constructed in `di/AppContainer`/`SpineHandle`
   alongside the dispatcher so the overlay can appear over any screen):
   - `state: StateFlow<PromptView>` (or expose `visible`/`view` separately).
   - On construct: `scope.launch { store.gcodeResponses.collect { line -> parseAction(line)?.let { state = reduce(state, it) } } }`. Pure parse+reduce; never throws.
   - **Disconnect (D-10):** `scope.launch { store.printerState.map { it.connection }.distinctUntilChanged().collect { c -> if (c is Disconnected || c is Error) reduce(state, DisconnectEvent) } }`. This is a **LOCAL close** — it runs the reducer's disconnect path (clears prompt + pending + container) but **emits NO `prompt_end` gcode** (the reducer disconnect branch is purely local state; the holder must not dispatch). `Connected→Disconnected` is the trigger; do NOT close on `Syncing`/`Connecting` transitions mid-reconnect unless they pass through Disconnected/Error. Coordinate with Phase-13 reconnect plumbing.
2. **Button press → `CommandDispatcher.dispatch(key, JsonRpcMethods.GCODE_SCRIPT, scriptParams(gcode))`** where `key` is a stable per-button id (e.g. `prompt:<epoch>:<itemIndex>`) so the busy/debounce guard works and a re-render doesn't change the key. Wrap gcode via the existing `PrinterCommands` `{"script": gcode}` helper. Buttons do NOT auto-close (D-11) — only an inbound `prompt_end` line closes it.
3. **Close control → dispatch `printer.gcode.script` with `RESPOND TYPE=command MSG="action:prompt_end"`** (NOT a local teardown). The resulting `// action:prompt_end` line comes back through the stream and the reducer closes it — so close is just another gcode dispatch, identical to a footer button whose gcode emits `prompt_end`. Use a fixed key like `prompt:close`.
4. **AppShell overlay hoist:** add `if (promptEngine.visible) { PromptDialog(view, onButton, onClose) }` OUTSIDE `when(dest)` (after the routing block), exactly like the existing `MacroExecutionPopup` / `nav.scanActive` / `drawerOpen` overlays at `AppShell.kt:554/567/591`. **Suppress the swipe-up drawer while visible** (LAYOUT.md "Scroll Field ⇒ no swipe-up"). `[VERIFIED: read AppShell.kt overlay pattern]`
5. **Failure toast:** fold `CommandDispatcher.events` Failure for prompt keys → `SeverityToast` over the overlay (mirror `ProbeCalibrateHolder`'s `events.collect`). The dispatcher already redacts credentials and uses the long 120s gcode timeout (G1/G4 fixes) — reuse, don't re-roll.
6. **Raw-stream contract confirmed (Phase 8):** `store.gcodeResponses` is un-throttled (`extraBufferCapacity` large, never conflated). `ConsoleSeverity` classifies `// action:` as ACTION and `ConsoleFilters.HIDE_PROMPT_COMMANDS = ^(?:// )?action:prompt` hides prompt lines from the *console view* while the raw stream still carries them to the engine. The engine and console are independent subscribers — the engine is never starved. `[VERIFIED: read ConsoleSeverity.kt, ConsoleFilters.kt]`

## Image path safety + bounding

- **Path safety in the pure layer** (`isValidImagePath`, ported above) runs in the reducer BEFORE any
  load — a rejected path becomes an alt-text item (or is dropped), so the renderer only ever gets valid
  `config/...` paths. `[CITED: image.ts + SPEC §Images]`
- **Render with Coil 3** (`AsyncImage`, present): resolve `config/...` against the Moonraker base URL
  (the same host the WS/REST use; build `http://<host>/server/files/config/...` or the appropriate
  Moonraker file path — confirm the Moonraker file-serving route during planning; Phase-10 webcam/Files
  already resolves Moonraker URLs, reuse that base-URL plumbing).
- **Bound HARD** (`ContentScale.Fit`, square box = contentWidth/3 × clamped-scale; never intrinsic).
  Reuse the Phase-10/Files downscale discipline (request size tied to the cell, `inSampleSize`-style).
  Full-res decode OOMs the 2GB Adreno-320.
- **Failure/unsupported → alt text as plain centered 15sp `--text-2`**; no alt → omit the item; a failed
  image never blocks the rest of the prompt (Coil `error`/`onError` → swap to alt composable). SVG via
  Coil's SVG decoder (PNG/JPEG/SVG required) — verify Coil-SVG is on the classpath during planning
  (Coil 3 ships SVG as `coil-svg`; only `coil-compose` + `coil-network-okhttp` are currently in the
  catalog — **a `coil-svg` artifact may need adding** if SVG prompt images are in scope; flag for the
  planner). `[ASSUMED: coil-svg artifact name — verify on the Coil 3 docs/registry before adding]`

## Risks / Landmines

### Pitfall 1: Reinventing reducer semantics instead of porting
**What goes wrong:** Subtle deviations (pending-consumed-while-suppressed, align-preserve-vs-size-clear,
container nested-start-ignored-but-children-still-append) fail fixtures cryptically.
**Avoid:** Port the 7 JS modules near-verbatim; keep them pure; run fixtures continuously. The JS is the oracle.

### Pitfall 2: The mock-vs-reality trap (this project's recurring Nemesis — Nth strike noted in MEMORY)
**What goes wrong:** A too-lenient fake gcode stream (e.g. one that pre-strips `// `, or never emits the
`prompt_end` echo after a close-control dispatch) passes unit tests but the real Moonraker stream behaves
differently — and the prompt won't close on-device.
**Avoid:** (1) The fixtures use the REAL `// action:` form — don't strip it in the fake. (2) The close
control and `prompt_end`-emitting buttons must be proven END-TO-END: dispatch → printer runs → echo line
returns → reducer closes. Unit tests can't cover the echo round-trip; **on-device manual run with
`macro-examples.cfg` is mandatory** (D-14 + Success Criterion 4). (3) Harden any `FakeCommandDispatcher`
to the real contract.

### Pitfall 3: Re-entrancy / replace-active-prompt
**What goes wrong:** A second `prompt_begin` mid-prompt, or rapid begin/show/end, leaves stale items or a
stuck overlay. **Avoid:** the reducer's `begin` always starts from `freshIdle(epoch+1)` — trust it; key
the Compose overlay on `promptEpoch` so a replace forces a clean recomposition. Don't hold render state
outside the view.

### Pitfall 4: Button key instability → busy-guard breaks
**What goes wrong:** Using the gcode string or a recomposition-derived value as the dispatch key makes the
debounce/in-flight guard misfire. **Avoid:** stable `prompt:<epoch>:<index>` keys; footer/close keys fixed.

### Pitfall 5: Adreno-320 fill-rate on images/rows
**What goes wrong:** Unbounded image decode OOMs; a wide row of cells over-draws. **Avoid:** hard Coil
bounding (above); equal-width `Row(weight(1f))` cells; no continuous animation (one-shot show/hide only).
Re-measure if a prompt carries multiple images (matches the Phase-5/10 perf-gate discipline).

### Pitfall 6: Disconnect emitting `prompt_end` (would close OTHER clients)
**What goes wrong:** Treating disconnect like the close control and dispatching `prompt_end`. **Avoid:**
disconnect/process-death = LOCAL reducer reset, NEVER a gcode dispatch (D-10, SPEC §Lifecycle "local-only events must not emit prompt_end").

### Pitfall 7: Stale committed `fixtures.json` copy
**What goes wrong:** The test corpus drifts from the sibling protocol repo. **Avoid:** assert
`schema_version == 1`, count == 26, and add a header comment + a Gradle copy task or a documented refresh
step. (The repo is paused at clean `main`; low churn risk, but guard anyway.)

## Validation Architecture

> Nyquist enabled (`workflow.nyquist_validation: true`). `[VERIFIED: .planning/config.json]`

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit4 4.13.2 (host/unit), Compose UI test + AndroidX instrumented (on-device), Macrobenchmark (perf) |
| Config file | `app/build.gradle.kts` (test deps) — no separate test config |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.prompt.*' --no-daemon"` (list classes explicitly — the glob false-fails on this AGP, per MEMORY) |
| Full suite command | `... "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` ; instrumented: `:app:connectedDebugAndroidTest` |

### What is HOST-testable (pure, no I/O — the bulk and the conformance gate)
- `parseAction` (line → event), all sub-parsers (`parseButtonFields`, `parseImageScale`, `isValidImagePath`, `normalizeStyle`).
- `reducePrompt` state machine + `promptView` projection.
- `parseMarkup` AST + `markupToPlainText`.
- **The 26-fixture conformance gate** (parameterized, the headline test).
- The 8 `hardening.spec.ts` edge cases re-ported.

### What needs INSTRUMENTED / ON-DEVICE proof (the mock-vs-reality gates)
- `PromptDialog` render (overlay regions, button styles, empty/degenerate states, markup spans, image bounding) → Compose UI test + manual eyeball.
- Button gcode dispatch → `CommandDispatcher` → `printer.gcode.script` wiring (assert method/params; the round-trip needs a live printer).
- **Close control / `prompt_end` echo round-trip** (close → dispatch → inbound line → reducer closes) — needs live or a faithful fake that echoes.
- **Disconnect closes locally, emits NO `prompt_end`** — instrumented with the existing `LiveReconnectYank`/`LiveSocketReconnect` test harness.
- **On-device manual run of `macro-examples.cfg`** on a real printer (E5=192.168.1.120:7125 / E3=192.168.1.121:7125) — Success Criterion 4; the load-filament wizard + jog cluster exercise rows, buttons, groups, images, live-append.

### Phase Requirements → Test Map
| Req (Success Criterion) | Behavior | Test Type | Command / Gate | Exists? |
|--------|----------|-----------|-------------------|---------|
| SC-1 parse tolerant of malformed/partial | parser/reducer total, never crashes | unit (fixtures + hardening) | `testDebugUnitTest --tests 'works.mees.dinghy.prompt.PromptFixtureTest'` | ❌ Wave 0 |
| SC-2 render dialog, buttons fire gcode | overlay assembly + dispatch | instrumented + UI test | `connectedDebugAndroidTest` | ❌ Wave 0 |
| SC-3 end/footer/re-entrancy, garbage never wedges Console | reducer replace + console independence | unit + instrumented | fixtures + console-filter test | ❌ Wave 0 |
| SC-4 provable with a real macro | live macro run | manual on-device | `macro-examples.cfg` on E5/E3 | ❌ manual |

### Sampling rate
- **Per task commit:** the prompt unit suite (fixtures + parser + reducer + markup).
- **Per wave merge:** full `:app:testDebugUnitTest`.
- **Phase gate:** full host suite green + on-device `macro-examples.cfg` run + disconnect-close instrumented test before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `app/src/test/resources/prompt/fixtures.json` — committed copy of the corpus (+ schema/count guard).
- [ ] `PromptFixtureTest.kt` — parameterized 26-fixture gate (port `fixtures.spec.ts` driver + `toMatchObject` comparator), incl. `dinghy→["touch"]` frontend row.
- [ ] `PromptReducerTest.kt` / `PromptMarkupTest.kt` / `PromptParseTest.kt` — port the per-module JS specs + the 8 hardening cases.
- [ ] No new framework install — JUnit4 + Compose test already present.

## Security Domain

> `security_enforcement: true` `[VERIFIED: .planning/config.json]`. The protocol has an explicit security model (SPEC §Security Model).

### Applicable ASVS Categories
| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | YES | The entire parser is hostile-input handling: total functions, malformed/unknown input degrades (never throws/wedges). Fixtures + hardening tests are the validation gate. |
| V5 Injection (markup) | YES | PromptMarkup is **NOT HTML/Pango** — render only as `AnnotatedString` spans; **never** inject into a WebView/HTML, never execute scripts/links/CSS. SVG images render as image assets only (Coil decoder), never markup-injected. |
| V5 Path traversal (images) | YES | `isValidImagePath` rejects `..`, absolute, `~`, `:`, `\`, non-`config/` — ported verbatim and enforced before any HTTP load. |
| V6 Cryptography | NO | No crypto in this phase. |
| V2/V3/V4 Auth/Session/Access | NO (inherited) | Connection auth/API-key handled by the existing session layer; this phase adds no new transport (D-13). |
| Secrets in logs | YES (inherited) | `CommandDispatcher` already redacts `?token=` from failure messages (T-05-11-01); reuse — never build a toast from raw exception text. |

### Known Threat Patterns
| Pattern | STRIDE | Mitigation |
|---------|--------|------------|
| Malicious markup → script/HTML injection | Tampering/Elevation | AnnotatedString only; no HTML/WebView; entities decoded once, never re-parsed |
| Image path traversal to read non-config files | Information Disclosure | `isValidImagePath` allowlist (`config/...` only); rejected → alt text |
| `prompt_end` broadcast closing other clients on local events | DoS (cross-client) | Disconnect/teardown = local reducer reset, NEVER a `prompt_end` dispatch (D-10) |
| Macro gcode is arbitrary command execution | (by design) | NOT sandboxed — SPEC §Security: button gcode is intentional macro-authored execution; Dinghy does not second-guess (no extra ConfirmGuard, D-11). The author signals danger via `error` style. |
| Hostile/garbage stream wedging the Console | DoS | Engine + console are independent subscribers on an un-throttled buffer; parser is total. |

## Project Constraints (from CLAUDE.md)
- **Build Windows-side:** `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"` — `./gradlew` does NOT run from WSL. Pipe through `tr -d '\r'`; exit code is authoritative.
- **minSdk 23 floor** (Nexus 7 2013 / Adreno 320 perf floor). No dependency that drops below 23. (No new deps here.)
- **UI LAW is `docs/ui_design/`** — chrome via semantic tokens only; the **author-hex carve-out (D-03) must be documented in `docs/ui_design/CLAUDE.md` + `THEMING.md`** as part of this phase.
- **Fonts-too-small recurring lesson** — use `fsSp(baseSp, t.fs)`, floor 15sp. The four markup sizes = 15/18/22/28 (UI-SPEC).
- **Mock-vs-reality lesson** — live/on-device gates are mandatory; harden fakes to the real Moonraker contract (the `// action:` form, the `prompt_end` echo round-trip).
- **Codex final-plan review** — run final plans by Codex before execute (per MEMORY workflow rule).
- **GSD-only edits** — no direct repo edits outside a GSD workflow.

## Assumptions Log
| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `coil-svg` artifact is needed for SVG `prompt_image` (only `coil-compose` + `coil-network-okhttp` are in the catalog) | Image bounding | If SVG prompts are in scope and the decoder is missing, SVGs silently fail → alt-text only. Planner: verify Coil 3 SVG artifact name/availability before adding. Low blast radius (alt-text fallback covers it). |
| A2 | Moonraker serves `config/...` images at a derivable URL reusing the existing base-URL plumbing | Image bounding | If the route differs, image load fails → alt-text fallback. Confirm the Moonraker file route during planning (Phase-10/Files already resolve Moonraker URLs). |
| A3 | `Connected→Disconnected/Error` is the right D-10 trigger (not `Syncing`/`Connecting`) | Wiring | Closing too eagerly on a transient reconnect blip would flicker the prompt. Coordinate with Phase-13 reconnect state. |

## Open Questions
1. **Where does `PromptEngine` live / who owns its scope?**
   - Known: spine-level, long-lived, alongside `CommandDispatcher` (so the overlay floats over any screen).
   - Unclear: exact construction seam in `di/AppContainer` / `SpineHandle` and how `AppShell` reads `visible`.
   - Recommendation: follow the calibration-holder + `MacroExecutionPopup` wiring; planner confirms the seam against current `AppShell`/`AppContainer`.
2. **Commit a copy of `fixtures.json` vs read the sibling repo at test time?**
   - Recommendation: commit a copy under `src/test/resources/prompt/` (hermetic), guard with `schema_version`+count assertions, document the refresh step. The sibling repo is paused/stable.
3. **Coil SVG decoder presence** (see A1) — verify before planning the image task.

## Environment Availability
| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Android SDK / `gw.bat` build | all build/test | ✓ | SDK platform-35, build-tools 34, JDK 21 | — |
| JUnit4 | host conformance tests | ✓ | 4.13.2 | — |
| Compose UI test + AndroidX instrumented | overlay/instrumented | ✓ | via BOM | — |
| Coil 3 (compose + okhttp) | image render | ✓ | 3.1.0 | — |
| Coil 3 SVG decoder | SVG prompt images | ✗ (not in catalog) | — | alt-text fallback; or add `coil-svg` (verify) |
| kotlinx.serialization json | load fixtures.json in test | ✓ | 1.7.3 | — |
| Live printer (E5/E3 Moonraker) | SC-4 on-device macro run | ✓ (on LAN) | E5 .120:7125 / E3 .121:7125 | manual gate; can't be automated |
| `fixtures.json` + `macro-examples.cfg` | conformance + manual | ✓ (sibling repo) | schema_version 1, 26 fixtures | copy into test resources |

**Missing with no fallback:** none.
**Missing with fallback:** Coil SVG decoder (alt-text covers it until verified/added).

## State of the Art
| Old Approach | Current Approach | When | Impact |
|--------------|------------------|------|--------|
| Each frontend's ad-hoc `action:prompt_*` interpretation | A shared spec + renderer-neutral fixtures + clean-room reference engine | 2026-05 (this repo) | Dinghy validates against the SAME oracle as every other frontend; it's the first *native* full-v1 renderer |
| Mainsail snapshot-on-show | Live-append after show (KlipperScreen/Fluidd-ref/this) | v1 optional ext | Dinghy sets `liveAppend=true` (D-02) |

**Deprecated/out of scope:** `prompt_input` (reserved, not v1), `prompt_text_scale` / `prompt_image_scale` (KlipperScreen local aliases, not v1). Do not implement.

## Sources
### Primary (HIGH)
- `klipper-macro-prompt-protocol/SPEC.md` — full protocol (read in full)
- `packages/js/src/{reducer,parse-action,markup,button,image,style,view,types,index}.ts` — the reference engine (read in full)
- `packages/js/test/{fixtures,hardening}.spec.ts` — the conformance driver + edge cases (read in full)
- `fixtures/fixtures.json` (26 fixtures), `fixtures/README.md` — the oracle
- Dinghy code (read): `state/PrinterStateStore.kt`, `net/{JsonRpcClient,MoonrakerSession}.kt`, `command/CommandDispatcher.kt`, `calibration/ProbeCalibrateHolder.kt`, `ui/console/{ConsoleSeverity,ConsoleFilters}.kt`, `ui/shell/AppShell.kt`, `net/GoldenFixtures.kt`, `state/PrinterState.kt`, `gradle/libs.versions.toml`, `.planning/config.json`
- `12-CONTEXT.md`, `12-UI-SPEC.md` — locked decisions + render contract

### Secondary (MEDIUM)
- MEMORY.md — build env, mock-vs-reality lesson, fonts-too-small, AGP glob gotcha, printer IPs, Codex-review rule

### Tertiary (LOW)
- A1 (coil-svg artifact) — not yet verified against the Coil 3 registry; flagged ASSUMED.

## Metadata
**Confidence breakdown:**
- Reducer/parser/markup semantics: HIGH — extracted from complete working reference + fixtures.
- Fixture-port approach: HIGH — project already has the exact loader + parameterized idiom.
- Wiring: HIGH — calibration holder + AppShell overlay + dispatcher are read and directly analogous.
- Image rendering: MEDIUM — Coil bounding is established (Phase 10), but SVG decoder presence + Moonraker URL route need a quick planning-time confirm.

**Research date:** 2026-06-04
**Valid until:** ~30 days (protocol repo paused at stable `main`; Dinghy deps pinned).

## RESEARCH COMPLETE
Phase 12 is a near-mechanical Kotlin port of a small, total, fully-specified JS reference engine gated by 26 renderer-neutral fixtures (host-testable) plus a reuse-heavy full-screen Compose overlay — the only genuinely new work is the markup→AnnotatedString builder (with the D-03 author-hex carve-out), the dialog assembly, the spine-level holder wiring (gcode stream → reducer → CommandDispatcher → AppShell, disconnect=local-close-no-prompt_end), and Coil-bounded images; zero new dependencies, and the on-device `macro-examples.cfg` run is the mandatory mock-vs-reality gate.
