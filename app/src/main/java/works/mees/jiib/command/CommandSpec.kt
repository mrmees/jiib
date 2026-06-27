package works.mees.jiib.command

import kotlinx.serialization.json.JsonElement

/**
 * Headless command definition model for Jiib-sent operations.
 *
 * This layer is deliberately plain Kotlin: no Android, no Compose, no service ownership. It is the
 * catalog-linked shape later phases can register into while keeping transport behavior in the
 * existing dispatcher/client classes.
 */
enum class CommandTransport {
    JsonRpc,
    GcodeScript,
    RestEndpoint,
    SpoolmanRest,
}

sealed interface AvailabilityPredicate {
    data object Always : AvailabilityPredicate
    data class ObjectPresent(val name: String) : AvailabilityPredicate
    data class MacroPresent(val name: String) : AvailabilityPredicate
    data class ComponentPresent(val name: String) : AvailabilityPredicate
    data class GcodeCommandPresent(val name: String) : AvailabilityPredicate
    data class AnyOf(val predicates: List<AvailabilityPredicate>) : AvailabilityPredicate
    data class NotOnOurPrinters(val reason: String) : AvailabilityPredicate
}

data class CommandSemantics(
    val success: String,
    val error: String,
    val acceptance: String,
)

/** UI gating behavior for a command (ready-for-action gating). See the gating design spec. */
enum class GatingMode {
    /** No gating — toggles, settings, instant commands. */
    None,
    /** Controls stay tappable (queueable); a non-blocking "busy" indicator shows while running. */
    SoftBusy,
    /** Focus morphs to a status card, Field dims, confirm-on-back. E-stop stays live. */
    HardLock,
}

data class CommandSpec<P>(
    val catalogId: String,
    val transport: CommandTransport,
    val method: String?,
    val dispatchKey: (P) -> String,
    val params: (P) -> JsonElement?,
    val availability: AvailabilityPredicate = AvailabilityPredicate.Always,
    /** When true, the gcode script gets a trailing `\nM400` so the reply means "motion drained." */
    val fence: Boolean = false,
    /** UI gating behavior — drives busy indicator / Focus morph / confirm-on-back. */
    val gating: GatingMode = GatingMode.None,
    /** Per-command action deadline override (ms); null → dispatcher default (gcode = 120s). */
    val gatingTimeoutMs: Long? = null,
    val semantics: CommandSemantics,
)
