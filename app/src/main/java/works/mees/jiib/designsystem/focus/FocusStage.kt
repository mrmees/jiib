package works.mees.jiib.designsystem.focus

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import works.mees.jiib.designsystem.layout.FocusZoneInset
import works.mees.jiib.designsystem.layout.FocusZones

/**
 * Archetype #7 — spatial/interactive surface: Cap (readout) / Body (stage, centered) / Dock (controls).
 * Dense inset per LAW 4 (dock-bearing control surface).
 */
@Composable
fun FocusStage(
    modifier: Modifier = Modifier,
    cap: (@Composable BoxScope.() -> Unit)? = null,
    dock: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable BoxScope.() -> Unit,
) = FocusZones(inset = FocusZoneInset.Dense, modifier = modifier, cap = cap, dock = dock, body = body)
