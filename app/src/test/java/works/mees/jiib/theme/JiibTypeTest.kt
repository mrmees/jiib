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
    fun toTextStyleResolvesFamilyFromTokens() {
        val tokens = TokensDark.copy(fs = 1.0f)
        // default tokens → default catalog families
        assertEquals(FontCatalog.DEFAULT_DATA.family, JiibType.statValue.toTextStyle(tokens).fontFamily)
        assertEquals(FontCatalog.DEFAULT_UI.family, JiibType.listLabel.toTextStyle(tokens).fontFamily)
        assertEquals(26f, JiibType.statValue.toTextStyle(tokens).fontSize.value)

        // a non-default UI face in tokens must flow through to a UI role
        val custom = tokens.copy(uiFont = FontCatalog.GEIST_MONO) // stand-in distinct family
        assertEquals(FontCatalog.GEIST_MONO.family, JiibType.listLabel.toTextStyle(custom).fontFamily)
    }
}
