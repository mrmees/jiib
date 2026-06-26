package works.mees.jiib.designsystem.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import works.mees.jiib.R
import works.mees.jiib.designsystem.icons.DinghyIcon
import works.mees.jiib.designsystem.icons.DinghyIcons
import works.mees.jiib.designsystem.icons.IconRef

/**
 * Host tests for [ligatureOf] — the DinghyIcon-to-ligature-name bridge used by the
 * DinghyIcon-aware [OutlinedControl] overload. Pure logic; no Compose runtime needed.
 */
class OutlinedControlIconTest {

    @Test
    fun `ligatureOf Sort returns sort`() {
        assertEquals("sort", ligatureOf(DinghyIcons.Sort))
    }

    @Test
    fun `ligatureOf ExpandCircleUp returns expand_circle_up`() {
        assertEquals("expand_circle_up", ligatureOf(DinghyIcons.ExpandCircleUp))
    }

    @Test
    fun `ligatureOf Drawable-backed icon throws IllegalArgumentException`() {
        val drawableIcon = DinghyIcon(IconRef.Drawable(R.drawable.nozzle), alternate = "nozzle")
        assertThrows(IllegalArgumentException::class.java) {
            ligatureOf(drawableIcon)
        }
    }
}
