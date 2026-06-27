package works.mees.jiib.command

import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.KlippyState

/** What the gating UI should show. HardLock wins over SoftBusy; lifecycle loss wins over both. */
sealed interface GatingState {
    data object Idle : GatingState
    /** A SoftBusy command is running — show a non-blocking "busy" indicator; controls stay live. */
    data class Busy(val key: String) : GatingState
    /** A HardLock command is running and the link is healthy — Focus morph + confirm-on-back. */
    data class Locked(val key: String) : GatingState
    /** A HardLock op may still be running but the link/firmware can't confirm — do NOT unlock silently. */
    data object Unknown : GatingState
}

/**
 * Pure gating reducer (Codex Findings 4 & 7). Precedence:
 *   1. an unresolved-HardLock latch → Unknown (abnormal exit; printer may still be working);
 *   2. an active HardLock while the link is NOT healthy (not Connected+Ready) → Unknown;
 *   3. an active HardLock with a healthy link → Locked;
 *   4. an active SoftBusy → Busy;
 *   5. otherwise → Idle.
 */
fun deriveGatingState(
    active: List<CommandDispatcher.ActiveCommand>,
    unresolvedHardLock: String?,
    connection: ConnectionState,
    klippy: KlippyState,
): GatingState {
    if (unresolvedHardLock != null) return GatingState.Unknown
    val healthy = connection == ConnectionState.Connected && klippy == KlippyState.Ready
    val hard = active.firstOrNull { it.gating == GatingMode.HardLock }
    if (hard != null) return if (healthy) GatingState.Locked(hard.key) else GatingState.Unknown
    val soft = active.firstOrNull { it.gating == GatingMode.SoftBusy }
    if (soft != null) return GatingState.Busy(soft.key)
    return GatingState.Idle
}
