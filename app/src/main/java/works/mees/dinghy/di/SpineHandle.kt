package works.mees.dinghy.di

import kotlinx.coroutines.flow.StateFlow
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.PrinterStateStore

/**
 * An IMMUTABLE atomic snapshot of one live Moonraker spine (review #6). All references a screen needs
 * for the CURRENT session live here as ONE value; the [MoonrakerService][works.mees.dinghy.service.MoonrakerService]
 * publishes a whole new [SpineHandle] on every (re)build via a single `StateFlow<SpineHandle?>` assignment,
 * so a partially-swapped mix of an old session's flows with a new session's dispatcher is unrepresentable.
 *
 * [sessionInstanceId] is a monotonic, build-time-stamped id (set by the service when it assembles the
 * spine). It is the CONTINUITY SIGNAL the instrumented rotation gate asserts (review #3): if Activity
 * recreation rebuilt the spine the id would change; an unchanged id across rotation proves the FGS — not
 * the Activity — owns the connection (SHELL-03 / D-02). A republish by a harness would mint a NEW id, so
 * the id-continuity assertion cannot be spoofed by a re-collect.
 *
 * Plain data class with no Compose annotations — toolkit-agnostic, like the rest of the headless spine.
 */
data class SpineHandle(
    /** The current session's printer-state stream (the store's conflated StateFlow). */
    val printerState: StateFlow<PrinterState>,
    /** The current session's connection lifecycle (CONN-06). */
    val connectionState: StateFlow<ConnectionState>,
    /** The current session's re-derived capabilities (STATE-02). */
    val capabilities: StateFlow<Capabilities>,
    /** The per-session action dispatcher (PRIM-05) — every action tap routes through this. */
    val dispatcher: CommandDispatcher,
    /**
     * The current session's [PrinterStateStore] — the SAME store [printerState]/[capabilities] are
     * exposed from. The Print Status shell (04-07) needs the concrete store (not just its flows) to
     * construct a per-session [works.mees.dinghy.ui.printstatus.PrintStatusHolder], which owns the
     * primary-heater RingBuffer and the 2×3 grid model. The service constructs the store; the UI
     * consumes it (the "service constructs, UI consumes" discipline, D-02) — the UI still never opens a
     * socket or owns the connection lifecycle.
     */
    val store: PrinterStateStore,
    /** Monotonic, build-time-stamped id; the rotation-continuity signal (review #3). */
    val sessionInstanceId: Long,
)
