---
phase: quick-260601-sip
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - app/src/main/java/works/mees/dinghy/state/PrintMetadata.kt
  - app/src/test/java/works/mees/dinghy/state/PrintMetadataParseTest.kt
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintMetadataHolder.kt
  - app/src/test/java/works/mees/dinghy/ui/printstatus/PrintMetadataHolderTest.kt
  - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
  - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
  - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
  - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
autonomous: true
requirements: [SHELL-04]
must_haves:
  truths:
    - "When a print starts, the gcode metadata is fetched exactly ONCE (keyed on the active filename) and re-fetched only when the filename changes."
    - "While printing, the ProgressRing center shows the print's gcode thumbnail (Coil 3 AsyncImage); idle reverts to the Benchy image."
    - "The Layer cell denominator shows metadata layer_count; the Z cell shows the metadata object_height as final-height context; both fall back to '—' when metadata is absent."
    - "The Remaining cell shows estimated_time × (1 − progress) formatted H:MM, falling back to '—' when estimated_time is unknown."
    - "Every field is read STRICTLY from docs/moonraker-capabilities.md; unconfirmed fields are never invented and degrade to a dashed cell / Benchy."
  artifacts:
    - path: "app/src/main/java/works/mees/dinghy/state/PrintMetadata.kt"
      provides: "PrintMetadata model + pure parsePrintMetadata mapper + pure thumbnailUrl builder"
      contains: "fun parsePrintMetadata"
    - path: "app/src/main/java/works/mees/dinghy/ui/printstatus/PrintMetadataHolder.kt"
      provides: "Session-scoped one-shot-per-filename metadata fetcher exposing StateFlow<PrintMetadata?>"
      contains: "class PrintMetadataHolder"
  key_links:
    - from: "app/src/main/java/works/mees/dinghy/ui/printstatus/PrintMetadataHolder.kt"
      to: "server.files.metadata"
      via: "injected rpc.request lambda, fired once per new printing filename"
      pattern: "FILES_METADATA"
    - from: "app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt"
      to: "PrintMetadataHolder.metadata"
      via: "collectAsStateWithLifecycle off the SpineHandle"
      pattern: "metadata"
---

<objective>
Status/home screen **Inc 2** — light up the currently-dashed cells by fetching gcode metadata ONCE
per print file (keyed on the active filename) via the Moonraker `server.files.metadata` method, then
wiring three derived surfaces into the existing `PrintStatusScreen`:

1. **gcode thumbnail** into the `ProgressRing` center while printing (Coil 3 `AsyncImage`); idle → Benchy.
2. **Total layers** (`layer_count`) into the Layer-cell denominator; **final height** (`object_height`)
   into the Z-cell context.
3. **Time remaining** = `estimated_time × (1 − progress)`, formatted `H:MM`, into the Remaining cell.

Purpose: Inc 1 left these cells dashed with explicit `// until Inc 2` TODOs in `PrintStatusScreen.kt`.
This closes them using the ONLY new networking the increment needs — one cached metadata read.

Output: a pure parser/model (`PrintMetadata.kt`), a host-testable reactive holder
(`PrintMetadataHolder.kt`), session-scope wiring through `SpineHandle`/`AppContainer`/`MoonrakerService`,
and the screen edits that consume the resulting `StateFlow<PrintMetadata?>`.

**Hard constraint — catalog fidelity:** Build STRICTLY against `docs/moonraker-capabilities.md`. The
confirmed metadata fields are `estimated_time`, `layer_count`, `object_height`, and
`thumbnails[] = [{width,height,size,relative_path}]`. `progress` comes from the LIVE
`virtual_sdcard.progress` / `display_status.progress` (already reduced into `PrinterState.progress`).
Any field NOT in that doc is NOT invented — the cell stays dashed / the ring keeps Benchy.

**Local build:** Windows-side via `E:\Android\gw.bat` (see CLAUDE.md "Local Build Environment");
`./gradlew` does NOT run from WSL. On-device install/UAT is Matthew-driven, NOT part of automated verify.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@docs/moonraker-capabilities.md

# UI design law (read before touching any screen)
@docs/ui_design/LAYOUT.md
@docs/ui_design/THEMING.md

# The screen + grid this increment lights up (note the `// until Inc 2` TODOs)
@app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt

# The established one-shot-read pattern to MIRROR (05-03): pure mapper + StateFlow on the store,
# injectable rpc.request seam, best-effort runCatching, null-safe wire walks (never `!!`).
@app/src/main/java/works/mees/dinghy/state/TemperatureStore.kt
@app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt

# State model (filename / printState / progress sources), JSON-RPC method constants + loose-JSON posture
@app/src/main/java/works/mees/dinghy/state/PrinterState.kt
@app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
@app/src/main/java/works/mees/dinghy/net/JsonRpcClient.kt

# Session-scoped wiring seams the holder rides on (mirror minExtrudeTemp/temperatureBackfill)
@app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
@app/src/main/java/works/mees/dinghy/di/AppContainer.kt
@app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
@app/src/main/java/works/mees/dinghy/config/ConnectionConfig.kt

# Coil 3 AsyncImage call shape already proven in the repo
@app/src/main/java/works/mees/dinghy/bench/ComposeBenchScene.kt
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Pure PrintMetadata model + parser + thumbnail-URL builder</name>
  <files>app/src/main/java/works/mees/dinghy/state/PrintMetadata.kt, app/src/test/java/works/mees/dinghy/state/PrintMetadataParseTest.kt, app/src/main/java/works/mees/dinghy/net/JsonRpc.kt</files>
  <behavior>
    parsePrintMetadata(result: JsonObject):
    - Maps a confirmed `server.files.metadata` reply (see docs/moonraker-capabilities.md § "File metadata")
      to a PrintMetadata with: layerCount (Int? from `layer_count`), objectHeight (Double? from
      `object_height`), estimatedTime (Double? from `estimated_time`, seconds), and largestThumbRelPath
      (String? = the `relative_path` of the LARGEST thumbnail by width — typically the 300x300).
    - A missing/garbage field yields null for THAT field (never `!!`, mirror TemperatureStore null-safety);
      an entirely empty object yields PrintMetadata with all-null fields.
    - thumbnails[]: pick the entry with the greatest `width`; if the array is absent/empty → largestThumbRelPath null.

    thumbnailUrl(httpBase: String, gcodeFilename: String, relPath: String): String
    - Builds `<httpBase>/server/files/gcodes/<dir>/<relPath>` where `<dir>` = the directory portion of
      gcodeFilename ("" for a root file → no extra slash); URL-ENCODE each path segment (filenames have
      spaces — capabilities doc § "Thumbnail URL construction"). relPath segments (e.g. ".thumbs/<name>-300x300.png")
      are also encoded per-segment but `/` separators preserved.

    Test cases (host JVM, no Android, no I/O — mirror TemperatureStore tests):
    - Test 1: a faithful E5 metadata JsonObject (from the capabilities doc sample) → layerCount=50,
      objectHeight≈ (use the E3 sample's object_height 9.96 in a second fixture; E5 sample omits it → null),
      estimatedTime=2191.0, largestThumbRelPath ends with "-300x300.png".
    - Test 2: empty object → all fields null.
    - Test 3: thumbnails present but only 32/48 sizes → largest is the 48 one (no fabricated 300).
    - Test 4: thumbnailUrl for a file in a subdirectory "miata/plate.gcode" with relPath ".thumbs/plate-300x300.png"
      → "<base>/server/files/gcodes/miata/.thumbs/plate-300x300.png"; a root file "plate.gcode" → no leading
      "/" before ".thumbs"; a filename with a space is percent-encoded.
  </behavior>
  <action>
Create `state/PrintMetadata.kt`. Define `data class PrintMetadata(val layerCount: Int?, val objectHeight: Double?, val estimatedTime: Double?, val largestThumbRelPath: String?)`. Add a top-level pure `fun parsePrintMetadata(result: kotlinx.serialization.json.JsonObject): PrintMetadata` and a pure `fun thumbnailUrl(httpBase: String, gcodeFilename: String, relPath: String): String`. Walk the JSON null-safely exactly like `parseTemperatureStore` (`as? JsonObject`, `jsonPrimitive`, `intOrNull`/`doubleOrNull`, `runCatching`) — NO `!!`, NO assumed fields. For the thumbnail pick, iterate `thumbnails` as a `JsonArray`, choose max by `width` (int), read its `relative_path` string. Build fields ONLY for the four catalog-confirmed keys; do not read anything not present in docs/moonraker-capabilities.md. For URL encoding use `java.net.URLEncoder.encode(seg, "UTF-8").replace("+", "%20")` per path segment, joining the directory of `gcodeFilename` (substringBeforeLast('/', "")) and the `relPath` (split on '/', encode each segment, rejoin with '/'); omit the dir segment entirely when it is blank. Add a single new method constant to `net/JsonRpc.kt` `JsonRpcMethods`: `const val FILES_METADATA = "server.files.metadata"` (placed near the other request constants, with a one-line comment citing 260601-sip Inc 2). Keep the parser/model 100% toolkit-agnostic plain Kotlin (no Compose, no coroutines) so it is host-unit-testable.

Then create `app/src/test/java/works/mees/dinghy/state/PrintMetadataParseTest.kt` covering the four cases above, building the input `JsonObject`s with `MoonrakerJson.parseToJsonElement(rawJson).jsonObject` from raw JSON string literals copied to match the capabilities-doc shapes (faithful fixtures, per the mock-vs-reality lesson — do NOT hand-build a too-lenient object).
  </action>
  <verify>
    <automated>/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon --tests works.mees.dinghy.state.PrintMetadataParseTest" 2>&1 | tr -d '\r' | tail -20</automated>
  </verify>
  <done>parsePrintMetadata + thumbnailUrl exist as pure functions; PrintMetadataParseTest passes all 4 cases; JsonRpcMethods.FILES_METADATA defined. No invented fields (only layer_count/object_height/estimated_time/thumbnails read).</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Reactive one-shot-per-filename PrintMetadataHolder + session wiring</name>
  <files>app/src/main/java/works/mees/dinghy/ui/printstatus/PrintMetadataHolder.kt, app/src/test/java/works/mees/dinghy/ui/printstatus/PrintMetadataHolderTest.kt, app/src/main/java/works/mees/dinghy/di/SpineHandle.kt, app/src/main/java/works/mees/dinghy/di/AppContainer.kt, app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt</files>
  <behavior>
    PrintMetadataHolder(scope, printerState: StateFlow<PrinterState>, fetch: suspend (filename: String) -> JsonElement?):
    - Exposes `metadata: StateFlow<PrintMetadata?>` (null when not printing / metadata unavailable).
    - Collects printerState. Derives the "active print filename" = printFilename WHEN
      printState is Printing or Paused, else "" (idle).
    - On a TRANSITION to a NON-BLANK active filename that DIFFERS from the last-fetched key: call fetch(filename)
      exactly ONCE, parsePrintMetadata the result, publish it, and remember the key. A subsequent identical
      filename (every status delta carries it) does NOT re-fetch (one-shot, cached on filename).
    - On a transition to blank (idle / standby / complete / cancelled / error): clear metadata to null and
      reset the cached key so the NEXT print re-fetches.
    - fetch returning null (best-effort, e.g. printer rejected/absent) → metadata stays null, cell degrades; a
      later filename change still retries.

    Tests (runTest virtual time, injected fetch lambda + a MutableStateFlow<PrinterState>):
    - Test 1: emit Printing+filename "a.gcode" repeatedly (5×) → fetch called exactly ONCE; metadata reflects parsed value.
    - Test 2: filename changes "a.gcode" → "b.gcode" while still Printing → fetch called a 2nd time (re-keyed).
    - Test 3: Printing→Standby (filename blank) → metadata becomes null AND a later Printing of the SAME "a.gcode" re-fetches.
    - Test 4: fetch returns null → metadata stays null, no crash.
  </behavior>
  <action>
Create `ui/printstatus/PrintMetadataHolder.kt` — a plain-Kotlin holder mirroring `PrintStatusHolder`'s shape (constructor-injected `scope: CoroutineScope`, a `StateFlow<PrinterState>`, and a substitutable `fetch: suspend (String) -> kotlinx.serialization.json.JsonElement?` seam so it is host-testable WITHOUT a real socket — exactly the injectable-rpc discipline `MoonrakerSession`/`CommandDispatcher` use). Hold `private var lastKey: String? = null` and a `MutableStateFlow<PrintMetadata?>(null)` exposed as `val metadata`. In `init { scope.launch { printerState.collect { state -> ... } } }` compute `active = if (state.printState == PrintState.Printing || state.printState == PrintState.Paused) state.printFilename else ""`. When `active.isBlank()`: if `lastKey != null` set metadata=null and lastKey=null. When `active.isNotBlank() && active != lastKey`: set `lastKey = active`, then `runCatching { fetch(active) }.getOrNull()?.let { metadata.value = parsePrintMetadata(it.jsonObject) }` — best-effort, never throws into the collector. Document that fetch is fired on the collect coroutine but is a single suspend call per key (the conflated 250ms store flow means re-emits of the same filename are cheap no-ops past the `active != lastKey` guard).

Wire the production fetch: in `service/MoonrakerService.kt` `buildSpineAndLaunch`, after constructing `rpc`/`store`/`session`, build `val metadataHolder = PrintMetadataHolder(serviceScope, store.printerState) { filename -> runCatching { rpc.request(JsonRpcMethods.FILES_METADATA, buildJsonObject { put("filename", filename) }) }.getOrNull() }` (import `buildJsonObject`/`put` from kotlinx.serialization.json). The `rpc.request` returns the `result` JsonElement; metadata reply is `{ ...fields... }` (NOT wrapped in `status`), so parse the result object directly. Add `httpBase = cfg.httpBase` and `metadata = metadataHolder.metadata` to the published `SpineHandle`.

Extend `di/SpineHandle.kt`: add `val httpBase: String` (the `http://host:port` base for building thumbnail URLs in the UI — sourced from `cfg.httpBase`) and `val metadata: StateFlow<PrintMetadata?>` with KDoc mirroring the existing one-shot-read fields (written once per filename; null when idle/unavailable).

Extend `di/AppContainer.kt`: add derived convenience flows `val printMetadata: Flow<PrintMetadata?> = spine.flatMapLatest { it?.metadata ?: flowOf(null) }` and `val httpBase: Flow<String> = spine.map { it?.httpBase ?: "" }` (mirror the existing `printerState`/`dispatcher` derivations).

Create `app/src/test/java/works/mees/dinghy/ui/printstatus/PrintMetadataHolderTest.kt` with the four runTest cases using a `MutableStateFlow<PrinterState>`, a counting injected fetch lambda returning a faithful metadata `JsonElement` (reuse a raw-JSON fixture parsed via `MoonrakerJson`), and `TestScope`. Advance the virtual clock as the existing holder tests do; assert the fetch call count and `metadata.value`.
  </action>
  <verify>
    <automated>/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon --tests works.mees.dinghy.ui.printstatus.PrintMetadataHolderTest" 2>&1 | tr -d '\r' | tail -20</automated>
  </verify>
  <done>PrintMetadataHolder fetches once per filename, re-fetches on filename change, clears on idle, survives null fetch; all 4 tests pass. SpineHandle carries httpBase + metadata; AppContainer exposes printMetadata + httpBase; MoonrakerService builds + publishes the holder. Full release unit suite still compiles.</done>
</task>

<task type="auto">
  <name>Task 3: Wire the screen — thumbnail in the ring, layers/height/remaining cells</name>
  <files>app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt</files>
  <action>
In `PrintStatusScreen.kt`, collect the two new flows alongside the existing ones: `val metadata by container.printMetadata.collectAsStateWithLifecycle(initialValue = null)` and `val httpBase by container.httpBase.collectAsStateWithLifecycle(initialValue = "")`. Thread `metadata` + `httpBase` down into `PrintStatusFocus` and `StatGrid` (add params; keep them defaulting so any other caller/test still compiles).

**(1) Thumbnail in the ring center** — in `PrintStatusFocus`, inside the existing circle-clipped preview `Box` (the `Modifier.fillMaxSize(0.9f).clip(CircleShape)` box that currently shows Benchy only when `!printing`): when `printing` AND `metadata?.largestThumbRelPath != null` AND `httpBase.isNotBlank()` AND the filename is non-blank, render `AsyncImage` (coil3.compose.AsyncImage + coil3.request.ImageRequest, the call shape in ComposeBenchScene.kt) with `model = ImageRequest.Builder(context).data(thumbnailUrl(httpBase, state.printFilename, metadata!!.largestThumbRelPath!!)).build()`, `contentDescription = null`, `Modifier.fillMaxSize()`, `contentScale = ContentScale.Crop`. Provide a graceful fallback: if printing but no thumbnail URL is available yet, leave the center EMPTY (the ring + % still read) — do NOT show Benchy while printing. Idle path unchanged (Benchy). Use `LocalContext.current` for the Coil context. Do NOT register a custom ImageLoader — the app's default Coil loader with coil-network-okhttp is already on the classpath; cleartext to the LAN printer rides the same NSC posture the websocket/REST already use.

**(2) Layer denominator + Z context** — in the Focus `Z … · Layer …` line and/or the `StatGrid` LAYER cell: the Layer denominator (total) becomes `state.totalLayer ?: metadata?.layerCount` (live slicer value preferred, metadata `layer_count` as the reliable fallback per the catalog), formatted, else "—". For the Z cell, keep the live `fmtZ(state)` as the active value and surface `metadata?.objectHeight` as the INACTIVE/context line (final height, formatted via the existing `fmt`), "—" when null. (The existing `IconTwoRowCell` already takes active/inactive — feed objectHeight into the Z cell's inactive slot.) Do NOT fabricate: if both live total and metadata layer_count are null → "—".

**(3) Remaining cell** — replace the hardcoded `value = "—"` Remaining `IconValueCell` (the `timer_arrow_down` one with the `// — until Inc 2` intent): compute remaining seconds = `metadata?.estimatedTime?.let { it * (1.0 - state.progress.coerceIn(0.0, 1.0)) }`; render via the existing `fmtDuration(...)` when non-null and `> 0`, else "—". Use `state.progress` (already sourced from virtual_sdcard/display_status progress in the reducer — confirmed in PrinterStateReducer). Recolor the cell value from `t.text3` (dashed) to `t.text` when a real value is present.

Update the KDoc "## Deferred / Inc 2" comment block at the top of the file to reflect that Inc 2 is now wired (thumbnail + total layers + object_height + slicer-estimate ETA), leaving only Inc 3 (tap-a-temp-cell / Tune button) deferred. Keep ALL color via `LocalTokens` (THEME-01) — no raw Color literals; keep GeistMono on the numeric readouts.
  </action>
  <verify>
    <automated>/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileReleaseKotlin :app:testReleaseUnitTest --no-daemon" 2>&1 | tr -d '\r' | tail -25</automated>
  </verify>
  <done>PrintStatusScreen compiles; while printing the ring center binds the gcode thumbnail (Benchy only when idle); Layer denominator falls back to metadata layer_count; Z cell shows object_height as context; Remaining shows estimated_time × (1 − progress) as H:MM with "—" fallback. Full release unit suite green. Token-pure (no raw Color). On-device thumbnail/ETA visual check is Matthew-driven (not automated here).</done>
</task>

</tasks>

<verification>
- `parsePrintMetadata` / `thumbnailUrl` read ONLY catalog-confirmed fields (`layer_count`, `object_height`,
  `estimated_time`, `thumbnails[].relative_path`/`width`); grep the new files for any other metadata key →
  none should appear.
- One-shot semantics: `PrintMetadataHolderTest` proves fetch fires once per filename, re-keys on change,
  clears on idle, and survives a null fetch.
- Graceful degradation: every consumer falls back to "—" / empty-center / Benchy when its source is null.
- Token purity: no raw `Color(` literals introduced in `PrintStatusScreen.kt` (all via `LocalTokens`).
- `time remaining` uses LIVE `state.progress` (virtual_sdcard/display_status), NOT a metadata progress field.
</verification>

<success_criteria>
- The three previously-dashed surfaces (ring thumbnail, Layer total, Remaining) and the Z final-height
  context are wired off a single cached `server.files.metadata` read keyed on the active filename.
- Metadata is fetched exactly once per print file and reverts to idle (Benchy / "—") when not printing.
- Nothing is built against an unconfirmed Moonraker field; missing data degrades gracefully.
- `:app:compileReleaseKotlin` + `:app:testReleaseUnitTest` pass; new parser + holder tests green.
</success_criteria>

<output>
Create `.planning/quick/260601-sip-status-print-metadata/260601-sip-SUMMARY.md` when done.
</output>
