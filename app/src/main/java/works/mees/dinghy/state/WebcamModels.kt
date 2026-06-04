package works.mees.dinghy.state

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import works.mees.dinghy.net.MoonrakerJson

/**
 * Tolerant model of a single `/server/webcams/list` entry (CAM-01, D-02/D-09; Security V5).
 *
 * EVERY field is untrusted (the cams come from the printer host's config, written from ITS frame of
 * reference): `service`/`name` may be blank or carry odd unicode (the E3 golden has
 * `microsoft®_lifecam_…`); `snapshot_url` may be empty (`""`); `stream_url` may be relative
 * (`/webcam2/?action=stream`) or `localhost`/`127.0.0.1` (D-09); `rotation` may be any int. So every
 * field is nullable-or-defaulted and NEVER fabricated — mirroring [works.mees.dinghy.ui.move.MoveVm]'s
 * "never invent a value the printer didn't report" discipline and the spine-wide tolerant
 * [MoonrakerJson] decode (`ignoreUnknownKeys`, lenient). A malformed payload yields an EMPTY list
 * (fail-safe → greyed tile, D-08), never an exception.
 *
 * The snake_case wire keys are mapped via [SerialName]; unknown keys are ignored by [MoonrakerJson].
 */
@Serializable
data class Webcam(
    val name: String = "",
    val location: String? = null,
    val service: String = "",
    val enabled: Boolean = true,
    val icon: String? = null,
    @SerialName("target_fps") val targetFps: Int? = null,
    @SerialName("target_fps_idle") val targetFpsIdle: Int? = null,
    @SerialName("stream_url") val streamUrl: String? = null,
    @SerialName("snapshot_url") val snapshotUrl: String? = null,
    @SerialName("flip_horizontal") val flipHorizontal: Boolean = false,
    @SerialName("flip_vertical") val flipVertical: Boolean = false,
    val rotation: Int = 0,
    @SerialName("aspect_ratio") val aspectRatio: String? = null,
    @SerialName("extra_data") val extraData: JsonObject = JsonObject(emptyMap()),
    val source: String? = null,
    val uid: String? = null,
) {
    /**
     * The rotation coerced to a legal MJPEG draw rotation ∈ {0, 90, 180, 270}; any other value (a
     * hostile/garbled `rotation`) folds to 0 so the draw-time [android.graphics.Matrix] can never be
     * fed a nonsense angle.
     */
    val safeRotation: Int get() = if (rotation in setOf(0, 90, 180, 270)) rotation else 0

    /** True when a non-blank snapshot URL is present (the rung-2 eligibility signal, D-01). */
    val hasSnapshot: Boolean get() = !snapshotUrl.isNullOrBlank()
}

/**
 * The decode rung selected for a cam (D-01 three-rung fallback ladder). The numeric [tier] mirrors the
 * D-01 rung numbers (1 = live MJPEG, 2 = snapshot poll, 3 = unsupported/unreachable).
 */
enum class Rung(val tier: Int) {
    /** Rung 1: the stream is a decodable `multipart/x-mixed-replace` MJPEG body. */
    Mjpeg(1),

    /** Rung 2: not MJPEG, but a snapshot is reachable (image/jpeg, or snapshot_url present). */
    Snapshot(2),

    /** Rung 3: no usable stream AND no usable snapshot (WebRTC-only, 401/403/404, or unreachable). */
    Unsupported(3),
}

/**
 * Pure D-02 rung selector: the HTTP **Content-Type is the source of truth**; the `/server/webcams/list`
 * `service` field is a HINT ONLY and is NEVER consulted here (a hostile/blank `service` must not be able
 * to route bytes into the wrong decoder — T-10-06).
 *
 * Decision (D-01/D-02):
 *  - `multipart/x-mixed-replace` (any case / with `;boundary=…` params) on a usable response → [Rung.Mjpeg].
 *  - A 2xx `image/jpeg`, OR a non-multipart-but-usable response when a [snapshotUrlPresent] snapshot
 *    exists → [Rung.Snapshot].
 *  - 401 / 403 / 404 (terminal-for-cam — A4/Pitfall 3, never spin-retried), or neither a decodable
 *    stream nor a snapshot → [Rung.Unsupported].
 *
 * @param contentType the stream response `Content-Type` (null/blank when unknown/unreachable).
 * @param httpCode the stream response HTTP status code.
 * @param snapshotUrlPresent whether the cam carries a non-blank `snapshot_url` (its rung-2 fallback).
 */
fun rungFor(contentType: String?, httpCode: Int, snapshotUrlPresent: Boolean): Rung {
    val ct = contentType.orEmpty().trim()

    // Auth/missing failures on the STREAM are terminal-for-this-cam — but a present snapshot is the
    // cam's own independent fallback (the snapshot poll probes itself), so fall to rung 2 if we have one.
    if (httpCode == 401 || httpCode == 403 || httpCode == 404) {
        return if (snapshotUrlPresent) Rung.Snapshot else Rung.Unsupported
    }

    val usable = httpCode in 200..299
    return when {
        usable && ct.startsWith("multipart/x-mixed-replace", ignoreCase = true) -> Rung.Mjpeg
        usable && ct.startsWith("image/jpeg", ignoreCase = true) -> Rung.Snapshot
        // Stream not MJPEG-decodable (or unusable code) but a snapshot fallback exists → rung 2.
        snapshotUrlPresent -> Rung.Snapshot
        else -> Rung.Unsupported
    }
}

/**
 * Walk a `/server/webcams/list` JSON-RPC `result` element into a tolerant list of [Webcam] models
 * (Security V5 / T-10-04). Reads `result.webcams[]` defensively: a missing/garbage shape, a malformed
 * entry, or a non-array `webcams` yields an EMPTY list (fail-safe), never throws.
 *
 * Each entry is decoded with the shared [MoonrakerJson]; a single un-decodable entry is dropped, the
 * rest survive (one bad cam never blanks the whole picker).
 */
fun parseWebcamsList(result: JsonElement?): List<Webcam> {
    if (result == null) return emptyList()
    return runCatching {
        val webcams = (result as? JsonObject)?.get("webcams")
            ?: (result.jsonObject)["webcams"]
        val array = webcams?.jsonArray ?: return emptyList()
        array.mapNotNull { entry ->
            runCatching { MoonrakerJson.decodeFromJsonElement(Webcam.serializer(), entry) }.getOrNull()
        }
    }.getOrDefault(emptyList())
}

/**
 * The resolved/selected-cam view-model the page holder (plan 10-06) consumes — mirrors
 * [works.mees.dinghy.ui.move.MoveVm]'s "resolved VM for the screen" role. Carries the chosen [Webcam],
 * its selected decode [rung], and the D-09-resolved absolute URLs (already host-rewritten + token-preserving;
 * the holder/view must redact via `redactWebcamUrl` before any log/crash surface — Security V7).
 *
 * Built later in the holder; defined here so the model + its consumer shape live in one place.
 */
data class ResolvedWebcam(
    val webcam: Webcam,
    val rung: Rung,
    val resolvedStreamUrl: String? = null,
    val resolvedSnapshotUrl: String? = null,
)
