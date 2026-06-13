package works.mees.dinghy.designsystem.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons

/**
 * A printing-only floating emergency-stop overlay button (~70% of one unit U).
 *
 * ## Role after the Focus-header law (2026-06-13) — FALLBACK, not the general pattern
 * The canonical e-stop is now the **FocusFrame header dock**: every FocusFrame's start-icon slot
 * morphs into the e-stop while printing (see [works.mees.dinghy.designsystem.components.FocusFrame]).
 * This [FloatingEStop] survives ONLY as the **shell-level fallback** for the handful of destinations
 * that do NOT render a FocusFrame header — currently **Webcam** (full-bleed media, the deliberate
 * header exemption) and **Theme** (outside the header migration scope). It is rendered once, app-level,
 * in [works.mees.dinghy.ui.shell.AppShell] and gated by `!screenOwnsEstop` (the set of header-owning
 * destinations) — so it appears only where no header e-stop exists. It is NOT placed per-screen anymore.
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
 *                 the button is sized to `(uDp * 0.7f).coerceAtLeast(64.dp)` (~70% of U with a
 *                 64dp touch-target floor so the box never drops below the ≥64dp minimum and
 *                 stays a square on tablet-sized U values).
 * @param onHold   optional long-press PANIC path — fires the e-stop IMMEDIATELY, no guard (the
 *                 hold semantics the retired gutter StopButton carried: tap = guarded, hold ≈ ½s
 *                 = instant halt — Matthew). Null = tap-only (pre-R1 behavior).
 * @param modifier caller-supplied modifier — typically `Modifier.align(Alignment.TopStart).padding(14.dp)`;
 *                 the component does NOT impose absolute offsets itself (Pitfall 7).
 */
@Composable
fun FloatingEStop(
    visible: Boolean,
    onClick: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
    onHold: (() -> Unit)? = null,
) {
    if (!visible) return

    OutlinedControl(
        label = "",
        onClick = onClick,
        onLongClick = onHold,
        modifier = modifier.size((uDp * 0.7f).coerceAtLeast(64.dp)),
        intent = Intent.Danger,
        // Registered e-stop glyph — disabled_by_default (owner-assigned in DinghyIcons.StatusStop).
        // NOT emergency_stop (that raw string is never used — icon law; 23-05 plan §Task 2).
        icon = DinghyIcons.StatusStop,
        // Icon-only control: without this TalkBack speaks the raw ligature name (WR-05). The
        // spoken affordance the retired gutter StopButton carried (Codex W-01).
        contentDescription = stringResource(R.string.cd_emergency_stop),
    )
}
