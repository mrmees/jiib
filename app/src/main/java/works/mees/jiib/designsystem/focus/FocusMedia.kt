package works.mees.jiib.designsystem.focus

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import works.mees.jiib.designsystem.layout.FocusZoneInset
import works.mees.jiib.designsystem.layout.FocusZones

/**
 * Archetype #6 — flush-fill media/canvas host (graph, console feed, heatmap).
 * Inset is always [FocusZoneInset.Flush] — content bleeds edge-to-edge per LAW 4.
 * [overlay] renders above [content] in the same Box (for e-stop, scrubber overlays, etc.).
 */
@Composable
fun FocusMedia(
    modifier: Modifier = Modifier,
    cap: (@Composable BoxScope.() -> Unit)? = null,
    dock: (@Composable ColumnScope.() -> Unit)? = null,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) = FocusZones(inset = FocusZoneInset.Flush, modifier = modifier, cap = cap, dock = dock, body = {
    content()
    overlay?.invoke(this)
})
