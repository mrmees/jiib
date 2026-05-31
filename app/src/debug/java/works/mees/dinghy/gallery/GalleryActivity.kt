package works.mees.dinghy.gallery

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import works.mees.dinghy.state.PrinterStateStore
import works.mees.dinghy.theme.ThemePrefs
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.theme.compose.DinghyTheme

/**
 * The DEBUG-ONLY gallery launcher Activity (D-08) — registered by `app/src/debug/AndroidManifest.xml`
 * so it exists in the debug variant ONLY and is provably ABSENT from the release APK
 * (tools/check-release-no-gallery.sh). It mirrors [works.mees.dinghy.MainActivity]'s `setContent`
 * host shape, swapping the body for [GalleryScreen] inside the single [DinghyTheme] boundary.
 *
 * ## Sole assembler of GalleryScreen's dependencies
 * GalleryScreen constructs NONE of its deps; this Activity is the ONE place that assembles them:
 *  - a [ThemeResolver] seeded from [ThemePrefs.DEFAULT] (Dark / no overrides / M) — the gallery's
 *    own theme controls then drive it live;
 *  - a [PrinterStateStore] passed as the NULLABLE live spine (D-14). It is constructed here with the
 *    [lifecycleScope] for its internal sampler. CRUCIALLY this opens NO Moonraker connection — no
 *    socket, no transport, no app-bootstrap. It is an empty in-process store whose StateFlow the
 *    gallery may read; on real hardware a future host could seed it, but the gallery never owns the
 *    connection lifecycle (that is Phase 4). The "Live" feed source therefore shows the store's
 *    current (default/empty) readings — proving the consume-an-injected-store seam end-to-end without
 *    crossing the Phase-4 boundary;
 *  - the feed-source selector state ([FeedSource]), hoisted here and passed down with its setter.
 *
 * [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON] is set (the bench idiom) so the gallery stays
 * awake through the on-device perf run (plan 03-07) and the manual sign-off.
 */
class GalleryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Stay awake for the on-device perf run + manual matrix sign-off (bench idiom).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // SOLE ASSEMBLER: build the deps the gallery only consumes.
        val resolver = ThemeResolver(
            base = ThemePrefs.DEFAULT.base,
            deltas = ThemePrefs.DEFAULT.deltas,
            fs = ThemePrefs.DEFAULT.fs,
        )
        // The live spine (D-14) — an in-process store with NO connection opened. The gallery reads
        // its StateFlow only; it never constructs a socket/transport here (Phase-4 boundary).
        val printerStateStore = PrinterStateStore(scope = lifecycleScope)

        setContent {
            DinghyTheme(resolver) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Feed-source selection is hoisted to the host (the sole assembler).
                    var feedSource by remember { mutableStateOf(FeedSource.SYNTHETIC) }
                    GalleryScreen(
                        resolver = resolver,
                        printerStateStore = printerStateStore,
                        feedSource = feedSource,
                        onFeedSourceChange = { feedSource = it },
                    )
                }
            }
        }
    }
}
