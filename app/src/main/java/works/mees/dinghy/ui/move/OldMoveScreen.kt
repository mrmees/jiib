package works.mees.dinghy.ui.move

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.CommandSpec
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.ForceMoveArgs
import works.mees.dinghy.command.HomeAxisArgs
import works.mees.dinghy.command.JogArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FocusFrame

import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.icons.IconRef
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/** Jog feedrates (mm/min) — XY fast, Z slow (Claude discretion; clamped again in PrinterCommands). */
private const val FEED_XY = 3000
private const val FEED_Z = 600

/** The fixed 6-up jog-distance selector (mm), verbatim from 04-move.png (LAW, D-03). */
private val DISTANCES = listOf(0.1, 1.0, 10.0, 25.0, 50.0, 100.0)

/**
 * The Move panel — thin VM-reading wrapper that forwards to the stateless [OldMoveContent].
 *
 * This public entry-point reads the live holder/dispatcher state and wires callbacks to the
 * stateless [OldMoveContent] composable (WARNING-5: `@Preview` targets [OldMoveContent] directly,
 * not this VM-bound wrapper — no AppContainer or VM is instantiated in previews).
 *
 * @param container service-locator (provides live `printerState` + session dispatcher).
 * @param holder    toolkit-agnostic [MoveHolder] (live X/Y/Z + per-axis homed gating).
 * @param onBack    invoked by the Back foot button.
 */
@Composable
fun OldMoveScreen(
    container: AppContainer,
    holder: MoveHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val vm by holder.vm.collectAsStateWithLifecycle()
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused

    var failureText by remember { mutableStateOf<String?>(null) }

    // Surface a dispatch failure (redacted message) as an error toast (PRIM-04). Reset on session swap.
    LaunchedEffect(dispatcher) {
        failureText = null
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event ->
            when (event) {
                is DispatchEvent.Failure -> failureText = event.message
            }
        }
    }
    LaunchedEffect(failureText) {
        if (failureText != null) {
            delay(4_000)
            failureText = null
        }
    }

    // One dispatch helper — every action funnels through the registry (no raw rpc).
    fun <P> dispatchCommand(command: CommandSpec<P>, args: P) {
        if (command.dispatchKey(args) in inFlight) return
        dispatcher?.dispatch(command, args)
    }

    OldMoveContent(
        vm = vm,
        inFlight = inFlight,
        failureText = failureText,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onJog = { axis, mm, feed -> dispatchCommand(CommandRegistry.jog, JogArgs(axis, mm, feed)) },
        onForceJog = { axis, mm, feed -> dispatchCommand(CommandRegistry.forceMove, ForceMoveArgs(axis, mm, feed / 60)) },
        onHomeXY = { dispatchCommand(CommandRegistry.homeXY, Unit) },
        onHomeAxis = { axis -> dispatchCommand(CommandRegistry.homeAxis, HomeAxisArgs(axis)) },
        onHomeAll = { dispatchCommand(CommandRegistry.homeAll, Unit) },
        onDisable = { dispatchCommand(CommandRegistry.disableSteppers, Unit) },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless preview seam for [OldMoveScreen] (WARNING-5 / ExtrudeScreen.kt:209 pattern).
 *
 * All callbacks default to no-ops; all data is injected. Used by `MovePreviews.kt`.
 * This overload has no VM/AppContainer dependency so `@Preview` can render it without
 * a live Moonraker connection.
 */
@Composable
fun OldMoveScreen(
    vm: MoveVm = MoveVm(),
    inFlight: Set<String> = emptySet(),
    isPrinting: Boolean = false,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    OldMoveContent(
        vm = vm,
        inFlight = inFlight,
        failureText = null,
        isPrinting = isPrinting,
        onEmergencyStop = {},
        onJog = { _, _, _ -> },
        onForceJog = { _, _, _ -> },
        onHomeXY = {},
        onHomeAxis = {},
        onHomeAll = {},
        onDisable = {},
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless layout composable for the Move panel. Accepts all VM data and callbacks as plain
 * parameters — no live Moonraker, no AppContainer, no ViewModel. Targeted directly by
 * `MovePreviews.kt` (WARNING-5 seam).
 *
 * ## Layout
 *
 * Uses [BoxWithConstraints] for manual sizing (NOT `portraitFocusAspect` — the Move screen has a
 * specialized-layout exemption like Extrude's D-15, since the spatial jog pad must stay a grid).
 *
 * **Portrait (D-03):**
 * `padSize = (maxHeight * 0.60f).coerceAtMost(maxWidth)` — the 60%-of-height cap guarantees
 * room for the Z/distance columns beneath the pad.
 *
 * **Landscape:**
 * The pad fills full height of its half; Z column + distance stepper sit beside it.
 *
 * **Gutter = null** — Home All / Disable / Back live in [FootButtonBar] inside the layout.
 *
 * ## Sacred-square JogPad (D-04)
 * Pad internals carry over unchanged: home-XY center, axis-corner live readouts + tap-to-home,
 * red force-move toggle. Only the chrome tokens are updated.
 */
@Composable
internal fun OldMoveContent(
    vm: MoveVm,
    inFlight: Set<String>,
    failureText: String?,
    isPrinting: Boolean = false,
    onEmergencyStop: () -> Unit = {},
    onJog: (axis: String, mm: Double, feed: Int) -> Unit,
    onForceJog: (axis: String, mm: Double, feed: Int) -> Unit,
    onHomeXY: () -> Unit,
    onHomeAxis: (axis: String) -> Unit,
    onHomeAll: () -> Unit,
    onDisable: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var distance by remember { mutableStateOf(10.0) } // default highlighted step (mockup shows 10).
    var showDisableGuard by remember { mutableStateOf(false) }
    var forceMove by remember { mutableStateOf(false) }

    // Wrap jog dispatch with force-move mode awareness
    fun jog(axis: String, mm: Double, feed: Int) {
        if (forceMove) onForceJog(axis, mm, feed) else onJog(axis, mm, feed)
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val landscape = maxWidth > maxHeight

        if (landscape) {
            // Landscape: JogPad fills left half; right half = FocusFrame header + Z col + distance col + FootButtonBar
            Row(Modifier.fillMaxSize()) {
                JogPad(
                    vm = vm,
                    distance = distance,
                    inFlight = inFlight,
                    forceMove = forceMove,
                    onJog = ::jog,
                    onHomeXY = onHomeXY,
                    onHomeAxis = onHomeAxis,
                    onToggleForceMove = { forceMove = !forceMove },
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                )
                Column(
                    Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FocusFrame(
                        title = stringResource(R.string.cd_launcher_move),
                        icon = DinghyIcons.LauncherMove,
                        uDp = grid.uDp,
                        modifier = Modifier.fillMaxWidth(),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        MoveFocusBlurb()
                    }
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ZColumn(
                            zHomed = vm.zHomed,
                            zValue = vm.z,
                            inFlight = inFlight,
                            forceMove = forceMove,
                            onJogZ = { mm -> jog("Z", mm, FEED_Z) },
                            onHomeZ = { onHomeAxis("Z") },
                            distance = distance,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                        DistanceStepperColumn(
                            distance = distance,
                            onSelect = { distance = it },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                    failureText?.let { msg ->
                        SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
                    }
                    FootButtonBar(
                        uDp = grid.uDp,
                        modifier = Modifier.padding(horizontal = 0.dp, vertical = 4.dp),
                    ) {
                        MoveFootButtons(onHomeAll, { showDisableGuard = true }, onBack, Modifier.weight(1f))
                    }
                }
            }
        } else {
            // Portrait (D-03): FocusFrame header + JogPad (capped at 60% HEIGHT); remaining = Z col + distance + footer
            val padSize: Dp = (maxHeight * 0.60f).coerceAtMost(maxWidth)
            Column(Modifier.fillMaxSize()) {
                // Focus header — identiy + e-stop when printing
                FocusFrame(
                    title = stringResource(R.string.cd_launcher_move),
                    icon = DinghyIcons.LauncherMove,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxWidth(),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    MoveFocusBlurb()
                }
                // JogPad at padSize × padSize, centered horizontally
                Box(
                    Modifier.fillMaxWidth().height(padSize),
                    contentAlignment = Alignment.Center,
                ) {
                    JogPad(
                        vm = vm,
                        distance = distance,
                        inFlight = inFlight,
                        forceMove = forceMove,
                        onJog = ::jog,
                        onHomeXY = onHomeXY,
                        onHomeAxis = onHomeAxis,
                        onToggleForceMove = { forceMove = !forceMove },
                        modifier = Modifier.size(padSize).padding(8.dp),
                    )
                }
                // Bottom: Z col + distance col + footer bar
                Row(
                    Modifier.fillMaxWidth().weight(1f).padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ZColumn(
                        zHomed = vm.zHomed,
                        zValue = vm.z,
                        inFlight = inFlight,
                        forceMove = forceMove,
                        onJogZ = { mm -> jog("Z", mm, FEED_Z) },
                        onHomeZ = { onHomeAxis("Z") },
                        distance = distance,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    DistanceStepperColumn(
                        distance = distance,
                        onSelect = { distance = it },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                failureText?.let { msg ->
                    SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
                }
                FootButtonBar(
                    uDp = grid.uDp,
                ) {
                    MoveFootButtons(onHomeAll, { showDisableGuard = true }, onBack, Modifier.weight(1f))
                }
            }
        }

        // Disable ConfirmGuard (D-04: carry over unchanged semantics)
        if (showDisableGuard) {
            ConfirmGuard(
                title = stringResource(R.string.move_disable_steppers),
                message = stringResource(R.string.move_disable_confirm),
                confirmLabel = stringResource(R.string.move_disable_steppers).uppercase(),
                onConfirm = {
                    onDisable()
                    showDisableGuard = false
                },
                onCancel = { showDisableGuard = false },
                destructive = true,
            )
        }
    }
}

/**
 * Focus body blurb — shown inside the [FocusFrame] header region when no richer hero is present.
 * Move's position readouts live in the JogPad corner cells (the hero control); the Focus body
 * is a brief orientation blurb per the D8 spec.
 */
@Composable
private fun MoveFocusBlurb() {
    val t = LocalTokens.current
    Text(
        text = stringResource(R.string.move_focus_blurb),
        color = t.text2,
        fontFamily = Geist,
        fontSize = fsSp(17f, t.fs).sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** The three foot buttons shared between portrait and landscape: Home All | Disable | Back. */
@Composable
private fun MoveFootButtons(
    onHomeAll: () -> Unit,
    onShowDisable: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedControl(
        label = stringResource(R.string.move_home_all),
        onClick = onHomeAll,
        modifier = modifier,
        intent = Intent.Accent,
        icon = DinghyIcons.LauncherMove,
        contentDescription = stringResource(R.string.move_home_all),
    )
    OutlinedControl(
        label = stringResource(R.string.move_disable_steppers),
        onClick = onShowDisable,
        modifier = modifier,
        intent = Intent.Danger,
    )
    OutlinedControl(
        label = "",
        onClick = onBack,
        modifier = modifier,
        intent = Intent.Neutral,
        icon = DinghyIcons.Back,
        contentDescription = stringResource(R.string.common_back),
    )
}

/**
 * The vertical 3-cell Z column (D-01): Z-up / live-Z readout + home / Z-down.
 *
 * Uses `t.directional.z` outline on both jog cells to identify the Z motion plane.
 * The center cell shows the live Z value (Geist Mono tabular numerals) AND acts as the
 * Z home button — the SAME z value read by the JogPad Z corner (Pitfall 7: both surfaces
 * read [vm.z] / [zValue] — no divergence).
 *
 * All three cells use `Modifier.weight(1f)` — equal thirds. No cell uses weight(2f)
 * to accommodate a larger readout (Pitfall 4 / anti-pattern 7).
 */
@Composable
private fun ZColumn(
    zHomed: Boolean,
    zValue: Double?,
    inFlight: Set<String>,
    forceMove: Boolean,
    onJogZ: (mm: Double) -> Unit,
    onHomeZ: () -> Unit,
    distance: Double,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val disabled = "jog_Z" in inFlight || (!forceMove && !zHomed)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Top: Z+ jog with directional.z outline
        JogColumnCell(
            symbol = "expand",
            contentDescription = stringResource(R.string.move_z_up),
            outline = t.directional.z,
            onClick = { onJogZ(distance) },
            disabled = disabled,
            forceMove = forceMove,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        // Center: live Z readout + tap-to-home (Pitfall 7 — same z value as JogPad Z corner)
        HomeZCell(
            zHomed = zHomed,
            zValue = zValue,
            onHomeZ = onHomeZ,
            inFlight = inFlight,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        // Bottom: Z- jog with directional.z outline
        JogColumnCell(
            symbol = "compress",
            contentDescription = stringResource(R.string.move_z_down),
            outline = t.directional.z,
            onClick = { onJogZ(-distance) },
            disabled = disabled,
            forceMove = forceMove,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/**
 * The vertical 3-cell distance stepper column (D-02): + / active-step readout / −.
 *
 * Cycles the fixed [DISTANCES] set with index coercion — no free numeric entry.
 * Uses `t.outline` (neutral) — this is a step-selector, not a directional control.
 * Uses [DinghyIcons.BabystepExpand] / [DinghyIcons.BabystepCompress] per the owner
 * decision in 27-01 SUMMARY (Group B: reuse expand/compress).
 *
 * All three cells use `Modifier.weight(1f)` (equal thirds — Pitfall 4).
 */
@Composable
private fun DistanceStepperColumn(
    distance: Double,
    onSelect: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val idx = DISTANCES.indexOf(distance).coerceAtLeast(0)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Top: increase distance
        DistanceStepCell(
            icon = DinghyIcons.BabystepExpand,
            contentDescription = stringResource(R.string.move_distance_increase),
            onClick = { onSelect(DISTANCES[(idx + 1).coerceAtMost(DISTANCES.lastIndex)]) },
            enabled = idx < DISTANCES.lastIndex,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        // Center: current distance readout
        DistanceDisplay(
            distance = distance,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        // Bottom: decrease distance
        DistanceStepCell(
            icon = DinghyIcons.BabystepCompress,
            contentDescription = stringResource(R.string.move_distance_decrease),
            onClick = { onSelect(DISTANCES[(idx - 1).coerceAtLeast(0)]) },
            enabled = idx > 0,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

/**
 * A single + or − cell in the distance stepper column.
 * Neutral outline (step-selector identity, not directional).
 */
@Composable
private fun DistanceStepCell(
    icon: works.mees.dinghy.designsystem.icons.DinghyIcon,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = if (enabled) t.outline else t.hair
    var box = modifier
        .clip(shape)
        .border(BorderStroke(2.dp, outline), shape)
    if (enabled) box = box.clickable(onClick = onClick)
    Box(box, contentAlignment = Alignment.Center) {
        DinghyIconView(
            icon = icon,
            contentDescription = contentDescription,
            tint = if (enabled) t.text else t.text3,
            sizeDp = 40.dp,
        )
    }
}

/**
 * The current jog distance readout (center cell of the distance stepper column).
 * Geist Mono tabular numerals; neutral outline (step-selector identity).
 */
@Composable
private fun DistanceDisplay(distance: Double, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier.clip(shape).border(BorderStroke(2.dp, t.outline), shape),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = fmtDist(distance),
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(22f, t.fs).sp,
            )
            Text(
                text = "mm",
                color = t.text2,
                fontFamily = GeistMono,
                fontSize = fsSp(14f, t.fs).sp,
            )
        }
    }
}

/**
 * The Z column center cell: live Z readout + tap-to-home.
 *
 * Shows the same `zValue` as the JogPad Z corner AxisCorner — the IDENTICAL value
 * (Pitfall 7: both surfaces must read the same data source; they are wired via the
 * same [MoveVm.z] flowing from [MoveHolder]).
 * Tapping homes the Z axis via [onHomeZ].
 */
@Composable
private fun HomeZCell(
    zHomed: Boolean,
    zValue: Double?,
    onHomeZ: () -> Unit,
    inFlight: Set<String>,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val homeDisabled = "home_Z" in inFlight
    val shape = RoundedCornerShape(t.rCtrl)
    var box = modifier
        .clip(shape)
        .border(BorderStroke(2.dp, if (homeDisabled) t.hair else t.directional.z), shape)
    if (!homeDisabled) box = box.clickable(onClick = onHomeZ)
    Box(box, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = zValue?.let { fmt(it) } ?: "—",
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(22f, t.fs).sp,
            )
            Text(
                text = stringResource(R.string.move_z_home),
                color = if (zHomed) t.go else t.heat,
                fontSize = fsSp(13f, t.fs).sp,
            )
        }
    }
}

/**
 * A directional jog cell for the Z column (expand = Z+, compress = Z−).
 * Uses the specified [outline] color (directional.z for the Z column).
 */
@Composable
private fun JogColumnCell(
    symbol: String,
    contentDescription: String,
    outline: Color,
    onClick: () -> Unit,
    disabled: Boolean,
    forceMove: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val activeOutline = if (disabled) t.hair else outline
    var box = modifier
        .clip(shape)
        .border(BorderStroke(2.dp, activeOutline), shape)
    if (!disabled) box = box.clickable(onClick = onClick)
    Box(box, contentAlignment = Alignment.Center) {
        MaterialSymbol(
            name = symbol,
            modifier = Modifier.clearAndSetSemantics {},
            tint = jogIconTint(t, disabled, forceMove),
            sizeSp = fsSp(40f, t.fs),
        )
    }
}

/**
 * The 3×3 jog pad — sacred-square cells, arranged per the mockup (D-04: internals carry over
 * unchanged). Edge arrows jog X/Y by [distance]; the center homes XY; the Y/Z/X corners render
 * the live value and tap to home that axis; the top-right is the force-move toggle (the
 * unhomed-jog escape). When [forceMove] is on, jog arrows issue FORCE_MOVE and stay active
 * even when unhomed; off, they jog with G1 gated on the axis being homed.
 */
@Composable
private fun JogPad(
    vm: MoveVm,
    distance: Double,
    inFlight: Set<String>,
    forceMove: Boolean,
    onJog: (axis: String, mm: Double, feed: Int) -> Unit,
    onHomeXY: () -> Unit,
    onHomeAxis: (axis: String) -> Unit,
    onToggleForceMove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // A jog of [axis] is blocked only by an in-flight dispatch, OR — in normal (locked) mode — by that
    // axis being unhomed. Force-move mode lifts the homed gate (that IS its purpose).
    fun jogDisabled(axisHomed: Boolean, key: String): Boolean =
        key in inFlight || (!forceMove && !axisHomed)

    // Sacred-square fit: the 3×3 pad is the largest CENTERED square that fits the focus region.
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.size(minOf(maxWidth, maxHeight)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Row 0: Y readout · Y+ · force-move toggle
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AxisCorner("Y", vm.y, vm.yHomed, { onHomeAxis("Y") }, "home_Y" in inFlight, Modifier.weight(1f))
                JogCell(rawJogArrow("arrow_upward"), { onJog("Y", distance, FEED_XY) }, jogDisabled(vm.yHomed, "jog_Y"), forceMove, Modifier.weight(1f))
                ForceMoveCell(enabled = forceMove, onToggle = onToggleForceMove, modifier = Modifier.weight(1f))
            }
            // Row 1: X− · XY home · X+ (X+ = the registry-backed JogXPlus token, 27-review WR-04)
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JogCell(rawJogArrow("arrow_back"), { onJog("X", -distance, FEED_XY) }, jogDisabled(vm.xHomed, "jog_X"), forceMove, Modifier.weight(1f))
                HomeCell(homed = vm.xHomed && vm.yHomed, onHome = onHomeXY, disabled = "home_xy" in inFlight, modifier = Modifier.weight(1f))
                JogCell(DinghyIcons.JogXPlus, { onJog("X", distance, FEED_XY) }, jogDisabled(vm.xHomed, "jog_X"), forceMove, Modifier.weight(1f))
            }
            // Row 2: Z readout · Y− · X readout (Pitfall 7: Z corner also reads vm.z — same source as ZColumn)
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AxisCorner("Z", vm.z, vm.zHomed, { onHomeAxis("Z") }, "home_Z" in inFlight, Modifier.weight(1f))
                JogCell(rawJogArrow("arrow_downward"), { onJog("Y", -distance, FEED_XY) }, jogDisabled(vm.yHomed, "jog_Y"), forceMove, Modifier.weight(1f))
                AxisCorner("X", vm.x, vm.xHomed, { onHomeAxis("X") }, "home_X" in inFlight, Modifier.weight(1f))
            }
        }
    }
}

/**
 * Directional-jog ICON tint: GRAY when unavailable, ACCENT in normal mode, RED in force-move mode.
 *
 * 15.2-06 (M1 / C1): normal-jog is ACCENT (the screen's EXPECTED physical action — C1), not caution.
 * Force-move-armed is the stop color (genuinely hazardous — no homing/limits).
 */
private fun jogIconTint(t: ThemeTokens, disabled: Boolean, forceMove: Boolean): Color = when {
    disabled -> t.text3
    forceMove -> t.stop
    else -> t.accent2
}

/**
 * The three non-registry jog arrows (`arrow_upward` / `arrow_back` / `arrow_downward`) as inline
 * [DinghyIcon]s — the sanctioned 18.1-03 ProbeIconButton pattern: rendered a11y-safe through
 * [DinghyIconView] without promoting the sites into the [DinghyIcons] registry. All three names are
 * covered by the verify_ligatures.py NEEDED set (and `arrow_back` is registry-backed anyway via
 * [DinghyIcons.Back]). The X+ arrow (`arrow_forward`) IS registry-backed — [DinghyIcons.JogXPlus]
 * (27-review WR-04) — and is passed as that token at its call site.
 */
private fun rawJogArrow(name: String) = DinghyIcon(IconRef.Ligature(name), alternate = name)

/**
 * A directional jog arrow (Material Symbols ligature rendered through [DinghyIconView] — 27-review
 * WR-04). The outline wears the XY-plane directional color ([ThemeTokens.directional]`.xy`). The
 * icon color signals state via [jogIconTint]. Disabled cells ignore taps. The null
 * contentDescription marks the glyph decorative (DinghyIconView clears semantics — same TalkBack
 * behavior as the previous explicit clearAndSetSemantics).
 */
@Composable
private fun JogCell(
    icon: DinghyIcon,
    onClick: () -> Unit,
    disabled: Boolean,
    forceMove: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    PadCell(
        outline = t.directional.xy,
        onClick = onClick,
        disabled = disabled,
        modifier = modifier,
    ) {
        DinghyIconView(
            icon = icon,
            tint = jogIconTint(t, disabled, forceMove),
            sizeDp = fsSp(44f, t.fs).dp,
        )
    }
}

/**
 * A home button cell (MOVE-02): the registry-backed home-state token — [DinghyIcons.HomeStateHomed]
 * (`in_home_mode`) when [homed], [DinghyIcons.HomeStateUnhomed] (`wifi_home`) when it still needs
 * homing (green/amber status-as-color; glyphs unchanged, promoted to tokens by 27-review WR-04).
 * The outline wears the XY-plane directional color so the XY home reads as part of the XY group.
 */
@Composable
private fun HomeCell(homed: Boolean, onHome: () -> Unit, disabled: Boolean, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    PadCell(
        outline = if (disabled) t.hair else t.directional.xy,
        onClick = onHome,
        disabled = disabled,
        modifier = modifier,
    ) {
        DinghyIconView(
            icon = if (homed) DinghyIcons.HomeStateHomed else DinghyIcons.HomeStateUnhomed,
            tint = if (homed) t.go else t.heat,
            sizeDp = fsSp(48f, t.fs).dp,
        )
    }
}

/**
 * An axis readout cell: the axis letter (green=homed / amber=unhomed) stacked over the live value,
 * both in GeistMono. Tapping homes that axis (MOVE-02). An UNHOMED axis additionally shows the
 * caution-triangle shape glyph (D-01/D-02 shape-coded safety layer).
 */
@Composable
private fun AxisCorner(
    axis: String,
    value: Double?,
    homed: Boolean,
    onHome: () -> Unit,
    disabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val glyphColor = if (homed) t.go else t.heat
    PadCell(
        outline = t.outline,
        onClick = onHome,
        disabled = disabled,
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = axis,
                    color = glyphColor,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(34f, t.fs).sp,
                )
                if (!homed) {
                    StatusShape(
                        glyphName = "warning",
                        tint = t.heat,
                        sizeSp = fsSp(20f, t.fs),
                    )
                }
            }
            Text(
                text = value?.let { fmt(it) } ?: "—",
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(24f, t.fs).sp,
            )
        }
    }
}

/**
 * Render a status SHAPE Material Symbols ligature tinted from a role token at an EXPLICIT fsSp size.
 * Decorative status shapes carry no contentDescription (the meaning is in the labeled element they sit on).
 */
@Composable
private fun StatusShape(glyphName: String, tint: Color, sizeSp: Float, modifier: Modifier = Modifier) {
    MaterialSymbol(
        name = glyphName,
        modifier = modifier.clearAndSetSemantics {},
        tint = tint,
        sizeSp = sizeSp,
    )
}

/**
 * The force-move toggle (D-04 carry-over + 15.2-06 M2/C4): latching lock with shape-coded safety layer.
 * OFF (SAFE) = green closed padlock (`lock`); ON (ARMED) = red open padlock (`lock_open_right`).
 * When armed: filled stop-red background (C4 law — visible catastrophic-mode signal).
 */
@Composable
private fun ForceMoveCell(enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val color = if (enabled) t.stop else t.go
    PadCell(
        outline = color,
        onClick = onToggle,
        disabled = false,
        fill = if (enabled) t.stopSoft else Color.Transparent,
        modifier = modifier,
    ) {
        StatusShape(
            glyphName = if (enabled) "lock_open_right" else "lock",
            tint = color,
            sizeSp = fsSp(40f, t.fs),
        )
    }
}

/**
 * Shared jog-pad cell chrome: a sacred `aspectRatio(1f)` square, 2dp token outline, ≥64dp floor.
 * [fill] is the optional cell background (transparent by default; a soft token tint for armed states, C4).
 */
@Composable
private fun PadCell(
    outline: Color,
    onClick: () -> Unit,
    disabled: Boolean,
    modifier: Modifier = Modifier,
    fill: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    var box = modifier
        .aspectRatio(1f) // sacred square (NON-NEGOTIABLE 2).
        .clip(shape)
        .background(fill, shape)
        .border(BorderStroke(2.dp, outline), shape)
    if (!disabled) box = box.clickable(onClick = onClick)
    Box(box, contentAlignment = Alignment.Center) { content() }
}

/** Tabular-friendly one-decimal mm formatting (rounded, not truncated). */
private fun fmt(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()

/** Distance label: drop the trailing ".0" on whole-mm steps (1/10/25/50/100), keep "0.1". */
private fun fmtDist(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
