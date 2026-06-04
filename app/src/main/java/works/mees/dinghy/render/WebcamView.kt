package works.mees.dinghy.render

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import works.mees.dinghy.R
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.theme.views.ThemeableView

/**
 * The live webcam raster surface (CAM-01) — a classic-Views custom-`Canvas` `View`, the ADR-0001
 * hybrid's HIGH-churn render primitive for a streaming MJPEG/snapshot frame. It is a deliberate
 * SIBLING of [GraphView]/[BedMeshHeatmapView] (NOT a fork): GraphView is a 1-D line, the heatmap is a
 * 2-D fill, this is a raster blit. It reuses their allocation-free discipline VERBATIM (the Adreno-320
 * fill-rate floor — a decoded full-res frame blitted per arrival is the worst case):
 *  - every [Paint]/[Path]/[Matrix]/[RectF] is pre-allocated in init — `onDraw` NEVER `new`s (the
 *    GC-churn trap, Pitfall 4); the per-frame transform is COMPUTED into the ONE reused [frameMatrix]
 *    with no allocation;
 *  - it implements [ThemeableView] — the [WebcamViewHost] PUSHES the active tokens via [applyTokens]
 *    (recolor + `invalidate()`), so a dark/light/custom flip recolors ALL chrome (badge / reconnect
 *    overlay / cycle overlay / dead-end card) with NO view recreation;
 *  - the repaint trigger is the imperative [setFrame]/[setChrome] called on a NEW frame or state change
 *    (the decoder/poller thread calls `setFrame(...) → postInvalidate()`); there is NO animation loop
 *    (no `ValueAnimator` / `postInvalidateOnAnimation`), honoring the CLAUDE.md motion rule (the floor
 *    cannot spare a continuous loop; the static reconnect/cycle glow is paint, not animation);
 *  - NO raw hex literal lives here (THEME-01) — every chrome color comes from a role token.
 *
 * ## Pixel-square never-stretch draw (camera_feed note — LOCKED design contract)
 * The current [frame] is drawn with a Matrix scale-to-FIT — `min(viewW/frameW, viewH/frameH)`, centered
 * — so the aspect ratio is ALWAYS preserved in BOTH portrait and landscape (it can grow/shrink to fit
 * but is NEVER stretched). The cam's `flip_horizontal`/`flip_vertical`/`rotation` (already coerced to a
 * legal {0,90,180,270} angle by [works.mees.dinghy.state.Webcam.safeRotation]) is folded into the same
 * Matrix. The blit is clipped to a ROUNDED framing cutout ([clipPath]/[RoundRect]) — edge loss is
 * explicitly acceptable per the note ("we're ok losing that content to ensure visual continuity").
 *
 * ## Token chrome (D-03 / D-04 / D-11 + the camera_feed cycle overlay)
 *  - [Mode.SnapshotFallback] (D-03) → a small PERSISTENT corner badge ("Snapshot ~2fps") for the whole
 *    fallback duration — NOT a toast. Drawn over the frame in the top-right cutout corner.
 *  - [Mode.Reconnecting] (D-11) → keep the LAST good frame visible but DIMMED (a scrim over the blit) +
 *    a subtle centered "Reconnecting…" overlay. Does NOT black out / clear to an error on a transient blip.
 *  - [Mode.DeadEnd] (D-04, rung 3) → a card INSIDE the cutout naming the detected [serviceName]
 *    ("This camera uses WebRTC — not supported yet"). NO open-in-browser button (the View draws no
 *    affordance; the gutter Back lives on the screen, plan 10-06). The tokened URL is NEVER drawn (T-10-12).
 *  - [multiCam] in full-focus → the in-feed CYCLE overlay (a burst-mode glyph + the [camName]) in the
 *    bottom-left cutout corner; the SCREEN owns the tap-to-cycle gesture (plan 10-06), the View only paints it.
 */
class WebcamView(context: Context) : View(context), ThemeableView {

    /** The chrome state the View paints over (or instead of) the frame. */
    enum class Mode {
        /** Rung 1/2 live: just the frame (+ cycle overlay if [multiCam]). */
        Live,

        /** Rung 2 snapshot-poll fallback: frame + persistent "Snapshot ~2fps" corner badge (D-03). */
        SnapshotFallback,

        /** Transient stall: last frame dimmed + "Reconnecting…" overlay, auto-retrying (D-11). */
        Reconnecting,

        /** Rung 3: no usable stream/snapshot — the dead-end card naming the service (D-04). */
        DeadEnd,
    }

    // ── Frame transform (allocation-free; computed into these reused objects in onDraw) ───────────

    /** The single reusable frame transform (scale-to-fit + flip/rotation). NEVER reallocated. */
    private val frameMatrix = Matrix()

    /** The single reusable rounded-cutout clip path. Rebuilt (rewind) on a size change, never per draw. */
    private val cutoutPath = Path()

    /** Reusable rect for the cutout bounds (drives the clip path + chrome placement). */
    private val cutoutRect = RectF()

    /** Cached cutout corner radius (px) — re-derived in applyTokens from the `--r-card` token. */
    private var cutoutRadiusPx: Float = 0f

    /** Display density, cached once (avoids re-reading metrics per draw). */
    private val density = resources.displayMetrics.density

    // ── Chrome paints (all pre-allocated; colors pushed from tokens, never raw — BedMesh precedent) ──

    /** The cutout-background fill (drawn behind/around the frame; matches the page surface). */
    private val backdropPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** Dimming scrim over the last frame while [Mode.Reconnecting] (D-11). Alpha-bearing surface tone. */
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** The snapshot badge / cycle-overlay pill background (a rounded chip behind the chrome text). */
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** The dead-end card background fill (a rounded surface inside the cutout). */
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    /** The dead-end card hairline outline (the affordance-edge token). */
    private val cardOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    /** Strong chrome text (badge / cycle name / card title). GeistMedium, color from `--text`. */
    private val chromeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.geist_medium) }
            .getOrNull() ?: Typeface.DEFAULT
    }

    /** Muted chrome text (the dead-end card body line). GeistRegular, color from `--text-2`. */
    private val chromeBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        typeface = runCatching { ResourcesCompat.getFont(context, R.font.geist_regular) }
            .getOrNull() ?: Typeface.DEFAULT
    }

    /** The burst-mode glyph for the cycle overlay (a small accent-tinted stack of squares). */
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    // ── State the View renders ───────────────────────────────────────────────────────────────────

    /** The current decoded frame, or `null` before the first frame / on a hard dead-end. */
    private var frame: Bitmap? = null

    /** Cam draw transform inputs (from the selected [works.mees.dinghy.state.Webcam]). */
    private var flipHorizontal: Boolean = false
    private var flipVertical: Boolean = false
    private var rotationDeg: Int = 0

    /** Active chrome mode + its text inputs. */
    private var mode: Mode = Mode.Live
    private var camName: String = ""
    private var serviceName: String = ""
    private var multiCam: Boolean = false

    /**
     * Push the active tokens (THEME-01): derive every chrome color from a role token + repaint. No raw
     * hex — backdrop=`--bg-2`, dim scrim=`--bg` (alpha), pill/card=`--surface-2`, card outline=`--outline`,
     * strong text=`--text`, body text=`--text-2`, burst glyph=`--accent-2`. The card title uses the strong
     * text token too (the screen owns the red Back intent in its gutter; nothing red is drawn IN the feed).
     * Type sizes are `--fs`-scaled via [fsSp] so the chrome tracks the S/M/L setting. The cutout radius is
     * re-derived from the `--r-card` token (the round-edges contract, camera_feed note).
     */
    override fun applyTokens(t: ThemeTokens) {
        backdropPaint.color = t.bg2.toArgb()

        dimPaint.color = t.bg.toArgb()
        dimPaint.alpha = DIM_ALPHA

        pillPaint.color = t.surface2.toArgb()
        pillPaint.alpha = PILL_ALPHA

        cardPaint.color = t.surface2.toArgb()
        cardOutlinePaint.color = t.outline.toArgb()

        chromeTextPaint.color = t.text.toArgb()
        chromeTextPaint.textSize = fsSp(CHROME_TEXT_SP, t.fs) * density

        chromeBodyPaint.color = t.text2.toArgb()
        chromeBodyPaint.textSize = fsSp(CARD_BODY_SP, t.fs) * density

        glyphPaint.color = t.accent2.toArgb()
        glyphPaint.strokeWidth = 1.5f * density

        // Round-edges contract: the cutout radius is the `--r-card` token (Dp → px).
        cutoutRadiusPx = t.rCard.value * density

        // The radius changed → the cutout path must be rebuilt for the current size on next draw.
        rebuildCutout()
        invalidate()
    }

    /**
     * Hand the View a new decoded frame (the decoder/poller thread calls this then [postInvalidate]).
     * A field swap + `invalidate()` — the heavy blit happens lazily in `onDraw`. Pass `null` to clear
     * (e.g. a hard dead-end with no last frame). The bitmap is owned/recycled by the decoder's drop-behind
     * seam (plan 10-06); the View only references it for the blit.
     */
    fun setFrame(bitmap: Bitmap?) {
        this.frame = bitmap
        invalidate()
    }

    /**
     * Set the selected cam's draw transform (the `flip_*`/`rotation` from
     * [works.mees.dinghy.state.Webcam]). `rotation` MUST already be coerced to a legal {0,90,180,270}
     * angle (`Webcam.safeRotation`) — a non-legal value is folded to 0 here as a second guard so the
     * Matrix can never be fed nonsense. Repaints.
     */
    fun setTransform(flipHorizontal: Boolean, flipVertical: Boolean, rotation: Int) {
        this.flipHorizontal = flipHorizontal
        this.flipVertical = flipVertical
        this.rotationDeg = if (rotation == 90 || rotation == 180 || rotation == 270) rotation else 0
        invalidate()
    }

    /**
     * Set the chrome state (D-03/D-04/D-11 + the cycle overlay). [serviceName] feeds the dead-end card
     * text; [camName] feeds the cycle overlay; [multiCam] gates the cycle overlay (full-focus only).
     * Repaints.
     */
    fun setChrome(mode: Mode, camName: String, serviceName: String, multiCam: Boolean) {
        this.mode = mode
        this.camName = camName
        this.serviceName = serviceName
        this.multiCam = multiCam
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildCutout()
    }

    /** Rebuild the reused rounded-cutout path for the current size (NOT in onDraw — Pitfall 4). */
    private fun rebuildCutout() {
        val w = width.toFloat()
        val h = height.toFloat()
        cutoutPath.rewind()
        if (w <= 0f || h <= 0f) return
        cutoutRect.set(0f, 0f, w, h)
        cutoutPath.addRoundRect(cutoutRect, cutoutRadiusPx, cutoutRadiusPx, Path.Direction.CW)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        // Clip to the rounded framing cutout (edge loss OK — camera_feed note). Save/restore the layer.
        val save = canvas.save()
        canvas.clipPath(cutoutPath)

        // Backdrop behind/around the frame (a portrait feed in a landscape cutout shows letterbox bars).
        canvas.drawRect(0f, 0f, w, h, backdropPaint)

        val bmp = frame
        if (bmp != null && !bmp.isRecycled) {
            // Pixel-square scale-to-FIT (never stretch) + the cam's flip/rotation, centered. Computed
            // into the ONE reused Matrix — no allocation (Pitfall 4).
            computeFrameMatrix(bmp.width, bmp.height, w, h)
            canvas.drawBitmap(bmp, frameMatrix, null)

            // Reconnecting (D-11): keep the last frame but DIM it (do not black out).
            if (mode == Mode.Reconnecting) {
                canvas.drawRect(0f, 0f, w, h, dimPaint)
                drawCenteredOverlay(canvas, w, h, RECONNECTING_TEXT)
            }
        }

        when (mode) {
            // Persistent corner badge (D-03) — NOT a toast; top-right of the cutout.
            Mode.SnapshotFallback -> drawCornerBadge(canvas, w, SNAPSHOT_BADGE_TEXT)
            // Dead-end card (D-04) — inside the cutout, names the service, NO browser button.
            Mode.DeadEnd -> drawDeadEndCard(canvas, w, h)
            else -> { /* Live / Reconnecting handled above */ }
        }

        // The in-feed cycle overlay (camera_feed note): full-focus + multiple cams → burst glyph + name.
        // Suppressed on the dead-end card (no frame to cycle over there).
        if (multiCam && mode != Mode.DeadEnd) {
            drawCycleOverlay(canvas, h)
        }

        canvas.restoreToCount(save)
    }

    /**
     * Compute the pixel-square scale-to-fit + flip/rotation transform into [frameMatrix] (reused, no
     * allocation). The frame is scaled by `min(viewW/srcW, viewH/srcH)` against its POST-rotation extent
     * (so a 90/270 rotation swaps the fitted dimensions), flipped, then centered in the view.
     */
    private fun computeFrameMatrix(srcW: Int, srcH: Int, viewW: Float, viewH: Float) {
        frameMatrix.reset()
        if (srcW <= 0 || srcH <= 0) return

        // 1) Flip about the bitmap center (sign of scale; -1 flips that axis).
        val sx = if (flipHorizontal) -1f else 1f
        val sy = if (flipVertical) -1f else 1f
        frameMatrix.postScale(sx, sy, srcW / 2f, srcH / 2f)

        // 2) Rotate about the bitmap center (legal angle only).
        if (rotationDeg != 0) {
            frameMatrix.postRotate(rotationDeg.toFloat(), srcW / 2f, srcH / 2f)
        }

        // 3) Post-rotation extent (90/270 swaps W/H) drives the pixel-square fit scale.
        val rotated = rotationDeg == 90 || rotationDeg == 270
        val effW = if (rotated) srcH.toFloat() else srcW.toFloat()
        val effH = if (rotated) srcW.toFloat() else srcH.toFloat()
        val fit = minOf(viewW / effW, viewH / effH) // FIT, never fill — never-stretch contract
        frameMatrix.postScale(fit, fit, srcW / 2f, srcH / 2f)

        // 4) Center the (already-centered-about-its-own-middle) frame in the view.
        frameMatrix.postTranslate((viewW - srcW) / 2f, (viewH - srcH) / 2f)
    }

    /** A small persistent corner pill at the top-right (the snapshot badge, D-03). */
    private fun drawCornerBadge(canvas: Canvas, viewW: Float, text: String) {
        val pad = CHROME_PAD * density
        val textW = chromeTextPaint.measureText(text)
        val th = chromeTextPaint.textSize
        val pillH = th + 2f * (PILL_VPAD * density)
        val pillW = textW + 2f * (PILL_HPAD * density)
        val right = viewW - pad
        val left = right - pillW
        val top = pad
        cutoutRect.set(left, top, right, top + pillH)
        val r = pillH / 2f
        canvas.drawRoundRect(cutoutRect, r, r, pillPaint)
        val baseline = top + pillH / 2f + th / 2f - chromeTextPaint.descent() / 2f
        canvas.drawText(text, left + PILL_HPAD * density, baseline, chromeTextPaint)
    }

    /** A centered text overlay (the "Reconnecting…" line, D-11) on its own pill for legibility. */
    private fun drawCenteredOverlay(canvas: Canvas, viewW: Float, viewH: Float, text: String) {
        val textW = chromeTextPaint.measureText(text)
        val th = chromeTextPaint.textSize
        val pillH = th + 2f * (PILL_VPAD * density)
        val pillW = textW + 2f * (PILL_HPAD * density)
        val left = (viewW - pillW) / 2f
        val top = (viewH - pillH) / 2f
        cutoutRect.set(left, top, left + pillW, top + pillH)
        val r = pillH / 2f
        canvas.drawRoundRect(cutoutRect, r, r, pillPaint)
        val baseline = top + pillH / 2f + th / 2f - chromeTextPaint.descent() / 2f
        canvas.drawText(text, left + PILL_HPAD * density, baseline, chromeTextPaint)
    }

    /**
     * The rung-3 dead-end card (D-04): a rounded surface centered in the cutout with a strong title line
     * and a muted body line naming the detected service. NO browser button (the View draws no affordance);
     * the tokened URL is NEVER drawn (T-10-12 — only the non-secret service string).
     */
    private fun drawDeadEndCard(canvas: Canvas, viewW: Float, viewH: Float) {
        val title = DEAD_END_TITLE
        val body = if (serviceName.isBlank()) DEAD_END_BODY_GENERIC
        else String.format(DEAD_END_BODY_FMT, serviceName)

        val hPad = CARD_HPAD * density
        val vPad = CARD_VPAD * density
        val titleH = chromeTextPaint.textSize
        val bodyH = chromeBodyPaint.textSize
        val lineGap = CARD_LINE_GAP * density

        val contentW = maxOf(chromeTextPaint.measureText(title), chromeBodyPaint.measureText(body))
        val cardW = minOf(contentW + 2f * hPad, viewW - 2f * (CHROME_PAD * density))
        val cardH = titleH + lineGap + bodyH + 2f * vPad
        val left = (viewW - cardW) / 2f
        val top = (viewH - cardH) / 2f

        cutoutRect.set(left, top, left + cardW, top + cardH)
        canvas.drawRoundRect(cutoutRect, cutoutRadiusPx, cutoutRadiusPx, cardPaint)
        canvas.drawRoundRect(cutoutRect, cutoutRadiusPx, cutoutRadiusPx, cardOutlinePaint)

        val titleBaseline = top + vPad + titleH
        canvas.drawText(title, left + hPad, titleBaseline, chromeTextPaint)
        val bodyBaseline = titleBaseline + lineGap + bodyH
        canvas.drawText(body, left + hPad, bodyBaseline, chromeBodyPaint)
    }

    /**
     * The in-feed cycle overlay (camera_feed note): a burst-mode glyph + the cam name in a pill at the
     * bottom-left cutout corner. The SCREEN owns the tap-to-cycle gesture (plan 10-06); the View only paints.
     */
    private fun drawCycleOverlay(canvas: Canvas, viewH: Float) {
        val pad = CHROME_PAD * density
        val name = camName
        val th = chromeTextPaint.textSize
        val glyphSize = th
        val textW = chromeTextPaint.measureText(name)
        val pillH = th + 2f * (PILL_VPAD * density)
        val pillW = (PILL_HPAD * density) + glyphSize + (GLYPH_TEXT_GAP * density) + textW + (PILL_HPAD * density)
        val left = pad
        val bottom = viewH - pad
        val top = bottom - pillH
        cutoutRect.set(left, top, left + pillW, bottom)
        val r = pillH / 2f
        canvas.drawRoundRect(cutoutRect, r, r, pillPaint)

        // Burst-mode glyph: a small stack of offset squares (a "multiple frames" affordance), accent-tinted.
        val gLeft = left + PILL_HPAD * density
        val gTop = top + (pillH - glyphSize) / 2f
        val step = glyphSize * GLYPH_STACK_FRAC
        val sq = glyphSize - step
        var i = GLYPH_STACK_COUNT - 1
        while (i >= 0) {
            val off = step * i
            cutoutRect.set(gLeft + off, gTop + off, gLeft + off + sq, gTop + off + sq)
            canvas.drawRect(cutoutRect, glyphPaint)
            i--
        }

        val textLeft = gLeft + glyphSize + GLYPH_TEXT_GAP * density
        val baseline = top + pillH / 2f + th / 2f - chromeTextPaint.descent() / 2f
        canvas.drawText(name, textLeft, baseline, chromeTextPaint)
    }

    companion object {
        /** Strong chrome text base size (sp) — `--fs`-scaled (badge / reconnect / cycle name / card title). */
        private const val CHROME_TEXT_SP = 15f

        /** Dead-end card body base size (sp) — slightly smaller than the title; `--fs`-scaled. */
        private const val CARD_BODY_SP = 13f

        /** Dimming-scrim alpha over the last frame while reconnecting (0-255) — visible but not black. */
        private const val DIM_ALPHA = 140

        /** Pill background alpha (0-255) — a translucent chip so the frame reads through behind the chrome. */
        private const val PILL_ALPHA = 200

        /** Chrome inset from the cutout edge (dp-equivalent; density-scaled). */
        private const val CHROME_PAD = 10f

        /** Pill horizontal / vertical inner padding (dp-equivalent). */
        private const val PILL_HPAD = 10f
        private const val PILL_VPAD = 5f

        /** Dead-end card inner padding + inter-line gap (dp-equivalent). */
        private const val CARD_HPAD = 18f
        private const val CARD_VPAD = 16f
        private const val CARD_LINE_GAP = 8f

        /** Gap between the burst glyph and the cam name in the cycle overlay (dp-equivalent). */
        private const val GLYPH_TEXT_GAP = 7f

        /** Burst-glyph stack: count of offset squares + the per-square offset fraction of the glyph size. */
        private const val GLYPH_STACK_COUNT = 3
        private const val GLYPH_STACK_FRAC = 0.22f

        /** The persistent snapshot-fallback badge text (D-03). */
        private const val SNAPSHOT_BADGE_TEXT = "Snapshot ~2fps"

        /** The transient-stall overlay text (D-11). */
        private const val RECONNECTING_TEXT = "Reconnecting…"

        /** The dead-end card title (D-04). */
        private const val DEAD_END_TITLE = "Camera not supported"

        /** The dead-end card body when the service is known — names the detected service (D-04, T-10-12). */
        private const val DEAD_END_BODY_FMT = "This camera uses %s — not supported yet"

        /** The dead-end card body when no service string is reported (still no browser button, D-04). */
        private const val DEAD_END_BODY_GENERIC = "This camera type is not supported yet"
    }
}
