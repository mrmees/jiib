package works.mees.jiib.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import works.mees.jiib.R
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.theme.FontScale
import works.mees.jiib.theme.compose.LocalTokens

/** App-global S/M/L text-size selector — selected segment uses accentSoft fill (the selection convention). */
@Composable
fun TextSizeSelector(
    selected: FontScale,
    onSelect: (FontScale) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FontScale.entries.forEach { choice ->
            val isSel = choice == selected
            OutlinedControl(
                label = stringResource(
                    when (choice) {
                        FontScale.S -> R.string.settings_text_size_s
                        FontScale.M -> R.string.settings_text_size_m
                        FontScale.L -> R.string.settings_text_size_l
                    },
                ),
                onClick = { onSelect(choice) },
                modifier = Modifier.weight(1f),
                intent = if (isSel) Intent.Accent else Intent.Neutral,
                fill = if (isSel) t.accentSoft else null,
            )
        }
    }
}
