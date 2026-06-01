package works.mees.dinghy.config

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean

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
     * Cold scan for `_moonraker._tcp`. Each collection (on the Settings "Scan" tap) acquires the
     * machinery; cancelling the collector (`awaitClose`) stops discovery and releases the multicast
     * lock. Emits zero-or-more [DiscoveredPrinter]s as services resolve.
     *
     * Resolves are SERIALIZED through a single in-flight guard — pre-API-29 `resolveService` is not
     * safe to call concurrently; overlapping resolves silently fail. Found-while-busy services are
     * dropped (best-effort; the user can re-scan), never queued unboundedly.
     */
    fun discover(): Flow<DiscoveredPrinter> = callbackFlow {
        // Acquire the machinery ONLY here, on collect (review #5 — nothing at construction).
        val nsdManager = nsdProvider()
        val multicastLock = multicastLockProvider()?.also { runCatching { it.acquire() } }

        // Single in-flight resolve guard (pre-API-29 resolveService is not concurrency-safe).
        val resolving = AtomicBoolean(false)

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                resolving.set(false)
                val host = serviceInfo.host?.hostAddress
                if (host != null) {
                    trySend(DiscoveredPrinter(serviceInfo.serviceName, host, serviceInfo.port))
                }
            }

            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                // Best-effort: a failed resolve is a normal no-op (T-04-01-DoS) — free the guard, no throw.
                resolving.set(false)
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                // Serialize resolves; drop a find while another resolve is in flight (best-effort).
                if (resolving.compareAndSet(false, true)) {
                    runCatching { nsdManager.resolveService(serviceInfo, resolveListener) }
                        .onFailure { resolving.set(false) }
                }
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
