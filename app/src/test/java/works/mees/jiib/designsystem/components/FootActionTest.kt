package works.mees.jiib.designsystem.components

import org.junit.Assert.assertThrows
import org.junit.Test
import works.mees.jiib.designsystem.icons.JiibIcons

class FootActionTest {
    @Test fun blankLabel_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            FootAction(label = "", icon = JiibIcons.Back, onClick = {})
        }
    }
    @Test fun drawableIcon_throws() {
        // LauncherSpool is drawable-backed (IconRef.Drawable) — not ligature-backed.
        assertThrows(IllegalArgumentException::class.java) {
            FootAction(label = "x", icon = JiibIcons.LauncherSpool, onClick = {})
        }
    }
}
