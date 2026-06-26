package works.mees.jiib.theme

import androidx.compose.ui.graphics.Color

/**
 * The three editable status slots (D-03) — the canonical, type-safe keys for the user-editable status
 * colors. Status colors become FULLY user-editable IN COLORFUL because safety is carried 100% by shape
 * + icon + position (Area A); the [key] strings ("stop"/"caution"/"go") are the RESERVED keys that ride
 * the same String→Long persisted override map as the integer pool indices (they cannot collide with the
 * pool-index keys "0".."63"). Use this enum EVERYWHERE in the status-override path — no bare `"stop"`
 * string literals, no integer keys for status.
 *
 * `Caution` maps to the dinghy `heat` token (D-13: dinghy's `heat` IS the caution color).
 */
enum class StatusSlot(val key: String) {
    Stop("stop"),
    Caution("caution"),
    Go("go"),
    ;

    companion object {
        private val BY_KEY: Map<String, StatusSlot> = entries.associateBy { it.key }

        /** Resolve a wire key to its [StatusSlot], or `null` if it is not one of the three status keys. */
        fun fromKey(s: String): StatusSlot? = BY_KEY[s]
    }
}

/**
 * The SHARED stored-ARGB → Compose [Color] conversion (the proven `AppContainer` pattern). A persisted
 * override is an unsigned 32-bit ARGB held in the low bits of a [Long]; [Color] from a `Long` is a
 * PACKED-COLOR constructor (NOT unsigned ARGB) and renders wrong, so we must go through `.toInt()` →
 * the `Color(Int)` sRGB-int constructor. Route ALL status (and pool) Long→Color conversions through this
 * one helper so the conversion is identical everywhere (never `Color(longArgb)`).
 */
fun Long.toComposeColor(): Color = Color(this.toInt())
