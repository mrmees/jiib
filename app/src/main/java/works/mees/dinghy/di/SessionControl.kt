package works.mees.dinghy.di

/**
 * The NARROW, UI-facing session-control contract (review #1). The shell's entire reconnect/restart
 * surface — Splash recovery (04-05) and any "retry"/"restart firmware" affordance — reaches the live
 * session ONLY through this interface. It deliberately exposes NO member that returns a
 * the raw session type (the spine's `net.Moonraker` session object): the raw session — with its
 * socket, scope, and full control
 * surface) is never reachable by UI code, so a screen cannot, e.g., tear the connection down or read
 * internal transport state. The concrete implementation is supplied by the service and forwards to the
 * CURRENT session/dispatcher (it tracks the latest published spine).
 *
 * Methods are intentionally fire-and-forget (no return value, no session leak):
 *  - [requestReconnectNow] forwards to the live session's `requestReconnectNow()` (cancel backoff / auth
 *    quiescence and attempt immediately).
 *  - [restartFirmware] / [restartHost] dispatch `printer.firmware_restart` / `printer.restart` through
 *    the current [works.mees.dinghy.command.CommandDispatcher] (debounce + in-flight + timeout for free).
 */
interface SessionControl {
    /** Cancel the pending backoff/auth-quiescence and fire an immediate reconnect attempt. */
    fun requestReconnectNow()

    /** Issue `printer.firmware_restart` (Splash recovery) via the current dispatcher. */
    fun restartFirmware()

    /** Issue `printer.restart` (soft Splash recovery) via the current dispatcher. */
    fun restartHost()
}
