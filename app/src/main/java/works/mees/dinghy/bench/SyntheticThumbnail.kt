package works.mees.dinghy.bench

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import okio.Buffer
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * Model handed to Coil's `AsyncImage` / `ImageLoader` to request a thumbnail. Carries
 * only a [seed] — the actual PNG BYTES are generated deterministically and decoded by
 * Coil's REAL decode path (D-07: no placeholder drawables, no predecoded bitmaps). The
 * same [seed] ⇒ the same PNG bytes ⇒ both scenes decode identical work (fairness, D-02).
 */
data class ThumbModel(val seed: Int)

/**
 * Coil [Keyer] for [ThumbModel]: cache key is purely the seed, so the Compose scene and
 * the Views scene hit/miss the SAME memory-cache entries under identical pressure.
 */
class ThumbKeyer : Keyer<ThumbModel> {
    override fun key(data: ThumbModel, options: Options): String = "synthbench-thumb-${data.seed}"
}

/**
 * Coil [Fetcher] that synthesizes a large real PNG from [ThumbModel.seed] and returns it
 * as an [ImageSource] for Coil to DECODE + DOWNSAMPLE normally. Producing a genuinely
 * large source (so downsampling actually happens) under memory-cache pressure is the
 * whole point of D-07 — it exercises Coil's API-≤23 large-PNG OOM-fixed path on a 2 GB
 * Adreno-320 device, exactly what Phase 5 thumbnails will ship.
 */
class SyntheticThumbnailFetcher(
    private val model: ThumbModel,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val png = SyntheticThumbnail.pngBytes(model.seed)
        val buffer = Buffer().apply { write(png.toByteString()) }
        return SourceFetchResult(
            source = ImageSource(source = buffer, fileSystem = okio.FileSystem.SYSTEM),
            mimeType = "image/png",
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory : Fetcher.Factory<ThumbModel> {
        override fun create(data: ThumbModel, options: Options, imageLoader: ImageLoader): Fetcher =
            SyntheticThumbnailFetcher(data)
    }
}

/**
 * Deterministic in-memory PNG generator. Draws a [SOURCE_DIM]² gradient + seeded noise
 * blocks and PNG-encodes it. PNG (not JPEG) per D-07 — large-PNG decode is the exact
 * path Coil fixed for API ≤23 and the one the benchmark must stress. Bytes are a pure
 * function of the seed, so the source is replayable and identical across both scenes.
 */
object SyntheticThumbnail {

    /**
     * Source render dimension. Deliberately large (well above the on-screen thumbnail
     * size) so Coil's downsample-to-target actually does work — a tiny source would
     * make the "real decode" claim hollow. ARGB_8888 at 512² ≈ 1 MB/bitmap in flight.
     */
    const val SOURCE_DIM: Int = 512

    /** Cache of encoded PNG bytes per seed (encoding is the slow part, not decode). */
    private val cache = HashMap<Int, ByteArray>()

    @Synchronized
    fun pngBytes(seed: Int): ByteArray = cache.getOrPut(seed) { encode(seed) }

    private fun encode(seed: Int): ByteArray {
        val rng = Random(seed.toLong())
        val bmp = Bitmap.createBitmap(SOURCE_DIM, SOURCE_DIM, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // Seeded base hue gradient.
        val baseR = 40 + rng.nextInt(180)
        val baseG = 40 + rng.nextInt(180)
        val baseB = 40 + rng.nextInt(180)
        val paint = Paint()
        val cells = 16
        val cell = SOURCE_DIM / cells
        for (gy in 0 until cells) {
            for (gx in 0 until cells) {
                val r = (baseR + gx * 6 + rng.nextInt(40)) and 0xFF
                val g = (baseG + gy * 6 + rng.nextInt(40)) and 0xFF
                val b = (baseB + (gx + gy) * 3 + rng.nextInt(40)) and 0xFF
                paint.color = Color.rgb(r, g, b)
                canvas.drawRect(
                    (gx * cell).toFloat(),
                    (gy * cell).toFloat(),
                    (gx * cell + cell).toFloat(),
                    (gy * cell + cell).toFloat(),
                    paint,
                )
            }
        }
        // A few seeded shapes so frames aren't trivially compressible (real decode cost).
        repeat(24) {
            paint.color = Color.argb(
                160 + rng.nextInt(96),
                rng.nextInt(256),
                rng.nextInt(256),
                rng.nextInt(256),
            )
            canvas.drawCircle(
                rng.nextInt(SOURCE_DIM).toFloat(),
                rng.nextInt(SOURCE_DIM).toFloat(),
                (8 + rng.nextInt(48)).toFloat(),
                paint,
            )
        }

        val out = ByteArrayOutputStream(64 * 1024)
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}

/**
 * Shared [ImageLoader] used by BOTH scenes so memory-cache size/pressure and the
 * synthetic-thumbnail fetch path are IDENTICAL (fairness, D-02). A modest memory cache
 * forces real eviction/re-decode during a long scroll on the 2 GB device (D-07).
 */
object BenchImageLoader {
    @Volatile
    private var instance: ImageLoader? = null

    fun get(context: PlatformContext): ImageLoader =
        instance ?: synchronized(this) {
            instance ?: build(context).also { instance = it }
        }

    private fun build(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(ThumbKeyer())
                add(SyntheticThumbnailFetcher.Factory())
            }
            .build()
}
