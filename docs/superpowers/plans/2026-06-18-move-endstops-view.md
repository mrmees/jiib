# Move Endstops View Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a read-only "Endstops" sub-mode to the Move Hub that live-polls and displays each configured endstop's trigger state.

**Architecture:** Endstop state is NOT in the `objects/subscribe` stream — it is fetched on demand via the Moonraker JSON-RPC `printer.query_endstops/status`. We add a registry `CommandSpec` for it (with catalog/matrix rows so the drift gate stays green), a `query()` member on `CommandDispatcher` for request/response reads that bypass the fire-and-forget `inFlight` tracking, a pure `parseEndstops()` function, a new `MoveMode.Endstops` sub-mode with its Field row and Focus content, and a `LaunchedEffect` poll loop (~500ms) scoped to the Endstops Focus branch so it starts on enter and cancels on leave/background.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization (`JsonElement`/`JsonObject`), Coroutines, JUnit host tests. Build is Windows-side via `E:\Android\gw.bat` (see "Build & test commands" below).

---

## Build & test commands (this repo)

`./gradlew` does NOT work from WSL. Run every Gradle task through the Windows helper:

```bash
/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <gradle args>" 2>&1 | tr -d '\r'
```

- Run a single unit test class:
  `... "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.<pkg>.<Class>' --no-daemon"`
- Run the full unit suite: `... "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"`
- The process exit code is authoritative; Gradle's CR progress bars are why we pipe through `tr -d '\r'`.
- Ligature gate (WSL native python is fine): `python tools/verify_ligatures.py` (exit 0 = all registry glyphs resolve).

## File map

| File | Change | Responsibility |
|------|--------|----------------|
| `app/src/main/java/works/mees/dinghy/net/JsonRpc.kt` | Modify | Add `QUERY_ENDSTOPS_STATUS` method constant |
| `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` | Modify | Add `queryEndstops` spec; add it to `all` |
| `docs/commands/catalog.json` | Modify | Add the `MR-printer.query_endstops.status` catalog row (drift gate) |
| `docs/commands/printer-matrix.json` | Modify | Add the matching `command_availability` row (drift gate) |
| `app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt` | Modify | Add `query(command, args)` member (request/response read) |
| `app/src/main/java/works/mees/dinghy/ui/move/EndstopState.kt` | Create | `EndstopStatus` model, `endstopLabel()`, `parseEndstops()` |
| `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` | Modify | Add `CropFree` + `CenterFocusStrong` (val + `all`) |
| `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` | Modify | New `MoveMode.Endstops`, header mapping, Field row, Focus content + poll loop, wrapper query lambda |
| `app/src/test/java/works/mees/dinghy/command/CommandDispatcherTest.kt` | Modify | Test `query()` routes + skips inFlight |
| `app/src/test/java/works/mees/dinghy/ui/move/EndstopStateTest.kt` | Create | `parseEndstops()` unit tests |

> **Icon law note:** the glyphs are OWNER-ASSIGNED — `crop_free` (open/inactive), `center_focus_strong` (triggered/active AND the list-row icon). Do NOT substitute. Both are already resolvable in the bundled `material_symbols_outlined.ttf` (verified), so no font subsetting is required.

---

## Task 1: Add the `queryEndstops` command spec + method constant

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/net/JsonRpc.kt`
- Modify: `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt:206-211` (insert after `objectsQuery`)
- Modify: `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt:818` (the `all` list, near `objectsQuery`)

- [ ] **Step 1: Add the method constant**

In `JsonRpc.kt`, in `object JsonRpcMethods` under `// Requests (client → server)`, after `OBJECTS_SUBSCRIBE`:

```kotlin
    const val QUERY_ENDSTOPS_STATUS = "printer.query_endstops/status"
```

- [ ] **Step 2: Add the spec**

In `CommandRegistry.kt`, immediately after the `objectsSubscribe` spec (around line 217), add:

```kotlin
    /** One-shot read of every configured endstop's trigger state (`{"x":"open","z":"TRIGGERED"}`).
     *  NOT subscribable — must be polled via request/response (Move → Endstops view). */
    val queryEndstops: CommandSpec<Unit> = jsonRpc(
        catalogId = "MR-printer.query_endstops.status",
        method = JsonRpcMethods.QUERY_ENDSTOPS_STATUS,
        key = { "query_endstops" },
        params = { null },
    )
```

- [ ] **Step 3: Register it in `all`**

In `CommandRegistry.kt`, in the `val all: List<CommandSpec<*>> = listOf(` block, add `queryEndstops,` right after `objectsQuery,` (line ~818):

```kotlin
        objectsQuery,
        queryEndstops,
        objectsSubscribe,
```

- [ ] **Step 4: Update the EXISTING catalog row (do NOT append — it already exists as reference-only)**

`docs/commands/catalog.json` ALREADY contains an `MR-printer.query_endstops.status` entry marked
`reference_only` / `registered: false`. Appending a second row would duplicate the id. Instead,
FIND that existing object and change its `runtime_registry`, `semantics_tier`, and `purpose` so it
reflects that the command is now actually registered/sent. Specifically:

- `"semantics_tier": "light"` → `"semantics_tier": "full"`
- `"purpose": "Moonraker \`printer.query_endstops.status\` operation from the official external API documentation."`
  → `"purpose": "Read the current trigger state (open/TRIGGERED) of each configured endstop."`
- the whole `"runtime_registry"` object →
  ```json
  "runtime_registry": {
    "status": "registered",
    "registered": true,
    "notes": "Present in CommandRegistry for the Move → Endstops live poll."
  }
  ```

Leave `id`, `catalog_id`, `source_api`, `transport`, `category`, `params`, `key_params`,
`availability`, `predicate`, and the `*_semantics` fields untouched. Do NOT add a second object.

- [ ] **Step 5: Add the printer-matrix row**

In `docs/commands/printer-matrix.json`, add this object to the `command_availability` array:

```json
{
  "catalog_id": "MR-printer.query_endstops.status",
  "name": "printer.query_endstops.status",
  "source_api": "moonraker",
  "transport": "json_rpc",
  "registry_status": "registered",
  "predicate": {
    "type": "always"
  },
  "predicate_key": "always",
  "printer_status": [
    {
      "printer_id": "ender5plus",
      "status": "present",
      "evidence": "printer.query_endstops/status is a core Klipper endpoint present on all Klipper printers."
    },
    {
      "printer_id": "ender3",
      "status": "present",
      "evidence": "printer.query_endstops/status is a core Klipper endpoint present on all Klipper printers."
    }
  ]
}
```

- [ ] **Step 6: Run the drift gate to verify it passes**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.command.CommandCatalogDriftTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS (all 5+ drift tests green). If it reports the catalog id as "missing", the JSON edit is malformed or in the wrong array.

- [ ] **Step 7: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" add app/src/main/java/works/mees/dinghy/net/JsonRpc.kt app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt docs/commands/catalog.json docs/commands/printer-matrix.json
"/mnt/c/Program Files/Git/cmd/git.exe" commit -m "feat(move): add query_endstops command spec + catalog rows"
```

---

## Task 2: `parseEndstops()` pure function + model (TDD)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/move/EndstopState.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/move/EndstopStateTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/works/mees/dinghy/ui/move/EndstopStateTest.kt`:

```kotlin
package works.mees.dinghy.ui.move

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class EndstopStateTest {

    private fun parse(json: String) = parseEndstops(Json.parseToJsonElement(json))

    @Test
    fun `triggered is case-insensitive, everything else is open`() {
        val result = parse("""{"x":"open","y":"TRIGGERED","z":"triggered"}""")
        assertEquals(
            listOf(
                EndstopStatus("x", triggered = false),
                EndstopStatus("y", triggered = true),
                EndstopStatus("z", triggered = true),
            ),
            result,
        )
    }

    @Test
    fun `canonical x,y,z order first, then remaining keys alphabetically`() {
        // Input deliberately out of order with an extra probe key.
        val result = parse("""{"probe":"open","z":"open","x":"open","y":"open"}""")
        assertEquals(listOf("x", "y", "z", "probe"), result.map { it.name })
    }

    @Test
    fun `empty object yields empty list`() {
        assertEquals(emptyList<EndstopStatus>(), parse("{}"))
    }

    @Test
    fun `non-object element yields empty list`() {
        assertEquals(emptyList<EndstopStatus>(), parse(""""nope""""))
    }

    @Test
    fun `endstopLabel title-cases known axes and capitalizes others`() {
        assertEquals("X", endstopLabel("x"))
        assertEquals("Y", endstopLabel("y"))
        assertEquals("Z", endstopLabel("z"))
        assertEquals("Probe", endstopLabel("probe"))
        assertEquals("Manual_stepper", endstopLabel("manual_stepper"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.move.EndstopStateTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — compile error, `parseEndstops` / `EndstopStatus` / `endstopLabel` unresolved.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/works/mees/dinghy/ui/move/EndstopState.kt`:

```kotlin
package works.mees.dinghy.ui.move

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** One endstop's resolved state. [name] is the raw Moonraker key (e.g. "x", "probe");
 *  [triggered] is true when the reported value is "TRIGGERED" (case-insensitive). */
data class EndstopStatus(val name: String, val triggered: Boolean)

private val CANONICAL_ORDER = listOf("x", "y", "z")

/**
 * Parse a `printer.query_endstops/status` result object into ordered [EndstopStatus] rows.
 * Order: x, y, z first (when present), then any remaining keys alphabetically (so `probe` and
 * exotic endstops render in a stable position). A non-object element returns an empty list.
 */
fun parseEndstops(result: JsonElement): List<EndstopStatus> {
    val obj = result as? JsonObject ?: return emptyList()
    val ordered = obj.keys.sortedWith(
        compareBy(
            { CANONICAL_ORDER.indexOf(it).let { i -> if (i == -1) Int.MAX_VALUE else i } },
            { it },
        ),
    )
    return ordered.map { key ->
        val value = (obj[key] as? JsonPrimitive)?.content ?: ""
        EndstopStatus(name = key, triggered = value.equals("TRIGGERED", ignoreCase = true))
    }
}

/** Human label for an endstop key: X/Y/Z upper-cased, others capitalized ("probe" -> "Probe"). */
fun endstopLabel(name: String): String =
    if (name in CANONICAL_ORDER) name.uppercase()
    else name.replaceFirstChar { it.uppercase() }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.ui.move.EndstopStateTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" add app/src/main/java/works/mees/dinghy/ui/move/EndstopState.kt app/src/test/java/works/mees/dinghy/ui/move/EndstopStateTest.kt
"/mnt/c/Program Files/Git/cmd/git.exe" commit -m "feat(move): parseEndstops + endstopLabel"
```

---

## Task 3: `CommandDispatcher.query()` read member (TDD)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt`
- Test: `app/src/test/java/works/mees/dinghy/command/CommandDispatcherTest.kt`

**Why a new member (not the existing `JsonRpcClient.request(command,args)` extension):** the UI does not hold a `JsonRpcClient`; it holds the `CommandDispatcher` (via `container.dispatcher`). `dispatch()` is fire-and-forget and mutates `inFlight`; a polled read must NOT touch `inFlight` (it would flicker control-busy state every 500ms). `query()` is the read sibling of `dispatch()`.

- [ ] **Step 1: Write the failing test**

Add to `app/src/test/java/works/mees/dinghy/command/CommandDispatcherTest.kt` (match the file's existing imports/test style — it already builds a `CommandDispatcher` with an injected `request` lambda and a `TestScope`/`CoroutineScope`). Add:

```kotlin
    @Test
    fun `query routes spec method and params and returns the result without touching inFlight`() = runTest {
        val seen = mutableListOf<Pair<String, kotlinx.serialization.json.JsonElement?>>()
        val dispatcher = CommandDispatcher(
            request = { method, params, _ ->
                seen += method to params
                kotlinx.serialization.json.Json.parseToJsonElement("""{"x":"open","z":"TRIGGERED"}""")
            },
            scope = this,
        )

        val result = dispatcher.query(CommandRegistry.queryEndstops, Unit)

        assertEquals("printer.query_endstops/status", seen.single().first)
        assertEquals(null, seen.single().second)
        assertEquals(
            kotlinx.serialization.json.Json.parseToJsonElement("""{"x":"open","z":"TRIGGERED"}"""),
            result,
        )
        assertTrue(dispatcher.inFlight.value.isEmpty())
    }
```

> If `CommandDispatcherTest` uses a virtual-clock `timeSource` or a specific scope helper in its other tests, mirror that exact construction shape rather than the minimal form above.

- [ ] **Step 2: Run test to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.command.CommandDispatcherTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — `query` unresolved.

- [ ] **Step 3: Write the implementation**

In `CommandDispatcher.kt`, add this member inside the class (e.g. just after the `dispatch(...)` methods). Ensure `import kotlinx.serialization.json.JsonElement` is present (it is — `request` uses it):

```kotlin
    /**
     * One-shot request/response READ (e.g. `printer.query_endstops/status`). Unlike [dispatch],
     * this awaits and returns the raw result and does NOT register the call in [inFlight] — it is a
     * poll/read, not a user action, so it must never flicker control-busy state. Throws on a
     * non-JSON-RPC spec, a no-connection/send failure, or a per-request timeout (propagated from
     * the underlying transport).
     */
    suspend fun <P> query(
        command: CommandSpec<P>,
        args: P,
        requestTimeoutMs: Long = timeoutMs,
    ): JsonElement {
        require(command.transport == CommandTransport.JsonRpc) {
            "CommandDispatcher.query requires a JSON-RPC command; ${command.catalogId} uses ${command.transport}"
        }
        val method = requireNotNull(command.method) {
            "Command ${command.catalogId} does not define a JSON-RPC method"
        }
        return request(method, command.params(args), requestTimeoutMs)
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests 'works.mees.dinghy.command.CommandDispatcherTest' --no-daemon" 2>&1 | tr -d '\r'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" add app/src/main/java/works/mees/dinghy/command/CommandDispatcher.kt app/src/test/java/works/mees/dinghy/command/CommandDispatcherTest.kt
"/mnt/c/Program Files/Git/cmd/git.exe" commit -m "feat(command): CommandDispatcher.query read member (no inFlight)"
```

---

## Task 4: Register the two owner-assigned icons

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`

- [ ] **Step 1: Add the two vals**

In `DinghyIcons.kt`, near the other Move glyphs (after `MoveDisableMotors`, ~line 290), add:

```kotlin
    /** Move → Endstops: inactive/open endstop state (owner-assigned 2026-06-18). */
    val CropFree = DinghyIcon(IconRef.Ligature("crop_free"), alternate = "crop_free")
    /** Move → Endstops: triggered/active endstop state AND the Endstops list-row icon
     *  (owner-assigned 2026-06-18). */
    val CenterFocusStrong = DinghyIcon(IconRef.Ligature("center_focus_strong"), alternate = "center_focus_strong")
```

- [ ] **Step 2: Add both to the `all` list**

In `DinghyIcons.kt`, in `val all: List<DinghyIcon> = listOf(`, add to the Move-related line (the one containing `JogXPlus, HomeStateHomed, HomeStateUnhomed`):

```kotlin
        JogXPlus, HomeStateHomed, HomeStateUnhomed, CropFree, CenterFocusStrong,
```

- [ ] **Step 3: Run the ligature gate**

Run: `python tools/verify_ligatures.py`
Expected: exit 0 — "... ligatures in font, missing: []". (Both glyphs are confirmed resolvable in the bundled v2.944 font.)

- [ ] **Step 4: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
"/mnt/c/Program Files/Git/cmd/git.exe" commit -m "feat(icons): register CropFree + CenterFocusStrong for endstops view"
```

---

## Task 5: Add `MoveMode.Endstops` + header mapping + Field row

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` (sealed interface ~line 82; `moveModeHeader` ~line 821; Field list ~line 748, after the saved-location `items(...)`)

- [ ] **Step 1: Add the sub-mode to the sealed interface**

In `MoveScreen.kt`, in `sealed interface MoveMode { ... }`, add an object alongside the other singletons (e.g. after `SaveDialog`):

```kotlin
    /** Read-only live view of each configured endstop's trigger state. */
    data object Endstops : MoveMode
```

- [ ] **Step 2: Add the header mapping**

In `moveModeHeader(...)` (the `when (mode)` returning `Pair<String, DinghyIcon>`), add:

```kotlin
    MoveMode.Endstops -> "Endstops" to DinghyIcons.CenterFocusStrong
```

- [ ] **Step 3: Add the Field list row (LAST — after the bookmark rows)**

In the `field = { ListBlock { ... } }` block, immediately AFTER the `items(savedLocations, ...) { ... }` block (which renders the bookmark rows), add:

```kotlin
                    // Endstops view — always available; last row in the list (owner: after bookmarks).
                    item("endstops") {
                        MoveRow(
                            "Endstops",
                            DinghyIcons.CenterFocusStrong,
                            mode == MoveMode.Endstops,
                            grid.uDp,
                            t.accent,
                        ) { mode = MoveMode.Endstops }
                    }
```

- [ ] **Step 4: Compile (the Focus `when(mode)` is now non-exhaustive — expected)**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'`
Expected: FAIL — `when (mode)` in the Focus body must be exhaustive; missing branch `MoveMode.Endstops`. This is the compile-enforced reminder that Task 6 adds the Focus content. (If the Focus `when` has an `else`, there will be no error — proceed to Task 6 regardless.)

- [ ] **Step 5: (No commit yet — Task 6 completes the compile.)**

---

## Task 6: Endstops Focus content + live poll loop

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt` (the `MoveScreen` wrapper ~line 106; `MoveHubContent` signature ~line 173; the Focus `when (mode)` body ~line 236)

This task threads a suspend query lambda from the stateful `MoveScreen` wrapper (which holds `dispatcher`) down into the stateless `MoveHubContent` (which owns `mode`), then renders + polls inside the `MoveMode.Endstops` Focus branch.

- [ ] **Step 1: Add the query lambda parameter to `MoveHubContent`**

In `MoveHubContent(...)`'s parameter list, add (e.g. after `onDeleteLocation`):

```kotlin
    queryEndstops: suspend () -> List<EndstopStatus>,
```

- [ ] **Step 2: Wire the lambda in the `MoveScreen` wrapper**

In `MoveScreen(...)`, in the `MoveHubContent(...)` call, add the argument. It calls the new dispatcher read and parses; a null dispatcher (idle/disconnected) throws, which the poll loop treats as "unavailable":

```kotlin
        queryEndstops = {
            val d = dispatcher ?: error("no active session")
            parseEndstops(d.query(CommandRegistry.queryEndstops, Unit))
        },
```

- [ ] **Step 3: Render the Focus content + poll loop**

In the Focus `when (mode) { ... }`, add the branch. Place it with the other branches:

```kotlin
                        MoveMode.Endstops -> {
                            // null = no result yet (Querying). errored = last poll threw before any data.
                            var endstops by remember { mutableStateOf<List<EndstopStatus>?>(null) }
                            var errored by remember { mutableStateOf(false) }
                            // rememberUpdatedState so the long-lived poll loop always calls the LATEST
                            // lambda (which reads the live dispatcher) — guards against a stale capture
                            // never recovering after reconnect.
                            val query by rememberUpdatedState(queryEndstops)
                            // Lifecycle-scoped poll (mirrors TemperatureScreen.kt:245-254): the
                            // LaunchedEffect cancels when this branch leaves composition (mode change),
                            // and repeatOnLifecycle(STARTED) suspends the loop while the screen is
                            // backgrounded — both required stop conditions per the spec.
                            val lifecycleOwner = LocalLifecycleOwner.current
                            LaunchedEffect(lifecycleOwner) {
                                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                    while (true) {
                                        try {
                                            endstops = query()
                                            errored = false
                                        } catch (e: CancellationException) {
                                            throw e // never swallow structured cancellation
                                        } catch (e: Throwable) {
                                            // Disconnected / Klippy not ready / timeout: keep last good
                                            // data if we have it; otherwise surface the unavailable hint.
                                            if (endstops == null) errored = true
                                        }
                                        delay(500)
                                    }
                                }
                            }

                            val current = endstops
                            when {
                                // No .padding() here — FocusFrame already insets its content by
                                // FocusInset (FocusFrame.kt:269); adding it again would double-inset.
                                current != null -> {
                                    Column(
                                        Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        current.forEach { es ->
                                            EndstopRow(es, grid.uDp)
                                        }
                                    }
                                }
                                errored -> FocusCenteredHint("Endstops unavailable")
                                else -> FocusCenteredHint("Querying…")
                            }
                        }
```

> If a homing-hint/centered-message composable already exists in `MoveScreen.kt` (the TouchMove
> "needs homing" hint), reuse THAT instead of adding `FocusCenteredHint` in Step 4.

- [ ] **Step 4: Add the row + hint composables (reuse existing if present)**

Add near the other private composables in `MoveScreen.kt` (e.g. by `MoveRow`):

```kotlin
/** One endstop status row: state glyph + axis label + OPEN/TRIGGERED readout.
 *  Color + shape both encode state (crop_free/center_focus_strong) for high-contrast/colorblind palettes. */
@Composable
private fun EndstopRow(status: EndstopStatus, uDp: Dp) {
    val t = LocalTokens.current
    val color = if (status.triggered) t.accent else t.text3
    val glyph = if (status.triggered) DinghyIcons.CenterFocusStrong else DinghyIcons.CropFree
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = uDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // DinghyIconView sizes the glyph via sizeDp (NOT Modifier.size); decorative (cd=null) —
        // the row's text label already conveys the state to TalkBack.
        DinghyIconView(
            icon = glyph,
            tint = color,
            sizeDp = uDp * 0.6f,
            contentDescription = null,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = endstopLabel(status.name),
            style = DinghyType.listLabel.toTextStyle(t),
            color = t.text,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (status.triggered) "TRIGGERED" else "OPEN",
            style = DinghyType.statValue.toTextStyle(t),
            color = color,
        )
    }
}

@Composable
private fun FocusCenteredHint(text: String) {
    val t = LocalTokens.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = DinghyType.body.toTextStyle(t), color = t.text3)
    }
}
```

> These use the EXISTING type roles + helper already imported in `MoveScreen.kt`
> (`DinghyType.listLabel/statValue/body` via `works.mees.dinghy.theme.compose.toTextStyle`, used at
> e.g. MoveScreen.kt:244). Font conformance FORBIDS inline `fontSize=`/`fontFamily=` — the build
> fails otherwise; only role styles are allowed.

- [ ] **Step 5: Add any missing imports**

Ensure these are imported in `MoveScreen.kt` (add only the missing ones):

- `androidx.compose.runtime.getValue`, `setValue`, `mutableStateOf`, `remember`, `LaunchedEffect`, `rememberUpdatedState`
- `kotlinx.coroutines.delay`, `kotlinx.coroutines.CancellationException`
- `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.repeatOnLifecycle`, `androidx.lifecycle.compose.LocalLifecycleOwner` (exact paths used at `TemperatureScreen.kt:37-39`)
- `androidx.compose.foundation.layout.Arrangement`, `Column`, `Box`, `Spacer`, `width`, `heightIn`
- `works.mees.dinghy.command.CommandRegistry`

Do NOT import `FocusInset` for the Endstops branch (FocusFrame already insets its content; the branch adds no padding). Most layout imports are already present from the other Move sub-modes.

- [ ] **Step 6: Compile**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run the full unit suite (regression)**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL (all green, including font-conformance and catalog-drift gates).

- [ ] **Step 8: Commit**

```bash
"/mnt/c/Program Files/Git/cmd/git.exe" add app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
"/mnt/c/Program Files/Git/cmd/git.exe" commit -m "feat(move): Endstops sub-mode with live-polled state view"
```

---

## Task 7: On-device verification (owner UAT)

**Files:** none (build + install + observe).

This is a UI feature; final verification is on-device per project convention (mock-vs-reality + on-device-iteration memories). Build a fresh APK and install to BOTH test devices (flox + moto).

- [ ] **Step 1: Build the debug APK (force-rebuild to dodge the stale-APK trap)**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --rerun-tasks --no-daemon" 2>&1 | tr -d '\r'`
Expected: BUILD SUCCESSFUL. Note the APK mtime is newer than the last commit.

- [ ] **Step 2: Install the matching ABI slice to both devices**

Install the `armeabi-v7a` slice to flox (id `0a64b42e`) and the `arm64-v8a` slice to moto (id `ZY22LBDRM9`). (Use the split-ABI outputs under `app/build/outputs/apk/debug/`.)

- [ ] **Step 3: Owner walkthrough**

Hand off to Matthew to: open Move → tap **Endstops** (last row) → confirm rows for each endstop render with `crop_free` (open) glyphs; physically trigger an endstop (or jog into one) and confirm it flips to `center_focus_strong`/TRIGGERED live within ~0.5s; leave the sub-mode and confirm polling stops (no lingering load). Expect look/sizing tweaks — iterate on-device.

---

## Self-review notes

- **Spec coverage:** §1 sub-mode → Tasks 5/6; §2 data path (RPC + parse) → Tasks 1/2/3; §3 live poll 500ms/stop-on-leave → Task 6 Step 3 (`LaunchedEffect` in branch); §4 dynamic rows + glyphs + color+shape + querying/unavailable states → Task 6 Steps 3/4; §5 foot bar unchanged → no change (Back already returns to TouchMove via existing wrapper logic); §6 icon plumbing → Task 4. No print gating (per spec) — not implemented anywhere. ✓
- **Type consistency:** `parseEndstops(JsonElement): List<EndstopStatus>`, `EndstopStatus(name, triggered)`, `endstopLabel(String): String`, `CommandDispatcher.query(command, args)`, `DinghyIcons.CropFree` / `DinghyIcons.CenterFocusStrong`, `MoveMode.Endstops` — referenced identically across tasks. ✓
- **API-name guards:** the icon-render and text-role calls in Task 6 Step 4 are gated by an explicit "grep the file first" instruction because those helpers' exact names (`DinghyIconView`, `TextRole.ListLabel`, etc.) must match this file's existing usage — the font-conformance build gate forbids inline font params.
- **Drift gate:** every `CommandRegistry.all` id must exist in catalog.json AND printer-matrix.json — both rows added in Task 1, verified in Task 1 Step 6.
