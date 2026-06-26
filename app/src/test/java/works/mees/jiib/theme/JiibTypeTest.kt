package works.mees.jiib.theme

import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.theme.compose.toTextStyle

class JiibTypeTest {

    private val sanctionedTiers = setOf(15f, 20f, 22f, 24f, 26f)

    @Test
    fun everyFixedRoleSitsOnASanctionedRampTier() {
        val offenders = JiibType.all
            .filter { it.maxSp == null } // shrink-to-fit roles validated separately
            .filterNot { it.baseSp in sanctionedTiers }
        assertTrue("Roles off the ramp: ${offenders.map { it.baseSp }}", offenders.isEmpty())
    }

    @Test
    fun focusHeroShrinksBetweenFloorAndMax() {
        val hero = JiibType.focusHero
        assertEquals(40f, hero.maxSp)
        assertEquals(15f, hero.minSp)
        assertEquals(TypeRole.Data, hero.role)
    }

    @Test
    fun focusHeroLabelIsGeistHeroEnvelope() {
        val role = JiibType.focusHeroLabel
        assertEquals(TypeRole.Ui, role.role)        // Geist (labels are UI text)
        assertEquals(40f, role.maxSp)
        assertEquals(15f, role.minSp)
        assertTrue("focusHeroLabel must be registered in JiibType.all", JiibType.focusHeroLabel in JiibType.all)
    }

    @Test
    fun dataRolesAreDataUiRolesAreUi() {
        assertEquals(TypeRole.Data, JiibType.statValue.role)
        assertEquals(TypeRole.Data, JiibType.dataInline.role)
        assertEquals(TypeRole.Data, JiibType.consoleLine.role)
        assertEquals(TypeRole.Ui, JiibType.listLabel.role)
        assertEquals(TypeRole.Ui, JiibType.caption.role)
    }

    @Test
    fun captionIsAtTheMetadataFloor() {
        assertEquals(15f, JiibType.caption.baseSp)
        assertEquals(15f, JiibType.dataMeta.baseSp)
    }

    @Test
    fun toTextStyleResolvesFamilyAndScaledSize() {
        val tokens = TokensDark.copy(fs = 1.0f) // baked default-seed fail-safe (theme/BakedTokens.kt)
        val style = JiibType.statValue.toTextStyle(tokens)
        assertEquals(GeistMono, style.fontFamily)
        assertEquals(26f, style.fontSize.value)   // fs = 1.0 → 26 * 1.0
        assertEquals(FontWeight.SemiBold, style.fontWeight)

        val uiStyle = JiibType.listLabel.toTextStyle(tokens)
        assertEquals(Geist, uiStyle.fontFamily)
    }
}
