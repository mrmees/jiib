package works.mees.dinghy.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import works.mees.dinghy.auth.AuthException
import works.mees.dinghy.auth.MoonrakerAuth
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.IdentifyArgs
import works.mees.dinghy.command.ObjectSubsetArgs
import works.mees.dinghy.command.request
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrinterStateStore
import works.mees.dinghy.state.deriveCapabilities
import works.mees.dinghy.state.deriveSubscribeSet
import works.mees.dinghy.state.parseTemperatureStore
import works.mees.dinghy.state.reduceSnapshot
import kotlin.random.Random
import kotlin.time.Duration

/**
 * The reconnect supervisor + resync handshake — the spine that turns the four seams into a live, resilient
 * connection (CONN-03/04/06, STATE-02, D-01/02/03/04, review HIGH #2/#3, review MEDIUM gentle auth).
 *
 * Supervisor (D-01/D-02): a structured-concurrency loop that maintains the connection forever for
 * network failures, with OVERFLOW-SAFE uncapped backoff+jitter ([backoffDelay]); the attempt counter
 * resets to 0 on a successful connect; [requestReconnectNow] cancels the pending backoff delay and fires
 * an immediate attempt. On an AuthRequired result the loop enters a GENTLE QUIESCENT state — it does NOT
 * churn token-fetches against a bad key — and resumes only on [requestReconnectNow] / config change.
 *
 * Per (re)connect, the handshake runs EXACTLY ONCE each, IN ORDER (review HIGH #2):
 *   `server.connection.identify` (type "display") → `server.info` (components) →
 *   `printer.objects.list` → `deriveCapabilities`/`deriveSubscribeSet` →
 *   `printer.objects.query(subset)` (seeds + overwrites stale state, D-04) →
 *   `printer.objects.subscribe(subset)`.
 * Only AFTER the subscribe seed lands is [ConnectionState.Connected] emitted (review HIGH #3); the
 * socket-open-but-not-yet-resynced window is [ConnectionState.Syncing]. Capabilities are re-derived and
 * exposed each reconnect (STATE-02).
 *
 * Testability: the socket-opening function is injected ([socketEvents]) so a FakeWebSocket-backed flow can
 * be substituted; backoff uses an injectable [Random]; the whole loop runs deterministically in `runTest`
 * virtual time.
 */
class MoonrakerSession(
    private val store: PrinterStateStore,
    private val rpc: JsonRpcClient,
    private val socketEvents: (token: String?) -> Flow<SocketEvent>,
    private val auth: MoonrakerAuth? = null,
    private val baseWsUrl: String = "",
    private val backoffBase: Duration = DEFAULT_BASE,
    private val rng: Random = Random.Default,
    private val clientName: String = "Dinghy Display",
    private val clientVersion: String = "0.1.0",
    // Moonraker's `server.connection.identify` REQUIRES a non-empty `url` argument alongside
    // client_name/version/type — omitting it returns `{code:400,"No data for argument: url"}`
    // (verified live against Moonraker v0.13). Recorded by Moonraker for its connection list only.
    private val clientUrl: String = "https://mees.works/dinghy-display",
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    /** Always-observable five-state connection lifecycle (CONN-06). */
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val reconnectNow = Channel<Unit>(Channel.CONFLATED)

    // G2/G3 (05-10): after the initial handshake completes, an incoming notify_klippy_ready (Klipper
    // FIRMWARE_RESTART / printer.cfg reload on the SAME still-open socket) drives a FULL re-handshake.
    // [handshakeComplete] gates it so the FIRST klippy_ready arriving as part of the initial connect
    // sequence does NOT duplicate the initial runHandshake(); [rehandshakeMutex] serializes re-runs so a
    // second klippy_ready arriving mid-re-handshake never starts an overlapping handshake. Both are
    // per-attempt: connectAndServe resets handshakeComplete=false at the top of each attempt (a fresh
    // socket re-runs the initial handshake, which re-arms the flag).
    @Volatile
    private var handshakeComplete: Boolean = false
    private val rehandshakeMutex = Mutex()

    /** D-02: cancel the pending backoff delay (or auth quiescence) and fire an immediate attempt. */
    fun requestReconnectNow() {
        reconnectNow.trySend(Unit)
    }

    /**
     * Run the supervisor until [scope][coroutineScope] cancellation. Maintains the connection forever:
     * network failures back off (uncapped, jittered) and retry; an AuthRequired result quiesces until
     * [requestReconnectNow].
     */
    suspend fun run() = coroutineScope {
        var attempt = 0
        while (isActive) {
            emit(ConnectionState.Connecting)

            // Reset the backoff counter ONLY when a connect+handshake actually reaches Connected
            // (a durable connection), NOT on every served-then-died attempt (WR-01). A socket that
            // connects then immediately dies in a loop (flapping AP / Klippy crash-loop) must escalate
            // the backoff like any other failure, not hammer the printer at the base interval forever.
            // NOT runCatching: that catches Throwable incl. CancellationException, mapping a scope
            // cancellation to a Network failure and running markStale/emit/backoff on an already-
            // cancelling job (structured-concurrency anti-pattern). Rethrow cancellation so it
            // propagates and the supervisor stops cleanly (WR-05).
            val result = try {
                connectAndServe(onConnected = { attempt = 0 })
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                ConnectAttempt.Network
            }

            when (result) {
                ConnectAttempt.AuthRequired -> {
                    // Gentle quiescence (review MEDIUM): surface Error(AuthRequired), DO NOT retry-churn.
                    store.markStale(ConnectionState.Error(ConnectionError.AuthRequired))
                    emit(ConnectionState.Error(ConnectionError.AuthRequired))
                    // Park until an explicit reconnect-now (or config change drives a new run()).
                    reconnectNow.receive()
                    attempt = 0
                }
                ConnectAttempt.Served -> {
                    // Socket served then died — retain state stale, back off, retry (D-01/D-03). The
                    // attempt counter was already reset to 0 via onConnected the moment this attempt
                    // reached Connected; here we ESCALATE so a flapping-after-connect socket backs off
                    // progressively instead of hammering at the base interval (WR-01).
                    attempt += 1
                    store.markStale(ConnectionState.Disconnected)
                    emit(ConnectionState.Disconnected)
                    waitBackoffOrTrigger(attempt)
                }
                ConnectAttempt.Network -> {
                    attempt += 1
                    store.markStale(ConnectionState.Disconnected)
                    emit(ConnectionState.Disconnected)
                    waitBackoffOrTrigger(attempt)
                }
            }
        }
    }

    /** Result of one connect attempt (drives the supervisor's backoff/quiescence decision). */
    private enum class ConnectAttempt { Served, Network, AuthRequired }

    /**
     * One connect attempt: optional token fetch → open socket → identify → list → derive → query →
     * subscribe → emit Connected → serve frames until the socket closes. Returns when the socket dies.
     */
    private suspend fun connectAndServe(onConnected: () -> Unit = {}): ConnectAttempt = coroutineScope {
        // Per-attempt re-handshake gate: a fresh socket has not yet run its initial handshake, so an
        // early klippy_ready in the connect sequence must NOT trigger a re-handshake (it would duplicate
        // the initial runHandshake()). Re-armed to true once the initial handshake below completes.
        handshakeComplete = false
        val attemptScope = this
        // Auth: fetch a oneshot token immediately before connect when keyed (5 s TTL / single-use).
        val token: String? = if (auth?.isKeyed == true) {
            try {
                auth.fetchOneshotToken()
            } catch (e: AuthException) {
                return@coroutineScope when (e.reason) {
                    ConnectionError.AuthRequired -> ConnectAttempt.AuthRequired
                    else -> ConnectAttempt.Network
                }
            }
        } else {
            null
        }

        val opened = CompletableDeferred<RpcConnection>()
        val closed = CompletableDeferred<ConnectionError?>()

        // Route the spine's notification flows into the store for the lifetime of this attempt.
        // CR-01: each notification flow is a replay=0 SharedFlow, so a frame tryEmit'd before its
        // collector has actually SUBSCRIBED is lost forever (never replayed to a late subscriber).
        // Gate frame dispatch (the collector below) on all three collectors being live via
        // onSubscription, so no early notify_* — notably the one-shot notify_klippy_ready — can be
        // dropped in the socket-open → subscribers-attached window. (Collapsed the former redundant
        // launch{ …launchIn } double-wrap to a single collect each — WR-03.)
        //
        // G2/G3 (05-10): notify_klippy_ready now drives a FULL re-handshake (re-objects/subscribe +
        // re-run BOTH one-shot reads), not merely a KlippyState routing note. After a Klipper
        // FIRMWARE_RESTART / printer.cfg reload on this still-open socket Klipper emits klippy_ready;
        // without re-running runHandshake() the objects/subscribe registration is lost (temps/positions
        // freeze) and the one-shot config reads (min_extrude_temp, …) go stale. The re-handshake is
        // launched on [attemptScope] (NOT this klippy collector's continuation, and NOT the frame
        // collector) so its blocking rpc.request() calls cannot stall klippy/frame dispatch; it is
        // serialized by [rehandshakeMutex] (no overlapping re-runs) and gated by [handshakeComplete] (no
        // duplicate of the initial handshake on the connect-sequence klippy_ready). A re-handshake
        // failure is best-effort (runCatching) — the prior state stays put; the next klippy_ready or a
        // socket close → full reconnect gets another chance. shutdown/disconnected are UNCHANGED.
        val statusReady = CompletableDeferred<Unit>()
        val klippyReady = CompletableDeferred<Unit>()
        val gcodeReady = CompletableDeferred<Unit>()
        val routing: Job = launch {
            launch { rpc.statusUpdates.onSubscription { statusReady.complete(Unit) }.collect { store.onStatusDiff(it) } }
            launch {
                rpc.klippyEvents.onSubscription { klippyReady.complete(Unit) }.collect { method ->
                    // Always fold into KlippyState (control-plane), as before.
                    store.onKlippyMethod(method)
                    // Only notify_klippy_ready, and only AFTER the initial handshake, re-handshakes.
                    if (method == JsonRpcMethods.NOTIFY_KLIPPY_READY && handshakeComplete) {
                        attemptScope.launch {
                            // Serialize re-runs (a second klippy_ready mid-re-handshake waits, then
                            // re-runs against the freshest config) and keep it non-fatal.
                            rehandshakeMutex.withLock {
                                runCatching { runHandshake() }
                            }
                        }
                    }
                }
            }
            launch { rpc.gcodeResponses.onSubscription { gcodeReady.complete(Unit) }.collect { store.onGcodeLine(it) } }
        }
        // Do NOT dispatch any inbound frame until all three notification subscribers are attached.
        statusReady.await()
        klippyReady.await()
        gcodeReady.await()

        val collector = launch {
            socketEvents(token).collect { event ->
                when (event) {
                    is SocketEvent.Open -> {
                        rpc.bind(event.connection)
                        if (!opened.isCompleted) opened.complete(event.connection)
                    }
                    is SocketEvent.Frame -> rpc.dispatch(event.text)
                    is SocketEvent.Closed -> {
                        if (!closed.isCompleted) closed.complete(event.cause)
                    }
                }
            }
            // Flow completed without an explicit Closed event → treat as closed (network).
            if (!closed.isCompleted) closed.complete(ConnectionError.NetworkUnavailable)
        }

        // Wait for the socket to open (or die before opening).
        select<Unit> {
            opened.onAwait {}
            closed.onAwait {}
        }
        if (closed.isCompleted && !opened.isCompleted) {
            routing.cancelAndJoin()
            collector.cancelAndJoin()
            rpc.close(ConnectionError.NetworkUnavailable)
            return@coroutineScope ConnectAttempt.Network
        }

        emit(ConnectionState.Syncing)

        // ---- Resync handshake (EXACT order, once each) ------------------------------------------
        val handshake = runCatching { runHandshake() }
        handshake.exceptionOrNull()?.let { ex ->
            routing.cancelAndJoin()
            collector.cancelAndJoin()
            val reason = (ex as? RpcError)?.let { classifyIdentifyError(it.code, it.message) }
                ?: (ex as? RpcConnectionException)?.reason
                ?: ConnectionError.NetworkUnavailable
            rpc.close(reason)
            return@coroutineScope if (reason == ConnectionError.AuthRequired) {
                ConnectAttempt.AuthRequired
            } else {
                ConnectAttempt.Network
            }
        }

        // Resync complete + subscribed → ONLY NOW Connected (review HIGH #3). Reaching Connected is
        // the ONLY event that resets the supervisor's backoff counter (WR-01) — a durable connection,
        // not a served-then-died flap.
        // Arm the re-handshake gate (G2/G3): a notify_klippy_ready from HERE ON is a real klippy
        // restart on the live socket and MUST re-run the full handshake. Set BEFORE emit(Connected) so
        // a klippy_ready racing the Connected transition is not dropped from the re-handshake path.
        handshakeComplete = true
        onConnected()
        emit(ConnectionState.Connected)

        // Serve until the socket dies.
        closed.await()
        routing.cancelAndJoin()
        collector.cancelAndJoin()
        rpc.close(ConnectionError.NetworkUnavailable)
        ConnectAttempt.Served
    }

    /** identify → server.info → objects.list → derive → objects.query(subset) → objects.subscribe(subset). */
    private suspend fun runHandshake() {
        // 1. identify (once, first).
        rpc.request(
            CommandRegistry.identify,
            IdentifyArgs(
                clientName = clientName,
                version = clientVersion,
                url = clientUrl,
                apiKey = auth?.xApiKeyHeader(),
            ),
        )

        // 2. server.info → live Moonraker component names for ComponentPresent predicates (D-05).
        val components = parseComponents(rpc.request(CommandRegistry.serverInfo, Unit))

        // 3. objects.list → the object-name capability source.
        val listResult = rpc.request(CommandRegistry.objectsList, Unit)
        val objects = parseObjectsList(listResult)

        // 4. derive capabilities + subscribe set (STATE-02, A3) — re-derived EVERY reconnect.
        val capabilities = deriveCapabilities(objects, components)
        store.setCapabilities(capabilities)
        val subset = deriveSubscribeSet(objects)

        // 5. objects.query(subset) — full snapshot; SEED overwrites stale state (D-04).
        val queryResult = rpc.request(CommandRegistry.objectsQuery, ObjectSubsetArgs(subset))
        val status = parseStatus(queryResult)
        if (status != null) store.seed(reduceSnapshot(status))

        // 6. objects.subscribe(subset) — register for diffs. Its reply IS the at-subscription
        //    snapshot (same {eventtime,status} shape as query, verified live), so SEED FROM IT: it is
        //    the authoritative post-subscribe truth, closing the query→subscribe gap where a change
        //    would otherwise be missed until a later diff touched the same field (CR-02/WR-04, D-04).
        val subResult = rpc.request(CommandRegistry.objectsSubscribe, ObjectSubsetArgs(subset))
        parseStatus(subResult)?.let { store.seed(reduceSnapshot(it)) }

        // 7. One-shot history/config reads (05-03, TEMP-04/EXTR-04). Both NOT subscribed
        //    (config is static; the store is a backfill seed — live points
        //    keep arriving on the existing notify_status_update stream). Each is BEST-EFFORT in its own
        //    runCatching so a printer lacking the endpoint/field never breaks the handshake (it already
        //    reached subscribe above): a failed read leaves the store's StateFlow at its null/empty
        //    default and the panels degrade gracefully (T-05-03-D). Results land on capability-like
        //    StateFlows holders OBSERVE (deterministic on-connect fullness, NOT the throttled hot path).
        runCatching {
            // temperature_store: backfill ONLY the heater sensors the graph draws (capability heaters),
            // by exact object name — ignore pure `temperature_sensor X` entries (RESEARCH §1 alignment).
            val storeResult = rpc.request(CommandRegistry.temperatureStore, Unit)
            val backfill = parseTemperatureStore(storeResult.jsonObject, capabilities.heaters.toSet())
            store.setTemperatureBackfill(backfill)
        }
        runCatching {
            // configfile (one-shot query, NOT live subscribe): static parsed-config numbers from the
            // PRIMARY `extruder` — single-extruder-accurate (the per-tool safety gate stays the live
            // can_extrude boolean, not these numbers).
            val cfgResult = rpc.request(CommandRegistry.objectsQuery, ObjectSubsetArgs(setOf("configfile")))
            val extruderCfg = parseStatus(cfgResult)
                ?.objectOrNull("configfile")
                ?.objectOrNull("settings")
                ?.objectOrNull("extruder")
            store.setMinExtrudeTemp(extruderCfg?.floatOrNullAt("min_extrude_temp"))
            store.setMaxExtrudeDistance(extruderCfg?.floatOrNullAt("max_extrude_only_distance"))
        }
    }

    private fun parseObjectsList(result: kotlinx.serialization.json.JsonElement): List<String> =
        runCatching {
            // Guard PER ELEMENT (mapNotNull), not the whole map: one non-string/non-primitive entry
            // (a nested object, a JSON null) must be skipped, NOT collapse the entire objects list to
            // empty → empty Capabilities → subscribe to nothing → blank screen. Matches the house rule
            // "a bad field is skipped, never fatal" at the collection level (WR-06 / T-02-04).
            (result.jsonObject["objects"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                ?: emptyList()
        }.getOrDefault(emptyList())

    private fun parseComponents(result: kotlinx.serialization.json.JsonElement): Set<String> =
        runCatching {
            (result.jsonObject["components"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
                ?.toSet()
                ?: emptySet()
        }.getOrDefault(emptySet())

    private fun parseStatus(result: kotlinx.serialization.json.JsonElement): JsonObject? =
        runCatching { result.jsonObject["status"]?.jsonObject }.getOrNull()

    // --- null-safe config walkers (05-03): a missing/garbage field yields null, never `!!` on wire data ---

    private fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.floatOrNullAt(key: String): Float? =
        runCatching { this[key]?.jsonPrimitive?.doubleOrNull?.toFloat() }.getOrNull()

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun waitBackoffOrTrigger(attempt: Int) {
        val backoff = backoffDelay(attempt, backoffBase, rng)
        select<Unit> {
            reconnectNow.onReceive {}
            onTimeout(backoff) {}
        }
    }

    private fun emit(state: ConnectionState) {
        _connectionState.value = state
        store.setConnectionState(state)
    }

    private companion object
}
