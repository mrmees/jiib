package works.mees.dinghy.ui.screen

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.FontScale
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.ui.settings.TextSizeSelector

/**
 * The **App Settings** screen (APP-SETTINGS-01) — app-global preferences that apply
 * across all printers: text size, display behaviour, battery handling, and babystep layers.
 *
 * Mirrors [SettingsScreen] structure with two differences:
 *  - No webcam row (webcam is per-printer → moves to the future Printer Settings screen).
 *  - Adds the app-global S/M/L [TextSizeSelector] at the top of the Field column.
 *
 * ## Layout
 * Field-only `ScreenScaffold`. Back is the last control inside the scrolling Column.
 * All toggles use the `ListRow` anatomy with a trailing `Switch` token-tinted via
 * `t.accent` for the checked state. The numeric babystep field uses `TokenTextField`
 * with a numeric keyboard. All persistence routes through the process-lifetime `container.set*`
 * writeScope intent helpers — never a composition scope (T-28-07-02).
 *
 * @param container the process-scoped service-locator.
 * @param onBack the explicit neutral Back exit.
 */
@Composable
fun AppSettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused

    // App-global font scale — process-scoped, durable writeScope intent (T-28-07-02).
    val fontScale by container.fontScale.collectAsStateWithLifecycle(FontScale.M)

    // Babystep app setting (D-06) — process-scoped, connection-INDEPENDENT (not per-profile).
    // All persistence routes through durable container intent helpers (writeScope), never a
    // composition scope ([[dinghy-compose-write-scope-cancellation]]).
    val babystepOn by container.babystepEnabled.collectAsStateWithLifecycle(true)
    val babystepLayers by container.babystepLayers.collectAsStateWithLifecycle(5)

    // Display / always-on (§R2, 26.5-05) — process-scoped, connection-INDEPENDENT.
    // Durable writeScope intent, never a composition scope (T-28-07-02).
    val keepScreenOn by container.keepScreenOn.collectAsStateWithLifecycle(true)

    // Battery-optimization exemption state — re-checked on ON_RESUME.
    val context = LocalContext.current
    val powerManager = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var isExempt by remember {
        mutableStateOf(powerManager.isIgnoringBatteryOptimizations(context.packageName))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, powerManager) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isExempt = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AppSettingsContent(
        fontScale = fontScale,
        onFontScale = { container.setFontScale(it) },
        babystepOn = babystepOn,
        onBabystepToggle = { container.setBabystepEnabled(it) },
        babystepLayers = babystepLayers,
        onBabystepLayers = { container.setBabystepLayers(it) },
        keepScreenOn = keepScreenOn,
        onKeepScreenOnToggle = { container.setKeepScreenOn(it) },
        isExempt = isExempt,
        onRequestExempt = {
            try {
                context.startActivity(
                    android.content.Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}"),
                    ),
                )
            } catch (_: Exception) {
                context.startActivity(
                    android.content.Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                )
            }
        },
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The STATELESS content seam (PREVIEW_AND_TOKENS preview-first LAW) — pure inputs, no
 * AppContainer/Moonraker, so the `@Preview` matrix in [works.mees.dinghy.preview.AppSettingsPreviews]
 * drives every theme + toggle state without a live session.
 */
@Composable
fun AppSettingsContent(
    fontScale: FontScale,
    onFontScale: (FontScale) -> Unit,
    babystepOn: Boolean,
    onBabystepToggle: (Boolean) -> Unit,
    babystepLayers: Int,
    onBabystepLayers: (Int) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnToggle: (Boolean) -> Unit,
    isExempt: Boolean,
    onRequestExempt: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
) {
    val t = LocalTokens.current
    // Local field mirrors the persisted layer-count. WR-03 (the Phase-19 value-not-sticking family):
    // the DataStore round-trip echo must NOT clobber the buffer mid-edit — typing "12" raced the
    // async flow emission for "1", which re-keyed the old remember(babystepLayers) and discarded the
    // "2". Re-seed ONLY while the field is unfocused; on blur the field resyncs to the persisted
    // (possibly coerced — setLayerCount floors at 1) value.
    var layersEditing by remember { mutableStateOf(false) }
    var layersField by remember { mutableStateOf(babystepLayers.toString()) }
    LaunchedEffect(babystepLayers, layersEditing) {
        if (!layersEditing) layersField = babystepLayers.toString()
    }

    // BoxWithConstraints for grid — needed by ListRow (uDp for 1U height floor) and OutlinedControl.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            fieldFramed = false,
            focus = {
                FocusFrame(
                    title = stringResource(R.string.system_row_app_settings),
                    icon = DinghyIcons.AppSettings,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxSize(),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {}
            },
            field = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ── Text size selector ────────────────────────────────────────────────────
                    // App-global S/M/L font scale — durable writeScope intent (T-28-07-02).
                    ListRow(
                        selected = false,
                        onClick = {},
                        uDp = grid.uDp,
                        leadingContent = {
                            ListRowIcon(
                                icon = DinghyIcons.TextSize,
                                uDp = grid.uDp,
                                tint = t.text,
                            )
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.settings_text_size),
                            color = t.text,
                            style = DinghyType.listLabel.toTextStyle(t),
                        )
                        Spacer(Modifier.weight(1f))
                    }
                    TextSizeSelector(
                        selected = fontScale,
                        onSelect = onFontScale,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    // ── Keep screen on ────────────────────────────────────────────────────────
                    // App-global — durable writeScope intent (T-28-07-02).
                    AppSettingsDenseToggleRow(
                        label = stringResource(R.string.settings_keep_screen_on),
                        checked = keepScreenOn,
                        enabled = true,
                        onToggle = onKeepScreenOnToggle,
                        uDp = grid.uDp,
                    )

                    // ── Battery optimization exemption ────────────────────────────────────────
                    // Status + tap-to-request deep-link. Once exempt the row reads greyed (system
                    // offers no in-app un-exempt dialog). android.content.Intent is fully-qualified
                    // because this file imports designsystem.control.Intent under the same name.
                    ListRow(
                        selected = false,
                        onClick = { if (!isExempt) onRequestExempt() },
                        uDp = grid.uDp,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_battery_optimization),
                            color = if (isExempt) t.text3 else t.text,
                            style = DinghyType.listLabel.toTextStyle(t),
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (isExempt) {
                                stringResource(R.string.settings_battery_exempt)
                            } else {
                                stringResource(R.string.settings_battery_optimized)
                            },
                            color = if (isExempt) t.go else t.text2,
                            style = DinghyType.caption.toTextStyle(t),
                        )
                    }

                    // ── Babystep enable ───────────────────────────────────────────────────────
                    // Process-scoped (D-06) — durable writeScope intent (T-28-07-02).
                    AppSettingsDenseToggleRow(
                        label = stringResource(R.string.settings_babystep),
                        checked = babystepOn,
                        enabled = true,
                        onToggle = onBabystepToggle,
                        uDp = grid.uDp,
                    )

                    // ── Babystep layer-count numeric field ────────────────────────────────────
                    // Commit-on-edit: valid positive int writes through the durable intent helper.
                    // T-28-07-01: invalid/blank buffer silently not persisted (no crash, no bad write).
                    ListRow(
                        selected = false,
                        onClick = {},
                        uDp = grid.uDp,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_babystep_layers),
                            color = if (babystepOn) t.text else t.text3,
                            style = DinghyType.listLabel.toTextStyle(t),
                        )
                        Spacer(Modifier.weight(1f))
                        TokenTextField(
                            value = layersField,
                            onValueChange = { raw ->
                                // Digits only — numeric keyboard guards, this guards paste.
                                val digits = raw.filter { it.isDigit() }
                                layersField = digits
                                // T-28-07-01: invalid input silently not persisted.
                                digits.toIntOrNull()?.let { onBabystepLayers(it) }
                            },
                            label = stringResource(R.string.settings_babystep_layers_hint),
                            keyboardType = KeyboardType.Number,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 8.dp)
                                // WR-03: while focused the persisted-value echo is held off the
                                // buffer; on blur the LaunchedEffect above resyncs the field.
                                .onFocusChanged { layersEditing = it.isFocused },
                        )
                    }

                    // ── Back foot ─────────────────────────────────────────────────────────────
                    // R5/R8 (supersedes D-10): Back = accent. Inside the Column so it
                    // is always visible at the foot of the single-page scroll.
                    OutlinedControl(
                        label = stringResource(R.string.common_back),
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp), // gapM
                        intent = Intent.Accent,
                    )
                }
            },
        )
    }
}

/**
 * A dense toggle row (C6 exempt, D-11) — `label + Spacer + Switch`. The Switch is token-tinted
 * via `t.accent` for the checked state; `t.accentSoft` for the track. When [enabled] is false
 * the row reads greyed and is inert.
 *
 * Copied from [SettingsScreen]'s private `DenseToggleRow` — AppSettingsScreen is self-contained
 * so it survives the future deletion of SettingsScreen.kt. All persistence is the caller's
 * responsibility — this composable only calls [onToggle].
 */
@Composable
private fun AppSettingsDenseToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    uDp: Dp,
) {
    val t = LocalTokens.current
    ListRow(
        selected = false,
        onClick = { if (enabled) onToggle(!checked) },
        uDp = uDp,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = { if (enabled) onToggle(it) },
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = t.accent,
                    checkedTrackColor = t.accentSoft,
                    checkedBorderColor = t.accentLine,
                ),
            )
        },
    ) {
        Text(
            text = label,
            color = if (enabled) t.text else t.text3,
            style = DinghyType.listLabel.toTextStyle(t),
        )
        Spacer(Modifier.weight(1f))
    }
}
