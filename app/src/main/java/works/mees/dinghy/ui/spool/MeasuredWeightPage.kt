package works.mees.dinghy.ui.spool

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * SPOOL-09 / D-04 — the measured GROSS-weight correction page. The user puts the whole spool on a scale
 * and enters the TOTAL weighed weight (filament + spool); Spoolman subtracts the empty-spool weight and
 * recomputes `remaining_weight`/`used_weight` (e.g. enter 1210 g on a spool whose empty weight is 210 g →
 * Spoolman records 1000 g remaining). The active-spool card / picker reflect the new remaining via the
 * existing flows.
 *
 * The header makes the deduction OBVIOUS: it shows this spool's **spool weight** (the empty/tare weight
 * Spoolman subtracts), so the user knows exactly what's being taken off their measurement. Per Matthew
 * (2026-06-04) the entry is the **system numeric keyboard** (BasicTextField, KeyboardType.Number) — a
 * deliberate exception to the keyboard-free control LAW for this single inventory-correction field.
 *
 * @param spool the spool being corrected (its id is the measure target; its spool weight seeds the header).
 * @param client the session Spoolman reader (the measure write); null → Set is a no-op (no session).
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
    var text by remember { mutableStateOf("") }
    val grams = text.toDoubleOrNull()
    val valid = grams != null && grams > 0.0

    Column(
        modifier.fillMaxSize().background(t.bg).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SpoolWeightHeader(spool, t)

        // The single data-entry field — the TOTAL weighed weight, via the system numeric keyboard.
        Text(
            text = "Total weighed weight (spool + filament)",
            color = t.text2,
            fontFamily = Geist,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(18f, t.fs).sp,
        )
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(t.rCtrl))
                .border(BorderStroke(2.dp, if (valid) t.accentLine else t.outline), RoundedCornerShape(t.rCtrl))
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = text,
                onValueChange = { new -> text = new.filter { it.isDigit() || it == '.' }.take(8) },
                singleLine = true,
                textStyle = TextStyle(
                    color = t.text,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(30f, t.fs).sp,
                ),
                cursorBrush = SolidColor(t.accent2),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (text.isEmpty()) {
                Text(
                    text = "0 g",
                    color = t.text3,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(30f, t.fs).sp,
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedControl(
                label = "Back",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                intent = Intent.Danger,
                symbol = "arrow_back",
            )
            OutlinedControl(
                label = "Set",
                onClick = {
                    if (valid) {
                        scope.launch {
                            client?.measureSpool(spool.id, grams)
                            onMeasured()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                intent = Intent.Go,
                symbol = "check",
            )
        }
    }
}

/**
 * The header (D-04): names the spool and shows the **spool weight** (empty/tare) Spoolman will subtract
 * from the entered gross — so it's obvious what the deduction is.
 */
@Composable
private fun SpoolWeightHeader(spool: SpoolmanSpool, t: ThemeTokens) {
    val shape = RoundedCornerShape(t.rCard)
    val filament = spool.filament
    val tare = spool.effectiveSpoolWeight
    // What Spoolman currently believes the WHOLE spool weighs (tare + filament remaining) — the user
    // sanity-checks their scale reading against this.
    val believedTotal = if (tare != null && spool.remainingWeight != null) tare + spool.remainingWeight else null
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.surface)
            .border(BorderStroke(2.dp, t.hair), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = listOfNotNull(filament?.material, filament?.name).joinToString(" · ").ifBlank { "Spool ${spool.id}" },
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(26f, t.fs).sp,
            maxLines = 1,
        )
        WeightStat("Spool weight", tare, t)
        WeightStat("Spoolman thinks total", believedTotal, t)
        Text(
            text = "Spoolman subtracts the spool weight from your weighed total to get filament remaining.",
            color = t.text2,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(18f, t.fs).sp,
        )
    }
}

/** One labelled weight stat in the header: "<label>  <grams> g" (or "not set" when null). */
@Composable
private fun WeightStat(label: String, grams: Double?, t: ThemeTokens) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = t.text2,
            fontFamily = Geist,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(18f, t.fs).sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = grams?.let { "${it.roundToInt()} g" } ?: "not set",
            color = if (grams == null) t.text3 else t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(26f, t.fs).sp,
            maxLines = 1,
        )
    }
}
