package works.mees.jiib.ui.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FileThumbnailLoaderTest {

    @Test
    fun rowThumbnailRequestsUseExplicitBoundedDimensions() {
        val spec = FileThumbnailLoader.rowRequestSpec()

        assertEquals(96, spec.widthPx)
        assertEquals(96, spec.heightPx)
    }

    @Test
    fun filesThumbnailCacheIsBoundedForLargeLibraries() {
        assertTrue(FileThumbnailLoader.MEMORY_CACHE_MAX_BYTES > 0)
        assertTrue(FileThumbnailLoader.MEMORY_CACHE_MAX_BYTES <= 2 * 1024 * 1024)
    }
}
