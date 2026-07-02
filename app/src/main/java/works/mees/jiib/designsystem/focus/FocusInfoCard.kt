package works.mees.jiib.designsystem.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIconView
import works.mees.jiib.designsystem.layout.FocusZoneInset
import works.mees.jiib.designsystem.layout.FocusZones
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.ThemeTokens
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp

// ─────────────────────────────────────────────────────────────────────────────
// Public API
// ─────────────────────────────────────────────────────────────────────────────

/** One stat item in a [FocusInfoCard]. */
data class InfoStat(
    val icon: JiibIcon?,
    val label: String,
    val value: String,
)

/**
 * Visual rendering style for a [FocusInfoCard].
 *
 * [IconRows] — icon-led labelled rows (ported from Files focus stat block).
 * [CenteredBlock] — headline + centered data lines (ported from ActivePrint focus text block).
 */
enum class InfoCardStyle { IconRows, CenteredBlock }

/**
 * Shared fill-to-fit sizing law for [FocusInfoCard].
 *
 * Returns a uniform scale such that the widest measured row fits within [availW] (at 96%) AND
 * the stacked text height (minus fixed [gapPx] * [gapCount] gap budget) fits within [availH],
 * clamped to [[minFrac], [maxFrac]].
 *
 * Degenerate (zero) measurements — widest or totalTextH — yield 1f for that dimension so the
 * block renders at the reference size when measurement data is unavailable.
 */
fun infoCardScale(
    availW: Float,
    availH: Float,
    widestPx: Float,
    totalTextHPx: Float,
    gapPx: Float,
    gapCount: Int,
    minFrac: Float,
    maxFrac: Float,
): Float {
    val wFrac = if (widestPx > 0f && availW > 0f) availW * 0.96f / widestPx else 1f
    val hFrac = if (totalTextHPx > 0f && availH > 0f) {
        (availH - gapPx * gapCount).coerceAtLeast(0f) / totalTextHPx
    } else {
        1f
    }
    return minOf(wFrac, hFrac).coerceIn(minFrac, maxFrac)
}

// ─────────────────────────────────────────────────────────────────────────────
// Composable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Archetype #4 — fill-to-fit stat block. One shared [infoCardScale] measurement pass feeds
 * BOTH styles so the block uniformly grows into spare room and shrinks when cramped without
 * live-value jitter (scale keys on line LENGTHS, not contents).
 *
 * @param stats       The stat items to display.
 * @param style       [InfoCardStyle.IconRows] for icon-led rows (Files);
 *                    [InfoCardStyle.CenteredBlock] for a centred text tower (ActivePrint).
 * @param headline    CenteredBlock only — optional title line, marqueed when it overflows.
 * @param background  Optional background layer drawn behind the stat block (e.g. thumbnail).
 * @param scrim       When true, draws a ~50 % surface scrim over [background] for legibility.
 * @param rowsMinFrac IconRows only — minimum allowed scale; the stat block never shrinks below
 *                    this fraction of the reference size. CenteredBlock ignores these; its
 *                    bounds are fixed from the ported ActivePrint source's sp constraints.
 * @param rowsMaxFrac IconRows only — maximum allowed scale; the stat block never grows beyond
 *                    this fraction of the reference size. CenteredBlock ignores these; its
 *                    bounds are fixed from the ported ActivePrint source's sp constraints.
 */
@Composable
fun FocusInfoCard(
    stats: List<InfoStat>,
    modifier: Modifier = Modifier,
    style: InfoCardStyle = InfoCardStyle.IconRows,
    headline: String? = null,
    background: (@Composable BoxScope.() -> Unit)? = null,
    scrim: Boolean = false,
    rowsMinFrac: Float = 0.7f,
    rowsMaxFrac: Float = 2.4f,
) {
    val t = LocalTokens.current
    FocusZones(
        inset = FocusZoneInset.Default,
        modifier = modifier,
        body = {
            Box(Modifier.fillMaxSize()) {
                background?.invoke(this)
                if (scrim) {
                    Box(Modifier.fillMaxSize().background(t.surface.copy(alpha = 0.5f)))
                }
                when (style) {
                    InfoCardStyle.IconRows -> IconRowsContent(stats, t, rowsMinFrac, rowsMaxFrac)
                    InfoCardStyle.CenteredBlock -> CenteredBlockContent(stats, headline, t)
                }
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// IconRows — ported from FilesFocusStats + FileStatRow (FilesScreen.kt)
// ─────────────────────────────────────────────────────────────────────────────

/** Fixed gap between rows — mirrors FilesScreen.StatRowGap. */
private val StatRowGap = 4.dp

/**
 * Fill-to-fit stat rows: icon (optional) · dim label · Mono value, uniformly scaled so the
 * widest row fits the available width and all rows (+ gaps) fit the available height.
 * Scale keys on line LENGTHS to prevent jitter on live value updates.
 */
@Composable
private fun IconRowsContent(
    stats: List<InfoStat>,
    t: ThemeTokens,
    minFrac: Float,
    maxFrac: Float,
) {
    if (stats.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val labelStyle = JiibType.caption.toTextStyle(t, fsSp(15f, t.fs))
        val valueStyle = JiibType.dataInline.toTextStyle(t, fsSp(20f, t.fs))
        val availW = with(density) { maxWidth.toPx() }
        val availH = with(density) { maxHeight.toPx() }
        val iconRefPx = with(density) { fsSp(18f, t.fs).dp.toPx() }
        val innerGapPx = with(density) { 8.dp.toPx() }
        val rowGapPx = with(density) { StatRowGap.toPx() }

        val key = stats.joinToString("¦") { stat ->
            "${stat.icon != null},${stat.label.length},${stat.value.length}"
        } + "|$availW|$availH|${t.fs}|${density.density}|${density.fontScale}"

        val scale = remember(key) {
            var widest = 0f
            var textH = 0f
            stats.forEach { stat ->
                val hasIcon = stat.icon != null
                val iconW = if (hasIcon) iconRefPx + innerGapPx else 0f
                val l = measurer.measure(stat.label, labelStyle, maxLines = 1, softWrap = false)
                val v = measurer.measure(stat.value, valueStyle, maxLines = 1, softWrap = false)
                val labelW = if (stat.label.isNotBlank()) l.size.width.toFloat() + innerGapPx else 0f
                widest = maxOf(widest, iconW + labelW + v.size.width.toFloat())
                textH += maxOf(
                    if (hasIcon) iconRefPx else 0f,
                    if (stat.label.isNotBlank()) l.size.height.toFloat() else 0f,
                    v.size.height.toFloat(),
                )
            }
            infoCardScale(availW, availH, widest, textH, rowGapPx, stats.size - 1, minFrac, maxFrac)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(StatRowGap),
        ) {
            stats.forEach { stat ->
                InfoStatRow(stat, t, scale)
            }
        }
    }
}

/** One icon-led stat line: optional icon + dim label + GeistMono value, uniformly scaled. */
@Composable
private fun InfoStatRow(
    stat: InfoStat,
    t: ThemeTokens,
    scale: Float,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (stat.icon != null) {
            JiibIconView(
                stat.icon,
                tint = t.text2,
                sizeDp = (fsSp(18f, t.fs) * scale).dp,
                contentDescription = null,
            )
        }
        if (stat.label.isNotBlank()) {
            Text(
                stat.label,
                color = t.text2,
                style = JiibType.caption.toTextStyle(t, fsSp(15f, t.fs) * scale),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stat.value,
            color = t.text,
            style = JiibType.dataInline.toTextStyle(t, fsSp(20f, t.fs) * scale),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// CenteredBlock — ported from ActivePrintFocus text block (PrintStatusFocus.kt)
// ─────────────────────────────────────────────────────────────────────────────

/** Headline size = data size × this (mirrors FILENAME_FACTOR in PrintStatusFocus). */
private const val FILENAME_FACTOR = 1.2f

/** Vertical gap between centred block lines (mirrors BLOCK_LINE_GAP in PrintStatusFocus). */
private val BLOCK_LINE_GAP = 4.dp

/**
 * Centred text tower: optional [headline] (marqueed) + one Text per stat, uniformly scaled.
 *
 * Sizing is source-faithful to ActivePrintFocus: the RAW (unclamped) fill fraction comes from
 * [infoCardScale], then the data and headline sizes are coerced INDEPENDENTLY in sp terms —
 * dataSp ∈ [minData (15sp), maxCap (46sp)]; nameSp ∈ [minData (15sp), maxCap × FILENAME_FACTOR].
 * The headline floor is 15sp FLAT (not 15sp × FILENAME_FACTOR).
 *
 * When [stat.label] is blank, only [InfoStat.value] is rendered; otherwise "${label}: ${value}".
 * The [InfoStat.icon] field is unused in this style.
 */
@Composable
private fun CenteredBlockContent(
    stats: List<InfoStat>,
    headline: String?,
    t: ThemeTokens,
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val maxDataSp = fsSp(26f, t.fs)
    val minDataSp = fsSp(15f, t.fs)
    val maxCapSp = fsSp(46f, t.fs)
    val nameStyleBase = JiibType.screenTitle.toTextStyle(t, maxDataSp * FILENAME_FACTOR)
    val dataStyleBase = JiibType.screenTitle.toTextStyle(t, maxDataSp)
    val gapPx = with(density) { BLOCK_LINE_GAP.toPx() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availW = constraints.maxWidth.toFloat()
        val availH = constraints.maxHeight.toFloat()

        val dataLines = stats.map { stat ->
            if (stat.label.isBlank()) stat.value else "${stat.label}: ${stat.value}"
        }

        // N+1 total lines (headline + N data) → N gaps; no headline → N-1 gaps.
        val gapCount = if (headline != null) dataLines.size else maxOf(0, dataLines.size - 1)

        val key = (headline ?: "") + "|" +
                dataLines.joinToString("¦") { it.length.toString() } +
                "|$availW|$availH|${t.fs}|${density.density}|${density.fontScale}"

        val sizeFrac = remember(key) {
            val nameLayout = headline?.let {
                measurer.measure(it, nameStyleBase, maxLines = 1, softWrap = false)
            }
            val dataLayouts = dataLines.map {
                measurer.measure(it, dataStyleBase, maxLines = 1, softWrap = false)
            }
            // Headline excluded from WIDTH fit (it marquees when too long); data lines drive width.
            // Headline HEIGHT still counted so the whole block fits the zone.
            val widestPx = if (dataLayouts.isNotEmpty()) {
                dataLayouts.maxOf { it.size.width }.toFloat()
            } else {
                0f
            }
            val nameH = nameLayout?.size?.height?.toFloat() ?: 0f
            val textHPx = nameH + dataLayouts.sumOf { it.size.height }.toFloat()
            // RAW fill fraction — unclamped (wide-open bounds) so the source's INDEPENDENT sp-level
            // coerces below stay faithful (headline floor = 15sp flat, not 15sp × FILENAME_FACTOR).
            infoCardScale(
                availW, availH, widestPx, textHPx, gapPx, gapCount,
                minFrac = 0f,
                maxFrac = Float.MAX_VALUE,
            )
        }

        // Source-faithful coerce bounds (PrintStatusFocus.kt:259-260): data and headline clamp
        // independently — dataSp ∈ [minData, maxCap]; nameSp ∈ [minData, maxCap × FILENAME_FACTOR].
        val dataSp = (maxDataSp * sizeFrac).coerceIn(minDataSp, maxCapSp)
        val nameSp = (maxDataSp * FILENAME_FACTOR * sizeFrac).coerceIn(minDataSp, maxCapSp * FILENAME_FACTOR)
        val shadow = remember(t.surface, density) {
            Shadow(
                color = t.surface,
                offset = Offset(0f, with(density) { 2.dp.toPx() }),
                blurRadius = with(density) { 4.dp.toPx() },
            )
        }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BLOCK_LINE_GAP),
            ) {
                if (headline != null) {
                    Text(
                        text = headline,
                        color = t.text,
                        style = JiibType.screenTitle.toTextStyle(t, nameSp).copy(shadow = shadow),
                        maxLines = 1,
                        softWrap = false,
                        textAlign = TextAlign.Center,
                        // Indefinite scroll — intentional override of "no looping animation" design
                        // law; only animates when the headline actually overflows.
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE),
                    )
                }
                dataLines.forEach { line ->
                    Text(
                        text = line,
                        color = t.text,
                        style = JiibType.screenTitle.toTextStyle(t, dataSp).copy(shadow = shadow),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
