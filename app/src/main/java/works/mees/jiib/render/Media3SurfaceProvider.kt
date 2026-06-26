package works.mees.jiib.render

import android.view.SurfaceView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

/**
 * The shared bridge between the H.264 player FEED (which runs in [works.mees.jiib.ui.webcam.WebcamHolder],
 * built in the shell before the screen composes) and the [Media3SurfaceHost] (which creates the raw
 * [SurfaceView] when the Webcam page composes).
 *
 * The composite Media3 feed cannot create its own SurfaceView — a SurfaceView must live in the view
 * hierarchy to render, and that hierarchy is owned by the Compose screen via `AndroidView`. So the host
 * REGISTERS its SurfaceView here on factory/dispose, and the feed AWAITS it ([awaitSurface]) before
 * attaching the player. When the host disposes (nav-away / screen exit) it [clear]s the registration so a
 * stale surface is never re-attached (part of the WR-01 leak-free discipline — the player's surface is
 * nulled BEFORE release in the feed, and the host clears the registration here).
 *
 * One provider per holder (re-keyed with the holder in the shell). Thread-safe via a [MutableStateFlow].
 */
class Media3SurfaceProvider {
    private val _surface = MutableStateFlow<SurfaceView?>(null)

    /** The currently-registered SurfaceView (or null when the host is not composed). */
    val surface: StateFlow<SurfaceView?> = _surface.asStateFlow()

    /** [Media3SurfaceHost] registers its SurfaceView here on `AndroidView` factory. */
    fun register(view: SurfaceView) {
        _surface.value = view
    }

    /** [Media3SurfaceHost] clears the registration on dispose/onReset/onRelease (no stale re-attach). */
    fun clear() {
        _surface.value = null
    }

    /**
     * Suspend until a SurfaceView is registered (the host composed). The H.264 feed calls this on the main
     * thread before `player.setVideoSurfaceView(...)`. Cooperatively cancellable — if the holder driver is
     * cancelled while waiting, this throws CancellationException and the feed exits cleanly (Cancelled).
     */
    suspend fun awaitSurface(): SurfaceView = _surface.filterNotNull().first()
}
