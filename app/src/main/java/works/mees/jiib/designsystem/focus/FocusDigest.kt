package works.mees.jiib.designsystem.focus

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import works.mees.jiib.designsystem.components.DigestColumn
import works.mees.jiib.designsystem.components.DigestRow
import works.mees.jiib.designsystem.layout.FocusZoneInset
import works.mees.jiib.designsystem.layout.FocusZones

/**
 * Archetype #3 — labelled data digest on DigestColumn (LAW 5 degrade).
 *
 * Inset is always [FocusZoneInset.Default] regardless of [dock] presence (LAW 4 digest carve-out
 * — FocusDigest keeps Default inset even when docked; Dense-when-docked remains for control
 * surfaces only). [maxScale] opts into grow-to-cap (LAW 5 extension): pass > 1f to allow the
 * digest to scale up when the zone has extra room.
 */
@Composable
fun FocusDigest(
    rows: List<DigestRow>,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    watermark: (@Composable BoxScope.() -> Unit)? = null,
    dock: (@Composable ColumnScope.() -> Unit)? = null,
    maxScale: Float = 1f,
) {
    FocusZones(
        inset = FocusZoneInset.Default,
        modifier = modifier,
        dock = dock,
        body = {
            watermark?.invoke(this)
            DigestColumn(rows = rows, modifier = Modifier.fillMaxSize(), horizontalAlignment = horizontalAlignment, maxScale = maxScale)
        },
    )
}
