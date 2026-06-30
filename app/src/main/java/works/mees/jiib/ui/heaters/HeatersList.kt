package works.mees.jiib.ui.heaters

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import works.mees.jiib.R
import works.mees.jiib.command.ApplyHeatPresetArgs
import works.mees.jiib.command.CommandDispatcher
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.components.ListRowLabel
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.ui.spool.parseNormalizedHex

/**
 * Route-level helper: fire one Heaters selection. Distinct keys per row (`heat_off`/`heat_spool`/
 * `preset_<id>`) guarantee OFF is never debounce-dropped (nothing else shares those keys).
 */
fun dispatchHeat(dispatcher: CommandDispatcher?, dispatch: HeatDispatch) {
    when (dispatch) {
        HeatDispatch.TurnOffAll -> dispatcher?.dispatch(CommandRegistry.cooldown, Unit)
        is HeatDispatch.ApplyPreset ->
            dispatcher?.dispatch(CommandRegistry.applyHeatPreset, ApplyHeatPresetArgs(dispatch.setpoints, dispatch.key))
    }
}

/**
 * The unified Heaters Field takeover. Renders [rows] (OFF, loaded-spool, presets); tapping a row
 * calls [onApply] with its dispatch then [onApplied] (the caller returns the Field to its normal
 * list). [onBack] dismisses without applying.
 */
@Composable
fun HeatersList(
    rows: List<HeatersRow>,
    onApply: (HeatDispatch) -> Unit,
    onApplied: () -> Unit,
    onBack: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(modifier.fillMaxSize()) {
        ListBlock(modifier = Modifier.weight(1f)) {
            items(rows, key = { it.key }) { row ->
                val tint = row.tintHex?.let { parseNormalizedHex(it) } ?: t.accent
                ListRow(
                    selected = false,
                    onClick = { onApply(row.dispatch); onApplied() },
                    uDp = uDp,
                    leadingContent = { ListRowIcon(icon = row.icon, uDp = uDp, tint = tint) },
                    trailingContent = row.summary?.let { s ->
                        { Text(text = s, style = JiibType.dataInline.toTextStyle(t), color = t.text2, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                ) {
                    ListRowLabel(row.label)
                }
            }
        }
        FootButtonBar(
            uDp = uDp,
            actions = listOf(
                FootAction(
                    label = stringResource(R.string.cd_back),
                    icon = JiibIcons.Back,
                    onClick = onBack,
                    intent = Intent.Accent,
                    contentDescription = stringResource(R.string.cd_back),
                ),
            ),
        )
    }
}
