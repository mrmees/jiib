package works.mees.jiib.designsystem.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LAW 4 — the three Focus content inset tiers. Chosen by the ARCHETYPE, never the screen.
 * Default = display archetypes (16dp). Dense = any archetype with a Dock (8dp).
 * Flush = media fill (0dp).
 */
enum class FocusZoneInset { Default, Dense, Flush }

fun focusZoneInsetDp(inset: FocusZoneInset): Dp = when (inset) {
    FocusZoneInset.Default -> FocusInset
    FocusZoneInset.Dense -> FocusInset / 2
    FocusZoneInset.Flush -> 0.dp
}

/**
 * Zone padding values as plain data for host tests. Sides + bottom only; the TOP inset is
 * always 0 — the FocusFrame header divider is the top landmark (owner UAT 2026-06-15).
 */
data class FocusZonePadding(val start: Dp, val top: Dp, val end: Dp, val bottom: Dp)

fun focusZonePadding(inset: FocusZoneInset): FocusZonePadding {
    val d = focusZoneInsetDp(inset)
    return FocusZonePadding(start = d, top = 0.dp, end = d, bottom = d)
}

/** Gap between zones and between docked rows — the app-wide 8dp registration rhythm. */
val FocusZoneGap: Dp = ListFrameInset

/**
 * LAW 1 — the universal Focus body skeleton. Cap (optional, ≤1U, top) / Body (required,
 * weight(1f), content centered BOTH axes as a block) / Dock (optional, bottom-anchored,
 * 8dp rhythm; rows self-cap at 1U per UAT-5).
 *
 * Layer-1 primitive: only Layer-2 archetypes (designsystem/focus/) may call this —
 * enforced by FocusLayerContainmentTest.
 */
@Composable
fun FocusZones(
    inset: FocusZoneInset,
    modifier: Modifier = Modifier,
    cap: (@Composable BoxScope.() -> Unit)? = null,
    dock: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable BoxScope.() -> Unit,
) {
    val uDp = LocalUnitDp.current ?: 64.dp
    val p = focusZonePadding(inset)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = p.start, top = p.top, end = p.end, bottom = p.bottom),
        verticalArrangement = Arrangement.spacedBy(FocusZoneGap),
    ) {
        if (cap != null) {
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(max = uDp),
                contentAlignment = Alignment.Center,
                content = cap,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center,
            content = body,
        )
        if (dock != null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(FocusZoneGap),
                content = dock,
            )
        }
    }
}
