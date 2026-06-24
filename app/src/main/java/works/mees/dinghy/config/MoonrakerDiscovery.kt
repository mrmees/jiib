package works.mees.dinghy.config

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * A printer surfaced by an mDNS `_moonraker._tcp` advertisement (D-04). Best-effort UI sugar only —
 * the user explicitly picks/edits one before connecting; discovery NEVER auto-connects (T-04-01-S).
 */
data class DiscoveredPrinter(
    val name: String,
    val host: String,
    val port: Int,
)

/**
 * Best-effort, FULLY LAZY mDNS scan for Moonraker instances advertising `_moonraker._tcp` (D-04,
 * review #5). Manual entry in Settings is ALWAYS the floor — this only ever ADDS candidates; an empty
 * scan is a NORMAL outcome (Moonraker's `[zeroconf]` is opt-in).
 *
 * LAZINESS CONTRACT (review #5): the constructor takes provider lambdas and touches NEITHER — it
 * acquires no [NsdManager], no [WifiManager.MulticastLock], and registers no listener. ALL of that
 * happens only inside [discover]'s flow body on collect, and is torn down on `awaitClose`. So merely
 * holding a [MoonrakerDiscovery] (e.g. in the DI container) costs nothing and pins no radio.
 *
 * RESILIENCE (T-04-01-DoS): no NSD failure path rethrows into the collector — listener error callbacks
 * just stop discovery and let the flow idle/complete; a scan can never crash the Settings screen.
 *
 * This file references NO session / socket / connection-URL type — discovery only surfaces
 * candidates the user selects; it has no connection authority and never auto-connects.
 *
 * @param nsdProvider lazily yields the platform [NsdManager] (e.g. `{ context.getSystemService(...) }`).
 * @param multicastLockProvider lazily yields a held-able [WifiManager.MulticastLock] (often required to
 *   receive mDNS multicast on Wi-Fi); default `{ null }` (no lock — best effort).
 */
class MoonrakerDiscovery(
    private val nsdProvider: () -> NsdManager,
    private val multicastLockProvider: () -> WifiManager.MulticastLock? = { null },
) {
    /**
     * Cold scan for `_moonraker._tcp`. Each collection (on the "Scan" tap) acquires the machinery;
     * cancelling the collector (`awaitClose`) stops discovery and releases the multicast lock. Emits
     * zero-or-more [DiscoveredPrinter]s as services resolve.
     *
     * Resolves are SERIALIZED — the framework's `resolveService` is not safe to call concurrently
     * (overlapping resolves silently fail) — but a service found while another resolve is in flight is
     * QUEUED via [ResolveSerializer], NOT dropped. Dropping was the flaky-discovery bug: with two
     * printers, the second `onServiceFound` (which fires within ms of the first) lost the resolve guard
     * and was discarded forever, so only one printer ever showed. Each distinct service resolves once.
     */
    fun discover(): Flow<DiscoveredPrinter> = callbackFlow {
        // Acquire the machinery ONLY here, on collect (review #5 — nothing at construction).
        val nsdManager = nsdProvider()
        val multicastLock = multicastLockProvider()?.also { runCatching { it.acquire() } }

        // FIFO resolve serializer (one resolve in flight; the rest queued, never dropped).
        val serializer = ResolveSerializer()
        // Found-but-not-yet-resolved service infos, keyed by service name (the serializer's queue key).
        val pendingInfos = HashMap<String, NsdServiceInfo>()
        // Forward-referenced so the resolve listener (which drains the queue) and the drain function
        // can call each other; assigned before any async NSD callback can fire.
        var resolveNext: ((String) -> Unit)? = null

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host?.hostAddress
                if (host != null) {
                    trySend(DiscoveredPrinter(serviceInfo.serviceName, host, serviceInfo.port))
                }
                serializer.onResolveDone()?.let { resolveNext?.invoke(it) }
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Best-effort: a failed resolve is a normal no-op (T-04-01-DoS) — drain the next, no throw.
                serializer.onResolveDone()?.let { resolveNext?.invoke(it) }
            }
        }

        resolveNext = fun(key: String) {
            val info = pendingInfos.remove(key)
            if (info == null) {
                serializer.onResolveDone()?.let { resolveNext?.invoke(it) }
                return
            }
            runCatching { nsdManager.resolveService(info, resolveListener) }
                .onFailure { serializer.onResolveDone()?.let { resolveNext?.invoke(it) } }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                val key = serviceInfo.serviceName
                pendingInfos[key] = serviceInfo
                // Resolve now if idle; otherwise it's queued (NOT dropped) and drained later.
                serializer.onFound(key)?.let { resolveNext?.invoke(it) }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                // We only ever ADD candidates; a lost service is ignored (the user picks from what's shown).
            }

            override fun onDiscoveryStarted(serviceType: String) { /* no-op */ }

            override fun onDiscoveryStopped(serviceType: String) { /* no-op */ }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                // NEVER rethrow (T-04-01-DoS): a failed start is a normal empty scan. Stop and idle.
                runCatching { nsdManager.stopServiceDiscovery(this) }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                // NEVER rethrow: nothing actionable, just swallow.
            }
        }

        runCatching {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        }
        // A failure to even start is a normal empty scan — discovery never blocks manual entry (D-04).

        awaitClose {
            runCatching { nsdManager.stopServiceDiscovery(discoveryListener) }
            multicastLock?.let { runCatching { if (it.isHeld) it.release() } }
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_moonraker._tcp."
    }
}

/**
 * Serializes mDNS resolves (the framework allows only one in flight at a time) WITHOUT losing finds:
 * a service found while a resolve is in flight is queued FIFO and drained as each resolve completes.
 * Each distinct key resolves exactly once per scan (duplicate/re-announced finds are ignored). Pure
 * and host-testable — no Android types. NSD delivers its callbacks serially on one handler thread, so
 * the methods are synchronized purely as cheap insurance against any cross-thread delivery.
 */
internal class ResolveSerializer {
    private val seen = HashSet<String>()
    private val queue = ArrayDeque<String>()
    private var inFlight: String? = null

    /** Register a found service [key]. Returns [key] if it should be resolved NOW (the caller starts
     *  the resolve), or null if it was queued behind an in-flight resolve, or is a duplicate. */
    @Synchronized
    fun onFound(key: String): String? {
        if (!seen.add(key)) return null          // duplicate / re-announced service — never resolve twice
        if (inFlight == null) { inFlight = key; return key }
        queue.addLast(key)
        return null
    }

    /** A resolve finished (success or failure). Returns the next queued key to resolve, or null when
     *  the queue is empty (idle). */
    @Synchronized
    fun onResolveDone(): String? {
        inFlight = queue.removeFirstOrNull()
        return inFlight
    }
}
