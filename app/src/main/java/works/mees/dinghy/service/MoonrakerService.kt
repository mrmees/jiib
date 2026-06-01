package works.mees.dinghy.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import works.mees.dinghy.DinghyApp
import works.mees.dinghy.MainActivity
import works.mees.dinghy.auth.MoonrakerAuth
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.di.SessionControl
import works.mees.dinghy.di.SpineHandle
import works.mees.dinghy.net.JsonRpcClient
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.net.MoonrakerSession
import works.mees.dinghy.net.MoonrakerSocket
import works.mees.dinghy.net.SocketEvent
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrinterStateStore
import java.util.concurrent.atomic.AtomicLong

/**
 * The started, `specialUse` FOREGROUND SERVICE that OWNS the Moonraker spine (SHELL-03, D-01..D-03).
 * Because the connection belongs to the OS (not the Activity), it survives Activity recreation
 * (rotation), config change, and screen-off. It is STARTED, not bound — [onBind] returns null and all
 * app state is process-held in the [AppContainer] the Activity collects (D-02).
 *
 * ## Config-driven rebuild (D-03, review #6/#12)
 * The service collects [ConnectionStore.config][works.mees.dinghy.config.ConnectionStore.config] via
 * `collectLatest`. On every config emission it `cancelAndJoin()`s the prior session job BEFORE building
 * the new one (no socket/scope leak — the clean teardown seam), then either:
 *  - cfg == null → publishes an idle spine (`publishSpine(null)`) and shows a "set up printer" notice
 *    (review #12 — a cleared config idles the spine, no leaked connection); or
 *  - cfg != null → assembles the real spine with the VERIFIED constructors (DevConfig wsUrl OVERRIDDEN
 *    with cfg.wsUrl; identify `clientUrl` preserved — regressing it re-breaks Connected, see 02-04),
 *    publishes ONE immutable [SpineHandle] with a monotonic [sessionInstanceId] (review #6, atomic), and
 *    launches `session.run()`.
 *
 * The rebuild loop is extracted into [runConfigLoop] (a Nyquist seam) so the cancel-before-relaunch
 * ordering + the monotonic-atomic-publish invariant are JVM-testable in virtual time (MoonrakerServiceTest)
 * with no Android lifecycle or real socket.
 *
 * SECURITY (T-04-03-I): the notification text is driven by [ConnectionState] ONLY — the API key / a
 * `?token=` URL NEVER appears in the notification or a log line.
 */
class MoonrakerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val idCounter = AtomicLong(0)
    private val notificationManager by lazy {
        getSystemService(NOTIFICATION_SERVICE) as NotificationManager
    }

    private val container: AppContainer
        get() = (application as DinghyApp).container

    override fun onBind(intent: Intent?): IBinder? = null // started, NOT bound — state is process-held.

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForegroundCompat(buildNotification("Starting…"))

        serviceScope.launch {
            runConfigLoop(
                configFlow = container.connectionStore.config,
                buildAndPublish = { cfg -> buildSpineAndLaunch(cfg) },
                publishIdle = {
                    container.bindSessionControl(null)
                    container.publishSpine(null)
                    updateNotification("Set up printer")
                },
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Assemble the real spine from [cfg] using the VERIFIED spine constructors, publish ONE atomic
     * [SpineHandle], wire [SessionControl], and launch `session.run()` in [serviceScope]. Returns the
     * launched job + the published [sessionInstanceId] for the rebuild loop.
     */
    private fun buildSpineAndLaunch(cfg: ConnectionConfig): Pair<Job, Long> {
        val client = MoonrakerSocket.defaultClient() // one shared OkHttpClient (ws + REST + auth).
        val store = PrinterStateStore(scope = serviceScope)
        val rpc = JsonRpcClient()
        val auth: MoonrakerAuth? =
            if (cfg.apiKey != null) MoonrakerAuth(client, cfg.httpBase, cfg.apiKey) else null

        // socketEvents OVERRIDES MoonrakerSocket.real's DevConfig.wsUrl default with cfg.wsUrl; on the
        // auth path the token-bearing URL is built via MoonrakerAuth.buildAuthedWsUrl (secret stays redacted).
        val socketEvents: (token: String?) -> Flow<SocketEvent> = { token ->
            val wsUrl =
                if (token != null && auth != null) auth.buildAuthedWsUrl(cfg.wsUrl, token) else cfg.wsUrl
            MoonrakerSocket.real(client, wsUrl = wsUrl).events()
        }

        val session = MoonrakerSession(
            store = store,
            rpc = rpc,
            socketEvents = socketEvents,
            auth = auth,
            baseWsUrl = cfg.wsUrl,
            // clientUrl left at its live-verified default ("https://mees.works/dinghy-display") — DO NOT regress.
        )
        val dispatcher = CommandDispatcher(rpc, serviceScope)

        val id = idCounter.incrementAndGet()
        val handle = SpineHandle(
            printerState = store.printerState,
            connectionState = session.connectionState,
            capabilities = store.capabilities,
            dispatcher = dispatcher,
            store = store, // the UI builds the per-session PrintStatusHolder from this (04-07).
            // One-shot handshake reads (05-03) — forwarded as the store's StateFlows so 05-05/05-07
            // collect them off the live handle (values carry forward to late collectors).
            minExtrudeTemp = store.minExtrudeTemp,
            maxExtrudeDistance = store.maxExtrudeDistance,
            temperatureBackfill = store.temperatureBackfill,
            sessionInstanceId = id,
        )
        // Atomic publication (review #6): the WHOLE handle swaps in one assignment.
        container.publishSpine(handle)
        // Narrow reconnect/restart surface (review #1) — forwards to THIS session/dispatcher.
        container.bindSessionControl(object : SessionControl {
            override fun requestReconnectNow() = session.requestReconnectNow()
            override fun restartFirmware() = dispatcher.dispatch("fw_restart", JsonRpcMethods.FIRMWARE_RESTART)
            override fun restartHost() = dispatcher.dispatch("host_restart", JsonRpcMethods.RESTART)
        })

        // Logged ONCE for the rotation gate (review #3) — id only, never the key/URL.
        Log.i(TAG_SPINE, "sessionInstanceId=$id")

        // Drive the notification text off the connection lifecycle (key-free).
        val statusJob = serviceScope.launch {
            session.connectionState.collect { state -> updateNotification(statusText(state)) }
        }
        val runJob = serviceScope.launch {
            try {
                session.run()
            } finally {
                statusJob.cancel()
            }
        }
        return runJob to id
    }

    // ---- Notification (key-free, T-04-03-I) --------------------------------------------------------

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Printer connection",
                NotificationManager.IMPORTANCE_LOW, // Pitfall 1 — low importance, no sound/peek.
            ).apply { description = "Keeps the Moonraker connection alive in the background." }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dinghy Display")
            .setContentText(text) // ONLY ever a connection/status string — never the API key.
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(tapIntent)
            .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(text: String) {
        notificationManager.notify(NOTIF_ID, buildNotification(text))
    }

    private fun statusText(state: ConnectionState): String = when (state) {
        is ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Syncing -> "Syncing…"
        is ConnectionState.Connected -> "Connected"
        is ConnectionState.Disconnected -> "Disconnected"
        is ConnectionState.Error -> "Connection error"
    }

    companion object {
        private const val CHANNEL_ID = "moonraker_connection"
        private const val NOTIF_ID = 1
        private const val TAG_SPINE = "DinghySpine"

        /**
         * The extracted config-rebuild loop (Nyquist seam). On every [configFlow] emission it
         * `cancelAndJoin()`s the prior session job BEFORE acting on the new config (the clean teardown
         * seam, D-03), then publishes idle (null cfg, review #12) or builds + launches the new session
         * via [buildAndPublish] (which publishes ONE atomic [SpineHandle] and returns its job +
         * monotonic id). `collectLatest` guarantees a rapid config change interrupts an in-progress
         * build, but the explicit `cancelAndJoin` is what guarantees no socket/scope leak per swap.
         *
         * Pure of the Android lifecycle and any real socket so it is JVM-testable in virtual time.
         */
        internal suspend fun runConfigLoop(
            configFlow: Flow<ConnectionConfig?>,
            buildAndPublish: (ConnectionConfig) -> Pair<Job, Long>,
            publishIdle: () -> Unit,
        ) {
            var sessionJob: Job? = null
            configFlow.collectLatest { cfg ->
                // Tear the old session down BEFORE building the new one — no leak (D-03).
                sessionJob?.cancelAndJoin()
                sessionJob = null
                if (cfg == null) {
                    publishIdle()
                    return@collectLatest
                }
                val (job, _) = buildAndPublish(cfg)
                sessionJob = job
            }
        }
    }
}
