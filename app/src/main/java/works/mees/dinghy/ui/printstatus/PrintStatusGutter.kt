package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The shared gutter renderer — drives all four Print-Status modes from [PrintStatusUiModel.gutter]
 * (the 16-02 per-mode set). Extracted as a named top-level composable so the ScreenScaffold
 * `gutter` slot lambda body contains ONLY a call to this function — creating an independently-
 * restartable recomposition scope that ScreenScaffold can skip (D-01/D-02 P0 fix).
 *
 * The slot lambda in [PrintStatusContent] is still `@Composable () -> Unit` (ScreenScaffold's
 * param type); what changed is that its body is now a SINGLE call to [PrintStatusGutter] instead
 * of an inline block of layout — making this scope independently restartable.
 */
@Composable
internal fun PrintStatusGutter(
    ui: PrintStatusUiModel,
    pendingActionIsNull: Boolean,
    onRunAction: (PrintStatusControlAction) -> Unit,
    onEmergencyStopHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ui.gutter.forEach { control ->
            val renderControl = control.copy(
                enabled = control.enabled &&
                    (pendingActionIsNull ||
                        control.tapAction == PrintStatusControlAction.OpenFiles ||
                        control.tapAction == PrintStatusControlAction.EmergencyStop),
            )
            if (control.tapAction == PrintStatusControlAction.EmergencyStop) {
                StopButton(
                    onTap = { onRunAction(PrintStatusControlAction.EmergencyStop) },
                    onHold = onEmergencyStopHold,
                    modifier = Modifier.weight(1f),
                )
            } else {
                PrintStatusControlTile(
                    control = renderControl,
                    onTap = { control.tapAction?.let(onRunAction) },
                    onHold = { control.holdAction?.let(onRunAction) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The gutter Stop: a red `crisis_alert` glyph (no label). TAP opens the e-stop [ConfirmGuard]; HOLD
 * (>~½ s, the system long-press) fires the e-stop IMMEDIATELY (the panic path, with haptic) — Matthew.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StopButton(onTap: () -> Unit, onHold: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.stop), shape)
            .combinedClickable(onClick = onTap, onLongClick = onHold),
        contentAlignment = Alignment.Center,
    ) {
        // D-01/D-02/D-06: the stop-status silhouette (a redundant non-color cue for the stop state). The
        // `disabled_by_default` glyph is a square+✕, not a literal octagon — safety is carried by the
        // distinct shape (D-06). Explicit fsSp-scaled size — do NOT rely on the 96dp intrinsic.
        // [[dinghy-font-sizes-too-small]]: the glyph tracks adjacent control text via fsSp(baseSp, t.fs).
        DinghyIconView(
            DinghyIcons.StatusStop,
            tint = t.stop,
            sizeDp = fsSp(32f, t.fs).dp,
            contentDescription = stringResource(R.string.cd_emergency_stop),
        )
    }
}

/**
 * A single gutter control tile — outlined, touch-first, label-only. Combines tap (primary) and
 * optional long-press hold (secondary) actions. Enabled/disabled state drives outline color.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PrintStatusControlTile(
    control: PrintStatusControl,
    onTap: () -> Unit,
    onHold: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = if (control.enabled) controlColor(control, t) else t.hair
    val cancelCd = stringResource(R.string.cd_cancel_print)
    val base = modifier
        .heightIn(min = 64.dp)
        .clip(shape)
        .border(BorderStroke(2.dp, outline), shape)
        .then(
            if (control.accessibilityAction == PrintStatusControlAction.GracefulCancel && control.enabled) {
                Modifier.semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(cancelCd) {
                            onHold()
                            true
                        },
                    )
                }
            } else {
                Modifier
            },
        )
    val actionModifier = if (control.enabled) {
        base.combinedClickable(
            onClick = onTap,
            onLongClick = if (control.holdAction != null) onHold else null,
        )
    } else {
        base.semantics { disabled() }
    }

    Box(actionModifier, contentAlignment = Alignment.Center) {
        Text(
            text = control.label,
            color = if (control.enabled) t.text else t.text3,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun controlColor(control: PrintStatusControl, t: ThemeTokens): Color =
    when (control.tapAction) {
        PrintStatusControlAction.OpenFiles,
        PrintStatusControlAction.PausePrint,
        PrintStatusControlAction.Preheat,
        -> t.accentLine
        PrintStatusControlAction.ResumePrint,
        PrintStatusControlAction.RestartPrint,
        -> t.go
        PrintStatusControlAction.EmergencyStop,
        PrintStatusControlAction.GracefulCancel,
        PrintStatusControlAction.Power,
        -> t.stop
        PrintStatusControlAction.Tune,
        PrintStatusControlAction.Dismiss,
        null,
        -> t.hair
    }
