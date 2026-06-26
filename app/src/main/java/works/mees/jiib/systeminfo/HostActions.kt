package works.mees.jiib.systeminfo

/**
 * Whether each host power/restart action can run, derived from `machine.system_info`'s `provider`
 * + `available_services`. Conservative: ENABLE by default (don't trap the user when data is sparse —
 * Moonraker will still reject if wrong); DISABLE only on a positive can't-run signal, with copy
 * explaining why (container hosts / unconfigured providers can't reboot the metal).
 *
 * Disabled reasons are non-blank strings when the action is blocked; null when enabled.
 */
data class HostActionAvailability(
    val canReboot: Boolean,
    val canShutdown: Boolean,
    val canRestartMoonraker: Boolean,
    val powerDisabledReason: String?,
    val moonrakerDisabledReason: String?,
)

// Providers Moonraker documents as able to reboot/shutdown the host. null/unknown → allow (don't
// trap a valid setup we don't recognize; Moonraker will still reject). Known non-power providers
// (none, supervisord*) → disable + explain.
private val POWER_CAPABLE_PROVIDERS = setOf("systemd_dbus", "systemd_cli")
private val KNOWN_NON_POWER_PROVIDERS = setOf("none", "supervisord", "supervisord_cli")

/**
 * Derives [HostActionAvailability] from the current [SystemInfo] (or null = no data yet).
 *
 * Power (reboot/shutdown): ENABLED by default; DISABLED only when provider is in the known
 * non-power set OR starts with "supervisord" (covers future supervisord_* variants). Unknown or
 * null provider stays enabled — don't trap setups we don't recognize.
 *
 * Restart-Moonraker: blocked when provider == "none" OR a non-empty availableServices list omits
 * "moonraker". An empty list means "no service data yet" → remain enabled.
 */
fun hostActionAvailability(identity: SystemInfo?): HostActionAvailability {
    val provider = identity?.provider
    val services = identity?.availableServices ?: emptyList()

    // Disable power only on a positive can't-run signal; unknown/null providers stay enabled.
    val powerBlocked = provider != null &&
        provider !in POWER_CAPABLE_PROVIDERS &&
        (provider in KNOWN_NON_POWER_PROVIDERS || provider.startsWith("supervisord"))

    // Restart-Moonraker: block when provider is "none", or a non-empty service list omits moonraker.
    val moonrakerBlocked = provider == "none" || (services.isNotEmpty() && "moonraker" !in services)

    return HostActionAvailability(
        canReboot = !powerBlocked,
        canShutdown = !powerBlocked,
        canRestartMoonraker = !moonrakerBlocked,
        powerDisabledReason = if (powerBlocked) "This host's service manager can't reboot/power it off" else null,
        moonrakerDisabledReason = if (moonrakerBlocked) "Moonraker isn't a managed service on this host" else null,
    )
}
