# Manage Printers Rework — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize the printer-management UI — Add + Find rows at the top of the printer list, Delete moved into each printer's own editor, Find becomes a scan→pick→add+connect flow with an editor fallback, the Edit foot button is always accent-outlined and fills when armed, and the Manage Printers Focus shows static add/edit/delete instructions.

**Architecture:** Pure logic seams are host-tested first (atomic persistence, Find decision, editor field seeding), then the Compose surfaces are reworked against them. The Find takeover is a standalone full-screen composable (Focus/Field) mirroring the existing `PrinterConnectionEditor` takeover precedent, so it is independently previewable. All persistence routes through `AppContainer` intent helpers on the process write-scope.

**Tech Stack:** Kotlin, Jetpack Compose + classic Views hybrid, kotlinx.serialization, Coroutines/Flow, DataStore, JUnit4 host tests, OkHttp probe.

## Global Constraints

- minSdk 23 floor; target phones→tablets, portrait + landscape; Nexus 7 2013 (Adreno 320) is the perf floor — one shared 1U grid, no continuous animation. (verbatim from CLAUDE.md)
- All UI routes through role tokens — never raw colors (THEME-01). Foot-bar buttons are `OutlinedControl` (filled surface + intent-colored 2dp border).
- Buttons FILLED, list rows translucent. Intent (R5): red = could destroy · amber = hazardous-in-process · green = expected action · accent = neutral/nav (Back, Home).
- NEVER pick a new glyph — ask the owner. (This plan adds NO new icons — `PrinterAdd`, `Search`, `Delete`, `Edit` already exist.)
- Write-scope law: every DataStore persist routes through `AppContainer` intent helpers (process-lifetime `writeScope`), never a composition scope. Read-modify-write happens inside one `dataStore.edit`.
- Preview-first LAW: live screens delegate to a stateless `*Content` seam that the `@Preview` matrix targets — screen and previews must not drift.
- Build Windows-side: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"`, pipe through `tr -d '\r'`. Tests are JUnit4 (`org.junit`), not kotlin.test.
- Test devices: flox (Nexus 7 2013, armeabi-v7a, id `0a64b42e`) + moto (Moto G Play 2024, arm64-v8a, id `ZY22LBDRM9`) — push the MATCHING ABI slice to BOTH for UAT.

---

### Task 1: Atomic save-and-activate persistence

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/config/ProfileStore.kt` (add `upsertAndSetActive`)
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` (add `saveAndSetActiveProfile`)
- Test: `app/src/test/java/works/mees/dinghy/config/ProfileStoreTest.kt` (add round-trip test)

**Interfaces:**
- Produces: `suspend fun ProfileStore.upsertAndSetActive(profile: Profile)` — one `edit`: upsert the profile AND force the active-id to `profile.id` (unlike `upsert`, which only auto-selects when none active). `fun AppContainer.saveAndSetActiveProfile(profile: Profile)` — durable wrapper that also seeds Heat Presets / Increment Lists for a NEW profile (parity with `saveProfile`).

- [ ] **Step 1: Write the failing test** — append to `ProfileStoreTest.kt` (uses the existing `newStore()` / `profile()` helpers):

```kotlin
@Test
fun upsertAndSetActive_forcesActiveEvenWhenOneAlreadyActive() = runTest {
    val (store, _) = newStore()
    store.upsert(profile("a"))                       // D-11: "a" becomes active
    assertEquals("a", store.activeId.first())

    store.upsertAndSetActive(profile("b"))           // must FORCE active → "b"
    assertEquals(
        "upsertAndSetActive must force the new profile active even when another is already active",
        "b",
        store.activeId.first(),
    )
    assertEquals(
        "both profiles must be persisted",
        listOf("a", "b"),
        store.profiles.first().map { it.id },
    )
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.ProfileStoreTest --rerun-tasks -Pkotlin.incremental=false --no-daemon" 2>&1 | tr -d '\r' | tail -20`
Expected: FAIL — `upsertAndSetActive` unresolved reference.

- [ ] **Step 3: Add `upsertAndSetActive` to `ProfileStore.kt`** (place directly after `upsert`):

```kotlin
/**
 * Upsert [profile] AND force it active in ONE `edit` (the discovery add-and-connect path). Unlike
 * [upsert] — which only auto-selects the first profile (D-11) and never steals active afterward — this
 * ALWAYS writes [profile].id as the active id, because the user just deliberately picked this printer.
 */
suspend fun upsertAndSetActive(profile: Profile) {
    dataStore.edit { prefs ->
        val plan = planUpsert(decode(prefs[KEY_PROFILES]), prefs[KEY_ACTIVE_ID], profile)
        prefs[KEY_PROFILES] = json.encodeToString(PROFILE_LIST_SERIALIZER, plan.profiles)
        prefs[KEY_ACTIVE_ID] = profile.id
    }
}
```

- [ ] **Step 4: Add `saveAndSetActiveProfile` to `AppContainer.kt`** (place directly after `saveProfile`):

```kotlin
/**
 * Persist [profile] AND make it the active printer atomically (the Find/discovery add-and-connect
 * path), durably. A NEW profile (id not already present) is seeded with the default Heat Presets +
 * Increment Lists, exactly like [saveProfile]; an existing id reuses its data. The persist + activate
 * happen in ProfileStore's single `edit` (no transiently-dangling active id).
 */
fun saveAndSetActiveProfile(profile: Profile) {
    writeScope.launch {
        val isNew = profileStore.profiles.first().none { it.id == profile.id }
        profileStore.upsertAndSetActive(profile)
        if (isNew) {
            heatPresetPrefs.seedIfEmpty(profile.id, defaultHeatPresets())
            incrementListPrefs.seedIfEmpty(profile.id, IncrementControls.defaultStringMap())
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.ProfileStoreTest --rerun-tasks -Pkotlin.incremental=false --no-daemon" 2>&1 | tr -d '\r' | tail -20`
Expected: PASS (all ProfileStoreTest cases).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/config/ProfileStore.kt app/src/main/java/works/mees/dinghy/di/AppContainer.kt app/src/test/java/works/mees/dinghy/config/ProfileStoreTest.kt
git commit -m "feat(printers): atomic saveAndSetActiveProfile for discovery add+connect"
```

---

### Task 2: Find domain types + pure pick-decision

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterFindModel.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/screen/PrinterFindModelTest.kt`

**Interfaces:**
- Produces: `data class ConnectionSeed(val host: String, val port: Int)`; `enum class FindPickEffect { AddAndConnect, OpenEditorSeeded }`; `fun findPickDecision(probe: ProbeResult): FindPickEffect` (both transports OK → `AddAndConnect`, else `OpenEditorSeeded`).

- [ ] **Step 1: Write the failing test** — create `PrinterFindModelTest.kt`:

```kotlin
package works.mees.dinghy.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.net.ProbeFailure
import works.mees.dinghy.net.ProbeResult
import works.mees.dinghy.net.TransportResult

class PrinterFindModelTest {
    private fun probe(httpOk: Boolean, wsOk: Boolean) = ProbeResult(
        httpUrl = "http://h:7125",
        wsUrl = "ws://h:7125/websocket",
        http = TransportResult(ok = httpOk, failure = if (httpOk) null else ProbeFailure.Refused),
        ws = TransportResult(ok = wsOk, failure = if (wsOk) null else ProbeFailure.Unauthorized),
    )

    @Test
    fun bothTransportsOk_addsAndConnects() {
        assertEquals(FindPickEffect.AddAndConnect, findPickDecision(probe(httpOk = true, wsOk = true)))
    }

    @Test
    fun anyTransportFails_opensEditorSeeded() {
        assertEquals(FindPickEffect.OpenEditorSeeded, findPickDecision(probe(httpOk = true, wsOk = false)))
        assertEquals(FindPickEffect.OpenEditorSeeded, findPickDecision(probe(httpOk = false, wsOk = true)))
        assertEquals(FindPickEffect.OpenEditorSeeded, findPickDecision(probe(httpOk = false, wsOk = false)))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.screen.PrinterFindModelTest --rerun-tasks -Pkotlin.incremental=false --no-daemon" 2>&1 | tr -d '\r' | tail -20`
Expected: FAIL — `PrinterFindModel` symbols unresolved.

- [ ] **Step 3: Create `PrinterFindModel.kt`:**

```kotlin
package works.mees.dinghy.ui.screen

import works.mees.dinghy.net.ProbeResult

/** Host/port carried from a discovered printer into a fresh editor (the connect-failed path). */
data class ConnectionSeed(val host: String, val port: Int)

/** What to do after probing a picked discovered printer. */
enum class FindPickEffect { AddAndConnect, OpenEditorSeeded }

/**
 * Pure decision for a discovered-printer pick: if BOTH transports connect, add + activate it
 * silently; otherwise (auth/security/refused/timeout) fall back to the editor pre-filled.
 */
fun findPickDecision(probe: ProbeResult): FindPickEffect =
    if (probe.http.ok && probe.ws.ok) FindPickEffect.AddAndConnect else FindPickEffect.OpenEditorSeeded
```

- [ ] **Step 4: Run the test to verify it passes**

Run: same command as Step 2.
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/screen/PrinterFindModel.kt app/src/test/java/works/mees/dinghy/ui/screen/PrinterFindModelTest.kt
git commit -m "feat(printers): ConnectionSeed + pure findPickDecision"
```

---

### Task 3: Editor — seed param, remove Find, Delete row + guard

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/screen/PrinterFindModelTest.kt` (add `editorInitialFields` cases) — or a new `EditorSeedTest.kt`; either is fine, keep it in `PrinterFindModelTest.kt` for cohesion.

**Interfaces:**
- Consumes: `ConnectionSeed` (Task 2).
- Produces: `internal data class EditorFields(val name: String, val host: String, val port: String, val advancedUrl: String, val keyAlreadySaved: Boolean)`; `internal fun editorInitialFields(profile: Profile?, seed: ConnectionSeed?): EditorFields`; `PrinterConnectionEditor(..., seed: ConnectionSeed? = null, ...)` gains a defaulted `seed` param (existing call sites stay source-compatible).

- [ ] **Step 1: Write the failing test** — append to `PrinterFindModelTest.kt`:

```kotlin
@Test
fun editorInitialFields_newWithSeed_usesSeedHostPort() {
    val f = editorInitialFields(profile = null, seed = ConnectionSeed(host = "192.168.1.50", port = 7130))
    assertEquals("192.168.1.50", f.host)
    assertEquals("7130", f.port)
    assertEquals("", f.name)
    assertEquals(false, f.keyAlreadySaved)
}

@Test
fun editorInitialFields_existingProfile_ignoresSeed() {
    val p = works.mees.dinghy.config.Profile.fromPersisted(
        works.mees.dinghy.config.PersistedProfile(id = "x", host = "host.lan", port = 7125, apiKey = "k"),
    )
    val f = editorInitialFields(profile = p, seed = ConnectionSeed(host = "should.ignore", port = 9999))
    assertEquals("host.lan", f.host)
    assertEquals("7125", f.port)
    assertEquals(true, f.keyAlreadySaved)
}

@Test
fun editorInitialFields_newNoSeed_defaults() {
    val f = editorInitialFields(profile = null, seed = null)
    assertEquals("", f.host)
    assertEquals("7125", f.port)
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.screen.PrinterFindModelTest --rerun-tasks -Pkotlin.incremental=false --no-daemon" 2>&1 | tr -d '\r' | tail -20`
Expected: FAIL — `editorInitialFields` / `EditorFields` unresolved.

- [ ] **Step 3: Add the pure seam to `PrinterConnectionEditor.kt`** (top-level, near `buildProfileFromConnectionEditorSave`):

```kotlin
/** The editor's initial field values, resolved from an existing [profile] or a discovery [seed]. */
internal data class EditorFields(
    val name: String,
    val host: String,
    val port: String,
    val advancedUrl: String,
    val keyAlreadySaved: Boolean,
)

/**
 * Pure resolution of the editor's seed values. An existing [profile] wins outright; a null profile
 * (a New target) takes host/port from [seed] when present, else the blank defaults. The API key is
 * NEVER seeded (T-28-06-01) — only the "(set)" indicator via [keyAlreadySaved].
 */
internal fun editorInitialFields(profile: Profile?, seed: ConnectionSeed?): EditorFields = EditorFields(
    name = profile?.name ?: "",
    host = profile?.host ?: seed?.host ?: "",
    port = (profile?.port ?: seed?.port)?.toString() ?: "7125",
    advancedUrl = profile?.advancedUrl.orEmpty(),
    keyAlreadySaved = profile?.apiKey != null,
)
```

- [ ] **Step 4: Run the test to verify it passes**

Run: same command as Step 2.
Expected: PASS.

- [ ] **Step 5: Wire `seed` into the editor composable.** In `PrinterConnectionEditor(...)` add the param and route initial state through `editorInitialFields`:

Change the signature:
```kotlin
@Composable
internal fun PrinterConnectionEditor(
    container: AppContainer,
    profile: Profile?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    seed: ConnectionSeed? = null,
) {
```

Replace the seed `LaunchedEffect(profile?.id) { … }` block with a key that includes the seed and uses the pure helper:
```kotlin
// Seed from profile (or discovery seed for a New target) — NEVER pre-fill raw API key.
LaunchedEffect(profile?.id, seed?.host, seed?.port) {
    val init = editorInitialFields(profile, seed)
    name = init.name
    host = init.host
    port = init.port
    apiKey = ""
    advancedUrl = init.advancedUrl
    keyAlreadySaved = init.keyAlreadySaved
    keyCleared = false
    hostError = null
    portError = false
    selected = null
    probe = null
    probing = false
}
```

- [ ] **Step 6: Remove the Find row + scan machinery from the editor.** Delete:
  - `private const val SCAN_WINDOW_MS` and the `Find` case from `enum class ConnRow` → `enum class ConnRow { Name, Host, Port, ApiKey, Advanced, Delete }` (Delete added in Step 7).
  - the scan state vars: `scanRequest`, `scanning`, `scanned`, `discovered`.
  - the `LaunchedEffect(scanRequest) { … }` scan block.
  - the `ConnRow.Find` Field `item { ConnListRow(ConnRow.Find, …) }`.
  - the `ConnFocus` `discovered/scanning/scanned/onScan/onPick` params and its `ConnRow.Find -> ConnFindPanel(...)` branch.
  - the `ConnFindPanel` composable entirely.
  - the now-unused imports: `DiscoveredPrinter`, `kotlinx.coroutines.withTimeoutOrNull`, `androidx.compose.foundation.lazy.items` (verify each is unused after removal before deleting).

- [ ] **Step 7: Add the Delete action row + ConfirmGuard + BackHandler.** Add state near the other `remember`s:
```kotlin
var pendingDelete by remember { mutableStateOf(false) }
```
Add a BackHandler ABOVE the `ScreenScaffold` (so a pending guard dismisses first):
```kotlin
BackHandler(pendingDelete) { pendingDelete = false }
```
Render the guard (just before `ScreenScaffold`), only meaningful for an existing profile:
```kotlin
if (pendingDelete && profile != null) {
    ConfirmGuard(
        title = stringResource(R.string.printers_delete_confirm_title),
        message = stringResource(R.string.printers_delete_confirm_body),
        confirmLabel = stringResource(R.string.printers_delete),
        cancelLabel = stringResource(R.string.common_back),
        onConfirm = { container.deleteProfile(profile.id); pendingDelete = false; onDone() },
        onCancel = { pendingDelete = false },
        destructive = true,
    )
}
```
Add the Delete row as the LAST `item` in the Field `ListBlock`, gated on an existing profile (NOT a `ConnListRow` — it is a direct action, `t.stop`-tinted, no Focus editor):
```kotlin
if (profile != null) {
    item {
        val t = LocalTokens.current
        ListRow(
            selected = false,
            onClick = { pendingDelete = true },
            uDp = uDp,
            leadingContent = { ListRowIcon(DinghyIcons.Delete, uDp, t.stop) },
        ) {
            Text(
                text = stringResource(R.string.printers_delete),
                color = t.stop,
                style = DinghyType.listLabel.toTextStyle(t),
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
```
Add imports: `androidx.activity.compose.BackHandler`, `works.mees.dinghy.designsystem.ConfirmGuard`.

- [ ] **Step 8: Build to verify the editor compiles**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin -Pkotlin.incremental=false --no-daemon" 2>&1 | tr -d '\r' | tail -15`
Expected: `BUILD SUCCESSFUL`. (Existing editor callers pass no `seed` — defaulted — so they still compile.)

- [ ] **Step 9: Run the editor unit tests**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.screen.PrinterFindModelTest --rerun-tasks -Pkotlin.incremental=false --no-daemon" 2>&1 | tr -d '\r' | tail -15`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt app/src/test/java/works/mees/dinghy/ui/screen/PrinterFindModelTest.kt
git commit -m "feat(printers): editor seed seam, remove Find, add Delete row + guard"
```

---

### Task 4: Find takeover screen (standalone, previewable)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterFindScreen.kt`
- Create: `app/src/main/java/works/mees/dinghy/preview/PrinterFindPreviews.kt`

**Interfaces:**
- Consumes: `AppContainer.discovery`, `AppContainer.runConnectionProbe`, `ConnectionSeed`, `findPickDecision`, `buildProfileFromConnectionEditorSave` (Task 2/existing).
- Produces:
  - `@Composable fun PrinterFindScreen(container: AppContainer, onAddAndConnect: (Profile) -> Unit, onNeedsEditor: (ConnectionSeed) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)` — owns scan + probe lifecycle, reports outcomes.
  - `@Composable fun PrinterFindContent(scanning: Boolean, scanned: Boolean, discovered: List<DiscoveredPrinter>, probingHost: String?, onScan: () -> Unit, onPick: (DiscoveredPrinter) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier)` — stateless seam the previews target.

- [ ] **Step 1: Create `PrinterFindScreen.kt`.** The stateful screen owns discovery, the bounded scan, and the probe (with cancellation/staleness ownership per Codex), and computes the pick outcome:

```kotlin
package works.mees.dinghy.ui.screen

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import works.mees.dinghy.R
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.config.DiscoveredPrinter
import works.mees.dinghy.config.Profile
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

/** Bounded settle window for the mDNS scan (lifted from the old editor Find panel). */
private const val FIND_SCAN_WINDOW_MS = 6000L

/**
 * The Find-on-network takeover. Scans for Moonraker instances, lets the user pick one, probes it
 * (no API key), and reports: a clean connect → [onAddAndConnect] (caller persists + activates), or a
 * failed connect → [onNeedsEditor] (caller opens the editor pre-filled). Owns probe cancellation so a
 * stale probe can't fire after Back.
 */
@Composable
fun PrinterFindScreen(
    container: AppContainer,
    onAddAndConnect: (Profile) -> Unit,
    onNeedsEditor: (ConnectionSeed) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scanRequest by remember { mutableStateOf(1) } // auto-scan on open
    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }
    var probingHost by remember { mutableStateOf<String?>(null) }
    var probeJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(scanRequest) {
        scanning = true; scanned = false; discovered = emptyList()
        try {
            withTimeoutOrNull(FIND_SCAN_WINDOW_MS) {
                container.discovery.discover().collect { p ->
                    if (discovered.none { it.host == p.host && it.port == p.port }) discovered = discovered + p
                }
            }
        } finally { scanning = false; scanned = true }
    }
    DisposableEffect(Unit) { onDispose { probeJob?.cancel() } }

    PrinterFindContent(
        scanning = scanning,
        scanned = scanned,
        discovered = discovered,
        probingHost = probingHost,
        onScan = { if (!scanning) scanRequest++ },
        onPick = { printer ->
            probeJob?.cancel()
            probingHost = printer.host
            val config = ConnectionConfig(
                host = printer.host, port = printer.port, apiKey = null,
                useSecure = false, advancedUrl = null,
            )
            probeJob = container.runConnectionProbe(config) { result ->
                probingHost = null
                probeJob = null
                when (findPickDecision(result)) {
                    FindPickEffect.AddAndConnect -> onAddAndConnect(
                        buildProfileFromConnectionEditorSave(
                            existing = null, nameInput = printer.name,
                            host = printer.host, port = printer.port,
                            apiKeyInput = "", keyCleared = false, advancedUrlInput = "",
                        ),
                    )
                    FindPickEffect.OpenEditorSeeded ->
                        onNeedsEditor(ConnectionSeed(printer.host, printer.port))
                }
            }
        },
        onBack = onBack,
        modifier = modifier,
    )
}

/** Stateless Find layout — the `@Preview` matrix targets this. */
@Composable
fun PrinterFindContent(
    scanning: Boolean,
    scanned: Boolean,
    discovered: List<DiscoveredPrinter>,
    probingHost: String?,
    onScan: () -> Unit,
    onPick: (DiscoveredPrinter) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val uDp = grid.uDp
        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = stringResource(R.string.conn_row_find),
                    icon = DinghyIcons.Search,
                    uDp = uDp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                ) {
                    Column(Modifier.fillMaxSize()) {
                        val status = when {
                            probingHost != null -> stringResource(R.string.conn_test) // "Testing…" reuse
                            scanning -> stringResource(R.string.printers_scanning)
                            scanned && discovered.isEmpty() -> stringResource(R.string.printers_scan_none_found)
                            scanned -> stringResource(R.string.printers_pick_found) // new string (Task 5 adds it)
                            else -> stringResource(R.string.conn_scan_empty)
                        }
                        Text(text = status, color = t.text2, style = DinghyType.body.toTextStyle(t))
                    }
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    items(discovered, key = { "${it.host}:${it.port}" }) { p ->
                        ListRow(
                            selected = false,
                            onClick = { onPick(p) },
                            uDp = uDp,
                            leadingContent = { ListRowIcon(DinghyIcons.SysInfoCpu, uDp, t.text) },
                            trailingContent = {
                                Text(p.port.toString(), color = t.text2, style = DinghyType.dataMeta.toTextStyle(t))
                            },
                        ) {
                            Text(
                                text = p.name.ifBlank { p.host },
                                color = t.text,
                                style = DinghyType.listLabel.toTextStyle(t),
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                FootButtonBar(
                    uDp = uDp,
                    actions = listOf(
                        FootAction(stringResource(R.string.common_back), DinghyIcons.Back, onBack, Intent.Accent),
                        FootAction(
                            label = if (scanning) stringResource(R.string.printers_scanning)
                                    else stringResource(R.string.conn_scan),
                            icon = DinghyIcons.Search,
                            onClick = onScan,
                            intent = Intent.Accent,
                            enabled = !scanning,
                        ),
                    ),
                )
            },
        )
    }
}

@Suppress("unused")
private fun unusedDpRef(): Dp = 0.dp // keep Dp import honest if refactored; remove if Dp used above
```

> Note: remove the `unusedDpRef`/`Dp`/`Spacer`/`height` scaffolding imports if the final body doesn't reference them — the compile step will flag unused imports as warnings, not errors, but keep the file clean.

- [ ] **Step 2: Add the two new strings** to `app/src/main/res/values/strings.xml` (near the other `printers_*` entries):

```xml
<string name="printers_pick_found">Tap a printer to add it.</string>
```
(The empty/none-found case reuses the existing `printers_scan_none_found`; "Scanning…" reuses `printers_scanning`; the pre-scan hint reuses `conn_scan_empty`.)

- [ ] **Step 3: Create `PrinterFindPreviews.kt`** with the four scan states (scanning, found, none-found, probing). Mirror the existing `PrintersPreviews.kt` preview wrapper (same `@Preview` annotations + theme wrapper used there):

```kotlin
package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.dinghy.config.DiscoveredPrinter
import works.mees.dinghy.ui.screen.PrinterFindContent

private val SAMPLE = listOf(
    DiscoveredPrinter(name = "ender5plus", host = "192.168.1.120", port = 7125),
    DiscoveredPrinter(name = "voron", host = "192.168.1.121", port = 7125),
)

@Preview(name = "Find — found", widthDp = 1920, heightDp = 1200)
@Composable
private fun FindFound() = PreviewSurface { // PreviewSurface = the existing theme wrapper in this package
    PrinterFindContent(scanning = false, scanned = true, discovered = SAMPLE, probingHost = null,
        onScan = {}, onPick = {}, onBack = {})
}

@Preview(name = "Find — scanning", widthDp = 1920, heightDp = 1200)
@Composable
private fun FindScanning() = PreviewSurface {
    PrinterFindContent(scanning = true, scanned = false, discovered = emptyList(), probingHost = null,
        onScan = {}, onPick = {}, onBack = {})
}

@Preview(name = "Find — none", widthDp = 1920, heightDp = 1200)
@Composable
private fun FindNone() = PreviewSurface {
    PrinterFindContent(scanning = false, scanned = true, discovered = emptyList(), probingHost = null,
        onScan = {}, onPick = {}, onBack = {})
}
```

> Before writing this file, open `PrintersPreviews.kt` and copy its exact theme-wrapper helper name and `@Preview` parameters (the placeholder `PreviewSurface` above must be replaced with the real wrapper used in `app/src/main/java/works/mees/dinghy/preview/`). Do not invent a wrapper.

- [ ] **Step 4: Build to verify it compiles**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin -Pkotlin.incremental=false --no-daemon" 2>&1 | tr -d '\r' | tail -15`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/screen/PrinterFindScreen.kt app/src/main/java/works/mees/dinghy/preview/PrinterFindPreviews.kt app/src/main/res/values/strings.xml
git commit -m "feat(printers): Find-on-network takeover screen (scan, pick, probe)"
```

---

### Task 5: Printers screen — mode collapse, Add/Find rows, instructions Focus, wiring

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt`
- Modify: `app/src/main/java/works/mees/dinghy/preview/PrintersPreviews.kt`
- Modify: `app/src/test/java/works/mees/dinghy/ui/screen/PrintersModeToggleTest.kt`
- Modify: `app/src/main/res/values/strings.xml` (instructions strings)

**Interfaces:**
- Consumes: `PrinterFindScreen` (Task 4), `ConnectionSeed` (Task 2), `AppContainer.saveAndSetActiveProfile` (Task 1), the editor `seed` param (Task 3).
- Produces: `enum class PrinterMode { Normal, EditArmed }`; `fun armEdit`, `fun disarm`, `fun rowTapEffect` (2-effect: `SwitchActive`/`OpenEditor`); `EditorTarget.New(val seed: ConnectionSeed? = null)`.

- [ ] **Step 1: Rewrite `PrintersModeToggleTest.kt` for the 2-mode machine.** Remove every `DeleteArmed` / `armDelete` / `RequestDelete` case; keep the Edit arming/disarming, Back disarm, and the Normal/EditArmed row-tap cases, plus the unchanged `resolveEditorKeyOnSave` / `connectionEditorSave` cases. The mode section becomes:

```kotlin
@Test fun armEdit_fromNormal_givesEditArmed() =
    assertEquals(PrinterMode.EditArmed, armEdit(PrinterMode.Normal))

@Test fun armEdit_fromEditArmed_disarms() =
    assertEquals(PrinterMode.Normal, armEdit(PrinterMode.EditArmed))

@Test fun disarm_alwaysGivesNormal() =
    assertEquals(PrinterMode.Normal, disarm())

@Test fun rowTap_inNormal_givesSwitchActive() =
    assertEquals(RowTapEffect.SwitchActive, rowTapEffect(PrinterMode.Normal))

@Test fun rowTap_inEditArmed_givesOpenEditor() =
    assertEquals(RowTapEffect.OpenEditor, rowTapEffect(PrinterMode.EditArmed))
```
(Keep the imports/`@Test` annotations; delete the three import lines only if they go unused.)

- [ ] **Step 2: Run it to verify it fails to compile**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.ui.screen.PrintersModeToggleTest --rerun-tasks -Pkotlin.incremental=false --no-daemon" 2>&1 | tr -d '\r' | tail -20`
Expected: FAIL — `DeleteArmed`/`armDelete`/`RequestDelete` still referenced by `PrintersScreen.kt` (compiled together), or the test no longer matches. This drives Step 3.

- [ ] **Step 3: Collapse the mode machine in `PrintersScreen.kt`.** Replace the enum + transition block:

```kotlin
enum class PrinterMode { Normal, EditArmed }

enum class RowTapEffect { SwitchActive, OpenEditor }

/** Arm Edit (or disarm if already armed). */
fun armEdit(current: PrinterMode): PrinterMode =
    if (current == PrinterMode.EditArmed) PrinterMode.Normal else PrinterMode.EditArmed

/** Disarm to Normal (Back key handler). */
fun disarm(): PrinterMode = PrinterMode.Normal

/** Map a row tap under [mode] to its outcome. */
fun rowTapEffect(mode: PrinterMode): RowTapEffect = when (mode) {
    PrinterMode.Normal    -> RowTapEffect.SwitchActive
    PrinterMode.EditArmed -> RowTapEffect.OpenEditor
}
```
Delete `armDelete`, the `DeleteArmed` enum case, and `RowTapEffect.RequestDelete`.

- [ ] **Step 4: Update `EditorTarget` to carry a seed:**

```kotlin
private sealed interface EditorTarget {
    data class Edit(val profile: Profile) : EditorTarget
    data class New(val seed: ConnectionSeed? = null) : EditorTarget
}
```
(`New` becomes a `data class`; update the `EditorTarget.New` reference at the Add handler to `EditorTarget.New()`.)

- [ ] **Step 5: Rework the stateful `PrintersScreen` body.** Remove `pendingDelete` state + its `ConfirmGuard` block + the `pendingDelete` branch in the `BackHandler`. Add Find state and wire outcomes:

```kotlin
var printerMode by remember { mutableStateOf(PrinterMode.Normal) }
var editingTarget by remember { mutableStateOf<EditorTarget?>(null) }
var findActive by remember { mutableStateOf(false) }

BackHandler(printerMode != PrinterMode.Normal || editingTarget != null || findActive) {
    when {
        editingTarget != null -> editingTarget = null
        findActive -> findActive = false
        else -> printerMode = disarm()
    }
}

// Find takeover (Task 4 screen).
if (findActive) {
    PrinterFindScreen(
        container = container,
        onAddAndConnect = { profile ->
            container.saveAndSetActiveProfile(profile)
            findActive = false
            onSwitched()
        },
        onNeedsEditor = { seed ->
            findActive = false
            editingTarget = EditorTarget.New(seed)
        },
        onBack = { findActive = false },
        modifier = modifier,
    )
    return
}

// Inline connection editor (Edit-mode row tap, Add, or Find→editor fallback).
val target = editingTarget
if (target != null) {
    PrinterConnectionEditor(
        container = container,
        profile = if (target is EditorTarget.Edit) target.profile else null,
        seed = (target as? EditorTarget.New)?.seed,
        onDone = { editingTarget = null },
        modifier = modifier,
    )
    return
}
```
Then the `PrintersContent(...)` call drops `onArmDelete` and adds `onFind`:
```kotlin
PrintersContent(
    profiles = profiles,
    activeId = activeId,
    printerMode = printerMode,
    isPrinting = isPrinting,
    onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
    onRowClick = { profile ->
        when (rowTapEffect(printerMode)) {
            RowTapEffect.SwitchActive -> { container.setActiveProfile(profile.id); onSwitched() }
            RowTapEffect.OpenEditor -> { editingTarget = EditorTarget.Edit(profile) }
        }
    },
    onAdd = { editingTarget = EditorTarget.New() },
    onFind = { findActive = true },
    onArmEdit = { printerMode = armEdit(printerMode) },
    onBack = { if (printerMode != PrinterMode.Normal) printerMode = disarm() else onBack() },
    modifier = modifier,
)
```
(Remove the now-unused `connectionState` collection if nothing else uses it — the instructions Focus drops the ring. Keep it only if still referenced.)

- [ ] **Step 6: Rework `PrintersContent`** — signature (drop `connectionState`, `onArmDelete`; add `onFind`), Field rows, instructions Focus, 2-button foot bar:

Signature:
```kotlin
@Composable
fun PrintersContent(
    profiles: List<works.mees.dinghy.config.Profile>,
    activeId: String?,
    printerMode: PrinterMode,
    isPrinting: Boolean = false,
    onEmergencyStop: () -> Unit = {},
    onRowClick: (works.mees.dinghy.config.Profile) -> Unit,
    onAdd: () -> Unit,
    onFind: () -> Unit,
    onArmEdit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = stringResource(R.string.system_row_printers),
                    icon = DinghyIcons.SystemRowPrinters,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Text(stringResource(R.string.printers_help_add),    color = t.text2, style = DinghyType.body.toTextStyle(t))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.printers_help_switch), color = t.text2, style = DinghyType.body.toTextStyle(t))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.printers_help_edit),   color = t.text2, style = DinghyType.body.toTextStyle(t))
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.printers_help_delete), color = t.text2, style = DinghyType.body.toTextStyle(t))
                    }
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    item {
                        ListRow(selected = false, onClick = onAdd, uDp = grid.uDp,
                            leadingContent = { ListRowIcon(DinghyIcons.PrinterAdd, grid.uDp, t.accent2) }) {
                            Text(stringResource(R.string.printers_add), color = t.text,
                                style = DinghyType.listLabel.toTextStyle(t), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    item {
                        ListRow(selected = false, onClick = onFind, uDp = grid.uDp,
                            leadingContent = { ListRowIcon(DinghyIcons.Search, grid.uDp, t.accent2) }) {
                            Text(stringResource(R.string.conn_row_find), color = t.text,
                                style = DinghyType.listLabel.toTextStyle(t), modifier = Modifier.fillMaxWidth())
                        }
                    }
                    items(profiles, key = { it.id }) { profile ->
                        ListRow(selected = profile.id == activeId, onClick = { onRowClick(profile) }, uDp = grid.uDp) {
                            Text(profile.displayName(), color = t.text, style = DinghyType.listLabel.toTextStyle(t),
                                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${profile.host}:${profile.port}", color = t.text2,
                                style = DinghyType.dataMeta.toTextStyle(t), maxLines = 1)
                        }
                    }
                }
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(stringResource(R.string.common_back), DinghyIcons.Back, onBack, Intent.Accent),
                        FootAction(
                            label = stringResource(R.string.printers_edit),
                            icon = DinghyIcons.Edit,
                            onClick = onArmEdit,
                            intent = Intent.Accent, // always outlined in color
                            fill = if (printerMode == PrinterMode.EditArmed) t.accentSoft else null, // filled when armed
                        ),
                    ),
                )
            },
        )
    }
}
```
Add the needed imports if missing: `androidx.compose.foundation.layout.Spacer`, `androidx.compose.foundation.layout.height`, `androidx.compose.foundation.lazy.items` (already present), `ListRowIcon`. Remove now-unused: `ConnectionState`, `FocusEdge`, the `ringColor` logic, the empty-state branch (instructions cover empty), `printers_empty_*` usage, and `Alignment`/`TextAlign` if unused.

- [ ] **Step 7: Add instruction strings** to `strings.xml`:

```xml
<string name="printers_help_add">Add a printer with the button at the top of the list.</string>
<string name="printers_help_switch">Tap a printer to make it active.</string>
<string name="printers_help_edit">To change a printer, tap Edit, then tap the printer.</string>
<string name="printers_help_delete">Delete a printer from inside its own settings.</string>
```

- [ ] **Step 8: Update `PrintersPreviews.kt`** — remove the `DeleteArmedMode` preview and every `onArmDelete = {}` arg; remove the `connectionState`/`onArmDelete` args from all `PrintersContent` calls; add `onFind = {}`. Keep Normal + EditArmed + Empty previews. (Open the file and update each `PrintersContent(...)` call to the new signature.)

- [ ] **Step 9: Build + run the full unit suite**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --rerun-tasks -Pkotlin.incremental=false --no-daemon" 2>&1 | tr -d '\r' | tail -25`
Expected: `BUILD SUCCESSFUL`; `PrintersModeToggleTest`, `PrinterFindModelTest`, `ProfileStoreTest` all green.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt app/src/main/java/works/mees/dinghy/preview/PrintersPreviews.kt app/src/test/java/works/mees/dinghy/ui/screen/PrintersModeToggleTest.kt app/src/main/res/values/strings.xml
git commit -m "feat(printers): Add/Find rows, instructions Focus, 2-button foot bar, mode collapse"
```

---

### Task 6: Full build, install, on-device UAT

**Files:** none (verification).

- [ ] **Step 1: Force-rebuild the split-ABI debug APK**

Run: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug -Pkotlin.incremental=false --no-daemon --rerun-tasks" 2>&1 | tr -d '\r' | tail -8`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Confirm devices + fresh APK mtime**

Run:
```bash
ls -la --time-style=+%H:%M:%S /mnt/e/claude/personal/github/dinghy-display/app/build/outputs/apk/debug/*.apk
/mnt/e/Android/Sdk/platform-tools/adb.exe devices
```
Expected: two APKs newer than the Task 5 commit; both `0a64b42e` and `ZY22LBDRM9` listed as `device`.

- [ ] **Step 3: Install the MATCHING ABI slice on each device**

Run:
```bash
/mnt/e/Android/Sdk/platform-tools/adb.exe -s 0a64b42e install -r /mnt/e/claude/personal/github/dinghy-display/app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk
/mnt/e/Android/Sdk/platform-tools/adb.exe -s ZY22LBDRM9 install -r /mnt/e/claude/personal/github/dinghy-display/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```
Expected: `Success` on both.

- [ ] **Step 4: Owner UAT checklist** (Manage Printers screen on both devices):
  - Add row at top → opens a blank editor; saving adds a printer.
  - Find row under Add → scan; nothing found shows "No printers found…"; a found printer that connects is added + activated; one that can't (e.g. key required) opens the editor pre-filled with its host/port.
  - Foot bar shows only Back + Edit; Edit is accent-outlined always and fills when armed; tapping a printer while armed opens its editor.
  - Inside a printer's editor: Delete sits at the very bottom after Advanced (only when editing an existing printer), raises the confirm guard, removes the printer, returns to the list; deleting the active printer reassigns active to another.
  - Focus shows the add/edit/delete instructions in all states (including zero printers).

- [ ] **Step 5: (after owner approval) finalize** — no code change; the feature branch/commits are ready for the owner's push decision.

---

## Self-Review

**Spec coverage:**
- Add at top of list → Task 5 Step 6 (Add row first). ✓
- Delete moved to editor after Advanced (existing-only) → Task 3 Steps 6–7. ✓
- Find moved to Printers under Add, scan→pick→add/connect, editor-on-fail → Tasks 2/4/5. ✓
- Empty scan shows "nothing found" → Task 4 (`printers_scan_none_found`). ✓
- Edit always outlined, filled when selected → Task 5 Step 6 (`intent=Accent`, `fill=accentSoft` when armed). ✓
- Focus = instructions → Task 5 Step 6 + Step 7 strings. ✓
- Manual Add separate from Find → Task 5 (`EditorTarget.New()` vs `New(seed)`). ✓
- Codex: editor BackHandler for guard → Task 3 Step 7. ✓ · probe lifecycle → Task 4 Step 1. ✓ · atomic save+activate → Task 1. ✓ · seed seam keyed in LaunchedEffect → Task 3 Step 5. ✓ · ListRow Delete `t.stop` tint → Task 3 Step 7. ✓ · preview seam (Find via stateless content) → Task 4. ✓ · editor previews note → Find previews added; editor Delete-row visibility verified on-device (Task 6) since editor has no stateless seam — acceptable, called out. · deleteProfile active reassignment → relied on, documented in spec. ✓

**Placeholder scan:** the only deliberate placeholders are the `PreviewSurface` wrapper name (Task 4 Step 3 — engineer must copy the real wrapper from `PrintersPreviews.kt`) and the `unusedDpRef` cleanup note — both explicitly flagged, not silent. No "TBD/handle errors/etc."

**Type consistency:** `ConnectionSeed(host, port)`, `FindPickEffect`, `findPickDecision(ProbeResult)`, `editorInitialFields(Profile?, ConnectionSeed?): EditorFields`, `upsertAndSetActive(Profile)`, `saveAndSetActiveProfile(Profile)`, `PrinterMode { Normal, EditArmed }`, `RowTapEffect { SwitchActive, OpenEditor }`, `EditorTarget.New(seed)`, `PrinterFindScreen(container, onAddAndConnect, onNeedsEditor, onBack)` — names used consistently across tasks.
