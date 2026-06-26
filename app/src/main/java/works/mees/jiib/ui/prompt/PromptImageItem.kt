package works.mees.jiib.ui.prompt

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import works.mees.jiib.theme.DinghyType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * A `prompt_image` content item — a Coil 3 [AsyncImage] bounded HARD for the Adreno-320 fill-rate floor
 * (D-07), with an alt-text fallback (12-04 Task 2). The path is already vetted by 12-01 `isValidImagePath`
 * upstream, so only `config/...` paths reach this loader (T-12-14: a rejected path became alt-text in the
 * reducer and never gets here — no URL is built from un-vetted segments).
 *
 * ## Bounding (T-12-15 resource-exhaustion mitigation)
 *  - A SQUARE box = `contentWidth / 3 × clampedScale` (UI-SPEC convention; the full-screen renderer tracks
 *    the full content width). [scale] is clamped: non-finite / ≤0 → 1.0.
 *  - [ContentScale.Fit] — the whole image is letterboxed/centered, never cropped (LAYOUT.md content-image
 *    rule / SPEC `object-fit: contain`).
 *  - The Coil request size is tied to the cell in PX (the `inSampleSize`/downscale discipline Files & the
 *    Phase-10 webcam use) — NEVER an intrinsic decode, which OOMs the 2GB device.
 *
 * ## Failure
 *  - On `error` (load failure / unsupported format — incl. SVG until a verified `coil-svg` artifact is
 *    added) → render [alt] as plain centered 15sp Geist `--text-2`. If [alt] is blank, the item collapses
 *    silently. A failed image NEVER blocks the rest of the prompt.
 *
 * @param path     the vetted `config/...` Moonraker file path.
 * @param alt      the author's alt text (blank → omit on failure).
 * @param scale    the parsed scale multiplier (`null`/≤0/non-finite → 1.0).
 * @param httpBase the Moonraker base URL (same host the WS/REST use; Files-style plumbing).
 */
@Composable
fun PromptImageItem(
    path: String,
    alt: String,
    scale: Double?,
    httpBase: String,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val density = LocalDensity.current

    val clampedScale = scale
        ?.takeIf { it.isFinite() && it > 0.0 }
        ?.toFloat()
        ?: 1.0f

    // A square box tied to the content width: base = width/3, then × clampedScale. BoxWithConstraints
    // gives us the parent content width so the bound is ratio-derived, not a hardcoded region px.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val boxWidthDp = (maxWidth / 3) * clampedScale
        // Coil request size in PX, tied to the cell — the downscale discipline (never intrinsic).
        val targetPx = with(density) { boxWidthDp.toPx() }.toInt().coerceAtLeast(1)
        val url = moonrakerImageUrl(httpBase, path)

        var failed by remember(url) { mutableStateOf(false) }

        Box(
            Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (failed || url == null) {
                // Alt-text fallback (or silent collapse when alt is blank).
                if (alt.isNotBlank()) {
                    Text(
                        text = alt,
                        color = t.text2,
                        style = DinghyType.caption.toTextStyle(t),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        // Bound the decode to the cell (PX) — inSampleSize/downscale discipline (D-07).
                        .size(targetPx, targetPx)
                        .build(),
                    contentDescription = alt.ifBlank { null },
                    contentScale = ContentScale.Fit,
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Error) failed = true
                    },
                    modifier = Modifier
                        // Square bounding box = (contentWidth / 3) × clampedScale, centered in the cell.
                        .width(boxWidthDp)
                        .aspectRatio(1f),
                )
            }
        }
    }
}

/**
 * Resolve a vetted `config/...` path to its Moonraker file URL: `<httpBase>/server/files/<path>` (the
 * Moonraker file-serving route Files/Phase-10 already use; `config/...` files are served from the same
 * `/server/files/` mount as `gcodes/...`). Returns null if [httpBase] is blank so the caller falls back
 * to alt text rather than firing a malformed request.
 */
internal fun moonrakerImageUrl(httpBase: String, path: String): String? {
    if (httpBase.isBlank()) return null
    val base = httpBase.trimEnd('/')
    return "$base/server/files/$path"
}
