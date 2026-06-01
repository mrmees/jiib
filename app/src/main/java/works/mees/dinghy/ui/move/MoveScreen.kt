package works.mees.dinghy.ui.move

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.net.JsonRpcMethods
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
 * Every action dispatches via the per-session [works.mees.dinghy.command.CommandDispatcher] as a
 * `printer.gcode.script` ([JsonRpcMethods.GCODE_SCRIPT]) carrying `scriptParams(PrinterCommands.*)` —
 * never a raw rpc request. A control whose dispatch key is in-flight is disabled (T-05-06-T). A
 * dispatcher [DispatchEvent.Failure] (e.g. a gcode error from an Override) surfaces a [SeverityToast].
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

    // One dispatch helper — every action funnels through GCODE_SCRIPT + scriptParams (no raw rpc).
    fun script(key: String, gcode: String) {
        if (key in inFlight) return
        dispatcher?.dispatch(key, JsonRpcMethods.GCODE_SCRIPT, PrinterCommands.scriptParams(gcode))
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            focus = {
                JogPad(
                    vm = vm,
                    distance = distance,
                    inFlight = inFlight,
                    onJog = { axis, mm, feed -> script("jog_$axis", PrinterCommands.jog(axis, mm, feed)) },
                    onHomeXY = { script("home_xy", PrinterCommands.homeXY()) },
                    onHomeAxis = { axis -> script("home_$axis", PrinterCommands.homeAxis(axis)) },
                    onOverride = { axis, mm, feed ->
                        script("override_$axis", PrinterCommands.overrideJog(axis, mm, feed))
                    },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            },
            field = {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ZRow(
                        z = vm.z,
                        zHomed = vm.zHomed,
                        inFlight = inFlight,
                        onJogZ = { mm -> script("jog_Z", PrinterCommands.jog("Z", mm, FEED_Z)) },
                        distance = distance,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                    DistanceSelector(
                        selected = distance,
                        onSelect = { distance = it },
                        modifier = Modifier.fillMaxWidth(),
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
                        label = "Home",
                        onClick = { script("home_all", PrinterCommands.homeAll()) },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent,
                    )
                    OutlinedControl(
                        label = "Disable",
                        onClick = { showDisableGuard = true },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Warn,
                    )
                    OutlinedControl(
                        label = "Back",
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Danger,
                    )
                }
            },
        )

        if (showDisableGuard) {
            // Amber proceed-at-peril (destructive=false → go-soft tint + green confirm). The guard
            // dispatches NOTHING — onConfirm does (PRIM-03). Disabling steppers un-homes the axes.
            ConfirmGuard(
                title = "Disable steppers?",
                message = "Motors release; the toolhead can be moved by hand and axes become un-homed.",
                confirmLabel = "DISABLE",
                onConfirm = {
                    dispatcher?.dispatch(
                        "disable_steppers",
                        JsonRpcMethods.GCODE_SCRIPT,
                        PrinterCommands.scriptParams(PrinterCommands.DISABLE_STEPPERS),
                    )
                    showDisableGuard = false
                },
                onCancel = { showDisableGuard = false },
                destructive = false,
            )
        }
    }
}

/**
 * The 3×3 jog pad — sacred-square cells (each `aspectRatio(1f)`), arranged exactly per the mockup.
 * Edge arrows jog X/Y by [distance]; the center homes XY; the three axis corners render live
 * value-on-glyph and tap to home that axis; one corner is the amber Override (unhomed escape).
 */
@Composable
private fun JogPad(
    vm: MoveVm,
    distance: Double,
    inFlight: Set<String>,
    onJog: (axis: String, mm: Double, feed: Int) -> Unit,
    onHomeXY: () -> Unit,
    onHomeAxis: (axis: String) -> Unit,
    onOverride: (axis: String, mm: Double, feed: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The single unhomed axis the Override cell currently acts on (X→Y→Z priority); null when all homed.
    val overrideAxis: String? = when {
        !vm.xHomed -> "X"
        !vm.yHomed -> "Y"
        !vm.zHomed -> "Z"
        else -> null
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Row 0: Y corner · ↑ Y+ · Override
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AxisCorner("Y", vm.y, vm.yHomed, { onHomeAxis("Y") }, "home_Y" in inFlight, Modifier.weight(1f))
            JogCell("↑", { onJog("Y", distance, FEED_XY) }, !vm.yHomed || "jog_Y" in inFlight, Modifier.weight(1f))
            OverrideCell(
                axis = overrideAxis,
                onOverride = { a -> onOverride(a, distance, if (a == "Z") FEED_Z else FEED_XY) },
                disabled = overrideAxis == null || "override_${overrideAxis ?: ""}" in inFlight,
                modifier = Modifier.weight(1f),
            )
        }
        // Row 1: ← X− · XY home · → X+
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            JogCell("←", { onJog("X", -distance, FEED_XY) }, !vm.xHomed || "jog_X" in inFlight, Modifier.weight(1f))
            CenterCell("XY", onHomeXY, "home_xy" in inFlight, Modifier.weight(1f))
            JogCell("→", { onJog("X", distance, FEED_XY) }, !vm.xHomed || "jog_X" in inFlight, Modifier.weight(1f))
        }
        // Row 2: Z corner · ↓ Y− · X corner
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AxisCorner("Z", vm.z, vm.zHomed, { onHomeAxis("Z") }, "home_Z" in inFlight, Modifier.weight(1f))
            JogCell("↓", { onJog("Y", -distance, FEED_XY) }, !vm.yHomed || "jog_Y" in inFlight, Modifier.weight(1f))
            AxisCorner("X", vm.x, vm.xHomed, { onHomeAxis("X") }, "home_X" in inFlight, Modifier.weight(1f))
        }
    }
}

/** A directional jog arrow (accent = physical command). Disabled cells dim and ignore taps. */
@Composable
private fun JogCell(glyph: String, onClick: () -> Unit, disabled: Boolean, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    PadCell(
        outline = if (disabled) t.hair else t.accentLine,
        onClick = onClick,
        disabled = disabled,
        modifier = modifier,
    ) {
        Text(
            text = glyph,
            color = if (disabled) t.text3 else t.accent2,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(34f, t.fs).sp,
        )
    }
}

/** The center XY-home cell (accent fill hint). */
@Composable
private fun CenterCell(label: String, onClick: () -> Unit, disabled: Boolean, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    PadCell(
        outline = if (disabled) t.hair else t.accentLine,
        onClick = onClick,
        disabled = disabled,
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = if (disabled) t.text3 else t.accent2,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(28f, t.fs).sp,
        )
    }
}

/**
 * An axis corner cell: a big background axis letter (green=homed / amber=unhomed) with the live
 * value overlaid (GeistMono). Tapping homes that axis (MOVE-02). Value-on-glyph (CLAUDE.md "Dense
 * cells … overlay the value on a large background glyph").
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
        // Large faint axis letter behind the value.
        Text(
            text = axis,
            color = glyphColor,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(44f, t.fs).sp,
        )
        Text(
            text = value?.let { fmt(it) } ?: "—",
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(16f, t.fs).sp,
        )
    }
}

/**
 * The amber Override cell ([Intent.Warn]) — the deliberate proceed-at-peril unhomed-jog escape. Active
 * (amber) only while some axis is unhomed; tapping jogs that axis via `overrideJog` (MOVE / D-03).
 * When all axes are homed it has nothing to override and is inert/dim.
 */
@Composable
private fun OverrideCell(
    axis: String?,
    onOverride: (axis: String) -> Unit,
    disabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    PadCell(
        outline = if (axis == null) t.hair else t.heat, // amber when armed (Intent.Warn role token).
        onClick = { axis?.let(onOverride) },
        disabled = disabled,
        modifier = modifier,
    ) {
        Text(
            text = "⊘",
            color = if (axis == null) t.text3 else t.heat,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(34f, t.fs).sp,
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
 * The Z row — three squares (∧ Z+, live Z value-on-glyph center, ∨ Z−). Glyphs only (≥3 cols rule).
 * Z jog is gated on Z being homed; the center cell is a passive readout (homing Z is the Z corner).
 */
@Composable
private fun ZRow(
    z: Double?,
    zHomed: Boolean,
    inFlight: Set<String>,
    onJogZ: (mm: Double) -> Unit,
    distance: Double,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val disabled = !zHomed || "jog_Z" in inFlight
        JogTall("∧", { onJogZ(distance) }, disabled, Modifier.weight(1f))
        // Center: live Z value over a faint Z glyph (green=homed / amber=unhomed).
        Box(
            Modifier.weight(1f).fillMaxSize()
                .clip(RoundedCornerShape(t.rCtrl))
                .border(BorderStroke(2.dp, t.outline), RoundedCornerShape(t.rCtrl)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Z",
                color = if (zHomed) t.go else t.heat,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(40f, t.fs).sp,
            )
            Text(
                text = z?.let { fmt(it) } ?: "—",
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(16f, t.fs).sp,
            )
        }
        JogTall("∨", { onJogZ(-distance) }, disabled, Modifier.weight(1f))
    }
}

/** A full-height Z jog button (fills the Z-row height; not square — it is a tall column per mockup). */
@Composable
private fun JogTall(glyph: String, onClick: () -> Unit, disabled: Boolean, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    var box = modifier.fillMaxSize().clip(shape)
        .border(BorderStroke(2.dp, if (disabled) t.hair else t.accentLine), shape)
    if (!disabled) box = box.clickable(onClick = onClick)
    Box(box, contentAlignment = Alignment.Center) {
        Text(
            text = glyph,
            color = if (disabled) t.text3 else t.accent2,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(30f, t.fs).sp,
        )
    }
}

/** The fixed 6-up distance selector; the active step is accent-outlined, the rest neutral. */
@Composable
private fun DistanceSelector(selected: Double, onSelect: (Double) -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (d in DISTANCES) {
            val active = d == selected
            val shape = RoundedCornerShape(t.rCtrl)
            Box(
                Modifier.weight(1f).aspectRatio(1f) // square tiles (CLAUDE.md "square the smallest buttons").
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
