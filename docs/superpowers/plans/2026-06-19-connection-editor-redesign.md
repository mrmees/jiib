# Connection Editor Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rewrite the printer connection editor into the Focus/Field tap-row-to-edit grammar, fix the
host-normalization "hangs at the logo" bug, derive the ws/wss scheme from the address (delete the
secure toggle), and add a Test-Connection probe that actually exercises auth.

**Architecture:** Extract all connection-URL logic into pure, unit-tested functions
(`ConnectionUrls`), migrate legacy `useSecure=true` profiles into an explicit `advancedUrl` so TLS
is not lost when the secure toggle is removed, add a `ConnectionProbe` that runs an HTTP +
websocket-identify probe concurrently and classifies failures, then rebuild
`PrinterConnectionEditor` as a `ScreenScaffold` Focus/Field screen modeled on
`IncrementValuesScreen` (Field = selectable setting rows; tapping a row swaps a `TokenTextField`
editor into the Focus; Save is always enabled in the foot-bar and relabels to "Save anyway" after a
failed probe).

**Tech Stack:** Kotlin, Jetpack Compose, OkHttp (websocket + REST), kotlinx.serialization, DataStore,
Coroutines/Flow. Unit tests: JUnit + `kotlinx-coroutines-test` (`runTest` + `UnconfinedTestDispatcher`).

## Global Constraints

- **minSdk 23**; no dependency or API above the floor. Compose BOM 2026.05, AGP 8.7.x, compileSdk 36.
- **No raw colors / fonts.** All color via `LocalTokens.current`; all type via `DinghyType` role
  styles (`role.toTextStyle(t)`); inline `fontFamily=`/`fontSize=` are build-failing
  (`FontConformanceTest`). All sizing via `LocalUnitDp` / `uDp` (1U control cap).
- **Icons:** owner-curated registry only. **NEVER pick a glyph** — Task 5 is an owner gate; do not
  start any row/button that needs a new glyph until the owner has assigned it. New `DinghyIcons`
  entries need the `val` AND the `all` list, verified by `python tools/verify_ligatures.py`.
- **Keyboard:** the "no alphanumeric keyboard" rule is **relaxed** — use `TokenTextField` where text
  entry is the honest input (Name/Host/API key/Advanced). Numeric keyboard for Port.
- **DataStore writes go through `AppContainer.writeScope`** (process-lifetime), never a composition
  scope (`dinghy-compose-write-scope-cancellation`).
- **Unit tests use JUnit 4 only.** Existing app tests import `org.junit.Test` and
  `org.junit.Assert.*`; `app/build.gradle.kts:256-258` declares JUnit + coroutines-test + serialization,
  not `kotlin-test`. Do not use `kotlin.test.*` in snippets.
- **Build/test** Windows-side: `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"`;
  pipe through `tr -d '\r'`; exit code is authoritative. Unit tests:
  `:app:testDebugUnitTest`. Guard hangs with `timeout` + `taskkill /F /T`.
- **On-device UAT** on BOTH flox (Nexus 7, armeabi-v7a) and moto (arm64-v8a) per
  `dinghy-test-devices`; force-rebuild before install (`dinghy-stale-apk-uat-gate`).

---

## File Structure

| File | Responsibility | Action |
|------|----------------|--------|
| `app/src/main/java/works/mees/dinghy/config/ConnectionUrls.kt` | Pure host/URL normalization + ws/http URL building (replaces the `ConnectionConfig` getters). | **Create** |
| `app/src/main/java/works/mees/dinghy/config/ConnectionConfig.kt` | Delegate `httpBase`/`wsUrl` to `ConnectionUrls`; carry `advancedUrl`. | Modify |
| `app/src/main/java/works/mees/dinghy/config/Profile.kt` | Add `advancedUrl: String?`; migrate persisted `useSecure=true` into `advancedUrl`; `toConnectionConfig()` passes `advancedUrl` and stops writing `useSecure=true`. | Modify |
| `app/src/main/java/works/mees/dinghy/config/ProfileStore.kt` | Keep the migration on the read/sanitize path by routing decoded blobs through `Profile.fromPersisted`. | Modify (comment/test only if needed) |
| `app/src/main/java/works/mees/dinghy/net/ConnectionProbe.kt` | Dual HTTP+WS probe orchestration + result types. | **Create** |
| `app/src/main/java/works/mees/dinghy/net/ProbeClassifier.kt` | Pure: classify a probe failure (timeout/refused/unauthorized/cert/unknown). | **Create** |
| `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` | Expose one probe API: `runConnectionProbe(config, onResult): Job` on a dedicated process-scope `probeScope`, callback on Main. | Modify |
| `app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt` | The Focus/Field rewrite. | **Rewrite** |
| `app/src/main/res/values/strings.xml` | New string resources (row labels, hints, probe statuses, `.local` warning). | Modify |
| `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` | Any owner-assigned new glyphs. | Modify (after Task 5) |
| `docs/ui_design/THEMING.md`, root `CLAUDE.md` | Relax the keyboard law. | Modify |
| Tests under `app/src/test/java/works/mees/dinghy/...` | Unit tests for each pure unit. | Create |

**Decomposition note:** `PrinterConnectionEditor.kt` will grow; keep the per-field editor and the
probe-results panel as `private @Composable` helpers in that file (mirrors `IncrementValuesScreen`'s
`IncrementEditor`). The editor's public signature is unchanged
(`PrinterConnectionEditor(container, profile, onDone, modifier)`), so the two call sites
(`PrinterSettingsScreen`, `PrintersScreen`) need no changes.

---

## Task 1: Add `advancedUrl` to the Profile model

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/config/Profile.kt`
- Test: `app/src/test/java/works/mees/dinghy/config/ProfileAdvancedUrlTest.kt`

**Interfaces:**
- Produces: `Profile.advancedUrl: String?` (default `null`), `PersistedProfile.advancedUrl: String?`
  (default `null`), round-trip through `fromPersisted`/`toPersisted`, and
  `ConnectionConfig.advancedUrl: String?`.
- Real seams verified in the current codebase:
  - `Profile.toConnectionConfig()` lives at
    `app/src/main/java/works/mees/dinghy/config/Profile.kt:98`.
  - `Profile.displayName()` exists at
    `app/src/main/java/works/mees/dinghy/config/Profile.kt:108`.
  - `Profile.fromPersisted(...)` lives at
    `app/src/main/java/works/mees/dinghy/config/Profile.kt:132`.

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileAdvancedUrlTest {
    @Test fun defaultsToNull() {
        val p = Profile(id = "a", host = "192.168.1.10")
        assertNull(p.advancedUrl)
    }

    @Test fun roundTripsThroughPersisted() {
        val p = Profile(id = "a", host = "h", advancedUrl = "https://h/printer")
        val back = Profile.fromPersisted(p.toPersisted())
        assertEquals("https://h/printer", back.advancedUrl)
    }

    @Test fun toConnectionConfigCarriesAdvancedUrl() {
        val p = Profile(id = "a", host = "h", advancedUrl = "https://proxy/printer")
        assertEquals("https://proxy/printer", p.toConnectionConfig().advancedUrl)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.ProfileAdvancedUrlTest"`
Expected: FAIL (compile error — `advancedUrl` unknown).

- [ ] **Step 3: Add the field to both data classes + mappings**

In `PersistedProfile` (after `accentOverrideArgb`):
```kotlin
    val advancedUrl: String? = null,
```
In `Profile` (after `accentOverrideArgb`):
```kotlin
    val advancedUrl: String? = null,
```
In `fromPersisted(...)` add: `advancedUrl = p.advancedUrl,`
In `toPersisted(...)` add: `advancedUrl = advancedUrl,`
In `ConnectionConfig.kt`, add the field after `useSecure`:
```kotlin
    val advancedUrl: String? = null,
```
In `Profile.toConnectionConfig()`, pass it:
```kotlin
fun toConnectionConfig(): ConnectionConfig =
    ConnectionConfig(host = host, port = port, apiKey = apiKey, useSecure = useSecure, advancedUrl = advancedUrl)
```

- [ ] **Step 4: Run test to verify it passes**

Run: same as Step 2. Expected: PASS. (`ignoreUnknownKeys = true` already makes old stored blobs
without this key decode to `null` — no destructive migration needed.)

- [ ] **Step 5: Commit**

```bash
# Include ConnectionConfig.kt — Step 3 added advancedUrl there too; omitting it leaves HEAD
# uncompilable (Profile.toConnectionConfig references the new param).
git add app/src/main/java/works/mees/dinghy/config/Profile.kt app/src/main/java/works/mees/dinghy/config/ConnectionConfig.kt app/src/test/java/works/mees/dinghy/config/ProfileAdvancedUrlTest.kt
git commit -m "feat(connection): add Profile.advancedUrl (proxy/full-URL override)"
```

> **Build-green check:** after this commit, run `git stash -u` then
> `cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon"` to confirm HEAD compiles with
> nothing uncommitted, then `git stash pop` if anything was stashed. (Guards the broken-HEAD trap.)

---

## Task 2: `ConnectionUrls` — host normalization + URL building (pure)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/config/ConnectionUrls.kt`
- Modify: `app/src/main/java/works/mees/dinghy/config/ConnectionConfig.kt`
- Test: `app/src/test/java/works/mees/dinghy/config/ConnectionUrlsTest.kt`

**Interfaces:**
- Produces:
  - `data class ConnectionUrls(val httpBase: String, val wsUrl: String)`
  - `fun normalizeHost(raw: String): HostResult` where
    `sealed interface HostResult { data class Clean(val host: String, val portOverride: Int? = null): HostResult; data class Rejected(val reason: String): HostResult }`
  - `fun buildConnectionUrls(host: String, port: Int, advancedUrl: String?, useSecure: Boolean): ConnectionUrls`
- Consumes: nothing (pure).

**Behavior contract (from spec + Codex):**
- `normalizeHost`: trim, reject blank, reject any scheme/full URL/path/query/fragment/user-info pasted
  into the Host field with a clear reason, parse `host:port` into `HostResult.Clean(host,
  portOverride)`, parse `[IPv6]` and `[IPv6]:port`, reject unclosed brackets, reject bare IPv6 with
  "Wrap IPv6 addresses in [brackets]", reject invalid/out-of-range ports.
- `buildConnectionUrls` precedence: **advancedUrl present** → parse with OkHttp `HttpUrl`
  (`okhttp3.HttpUrl.Companion.toHttpUrlOrNull`) after mapping `ws/wss` to `http/https`, then map back.
  Accept only `http`, `https`, `ws`, `wss` schemes (case-insensitive); reject query, fragment,
  scheme-only, and invalid bases. Collapse default ports via `HttpUrl.toString()`. `httpBase` is the
  parsed base without a trailing slash and without a terminal `/websocket`; `wsUrl` is the same route
  prefix mapped to `ws/wss` with exactly one `/websocket`. **Else** → plain: scheme =
  `useSecure ? https/wss : http/ws`, preserving the legacy fallback for already-constructed
  `ConnectionConfig(useSecure=true)` values.

- [ ] **Step 1: Write the failing tests**

```kotlin
package works.mees.dinghy.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionUrlsTest {
    private fun clean(s: String) = normalizeHost(s) as HostResult.Clean

    @Test fun trims() = assertEquals("h", clean("  h  "))
    @Test fun hostPortPopulatesPortOverride() {
        val r = clean("192.168.1.50:7130")
        assertEquals("192.168.1.50", r.host)
        assertEquals(7130, r.portOverride)
    }

    @Test fun bracketedIpv6WithPort() {
        val r = clean("[::1]:7125")
        assertEquals("[::1]", r.host)
        assertEquals(7125, r.portOverride)
    }

    @Test fun bracketedIpv6WithoutPort() {
        val r = clean("[fe80::1]")
        assertEquals("[fe80::1]", r.host)
        assertEquals(null, r.portOverride)
    }

    @Test fun rejectsUnclosedBracket() =
        assertTrue(normalizeHost("[::1:7125") is HostResult.Rejected)

    @Test fun rejectsBareIpv6() =
        assertTrue(normalizeHost("fe80::1:2:3:4") is HostResult.Rejected)

    @Test fun rejectsPathInHostField() =
        assertTrue(normalizeHost("host.local/foo") is HostResult.Rejected)

    @Test fun rejectsSchemeOnlyHostField() =
        assertTrue(normalizeHost("http://") is HostResult.Rejected)

    @Test fun rejectsFullUrlInHostField() =
        assertTrue(normalizeHost("https://host.local:7125/printer") is HostResult.Rejected)

    @Test fun rejectsBlank() = assertTrue(normalizeHost("   ") is HostResult.Rejected)

    @Test fun plainUrls() {
        val u = buildConnectionUrls("192.168.1.50", 7125, advancedUrl = null, useSecure = false)
        assertEquals("http://192.168.1.50:7125", u.httpBase)
        assertEquals("ws://192.168.1.50:7125/websocket", u.wsUrl)
    }

    @Test fun legacySecure() {
        val u = buildConnectionUrls("h", 7130, advancedUrl = null, useSecure = true)
        assertEquals("https://h:7130", u.httpBase)
        assertEquals("wss://h:7130/websocket", u.wsUrl)
    }

    @Test fun advancedUrlRoutePrefix() {
        val u = buildConnectionUrls("ignored", 7125, advancedUrl = "https://my.host/printer1", useSecure = false)
        assertEquals("https://my.host/printer1", u.httpBase)
        assertEquals("wss://my.host/printer1/websocket", u.wsUrl)
    }

    @Test fun advancedUrlCollapsesDefaultPorts() {
        val http = buildConnectionUrls("x", 7125, advancedUrl = "http://h:80", useSecure = false)
        val https = buildConnectionUrls("x", 7125, advancedUrl = "https://h:443", useSecure = false)
        assertEquals("http://h", http.httpBase)
        assertEquals("ws://h/websocket", http.wsUrl)
        assertEquals("https://h", https.httpBase)
        assertEquals("wss://h/websocket", https.wsUrl)
    }

    @Test fun advancedUrlUppercaseScheme() {
        val u = buildConnectionUrls("x", 7125, advancedUrl = "HTTPS://H.EXAMPLE/Printer", useSecure = false)
        assertEquals("https://h.example/Printer", u.httpBase)
        assertEquals("wss://h.example/Printer/websocket", u.wsUrl)
    }

    @Test fun advancedUrlWsRoundTrip() {
        val u = buildConnectionUrls("x", 7125, advancedUrl = "ws://h:7125/proxy", useSecure = false)
        assertEquals("http://h:7125/proxy", u.httpBase)
        assertEquals("ws://h:7125/proxy/websocket", u.wsUrl)
    }

    @Test fun advancedUrlWssRoundTrip() {
        val u = buildConnectionUrls("x", 7125, advancedUrl = "wss://h:7125/proxy", useSecure = false)
        assertEquals("https://h:7125/proxy", u.httpBase)
        assertEquals("wss://h:7125/proxy/websocket", u.wsUrl)
    }

    @Test fun advancedUrlDeDupesWebsocket() {
        val u = buildConnectionUrls("x", 7125, advancedUrl = "ws://h:7125/websocket", useSecure = false)
        assertEquals("http://h:7125", u.httpBase)
        assertEquals("ws://h:7125/websocket", u.wsUrl)
    }

    @Test fun advancedUrlRejectsQueryFragmentSchemeOnlyAndInvalid() {
        assertThrows(IllegalArgumentException::class.java) {
            buildConnectionUrls("x", 7125, advancedUrl = "https://h?x=1", useSecure = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildConnectionUrls("x", 7125, advancedUrl = "https://h#frag", useSecure = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildConnectionUrls("x", 7125, advancedUrl = "https://", useSecure = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            buildConnectionUrls("x", 7125, advancedUrl = "not a url", useSecure = false)
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.ConnectionUrlsTest"`
Expected: FAIL (unresolved `normalizeHost`/`buildConnectionUrls`).

- [ ] **Step 3: Implement `ConnectionUrls.kt`**

```kotlin
package works.mees.dinghy.config

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class ConnectionUrls(val httpBase: String, val wsUrl: String)

sealed interface HostResult {
    data class Clean(val host: String, val portOverride: Int? = null) : HostResult
    data class Rejected(val reason: String) : HostResult
}

private val SCHEME_RE = Regex("^([A-Za-z][A-Za-z0-9+.-]*):", RegexOption.IGNORE_CASE)
private val PORT_RANGE = 1..65535

fun normalizeHost(raw: String): HostResult {
    val s = raw.trim()
    if (s.isEmpty()) return HostResult.Rejected("Enter a host or IP")
    if ("://" in s || SCHEME_RE.matches(s)) {
        return HostResult.Rejected("Enter only the host here; put full URLs in Advanced")
    }
    if (s.any { it == '/' || it == '?' || it == '#' }) {
        return HostResult.Rejected("Enter only the host here; remove paths, query, or fragment")
    }
    if ('@' in s) {
        return HostResult.Rejected("Enter only the host; remove credentials")
    }

    if (s.startsWith("[")) {
        val close = s.indexOf(']')
        if (close < 0) return HostResult.Rejected("Close the IPv6 bracket")
        val host = s.substring(0, close + 1)
        if (host.length <= 2) return HostResult.Rejected("Enter an IPv6 address in brackets")
        val rest = s.substring(close + 1)
        return when {
            rest.isEmpty() -> HostResult.Clean(host)
            rest.startsWith(":") -> parsePort(rest.drop(1))?.let { HostResult.Clean(host, it) }
                ?: HostResult.Rejected("Port must be 1-65535")
            else -> HostResult.Rejected("Use [IPv6] or [IPv6]:port")
        }
    }

    val colonCount = s.count { it == ':' }
    if (colonCount >= 2) return HostResult.Rejected("Wrap IPv6 addresses in [brackets]")
    if (colonCount == 1) {
        val host = s.substringBefore(':').trim()
        val portText = s.substringAfter(':').trim()
        if (host.isEmpty()) return HostResult.Rejected("Enter a host or IP")
        val parsedPort = parsePort(portText) ?: return HostResult.Rejected("Port must be 1-65535")
        return HostResult.Clean(host, parsedPort)
    }

    return HostResult.Clean(s)
}

private fun parsePort(text: String): Int? =
    text.toIntOrNull()?.takeIf { it in PORT_RANGE }

fun buildConnectionUrls(host: String, port: Int, advancedUrl: String?, useSecure: Boolean): ConnectionUrls {
    val adv = advancedUrl?.trim().orEmpty()
    if (adv.isNotEmpty()) {
        return buildAdvancedConnectionUrls(adv)
    }
    val httpScheme = if (useSecure) "https" else "http"
    val wsScheme = if (useSecure) "wss" else "ws"
    return ConnectionUrls(
        httpBase = "$httpScheme://$host:$port",
        wsUrl = "$wsScheme://$host:$port/websocket",
    )
}

private fun buildAdvancedConnectionUrls(raw: String): ConnectionUrls {
    val match = SCHEME_RE.find(raw)
        ?: throw IllegalArgumentException("Advanced URL must start with http://, https://, ws://, or wss://")
    val rawScheme = match.groupValues[1].lowercase()
    val httpScheme = when (rawScheme) {
        "http", "https" -> rawScheme
        "ws" -> "http"
        "wss" -> "https"
        else -> throw IllegalArgumentException("Unsupported Advanced URL scheme: $rawScheme")
    }
    val suffix = raw.substring(match.value.length)
    if (suffix.isBlank() || suffix == "//") {
        throw IllegalArgumentException("Advanced URL must include a host")
    }

    val parsed = "$httpScheme:$suffix".toHttpUrlOrNull()
        ?: throw IllegalArgumentException("Invalid Advanced URL")
    if (parsed.query != null || parsed.fragment != null) {
        throw IllegalArgumentException("Advanced URL must not include query or fragment")
    }

    val basePath = parsed.encodedPath
        .trimEnd('/')
        .removeSuffix("/websocket")
        .ifBlank { "/" }
    val httpBaseUrl = parsed.newBuilder()
        .scheme(httpScheme)
        .encodedPath(basePath)
        .query(null)
        .fragment(null)
        .build()
        .toString()
        .removeSuffix("/")
    val wsScheme = if (httpScheme == "https") "wss" else "ws"
    val wsBase = httpBaseUrl.replaceFirst("$httpScheme://", "$wsScheme://")
    return ConnectionUrls(httpBase = httpBaseUrl, wsUrl = "$wsBase/websocket")
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: same as Step 2. Expected: PASS (all cases).

- [ ] **Step 5: Delegate `ConnectionConfig` to the builder**

In `ConnectionConfig.kt`, replace the getters:
```kotlin
val httpBase: String get() = buildConnectionUrls(host, port, advancedUrl, useSecure).httpBase
val wsUrl: String get() = buildConnectionUrls(host, port, advancedUrl, useSecure).wsUrl
```
Task 1 added `advancedUrl` to the data class and `Profile.toConnectionConfig()`.

- [ ] **Step 6: Run the full config test package + commit**

Run: `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.*"`
Expected: PASS.
```bash
git add app/src/main/java/works/mees/dinghy/config/ConnectionUrls.kt app/src/main/java/works/mees/dinghy/config/ConnectionConfig.kt app/src/main/java/works/mees/dinghy/config/Profile.kt app/src/test/java/works/mees/dinghy/config/ConnectionUrlsTest.kt
git commit -m "feat(connection): pure ConnectionUrls normalizer + builder, delegate ConnectionConfig"
```

---

## Task 3: `ProbeClassifier` — classify a probe failure (pure)

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/net/ProbeClassifier.kt`
- Test: `app/src/test/java/works/mees/dinghy/net/ProbeClassifierTest.kt`

**Interfaces:**
- Produces:
  - `enum class ProbeFailure { Timeout, Refused, Unauthorized, Certificate, Unknown }`
  - `fun classifyProbeFailure(t: Throwable?, httpStatus: Int?): ProbeFailure`
- Consumes: nothing.

**Contract:** httpStatus 401/403 → `Unauthorized`. `TimeoutCancellationException`,
`SocketTimeoutException`, `TimeoutException`, `RpcConnectionException(ConnectionError.Timeout)` →
`Timeout`; `ConnectException` (or message contains "refused") → `Refused`; `SSLException` /
`SSLHandshakeException` / `MoonrakerSocket.isTlsTrustFailure(t)` / message contains "cert"/"trust" →
`Certificate`; `AuthException(ConnectionError.AuthRequired)` or an identify `RpcError` classified as
`ConnectionError.AuthRequired` → `Unauthorized`; else `Unknown`.

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.net

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.net.ConnectException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Test

class ProbeClassifierTest {
    @Test fun unauthorizedFromStatus() =
        assertEquals(ProbeFailure.Unauthorized, classifyProbeFailure(null, 401))
    @Test fun timeout() =
        assertEquals(ProbeFailure.Timeout, classifyProbeFailure(SocketTimeoutException(), null))
    @Test fun coroutineTimeout() = runTest {
        val timeout = try {
            withTimeout(1) { delay(Long.MAX_VALUE) }
            throw AssertionError("expected timeout")
        } catch (e: TimeoutCancellationException) {
            e
        }
        assertEquals(ProbeFailure.Timeout, classifyProbeFailure(timeout, null))
    }
    @Test fun refused() =
        assertEquals(ProbeFailure.Refused, classifyProbeFailure(ConnectException("Connection refused"), null))
    @Test fun cert() =
        assertEquals(ProbeFailure.Certificate, classifyProbeFailure(SSLHandshakeException("bad cert"), null))
    @Test fun identifyUnauthorizedRpcError() =
        assertEquals(ProbeFailure.Unauthorized, classifyProbeFailure(RpcError(-32602, "Unauthorized"), null))
    @Test fun unknown() =
        assertEquals(ProbeFailure.Unknown, classifyProbeFailure(RuntimeException("?"), null))
}
```

- [ ] **Step 2: Run to verify fail**

Run: `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.net.ProbeClassifierTest"`
Expected: FAIL (unresolved symbols).

- [ ] **Step 3: Implement**

```kotlin
package works.mees.dinghy.net

import kotlinx.coroutines.TimeoutCancellationException
import works.mees.dinghy.auth.AuthException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException
import javax.net.ssl.SSLException

enum class ProbeFailure { Timeout, Refused, Unauthorized, Certificate, Unknown }

fun classifyProbeFailure(t: Throwable?, httpStatus: Int?): ProbeFailure {
    if (httpStatus == 401 || httpStatus == 403) return ProbeFailure.Unauthorized
    if (t is AuthException && t.reason == ConnectionError.AuthRequired) return ProbeFailure.Unauthorized
    if (t is RpcError && classifyIdentifyError(t.code, t.message) == ConnectionError.AuthRequired) {
        return ProbeFailure.Unauthorized
    }
    if (t is RpcConnectionException && t.reason == ConnectionError.Timeout) return ProbeFailure.Timeout
    if (t is RpcConnectionException && t.reason == ConnectionError.AuthRequired) {
        return ProbeFailure.Unauthorized
    }
    if (t != null && MoonrakerSocket.isTlsTrustFailure(t)) return ProbeFailure.Certificate

    val msg = t?.message?.lowercase().orEmpty()
    return when {
        t is TimeoutCancellationException || t is SocketTimeoutException || t is TimeoutException -> ProbeFailure.Timeout
        t is ConnectException || "refused" in msg -> ProbeFailure.Refused
        t is SSLException || "cert" in msg || "trust" in msg || "handshake" in msg -> ProbeFailure.Certificate
        else -> ProbeFailure.Unknown
    }
}
```

- [ ] **Step 4: Run to verify pass** — same command, expect PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/net/ProbeClassifier.kt app/src/test/java/works/mees/dinghy/net/ProbeClassifierTest.kt
git commit -m "feat(connection): pure probe-failure classifier"
```

---

## Task 4: `ConnectionProbe` — dual HTTP + websocket-identify probe

**Files:**
- Create: `app/src/main/java/works/mees/dinghy/net/ConnectionProbe.kt`
- Modify: `app/src/main/java/works/mees/dinghy/di/AppContainer.kt`
- Test: `app/src/test/java/works/mees/dinghy/net/ConnectionProbeTest.kt`

**Interfaces:**
- Produces:
  - `data class TransportResult(val ok: Boolean, val failure: ProbeFailure? = null)`
  - `data class ProbeResult(val httpUrl: String, val wsUrl: String, val http: TransportResult, val ws: TransportResult)`
  - `class ConnectionProbe(...)` with injectable
    `httpLeg: suspend (ConnectionConfig, ConnectionUrls) -> TransportResult`,
    `wsLeg: suspend (ConnectionConfig, ConnectionUrls) -> TransportResult`, and
    `suspend fun probe(config: ConnectionConfig): ProbeResult`.
  - `ConnectionProbe.real(sharedClient: OkHttpClient): ConnectionProbe`.
  - One AppContainer API, used by the UI task:
    `fun runConnectionProbe(config: ConnectionConfig, onResult: (ProbeResult) -> Unit): Job`.
    It launches on a dedicated process-lifetime `probeScope` and invokes `onResult` on
    `Dispatchers.Main.immediate`.

**Real seams verified in the current codebase:**
- `MoonrakerSocket.events(): Flow<SocketEvent>` is the public socket-open seam at
  `app/src/main/java/works/mees/dinghy/net/MoonrakerSocket.kt:67`.
- `JsonRpcClient.bind(connection)`, raw `request(...)`, and `dispatch(text)` are at
  `app/src/main/java/works/mees/dinghy/net/JsonRpcClient.kt:103`, `:115`, and `:170`.
- The typed `JsonRpcClient.request(CommandSpec, args, timeoutMs)` extension is at
  `app/src/main/java/works/mees/dinghy/command/CommandDispatchExtensions.kt:13`.
- `IdentifyArgs(clientName, version, type, url, apiKey)` is at
  `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt:11`; `CommandRegistry.identify`
  and `CommandRegistry.serverInfo` are at `:170` and `:192`.
- `MoonrakerAuth.fetchOneshotToken()` and `buildAuthedWsUrl(baseWsUrl, token)` are at
  `app/src/main/java/works/mees/dinghy/auth/MoonrakerAuth.kt:53` and `:95`.
- `MoonrakerService`'s current auth wiring constructs `MoonrakerAuth` and uses
  `auth.buildAuthedWsUrl(cfg.wsUrl, token)` before `MoonrakerSocket.real(...).events()` at
  `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt:132-140`.
- `AppContainer.webcamHttpClient` is `MoonrakerSocket.defaultClient()` at
  `app/src/main/java/works/mees/dinghy/di/AppContainer.kt:636-637`, so it has `readTimeout(0)` and
  must be derived with a finite REST read/call timeout before REST probing.

**Contract (Codex BLOCKER):** the probe must exercise **auth**, not just reachability.
- **HTTP leg:** GET `"$httpBase/server/info"`; add `X-Api-Key` header when `config.apiKey != null`;
  `ok` iff response is 200; on 401/403 or exception → `failure = classifyProbeFailure(e, code)`.
  (`/access/info` is reachable by unauthorized clients, so it is NOT a valid auth signal — use the
  protected `/server/info`.)
- **WS leg:** construct a one-shot `MoonrakerSocket.real(client, wsUrl).events()`, collect it, bind
  `JsonRpcClient` on `SocketEvent.Open`, route `SocketEvent.Frame` through `rpc.dispatch(text)`, send
  `CommandRegistry.identify` with `IdentifyArgs(clientName = "Dinghy Display", version = "0.1.0",
  url = "https://mees.works/dinghy-display", apiKey = auth.xApiKeyHeader())`, then send
  `CommandRegistry.serverInfo`. `ok` iff identify and server-info both succeed. Tear the live
  `RpcConnection` down regardless.
- **Keyed WS auth:** when `config.apiKey` is not blank, mirror `MoonrakerService`: create
  `MoonrakerAuth(sharedClient, urls.httpBase, config.apiKey)`, fetch a oneshot token with
  `fetchOneshotToken()`, open `auth.buildAuthedWsUrl(urls.wsUrl, token)`, and still pass the API key in
  `IdentifyArgs.apiKey`.
- Run both legs **concurrently** (`coroutineScope { async{} ; async{} }`), short per-leg timeout
  (5s). `runLeg` maps `TimeoutCancellationException` to `ProbeFailure.Timeout`, **rethrows every other
  `CancellationException`**, and maps non-cancel exceptions through `classifyProbeFailure`. A normal
  probe attempt always returns a `ProbeResult`; caller cancellation propagates.

**Testing note:** the live socket/HTTP are integration surfaces. **Unit-test the pure orchestration**
by constructing `ConnectionProbe` with injected lambdas for the two legs, so the test verifies: both
legs run, derived URLs come from `ConnectionUrls`, a thrown leg becomes a classified
`TransportResult`, timeout becomes `ProbeFailure.Timeout`, and non-timeout cancellation is rethrown.

- [ ] **Step 1: Write the failing test**

```kotlin
package works.mees.dinghy.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import works.mees.dinghy.config.ConnectionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConnectionProbeTest {
    @Test fun runsBothLegsAndDerivesUrls() = runTest {
        val probe = ConnectionProbe(
            httpLeg = { _, _ -> TransportResult(ok = true) },
            wsLeg = { _, _ -> TransportResult(ok = false, failure = ProbeFailure.Unauthorized) },
        )
        val r = probe.probe(ConnectionConfig(host = "10.0.0.5", port = 7125))
        assertEquals("http://10.0.0.5:7125", r.httpUrl)
        assertEquals("ws://10.0.0.5:7125/websocket", r.wsUrl)
        assertTrue(r.http.ok)
        assertFalse(r.ws.ok)
        assertEquals(ProbeFailure.Unauthorized, r.ws.failure)
    }

    @Test fun aThrowingLegBecomesClassifiedFailure() = runTest {
        val probe = ConnectionProbe(
            httpLeg = { _, _ -> throw java.net.ConnectException("Connection refused") },
            wsLeg = { _, _ -> TransportResult(ok = true) },
        )
        val r = probe.probe(ConnectionConfig(host = "h", port = 7125))
        assertFalse(r.http.ok)
        assertEquals(ProbeFailure.Refused, r.http.failure)
    }

    @Test fun timeoutBecomesClassifiedFailure() = runTest {
        val probe = ConnectionProbe(
            httpLeg = { _, _ ->
                delay(Long.MAX_VALUE)
                TransportResult(ok = true)
            },
            wsLeg = { _, _ -> TransportResult(ok = true) },
            perLegTimeoutMs = 1,
        )
        val r = probe.probe(ConnectionConfig(host = "h", port = 7125))
        assertFalse(r.http.ok)
        assertEquals(ProbeFailure.Timeout, r.http.failure)
    }

    @Test fun nonTimeoutCancellationRethrows() = runTest {
        val probe = ConnectionProbe(
            httpLeg = { _, _ -> throw CancellationException("caller cancelled") },
            wsLeg = { _, _ -> TransportResult(ok = true) },
        )
        try {
            probe.probe(ConnectionConfig(host = "h", port = 7125))
            fail("probe() must rethrow non-timeout CancellationException")
        } catch (e: CancellationException) {
            assertEquals("caller cancelled", e.message)
        }
    }
}
```

- [ ] **Step 2: Run to verify fail** —
`cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.net.ConnectionProbeTest"` → FAIL.

- [ ] **Step 3: Implement the orchestration (with injectable legs)**

```kotlin
package works.mees.dinghy.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.config.ConnectionUrls
import works.mees.dinghy.config.buildConnectionUrls
import works.mees.dinghy.auth.MoonrakerAuth
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.IdentifyArgs
import works.mees.dinghy.command.request
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class TransportResult(val ok: Boolean, val failure: ProbeFailure? = null)

data class ProbeResult(
    val httpUrl: String,
    val wsUrl: String,
    val http: TransportResult,
    val ws: TransportResult,
)

class ConnectionProbe(
    private val httpLeg: suspend (ConnectionConfig, ConnectionUrls) -> TransportResult,
    private val wsLeg: suspend (ConnectionConfig, ConnectionUrls) -> TransportResult,
    private val perLegTimeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    suspend fun probe(config: ConnectionConfig): ProbeResult = coroutineScope {
        val urls = buildConnectionUrls(config.host, config.port, config.advancedUrl, config.useSecure)
        val httpD = async { runLeg { httpLeg(config, urls) } }
        val wsD = async { runLeg { wsLeg(config, urls) } }
        ProbeResult(urls.httpBase, urls.wsUrl, httpD.await(), wsD.await())
    }

    private suspend fun runLeg(block: suspend () -> TransportResult): TransportResult =
        try {
            withTimeout(perLegTimeoutMs) { block() }
        } catch (e: TimeoutCancellationException) {
            TransportResult(ok = false, failure = ProbeFailure.Timeout)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            TransportResult(ok = false, failure = classifyProbeFailure(t, null))
        }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 5_000L
        private const val CLIENT_NAME = "Dinghy Display"
        private const val CLIENT_VERSION = "0.1.0"
        private const val CLIENT_URL = "https://mees.works/dinghy-display"

        fun real(
            sharedClient: OkHttpClient,
            socketFactory: (OkHttpClient, String) -> MoonrakerSocket =
                { client, wsUrl -> MoonrakerSocket.real(client = client, wsUrl = wsUrl) },
        ): ConnectionProbe {
            val restClient = sharedClient.newBuilder()
                .callTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .readTimeout(DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
            return ConnectionProbe(
                httpLeg = { config, urls -> realHttpLeg(restClient, config, urls) },
                wsLeg = { config, urls -> realWsLeg(sharedClient, config, urls, socketFactory) },
            )
        }

        private suspend fun realHttpLeg(
            client: OkHttpClient,
            config: ConnectionConfig,
            urls: ConnectionUrls,
        ): TransportResult = withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${urls.httpBase}/server/info")
                .get()
                .apply {
                    config.apiKey?.takeIf { it.isNotBlank() }?.let {
                        header(MoonrakerAuth.HEADER_API_KEY, it)
                    }
                }
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    if (resp.code == 200) {
                        TransportResult(ok = true)
                    } else {
                        TransportResult(ok = false, failure = classifyProbeFailure(null, resp.code))
                    }
                }
            } catch (e: IOException) {
                TransportResult(ok = false, failure = classifyProbeFailure(e, null))
            }
        }

        private suspend fun realWsLeg(
            client: OkHttpClient,
            config: ConnectionConfig,
            urls: ConnectionUrls,
            socketFactory: (OkHttpClient, String) -> MoonrakerSocket,
        ): TransportResult = coroutineScope {
            val auth = MoonrakerAuth(client, urls.httpBase, config.apiKey)
            val wsUrl = if (auth.isKeyed) {
                val token = withContext(Dispatchers.IO) { auth.fetchOneshotToken() }
                auth.buildAuthedWsUrl(urls.wsUrl, token)
            } else {
                urls.wsUrl
            }

            val rpc = JsonRpcClient(defaultTimeoutMs = DEFAULT_TIMEOUT_MS)
            val opened = CompletableDeferred<Unit>()
            var liveConnection: RpcConnection? = null
            val collector = launch {
                socketFactory(client, wsUrl).events().collect { event ->
                    when (event) {
                        is SocketEvent.Open -> {
                            liveConnection = event.connection
                            rpc.bind(event.connection)
                            if (!opened.isCompleted) opened.complete(Unit)
                        }
                        is SocketEvent.Frame -> rpc.dispatch(event.text)
                        is SocketEvent.Closed -> {
                            val reason = event.cause ?: ConnectionError.NetworkUnavailable
                            if (!opened.isCompleted) {
                                opened.completeExceptionally(
                                    RpcConnectionException(reason, "probe socket closed before open"),
                                )
                            }
                            rpc.close(reason)
                        }
                    }
                }
                if (!opened.isCompleted) {
                    opened.completeExceptionally(
                        RpcConnectionException(ConnectionError.NetworkUnavailable, "probe socket completed before open"),
                    )
                }
            }

            try {
                opened.await()
                rpc.request(
                    CommandRegistry.identify,
                    IdentifyArgs(
                        clientName = CLIENT_NAME,
                        version = CLIENT_VERSION,
                        url = CLIENT_URL,
                        apiKey = auth.xApiKeyHeader(),
                    ),
                    timeoutMs = DEFAULT_TIMEOUT_MS,
                )
                rpc.request(CommandRegistry.serverInfo, Unit, timeoutMs = DEFAULT_TIMEOUT_MS)
                TransportResult(ok = true)
            } finally {
                liveConnection?.close()
                rpc.close(ConnectionError.NetworkUnavailable)
                collector.cancelAndJoin()
            }
        }
    }
}
```

- [ ] **Step 4: Run to verify pass** — same command, expect PASS.

- [ ] **Step 5: Add the AppContainer probe API**

In `AppContainer.kt`, add the imports:
```kotlin
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import works.mees.dinghy.net.ConnectionProbe
import works.mees.dinghy.net.ProbeResult
```

Add a dedicated process-lifetime scope after `writeScope`:
```kotlin
private val probeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

Add this exact API (and use no other probe name in Task 8):
```kotlin
fun runConnectionProbe(
    config: ConnectionConfig,
    onResult: (ProbeResult) -> Unit,
): Job = probeScope.launch {
    val result = ConnectionProbe.real(webcamHttpClient).probe(config)
    withContext(Dispatchers.Main.immediate) {
        onResult(result)
    }
}
```

`ConnectionProbe.real(...)` derives a finite REST client from `webcamHttpClient.newBuilder()` but keeps
the shared pool/TLS configuration. Do **not** call `webcamHttpClient` directly for REST because it is
`MoonrakerSocket.defaultClient()` with `readTimeout(0)`.

- [ ] **Step 6: Build the app (compile check) + commit**

Run: `cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --no-daemon"` → BUILD SUCCESSFUL.
```bash
git add app/src/main/java/works/mees/dinghy/net/ConnectionProbe.kt app/src/main/java/works/mees/dinghy/di/AppContainer.kt app/src/test/java/works/mees/dinghy/net/ConnectionProbeTest.kt
git commit -m "feat(connection): dual HTTP+WS connection probe (auth-exercising)"
```

---

## Task 5: ICON GATE — owner assigns glyphs (no code until done)

**This is a hard stop, not a coding task.** Per the icon law, Claude must NOT pick glyphs. The
following row/button functions need a glyph. Some may reuse existing `DinghyIcons` tokens (e.g.
`Back`, `CheckCircle`, `Edit`, `ShieldLock`); the owner confirms each.

| Function | `DinghyIcons` val | Ligature | Notes |
|----------|-------------------|----------|-------|
| Name row | `TextFields` | `text_fields` | new |
| Host row | `DeveloperBoard` | `developer_board` | new |
| Port row | `Numbers` | `123` | new — val identifier `Numbers` (ligature is literally `123`) |
| API key row | `VpnKey` | `vpn_key` | new |
| Find-on-network row | `Search` | `search` | new (reuse if a `search` glyph already exists) |
| Advanced row | `Tune` | `tune` | new — if `FineTune` already maps to ligature `tune`, reuse it instead |
| Test (foot action) | `NetworkPing` | `network_ping` | new |
| Probe pass | `CheckCircle` | `check_circle` | **reuse existing** |
| Probe fail | `XCircle` | `x_circle` | new — ⚠ if Material Symbols lacks `x_circle`, add to font subset or use a custom drawable (Step 3 covers this) |
| Discovered-printer row | `DeveloperBoard` | `developer_board` | **reuse** (a found printer is a board) |
| Focus header (screen identity) | `AndroidWifi3BarPlus` | `android_wifi_3_bar_plus` | new — ⚠ obscure ligature; `verify_ligatures.py` (Step 3) confirms it; if absent from the font subset, add it or use a custom drawable |

- [ ] **Step 1:** Owner fills the table (ASK — do not proceed otherwise).
- [ ] **Step 2:** For any NEW ligature glyph, add the `val` to `DinghyIcons` AND the `all` list.
- [ ] **Step 3:** Run `python tools/verify_ligatures.py` → exit 0. If a glyph is missing from the font,
  add it to the font before relying on it.
- [ ] **Step 4: Commit** (only if glyphs were added):
```bash
git add app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
git commit -m "feat(connection): register owner-assigned connection-editor glyphs"
```

---

## Task 6: String resources

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- (No test — verified by compilation + UI task.)

- [ ] **Step 1:** Add the strings the UI references (exact names used in Task 8):
```xml
<string name="conn_title_add">Add a printer</string>
<string name="conn_title_edit">Edit %1$s</string>
<string name="conn_row_name">Name</string>
<string name="conn_row_host">Host</string>
<string name="conn_row_port">Port</string>
<string name="conn_row_apikey">API key</string>
<string name="conn_row_find">Find on network</string>
<string name="conn_row_advanced">Advanced</string>
<string name="conn_apikey_hint">Only needed if your install requires it</string>
<string name="conn_apikey_set">Set</string>
<string name="conn_apikey_unset">Not set</string>
<string name="conn_local_warning">.local names can be unreliable — prefer the numeric IP</string>
<string name="conn_test">Test</string>
<string name="conn_test_untested">Not tested yet</string>
<string name="conn_test_http">HTTP</string>
<string name="conn_test_ws">Websocket</string>
<string name="conn_fail_timeout">Connection timeout — printer off or not on this network</string>
<string name="conn_fail_refused">Connection refused — wrong port?</string>
<string name="conn_fail_unauthorized">Unauthorized — add this device\'s IP to Moonraker trusted_clients, or enter an API key</string>
<string name="conn_fail_cert">Certificate error</string>
<string name="conn_fail_unknown">Could not connect</string>
<string name="conn_save_anyway">Save anyway</string>
<string name="conn_scan">Scan</string>
<string name="conn_scan_empty">Tap Scan to search your network</string>
```

- [ ] **Step 2: Commit**
```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat(connection): string resources for the redesigned editor"
```

---

## Task 7: Secure-profile migration (`useSecure=true` → `advancedUrl`)

**Files:**
- Modify: `app/src/main/java/works/mees/dinghy/config/Profile.kt`
- Modify: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt` (pure save helper only; Task 8 rewrites the UI around it)
- Test: `app/src/test/java/works/mees/dinghy/config/ProfileSecureMigrationTest.kt`
- Test: `app/src/test/java/works/mees/dinghy/ui/screen/PrintersModeToggleTest.kt` (extend the existing editor-save helper tests)

**Interfaces:**
- Consumes Task 1 + Task 2.
- Produces a real migration path: persisted legacy profiles with `useSecure=true` decode to runtime
  `Profile(advancedUrl = "https://<host>:<port>", useSecure = false)`. The editor seeds its Advanced
  field from `profile.advancedUrl.orEmpty()`, and Save writes `advancedUrl` plus `useSecure=false`.
  A saved migrated profile must still produce `wss://.../websocket` through `toConnectionConfig()`.
- Real seams:
  - `ProfileStore.sanitize(...)` already maps decoded `PersistedProfile` values through
    `Profile.fromPersisted(...)` at `app/src/main/java/works/mees/dinghy/config/ProfileStore.kt:164-167`,
    so the migration belongs in `fromPersisted`, not in DataStore I/O.
  - Existing package-level editor key helper lives at
    `app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt:62`, and its tests are
    already in `app/src/test/java/works/mees/dinghy/ui/screen/PrintersModeToggleTest.kt:147-178`.

- [ ] **Step 1: Write the failing migration tests**

Create `app/src/test/java/works/mees/dinghy/config/ProfileSecureMigrationTest.kt`:

```kotlin
package works.mees.dinghy.config

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ProfileSecureMigrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun fromPersistedMigratesUseSecureToAdvancedUrl() {
        val p = Profile.fromPersisted(
            PersistedProfile(id = "legacy", host = "secure.local", port = 7130, useSecure = true),
        )

        assertEquals("https://secure.local:7130", p.advancedUrl)
        assertFalse("runtime Profile must not resurrect the retired toggle", p.useSecure)
        assertEquals("https://secure.local:7130", p.toConnectionConfig().httpBase)
        assertEquals("wss://secure.local:7130/websocket", p.toConnectionConfig().wsUrl)
    }

    @Test fun sanitizeMigratesLegacySecureProfiles() {
        val raw = json.encodeToString(
            ListSerializer(PersistedProfile.serializer()),
            listOf(PersistedProfile(id = "legacy", host = "secure.local", port = 7130, useSecure = true)),
        )

        val p = ProfileStore.sanitize(raw).single()

        assertEquals("https://secure.local:7130", p.advancedUrl)
        assertFalse(p.useSecure)
        assertEquals("wss://secure.local:7130/websocket", p.toConnectionConfig().wsUrl)
    }

    @Test fun toPersistedWritesAdvancedUrlNotUseSecure() {
        val opened = Profile.fromPersisted(
            PersistedProfile(id = "legacy", host = "secure.local", port = 7130, useSecure = true),
        )

        val saved = opened.copy(name = "Edited").toPersisted()

        assertEquals("https://secure.local:7130", saved.advancedUrl)
        assertFalse("save must clear the retired useSecure bit", saved.useSecure)
    }
}
```

Extend `app/src/test/java/works/mees/dinghy/ui/screen/PrintersModeToggleTest.kt`:

```kotlin
@Test
fun connectionEditorSave_migratedSecureProfileStillProducesWss() {
    val opened = Profile.fromPersisted(
        PersistedProfile(id = "legacy", host = "secure.local", port = 7130, useSecure = true),
    )

    val saved = buildProfileFromConnectionEditorSave(
        existing = opened,
        nameInput = "Secure Printer",
        host = opened.host,
        port = opened.port,
        apiKeyInput = "",
        keyCleared = false,
        advancedUrlInput = opened.advancedUrl.orEmpty(),
    )

    assertEquals("https://secure.local:7130", saved.advancedUrl)
    assertFalse(saved.useSecure)
    assertEquals("wss://secure.local:7130/websocket", saved.toConnectionConfig().wsUrl)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:
`cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.config.ProfileSecureMigrationTest --tests works.mees.dinghy.ui.screen.PrintersModeToggleTest"`

Expected: FAIL (`advancedUrl` does not migrate; `buildProfileFromConnectionEditorSave` is unresolved).

- [ ] **Step 3: Implement the migration in `Profile.fromPersisted` + `toPersisted`**

In `Profile.fromPersisted(...)`, compute the migrated field before the `Profile(...)` call:

```kotlin
val migratedAdvancedUrl =
    p.advancedUrl?.trim()?.ifBlank { null }
        ?: if (p.useSecure) "https://${p.host}:${p.port}" else null
```

Then pass:

```kotlin
advancedUrl = migratedAdvancedUrl,
useSecure = false,
```

In `Profile.toPersisted()`, keep writing `advancedUrl = advancedUrl`, but write the retired toggle as
false so a Save cannot resurrect TLS-via-toggle:

```kotlin
useSecure = false,
advancedUrl = advancedUrl,
```

`Profile.toConnectionConfig()` must keep passing `advancedUrl = advancedUrl`; `ConnectionUrls` then
derives `https`/`wss` from that URL.

- [ ] **Step 4: Add the pure editor-save helper and seed Advanced from the migrated profile**

In `PrinterConnectionEditor.kt`, add this package-level helper near `resolveEditorKeyOnSave(...)`:

```kotlin
internal fun buildProfileFromConnectionEditorSave(
    existing: Profile?,
    nameInput: String,
    host: String,
    port: Int,
    apiKeyInput: String,
    keyCleared: Boolean,
    advancedUrlInput: String,
): Profile {
    val cleanName = nameInput.trim().ifBlank { null }
    val resolvedKey = resolveEditorKeyOnSave(
        storedKey = existing?.apiKey,
        keyCleared = keyCleared,
        fieldInput = apiKeyInput,
    )
    val cleanAdvancedUrl = advancedUrlInput.trim().ifBlank { null }
    return if (existing != null) {
        existing.copy(
            name = cleanName,
            host = host.trim(),
            port = port,
            apiKey = resolvedKey,
            useSecure = false,
            advancedUrl = cleanAdvancedUrl,
            nameAutoSeeded = resolveAutoSeededOnSave(cleanName, existing.nameAutoSeeded, existing.name),
        )
    } else {
        Profile(
            id = Profile.newId(),
            name = cleanName,
            host = host.trim(),
            port = port,
            apiKey = resolvedKey,
            useSecure = false,
            advancedUrl = cleanAdvancedUrl,
            nameAutoSeeded = resolveAutoSeededOnSave(cleanName, prior = false, priorName = null),
        )
    }
}
```

When seeding editor state from an existing profile, set:

```kotlin
advancedUrl = profile?.advancedUrl.orEmpty()
useSecure = false
```

Task 8 must call `buildProfileFromConnectionEditorSave(...)` in the Save action instead of open-coded
`Profile.copy(...)`, so the migration test continues exercising the same save logic the UI uses.

- [ ] **Step 5: Run tests to verify they pass**

Run the same command as Step 2. Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/works/mees/dinghy/config/Profile.kt app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt app/src/test/java/works/mees/dinghy/config/ProfileSecureMigrationTest.kt app/src/test/java/works/mees/dinghy/ui/screen/PrintersModeToggleTest.kt
git commit -m "fix(connection): migrate legacy secure profiles to advanced URL"
```

---

## Task 8: Rewrite `PrinterConnectionEditor` into Focus/Field

**Files:**
- Rewrite: `app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt`
- Verification: on-device (flox + moto) — Compose UI; pure save/probe/url logic already tested in
  Tasks 1–4 and 7.

**Interfaces:**
- Consumes: `ScreenScaffold`, `FocusFrame`, `ListBlock`, `ListRow`, `ListRowIcon`, `OutlinedControl`,
  `FootButtonBar`/`FootAction`, `TokenTextField`, `Intent`, `LocalTokens`, `DinghyType`, the
  `rememberUnitGrid()` grid, `normalizeHost`, `buildConnectionUrls`,
  `container.runConnectionProbe(config, onResult)`, `container.saveProfile(...)`,
  `buildProfileFromConnectionEditorSave(...)`, `container.discovery.discover()`.
- Public signature **unchanged**: `PrinterConnectionEditor(container, profile, onDone, modifier)`.
- Real seams:
  - `IncrementValuesScreen` derives `BoxWithConstraints` + `rememberUnitGrid(minOf(maxWidth,
    maxHeight))`, collects `printerState`/`dispatcher`, and defines `isPrinting`/`estop` at
    `app/src/main/java/works/mees/dinghy/ui/increments/IncrementValuesScreen.kt:52-80`.
  - `FootAction` constructor order is `label, icon, onClick, intent, contentDescription,
    onLongClick, enabled, fill, modifier` at
    `app/src/main/java/works/mees/dinghy/designsystem/components/FootButtonBar.kt:72-82`.
  - `Profile.displayName()` exists at `app/src/main/java/works/mees/dinghy/config/Profile.kt:108`.

**Structure (model exactly on `IncrementValuesScreen` + `ThemeScreen`):**

- [ ] **Step 0: Add imports and the compile-ready composable preamble**

Add the imports this task uses:

```kotlin
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.config.ConnectionUrls
import works.mees.dinghy.config.DiscoveredPrinter
import works.mees.dinghy.config.HostResult
import works.mees.dinghy.config.Profile
import works.mees.dinghy.config.buildConnectionUrls
import works.mees.dinghy.config.normalizeHost
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.net.ProbeFailure
import works.mees.dinghy.net.ProbeResult
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
```

At the top of `PrinterConnectionEditor(...)`, mirror `IncrementValuesScreen`'s state setup and define
every symbol used later:

```kotlin
val printerState by container.printerState.collectAsStateWithLifecycle(PrinterState())
val dispatcher by container.dispatcher.collectAsStateWithLifecycle(null)
val isPrinting = printerState.printState == PrintState.Printing ||
    printerState.printState == PrintState.Paused
val estop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit); Unit }
val back = stringResource(R.string.common_back)

var name by remember { mutableStateOf("") }
var host by remember { mutableStateOf("") }
var port by remember { mutableStateOf("7125") }
var apiKey by remember { mutableStateOf("") }
var advancedUrl by remember { mutableStateOf("") }
var keyAlreadySaved by remember { mutableStateOf(false) }
var keyCleared by remember { mutableStateOf(false) }
var hostError by remember { mutableStateOf<String?>(null) }
var portError by remember { mutableStateOf(false) }
var selected by rememberSaveable { mutableStateOf<ConnRow?>(null) }
var probe by remember { mutableStateOf<ProbeResult?>(null) }
var probing by remember { mutableStateOf(false) }
var probeJob by remember { mutableStateOf<Job?>(null) }
var scanRequest by remember { mutableStateOf(0) }
var scanning by remember { mutableStateOf(false) }
var scanned by remember { mutableStateOf(false) }
var discovered by remember { mutableStateOf<List<DiscoveredPrinter>>(emptyList()) }

LaunchedEffect(profile?.id) {
    name = profile?.name ?: ""
    host = profile?.host ?: ""
    port = profile?.port?.toString() ?: "7125"
    apiKey = ""
    advancedUrl = profile?.advancedUrl.orEmpty()
    keyAlreadySaved = profile?.apiKey != null
    keyCleared = false
    hostError = null
    portError = false
    selected = null
    probe = null
    probing = false
    scanned = false
    discovered = emptyList()
}

LaunchedEffect(scanRequest) {
    if (scanRequest == 0) return@LaunchedEffect
    scanning = true
    scanned = false
    discovered = emptyList()
    try {
        withTimeoutOrNull(SCAN_WINDOW_MS) {
            container.discovery.discover().collect { printer ->
                if (discovered.none { it.host == printer.host && it.port == printer.port }) {
                    discovered = discovered + printer
                }
            }
        }
    } finally {
        scanning = false
        scanned = true
    }
}

DisposableEffect(Unit) {
    onDispose { probeJob?.cancel() }
}

BoxWithConstraints(modifier.fillMaxSize()) {
    val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
    val uDp = grid.uDp
    val previewPort = port.trim().toIntOrNull()?.takeIf { it in 1..65535 } ?: 7125
    val urls = runCatching {
        buildConnectionUrls(
            host = host.trim().ifBlank { "host" },
            port = previewPort,
            advancedUrl = advancedUrl.ifBlank { null },
            useSecure = false,
        )
    }.getOrElse { ConnectionUrls(httpBase = "", wsUrl = "") }

    // ScreenScaffold from Step 2 goes here.
}
```

- [ ] **Step 1: Define the row enum**

```kotlin
private enum class ConnRow { Name, Host, Port, ApiKey, Find, Advanced }
```

- [ ] **Step 2: Build the Field — selectable rows + foot bar**

```kotlin
ScreenScaffold(
    focus = {
        ConnFocus(
            profile = profile,
            selected = selected,
            urls = urls,
            probe = probe,
            probing = probing,
            name = name,
            host = host,
            port = port,
            apiKey = apiKey,
            advancedUrl = advancedUrl,
            hostError = hostError,
            keyAlreadySaved = keyAlreadySaved,
            keyCleared = keyCleared,
            discovered = discovered,
            scanning = scanning,
            uDp = uDp,
            isPrinting = isPrinting,
            estop = estop,
            onName = { name = it },
            onHost = { host = it; hostError = null },
            onPort = { port = it.filter(Char::isDigit); portError = false },
            onApiKey = { apiKey = it },
            onAdvancedUrl = { advancedUrl = it },
            onClearKey = {
                apiKey = ""
                keyAlreadySaved = false
                keyCleared = true
            },
            onScan = { if (!scanning) scanRequest++ },
            onPick = {
                host = it.host
                port = it.port.toString()
                hostError = null
                portError = false
                selected = null
            },
            onCommitHost = {
                when (val normalized = normalizeHost(host)) {
                    is HostResult.Clean -> {
                        host = normalized.host
                        normalized.portOverride?.let { port = it.toString() }
                        hostError = null
                        selected = null
                    }
                    is HostResult.Rejected -> hostError = normalized.reason
                }
            },
            onDone = { selected = null },
        )
    },
    field = {
        ListBlock(modifier = Modifier.weight(1f)) {
            item {
                ConnListRow(ConnRow.Name, DinghyIcons.TextFields, stringResource(R.string.conn_row_name), name.ifBlank { stringResource(R.string.conn_row_name) }, selected, uDp) {
                    selected = ConnRow.Name
                }
            }
            item {
                ConnListRow(ConnRow.Host, DinghyIcons.DeveloperBoard, stringResource(R.string.conn_row_host), host.ifBlank { "-" }, selected, uDp) {
                    selected = ConnRow.Host
                }
            }
            item {
                ConnListRow(ConnRow.Port, DinghyIcons.Numbers, stringResource(R.string.conn_row_port), port, selected, uDp) {
                    selected = ConnRow.Port
                }
            }
            item {
                ConnListRow(
                    ConnRow.ApiKey,
                    DinghyIcons.VpnKey,
                    stringResource(R.string.conn_row_apikey),
                    if (keyAlreadySaved && !keyCleared) stringResource(R.string.conn_apikey_set) else stringResource(R.string.conn_apikey_unset),
                    selected,
                    uDp,
                ) {
                    selected = ConnRow.ApiKey
                }
            }
            item {
                ConnListRow(ConnRow.Find, DinghyIcons.Search, stringResource(R.string.conn_row_find), "", selected, uDp) {
                    selected = ConnRow.Find
                }
            }
            item {
                ConnListRow(ConnRow.Advanced, DinghyIcons.Tune, stringResource(R.string.conn_row_advanced), advancedUrl.ifBlank { "-" }, selected, uDp) {
                    selected = ConnRow.Advanced
                }
            }
        }
        FootButtonBar(uDp = grid.uDp, actions = footActions)
    },
)
```

Add the row helper:

```kotlin
@Composable
private fun ConnListRow(
    row: ConnRow,
    icon: DinghyIcon,
    label: String,
    value: String,
    selected: ConnRow?,
    uDp: Dp,
    onClick: () -> Unit,
) {
    val t = LocalTokens.current
    ListRow(
        selected = selected == row,
        onClick = onClick,
        uDp = uDp,
        leadingContent = { ListRowIcon(icon, uDp, t.text) },
        trailingContent = {
            Text(
                text = value,
                color = t.text2,
                style = DinghyType.caption.toTextStyle(t),
                maxLines = 1,
                modifier = Modifier.basicMarquee(),
            )
        },
    ) {
        Text(
            text = label,
            color = t.text,
            style = DinghyType.listLabel.toTextStyle(t),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
        )
    }
}
```

- [ ] **Step 3: Build the Focus — summary at rest, editor when a row is selected**

```kotlin
@Composable
private fun ConnFocus(
    profile: Profile?,
    selected: ConnRow?,
    urls: ConnectionUrls,
    probe: ProbeResult?,
    probing: Boolean,
    name: String,
    host: String,
    port: String,
    apiKey: String,
    advancedUrl: String,
    hostError: String?,
    keyAlreadySaved: Boolean,
    keyCleared: Boolean,
    discovered: List<DiscoveredPrinter>,
    scanning: Boolean,
    uDp: Dp,
    isPrinting: Boolean,
    estop: () -> Unit,
    onName: (String) -> Unit,
    onHost: (String) -> Unit,
    onPort: (String) -> Unit,
    onApiKey: (String) -> Unit,
    onAdvancedUrl: (String) -> Unit,
    onClearKey: () -> Unit,
    onScan: () -> Unit,
    onPick: (DiscoveredPrinter) -> Unit,
    onCommitHost: () -> Unit,
    onDone: () -> Unit,
) {
    FocusFrame(
        title = profile?.let { stringResource(R.string.conn_title_edit, it.displayName()) }
            ?: stringResource(R.string.conn_title_add),
        icon = DinghyIcons.AndroidWifi3BarPlus,
        uDp = uDp,
        modifier = Modifier.fillMaxSize(),
        isPrinting = isPrinting,
        onEmergencyStop = estop,
        onPanic = estop,
    ) {
        when (selected) {
            null -> ConnSummary(urls = urls, probe = probe, probing = probing, uDp = uDp)
            ConnRow.Name -> ConnTextEditor(
                value = name,
                onChange = onName,
                label = stringResource(R.string.conn_row_name),
                keyboard = KeyboardType.Text,
                onDone = onDone,
            )
            ConnRow.Host -> ConnTextEditor(
                value = host,
                onChange = onHost,
                label = stringResource(R.string.conn_row_host),
                keyboard = KeyboardType.Text,
                warning = hostError ?: if (host.trim().endsWith(".local")) stringResource(R.string.conn_local_warning) else null,
                isError = hostError != null,
                onDone = onCommitHost,
            )
            ConnRow.Port -> ConnTextEditor(
                value = port,
                onChange = onPort,
                label = stringResource(R.string.conn_row_port),
                keyboard = KeyboardType.Number,
                onDone = onDone,
            )
            ConnRow.ApiKey -> ConnTextEditor(
                value = apiKey,
                onChange = onApiKey,
                label = stringResource(R.string.conn_row_apikey),
                keyboard = KeyboardType.Password,
                isPassword = true,
                warning = if (keyAlreadySaved && !keyCleared && apiKey.isBlank()) stringResource(R.string.conn_apikey_set) else stringResource(R.string.conn_apikey_hint),
                secondaryAction = if (keyAlreadySaved && !keyCleared) {
                    { OutlinedControl(label = stringResource(R.string.printers_clear_key), onClick = onClearKey, intent = Intent.Danger, modifier = Modifier.fillMaxWidth()) }
                } else {
                    null
                },
                onDone = onDone,
            )
            ConnRow.Advanced -> ConnTextEditor(
                value = advancedUrl,
                onChange = onAdvancedUrl,
                label = stringResource(R.string.conn_row_advanced),
                keyboard = KeyboardType.Uri,
                onDone = onDone,
            )
            ConnRow.Find -> ConnFindPanel(
                discovered = discovered,
                scanning = scanning,
                uDp = uDp,
                onScan = onScan,
                onPick = onPick,
            )
        }
    }
}
```

Add the editor helpers:

```kotlin
@Composable
private fun ConnTextEditor(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    keyboard: KeyboardType,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    warning: String? = null,
    isError: Boolean = false,
    secondaryAction: (@Composable () -> Unit)? = null,
    onDone: () -> Unit,
) {
    val t = LocalTokens.current
    Column(modifier.fillMaxSize()) {
        TokenTextField(
            value = value,
            onValueChange = onChange,
            label = label,
            keyboardType = keyboard,
            isPassword = isPassword,
            isError = isError,
            modifier = Modifier.fillMaxWidth(),
        )
        if (warning != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = warning,
                color = if (isError) t.stop else t.text2,
                style = DinghyType.caption.toTextStyle(t),
            )
        }
        secondaryAction?.let {
            Spacer(Modifier.height(8.dp))
            it()
        }
        Spacer(Modifier.weight(1f))
        OutlinedControl(
            label = stringResource(R.string.common_done),
            onClick = onDone,
            icon = DinghyIcons.CheckCircle,
            intent = Intent.Go,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConnFindPanel(
    discovered: List<DiscoveredPrinter>,
    scanning: Boolean,
    uDp: Dp,
    onScan: () -> Unit,
    onPick: (DiscoveredPrinter) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        OutlinedControl(
            label = if (scanning) stringResource(R.string.printers_scanning) else stringResource(R.string.conn_scan),
            onClick = onScan,
            intent = Intent.Accent,
            enabled = !scanning,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (discovered.isEmpty()) {
            Text(
                text = stringResource(R.string.conn_scan_empty),
                color = LocalTokens.current.text2,
                style = DinghyType.caption.toTextStyle(LocalTokens.current),
            )
        } else {
            ListBlock(modifier = Modifier.weight(1f)) {
                items(discovered, key = { "${it.host}:${it.port}" }) { printer ->
                    ConnListRow(
                        row = ConnRow.Find,
                        icon = DinghyIcons.DeveloperBoard,
                        label = printer.host,
                        value = printer.port.toString(),
                        selected = null,
                        uDp = uDp,
                    ) {
                        onPick(printer)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: `ConnSummary` — endpoint preview + Test results**

```kotlin
@Composable
private fun ConnSummary(
    urls: ConnectionUrls,
    probe: ProbeResult?,
    probing: Boolean,
    uDp: Dp,
) {
    val t = LocalTokens.current
    Column(Modifier.fillMaxSize()) {
        Text(
            text = urls.wsUrl.ifBlank { "-" },
            color = t.text,
            style = DinghyType.dataMeta.toTextStyle(t),
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
        )
        Spacer(Modifier.height(12.dp))
        when {
            probing -> Text(text = stringResource(R.string.conn_test), color = t.text2, style = DinghyType.caption.toTextStyle(t))
            probe == null -> Text(text = stringResource(R.string.conn_test_untested), color = t.text2, style = DinghyType.caption.toTextStyle(t))
            else -> {
                ProbeLine(stringResource(R.string.conn_test_http), probe.http.ok, probe.http.failure, uDp)
                ProbeLine(stringResource(R.string.conn_test_ws), probe.ws.ok, probe.ws.failure, uDp)
            }
        }
    }
}

@Composable
private fun ProbeLine(label: String, ok: Boolean, failure: ProbeFailure?, uDp: Dp) {
    val t = LocalTokens.current
    Row(
        modifier = Modifier.fillMaxWidth().height(uDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DinghyIconView(
            icon = if (ok) DinghyIcons.CheckCircle else DinghyIcons.XCircle,
            tint = if (ok) t.go else t.stop,
            sizeDp = uDp * 0.5f,
            contentDescription = label,
        )
        Text(
            text = if (ok) label else "$label: ${stringResource(failure.toMessageRes())}",
            color = if (ok) t.text else t.stop,
            style = DinghyType.caption.toTextStyle(t),
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp).basicMarquee(),
        )
    }
}

private fun ProbeFailure?.toMessageRes(): Int = when (this) {
    ProbeFailure.Timeout -> R.string.conn_fail_timeout
    ProbeFailure.Refused -> R.string.conn_fail_refused
    ProbeFailure.Unauthorized -> R.string.conn_fail_unauthorized
    ProbeFailure.Certificate -> R.string.conn_fail_cert
    ProbeFailure.Unknown, null -> R.string.conn_fail_unknown
}
```

- [ ] **Step 5: Foot actions — Back (contextual) · Test · Save**

Define this `footActions` value inside the `BoxWithConstraints` block, after `urls` and before the
`ScreenScaffold(...)` call from Step 2:

```kotlin
val failedProbe = probe?.let { !it.http.ok || !it.ws.ok } == true
val saveLabel = if (failedProbe) stringResource(R.string.conn_save_anyway) else stringResource(R.string.common_save)
val footActions = listOf(
    FootAction(
        label = back,
        icon = DinghyIcons.Back,
        onClick = {
            when {
                selected != null -> selected = null
                else -> onDone()
            }
        },
        intent = Intent.Accent,
        contentDescription = stringResource(R.string.cd_back),
    ),
    FootAction(
        label = stringResource(R.string.conn_test),
        icon = DinghyIcons.NetworkPing,
        onClick = {
            val portInt = port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
            portError = portInt == null
            when (val normalized = normalizeHost(host)) {
                is HostResult.Rejected -> hostError = normalized.reason
                is HostResult.Clean -> if (portInt != null) {
                    host = normalized.host
                    normalized.portOverride?.let { port = it.toString() }
                    hostError = null
                    probing = true
                    probeJob?.cancel()
                    val config = ConnectionConfig(
                        host = normalized.host,
                        port = normalized.portOverride ?: portInt,
                        apiKey = resolveEditorKeyOnSave(profile?.apiKey, keyCleared, apiKey),
                        useSecure = false,
                        advancedUrl = advancedUrl.ifBlank { null },
                    )
                    probeJob = container.runConnectionProbe(config) { result ->
                        probe = result
                        probing = false
                        probeJob = null
                    }
                }
            }
        },
        intent = Intent.Accent,
        enabled = host.isNotBlank() && !probing,
    ),
    FootAction(
        label = saveLabel,
        icon = DinghyIcons.CheckCircle,
        onClick = {
            val portInt = port.trim().toIntOrNull()?.takeIf { it in 1..65535 }
            portError = portInt == null
            when (val normalized = normalizeHost(host)) {
                is HostResult.Rejected -> hostError = normalized.reason
                is HostResult.Clean -> if (portInt != null) {
                    host = normalized.host
                    normalized.portOverride?.let { port = it.toString() }
                    val saved = buildProfileFromConnectionEditorSave(
                        existing = profile,
                        nameInput = name,
                        host = normalized.host,
                        port = normalized.portOverride ?: portInt,
                        apiKeyInput = apiKey,
                        keyCleared = keyCleared,
                        advancedUrlInput = advancedUrl,
                    )
                    container.saveProfile(saved)
                    apiKey = ""
                    onDone()
                }
            }
        },
        intent = Intent.Go,
    ),
)
```

Save is intentionally always enabled. After a failed probe, only the label changes from
`common_save` to `conn_save_anyway`; the save path is identical and still validates Host/Port.

- [ ] **Step 6: Save wiring and deleted secure toggle**

Use `buildProfileFromConnectionEditorSave(...)` from Task 7 for both new and edit saves. Do not
inline `Profile.copy(...)` in the button. Delete `SecureToggleRow`, delete `useSecure` state, and keep
`useSecure = false` in every constructed `ConnectionConfig`/`Profile`.

Host commit and Save both call `normalizeHost(host)`. On `HostResult.Clean`, write `host =
normalized.host` and, when `normalized.portOverride != null`, write `port =
normalized.portOverride.toString()`. On `Rejected`, keep the user text and show the rejection reason in
the Host editor.

- [ ] **Step 7: Build + install on flox AND moto, owner UAT**

Run (force rebuild — `dinghy-stale-apk-uat-gate`):
`cmd.exe /c "E:\Android\gw.bat :app:assembleDebug --rerun-tasks --no-daemon"` then install BOTH ABI
slices. Owner verifies: tap each row → editor; Host/Port edit; entering `192.168.1.50:7130` moves
`7130` into Port; entering `[::1]:7125` preserves the bracketed host and moves the port; pasted
`https://host/path` is rejected in Host; Find→Scan→pick fills host/port; Test → endpoint preview +
pass/fail (against the real Ender 5 @ 192.168.1.120:7125 and a deliberately wrong host → labeled
failure, no hang); failed probe relabels Save as "Save anyway"; Save persists; reconnect works.

- [ ] **Step 8: Commit**
```bash
git add app/src/main/java/works/mees/dinghy/ui/screen/PrinterConnectionEditor.kt
git commit -m "feat(connection): Focus/Field tap-row-to-edit editor + Test probe + scheme-derive"
```

---

## Task 9: Relax the keyboard law in the design docs

**Files:**
- Modify: `docs/ui_design/THEMING.md`, root `CLAUDE.md` (the UI-law keyboard bullet).

- [ ] **Step 1:** Replace the "No alphanumeric keyboard in printer controls (Settings + Save-name are
  the exceptions)" wording with: "The keyboard is used **sparingly, where text entry is the honest
  input** (host/name/API key/Advanced URL, Settings, Save-name). Numeric adjust still prefers the
  stepper/scrubber." Keep the rest of the law intact.
- [ ] **Step 2: Commit**
```bash
git add docs/ui_design/THEMING.md CLAUDE.md
git commit -m "docs(ui): relax keyboard law — keyboard used sparingly where text is honest input"
```

---

## Self-Review

**Spec coverage:**
- Field tap-row-to-edit (Name/Host/Port/API key/Find/Advanced) → Task 8. ✓
- Focus summary + endpoint preview + Test results → Task 8 Steps 3/4. ✓
- Test probe (dual, auth-exercising, classified, Save-anyway) → Tasks 3, 4, 8. ✓
- Host normalization (scheme/slash/userInfo/IPv6/path) → Task 2. ✓
- Scheme derived, secure toggle deleted → Task 2 (builder) + Task 7 migration + Task 8 Step 6. ✓
- `.local` warning → Task 8 Step 3. ✓
- Profile `advancedUrl` + non-destructive secure migration → Tasks 1, 2, and 7. ✓
- E-stop unchanged (FocusFrame `isPrinting`/`onEmergencyStop` passthrough) → Task 8 Step 0/3. ✓
- Keyboard-law relaxation → Task 9. ✓
- Icons by owner only → Task 5 gate. ✓

**Placeholder scan:** Zero `/*owner*/` markers remain — all owner-assigned glyphs are inlined in
Task 8 (`AndroidWifi3BarPlus` header, `TextFields`, `DeveloperBoard`, `Numbers`, `VpnKey`, `Search`,
`Tune`, `NetworkPing`, `CheckCircle` reuse, `XCircle`), plus `DeveloperBoard` reused for
discovered-printer rows. Task 5 Step 2/3 still registers the new vals + verifies ligatures before the
UI task builds. Logic tasks 1–4 and 7 carry complete test/implementation code.

**Type consistency:** `ConnectionUrls`, `HostResult`, `ProbeFailure`, `TransportResult`,
`ProbeResult`, `ConnRow` names are used identically across tasks. `buildConnectionUrls(host, port,
advancedUrl, useSecure)` signature is consistent in Tasks 2, 4, and 8. The probe API name is
`runConnectionProbe(config, onResult): Job` in both Tasks 4 and 8.

**Out of scope (this plan):** System Info device browser (its own plan); remote tunnels; QR key
import; the deferred e-stop-in-modal gap.

## Execution Handoff

After Codex review + owner icon assignments, dispatch per task via
superpowers:subagent-driven-development.
