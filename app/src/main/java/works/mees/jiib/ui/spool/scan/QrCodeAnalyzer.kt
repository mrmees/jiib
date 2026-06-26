package works.mees.jiib.ui.spool.scan

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * SPOOL-05 — the project's first [ImageAnalysis.Analyzer] and first ZXing use (RESEARCH Pattern 1).
 *
 * Converts a CameraX YUV_420_888 frame's luma plane to a ZXing [PlanarYUVLuminanceSource],
 * binarizes with [HybridBinarizer], and decodes QR-ONLY via a hinted [MultiFormatReader]. On a
 * successful decode it emits the RAW decoded string to [onResult] — it does NOT parse or validate.
 * [parseSpoolId] (11-03, the D-12 Security-V5 boundary) owns the untrusted-input parse; keeping the
 * analyzer parse-free keeps that boundary in one headless, unit-tested place.
 *
 * Load-bearing details (RESEARCH anti-patterns):
 *  - Row length is `planes[0].rowStride`, NOT `image.width` — YUV row padding differs from width;
 *    passing width corrupts the luminance source and the decode silently fails.
 *  - [ImageProxy.close] is in a `finally` — with STRATEGY_KEEP_ONLY_LATEST an unclosed proxy stalls
 *    the pipeline (no further frames arrive).
 *  - [NotFoundException] is a silent no-op: "no QR in this frame" is the normal steady state while
 *    the user is still aiming; any other [Throwable] is also swallowed so an odd frame never kills
 *    the scan loop.
 *
 * The reused [MultiFormatReader] is the only state; the analyzer is otherwise stateless. Default
 * BACK camera + continuous-AF binding and lifecycle release are the surface's job (11-07, D-14),
 * not this analyzer's.
 *
 * @param onResult invoked on the analysis executor thread with the raw decoded QR text.
 */
class QrCodeAnalyzer(private val onResult: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    override fun analyze(image: ImageProxy) {
        try {
            // plane[0] = luminance (Y). YUV_420_888 is the CameraX ImageAnalysis default.
            val plane = image.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining()).also { buffer.get(it) }

            val source = PlanarYUVLuminanceSource(
                data,
                plane.rowStride,   // row length — NOT image.width (padding differs)
                image.height,
                0,
                0,
                image.width,
                image.height,
                false,
            )
            val bitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                onResult(reader.decodeWithState(bitmap).text)
            } catch (_: NotFoundException) {
                // No QR in this frame — the normal case while the user is still aiming.
            }
        } catch (_: Throwable) {
            // Unreadable/odd frame — ignore and keep scanning; never kill the loop.
        } finally {
            image.close()   // MUST close, or STRATEGY_KEEP_ONLY_LATEST stalls.
        }
    }
}
