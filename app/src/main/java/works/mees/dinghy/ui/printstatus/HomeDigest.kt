package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import works.mees.dinghy.R
import works.mees.dinghy.state.HeaterState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.theme.fsSp
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

/** One resolved digest line. */
private data class DigestRowData(val label: String, val value: String, val color: Color)

/**
 * The non-printing home Focus digest, rendered as a UNIFORM-shrink two-column grid: a left label column
 * (Geist) + a right-aligned value column (Mono), every row at ONE font size — the largest at which the
 * widest row (longest label + widest value) fits the Focus width on a single line (R-CDX-3 / owner
 * 2026-06-16). Nothing wraps and nothing truncates in the normal case; more rows / longer names just
 * shrink the whole block. The font floor is [DinghyType.focusHero]'s minSp; a label that still overflows
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
    val labelBase = DinghyType.focusHeroLabel.toTextStyle(t)
    val valueBase = DinghyType.focusHero.toTextStyle(t)
    val maxScaled = labelBase.fontSize.value          // already fs-scaled (fsSp(40, fs))
    val minScaled = fsSp(DinghyType.focusHero.minSp ?: 15f, t.fs)

    BoxWithConstraints(modifier) {
        val availPx = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.MAX_VALUE
        val gapPx = with(density) { DIGEST_GAP.toPx() }

        // Measure each column at the BASE (max) size; widths scale ~linearly with font size, so one
        // measure pass gives us everything. Keyed so temp ticks (same digit-count) don't re-measure.
        val key = rows.joinToString("·") { "${it.label}/${it.value.length}" } + "|$availPx|${t.fs}"
        val grid = remember(key) {
            val maxLabelPx = rows.maxOf { measurer.measure(it.label, labelBase, maxLines = 1, softWrap = false).size.width }
            val maxValuePx = rows.maxOf { measurer.measure(it.value, valueBase, maxLines = 1, softWrap = false).size.width }
            val textBindingPx = (maxLabelPx + maxValuePx).toFloat()
            val sizeSp = if (textBindingPx <= 0f || availPx == Float.MAX_VALUE) maxScaled
                else (maxScaled * (availPx - gapPx) / textBindingPx).coerceIn(minScaled, maxScaled)
            val scale = if (maxScaled > 0f) sizeSp / maxScaled else 1f
            // +1px guards against rounding clipping the last glyph.
            val labelDp = with(density) { (maxLabelPx * scale + 1f).toDp() }
            val valueDp = with(density) { (maxValuePx * scale + 1f).toDp() }
            Triple(sizeSp, labelDp, valueDp)
        }
        val (sizeSp, labelColDp, valueColDp) = grid

        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            rows.forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        r.label,
                        color = t.text2,
                        style = DinghyType.focusHeroLabel.toTextStyle(t, sizeSp),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(labelColDp),
                    )
                    Spacer(Modifier.width(DIGEST_GAP))
                    Box(Modifier.width(valueColDp), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            r.value,
                            color = r.color,
                            style = DinghyType.focusHero.toTextStyle(t, sizeSp),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/** Fixed gap between the label and value columns (does not scale with the shrink). */
private val DIGEST_GAP = 16.dp
