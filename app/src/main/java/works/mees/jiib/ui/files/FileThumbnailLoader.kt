package works.mees.jiib.ui.files

import android.content.Context
import coil3.ImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.target.Target
import coil3.memory.MemoryCache

data class FileThumbnailRequestSpec(
    val widthPx: Int,
    val heightPx: Int,
)

object FileThumbnailLoader {
    const val ROW_THUMBNAIL_SIZE_PX = 96
    const val MEMORY_CACHE_MAX_BYTES = 2 * 1024 * 1024

    @Volatile
    private var instance: ImageLoader? = null

    fun rowRequestSpec(): FileThumbnailRequestSpec =
        FileThumbnailRequestSpec(
            widthPx = ROW_THUMBNAIL_SIZE_PX,
            heightPx = ROW_THUMBNAIL_SIZE_PX,
        )

    fun get(context: Context): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: build(context.applicationContext).also { instance = it }
        }

    fun build(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizeBytes(MEMORY_CACHE_MAX_BYTES.toLong())
                    .build()
            }
            .build()

    fun rowRequest(
        context: Context,
        url: String,
        target: Target,
    ): ImageRequest {
        val spec = rowRequestSpec()
        return ImageRequest.Builder(context)
            .data(url)
            .size(spec.widthPx, spec.heightPx)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .target(target)
            .build()
    }
}
