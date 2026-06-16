package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableMap
import works.mees.dinghy.R
import works.mees.dinghy.state.HeaterState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.theme.seriesColor
import works.mees.dinghy.ui.spool.ActiveSpoolCardState

/**
 * The non-printing home Focus state digest + its pure formatting helpers. Renders Heaters (OFF, or one
 * row per active heater) · Motors · Homed · Spool — label start-aligned (Geist [DinghyType.focusHeroLabel])
 * and value end-aligned (Mono [DinghyType.focusHero]). See
 * docs/superpowers/specs/2026-06-16-standby-focus-digest-design.md. The helpers are host-testable; the
 * composables consume them.
 */

/** Prettify a Klipper heater key: drop the `heater_generic `/`heater_` prefix, capitalize. Never invent a synonym. */
internal fun prettyHeaterLabel(key: String): String {
    val stripped = when {
        key.startsWith("heater_generic ") -> key.removePrefix("heater_generic ")
        key.startsWith("heater_") -> key.removePrefix("heater_")
        else -> key
    }
    return stripped.replaceFirstChar { it.uppercase() }
}

/**
 * Canonical heater order (R-CDX-2) driving BOTH row order and the color `localIndex`: `extruder` first
 * (→ accent), then `heater_bed`, then all remaining keys sorted. Keeps each heater's color stable.
 */
internal fun orderedHeaterKeys(heaters: Map<String, HeaterState>): List<String> {
    val keys = heaters.keys
    val head = listOf("extruder", "heater_bed").filter { it in keys }
    val rest = (keys - head.toSet()).sorted()
    return head + rest
}

/** Active heaters (target > 0) in canonical order. Empty → render the single `Heaters OFF` row. */
internal fun activeHeaterKeys(heaters: Map<String, HeaterState>): List<String> =
    orderedHeaterKeys(heaters).filter { (heaters[it]?.target ?: 0.0) > 0.0 }

/** `current/target`, both rounded to integers (no degree symbol). */
internal fun heaterValueText(h: HeaterState): String =
    "${h.temperature.roundToInt()}/${h.target.roundToInt()}"

/** Homed axes in canonical X/Y/Z order (R-CDX-4), any extra axes appended; blank → `NONE`. */
internal fun homedText(homedAxes: String): String {
    val lower = homedAxes.lowercase()
    val ordered = "xyz".filter { it in lower } + lower.filter { it !in "xyz" }
    return ordered.uppercase().ifBlank { "NONE" }
}

/**
 * Spool digest value (R-CDX-4). `null` → hide the row (Spoolman not configured). Otherwise `{weight}g`
 * only for a Loaded spool with a numeric remaining weight; every other variant → `N/A`.
 */
internal fun spoolDigestValue(spoolmanPresent: Boolean, state: ActiveSpoolCardState): String? {
    if (!spoolmanPresent) return null
    val weight = (state as? ActiveSpoolCardState.Loaded)?.spool?.remainingWeight
    return weight?.let { "${it.roundToInt()}g" } ?: "N/A"
}

/**
 * The non-printing home Focus digest. Heater values colored from [heaterColors] (per-printer trace
 * override) else the accent-first [seriesColor] by canonical index; other values neutral.
 *
 * @param heaterColors per-heater override colors keyed by heater object name (stable ImmutableMap).
 */
@Composable
internal fun HomeDigest(
    state: PrinterState,
    spoolmanPresent: Boolean,
    activeSpoolCardState: ActiveSpoolCardState,
    heaterColors: ImmutableMap<String, Color>,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val order = orderedHeaterKeys(state.heaters)
    val active = activeHeaterKeys(state.heaters)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(0.dp)) {
        // Heaters: OFF (single row) or one row per active heater, colored to its trace.
        if (active.isEmpty()) {
            DigestRow(stringResource(R.string.printstatus_digest_heaters), stringResource(R.string.printstatus_digest_off), t.text)
        } else {
            active.forEach { key ->
                val heater = state.heaters[key] ?: return@forEach
                val color = heaterColors[key] ?: t.seriesColor(order.indexOf(key).coerceAtLeast(0))
                DigestRow(prettyHeaterLabel(key), heaterValueText(heater), color)
            }
        }

        // Motors: hidden when motorsEnabled is null (never reported).
        state.motorsEnabled?.let { on ->
            val value = if (on) stringResource(R.string.printstatus_digest_on) else stringResource(R.string.printstatus_digest_off)
            DigestRow(stringResource(R.string.printstatus_digest_motors), value, t.text)
        }

        // Homed.
        DigestRow(stringResource(R.string.printstatus_digest_homed), homedText(state.homedAxes), t.text)

        // Spool (only when Spoolman configured).
        spoolDigestValue(spoolmanPresent, activeSpoolCardState)?.let { value ->
            DigestRow(stringResource(R.string.printstatus_digest_spool), value, t.text)
        }
    }
}

/** One digest line: Geist label at start, Mono value at end (right edges flush). */
@Composable
private fun DigestRow(label: String, value: String, valueColor: Color) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = t.text2, style = DinghyType.focusHeroLabel.toTextStyle(t))
        Text(value, color = valueColor, style = DinghyType.focusHero.toTextStyle(t))
    }
}
