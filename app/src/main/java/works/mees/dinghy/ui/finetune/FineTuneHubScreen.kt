package works.mees.dinghy.ui.finetune

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The Fine-Tune hub (D-20). TWO large entries only — Motion and Extrusion (the D-01 failure-mode split:
 * Motion = what moves the head; Extrusion = what affects the filament). NO summary values on the hub
 * (D-20): the entries are pure navigation, not readouts. Each calls the typed [onNavigate] with its
 * [FineTuneGroup] (REVIEW #4 — explicit typed callback, no stringly route).
 *
 * Field-only [ScreenScaffold]; gutter Back = [Intent.Neutral] (15.2 C7 — plain nav spends no safety
 * color). Static styling only (Adreno-320 floor). Every color routes through [LocalTokens].
 *
 * @param onNavigate invoked with the tapped [FineTuneGroup] (17-06 wires the sub-route).
 * @param onBack     the neutral Back gutter exit.
 */
@Composable
fun FineTuneHubScreen(
    onNavigate: (FineTuneGroup) -> Unit,
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
                        text = "Fine-Tune",
                        color = t.text,
                        fontFamily = Geist,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fsSp(24f, t.fs).sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                    HubEntry(
                        iconRes = R.drawable.speed,
                        label = "Motion",
                        onClick = { onNavigate(FineTuneGroup.MOTION) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    HubEntry(
                        iconRes = R.drawable.output_circle,
                        label = "Extrusion",
                        onClick = { onNavigate(FineTuneGroup.EXTRUSION) },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
            },
            gutter = {
                Box(Modifier.fillMaxWidth().padding(8.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .clip(RoundedCornerShape(t.rCtrl))
                            .border(BorderStroke(2.dp, t.outline), RoundedCornerShape(t.rCtrl))
                            .clickable(onClick = onBack)
                            .padding(horizontal = 12.dp, vertical = 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.keyboard_return),
                                contentDescription = null,
                                tint = t.text,
                                modifier = Modifier.size(fsSp(24f, t.fs).dp),
                            )
                            Text(
                                text = "  Back",
                                color = t.text,
                                fontFamily = Geist,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = fsSp(18f, t.fs).sp,
                            )
                        }
                    }
                }
            },
        )
    }
}

/** One large accent-outlined hub entry: glyph over the label, centered. NO live value (D-20). */
@Composable
private fun HubEntry(
    @DrawableRes iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .clip(shape)
            .border(BorderStroke(2.dp, t.accentLine), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = t.accent2,
                modifier = Modifier.size(fsSp(48f, t.fs).dp),
            )
            Text(
                text = label,
                color = t.accent2,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(22f, t.fs).sp,
            )
        }
    }
}
