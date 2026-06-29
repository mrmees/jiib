package works.mees.jiib.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import works.mees.jiib.R

/**
 * The single source of truth for selectable fonts (and the release `res/font` subset list). Each
 * [AppFont] is a singleton so [ThemeTokens] equality stays reference-cheap. Geist / Geist Mono are
 * the defaults and the unknown-id fallback. Adding a family = drop static TTFs in `res/font` + one
 * row here (+ update `tools/verify_fonts.py`).
 *
 * All bundled faces are STATIC TTFs (variable-axis selection is API 26+, the floor is API 23) and
 * carry redistributable licenses (OFL 1.1, Apache-2.0, or the Microsoft OFL for Cascadia Code).
 * Data faces are genuinely monospaced — tabular numerics by glyph-metric construction. Variable
 * upstreams were instanced to statics with fonttools (every axis pinned; the output carries no
 * `fvar`).
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

    // ---- UI families (sorted by displayName after Geist) -------------------------

    val ALDRICH = AppFont(
        id = "aldrich", displayName = "Aldrich", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.aldrich_regular, FontWeight.Normal),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.aldrich_regular,
        ),
    )
    val ARVO = AppFont(
        id = "arvo", displayName = "Arvo", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.arvo_regular, FontWeight.Normal),
            Font(R.font.arvo_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.arvo_regular,
            FontWeight.Bold to R.font.arvo_bold,
        ),
    )
    val ASIMOVIAN = AppFont(
        id = "asimovian", displayName = "Asimovian", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.asimovian_regular, FontWeight.Normal),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.asimovian_regular,
        ),
    )
    val BAKBAK_ONE = AppFont(
        id = "bakbak_one", displayName = "Bakbak One", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.bakbak_one_regular, FontWeight.Normal),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.bakbak_one_regular,
        ),
    )
    val CARLITO = AppFont(
        id = "carlito", displayName = "Carlito", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.carlito_regular, FontWeight.Normal),
            Font(R.font.carlito_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.carlito_regular,
            FontWeight.Bold to R.font.carlito_bold,
        ),
    )
    val FAUSTINA = AppFont(
        id = "faustina", displayName = "Faustina", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.faustina_regular, FontWeight.Normal),
            Font(R.font.faustina_medium, FontWeight.Medium),
            Font(R.font.faustina_semibold, FontWeight.SemiBold),
            Font(R.font.faustina_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.faustina_regular,
            FontWeight.Medium to R.font.faustina_medium,
            FontWeight.SemiBold to R.font.faustina_semibold,
            FontWeight.Bold to R.font.faustina_bold,
        ),
    )
    val GENOS = AppFont(
        id = "genos", displayName = "Genos", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.genos_regular, FontWeight.Normal),
            Font(R.font.genos_medium, FontWeight.Medium),
            Font(R.font.genos_semibold, FontWeight.SemiBold),
            Font(R.font.genos_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.genos_regular,
            FontWeight.Medium to R.font.genos_medium,
            FontWeight.SemiBold to R.font.genos_semibold,
            FontWeight.Bold to R.font.genos_bold,
        ),
    )
    val GOLDMAN = AppFont(
        id = "goldman", displayName = "Goldman", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.goldman_regular, FontWeight.Normal),
            Font(R.font.goldman_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.goldman_regular,
            FontWeight.Bold to R.font.goldman_bold,
        ),
    )
    val KANIT = AppFont(
        id = "kanit", displayName = "Kanit", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.kanit_regular, FontWeight.Normal),
            Font(R.font.kanit_medium, FontWeight.Medium),
            Font(R.font.kanit_semibold, FontWeight.SemiBold),
            Font(R.font.kanit_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.kanit_regular,
            FontWeight.Medium to R.font.kanit_medium,
            FontWeight.SemiBold to R.font.kanit_semibold,
            FontWeight.Bold to R.font.kanit_bold,
        ),
    )
    val NOTO_SANS = AppFont(
        id = "noto_sans", displayName = "Noto Sans", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.noto_sans_regular, FontWeight.Normal),
            Font(R.font.noto_sans_medium, FontWeight.Medium),
            Font(R.font.noto_sans_semibold, FontWeight.SemiBold),
            Font(R.font.noto_sans_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.noto_sans_regular,
            FontWeight.Medium to R.font.noto_sans_medium,
            FontWeight.SemiBold to R.font.noto_sans_semibold,
            FontWeight.Bold to R.font.noto_sans_bold,
        ),
    )
    val OXANIUM = AppFont(
        id = "oxanium", displayName = "Oxanium", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.oxanium_regular, FontWeight.Normal),
            Font(R.font.oxanium_medium, FontWeight.Medium),
            Font(R.font.oxanium_semibold, FontWeight.SemiBold),
            Font(R.font.oxanium_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.oxanium_regular,
            FontWeight.Medium to R.font.oxanium_medium,
            FontWeight.SemiBold to R.font.oxanium_semibold,
            FontWeight.Bold to R.font.oxanium_bold,
        ),
    )
    val RASA = AppFont(
        id = "rasa", displayName = "Rasa", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.rasa_regular, FontWeight.Normal),
            Font(R.font.rasa_medium, FontWeight.Medium),
            Font(R.font.rasa_semibold, FontWeight.SemiBold),
            Font(R.font.rasa_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.rasa_regular,
            FontWeight.Medium to R.font.rasa_medium,
            FontWeight.SemiBold to R.font.rasa_semibold,
            FontWeight.Bold to R.font.rasa_bold,
        ),
    )
    val ROBOTO = AppFont(
        id = "roboto", displayName = "Roboto", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.roboto_regular, FontWeight.Normal),
            Font(R.font.roboto_medium, FontWeight.Medium),
            Font(R.font.roboto_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.roboto_regular,
            FontWeight.Medium to R.font.roboto_medium,
            FontWeight.Bold to R.font.roboto_bold,
        ),
    )
    val SARPANCH = AppFont(
        id = "sarpanch", displayName = "Sarpanch", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.sarpanch_regular, FontWeight.Normal),
            Font(R.font.sarpanch_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.sarpanch_regular,
            FontWeight.Bold to R.font.sarpanch_bold,
        ),
    )
    val ZILLA_SLAB = AppFont(
        id = "zilla_slab", displayName = "Zilla Slab", kind = FontKind.Ui,
        family = FontFamily(
            Font(R.font.zilla_slab_regular, FontWeight.Normal),
            Font(R.font.zilla_slab_medium, FontWeight.Medium),
            Font(R.font.zilla_slab_semibold, FontWeight.SemiBold),
            Font(R.font.zilla_slab_bold, FontWeight.Bold),
        ),
        weights = mapOf(
            FontWeight.Normal to R.font.zilla_slab_regular,
            FontWeight.Medium to R.font.zilla_slab_medium,
            FontWeight.SemiBold to R.font.zilla_slab_semibold,
            FontWeight.Bold to R.font.zilla_slab_bold,
        ),
    )

    // ---- Data (monospaced) families (sorted by displayName after Geist Mono) ------

    val ANONYMOUS_PRO = AppFont(
        id = "anonymous_pro", displayName = "Anonymous Pro", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.anonymous_pro_medium, FontWeight.Medium),
            Font(R.font.anonymous_pro_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.anonymous_pro_medium,
            FontWeight.SemiBold to R.font.anonymous_pro_semibold,
        ),
    )
    val CASCADIA_CODE = AppFont(
        id = "cascadia_code", displayName = "Cascadia Code", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.cascadia_code_medium, FontWeight.Medium),
            Font(R.font.cascadia_code_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.cascadia_code_medium,
            FontWeight.SemiBold to R.font.cascadia_code_semibold,
        ),
    )
    val COURIER_PRIME = AppFont(
        id = "courier_prime", displayName = "Courier Prime", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.courier_prime_medium, FontWeight.Medium),
            Font(R.font.courier_prime_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.courier_prime_medium,
            FontWeight.SemiBold to R.font.courier_prime_semibold,
        ),
    )
    val DATATYPE = AppFont(
        id = "datatype", displayName = "Datatype", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.datatype_medium, FontWeight.Medium),
            Font(R.font.datatype_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.datatype_medium,
            FontWeight.SemiBold to R.font.datatype_semibold,
        ),
    )
    val KODE_MONO = AppFont(
        id = "kode_mono", displayName = "Kode Mono", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.kode_mono_medium, FontWeight.Medium),
            Font(R.font.kode_mono_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.kode_mono_medium,
            FontWeight.SemiBold to R.font.kode_mono_semibold,
        ),
    )
    val M_PLUS_1_CODE = AppFont(
        id = "m_plus_1_code", displayName = "M PLUS 1 Code", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.m_plus_1_code_medium, FontWeight.Medium),
            Font(R.font.m_plus_1_code_semibold, FontWeight.SemiBold),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.m_plus_1_code_medium,
            FontWeight.SemiBold to R.font.m_plus_1_code_semibold,
        ),
    )
    val NOVA_MONO = AppFont(
        id = "nova_mono", displayName = "Nova Mono", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.nova_mono_medium, FontWeight.Medium),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.nova_mono_medium,
        ),
    )
    val SHARE_TECH_MONO = AppFont(
        id = "share_tech_mono", displayName = "Share Tech Mono", kind = FontKind.Data,
        family = FontFamily(
            Font(R.font.share_tech_mono_medium, FontWeight.Medium),
        ),
        weights = mapOf(
            FontWeight.Medium to R.font.share_tech_mono_medium,
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

    val DEFAULT_UI = GEIST
    val DEFAULT_DATA = GEIST_MONO

    val ui: List<AppFont> = listOf(
        GEIST,
        ALDRICH, ARVO, ASIMOVIAN, BAKBAK_ONE, CARLITO, FAUSTINA, GENOS, GOLDMAN,
        KANIT, NOTO_SANS, OXANIUM, RASA, ROBOTO, SARPANCH, ZILLA_SLAB,
    )
    val data: List<AppFont> = listOf(
        GEIST_MONO,
        ANONYMOUS_PRO, CASCADIA_CODE, COURIER_PRIME, DATATYPE, KODE_MONO, M_PLUS_1_CODE,
        NOVA_MONO, SHARE_TECH_MONO, SPACE_MONO,
    )
    val all: List<AppFont> = ui + data

    fun byId(id: String?): AppFont? = all.firstOrNull { it.id == id }
    fun uiOrDefault(id: String?): AppFont = ui.firstOrNull { it.id == id } ?: DEFAULT_UI
    fun dataOrDefault(id: String?): AppFont = data.firstOrNull { it.id == id } ?: DEFAULT_DATA
}
