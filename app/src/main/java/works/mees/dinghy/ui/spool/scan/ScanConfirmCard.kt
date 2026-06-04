package works.mees.dinghy.ui.spool.scan

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.spool.normalizeColorHex
import works.mees.dinghy.spool.parseSpoolmanSpoolDetail
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * SPOOL-05 / D-12 — the CONFIRM-FIRST scan-to-assign card. A decoded QR has already been parsed id-ONLY
 * by [parseSpoolId] (11-03, the V5 boundary — the scanned host is NEVER read or navigated); the scan
 * machine then sits in [ScanState.AwaitingConfirm]. This card resolves the spool DETAIL for that id and
 * shows it for confirmation. It NEVER auto-loads: set-active fires SOLELY on the explicit green confirm
 * (T-11-07-02). A red Back cancels back to scanning. The host is never navigated and no base URL is ever
 * derived from the scan — only the [spoolId] (an `Int`) crosses this boundary.
 *
 * Detail resolves via [SpoolmanClient.getSpool] → [parseSpoolmanSpoolDetail]; a failed/absent read keeps
 * the card in a lightweight "Spool {id}" pending shape (confirm still allowed — the id is valid, the
 * detail fetch is best-effort). An archived spool shows the D-09 amber warning before confirm.
 *
 * Font scale (D-16): spool title 22sp Geist, remaining tabular 26sp GeistMono, detail rows 17sp, the
 * status/id line ≥17sp, metadata never below the 15sp floor. No hardcoded `.sp`.
 *
 * @param spoolId the parsed-and-validated spool id from the QR (the only thing carried across V5).
 * @param client the session Spoolman inventory reader (resolves the detail; null → pending shape only).
 * @param onConfirm invoked ONLY on the green "Set active" tap — the caller dispatches the set-active write.
 * @param onCancel invoked on the red Back tap (return to scanning, no mutation).
 */
@Composable
fun ScanConfirmCard(
    spoolId: Int,
    client: SpoolmanClient?,
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // Resolve the detail best-effort (D-12: the id is trusted, the host is not; this is a plain getSpool).
    var detail by remember(spoolId) { mutableStateOf<SpoolmanSpool?>(null) }
    var resolved by remember(spoolId) { mutableStateOf(false) }
    LaunchedEffect(spoolId, client) {
        detail = parseSpoolmanSpoolDetail(client?.getSpool(spoolId), expectedId = spoolId)
        resolved = true
    }

    val shape = RoundedCornerShape(t.rCard)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(t.surface)
            .border(BorderStroke(2.dp, t.accentLine), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header: "Scanned spool" status line (≥17sp) so the user knows this is the confirm gate.
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialSymbol("qr_code_scanner", tint = t.accent2, sizeSp = fsSp(20f, t.fs))
            Text(
                text = "Scanned spool #$spoolId",
                color = t.text2,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Medium,
                fontSize = fsSp(17f, t.fs).sp,
            )
        }

        val spool = detail
        if (spool != null) {
            ConfirmSpoolDetail(spool, t)
        } else if (resolved) {
            // Detail fetch failed/absent — the id is still valid; allow confirm on a lightweight shape.
            Text(
                text = "Spool $spoolId",
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(22f, t.fs).sp,
            )
            Text(
                text = "Details unavailable — confirm to set active anyway.",
                color = t.text3,
                fontFamily = GeistMono,
                fontSize = fsSp(15f, t.fs).sp,
            )
        } else {
            Text(
                text = "Resolving…",
                color = t.text3,
                fontFamily = GeistMono,
                fontSize = fsSp(17f, t.fs).sp,
            )
        }

        // The confirm contract (D-12): green "Set active" is the ONLY path that mutates; red Back cancels.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedControl(
                label = "Back",
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                intent = Intent.Danger, // back = red (THEME-04).
                symbol = "arrow_back",
            )
            OutlinedControl(
                label = "Set active",
                onClick = { onConfirm(spoolId) }, // confirm-first: the SOLE set-active trigger (D-12).
                modifier = Modifier.weight(1f),
                intent = Intent.Go, // green accept/commit.
                symbol = "check_circle",
            )
        }
    }
}

/** The resolved spool detail block: split swatch · material/name · vendor · remaining hero · archived (D-09). */
@Composable
private fun ConfirmSpoolDetail(spool: SpoolmanSpool, t: ThemeTokens) {
    val filament = spool.filament
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConfirmSwatch(filament?.colorSwatches ?: emptyList(), t)
            Text(
                text = listOfNotNull(filament?.material, filament?.name)
                    .joinToString(" · ").ifBlank { "Spool ${spool.id}" },
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(22f, t.fs).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        filament?.vendor?.name?.let { ConfirmRow("storefront", "Vendor", it, t) }
        // Remaining — the GeistMono tabular hero (26sp, D-16).
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MaterialSymbol("scale", tint = t.text2, sizeSp = fsSp(22f, t.fs))
            Text(
                text = spool.remainingWeight?.let { "${it.roundToInt()} g" } ?: "—",
                color = if (spool.remainingWeight == null) t.text3 else t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(26f, t.fs).sp,
                maxLines = 1,
            )
            Text("remaining", color = t.text2, fontFamily = GeistMono, fontSize = fsSp(17f, t.fs).sp)
        }
        spool.location?.let { ConfirmRow("location_on", "Location", it, t) }
        if (spool.archived) {
            // D-09: an archived spool is scannable but flagged before confirm.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MaterialSymbol("archive", tint = t.heat, sizeSp = fsSp(20f, t.fs))
                Text(
                    text = "Archived — verify before loading",
                    color = t.heat,
                    fontFamily = Geist,
                    fontWeight = FontWeight.Medium,
                    fontSize = fsSp(15f, t.fs).sp,
                )
            }
        }
    }
}

/** The confirm split swatch (D-08 normalized; multi-color split; neutral marker on absence). */
@Composable
private fun ConfirmSwatch(swatches: List<String>, t: ThemeTokens) {
    val sz = fsSp(24f, t.fs).dp
    if (swatches.isEmpty()) {
        Box(
            Modifier.size(sz).clip(CircleShape).background(t.surface2)
                .border(BorderStroke(1.dp, t.hair), CircleShape),
        )
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        swatches.take(4).forEach { hex ->
            Box(
                Modifier.size(sz).clip(CircleShape)
                    .background(parseConfirmHex(hex) ?: t.surface2)
                    .border(BorderStroke(1.dp, t.hair), CircleShape),
            )
        }
    }
}

/** One icon-led detail stat (label/value 17sp — never below the 15sp floor). */
@Composable
private fun ConfirmRow(symbol: String, label: String, value: String, t: ThemeTokens) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MaterialSymbol(symbol, tint = t.text2, sizeSp = fsSp(20f, t.fs))
        Text(label, color = t.text2, fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = fsSp(15f, t.fs).sp)
        Text(
            value,
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(17f, t.fs).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Parse an already-normalized hex to a Compose [Color] (D-08); guard via [normalizeColorHex]. */
private fun parseConfirmHex(hex: String): Color? {
    val normalized = normalizeColorHex(hex) ?: return null
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}
