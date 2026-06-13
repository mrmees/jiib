package works.mees.dinghy.ui.move

import kotlinx.serialization.Serializable

/** A named toolhead position. [z] is null when the user chose not to save a Z height (XY-only recall). */
@Serializable
data class SavedLocation(
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double? = null,
)
