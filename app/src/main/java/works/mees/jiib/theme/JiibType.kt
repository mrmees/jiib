package works.mees.jiib.theme

import androidx.compose.ui.text.font.FontWeight

/** Which font family a role draws in. Ui → [Geist]; Data → [GeistMono] (values that came from the printer). */
enum class TypeRole { Ui, Data }

/**
 * One named text class — the single source of truth for `(family, size, weight)`. The concrete
 * [androidx.compose.ui.text.font.FontFamily] / [android.graphics.Typeface] are DERIVED from [role]
 * at each toolkit boundary, never stored here, so this type carries no rendering dependency (only the
 * plain [FontWeight] value). See `docs/ui_design/THEMING.md` §"The type ramp".
 *
 * Sizes are base sp values; the renderer scales them with `fsSp(baseSp, fs)` for the S/M/L `--fs`
 * setting. [maxSp]/[minSp] are set ONLY for shrink-to-fit roles ([JiibType.focusHero]).
 */
data class TextRole(
    val role: TypeRole,
    val baseSp: Float,
    val weight: FontWeight,
    val maxSp: Float? = null,
    val minSp: Float? = null,
)

/**
 * The app's named text classes (R11 type ramp made enforceable). A call site says WHAT the text is;
 * the role owns the family/size/weight. Adding/retuning a tier is a one-line edit here.
 */
object JiibType {
    // Geist — system / UI text
    val screenTitle = TextRole(TypeRole.Ui, 22f, FontWeight.SemiBold)
    val focusHeader = TextRole(TypeRole.Ui, 20f, FontWeight.SemiBold)
    val listLabel   = TextRole(TypeRole.Ui, 20f, FontWeight.SemiBold)
    val buttonLabel = TextRole(TypeRole.Ui, 20f, FontWeight.SemiBold)
    val body        = TextRole(TypeRole.Ui, 20f, FontWeight.Medium)
    val caption     = TextRole(TypeRole.Ui, 15f, FontWeight.Medium)

    // Geist Mono — live printer data
    val focusHero   = TextRole(TypeRole.Data, baseSp = 40f, weight = FontWeight.Bold, maxSp = 40f, minSp = 15f)

    /** Geist (UI) mirror of [focusHero]'s envelope — for a Focus-hero LABEL beside a Mono value. The
     *  maxSp is load-bearing for the ramp-tier test exemption (40 is not a sanctioned fixed tier). */
    val focusHeroLabel = TextRole(TypeRole.Ui, baseSp = 40f, weight = FontWeight.SemiBold, maxSp = 40f, minSp = 15f)
    val statValue   = TextRole(TypeRole.Data, 26f, FontWeight.SemiBold)
    val dataInline  = TextRole(TypeRole.Data, 20f, FontWeight.Medium)
    val dataMeta    = TextRole(TypeRole.Data, 15f, FontWeight.Medium)
    val consoleLine = TextRole(TypeRole.Data, 15f, FontWeight.Medium)

    /** All roles, for tests and tooling. */
    val all: List<TextRole> = listOf(
        screenTitle, focusHeader, listLabel, buttonLabel, body, caption,
        focusHero, focusHeroLabel, statValue, dataInline, dataMeta, consoleLine,
    )
}
