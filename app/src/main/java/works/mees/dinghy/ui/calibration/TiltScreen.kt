package works.mees.dinghy.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.R
import works.mees.dinghy.calibration.TiltState
import works.mees.dinghy.calibration.TiltVm
import works.mees.dinghy.calibration.ZAdjustment
import works.mees.dinghy.calibration.tiltState
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/** The two routines this ONE screen serves (D-02 shared automatic-flow code path). */
enum class TiltVariant { ZTilt, Qgl }

/**
 * The Z-tilt / QGL shared automatic-flow screen (CALIB-03 / UI-SPEC §3). ONE code path serves both
 * routines — [variant] selects the title + which [CommandRegistry] command Run dispatches.
 *
 * This is a "run it and watch it converge" page with NO mid-run user action (no Abort; Klipper has no
 * clean cancel — the routine is short and hands-off). State is LOAD-scoped: every entry resets to the
 * landing state ([onEnter] → the holder's reset) because the printer has no reliable persistent
 * "applied" flag to read. The landing is "Ready to Run" (homed) or "Home Axis First" (unhomed). Once
 * Run fires, the in-flight set drives Running → Done; a dispatcher Failure → Failed. On Done the parsed
 * per-stepper Z adjustments (from the run's console output) are shown.
 *
 * Layout (ScreenScaffold):
 *  - Focus = the single `bed_tilt` icon, theme-tinted (NO state overlay glyph).
 *  - Field = the status headline + body; on Done, the per-stepper adjustment list.
 *  - Gutter = Run (homed; disabled while Running) OR Home All (unhomed) + Back.
 *
 * Ratio-only sizing; token-only color.
 *
 * @param vm         the resolved [TiltVm] (load-scoped run facts + homed gate + adjustments + error).
 * @param variant    Z-tilt or QGL — selects the title + dispatched command (D-02).
 * @param tokens     the active resolved tokens (THEME-01 — never raw color).
 * @param dispatcher the live session dispatcher; Run goes through it (null until a session exists).
 * @param onRunDispatched called after Run dispatches so the holder marks the routine run this load.
 * @param onHome     dispatch the homed pre-flight (G28) — the Home All offer (D-13).
 * @param onEnter    called once on page entry to reset the holder to the Idle landing state.
 * @param onBack     leave the page (neutral Back, D-10).
 */
@Composable
fun TiltScreen(
    vm: TiltVm,
    variant: TiltVariant,
    tokens: ThemeTokens,
    dispatcher: CommandDispatcher?,
    onRunDispatched: () -> Unit,
    onHome: () -> Unit,
    onEnter: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (variant) {
        TiltVariant.ZTilt -> "Z-Tilt Adjust"
        TiltVariant.Qgl -> "Quad Gantry Level"
    }
    val runLabel = when (variant) {
        TiltVariant.ZTilt -> "Z-Tilt"
        TiltVariant.Qgl -> "QGL"
    }
    val runCommand = when (variant) {
        TiltVariant.ZTilt -> CommandRegistry.zTiltAdjust
        TiltVariant.Qgl -> CommandRegistry.quadGantryLevel
    }

    // Every entry resets to the Idle landing state — a prior run never persists across loads.
    LaunchedEffect(Unit) { onEnter() }

    // Running vs Done is the dispatcher's in-flight truth (this screen owns the dispatcher). Done is the
    // run leaving the in-flight set without a Failure; we never read the persisted z_tilt.applied flag.
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val running = runCommand.dispatchKey(Unit) in inFlight
    val state = tiltState(ran = vm.ran, running = running, failed = vm.failed)

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            focus = {
                TiltFocus(title = title, modifier = Modifier.fillMaxSize().padding(8.dp))
            },
            field = {
                TiltField(
                    state = state,
                    homedGate = vm.homedGate,
                    runLabel = runLabel,
                    adjustments = vm.adjustments,
                    errorText = vm.errorText,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!vm.homedGate) {
                        // Home All pre-flight (D-13) — replaces Run until the printer is homed.
                        TiltGutterButton(
                            label = "Home All",
                            onClick = onHome,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Accent,
                            enabled = dispatcher != null,
                        )
                    } else {
                        TiltGutterButton(
                            label = if (running) "Running…" else "Run",
                            onClick = {
                                val d = dispatcher ?: return@TiltGutterButton
                                d.dispatch(runCommand, Unit)
                                onRunDispatched()
                            },
                            modifier = Modifier.weight(1f),
                            intent = Intent.Accent,
                            enabled = dispatcher != null && !running,
                        )
                    }
                    TiltGutterButton(
                        label = "Back",
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Neutral, // D-10: plain nav spends no safety color.
                    )
                }
            },
        )
    }
}

@Composable
private fun TiltFocus(title: String, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(22f, t.fs).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Box(
            Modifier.fillMaxWidth(0.9f).aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            // bed_tilt → token-tinted (THEME-01). The single Focus asset for both routines — no overlay.
            Icon(
                painter = painterResource(R.drawable.bed_tilt),
                contentDescription = title,
                tint = t.text2,
                modifier = Modifier.fillMaxSize(0.9f),
            )
        }
    }
}

@Composable
private fun TiltField(
    state: TiltState,
    homedGate: Boolean,
    runLabel: String,
    adjustments: List<ZAdjustment>,
    errorText: String?,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Column(modifier, verticalArrangement = Arrangement.Center) {
        when (state) {
            TiltState.Idle ->
                if (homedGate) {
                    HeadlineText("$runLabel Ready to Run", t.text)
                    BodyText("Run to level the gantry automatically.")
                } else {
                    HeadlineText("Home Axis First", t.heat)
                    BodyText("Home all axes before running $runLabel.")
                }
            TiltState.Running -> {
                HeadlineText("Running…", t.text)
                BodyText("Probing and adjusting. Hands-off — wait for it to converge.")
            }
            TiltState.Failed -> {
                HeadlineText("Failed", t.stop)
                BodyText(errorText ?: "The printer rejected the routine.")
            }
            TiltState.Done -> {
                HeadlineText("Leveled", t.go)
                if (adjustments.isEmpty()) {
                    BodyText("Gantry leveled.")
                } else {
                    BodyText("Z adjustments applied:")
                    adjustments.forEach { adj ->
                        Row(
                            Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = adj.stepper,
                                color = t.text2,
                                fontFamily = GeistMono,
                                fontWeight = FontWeight.Medium,
                                fontSize = fsSp(16f, t.fs).sp,
                            )
                            Text(
                                text = String.format(Locale.US, "%+.4f mm", adj.mm),
                                color = t.text,
                                fontFamily = GeistMono,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = fsSp(16f, t.fs).sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeadlineText(text: String, color: Color) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = color,
        fontFamily = GeistMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = fsSp(26f, t.fs).sp,
    )
}

@Composable
private fun BodyText(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text2,
        fontFamily = Geist,
        fontWeight = FontWeight.Normal,
        fontSize = fsSp(16f, t.fs).sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun TiltGutterButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: Intent = Intent.Neutral,
    enabled: Boolean = true,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = intentColor(intent, t)
    val base = modifier
        .heightIn(min = 64.dp)
        .clip(shape)
        .border(BorderStroke(2.dp, if (enabled) outline else t.hair), shape)
        .background(if (enabled) Color.Transparent else t.surface)
        .padding(horizontal = 12.dp, vertical = 18.dp)
    val box = if (enabled) base.clickable(onClick = onClick) else base
    Box(box, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = if (enabled) t.text else t.text3,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
            maxLines = 1,
        )
    }
}
