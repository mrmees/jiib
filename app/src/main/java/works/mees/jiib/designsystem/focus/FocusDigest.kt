package works.mees.jiib.designsystem.focus

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import works.mees.jiib.designsystem.components.DigestColumn
import works.mees.jiib.designsystem.components.DigestRow
import works.mees.jiib.designsystem.layout.FocusZones
import works.mees.jiib.designsystem.layout.focusZoneInsetFor

/** Archetype #3 — labelled data digest on DigestColumn (LAW 5 degrade). */
@Composable
fun FocusDigest(
    rows: List<DigestRow>,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    watermark: (@Composable BoxScope.() -> Unit)? = null,
    dock: (@Composable ColumnScope.() -> Unit)? = null,
) {
    FocusZones(
        inset = focusZoneInsetFor(hasDock = dock != null),
        modifier = modifier,
        dock = dock,
        body = {
            watermark?.invoke(this)
            DigestColumn(rows = rows, modifier = Modifier.fillMaxSize(), horizontalAlignment = horizontalAlignment)
        },
    )
}
