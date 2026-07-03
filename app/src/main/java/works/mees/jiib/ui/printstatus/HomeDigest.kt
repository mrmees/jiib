package works.mees.jiib.ui.printstatus

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableMap
import works.mees.jiib.R
import works.mees.jiib.designsystem.components.DigestEmphasis
import works.mees.jiib.designsystem.components.DigestRow
import works.mees.jiib.state.HeaterState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.ThemeTokens
import works.mees.jiib.theme.seriesColor
import works.mees.jiib.ui.spool.ActiveSpoolCardState

/**
 * The non-printing home Focus state digest helpers. [homeDigestRows] builds [DigestRow.Line] items
 * for Heaters (OFF, or one row per active heater) · Motors · Homed · Spool in canonical order for
 * [works.mees.jiib.designsystem.focus.FocusDigest]. The pure formatting helpers are host-testable.
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

/** True when ANY heater is actively heating (target > 0) — drives the idle foot bar's
 *  Preheat→Cooldown swap. Pure (host-testable); reuses the canonical [activeHeaterKeys] "on" rule. */
internal fun anyHeaterOn(heaters: Map<String, HeaterState>): Boolean =
    activeHeaterKeys(heaters).isNotEmpty()

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
 * Builds the [DigestRow] list for the non-printing home Focus state digest: Heaters (OFF or per-active-
 * heater rows) · Motors · Homed · Spool — each a [DigestRow.Line] at [DigestEmphasis.Strong]. Data prep
 * (orderedHeaterKeys/activeHeaterKeys/heaterValueText/homedText/spoolDigestValue) is verbatim from the
 * previous HomeDigest composable. @Composable because it calls [stringResource].
 *
 * @param heaterColors per-heater override colors keyed by heater object name (stable ImmutableMap).
 */
@Composable
internal fun homeDigestRows(
    state: PrinterState,
    spoolmanPresent: Boolean,
    activeSpoolCardState: ActiveSpoolCardState,
    heaterColors: ImmutableMap<String, Color>,
    t: ThemeTokens,
): List<DigestRow> {
    val order = orderedHeaterKeys(state.heaters)
    val active = activeHeaterKeys(state.heaters)

    return buildList {
        if (active.isEmpty()) {
            add(
                DigestRow.Line(
                    label = stringResource(R.string.printstatus_digest_heaters),
                    value = stringResource(R.string.printstatus_digest_off),
                    emphasis = DigestEmphasis.Strong,
                    valueColor = t.text,
                ),
            )
        } else {
            active.forEach { key ->
                val heater = state.heaters[key]
                if (heater != null) {
                    val color = heaterColors[key] ?: t.seriesColor(order.indexOf(key).coerceAtLeast(0))
                    add(
                        DigestRow.Line(
                            label = prettyHeaterLabel(key),
                            value = heaterValueText(heater),
                            emphasis = DigestEmphasis.Strong,
                            valueColor = color,
                        ),
                    )
                }
            }
        }
        state.motorsEnabled?.let { on ->
            val value = if (on) stringResource(R.string.printstatus_digest_on) else stringResource(R.string.printstatus_digest_off)
            add(
                DigestRow.Line(
                    label = stringResource(R.string.printstatus_digest_motors),
                    value = value,
                    emphasis = DigestEmphasis.Strong,
                    valueColor = t.text,
                ),
            )
        }
        add(
            DigestRow.Line(
                label = stringResource(R.string.printstatus_digest_homed),
                value = homedText(state.homedAxes),
                emphasis = DigestEmphasis.Strong,
                valueColor = t.text,
            ),
        )
        spoolDigestValue(spoolmanPresent, activeSpoolCardState)?.let { value ->
            add(
                DigestRow.Line(
                    label = stringResource(R.string.printstatus_digest_spool),
                    value = value,
                    emphasis = DigestEmphasis.Strong,
                    valueColor = t.text,
                ),
            )
        }
    }
}
