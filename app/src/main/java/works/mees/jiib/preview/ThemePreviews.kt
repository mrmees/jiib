package works.mees.jiib.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.jiib.theme.ThemePrefs
import works.mees.jiib.theme.ThemeResolver
import works.mees.jiib.ui.screen.ThemeContent
import works.mees.jiib.ui.screen.ThemeRow

private val DEFAULT = ThemePrefs.TUPLE_DEFAULT
private val SIMPLE = ThemePrefs.TUPLE_DEFAULT.copy(paletteMode = ThemeResolver.MODE_SIMPLE)

@Preview(name = "Theme · resting · dark", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable private fun ThemeRestingDark() = PreviewBox(colorfulDark) {
    ThemeContent(working = DEFAULT, saved = DEFAULT, hasDraft = false, isPrinting = false,
        onEmergencyStop = null, onBack = {}, onDarkToggle = {}, onPaletteMode = {},
        container = null, hasActive = false, initialSelected = null)
}

@Preview(name = "Theme · palette · dark", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable private fun ThemePaletteDark() = PreviewBox(colorfulDark) {
    ThemeContent(working = DEFAULT, saved = DEFAULT, hasDraft = false, isPrinting = false,
        onEmergencyStop = null, onBack = {}, onDarkToggle = {}, onPaletteMode = {},
        container = null, hasActive = false, initialSelected = ThemeRow.PaletteMode)
}

@Preview(name = "Theme · colors · light", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable private fun ThemeColorsLight() = PreviewBox(colorfulLight) {
    val t = DEFAULT.copy(dark = false)
    ThemeContent(working = t, saved = t, hasDraft = false, isPrinting = false,
        onEmergencyStop = null, onBack = {}, onDarkToggle = {}, onPaletteMode = {},
        container = null, hasActive = false, initialSelected = ThemeRow.Colors)
}

@Preview(name = "Theme · colors · simple", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable private fun ThemeColorsSimple() = PreviewBox(colorfulDark) {
    ThemeContent(working = SIMPLE, saved = SIMPLE, hasDraft = false, isPrinting = false,
        onEmergencyStop = null, onBack = {}, onDarkToggle = {}, onPaletteMode = {},
        container = null, hasActive = false, initialSelected = ThemeRow.Colors)
}

@Preview(name = "Theme · seed · dirty(red back)", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable private fun ThemeSeedDirty() = PreviewBox(colorfulDark) {
    ThemeContent(working = DEFAULT, saved = DEFAULT, hasDraft = true, isPrinting = false,
        onEmergencyStop = null, onBack = {}, onDarkToggle = {}, onPaletteMode = {},
        container = null, hasActive = false, initialSelected = ThemeRow.Seed)
}

@Preview(name = "Theme · resting · landscape", device = NEXUS7, showBackground = true)
@Composable private fun ThemeRestingLandscape() = PreviewBox(colorfulDark) {
    ThemeContent(working = DEFAULT, saved = DEFAULT, hasDraft = false, isPrinting = false,
        onEmergencyStop = null, onBack = {}, onDarkToggle = {}, onPaletteMode = {},
        container = null, hasActive = false, initialSelected = ThemeRow.Colors)
}
