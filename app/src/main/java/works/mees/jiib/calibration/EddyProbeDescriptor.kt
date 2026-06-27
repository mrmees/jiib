package works.mees.jiib.calibration

import works.mees.jiib.state.Capabilities

/**
 * Descriptor for an eddy-current probe object derived from [Capabilities.objects].
 *
 * [chip] is the bare CHIP name extracted from the Klipper section name
 * (e.g. `"probe_eddy_current My_Eddy"` → chip = `"My_Eddy"`). Case is preserved.
 * An unnamed section (`"probe_eddy_current"`) yields chip = `""`.
 */
data class EddyProbeDescriptor(val chip: String)

private const val PREFIX = "probe_eddy_current"

/**
 * Returns an [EddyProbeDescriptor] for the first eddy-current probe object found in [caps],
 * or null if none exists.
 */
fun eddyProbeDescriptor(caps: Capabilities): EddyProbeDescriptor? {
    val match = caps.objects.firstOrNull { name ->
        name == PREFIX || name.startsWith("$PREFIX ")
    } ?: return null
    val chip = match.removePrefix(PREFIX).trim()
    return EddyProbeDescriptor(chip)
}
