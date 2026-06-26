package works.mees.dinghy

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.service.MoonrakerService
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.theme.compose.DinghyTheme
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.SyncSystemBarsToTheme
import works.mees.dinghy.ui.route.NavDest
import works.mees.dinghy.ui.shell.RootController
import works.mees.dinghy.ui.shell.parseStartDest

/**
 * The launcher entry point and the ONE Compose host for the whole app (04-07). It grows up from the
 * Phase-1 scaffold placeholder into the real shell host:
 *
 *  1. resolves the process-scoped [works.mees.dinghy.di.AppContainer] off the [DinghyApp] (D-02);
 *  2. STARTS the foreground service ([MoonrakerService]) that OWNS the Moonraker spine (SHELL-03/D-01)
 *     — the service, not this Activity, owns the connection, so it survives rotation/screen-off;
 *  3. hosts the whole UI in EXACTLY ONE [DinghyTheme] boundary (the single token/`--fs` authority) and
 *     delegates ALL top-level routing to the single [RootController] (review #2). MainActivity itself
 *     does NOT branch on routes/Dest — it composes the one controller and nothing else.
 *
 * The host shape is `setContent { DinghyTheme(resolver) { Surface { … } } }` with [RootController]
 * as the sole body. No Navigation-Compose.
 */
class MainActivity : ComponentActivity() {

    /**
     * R1 (26.5-04, roadmap §R1 step 4): POST_NOTIFICATIONS one-shot launcher. Registered as an
     * Activity FIELD (registerForActivityResult must run during activity initialization, never
     * inside a LaunchedEffect). The result handler is a DOCUMENTED NO-OP: denial changes NOTHING
     * except the FGS status notification — the service itself keeps running notification-less,
     * so the connect → monitor → control loop is untouched either way.
     */
    private val notificationPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* isGranted — deliberate no-op: FGS runs with or without the notification */ }

    /**
     * Once-per-process latch for the notification permission ask (R1 step 4: "exactly once").
     * configChanges handles rotation (no Activity recreation), so an Activity field is
     * process-lifetime in practice; even on a rare recreation the checkSelfPermission gate
     * prevents a re-prompt for an already-granted permission.
     */
    private var notificationPermAsked = false

    /**
     * Launch the POST_NOTIFICATIONS request iff: API >= 33 (the permission does not exist below
     * TIRAMISU — auto-granted there, including the API-23 floor), not already granted, and not
     * already asked this process. Called on the FIRST transition to [ConnectionState.Connected].
     */
    private fun requestNotificationPermissionIfNeeded() {
        if (notificationPermAsked) return
        notificationPermAsked = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // R1 (26.5-04, roadmap §R1 step 2): edge-to-edge MUST be the FIRST statement, before
        // super.onCreate (Pitfall 1 — window decor flags are configured before the view
        // hierarchy attaches). The AndroidX backport no-ops gracefully pre-API-35; on
        // Android 15+ (targetSdk 35) edge-to-edge is forced anyway — this makes it correct.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as DinghyApp).container

        // Start the FGS that owns the spine (SHELL-03/D-01). Idempotent: the started service is
        // START_STICKY and re-delivers cleanly; the spine is process-held in the AppContainer.
        // ContextCompat (26.5-04 codex review): the platform startForegroundService() is API 26+ —
        // the bare call was a latent launch CRASH on genuine API 23-25 devices (the minSdk floor;
        // latent since Phase 4 because flox runs API 30). ContextCompat falls back to
        // startService() pre-26; MoonrakerService promotes itself via startForeground() either way.
        ContextCompat.startForegroundService(this, Intent(this, MoonrakerService::class.java))

        // ---- Dev-gated start_dest deep-jump (SC-4b / D-06, T-18-04-01) ---------------------------------
        // MainActivity is exported="true", so an `am start ... --es start_dest <Dest>` extra crosses an
        // UNTRUSTED boundary. The jump is gated on the APP-GLOBAL devCyclerEnabled DataStore flag (default
        // FALSE → inert in release). The gate read is a BOUNDED one-shot first-emission read (the safe/inert
        // FALSE on timeout) so onCreate cannot hang on a slow/empty store. Only when the gate is ON do we
        // read + SAFE-parse the extra (parseStartDest → Dest?, never throws on garbage, T-18-04-02). This is
        // a one-shot intent READ — no DataStore WRITE — so the write-scope-cancellation trap does not apply
        // (T-18-04-03). Per D-06 this is THE live path for classic-View perf truth on flox (jump to
        // Temperature/Webcam/BedMesh); no new View harness exists.
        //
        // WR-01: the WHOLE gate read is additionally short-circuited behind BuildConfig.DEBUG. The
        // devCyclerEnabled DataStore flag CANNOT be true in a release build (the dev cyclers that flip it
        // are themselves debug-only), so the bounded main-thread first-emission read is pure cold-start cost
        // in release for an answer that is always FALSE. Skipping the read entirely in release means a
        // release cold start pays ZERO main-thread DataStore I/O here and never reads the start_dest extra —
        // STRENGTHENING the release-inert contract (release is inert by construction, not just by flag value).
        // Debug behavior is unchanged: the bounded gate read + safe-parse still runs.
        val startDest: NavDest? =
            if (BuildConfig.DEBUG && container.devCyclerEnabledBlocking()) {
                parseStartDest(intent?.getStringExtra(EXTRA_START_DEST))
            } else {
                null
            }

        setContent {
            // The ONE theme boundary (D-05) — every screen composes inside it and reads LocalTokens.
            // Collects the override-aware effectiveTokens flow (15.2-01 HIGH-1): a transient dev override
            // re-themes the whole app (Compose + Views) without persisting; the persisted path is a PURE
            // bake of the canonical tuple (never the async-lagged themeResolver.tokens).
            DinghyTheme(container.effectiveTokens) {
                // 260611-cj1: system bars follow the ACTIVE tokens, not the system uiMode — the
                // no-arg enableEdgeToEdge() baseline above is refined reactively from here (white
                // nav bar on system-light API 34; live restyle on every theme change).
                SyncSystemBarsToTheme()
                // R1 (26.5-04): first-connect trigger for the POST_NOTIFICATIONS one-shot.
                // Suspends on the FIRST emission of Connected (the spine's post-resync state),
                // then asks exactly once (field latch + grant check inside the helper).
                LaunchedEffect(Unit) {
                    container.connectionState.first { it is ConnectionState.Connected }
                    requestNotificationPermissionIfNeeded()
                }
                // G-1 hardening: explicit token bg instead of a bare Material3 Surface() (which
                // defaults to the never-populated colorScheme.surface). Production screens each
                // paint t.bg, but this removes the bare-Surface footgun at the root.
                // R1 (26.5-04): .background BEFORE the inset padding — the token bg paints
                // edge-to-edge BEHIND the system bars/cutout while the padded content stays
                // chrome-clear. The four Views-hosted surfaces (Files/Console/Graph/Webcam)
                // live inside this root and inherit the insets. Insets resolve to 0 where the
                // hardware lacks bars/cutouts — API-23 safe.
                // safeDrawing EXCLUDING ime: the IME must OVERLAY the app (draw on top), NOT
                // compress the content upward. Screens that need a field lifted above the
                // keyboard opt in locally with Modifier.imePadding().
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LocalTokens.current.bg)
                        .windowInsetsPadding(WindowInsets.safeDrawing.exclude(WindowInsets.ime))
                ) {
                    // ALL routing is delegated to the single root authority (review #2). The dev-gated
                    // [startDest] (null in release / when the gate is off) seeds the initial screen ONCE.
                    RootController(container, startDest = startDest)
                }
            }
        }
    }

    companion object {
        /**
         * Intent extra key for the dev-gated deep-jump (mirrors [works.mees.dinghy.bench.BenchActivity.EXTRA_SCENE]).
         * Honored ONLY when [AppContainer.devCyclerEnabled] is true; safe-parsed via [parseStartDest].
         * Launch: `adb shell am start -n works.mees.dinghy/.MainActivity --es start_dest FineTune`.
         */
        const val EXTRA_START_DEST = "start_dest"
    }
}

/**
 * Read the APP-GLOBAL dev-widget enable flag with a BOUNDED one-shot first-emission (Codex MEDIUM-4).
 * [AppContainer.devCyclerEnabled] is a `Flow<Boolean>` (no synchronous cached accessor), so this does a
 * timeboxed blocking read on the calling thread: DataStore's first read is fast, and the timeout
 * guarantees `onCreate` cannot hang if the store is slow/empty — defaulting to FALSE (the safe/inert
 * value) on timeout or any read failure. A named seam so the bounded-read intent is unmistakable; do NOT
 * replace with an unbounded `.first()`/`runBlocking` without a timeout.
 */
private fun AppContainer.devCyclerEnabledBlocking(): Boolean =
    runBlocking { withTimeoutOrNull(500) { devCyclerEnabled.first() } } ?: false
