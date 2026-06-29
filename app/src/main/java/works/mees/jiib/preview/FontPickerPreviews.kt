package works.mees.jiib.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.theme.FontCatalog
import works.mees.jiib.ui.screen.FontPickerContent

@Preview(name = "FontPicker · Interface · dark", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun FontPickerInterfaceDark() = PreviewBox(colorfulDark) {
    FontPickerContent(
        title = "Interface Font",
        icon = JiibIcons.Serif,
        fonts = FontCatalog.ui,
        selected = FontCatalog.DEFAULT_UI,
        focusCaption = "Choose the font for buttons, labels, and titles.",
        onSelect = {},
        onBack = {},
    )
}

@Preview(name = "FontPicker · Data · dark", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun FontPickerDataDark() = PreviewBox(colorfulDark) {
    FontPickerContent(
        title = "Data Font",
        icon = JiibIcons.DataFont,
        fonts = FontCatalog.data,
        selected = FontCatalog.DEFAULT_DATA,
        focusCaption = "Choose the font for live numbers (temps, positions, progress). Monospaced for steady digits.",
        onSelect = {},
        onBack = {},
    )
}

@Preview(name = "FontPicker · Interface · light", device = NEXUS7_PORTRAIT, showBackground = true)
@Composable
private fun FontPickerInterfaceLight() = PreviewBox(colorfulLight) {
    FontPickerContent(
        title = "Interface Font",
        icon = JiibIcons.Serif,
        fonts = FontCatalog.ui,
        selected = FontCatalog.DEFAULT_UI,
        focusCaption = "Choose the font for buttons, labels, and titles.",
        onSelect = {},
        onBack = {},
    )
}

@Preview(name = "FontPicker · landscape", device = NEXUS7, showBackground = true)
@Composable
private fun FontPickerLandscape() = PreviewBox(colorfulDark) {
    FontPickerContent(
        title = "Interface Font",
        icon = JiibIcons.Serif,
        fonts = FontCatalog.ui,
        selected = FontCatalog.DEFAULT_UI,
        focusCaption = "Choose the font for buttons, labels, and titles.",
        onSelect = {},
        onBack = {},
    )
}
