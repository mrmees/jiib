package works.mees.dinghy.ui.outputs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.outputs.OutputRowVm
import works.mees.dinghy.outputs.OutputsHolder
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The Outputs list (SC-1) — a flat alpha [LazyColumn] of every discovered controllable output (fan / LED /
 * servo / heater_generic / output_pin / pwm_tool), each row = owner-locked family glyph + prettyName + the
 * live value (and, for an LED, its data-color swatch chip). One drawer destination; reached from the App
 * Drawer's Outputs tile (D-10). Mirrors [works.mees.dinghy.ui.calibration.CalibrationHubScreen]'s
 * ScreenScaffold field+gutter grammar but is a flat scroll list rather than a tile grid.
 *
 * Field-only [ScreenScaffold] (Focus omitted). Gutter = single neutral `Back` (plain nav spends NO safety
 * color — never red, D-10). The swipe-up App Drawer is suppressed on this screen (the Field is a scroll
 * list — drawer-swipe would fight the scroll). Static styling only (Adreno-320 floor — no looping
 * animation). Every color routes through [LocalTokens] EXCEPT the LED swatch (the THEME-01 data-color
 * carve-out: an LED's color is DATA, rendered literally).
 *
 * SC-3 (graceful degrade): a row whose [OutputRowVm.displayValue] is null (absent live value, or a servo
 * whose PWM `.value` is not a readable angle) OMITS the value Composable entirely but the row STAYS present
 * and `clickable` — you can still open it to command it.
 *
 * @param holder     the headless [OutputsHolder] (typed row VM list, live off the spine).
 * @param onRowTap   invoked with the tapped row's objectKey (a later plan wires the per-output detail page).
 * @param onBack     the neutral Back gutter exit (D-10).
 */
@Composable
fun OutputsScreen(
    holder: OutputsHolder,
    onRowTap: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by holder.rows.collectAsStateWithLifecycle()
    OutputsContent(rows = rows, onRowTap = onRowTap, onBack = onBack, modifier = modifier)
}

/**
 * The STATELESS content seam (preview-drivable — no Moonraker, no holder). Takes the typed [OutputRowVm]
 * list directly so the @Preview matrix can render every degrade case from pure fixtures (PREVIEW_AND_TOKENS).
 */
@Composable
fun OutputsContent(
    rows: List<OutputRowVm>,
    onRowTap: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            field = {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.outputs_title),
                        color = t.text,
                        fontFamily = Geist,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fsSp(24f, t.fs).sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(rows, key = { it.descriptor.objectKey }) { row ->
                            OutputRow(row = row, onClick = { onRowTap(row.descriptor.objectKey) })
                        }
                    }
                }
            },
            gutter = {
                Box(Modifier.fillMaxWidth().padding(8.dp)) {
                    BackControl(onClick = onBack, modifier = Modifier.fillMaxWidth())
                }
            },
        )
    }
}

/**
 * One output row: `[family glyph]  prettyName  …  [swatch?] [value?]`. The glyph is the OWNER-LOCKED
 * [DinghyIcons].Output* token chosen by family (D-01..D-06) — NEVER a raw ligature string, NEVER invented.
 * The value is rendered ONLY when [OutputRowVm.displayValue] is non-null (absent → omitted, row stays
 * tappable — SC-3). An LED row also shows its data-color swatch chip (THEME-01 literal-color carve-out).
 */
@Composable
private fun OutputRow(row: OutputRowVm, onClick: () -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = if (row.isSettable) t.accentLine else t.hair
    val contentColor = if (row.isSettable) t.text else t.text3

    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, outline), shape)
            .background(t.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DinghyIconView(
            icon = glyphFor(row.descriptor.family),
            tint = contentColor,
            sizeDp = fsSp(28f, t.fs).dp,
            contentDescription = stringResource(R.string.cd_output_glyph),
        )
        Text(
            text = row.descriptor.prettyName,
            color = contentColor,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
            modifier = Modifier.weight(1f),
        )
        // LED data-color swatch (THEME-01 carve-out — literal color, never token-routed).
        row.swatchColor?.let { argb ->
            Box(
                Modifier
                    .size(fsSp(22f, t.fs).dp)
                    .clip(RoundedCornerShape(t.rCtrl))
                    .background(Color(argb))
                    .border(BorderStroke(1.dp, t.hair), RoundedCornerShape(t.rCtrl)),
            )
        }
        // SC-3: render the value ONLY when present; absent → omit entirely (row stays tappable).
        row.displayValue?.let { value ->
            Spacer(Modifier.width(2.dp))
            Text(
                text = value,
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(20f, t.fs).sp,
            )
        }
    }
}

/**
 * The owner-locked per-family row glyph (icon law D-01..D-06 — NEVER invented, NEVER a raw ligature):
 * heater_generic → [DinghyIcons.OutputHeater] (D-01), fan_generic → [DinghyIcons.OutputFan] (D-02),
 * every LED family → [DinghyIcons.OutputLed] (D-03), servo → [DinghyIcons.OutputServo] (D-04),
 * output_pin → [DinghyIcons.OutputPin] (D-05), pwm_tool → [DinghyIcons.OutputPwmTool] (D-06). An unknown
 * family falls back to the Outputs section glyph (defensive; the discovery whitelist precludes it).
 */
private fun glyphFor(family: String): DinghyIcon = when (family) {
    OutputsHolder.FAMILY_HEATER -> DinghyIcons.OutputHeater
    OutputsHolder.FAMILY_FAN -> DinghyIcons.OutputFan
    OutputsHolder.FAMILY_SERVO -> DinghyIcons.OutputServo
    OutputsHolder.FAMILY_OUTPUT_PIN -> DinghyIcons.OutputPin
    OutputsHolder.FAMILY_PWM_TOOL -> DinghyIcons.OutputPwmTool
    in OutputsHolder.LED_FAMILIES -> DinghyIcons.OutputLed
    else -> DinghyIcons.OutputSection
}

/** Neutral Back gutter control (plain nav = neutral outline, never a safety color — D-10). */
@Composable
private fun BackControl(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, intentColor(Intent.Neutral, t)), shape)
            .padding(horizontal = 12.dp, vertical = 18.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.common_back),
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
        )
    }
}

private fun intentColor(intent: Intent, t: ThemeTokens) = when (intent) {
    Intent.Neutral -> t.outline
    Intent.Accent -> t.accentLine
    Intent.Warn -> t.heat
    Intent.Danger -> t.stop
    Intent.Go -> t.go
}
