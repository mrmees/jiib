package works.mees.jiib.systeminfo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.ObjectSubsetArgs
import works.mees.jiib.di.SpineHandle

/**
 * Toolkit-agnostic holder for the System Information page (SYS-01/02/03), off the printer hot path
 * (Open Q2 resolved: host CPU telemetry is NOT Klipper printer-state, so it lives in a dedicated
 * holder, not [works.mees.jiib.state.PrinterStateStore]). Plain Kotlin, no Compose — host-unit-testable
 * (ADR-0001), mirroring [works.mees.jiib.outputs.OutputsHolder] in shape (scope.launch collect,
 * MutableStateFlow + asStateFlow) but READ-ONLY: no markPending / reached / timeout machinery (the page
 * dispatches nothing back).
 *
 * Three flows feed the two data planes the research mandates:
 *  - [identity] — static host identity from the one-shot `machine.system_info` query (forwarded off
 *    [SpineHandle.systemInfo]). The host LABEL fallback (Open Q1: `cpu_info.model` blank → distro name)
 *    is a DISPLAY choice the UI applies via `identity.model ?: identity.distroName`; both fields are
 *    carried here so the page never reaches past the holder.
 *  - [procStats] — throttle + uptime from the one-shot `machine.proc_stats` query (off
 *    [SpineHandle.procStatQuery]). The 1 Hz push OMITS both, so this is their only source.
 *  - [live] — the per-tick resource sample (cpu%/mem/temp) from the free ~1 Hz `notify_proc_stat_update`
 *    push, parsed via [ProcStatLive.fromPush]. The push is already at/above the store's 250ms floor, so
 *    no extra throttle is needed.
 *
 * @param scope the lifecycle scope the live collect runs on (the service supplies its session scope).
 * @param spine the active session handle; the holder forwards its one-shot StateFlows directly.
 * @param procStatUpdates the JsonRpcClient's raw proc-stat push flow (params[0] JsonObject).
 */
class SystemInfoHolder(
    scope: CoroutineScope,
    private val spine: SpineHandle,
    procStatUpdates: SharedFlow<JsonObject>,
) {
    /** Static host identity (off the one-shot `machine.system_info` query). Null until the read lands. */
    val identity: StateFlow<SystemInfo?> = spine.systemInfo

    /** Throttle + uptime (off the one-shot `machine.proc_stats` query). Null until the read lands. */
    val procStats: StateFlow<ProcStatQuery?> = spine.procStatQuery

    private val _live = MutableStateFlow<ProcStatLive?>(null)
    /** The latest ~1 Hz live resource sample (cpu%/mem/temp), parsed from the push. Null until the first frame. */
    val live: StateFlow<ProcStatLive?> = _live.asStateFlow()

    private val loader = SystemInfoLoader(
        queryObjects = { names -> spine.dispatcher.query(CommandRegistry.objectsQuery, ObjectSubsetArgs(names)) },
        queryPrinterInfo = { spine.dispatcher.query(CommandRegistry.printerInfo, Unit) },
        queryServerInfo = { spine.dispatcher.query(CommandRegistry.serverInfo, Unit) },
    )

    private val _mcuDevices = MutableStateFlow<List<McuDevice>?>(null)
    /** Enumerated MCUs + per-MCU detail. Null until [ensureLoaded] runs (lazy — screen-mount only). */
    val mcuDevices: StateFlow<List<McuDevice>?> = _mcuDevices.asStateFlow()

    private val _klipperVersion = MutableStateFlow<String?>(null)
    val klipperVersion: StateFlow<String?> = _klipperVersion.asStateFlow()

    private val _moonrakerVersion = MutableStateFlow<String?>(null)
    val moonrakerVersion: StateFlow<String?> = _moonrakerVersion.asStateFlow()

    private val loadMutex = Mutex()
    private var versionsLoaded = false

    /**
     * Idempotent + retry-safe: fetch versions ONCE; load MCUs whenever they aren't loaded yet AND
     * capabilities have populated. An early call (before the handshake fills `capabilities.objects`)
     * must NOT permanently cache an empty list — so the MCU load is gated on non-empty caps and only
     * sets [_mcuDevices] on a non-null result, leaving null (= "retry me") otherwise (Codex). The
     * screen retries while [mcuDevices] is null (Task 9).
     */
    suspend fun ensureLoaded() {
        loadMutex.withLock {
            if (!versionsLoaded) {
                versionsLoaded = true
                runCatching {
                    val v = loader.loadVersions()
                    _klipperVersion.value = v.klipper
                    _moonrakerVersion.value = v.moonraker
                }
            }
            if (_mcuDevices.value == null) {
                val objs = spine.capabilities.value.objects
                if (objs.isNotEmpty()) {
                    runCatching { _mcuDevices.value = loader.loadMcuDevices(objs) }
                }
            }
        }
    }

    /** Re-query MCU last_stats (screen-scoped poll). No-op until [ensureLoaded] has populated devices. */
    suspend fun refreshMcuStats() {
        val current = _mcuDevices.value ?: return
        runCatching { _mcuDevices.value = loader.refreshStats(spine.capabilities.value.objects, current) }
    }

    // The 1 Hz push collector runs on the process-lifetime serviceScope, so unlike the other
    // serviceScope holders it is rebuilt on every reconnect/profile-switch — without a cancel handle
    // each rebuild would leak one idle collector. Retain the Job and expose [cancel] so the service
    // can tear down the PRIOR holder before publishing the new one (WR-01).
    private val collectorJob: Job = scope.launch {
        procStatUpdates.collect { push ->
            _live.value = ProcStatLive.fromPush(push)
        }
    }

    /** Cancel the 1 Hz live collector. Called by the service on session teardown / before republish. */
    fun cancel() {
        collectorJob.cancel()
    }
}
