package works.mees.dinghy.designsystem.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import works.mees.dinghy.control.ControlSpecs
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.theme.compose.DinghyTheme

@RunWith(AndroidJUnit4::class)
class FootButtonBarRenderTest {
    @get:Rule val rule = createComposeRule()
    private fun action(label: String) = FootAction(label = label, icon = DinghyIcons.Back, onClick = {})

    @Test fun twoActions_renderLabels() {
        rule.setContent { DinghyTheme(ThemeResolver()) {
            FootButtonBar(uDp = 48.dp, actions = listOf(action("Alpha"), action("Beta")))
        } }
        rule.onNodeWithText("Alpha").assertIsDisplayed()
        rule.onNodeWithText("Beta").assertIsDisplayed()
    }

    @Test fun threeActions_renderNoLabels() {
        rule.setContent { DinghyTheme(ThemeResolver()) {
            FootButtonBar(uDp = 48.dp, actions = listOf(action("Alpha"), action("Beta"), action("Gamma")))
        } }
        rule.onAllNodesWithText("Alpha").assertCountEquals(0) // ≥3 → icon-only
        // a11y path: contentDescription must still be present (label becomes the fallback cd)
        rule.onAllNodesWithContentDescription("Alpha").assertCountEquals(1)
        rule.onAllNodesWithContentDescription("Beta").assertCountEquals(1)
        rule.onAllNodesWithContentDescription("Gamma").assertCountEquals(1)
    }

    @Test fun footAction_nullLabelSpec_doesNotThrow() {
        // spoolLoad has labelRes=null and contentDescriptionRes=cd_spool_load — footAction must
        // fall through to contentDescriptionRes without throwing (Codex-F1 no-label path).
        rule.setContent { DinghyTheme(ThemeResolver()) {
            FootButtonBar(
                uDp = 48.dp,
                actions = listOf(
                    footAction(ControlSpecs.spoolLoad, onClick = {}),
                    footAction(ControlSpecs.spoolScan, onClick = {}),
                ),
            )
        } }
        // 2 actions → icon+label mode; the label is sourced from contentDescriptionRes.
        // Assert at least the spool-load a11y node is present (cd_spool_load = "Load this spool").
        rule.onAllNodesWithContentDescription("Load this spool").assertCountEquals(1)
    }
}
