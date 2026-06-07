package works.mees.dinghy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.service.MoonrakerService
import works.mees.dinghy.theme.compose.DinghyTheme
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.ui.route.Dest
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
 * Mirrors `gallery/GalleryActivity`'s `setContent { DinghyTheme(resolver){ Surface { … } } }` host
 * shape, swapping the gallery body for [RootController]. No Navigation-Compose.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as DinghyApp).container

        // Start the FGS that owns the spine (SHELL-03/D-01). Idempotent: the started service is
        // START_STICKY and re-delivers cleanly; the spine is process-held in the AppContainer.
        startForegroundService(Intent(this, MoonrakerService::class.java))

        // ---- Dev-gated start_dest deep-jump (SC-4b / D-06, T-18-04-01) ---------------------------------
        // MainActivity is exported="true", so an `am start ... --es start_dest <Dest>` extra crosses an
        // UNTRUSTED boundary. The jump is gated on the APP-GLOBAL devCyclerEnabled DataStore flag (default
        // FALSE → inert in release), NOT BuildConfig.DEBUG. The gate read is a BOUNDED one-shot first-
        // emission read (the safe/inert FALSE on timeout) so onCreate cannot hang on a slow/empty store.
        // Only when the gate is ON do we read + SAFE-parse the extra (parseStartDest → Dest?, never throws
        // on garbage, T-18-04-02). This is a one-shot intent READ — no DataStore WRITE — so the
        // write-scope-cancellation trap does not apply (T-18-04-03). Per D-06 this is THE live path for
        // classic-View perf truth on flox (jump to Temperature/Webcam/BedMesh); no new View harness exists.
        val startDest: Dest? =
            if (container.devCyclerEnabledBlocking()) {
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
                // G-1 hardening: explicit token bg instead of a bare Material3 Surface() (which
                // defaults to the never-populated colorScheme.surface). Production screens each
                // paint t.bg, but this removes the bare-Surface footgun at the root.
                Box(Modifier.fillMaxSize().background(LocalTokens.current.bg)) {
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
