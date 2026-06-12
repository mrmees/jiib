package works.mees.dinghy.ui.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.R
import works.mees.dinghy.calibration.TiltHolder
import works.mees.dinghy.calibration.TiltState
import works.mees.dinghy.calibration.TiltVm
import works.mees.dinghy.calibration.ZAdjustment
import works.mees.dinghy.calibration.tiltState
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.components.FloatingEStop
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/** The two routines this ONE screen serves (D-10 shared automatic-flow code path). */
enum class TiltVariant { ZTilt, Qgl }

/**
 * The Z-tilt / QGL shared automatic-flow screen (CALIB-03 / D-10 jiib restyle). ONE code path
 * serves both routines — [variant] selects the title + which [CommandRegistry] command Run dispatches.
 *
 * Thin VM-collecting wrapper that forwards a plain snapshot to the STATELESS [TiltContent]
 * composable (WARNING-5 seam). [TiltContent] is the @Preview target.
 *
 * @param container the service-locator (provides the session dispatcher).
 * @param holder    the headless [TiltHolder] (load-scoped run facts + homed gate + adjustments + error).
 * @param variant   Z-tilt or QGL — selects the title + dispatched command.
 * @param onBack    leave the page (neutral Back, D-10).
 */
@Composable
fun TiltScreen(
    container: AppContainer,
    holder: TiltHolder,
    variant: TiltVariant,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val vm by holder.vm.collectAsStateWithLifecycle()

    val runCommand = when (variant) {
        TiltVariant.ZTilt -> CommandRegistry.zTiltAdjust
        TiltVariant.Qgl -> CommandRegistry.quadGantryLevel
    }

    // Every entry resets to the Idle landing state.
    LaunchedEffect(Unit) { holder.reset() }

    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val running = runCommand.dispatchKey(Unit) in inFlight
    val state = tiltState(ran = vm.ran, running = running, failed = vm.failed)

    TiltContent(
        vm = vm,
        variant = variant,
        state = state,
        running = running,
        onRun = {
            val d = dispatcher ?: return@TiltContent
            d.dispatch(runCommand, Unit)
            holder.markDispatched()
        },
        onHome = { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless layout for the Z-tilt / QGL page — @Preview target (WARNING-5 seam).
 *
 * Both [TiltVariant.ZTilt] and [TiltVariant.Qgl] render from this ONE parameterized composable.
 *
 * Layout:
 *  - Focus = bed-tilt icon + state headline (token-restyled, unchanged information).
 *  - Field = status body text + adjustment recommendations (result state). No scrollable list.
 *  - Foot = state-adaptive [FootButtonBar] (gutter = null, D-10).
 */
@Composable
fun TiltContent(
    vm: TiltVm,
    variant: TiltVariant = TiltVariant.ZTilt,
    state: TiltState = TiltState.Idle,
    running: Boolean = false,
    onRun: () -> Unit = {},
    onHome: () -> Unit = {},
    onBack: () -> Unit = {},
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

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        Box(Modifier.fillMaxSize()) {
            ScreenScaffold(
                focus = {
                    TiltFocus(
                        title = title,
                        state = state,
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                    )
                },
                field = {
                    TiltFieldBody(
                        state = state,
                        homedGate = vm.homedGate,
                        runLabel = runLabel,
                        adjustments = vm.adjustments,
                        errorText = vm.errorText,
                        modifier = Modifier.weight(1f).padding(8.dp),
                    )
                    // State-adaptive FootButtonBar (gutter = null, D-10).
                    FootButtonBar(
                        uDp = grid.uDp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        when {
                            !vm.homedGate -> {
                                // Unhomed: offer Home All.
                                OutlinedControl(
                                    label = stringResource(R.string.calibration_home_all),
                                    onClick = onHome,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Accent,
                                )
                            }
                            running -> {
                                // Running: non-interactive.
                                OutlinedControl(
                                    label = stringResource(R.string.calibration_running),
                                    onClick = {},
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Neutral,
                                    enabled = false,
                                )
                            }
                            state == TiltState.Done || state == TiltState.Failed -> {
                                // Result shown: Run Again.
                                OutlinedControl(
                                    label = stringResource(R.string.calibration_run_again),
                                    onClick = onRun,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Neutral,
                                )
                            }
                            else -> {
                                // Homed idle: Run (variant-selected, gated on not Running).
                                OutlinedControl(
                                    label = stringResource(R.string.calibration_run),
                                    onClick = onRun,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Accent,
                                    enabled = !running,
                                )
                            }
                        }
                        OutlinedControl(
                            label = "",
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Neutral,
                            icon = DinghyIcons.Back,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                gutter = null,
            )
            // FloatingEStop top-left corner reservation (UAT-4).
            // Calibration = pop-to-root foot-gun: Tilt is only reachable while idle.
            FloatingEStop(
                visible = false,
                onClick = {},
                uDp = grid.uDp,
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
            )
        }
    }
}

@Composable
private fun TiltFocus(
    title: String,
    state: TiltState,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val headlineColor = when (state) {
        TiltState.Done -> t.go
        TiltState.Failed -> t.stop
        TiltState.Running -> t.text
        TiltState.Idle -> t.text
    }
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
            // bed_tilt → token-tinted (THEME-01). ONE Focus asset for both variants — no overlay.
            Icon(
                painter = painterResource(R.drawable.bed_tilt),
                contentDescription = title,
                tint = headlineColor,
                modifier = Modifier.fillMaxSize(0.9f),
            )
        }
    }
}

@Composable
private fun TiltFieldBody(
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
                    TiltHeadlineText("$runLabel Ready to Run", t.text)
                    TiltBodyText("Run to level the gantry automatically.")
                } else {
                    TiltHeadlineText("Home Axis First", t.heat)
                    TiltBodyText("Home all axes before running $runLabel.")
                }
            TiltState.Running -> {
                TiltHeadlineText("Running…", t.text)
                TiltBodyText("Probing and adjusting. Hands-off — wait for it to converge.")
            }
            TiltState.Failed -> {
                TiltHeadlineText("Failed", t.stop)
                TiltBodyText(errorText ?: "The printer rejected the routine.")
            }
            TiltState.Done -> {
                TiltHeadlineText("Leveled", t.go)
                if (adjustments.isEmpty()) {
                    TiltBodyText("Gantry leveled.")
                } else {
                    TiltBodyText("Z adjustments applied:")
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
private fun TiltHeadlineText(text: String, color: Color) {
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
private fun TiltBodyText(text: String) {
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
