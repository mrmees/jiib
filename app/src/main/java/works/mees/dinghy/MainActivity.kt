package works.mees.dinghy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import works.mees.dinghy.service.MoonrakerService
import works.mees.dinghy.theme.compose.DinghyTheme
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.ui.shell.RootController

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
                    // ALL routing is delegated to the single root authority (review #2).
                    RootController(container)
                }
            }
        }
    }
}
