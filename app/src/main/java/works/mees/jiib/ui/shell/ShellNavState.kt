package works.mees.jiib.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import works.mees.jiib.ui.macros.MacroVm
import works.mees.jiib.ui.route.NavDest
import works.mees.jiib.ui.spool.SpoolPrefilterSeed

/**
 * The shell's in-screen sub-nav state, HOISTED above the [RootController] Splash/Shell switch.
 *
 * ## Phase 24 migration (plan 24-03)
 * The top-level destination (`dest`) and the drill-down back-stack (`backStack` / `navigateTo` /
 * `goBack`) have been REMOVED — Navigation-Compose's [NavHost] now owns the drill-down back-stack.
 * This class retains only the **in-screen sub-nav** state for the D-01 holdouts
 * (Macros bookmarked-vs-system, Outputs detail) and the transient overlay flags (scan, spool prefilter).
 *
 * ## Phase 27 migration (plan 27-02)
 * The `calibrationRoutine` field (the in-screen Calibration sub-nav) has been REMOVED — the NavHost
 * back-stack is now the SINGLE source of truth for the active calibration screen (D-07). Each routine
 * is a real [NavDest] sub-route navigated to via `navController.navigate(routine.toNavDest())`.
 *
 * ## Accepted regression (FIX-3 — owner-locked 2026-06-09)
 * After a recovery Splash the user LANDS ON [NavDest.WaterfallHome] (the morphing root) and each
 * in-screen sub-nav RESETS to its hub. The NavHost is composition-local inside [AppShell] and
 * decomposes during the Splash (gate-above in [RootController]), so the drill-down back-stack is
 * NOT preserved across the recovery. [applyEntryReset] clears the Calibration routine / Macros
 * sub-nav on the next entry, so in-screen sub-nav also resets. This is the
 * deliberate, simpler path the owner accepted (replacing the old dest-preservation gate). It is
 * NOT a bug — executors and verifiers must EXPECT both the land-on-root AND the sub-nav reset.
 *
 * ## Why hoist (the 13-04 UAT bug — still applies to sub-nav)
 * A transient recovery flips the top route to [works.mees.jiib.ui.route.TopRoute.Splash],
 * DECOMPOSING [AppShell]. If the in-screen sub-nav state lived inside AppShell-local `remember`
 * blocks it would die with that decompose and re-init (e.g. a user mid-calibration-routine would
 * return to the hub, not the page they were on). Hoisting here — above [RootController]'s
 * Splash/Shell switch — keeps the sub-nav state alive across the blip. [resetTransient] clears
 * only the non-preservable flags on return.
 *
 * ## startDest seed
 * [startDest] (the dev-gated `start_dest` deep-jump, 18-04) is STORED here as a nullable field.
 * [AppShell] reads it ONCE as the NavHost `startDestination` param — it cannot be a
 * `LaunchedEffect` because that would re-fire on every recompose (RESEARCH Pitfall 2). The seed
 * is null in release / when the dev gate is off, leaving the NavHost at [NavDest.WaterfallHome].

 */
class ShellNavState(val startDest: NavDest? = null) {
    /** Macro sub-nav: System-vs-Bookmarked toggle. A view PREFERENCE — preserved across a Splash blip. */
    var macroShowSystem by mutableStateOf(false)

    /** Macro Execution popup target. TRANSIENT — reset on return from a recovery Splash ([resetTransient]). */
    var macroPopupFor by mutableStateOf<MacroVm?>(null)

    // NOTE: calibrationRoutine REMOVED in Phase 27 (plan 27-02). The NavHost back-stack is now
    // the single source of truth for the active calibration screen (D-07). Use
    // navController.navigate(routine.toNavDest()) and navController.popBackStack() instead.

    /**
     * Spool QR-scan sub-surface (11-07): true = the full-screen camera scan surface is open. TRANSIENT
     * — reset on return from a recovery Splash ([resetTransient]): a live camera surface must NOT
     * survive a reconnect (the camera was released on decompose).
     */
    var scanActive by mutableStateOf(false)

    /**
     * D-04 gcode-aware prefilter seed (11-08): carried from a Files spool-warning "Pick spool" into the
     * Spool picker. null = a plain Spool open (no seed). TRANSIENT — reset on return from a recovery
     * Splash ([resetTransient]).
     */
    var spoolPrefilter by mutableStateOf<SpoolPrefilterSeed?>(null)

    /**
     * The per-dest ENTRY-RESET side-effects, run on EVERY entry into a destination — called from a
     * [LaunchedEffect] inside each affected [NavHost] destination composable. Clears that destination's
     * sub-nav so re-entering always lands on its entry surface, never a stale sub-page (17-08, REVIEW #6).
     * This also implements FIX-3: on return from a recovery Splash the NavHost re-enters [NavDest.WaterfallHome],
     * and each sub-nav resets the NEXT time the user navigates to that destination.
     *
     * Visibility is `internal` so [AppShell]'s composable lambdas (same package) can call it from
     * their `LaunchedEffect(Unit)` blocks.
     */
    internal fun applyEntryReset(target: NavDest) {
        // Entering the Macros surface always starts on the Bookmarked launcher with no popup open.
        if (target == NavDest.Macros) {
            macroShowSystem = false
            macroPopupFor = null
        }
        // NOTE: NavDest.CalibrationHub reset is now a no-op in the body — the NavHost back-stack
        // is the single source of truth for sub-nav (D-07, Phase 27). The call-site in AppShell's
        // composable<NavDest.CalibrationHub> LaunchedEffect(Unit) is KEPT for FIX-3 symmetry
        // (re-entering the hub clears any route the NavHost may have stacked under a recovery Splash).
        // No field to clear here — calibrationRoutine was removed in plan 27-02.
    }

    /**
     * Clear the TRANSIENT sub-nav state on return from a recovery Splash. Only [macroPopupFor],
     * [scanActive], and [spoolPrefilter] are transient (half-state overlays must not survive a reconnect);
     * [macroShowSystem] is deliberately PRESERVED (G-A1 — the user returns to their sub-nav state,
     * not the hub, after recovery).
     *
     * NOTE: `calibrationRoutine` was REMOVED in Phase 27 (plan 27-02). The NavHost back-stack is
     * now the single source of truth for the active calibration screen.
     */
    fun resetTransient() {
        macroPopupFor = null
        scanActive = false
        spoolPrefilter = null
    }
}

/**
 * Remember a [ShellNavState] in the CURRENT composition scope (call from above the Splash/Shell switch).
 * [startDest] (the dev-gated `start_dest` deep-jump, 18-04) is stored in [ShellNavState.startDest]
 * and read ONCE by [AppShell] as the NavHost `startDestination` param — it is not a live re-navigation
 * lever.
 */
@Composable
fun rememberShellNavState(startDest: NavDest? = null): ShellNavState =
    remember { ShellNavState(startDest) }
