package works.mees.jiib.config

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * One per-printer preheat preset: a NAME plus a SPARSE map of Moonraker heater object name → target °C.
 * Absent key = "skip this heater" (the preset never touches it). A stored value of `0` is a REAL
 * setpoint that commands TARGET=0 (turn the heater off). Keys are full object names
 * (`extruder`, `heater_bed`, `heater_generic chamber`, `temperature_fan exhaust`).
 */
@Serializable
@Immutable
data class HeatPreset(
    val id: String,
    val name: String,
    val setpoints: Map<String, Int> = emptyMap(),
) {
    /** Primary-extruder setpoint — drives list sorting and the Extrude page's nozzle-only apply. */
    val extruderTemp: Int? get() = setpoints["extruder"]
}

/** Sort for display: by primary-extruder temp ascending, presets without one last, then name (ci). */
fun List<HeatPreset>.sortedForDisplay(): List<HeatPreset> =
    sortedWith(compareBy({ it.extruderTemp ?: Int.MAX_VALUE }, { it.name.lowercase() }))

/** The three jiib defaults seeded for a NEW printer (extruder + bed only; generics/fans omitted). */
fun defaultHeatPresets(): List<HeatPreset> = listOf(
    HeatPreset(UUID.randomUUID().toString(), "Low", mapOf("extruder" to 150, "heater_bed" to 50)),
    HeatPreset(UUID.randomUUID().toString(), "Medium", mapOf("extruder" to 200, "heater_bed" to 65)),
    HeatPreset(UUID.randomUUID().toString(), "High", mapOf("extruder" to 230, "heater_bed" to 90)),
)
