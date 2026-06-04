package works.mees.dinghy.ui.spool

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.NumpadPage
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/** Sensible gross-weight bounds (g): 0 up to a generous spool+filament ceiling — clamps fat-finger entry. */
private val GROSS_WEIGHT_RANGE: ClosedFloatingPointRange<Double> = 0.0..10_000.0

/**
 * SPOOL-09 / D-04 — the measured GROSS-weight correction page. A THIN caller of [NumpadPage] (the
 * shared full-screen single-bounded-numeric-entry primitive — REUSED, never forked): label="Gross
 * weight", unit="g", `allowDecimal=true`, seeded from the spool's last-known gross when derivable. On
 * Set it issues [SpoolmanClient.measureSpool] (PUT /v1/spool/{id}/measure) — Spoolman subtracts the
 * spool's empty weight and RECOMPUTES `remaining_weight` (and therefore `used_weight`); the active-spool
 * card/picker reflect the new remaining via the existing flows.
 *
 * ## D-04 / Pitfall 7: show that remaining and used are LINKED
 * `used = initial − remaining` — the page's header (CALLER-side copy, NOT a NumpadPage change) spells
 * this out so the user understands a measured correction propagates to BOTH: a lower measured gross
 * lowers `remaining` and raises `used` in lock-step. The header shows the spool's current
 * remaining/used so the relationship is concrete before the correction.
 *
 * Numeric-only entry (NumpadPage is a digit pad, not an alphanumeric keyboard — honors the "no keyboard
 * in printer controls" LAW). [onCancel] returns without a write. Font scale (D-16): header title 22sp,
 * the remaining/used tabular figures 26sp GeistMono, the relationship line ≥17sp; no hardcoded `.sp`.
 *
 * @param spool the spool being corrected (its id is the measure target; its remaining/used seed the copy).
 * @param client the session Spoolman reader (the measure write); null → the Set is a no-op (no session).
 * @param onCancel returns without writing.
 * @param onMeasured invoked AFTER a successful measure write so the caller can refresh + dismiss.
 */
@Composable
fun MeasuredWeightPage(
    spool: SpoolmanSpool,
    client: SpoolmanClient?,
    onCancel: () -> Unit,
    onMeasured: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val scope = rememberCoroutineScope()

    // Seed the entry from the last-known gross when derivable (remaining + the empty-spool weight is not
    // exposed on the spool here, so default to the current remaining as the nearest sensible starting
    // point — the user overwrites it with the scale reading anyway).
    val seed = spool.remainingWeight ?: 0.0

    Column(modifier.fillMaxSize().background(t.bg)) {
        LinkedWeightHeader(spool, t)
        NumpadPage(
            label = "Gross weight",
            initial = seed,
            range = GROSS_WEIGHT_RANGE,
            unit = "g",
            allowDecimal = true,
            onCancel = onCancel,
            onSet = { grams ->
                scope.launch {
                    client?.measureSpool(spool.id, grams)
                    onMeasured()
                }
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}

/**
 * The CALLER-side header (D-04): names the spool and shows that remaining/used are LINKED. The
 * `used = initial − remaining` relationship is spelled out so the user understands the measured gross
 * correction propagates to both figures.
 */
@Composable
private fun LinkedWeightHeader(spool: SpoolmanSpool, t: ThemeTokens) {
    val shape = RoundedCornerShape(t.rCard)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(shape)
            .background(t.surface)
            .border(BorderStroke(2.dp, t.hair), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val filament = spool.filament
        Text(
            text = listOfNotNull(filament?.material, filament?.name)
                .joinToString(" · ").ifBlank { "Spool ${spool.id}" },
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(22f, t.fs).sp,
        )
        // Current remaining + used, the GeistMono tabular pair (26sp) — the concrete linked figures.
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinkedFigure("scale", "remaining", spool.remainingWeight, t)
            LinkedFigure("history", "used", spool.usedWeight, t)
        }
        // The load-bearing D-04 copy: remaining and used are linked — a measured gross moves BOTH.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialSymbol("link", tint = t.accent2, sizeSp = fsSp(20f, t.fs))
            Text(
                text = "Linked: used = initial − remaining. A measured gross weight updates both.",
                color = t.text2,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Medium,
                fontSize = fsSp(17f, t.fs).sp,
            )
        }
    }
}

/** One tabular linked figure (the 26sp GeistMono value + its caption, D-16). */
@Composable
private fun LinkedFigure(symbol: String, caption: String, grams: Double?, t: ThemeTokens) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(symbol, tint = t.text2, sizeSp = fsSp(22f, t.fs))
        Text(
            text = grams?.let { "${it.roundToInt()} g" } ?: "—",
            color = if (grams == null) t.text3 else t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(26f, t.fs).sp,
            maxLines = 1,
        )
        Text(caption, color = t.text2, fontFamily = GeistMono, fontSize = fsSp(17f, t.fs).sp)
    }
}
