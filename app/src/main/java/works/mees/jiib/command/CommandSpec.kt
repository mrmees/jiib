package works.mees.jiib.command

import kotlinx.serialization.json.JsonElement

/**
 * Headless command definition model for Dinghy-sent operations.
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

data class CommandSpec<P>(
    val catalogId: String,
    val transport: CommandTransport,
    val method: String?,
    val dispatchKey: (P) -> String,
    val params: (P) -> JsonElement?,
    val availability: AvailabilityPredicate = AvailabilityPredicate.Always,
    val semantics: CommandSemantics,
)
