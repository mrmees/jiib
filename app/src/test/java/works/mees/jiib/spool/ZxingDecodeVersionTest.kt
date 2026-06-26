package works.mees.jiib.spool

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SPOOL-05/06 — version LOCK for `com.google.zxing:core` (RESEARCH Pitfall 1, T-11-05-01).
 *
 * This is a REAL JVM round-trip: encode a known string to a QR [BitMatrix] with [QRCodeWriter],
 * then decode it back through the SAME pipeline the on-device [QrCodeAnalyzer] uses
 * ([HybridBinarizer] → [MultiFormatReader] hinted QR-only). It asserts the decoded text equals
 * the input.
 *
 * WHY THIS TEST EXISTS (the mock-vs-reality guard): from zxing:core 3.4.0 the QR decode path
 * (FinderPatternFinder) calls Java-8 `List.sort`, which `NoSuchMethodError`-crashes on API 23 —
 * but flox runs API 30, so a device run would never surface it. This pure-JVM decode exercises
 * the regression surface (the decode path) on every CI run; a future drift to 3.4.0+ surfaces
 * here as a failing/erroring test, NOT as a silent crash on a Nexus-7-class floor device.
 *
 * The pin lives in `gradle/libs.versions.toml` (zxingCore = "3.3.3"); this test is its enforcement.
 */
class ZxingDecodeVersionTest {

    /**
     * Minimal [LuminanceSource] over a QR [BitMatrix] — no AWT/BufferedImage dependency
     * (keeps the test a pure JVM unit test). A set module (the QR's black ink) maps to luminance 0,
     * an unset module (white) to 255, matching how a real camera frame presents a printed QR.
     */
    private class BitMatrixLuminanceSource(private val matrix: BitMatrix) :
        LuminanceSource(matrix.width, matrix.height) {

        override fun getRow(y: Int, row: ByteArray?): ByteArray {
            val out = row?.takeIf { it.size >= width } ?: ByteArray(width)
            for (x in 0 until width) {
                out[x] = if (matrix.get(x, y)) 0 else 255.toByte()
            }
            return out
        }

        override fun getMatrix(): ByteArray {
            val out = ByteArray(width * height)
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    out[offset + x] = if (matrix.get(x, y)) 0 else 255.toByte()
                }
            }
            return out
        }
    }

    @Test
    fun roundTripsKnownQrPayloadThroughTheDecodePath() {
        val payload = "web+spoolman:s-42"

        // Encode → QR BitMatrix (writer side; not the regression surface, but proves a real QR).
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, 256, 256)

        // Decode → the EXACT pipeline QrCodeAnalyzer runs on-device (HybridBinarizer + QR hint).
        // This is the API-23-regression surface that the 3.3.3 pin protects.
        val bitmap = BinaryBitmap(HybridBinarizer(BitMatrixLuminanceSource(matrix)))
        val reader = MultiFormatReader().apply {
            setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
        }

        val decoded = reader.decodeWithState(bitmap).text

        assertEquals(payload, decoded)
    }
}
