package works.mees.jiib.ui.screen

import works.mees.jiib.net.ProbeResult

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

/** Strips Moonraker's redundant `moonraker @ ` mDNS-instance prefix from a discovered service name,
 *  leaving just the hostname. Returns "" when nothing useful remains (a bare "moonraker", the prefix
 *  alone, or an empty advert) — Moonraker is the only thing we connect to, so that label carries no
 *  information and the row should fall back to the IP. */
private val MOONRAKER_PREFIX = Regex("(?i)^moonraker\\s*@\\s*")

fun discoveredHostname(serviceName: String): String {
    val stripped = serviceName.trim().replaceFirst(MOONRAKER_PREFIX, "").trim()
    return if (stripped.isBlank() || stripped.equals("moonraker", ignoreCase = true)) "" else stripped
}
