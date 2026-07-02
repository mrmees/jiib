package works.mees.jiib.designsystem.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import works.mees.jiib.designsystem.layout.FocusZoneInset
import works.mees.jiib.designsystem.layout.FocusZones

/**
 * Archetype #8 — form: TOP-FLOW rows of classed controls (the named LAW-1 exception, owned here)
 * + docked action. Dense inset per LAW 4.
 *
 * The body Column is the LAW-1 named exception: controls flow top-down with an 8dp spacedBy
 * rhythm instead of being centered. Callers pass classed control rows (ToggleRow, StepperRow,
 * OutlinedControl, etc.) directly — do NOT add a nested Column inside [body].
 */
@Composable
fun FocusForm(
    modifier: Modifier = Modifier,
    cap: (@Composable BoxScope.() -> Unit)? = null,
    dock: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) = FocusZones(inset = FocusZoneInset.Dense, modifier = modifier, cap = cap, dock = dock, body = {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = body,
    )
})
