package works.mees.jiib.ui.printstatus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.collections.immutable.ImmutableMap
import works.mees.jiib.R
import works.mees.jiib.state.HeaterState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp
import works.mees.jiib.theme.seriesColor
import works.mees.jiib.ui.spool.ActiveSpoolCardState

/**
 * The non-printing home Focus state digest + its pure formatting helpers. Renders Heaters (OFF, or one
 * row per active heater) · Motors · Homed · Spool — label start-aligned (Geist [JiibType.focusHeroLabel])
 * and value end-aligned (Mono [JiibType.focusHero]). See
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

/** One resolved digest line. */
private data class DigestRowData(val label: String, val value: String, val color: Color)

/**
 * The non-printing home Focus digest, rendered as a UNIFORM-shrink two-column grid: a left label column
 * (Geist) + a right-aligned value column (Mono), every row at ONE font size — the largest at which the
 * widest row (longest label + widest value) fits the Focus width on a single line (R-CDX-3 / owner
 * 2026-06-16). Nothing wraps and nothing truncates in the normal case; more rows / longer names just
 * shrink the whole block. The font floor is [JiibType.focusHero]'s minSp; a label that still overflows
 * at the floor ellipsizes (pathological-name safety net).
 *
 * Heater values are colored from [heaterColors] (per-printer trace override) else the accent-first
 * [seriesColor] by canonical index; other values neutral.
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

    // Build the resolved rows (label · value · value-color) in canonical order.
    val rows = buildList {
        if (active.isEmpty()) {
            add(DigestRowData(stringResource(R.string.printstatus_digest_heaters), stringResource(R.string.printstatus_digest_off), t.text))
        } else {
            active.forEach { key ->
                val heater = state.heaters[key]
                if (heater != null) {
                    val color = heaterColors[key] ?: t.seriesColor(order.indexOf(key).coerceAtLeast(0))
                    add(DigestRowData(prettyHeaterLabel(key), heaterValueText(heater), color))
                }
            }
        }
        state.motorsEnabled?.let { on ->
            val value = if (on) stringResource(R.string.printstatus_digest_on) else stringResource(R.string.printstatus_digest_off)
            add(DigestRowData(stringResource(R.string.printstatus_digest_motors), value, t.text))
        }
        add(DigestRowData(stringResource(R.string.printstatus_digest_homed), homedText(state.homedAxes), t.text))
        spoolDigestValue(spoolmanPresent, activeSpoolCardState)?.let { value ->
            add(DigestRowData(stringResource(R.string.printstatus_digest_spool), value, t.text))
        }
    }

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelBase = JiibType.focusHeroLabel.toTextStyle(t)
    val valueBase = JiibType.focusHero.toTextStyle(t)
    val maxScaled = labelBase.fontSize.value          // already fs-scaled (fsSp(40, fs))
    val minScaled = fsSp(JiibType.focusHero.minSp ?: 15f, t.fs)

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val availPx = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.MAX_VALUE
        val gapPx = with(density) { DIGEST_GAP.toPx() }

        // ONE font size for the whole block: the largest at which the WIDEST row (its own label+value)
        // fits availPx on a single line. Rows fill the Focus width with the value pinned to the end, so
        // every value's right edge is flush to the Focus frame and nothing wraps. Widths scale ~linearly
        // with size, so one base-size measure pass suffices. Keyed so temp ticks (same digit-count) skip
        // re-measuring.
        val key = rows.joinToString("·") { "${it.label}/${it.value.length}" } + "|$availPx|${t.fs}"
        val sizeSp = remember(key) {
            val widestRowPx = rows.maxOf {
                measurer.measure(it.label, labelBase, maxLines = 1, softWrap = false).size.width +
                    measurer.measure(it.value, valueBase, maxLines = 1, softWrap = false).size.width
            }.toFloat()
            if (widestRowPx <= 0f || availPx == Float.MAX_VALUE) maxScaled
            else (maxScaled * (availPx - gapPx) / widestRowPx).coerceIn(minScaled, maxScaled)
        }

        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            rows.forEach { r ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.label,
                        color = t.text2,
                        style = JiibType.focusHeroLabel.toTextStyle(t, sizeSp),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(DIGEST_GAP))
                    Text(
                        r.value,
                        color = r.color,
                        style = JiibType.focusHero.toTextStyle(t, sizeSp),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** Fixed gap between the label and value columns (does not scale with the shrink). */
private val DIGEST_GAP = 16.dp
