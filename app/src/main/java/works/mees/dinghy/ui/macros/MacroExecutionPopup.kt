package works.mees.dinghy.ui.macros

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.MacroInvocation
import works.mees.dinghy.command.MacroParamRejected
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.NumpadPage
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.ui.screen.TokenTextField

/**
 * Generous numeric bound for a NumpadPage opened on a macro param. The macro body never declares a
 * range, so this is intentionally wide — NumpadPage is still the SOLE owner of numeric range-clamping
 * (S4), it just clamps against this sane envelope. A printer-side limit (e.g. an out-of-range move)
 * is enforced by Klipper and surfaces as a `DispatchEvent.Failure` toast, not by this UI.
 */
private val MACRO_NUMERIC_RANGE = -100_000.0..100_000.0

/**
 * The macro Execution popup (D-08/D-09/D-10, MACRO-01/02) — a full-screen overlay that IS the
 * deliberate action gate. There is intentionally NO [works.mees.dinghy.designsystem.ConfirmGuard]
 * layered on top (D-08): committing parameters and tapping Execute IS the confirmation.
 *
 * One entry field is rendered per detected [MacroParam], pre-filled with the parsed `|default(...)`:
 *  - a numeric param (`type` == `int`/`double`) → a tappable value control that opens [NumpadPage]
 *    (keyboard-free). NumpadPage clamps to [MACRO_NUMERIC_RANGE] BEFORE the value is collected — it is
 *    the single numeric-clamp owner (S4); [MacroInvocation] does NOT re-clamp.
 *  - a string param (`type` == `string`/null) → a [TokenTextField] on the system keyboard. This popup
 *    is the ONE sanctioned alpha-keyboard site in printer controls (D-10).
 *  - an unclassified param degrades to the string field and NEVER blocks Execute.
 *
 * Execute assembles the gcode via [MacroInvocation.buildTyped] (the load-bearing V5 sanitizer —
 * numeric values unquoted, string values quoted + REJECTED on any forbidden char, T-08-06-T1), then
 * dispatches via [CommandDispatcher] using the per-macro busy key `macro_<name>` (PRIM-05). The
 * raw keyboard text NEVER reaches [PrinterCommands.scriptParams] directly — the sanitizer is always
 * between. A rejection (forbidden char locally, or the printer's own `!!` reply) surfaces as a
 * [SeverityToast]; the dispatcher's [DispatchEvent.Failure] message is already redacted (composed from
 * method/key, never the API key — V7).
 *
 * @param macro      the macro to run (name + parsed params).
 * @param dispatcher the shared command dispatcher (in-flight/debounce/timeout/redacted failure).
 * @param onDismiss  called to close the popup (Cancel, or after a successful dispatch).
 */
@Composable
fun MacroExecutionPopup(
    macro: MacroVm,
    dispatcher: CommandDispatcher,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val busyKey = "macro_${macro.name}"
    val inFlight by dispatcher.inFlight.collectAsStateWithLifecycle()
    val running = busyKey in inFlight

    // Current value per param (seeded with the parsed default; the user edits over it).
    val values = remember(macro.name) {
        mutableStateMapOf(*macro.params.map { it.name to (it.default ?: "") }.toTypedArray())
    }

    // A locally-detected forbidden-char rejection, or the printer's own rejection text.
    var toast by remember(macro.name) { mutableStateOf<String?>(null) }

    // Which numeric param (if any) has its NumpadPage open as a sub-page.
    var numpadParam by remember(macro.name) { mutableStateOf<MacroParam?>(null) }

    // Surface dispatcher failures (printer `!!` rejection) for THIS macro's busy key as a toast.
    LaunchedEffect(dispatcher, macro.name) {
        dispatcher.events.collect { event ->
            if (event is DispatchEvent.Failure && event.key == busyKey) {
                toast = "${macro.name} was rejected: ${event.message}"
            }
        }
    }

    fun execute() {
        val gcode = try {
            MacroInvocation.buildTyped(
                macro.name,
                macro.params.map { p ->
                    Triple(p.name, values[p.name].orEmpty(), p.isNumeric)
                },
            )
        } catch (e: MacroParamRejected) {
            // The local V5 gate refused a forbidden character — surface and do NOT dispatch.
            toast = "${macro.name} was rejected: ${e.reason}"
            return
        }
        dispatcher.dispatch(
            key = busyKey,
            method = JsonRpcMethods.GCODE_SCRIPT,
            params = PrinterCommands.scriptParams(gcode),
        )
        onDismiss()
    }

    // NumpadPage sub-page takes over the whole surface while a numeric param is being entered.
    val editing = numpadParam
    if (editing != null) {
        NumpadPage(
            label = editing.name,
            initial = values[editing.name].orEmpty().toDoubleOrNull() ?: 0.0,
            range = MACRO_NUMERIC_RANGE,
            onCancel = { numpadParam = null },
            onSet = { committed ->
                // NumpadPage already clamped to MACRO_NUMERIC_RANGE (S4 — the sole clamp owner).
                values[editing.name] = formatNumeric(committed)
                numpadParam = null
            },
        )
        return
    }

    Box(modifier.fillMaxSize().background(t.bg)) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title — `Run {MACRO_NAME}` (GeistMono prominent).
            Text(
                text = "Run ${macro.name}",
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(28f, t.fs).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            // Param fields (scrollable so a macro with many params doesn't crowd the gutter).
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (macro.params.isEmpty()) {
                    Text(
                        text = "No parameters. Execute runs this macro as-is.",
                        color = t.text2,
                        fontFamily = Geist,
                        fontSize = fsSp(15f, t.fs).sp,
                    )
                }
                for (param in macro.params) {
                    if (param.isNumeric) {
                        NumericParamField(
                            param = param,
                            value = values[param.name].orEmpty(),
                            onClick = { numpadParam = param },
                        )
                    } else {
                        // String / unclassified → the ONE sanctioned alpha keyboard (D-10).
                        TokenTextField(
                            value = values[param.name].orEmpty(),
                            onValueChange = { values[param.name] = it },
                            label = param.name,
                            keyboardType = KeyboardType.Text,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            toast?.let { msg ->
                SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
            }
            if (running) {
                SeverityToast(
                    Severity.Info,
                    "Running ${macro.name}... waiting for the printer.",
                    Modifier.fillMaxWidth(),
                )
            }

            // Gutter: Execute (accent — ordinary physical command) · Cancel (go — safe dismiss).
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PopupControl(
                    label = "Cancel",
                    onClick = onDismiss,
                    intent = Intent.Go,
                    modifier = Modifier.weight(1f),
                )
                PopupControl(
                    label = "Execute",
                    onClick = { execute() },
                    intent = Intent.Accent,
                    enabled = !running,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** A numeric param row: the param name + its current value, tappable to open the NumpadPage. */
@Composable
private fun NumericParamField(
    param: MacroParam,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.outline), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MaterialSymbol("dialpad", tint = t.text2, sizeSp = fsSp(20f, t.fs))
        Text(
            text = param.name,
            color = t.text2,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(15f, t.fs).sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value.ifEmpty { "—" },
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
        )
    }
}

/** A 64dp+ outlined gutter control (FilesScreen FileActionControl template), intent-colored. */
@Composable
private fun PopupControl(
    label: String,
    onClick: () -> Unit,
    intent: Intent,
    modifier: Modifier = Modifier,
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
    val clickable = if (enabled) base.clickable(onClick = onClick) else base
    Box(clickable, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = if (enabled) t.text else t.text3,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
        )
    }
}

private fun intentColor(intent: Intent, t: ThemeTokens): Color = when (intent) {
    Intent.Neutral -> t.outline
    Intent.Accent -> t.accentLine
    Intent.Warn -> t.heat
    Intent.Danger -> t.stop
    Intent.Go -> t.go
}

/** Drop a trailing ".0" on a whole-number numeric value (matches NumpadPage display). */
private fun formatNumeric(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
