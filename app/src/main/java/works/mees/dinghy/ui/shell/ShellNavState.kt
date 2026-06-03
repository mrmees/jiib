package works.mees.dinghy.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import works.mees.dinghy.calibration.CalibrationRoutine
import works.mees.dinghy.ui.macros.MacroVm
import works.mees.dinghy.ui.route.Dest

/**
 * The shell's NAV state, HOISTED above the [RootController] Splash/Shell switch (G-A1).
 *
 * ## Why hoist (the 13-04 UAT bug)
 * A transient recovery flips the top route to [works.mees.dinghy.ui.route.TopRoute.Splash] (klippy not
 * Ready OR — after 13-05 Task 3 — the socket reconnecting), and [RootController] HARD-OVERRIDES the
 * shell for [works.mees.dinghy.ui.screen.SplashScreen], DECOMPOSING [AppShell]. If the nav state lived
 * in an AppShell-local `remember`, it would die with that decompose and re-init to [Dest.PrintStatus]
 * (Home) on return — exactly the "app returns to Home on recovery" side-effect the live UAT caught.
 *
 * Hoisting the plain `remember` state UP into [RootController] (which stays composed across the
 * Splash/Shell flip) is cleaner than a `SaveableStateHolder` here (Codex-reviewed): [RootController]
 * owns this holder via [rememberShellNavState] and passes it into [AppShell], so the visible [dest],
 * the [backStack], and an in-progress [calibrationRoutine] SURVIVE the Splash blip — the user returns
 * to the screen (and calibration routine page) they were on.
 *
 * ## Disposition of the sub-nav state (Codex-required enumeration, 13-05 Task 2)
 * - **Hoisted + PRESERVED across the Splash blip:** [dest], [backStack], [calibrationRoutine] — a user
 *   mid-calibration-routine returns to it, not to a blank hub or Home.
 * - **RESET on return from a recovery Splash:** [macroPopupFor] (via [resetTransient]) — a transient
 *   macro Execution popup must NOT survive a reconnect (re-opening a half-state popup is wrong); it is
 *   cleared the moment the shell re-composes after a Splash.
 * - **PRESERVED:** [macroShowSystem] (the System-vs-Bookmarked toggle) — a VIEW PREFERENCE, not
 *   transient state, so it survives the blip like [dest]. (Documented in the 13-05 SUMMARY.)
 *
 * [drawerOpen] stays AppShell-local (it is meaningless while the shell is decomposed) — it is NOT
 * hoisted here.
 */
class ShellNavState {
    /** The visible screen. PrintStatus is the home/root. */
    var dest by mutableStateOf(Dest.PrintStatus)

    /** CALLER dests (most-recent last). PrintStatus clears it; Back pops to the caller. */
    val backStack = mutableStateListOf<Dest>()

    /** Macro sub-nav: System-vs-Bookmarked toggle. A view PREFERENCE — preserved across a Splash blip. */
    var macroShowSystem by mutableStateOf(false)

    /** Macro Execution popup target. TRANSIENT — reset on return from a recovery Splash ([resetTransient]). */
    var macroPopupFor by mutableStateOf<MacroVm?>(null)

    /** Calibration sub-nav: null = the hub, non-null = that routine's page. Preserved across a Splash blip. */
    var calibrationRoutine by mutableStateOf<CalibrationRoutine?>(null)

    fun navigateTo(target: Dest) {
        if (target == dest) return
        if (target == Dest.PrintStatus) backStack.clear() else backStack.add(dest)
        // Entering the Macros surface always starts on the Bookmarked launcher with no popup open.
        if (target == Dest.Macros) {
            macroShowSystem = false
            macroPopupFor = null
        }
        // Entering the Calibration surface always starts on the hub (no routine selected).
        if (target == Dest.Calibration) {
            calibrationRoutine = null
        }
        dest = target
    }

    fun goBack() {
        if (backStack.isNotEmpty()) dest = backStack.removeAt(backStack.lastIndex)
    }

    /**
     * Clear the TRANSIENT sub-nav state on return from a recovery Splash. Only [macroPopupFor] is
     * transient (a half-state macro popup must not survive a reconnect); [dest]/[backStack]/
     * [calibrationRoutine]/[macroShowSystem] are deliberately PRESERVED (G-A1 — the user returns to
     * their screen).
     */
    fun resetTransient() {
        macroPopupFor = null
    }
}

/** Remember a [ShellNavState] in the CURRENT composition scope (call from above the Splash/Shell switch). */
@Composable
fun rememberShellNavState(): ShellNavState = remember { ShellNavState() }
