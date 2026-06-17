package works.mees.dinghy.designsystem.components

import org.junit.Assert.assertThrows
import org.junit.Test
import works.mees.dinghy.designsystem.icons.DinghyIcons

class FootActionTest {
    @Test fun blankLabel_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            FootAction(label = "", icon = DinghyIcons.Back, onClick = {})
        }
    }
    @Test fun drawableIcon_throws() {
        // LauncherSpool is drawable-backed (IconRef.Drawable) — not ligature-backed.
        assertThrows(IllegalArgumentException::class.java) {
            FootAction(label = "x", icon = DinghyIcons.LauncherSpool, onClick = {})
        }
    }
}
