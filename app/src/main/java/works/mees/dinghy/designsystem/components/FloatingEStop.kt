package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons

/**
 * A printing-only floating emergency-stop overlay button (~70% of one unit U).
 *
 * ## Overlay-sibling positioning rule (Pitfall 7)
 * [FloatingEStop] is a **Box sibling** of the Focus content — it must NOT be placed inside the
 * Focus Column. The caller wraps the Focus content in a `Box(Modifier.fillMaxSize())` and adds
 * [FloatingEStop] as a sibling with `Modifier.align(Alignment.TopStart).padding(14.dp)`.
 *
 * ```kotlin
 * // Correct — Box sibling (same pattern as ConfirmGuard in PrintStatusScreen):
 * Box(Modifier.fillMaxWidth().weight(1f)) {
 *     DetailCard(ringColor = spoolColor, modifier = Modifier.fillMaxSize()) { … }
 *     FloatingEStop(
 *         visible = isPrinting,
 *         onClick = onEmergencyStop,
 *         uDp = grid.uDp,
 *         modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
 *     )
 * }
 * ```
 *
 * ## Glyph — registered e-stop icon
 * Renders [DinghyIcons.StatusStop] (`disabled_by_default` ligature) — the owner-assigned e-stop
 * shape (square+✕ silhouette). This is the **SAME** registered token as the printer-stop status
 * indicator; it is reused here because it is the canonical owner-assigned emergency-halt glyph.
 * The raw `emergency_stop` string is intentionally NOT used; all glyphs must go through the
 * DinghyIcons registry per icon law ([[dinghy-never-pick-icons-ask]]).
 *
 * ## Intent
 * [Intent.Danger] — emergency stop is a destructive action that immediately halts the printer
 * firmware. Red is the correct affordance signal per docs/ui_design/THEMING.md button intent.
 *
 * @param visible  when `false` the composable returns immediately without emitting any layout.
 * @param onClick  invoked on tap — callers should present a [works.mees.dinghy.designsystem.ConfirmGuard]
 *                 before issuing the actual EMERGENCY_STOP gcode.
 * @param uDp      one unit U from [works.mees.dinghy.designsystem.layout.rememberUnitGrid];
 *                 the button is sized to `uDp * 0.7f` (icon tile at ~70% of U).
 * @param modifier caller-supplied modifier — typically `Modifier.align(Alignment.TopStart).padding(14.dp)`;
 *                 the component does NOT impose absolute offsets itself (Pitfall 7).
 */
@Composable
fun FloatingEStop(
    visible: Boolean,
    onClick: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    OutlinedControl(
        label = "",
        onClick = onClick,
        modifier = modifier.size(uDp * 0.7f),
        intent = Intent.Danger,
        // Registered e-stop glyph — disabled_by_default (owner-assigned in DinghyIcons.StatusStop).
        // NOT emergency_stop (that raw string is never used — icon law; 23-05 plan §Task 2).
        icon = DinghyIcons.StatusStop,
    )
}
