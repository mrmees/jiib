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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import works.mees.dinghy.R
import works.mees.dinghy.calibration.ProbeCalibrateVm
import works.mees.dinghy.calibration.ProbePageState
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.TestZArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.theme.seriesColor

/**
 * Fine TESTZ jog steps (mm), ascending — the +/- step selector walks this list. Selecting a step is a
 * setting, not a command. Spans coarse (10 mm) down to ultra-fine (0.005 mm) paper-test nudges.
 */
private val TESTZ_STEPS = listOf(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 5.0, 10.0)

/**
 * The interactive manual-probe Z-calibrate page (CALIB-05 / D-01 / UI-SPEC §5). This is the ONE
 * stateful calibration page: Start opens a manual-probe session, the jog pad nudges Z via TESTZ,
 * Accept/Abort closes it, and an amber SAVE_CONFIG persists the captured offset.
 *
 * The three Focus/gutter states are driven purely by [ProbeCalibrateVm.state]
 * ([ProbePageState.Idle]/[ProbePageState.Active]/[ProbePageState.Accepted] — derived from
 * `manual_probe.is_active`). The screen NEVER infers state; it renders the vm verbatim.
 *
 * Layout (ScreenScaffold):
 *  - Focus: the nozzle + "expand" (Z-gap) icons side by side over a single centered cell holding the
 *    current Z offset read from the printer — saved `probe.z_offset` when Idle (your current calibration),
 *    the live `manual_probe.z_position` when Active (updates as you nudge), the captured offset when
 *    Accepted (green). See [ProbeFocus].
 *  - Field = the jog pad (Z nudge disabled until Active): row 1 = a [−]/[+] step selector walking
 *    [TESTZ_STEPS] with the current step shown between; row 2 = up/down arrows firing TESTZ(±step).
 *  - Gutter state-adaptive: Idle → Start (blue, the gated PROBE_CALIBRATE/Z_ENDSTOP_CALIBRATE) + Back
 *    (green); Active → Accept (green) + Abort (red) — BACK SUPPRESSED while a session is live
 *    (T-09-06-02 — leaving mid-probe leaves a dangling session); Accepted → Save (amber → ConfirmGuard
 *    restart gate → SAVE_CONFIG) + Back (green, discards the in-memory offset).
 *
 * Ratio-only sizing; token-only color.
 *
 * @param vm         the resolved [ProbeCalibrateVm] (state machine + live Z + bracket + start cmd + error).
 * @param tokens     the active resolved tokens (THEME-01 — never raw color).
 * @param dispatcher the live session dispatcher; all actions go through it (null until a session exists).
 * @param onStartDispatched called after Start dispatches (parity with the other calibration screens).
 * @param onBack     leave the page (neutral Back, D-10; only offered when NOT Active).
 */
@Composable
fun ProbeCalibrateScreen(
    vm: ProbeCalibrateVm,
    tokens: ThemeTokens,
    dispatcher: CommandDispatcher?,
    onStartDispatched: () -> Unit,
    onEnter: () -> Unit,
    onHome: () -> Unit,
    onAbort: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Fresh-instance reset on entry (mirrors TiltScreen) — the per-session holder's sawActive/captured
    // latches survive re-entry, so without this a returning user sees the previous round's offset as a
    // stale Accepted page. Keyed on Unit → once per navigation into the page, not on recomposition.
    LaunchedEffect(Unit) { onEnter() }

    var step by remember { mutableStateOf(0.05) } // default a sensible fine step.
    var saveGuard by remember { mutableStateOf(false) }
    val active = vm.state == ProbePageState.Active

    // Immediate "Starting…" feedback: the Start gcode is in flight but the session hasn't gone Active yet
    // (the klicky macro homes/attaches/probes for several seconds before is_active flips). While that
    // start key is in flight AND we're still Idle, the gutter shows a disabled "Starting…" so the tap is
    // acknowledged instantly. Self-clears: success → state leaves Idle; failure → the key leaves inFlight.
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val starting = vm.state == ProbePageState.Idle &&
        ("probe_calibrate" in inFlight || "z_endstop_calibrate" in inFlight)

    // Dismissable error toast: a rejected TESTZ/Accept/Start surfaces a redacted Failure. Drive the toast
    // from local state so it can be dismissed (tap) and auto-clears after a few seconds — the holder's
    // folded errorText persists, which previously left the popup stuck on screen. Seeded from live
    // dispatcher Failures AND the holder fold (catches a failure that predated this collector).
    var toastError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dispatcher) {
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event -> if (event is DispatchEvent.Failure) toastError = event.message }
    }
    LaunchedEffect(vm.errorText) { vm.errorText?.let { toastError = it } }
    LaunchedEffect(toastError) {
        if (toastError != null) {
            delay(5_000)
            toastError = null
        }
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            focus = {
                ProbeFocus(vm = vm, modifier = Modifier.fillMaxSize().padding(8.dp))
            },
            field = {
                ProbeJogPad(
                    // Disable the Z nudge while a TESTZ is in flight — gives visual feedback that the move
                    // is happening and prevents a double-tap stacking moves before the new height confirms.
                    // "testz" leaves inFlight when the gcode.script response lands (the height is settled).
                    enabled = active && dispatcher != null && "testz" !in inFlight,
                    step = step,
                    onSelectStep = { step = it },
                    onTestZ = { delta -> dispatcher?.dispatch(CommandRegistry.testZ, TestZArgs(delta)) },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (vm.state) {
                        ProbePageState.Idle -> if (starting) {
                            // Start dispatched, session not yet live — acknowledge the tap, suppress Back
                            // (a session is beginning, like the Active state).
                            ProbeGutterButton(
                                label = "Starting…",
                                onClick = {},
                                modifier = Modifier.weight(1f),
                                intent = Intent.Accent,
                                enabled = false,
                            )
                        } else if (!vm.homedGate) {
                            // Home All pre-flight (D-13) — replaces Start until the printer is homed, so we
                            // never send PROBE_CALIBRATE unhomed (the klicky macro raises "Must Home … First!").
                            ProbeGutterButton(
                                label = "Home All",
                                onClick = onHome,
                                modifier = Modifier.weight(1f),
                                intent = Intent.Accent,
                                enabled = dispatcher != null,
                            )
                            ProbeGutterButton(
                                label = "Back",
                                onClick = onBack,
                                modifier = Modifier.weight(1f),
                                intent = Intent.Neutral, // D-10: plain nav spends no safety color.
                            )
                        } else {
                            ProbeGutterButton(
                                label = "Start",
                                onClick = {
                                    val d = dispatcher ?: return@ProbeGutterButton
                                    // The gated Z-calibrate command (A3): PROBE_CALIBRATE vs Z_ENDSTOP_CALIBRATE.
                                    if (vm.startCommand == "Z_ENDSTOP_CALIBRATE") {
                                        d.dispatch(CommandRegistry.zEndstopCalibrate, Unit)
                                    } else {
                                        d.dispatch(CommandRegistry.probeCalibrate, Unit)
                                    }
                                    onStartDispatched()
                                },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Accent,
                                enabled = dispatcher != null,
                            )
                            ProbeGutterButton(
                                label = "Back",
                                onClick = onBack,
                                modifier = Modifier.weight(1f),
                                intent = Intent.Neutral, // D-10: plain nav spends no safety color.
                            )
                        }
                        ProbePageState.Active -> {
                            // BACK SUPPRESSED while a session is live (T-09-06-02) — Accept or Abort to leave.
                            ProbeGutterButton(
                                label = "Accept",
                                onClick = { dispatcher?.dispatch(CommandRegistry.accept, Unit) },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Go,
                                enabled = dispatcher != null,
                            )
                            ProbeGutterButton(
                                label = "Abort",
                                onClick = {
                                    // Mark abort BEFORE dispatching so the is_active→false transition
                                    // returns to Idle (Start/Back), not Accepted (no Save of a discarded run).
                                    onAbort()
                                    dispatcher?.dispatch(CommandRegistry.abort, Unit)
                                },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Danger,
                                enabled = dispatcher != null,
                            )
                        }
                        ProbePageState.Accepted -> {
                            ProbeGutterButton(
                                label = "Save",
                                onClick = { saveGuard = true },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Warn,
                                enabled = dispatcher != null,
                            )
                            ProbeGutterButton(
                                label = "Back",
                                onClick = onBack,
                                modifier = Modifier.weight(1f),
                                intent = Intent.Neutral, // D-10: plain nav spends no safety color.
                            )
                        }
                    }
                }
            },
        )

        // Amber SAVE_CONFIG restart gate (D-12) — persists the captured probe offset.
        if (saveGuard) {
            ConfirmGuard(
                title = "Save & restart?",
                message = "This saves the new Z offset and restarts the printer — the expected result of " +
                    "calibration. The connection will briefly drop and reconnect.",
                confirmLabel = "Save & restart",
                warn = true,
                onConfirm = {
                    dispatcher?.dispatch(CommandRegistry.saveConfig, Unit)
                    saveGuard = false
                },
                onCancel = { saveGuard = false },
            )
        }

        // Failure → SeverityToast(Error) with the printer's REDACTED RpcError text (never e.message,
        // T-09-06-05) — overlaid at the bottom so the user sees WHY a TESTZ/Accept/Start was refused.
        // Tap to dismiss; also auto-clears after a few seconds (no longer a stuck popup).
        val shownError = toastError
        if (shownError != null) {
            Box(
                Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                SeverityToast(
                    Severity.Error,
                    shownError,
                    Modifier.fillMaxWidth().clickable { toastError = null },
                )
            }
        }
    }
}

/**
 * Focus = three icons (nozzle — expand — bed) over the focal Z value (theme accent). State-adaptive:
 *  - Idle → the saved `probe.z_offset` from config, shown NEGATED (Moonraker stores it positive; the
 *    nozzle-to-bed offset reads as negative);
 *  - Active → the LIVE `manual_probe.z_position` from the macro feedback (as reported), with the original
 *    saved offset (negated) shown smaller + non-accent underneath for reference;
 *  - Accepted → the captured offset, same saved-offset reference underneath.
 */
@Composable
private fun ProbeFocus(vm: ProbeCalibrateVm, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val saved = vm.savedZOffset // raw, stored positive in Moonraker
    // Where the printer thinks the head is — the macro-feedback current Z (live while Active, the captured
    // value once Accepted).
    val currentZ = when (vm.state) {
        ProbePageState.Active -> vm.zPosition
        ProbePageState.Accepted -> vm.capturedOffset
        else -> null
    }
    // The big focal value (theme accent) = "what the offset is going to be", negated for display (offsets
    // read negative, matching the saved value):
    //  - Idle: the current saved offset, negated (stored positive).
    //  - In-process: the resulting offset = −(initial offset − current head Z) = current head Z − initial.
    val zText = when (vm.state) {
        ProbePageState.Idle -> saved?.let { fmtZ(-it) } ?: "—"
        else -> if (saved != null && currentZ != null) fmtZ(currentZ - saved) else "—"
    }
    val zColor = t.accent2
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Three icons side by side: detector (the probe) — expand (the Z gap) — nozzle — the
        // "probe detects the bed, set the gap to the nozzle" story.
        Row(
            Modifier.fillMaxWidth(0.82f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.detector),
                contentDescription = "Probe",
                tint = t.text2,
                modifier = Modifier.weight(1f).aspectRatio(1f),
            )
            Icon(
                painter = painterResource(R.drawable.expand),
                contentDescription = "Z offset",
                tint = t.text2,
                modifier = Modifier.weight(1f).aspectRatio(1f),
            )
            Icon(
                painter = painterResource(R.drawable.nozzle),
                contentDescription = "Nozzle",
                tint = t.text2,
                modifier = Modifier.weight(1f).aspectRatio(1f),
            )
        }
        // The focal value — single centered cell under the icons, ~1.5× size, theme accent.
        Text(
            text = zText,
            color = zColor,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(72f, t.fs).sp,
            modifier = Modifier.padding(top = 20.dp),
        )
        // During the process, line 2 = the current saved offset (negated) and where the head is ("Z", the
        // raw macro-feedback current). Both smaller + non-accent. So the three values read as: hero = the
        // offset that's going to be set; "saved" = the offset currently in config; "Z" = the head position.
        if (vm.state != ProbePageState.Idle) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                saved?.let {
                    Text(
                        text = "saved  ${fmtZ(-it)}",
                        color = t.text2,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = fsSp(24f, t.fs).sp,
                    )
                }
                currentZ?.let {
                    Text(
                        text = "Z  ${fmtZ(it)}",
                        color = t.text2,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = fsSp(24f, t.fs).sp,
                    )
                }
            }
        }
    }
}

/**
 * The jog pad. Row 1 = the step selector: a [−] button, the current step (mm), and a [+] button —
 * +/- walk [TESTZ_STEPS] (clamped at the ends); selecting a step is a setting (white/neutral), always
 * enabled so the step is set before Start. Row 2 = the Z nudge: an up-arrow / down-arrow (blue/accent
 * physical commands) firing TESTZ(+step)/TESTZ(-step), disabled until the session is Active.
 */
@Composable
private fun ProbeJogPad(
    enabled: Boolean,
    step: Double,
    onSelectStep: (Double) -> Unit,
    onTestZ: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val idx = TESTZ_STEPS.indexOf(step).let { if (it < 0) 0 else it }
    // Two columns (the field rotated 90°): each control stacks in its natural vertical logical order —
    // larger/up on top, smaller/down on bottom — instead of laying a vertical relationship out sideways.
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Step selector column: [+] larger / current step / [−] smaller. Always enabled (the step is a setting).
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProbeIconButton(
                painter = painterResource(R.drawable.add),
                contentDescription = "Larger step",
                onClick = { onSelectStep(TESTZ_STEPS[(idx + 1).coerceAtMost(TESTZ_STEPS.lastIndex)]) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                intent = Intent.Neutral,
                enabled = idx < TESTZ_STEPS.lastIndex,
            )
            StepDisplay(value = step, modifier = Modifier.weight(1f).fillMaxWidth())
            ProbeIconButton(
                painter = painterResource(R.drawable.remove),
                contentDescription = "Smaller step",
                onClick = { onSelectStep(TESTZ_STEPS[(idx - 1).coerceAtLeast(0)]) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                intent = Intent.Neutral,
                enabled = idx > 0,
            )
        }
        // Z nudge column: ↑ raise on top, ↓ lower on bottom — the high/low HEIGHT affordances.
        // Owner rule (15.2-06 closed-loop sweep): the height ends follow the theme — HIGH = accent,
        // LOW = the generated data pool (seriesColor(1) == pool[0] in Colorful, the same source the
        // temp graph / data series use). Token-only (THEME-01); pool is mode-aware (Colorful/Simple/
        // High-Contrast all resolve through seriesColor), never a raw color and never a status color.
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProbeIconButton(
                painter = painterResource(R.drawable.arrow_upward),
                contentDescription = "Raise nozzle (TESTZ +)",
                onClick = { onTestZ(step) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                intent = Intent.Accent,
                iconTint = t.accent2, // HIGH end → accent.
                enabled = enabled,
            )
            ProbeIconButton(
                painter = painterResource(R.drawable.arrow_downward),
                contentDescription = "Lower nozzle (TESTZ -)",
                onClick = { onTestZ(-step) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                intent = Intent.Accent,
                iconTint = t.seriesColor(1), // LOW end → pool data color (pool[0] in Colorful).
                enabled = enabled,
            )
        }
    }
}

/** The current adjustment step (mm) — a neutral-outlined read-only cell between the [−]/[+] buttons. */
@Composable
private fun StepDisplay(value: Double, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier.clip(shape).border(BorderStroke(2.dp, t.outline), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = fmtStep(value),
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(34f, t.fs).sp,
            )
            Text(
                text = "mm",
                color = t.text3,
                fontFamily = Geist,
                fontSize = fsSp(15f, t.fs).sp, // 15.2-06: metadata floor 15sp ([[dinghy-font-sizes-too-small]]).
            )
        }
    }
}

/** An outline-led icon button (mirrors [ProbeGutterButton] but renders an Icon glyph, not a label). */
@Composable
private fun ProbeIconButton(
    painter: Painter,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: Intent = Intent.Neutral,
    iconTint: Color? = null,
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
    val box = if (enabled) base.clickable(onClick = onClick) else base
    Box(box, contentAlignment = Alignment.Center) {
        Icon(
            painter = painter,
            contentDescription = contentDescription,
            tint = if (!enabled) t.text3 else (iconTint ?: t.text),
            modifier = Modifier.fillMaxHeight(0.5f).aspectRatio(1f),
        )
    }
}

@Composable
private fun ProbeGutterButton(
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

/** Three-decimal mm — manual-probe nudges are fine (down to 0.005 mm). */
private fun fmtZ(v: Double): String = String.format(Locale.US, "%.3f", v)

/** Step label: drop the trailing ".0" on whole-mm steps, keep the fractional ones (0.005 … 0.5). */
private fun fmtStep(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
