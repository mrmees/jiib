package works.mees.dinghy.ui.screen

import works.mees.dinghy.net.ProbeResult

/** Host/port carried from a discovered printer into a fresh editor (the connect-failed path). */
data class ConnectionSeed(val host: String, val port: Int)

/** What to do after probing a picked discovered printer. */
enum class FindPickEffect { AddAndConnect, OpenEditorSeeded }

/**
 * Pure decision for a discovered-printer pick: if BOTH transports connect, add + activate it
 * silently; otherwise (auth/security/refused/timeout) fall back to the editor pre-filled.
 */
fun findPickDecision(probe: ProbeResult): FindPickEffect =
    if (probe.http.ok && probe.ws.ok) FindPickEffect.AddAndConnect else FindPickEffect.OpenEditorSeeded
