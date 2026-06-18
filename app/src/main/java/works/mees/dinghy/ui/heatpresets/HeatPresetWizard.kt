package works.mees.dinghy.ui.heatpresets

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.dinghy.R
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.config.HeatPreset
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.state.SettableHeater
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.ui.screen.TokenTextField

/** One heater step in the wizard: identity + label + clamp bounds + the prefilled text value. */
data class WizardHeaterField(
    val objectName: String,
    val displayName: String,
    val minTemp: Int,
    val maxTemp: Int,
    val initialValue: String,
)

/**
 * Build the ordered heater fields for the wizard against the CURRENT live [heaters]. On edit, prefill
 * from [saved]'s setpoints (blank for heaters the saved preset omits OR that are new since it was made);
 * heaters no longer in config simply don't appear → the preset is rebuilt fresh against current config.
 */
fun reconcileWizardFields(saved: HeatPreset?, heaters: List<SettableHeater>): List<WizardHeaterField> =
    heaters.map { h ->
        WizardHeaterField(
            objectName = h.objectName,
            displayName = h.displayName,
            minTemp = h.minTemp ?: PrinterCommands.MIN_TEMP_C,
            maxTemp = h.maxTemp ?: PrinterCommands.MAX_TEMP_C,
            initialValue = saved?.setpoints?.get(h.objectName)?.toString() ?: "",
        )
    }

/**
 * Build a [HeatPreset] from wizard input. [rawValues] maps object name → the user's text. A blank/
 * unparseable value OMITS that heater (skip); any parsed integer (including 0 = off) is KEPT, clamped
 * to that heater's [minTemp]..[maxTemp]. [name] is trimmed.
 *
 * Special case: a parsed value of `0` bypasses the minimum clamp — it is an explicit "turn off"
 * command and must not be clamped up to [SettableHeater.minTemp].
 *
 * Overflow-safe: values are parsed with [String.toLongOrNull] so a very long digit string does NOT
 * overflow Int → null → silently omit; positives clamp on Long bounds then narrow to Int.
 *
 * Edit-safe ([saved]): on edit the wizard rebuilds against the CURRENT live [heaters], but a heater
 * may be TEMPORARILY ABSENT (printer disconnected → enumerate falls back to extruder+bed). Setpoints
 * in [saved] for object names NOT in the current [heaters] are PRESERVED (e.g. a chamber/fan setpoint
 * is not lost just because that heater isn't reporting right now). The parsed values OVERLAY preserved.
 */
fun buildPresetFromInput(
    id: String,
    name: String,
    rawValues: Map<String, String>,
    heaters: List<SettableHeater>,
    saved: HeatPreset? = null,
): HeatPreset {
    val byName = heaters.associateBy { it.objectName }
    val parsedSetpoints = buildMap {
        for ((obj, raw) in rawValues) {
            val parsed = raw.trim().toLongOrNull() ?: continue  // blank/garbage/overflow → skip (omit heater)
            if (parsed == 0L) { put(obj, 0); continue }         // 0 = explicit OFF — bypass the min clamp
            val h = byName[obj]
            val lo = h?.minTemp ?: PrinterCommands.MIN_TEMP_C
            val hi = h?.maxTemp ?: PrinterCommands.MAX_TEMP_C
            put(obj, parsed.coerceIn(lo.toLong(), hi.toLong()).toInt())
        }
    }
    val heaterNames = heaters.map { it.objectName }.toSet()
    val preserved = saved?.setpoints?.filterKeys { it !in heaterNames } ?: emptyMap()
    return HeatPreset(id = id, name = name.trim(), setpoints = preserved + parsedSetpoints)
}

// ===========================================================================
// Compose wizard UI (Task 10). The pure functions above are unit-tested and
// MUST NOT change — this section is the Focus/Field create-edit wizard only.
// ===========================================================================

/**
 * The create/edit Heat Preset wizard — a one-step-per-page Focus chain.
 *
 * Step 0 = the preset NAME (alphanumeric field, a sanctioned keyboard carve-out — naming a saved
 * thing). Steps 1..fields.size = one settable heater each (numeric field; blank = omit, 0 = off).
 * Foot bar carries Cancel (danger, cancel-with-loss) + a single primary that is Next while more
 * heater steps remain, then Save on the last step. Save is gated on a non-blank name.
 *
 * @param saved   the preset being edited, or null when creating a new one.
 * @param heaters the CURRENT live settable heaters (from [works.mees.dinghy.state.enumerateSettableHeaters]).
 * @param isPrinting drives the FocusFrame header e-stop morph.
 * @param onEmergencyStop e-stop dispatch (wired from the screen).
 * @param onCancel exit the wizard discarding input.
 * @param onSave  commit the built [HeatPreset].
 * @param uDp     one unit U from the host screen's unit grid.
 */
@Composable
fun HeatPresetWizard(
    saved: HeatPreset?,
    heaters: List<SettableHeater>,
    isPrinting: Boolean,
    onEmergencyStop: () -> Unit,
    onCancel: () -> Unit,
    onSave: (HeatPreset) -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val fields = remember(saved, heaters) { reconcileWizardFields(saved, heaters) }

    // Step 0 = name; steps 1..fields.size = heater fields.
    var step by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf(saved?.name ?: "") }
    // Per-object text values. rememberSaveable so in-progress entries survive rotation (FIX 7); the
    // listSaver flattens to an alternating key,value List<String> and restores into a stateMap. Seeded
    // from the reconciled fields only on FIRST composition — the saver restores it after rotation.
    val values = rememberSaveable(
        saver = listSaver(
            save = { map -> map.flatMap { (k, v) -> listOf(k, v) } },
            restore = { flat ->
                mutableStateMapOf<String, String>().apply {
                    flat.chunked(2).forEach { pair -> if (pair.size == 2) put(pair[0], pair[1]) }
                }
            },
        ),
    ) {
        mutableStateMapOf<String, String>().apply {
            fields.forEach { put(it.objectName, it.initialValue) }
        }
    }

    // `fields` can SHRINK (config change / disconnect / profile switch) while `step` is restored from a
    // larger range, so clamp before indexing (FIX 1). safeStep == fields.size is the Save step.
    val safeStep = step.coerceIn(0, fields.size)
    val onLastStep = safeStep >= fields.size

    ScreenScaffold(
        modifier = modifier,
        focus = {
            FocusFrame(
                title = stringResource(R.string.heat_presets_wizard_title),
                icon = DinghyIcons.TempPresets,
                uDp = uDp,
                modifier = Modifier.fillMaxWidth().weight(1f),
                isPrinting = isPrinting,
                onEmergencyStop = onEmergencyStop,
                onPanic = onEmergencyStop,
            ) {
                val field = fields.getOrNull(safeStep - 1)
                if (safeStep == 0 || field == null) {
                    Text(
                        text = stringResource(R.string.heat_presets_name_label),
                        color = t.text,
                        style = DinghyType.listLabel.toTextStyle(t),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    TokenTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.heat_presets_name_hint),
                        keyboardType = KeyboardType.Text,
                        isError = name.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = field.displayName,
                        color = t.text,
                        style = DinghyType.focusHeader.toTextStyle(t),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    TokenTextField(
                        value = values[field.objectName] ?: "",
                        // Cap to 3 digits — max heater temp is 350 (FIX 4).
                        onValueChange = { raw -> values[field.objectName] = raw.filter(Char::isDigit).take(3) },
                        label = stringResource(R.string.heat_presets_value_hint),
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(R.string.heat_presets_skip_hint),
                        color = t.text2,
                        style = DinghyType.caption.toTextStyle(t),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        field = {
            val nameValid = name.isNotBlank()
            // A save needs a non-blank name AND at least one parseable setpoint (FIX 5) — a name-only
            // preset would dispatch an empty gcode script. Next stays enabled on name validity alone.
            val hasAnySetpoint = values.values.any { it.trim().toLongOrNull() != null }
            val primaryEnabled = if (onLastStep) nameValid && hasAnySetpoint else nameValid
            FootButtonBar(
                uDp = uDp,
                actions = listOf(
                    FootAction(
                        label = stringResource(R.string.common_cancel),
                        icon = DinghyIcons.DialogClose,
                        onClick = onCancel,
                        intent = Intent.Danger,
                    ),
                    FootAction(
                        label = if (onLastStep) {
                            stringResource(R.string.common_save)
                        } else {
                            stringResource(R.string.common_next)
                        },
                        icon = DinghyIcons.CheckCircle,
                        onClick = {
                            if (!onLastStep) {
                                step = safeStep + 1
                            } else {
                                onSave(
                                    buildPresetFromInput(
                                        id = saved?.id ?: java.util.UUID.randomUUID().toString(),
                                        name = name,
                                        rawValues = values.toMap(),
                                        heaters = heaters,
                                        saved = saved,
                                    ),
                                )
                            }
                        },
                        intent = Intent.Go,
                        enabled = primaryEnabled,
                    ),
                ),
            )
        },
    )
}
