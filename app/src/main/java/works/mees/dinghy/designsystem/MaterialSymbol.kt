package works.mees.dinghy.designsystem

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import works.mees.dinghy.R
import works.mees.dinghy.theme.compose.LocalTokens

/**
 * Google **Material Symbols** (Outlined) as a glyph font (`res/font/material_symbols_outlined.ttf`).
 * One font supplies every icon the app needs — rendered by **ligature**: the icon's name typed as text
 * resolves to its glyph (e.g. `"settings"` → the gear), so adding an icon costs no new asset. Source +
 * usage: https://developers.google.com/fonts/docs/material_symbols (icon names are the ligature keys).
 *
 * NOTE (size): the upstream variable font is ~10 MB (all ~3k icons). Before release it is SUBSET to only
 * the names this app references — see `tools/subset-symbols` / the icon-name inventory. Keep this family
 * the single source so the subset list is discoverable.
 */
val MaterialSymbols = FontFamily(Font(R.font.material_symbols_outlined))

/**
 * Render one Material Symbol by its ligature [name] (e.g. `"thermostat"`, `"open_with"`,
 * `"power_settings_new"`). Tinted via the semantic token system (defaults to the primary text role —
 * THEME-01). [sizeSp] is the glyph size in sp (the caller scales for `--fs` where appropriate).
 */
@Composable
fun MaterialSymbol(
    name: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalTokens.current.text,
    sizeSp: Float = 32f,
) {
    Text(
        text = name,
        modifier = modifier,
        color = tint,
        fontFamily = MaterialSymbols,
        fontSize = sizeSp.sp,
    )
}
