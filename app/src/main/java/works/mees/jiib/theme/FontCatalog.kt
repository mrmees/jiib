package works.mees.jiib.theme

import androidx.compose.ui.text.font.FontWeight
import works.mees.jiib.R

/**
 * The single source of truth for selectable fonts (and the release `res/font` subset list). Each
 * [AppFont] is a singleton so [ThemeTokens] equality stays reference-cheap. Geist / Geist Mono are
 * the defaults and the unknown-id fallback. Adding a family = drop static TTFs in `res/font` + one
 * row here (+ update `tools/verify_fonts.py`, Task 12).
 */
object FontCatalog {
    val GEIST = AppFont(
        id = "geist", displayName = "Geist", kind = FontKind.Ui, family = Geist,
        weights = mapOf(
            FontWeight.Normal to R.font.geist_regular,
            FontWeight.Medium to R.font.geist_medium,
            FontWeight.SemiBold to R.font.geist_semibold,
            FontWeight.Bold to R.font.geist_bold,
        ),
    )
    val GEIST_MONO = AppFont(
        id = "geist_mono", displayName = "Geist Mono", kind = FontKind.Data, family = GeistMono,
        weights = mapOf(
            FontWeight.Medium to R.font.geist_mono_medium,
            FontWeight.SemiBold to R.font.geist_mono_semibold,
        ),
    )

    val DEFAULT_UI = GEIST
    val DEFAULT_DATA = GEIST_MONO

    // Task 11 appends the wide library to these two lists.
    val ui: List<AppFont> = listOf(GEIST)
    val data: List<AppFont> = listOf(GEIST_MONO)
    val all: List<AppFont> = ui + data

    fun byId(id: String?): AppFont? = all.firstOrNull { it.id == id }
    fun uiOrDefault(id: String?): AppFont = ui.firstOrNull { it.id == id } ?: DEFAULT_UI
    fun dataOrDefault(id: String?): AppFont = data.firstOrNull { it.id == id } ?: DEFAULT_DATA
}
