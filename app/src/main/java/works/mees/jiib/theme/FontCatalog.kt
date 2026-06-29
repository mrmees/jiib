package works.mees.jiib.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import works.mees.jiib.R

/**
 * The single source of truth for selectable fonts (and the release `res/font` subset list). Each
 * [AppFont] is a singleton so [ThemeTokens] equality stays reference-cheap. Geist / Geist Mono are
 * the defaults and the unknown-id fallback. Adding a family = drop static TTFs in `res/font` + one
 * row here (+ update `tools/verify_fonts.py`, Task 12).
 *
 * All bundled faces are STATIC TTFs (variable-axis selection is API 26+, the floor is API 23) and
 * carry redistributable licenses (OFL 1.1, Apache-2.0, or DejaVu's permissive Bitstream Vera
 * license). Data faces are genuinely monospaced — tabular numerics by glyph-metric construction,
 * NO `fontFeatureSettings("tnum")` (see [Geist]). Variable upstreams were instanced to statics with
 * fonttools (every axis pinned; the output carries no `fvar`).
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

    // ---- UI families (Task 12) ----------------------------------------------------------------

    val INTER = AppFont(
        id = "inter", displayName = "Inter", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.inter_regular, FontWeight.Normal),
            Font(R.font.inter_medium, FontWeight.Medium),
            Font(R.font.inter_semibold, FontWeight.SemiBold),
            Font(R.font.inter_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.inter_regular,
            FontWeight.Medium to R.font.inter_medium,
            FontWeight.SemiBold to R.font.inter_semibold,
            FontWeight.Bold to R.font.inter_bold,
        ),
    )
    val IBM_PLEX_SANS = AppFont(
        id = "ibm_plex_sans", displayName = "IBM Plex Sans", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
            Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
            Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
            Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.ibm_plex_sans_regular,
            FontWeight.Medium to R.font.ibm_plex_sans_medium,
            FontWeight.SemiBold to R.font.ibm_plex_sans_semibold,
            FontWeight.Bold to R.font.ibm_plex_sans_bold,
        ),
    )
    val SOURCE_SANS_3 = AppFont(
        id = "source_sans_3", displayName = "Source Sans 3", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.source_sans_3_regular, FontWeight.Normal),
            Font(R.font.source_sans_3_semibold, FontWeight.SemiBold),
            Font(R.font.source_sans_3_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.source_sans_3_regular,
            FontWeight.SemiBold to R.font.source_sans_3_semibold,
            FontWeight.Bold to R.font.source_sans_3_bold,
        ),
    )
    val RUBIK = AppFont(
        id = "rubik", displayName = "Rubik", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.rubik_regular, FontWeight.Normal),
            Font(R.font.rubik_medium, FontWeight.Medium),
            Font(R.font.rubik_semibold, FontWeight.SemiBold),
            Font(R.font.rubik_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.rubik_regular,
            FontWeight.Medium to R.font.rubik_medium,
            FontWeight.SemiBold to R.font.rubik_semibold,
            FontWeight.Bold to R.font.rubik_bold,
        ),
    )
    val WORK_SANS = AppFont(
        id = "work_sans", displayName = "Work Sans", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.work_sans_regular, FontWeight.Normal),
            Font(R.font.work_sans_medium, FontWeight.Medium),
            Font(R.font.work_sans_semibold, FontWeight.SemiBold),
            Font(R.font.work_sans_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.work_sans_regular,
            FontWeight.Medium to R.font.work_sans_medium,
            FontWeight.SemiBold to R.font.work_sans_semibold,
            FontWeight.Bold to R.font.work_sans_bold,
        ),
    )
    val DM_SANS = AppFont(
        id = "dm_sans", displayName = "DM Sans", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.dm_sans_regular, FontWeight.Normal),
            Font(R.font.dm_sans_medium, FontWeight.Medium),
            Font(R.font.dm_sans_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.dm_sans_regular,
            FontWeight.Medium to R.font.dm_sans_medium,
            FontWeight.Bold to R.font.dm_sans_bold,
        ),
    )
    val MANROPE = AppFont(
        id = "manrope", displayName = "Manrope", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.manrope_regular, FontWeight.Normal),
            Font(R.font.manrope_medium, FontWeight.Medium),
            Font(R.font.manrope_semibold, FontWeight.SemiBold),
            Font(R.font.manrope_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.manrope_regular,
            FontWeight.Medium to R.font.manrope_medium,
            FontWeight.SemiBold to R.font.manrope_semibold,
            FontWeight.Bold to R.font.manrope_bold,
        ),
    )
    val NUNITO_SANS = AppFont(
        id = "nunito_sans", displayName = "Nunito Sans", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.nunito_sans_regular, FontWeight.Normal),
            Font(R.font.nunito_sans_semibold, FontWeight.SemiBold),
            Font(R.font.nunito_sans_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.nunito_sans_regular,
            FontWeight.SemiBold to R.font.nunito_sans_semibold,
            FontWeight.Bold to R.font.nunito_sans_bold,
        ),
    )
    val OPEN_SANS = AppFont(
        id = "open_sans", displayName = "Open Sans", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.open_sans_regular, FontWeight.Normal),
            Font(R.font.open_sans_semibold, FontWeight.SemiBold),
            Font(R.font.open_sans_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.open_sans_regular,
            FontWeight.SemiBold to R.font.open_sans_semibold,
            FontWeight.Bold to R.font.open_sans_bold,
        ),
    )
    val ATKINSON_HYPERLEGIBLE = AppFont(
        id = "atkinson_hyperlegible", displayName = "Atkinson Hyperlegible", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
            Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.atkinson_hyperlegible_regular,
            FontWeight.Bold to R.font.atkinson_hyperlegible_bold,
        ),
    )
    val LEXEND = AppFont(
        id = "lexend", displayName = "Lexend", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.lexend_regular, FontWeight.Normal),
            Font(R.font.lexend_medium, FontWeight.Medium),
            Font(R.font.lexend_semibold, FontWeight.SemiBold),
            Font(R.font.lexend_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.lexend_regular,
            FontWeight.Medium to R.font.lexend_medium,
            FontWeight.SemiBold to R.font.lexend_semibold,
            FontWeight.Bold to R.font.lexend_bold,
        ),
    )

    // ---- Data (monospaced) families (Task 12) -------------------------------------------------

    val JETBRAINS_MONO = AppFont(
        id = "jetbrains_mono", displayName = "JetBrains Mono", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
            Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.jetbrains_mono_medium,
            FontWeight.SemiBold to R.font.jetbrains_mono_semibold,
        ),
    )
    val IBM_PLEX_MONO = AppFont(
        id = "ibm_plex_mono", displayName = "IBM Plex Mono", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
            Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.ibm_plex_mono_medium,
            FontWeight.SemiBold to R.font.ibm_plex_mono_semibold,
        ),
    )
    val SOURCE_CODE_PRO = AppFont(
        id = "source_code_pro", displayName = "Source Code Pro", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.source_code_pro_medium, FontWeight.Medium),
            Font(R.font.source_code_pro_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.source_code_pro_medium,
            FontWeight.SemiBold to R.font.source_code_pro_semibold,
        ),
    )
    val ROBOTO_MONO = AppFont(
        id = "roboto_mono", displayName = "Roboto Mono", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.roboto_mono_medium, FontWeight.Medium),
            Font(R.font.roboto_mono_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.roboto_mono_medium,
            FontWeight.SemiBold to R.font.roboto_mono_semibold,
        ),
    )
    val SPACE_MONO = AppFont(
        id = "space_mono", displayName = "Space Mono", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.space_mono_medium, FontWeight.Medium),
            Font(R.font.space_mono_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.space_mono_medium,
            FontWeight.SemiBold to R.font.space_mono_semibold,
        ),
    )
    val INCONSOLATA = AppFont(
        id = "inconsolata", displayName = "Inconsolata", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.inconsolata_medium, FontWeight.Medium),
            Font(R.font.inconsolata_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.inconsolata_medium,
            FontWeight.SemiBold to R.font.inconsolata_semibold,
        ),
    )
    val DEJAVU_SANS_MONO = AppFont(
        id = "dejavu_sans_mono", displayName = "DejaVu Sans Mono", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.dejavu_sans_mono_medium, FontWeight.Medium),
            Font(R.font.dejavu_sans_mono_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.dejavu_sans_mono_medium,
            FontWeight.SemiBold to R.font.dejavu_sans_mono_semibold,
        ),
    )
    val OVERPASS_MONO = AppFont(
        id = "overpass_mono", displayName = "Overpass Mono", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.overpass_mono_medium, FontWeight.Medium),
            Font(R.font.overpass_mono_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.overpass_mono_medium,
            FontWeight.SemiBold to R.font.overpass_mono_semibold,
        ),
    )

    val DEFAULT_UI = GEIST
    val DEFAULT_DATA = GEIST_MONO

    val ui: List<AppFont> = listOf(
        GEIST, INTER, IBM_PLEX_SANS, SOURCE_SANS_3, RUBIK, WORK_SANS, DM_SANS, MANROPE,
        NUNITO_SANS, OPEN_SANS, ATKINSON_HYPERLEGIBLE, LEXEND,
    )
    val data: List<AppFont> = listOf(
        GEIST_MONO, JETBRAINS_MONO, IBM_PLEX_MONO, SOURCE_CODE_PRO, ROBOTO_MONO, SPACE_MONO,
        INCONSOLATA, DEJAVU_SANS_MONO, OVERPASS_MONO,
    )
    val all: List<AppFont> = ui + data

    fun byId(id: String?): AppFont? = all.firstOrNull { it.id == id }
    fun uiOrDefault(id: String?): AppFont = ui.firstOrNull { it.id == id } ?: DEFAULT_UI
    fun dataOrDefault(id: String?): AppFont = data.firstOrNull { it.id == id } ?: DEFAULT_DATA
}
