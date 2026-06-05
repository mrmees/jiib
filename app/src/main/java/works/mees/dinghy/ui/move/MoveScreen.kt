package works.mees.dinghy.ui.move

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
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
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
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
 * The Move panel (MOVE-01..04) — the printer-motion control surface, LAW per
 * docs/ui_design/images/04-move.png + README §4 (captured verbatim, D-03). Built on [ScreenScaffold]
 * (Focus/Field/Gutter); all color routes through [LocalTokens] role tokens (THEME-01); all live
 * numbers use GeistMono tabular numerals.
 *
 * ## Focus — the 3×3 jog pad (sacred squares, one per cell)
 * Mirrors the mockup grid exactly:
 * ```
 *   [Y corner]  [ ↑ Y+ ]  [ Override ]
 *   [ ← X− ]    [ XY home][ → X+ ]
 *   [Z corner]  [ ↓ Y− ]  [X corner]
 * ```
 *  - Edge cells = directional jog arrows → `jog("Y",±d)` / `jog("X",±d)` (MOVE-01).
 *  - Center cell = XY home → `homeXY()` (G28 X Y — XY ONLY, never a full G28; D-03 / MOVE-02).
 *  - Corner cells render the live X/Y/Z value-on-glyph (a big axis letter behind the GeistMono value),
 *    the letter colored green ([ThemeTokens.go]) when that axis is homed else amber ([ThemeTokens.heat]);
 *    tapping an axis corner homes THAT axis → `homeAxis(a)` (per-axis home, MOVE-02 / MOVE-04).
 *  - The amber Override cell ([Intent.Warn]) is the deliberate proceed-at-peril escape: when an axis is
 *    unhomed it jogs that axis via `overrideJog(axis,±d)` (SET_KINEMATIC_POSITION X=0 Y=0 Z=0 + relative
 *    move). Normal jog of an unhomed axis is GATED off (amber, disabled); Override is the only way (D-03).
 *
 * ## Field — Z row + the 6-up distance selector
 *  - A Z row of 3 squares: `∧` (Z+), the live Z value-on-glyph center, `∨` (Z−).
 *  - A 6-up fixed distance selector (0.1/1/10/25/50/100 mm); the active step is accent-outlined.
 *
 * ## Gutter (D-07) + Disable → ConfirmGuard (MOVE-03)
 *  - Home ([Intent.Accent]) → `homeAll()` (G28, all axes).
 *  - Disable ([Intent.Warn], amber) → raises the full-screen [ConfirmGuard] (`destructive=false` →
 *    amber proceed-at-peril); confirming dispatches `DISABLE_STEPPERS` (M84).
 *  - Back ([Intent.Danger], red) → [onBack].
 *
 * Every action dispatches a registry gcode entry via the per-session
 * [works.mees.dinghy.command.CommandDispatcher], never a raw rpc request. A control whose dispatch key
 * is in-flight is disabled (T-05-06-T). A dispatcher [DispatchEvent.Failure] (e.g. a gcode error from an
 * Override) surfaces a [SeverityToast].
 *
 * @param container the service-locator (provides the live `printerState` + the session dispatcher).
 * @param holder    the toolkit-agnostic [MoveHolder] (live X/Y/Z + per-axis homed gating).
 * @param onBack    invoked by the red Back gutter tile.
 */
@Composable
fun MoveScreen(
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
    val t = LocalTokens.current

    var distance by remember { mutableStateOf(10.0) } // default highlighted step (mockup shows 10).
    var showDisableGuard by remember { mutableStateOf(false) }
    var failureText by remember { mutableStateOf<String?>(null) }
    // Force-move ("unlocked") mode: jog issues FORCE_MOVE (no homing/limits) instead of G1. The red
    // override toggle arms it; default OFF (locked/safe). EXTR-/MOVE redesign 2026-06-01.
    var forceMove by remember { mutableStateOf(false) }

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

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            // Portrait: the jog pad is a sacred 1:1 square that fills the WIDTH; the field absorbs the
            // remaining height (the Z row + distance). Landscape is the normal 50/50 Focus|Field split.
            portraitFocusAspect = 1f,
            focus = {
                JogPad(
                    vm = vm,
                    distance = distance,
                    inFlight = inFlight,
                    forceMove = forceMove,
                    onJog = { axis, mm, feed ->
                        if (forceMove) {
                            dispatchCommand(CommandRegistry.forceMove, ForceMoveArgs(axis, mm, feed / 60))
                        } else {
                            dispatchCommand(CommandRegistry.jog, JogArgs(axis, mm, feed))
                        }
                    },
                    onHomeXY = { dispatchCommand(CommandRegistry.homeXY, Unit) },
                    onHomeAxis = { axis -> dispatchCommand(CommandRegistry.homeAxis, HomeAxisArgs(axis)) },
                    onToggleForceMove = { forceMove = !forceMove },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            },
            field = {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ZRow(
                        zHomed = vm.zHomed,
                        inFlight = inFlight,
                        forceMove = forceMove,
                        onJogZ = { mm ->
                            if (forceMove) {
                                dispatchCommand(CommandRegistry.forceMove, ForceMoveArgs("Z", mm, FEED_Z / 60))
                            } else {
                                dispatchCommand(CommandRegistry.jog, JogArgs("Z", mm, FEED_Z))
                            }
                        },
                        onHomeZ = { dispatchCommand(CommandRegistry.homeAxis, HomeAxisArgs("Z")) },
                        distance = distance,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    DistanceSelector(
                        selected = distance,
                        onSelect = { distance = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    failureText?.let { msg ->
                        SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
                    }
                }
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedControl(
                        label = "All",
                        onClick = { dispatchCommand(CommandRegistry.homeAll, Unit) },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent,
                        symbol = "home_and_garden",
                    )
                    OutlinedControl(
                        label = "Disable",
                        onClick = { showDisableGuard = true },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Danger, // destructive: releasing steppers un-homes the axes.
                    )
                    OutlinedControl(
                        label = "Back",
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Go, // plain back-nav is non-destructive (overrides LAW's red=back).
                    )
                }
            },
        )

        if (showDisableGuard) {
            // Destructive (red confirm): releasing steppers un-homes the axes. The guard dispatches
            // NOTHING — onConfirm does (PRIM-03).
            ConfirmGuard(
                title = "Disable steppers?",
                message = "Motors release; the toolhead can be moved by hand and axes become un-homed.",
                confirmLabel = "DISABLE",
                onConfirm = {
                    dispatchCommand(CommandRegistry.disableSteppers, Unit)
                    showDisableGuard = false
                },
                onCancel = { showDisableGuard = false },
                destructive = true,
            )
        }
    }
}

/**
 * The 3×3 jog pad — sacred-square cells, arranged per the mockup. Edge arrows jog X/Y by [distance];
 * the center homes XY; the Y/Z/X corners render the live value and tap to home that axis; the top-right
 * is the force-move toggle (the unhomed-jog escape). When [forceMove] is on, jog arrows issue
 * FORCE_MOVE and stay active even when unhomed; off, they jog with G1 gated on the axis being homed.
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

    // Sacred-square fit (NON-NEGOTIABLE 2): the 3×3 pad is the largest CENTERED square that fits the
    // focus region — sized to the SMALLER of the two dimensions so the aspect-ratio cells never overflow
    // their weighted rows (the bug: width-driven squares overran a short focus, eating the row gaps and
    // overlapping in portrait / touching in landscape). With a square pad, weight rows + weight cells +
    // the 8dp gaps land each cell exactly (S−16)/3 square, gaps intact, in BOTH orientations.
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        Column(
            Modifier.size(minOf(maxWidth, maxHeight)),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Row 0: Y readout · Y+ · force-move toggle
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AxisCorner("Y", vm.y, vm.yHomed, { onHomeAxis("Y") }, "home_Y" in inFlight, Modifier.weight(1f))
                JogCell("arrow_upward", { onJog("Y", distance, FEED_XY) }, jogDisabled(vm.yHomed, "jog_Y"), forceMove, Modifier.weight(1f))
                ForceMoveCell(enabled = forceMove, onToggle = onToggleForceMove, modifier = Modifier.weight(1f))
            }
            // Row 1: X− · XY home · X+
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                JogCell("arrow_back", { onJog("X", -distance, FEED_XY) }, jogDisabled(vm.xHomed, "jog_X"), forceMove, Modifier.weight(1f))
                HomeCell(homed = vm.xHomed && vm.yHomed, onHome = onHomeXY, disabled = "home_xy" in inFlight, modifier = Modifier.weight(1f))
                JogCell("arrow_forward", { onJog("X", distance, FEED_XY) }, jogDisabled(vm.xHomed, "jog_X"), forceMove, Modifier.weight(1f))
            }
            // Row 2: Z readout · Y− · X readout
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AxisCorner("Z", vm.z, vm.zHomed, { onHomeAxis("Z") }, "home_Z" in inFlight, Modifier.weight(1f))
                JogCell("arrow_downward", { onJog("Y", -distance, FEED_XY) }, jogDisabled(vm.yHomed, "jog_Y"), forceMove, Modifier.weight(1f))
                AxisCorner("X", vm.x, vm.xHomed, { onHomeAxis("X") }, "home_X" in inFlight, Modifier.weight(1f))
            }
        }
    }
}

/**
 * Directional-jog ICON tint (the outline stays blue — state is carried by the icon color only,
 * 2026-06-01): GRAY when unavailable, YELLOW in normal mode (jog moves the toolhead — caution), RED in
 * force-move mode (no homing/limits — extra danger).
 */
private fun jogIconTint(t: ThemeTokens, disabled: Boolean, forceMove: Boolean): Color = when {
    disabled -> t.text3
    forceMove -> t.stop
    else -> t.heat
}

/**
 * A directional jog arrow (Material Symbol). The outline wears the XY-plane directional color
 * ([ThemeTokens.directional]`.xy`, 15-07 / D-13) — the jog arrows identify the XY motion plane, so
 * they carry the plane color rather than the theme accent. The icon color signals state via
 * [jogIconTint] (gray/yellow/red). Disabled cells ignore taps.
 */
@Composable
private fun JogCell(
    symbol: String,
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
        MaterialSymbol(
            name = symbol,
            tint = jogIconTint(t, disabled, forceMove),
            sizeSp = fsSp(44f, t.fs),
        )
    }
}

/**
 * A home button cell (MOVE-02): a Material-Symbol home-state icon — `in_home_mode` when [homed],
 * `wifi_home` when it still needs homing (green/amber status-as-color). Used for BOTH the center XY
 * home and the Z home (D-redesign 2026-06-01); tapping issues the home command.
 */
@Composable
private fun HomeCell(homed: Boolean, onHome: () -> Unit, disabled: Boolean, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    PadCell(
        outline = if (disabled) t.hair else t.accentLine,
        onClick = onHome,
        disabled = disabled,
        modifier = modifier,
    ) {
        MaterialSymbol(
            name = if (homed) "in_home_mode" else "wifi_home",
            tint = if (homed) t.go else t.heat,
            sizeSp = fsSp(48f, t.fs),
        )
    }
}

/**
 * An axis readout cell: the axis letter (green=homed / amber=unhomed) stacked OVER the live value,
 * both large and readable (GeistMono). Tapping homes that axis (MOVE-02). The letter+value are stacked
 * (not overlaid) so neither is obscured (2026-06-01 fix to the old value-on-glyph collision).
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
    val glyphColor = if (homed) t.go else t.heat // green=homed / amber=unhomed (status-as-color).
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
            Text(
                text = axis,
                color = glyphColor,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(34f, t.fs).sp,
            )
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
 * The force-move toggle (replaces the old Override cell, 2026-06-01). A latching lock: GREEN + `lock`
 * when OFF (normal G1 jog, homing enforced), RED + `lock_open_right` when ON (jog issues FORCE_MOVE —
 * moves a stepper with NO homing/limit checks, the deliberate proceed-at-peril unhomed escape; requires
 * `enable_force_move` in the printer config). Tapping toggles the mode.
 */
@Composable
private fun ForceMoveCell(enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val color = if (enabled) t.stop else t.go // red unlocked (danger) / green locked (safe).
    PadCell(
        outline = color,
        onClick = onToggle,
        disabled = false,
        modifier = modifier,
    ) {
        MaterialSymbol(
            name = if (enabled) "lock_open_right" else "lock",
            tint = color,
            sizeSp = fsSp(40f, t.fs),
        )
    }
}

/** Shared jog-pad cell chrome: a sacred `aspectRatio(1f)` square, 2px token outline, ≥64dp floor. */
@Composable
private fun PadCell(
    outline: Color,
    onClick: () -> Unit,
    disabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    var box = modifier
        .aspectRatio(1f) // sacred square (NON-NEGOTIABLE 2).
        .clip(shape)
        .border(BorderStroke(2.dp, outline), shape)
    if (!disabled) box = box.clickable(onClick = onClick)
    Box(box, contentAlignment = Alignment.Center) { content() }
}

/**
 * The Z row — ∧ (Z+ jog) · Z HOME button · ∨ (Z− jog), each filling the row height. The center is the
 * Z home button (home-state icon: `in_home_mode` homed / `wifi_home` needs-homing); the live Z VALUE
 * lives in the focus Z corner (2026-06-01 swap). Z jog is gated on Z being homed.
 */
@Composable
private fun ZRow(
    zHomed: Boolean,
    inFlight: Set<String>,
    forceMove: Boolean,
    onJogZ: (mm: Double) -> Unit,
    onHomeZ: () -> Unit,
    distance: Double,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Force-move mode lifts the homed gate (its purpose); otherwise Z jog needs Z homed.
        val disabled = "jog_Z" in inFlight || (!forceMove && !zHomed)
        JogTall("expand", { onJogZ(distance) }, disabled, forceMove, Modifier.weight(1f))
        // Center: the Z HOME button, filling the tall row height (not the square PadCell).
        val homeDisabled = "home_Z" in inFlight
        var homeBox = Modifier.weight(1f).fillMaxSize().clip(shape)
            .border(BorderStroke(2.dp, if (homeDisabled) t.hair else t.accentLine), shape)
        if (!homeDisabled) homeBox = homeBox.clickable(onClick = onHomeZ)
        Box(homeBox, contentAlignment = Alignment.Center) {
            MaterialSymbol(
                name = if (zHomed) "in_home_mode" else "wifi_home",
                tint = if (zHomed) t.go else t.heat,
                sizeSp = fsSp(40f, t.fs),
            )
        }
        JogTall("compress", { onJogZ(-distance) }, disabled, forceMove, Modifier.weight(1f))
    }
}

/**
 * A full-height Z jog button (Material Symbol; fills the Z-row height — a tall column per mockup). The
 * outline wears the Z-plane directional color ([ThemeTokens.directional]`.z`, 15-07 / D-13) — the Z
 * jog buttons identify the Z motion plane. The icon color signals state via [jogIconTint]
 * (gray/yellow/red), matching [JogCell].
 */
@Composable
private fun JogTall(
    symbol: String,
    onClick: () -> Unit,
    disabled: Boolean,
    forceMove: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    var box = modifier.fillMaxSize().clip(shape)
        .border(BorderStroke(2.dp, t.directional.z), shape)
    if (!disabled) box = box.clickable(onClick = onClick)
    Box(box, contentAlignment = Alignment.Center) {
        MaterialSymbol(
            name = symbol,
            tint = jogIconTint(t, disabled, forceMove),
            sizeSp = fsSp(40f, t.fs),
        )
    }
}

/**
 * The fixed 6-up distance selector; the active step is accent-outlined, the rest neutral. Cells FILL
 * the row height (the field's two rows — Z controls + this — are equal-height weighted rows, 2026-06-01).
 */
@Composable
private fun DistanceSelector(selected: Double, onSelect: (Double) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (d in DISTANCES) {
            val active = d == selected
            val shape = RoundedCornerShape(t.rCtrl)
            Box(
                Modifier.weight(1f).fillMaxHeight()
                    .clip(shape)
                    .border(BorderStroke(2.dp, if (active) t.accentLine else t.outline), shape)
                    .clickable { onSelect(d) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = fmtDist(d),
                    color = if (active) t.accent2 else t.text2,
                    fontFamily = GeistMono,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = fsSp(15f, t.fs).sp,
                )
            }
        }
    }
}

/** Tabular-friendly one-decimal mm formatting (rounded, not truncated). */
private fun fmt(v: Double): String = ((v * 10).roundToInt() / 10.0).toString()

/** Distance label: drop the trailing ".0" on whole-mm steps (1/10/25/50/100), keep "0.1". */
private fun fmtDist(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
