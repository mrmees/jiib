package works.mees.dinghy.ui.screen

import android.content.Context
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.components.StepperRow
import works.mees.dinghy.designsystem.components.ToggleRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
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

/** Which App Settings row is selected; null = no selection (Focus shows the overview placeholder). */
enum class AppSetting { TextSize, KeepAwake, Webcam, Babystep, Battery }

/**
 * The **App Settings** screen (APP-SETTINGS-01) — app-global preferences that apply
 * across all printers: text size, display behaviour, webcam, battery handling, and babystep layers.
 *
 * ## Layout
 * Pure-selector Focus/Field (R5 lists-first law): the Field is a `ListBlock` of selector rows
 * (icon + label + READ-ONLY text indicator) + a `FootButtonBar` Back, inside `ScreenScaffold`'s
 * DEFAULT-framed slots. Tapping a row selects it; the `FocusFrame` swaps its body by the selected
 * [AppSetting], showing an explanation + the control. No control lives in a list row.
 * All persistence routes through the process-lifetime `container.set*` writeScope intent
 * helpers — never a composition scope (T-28-07-02).
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

    // App-global webcam toggle (moved from per-printer, 2026-06-15) — process-scoped, durable.
    val webcamEnabled by container.webcamEnabled.collectAsStateWithLifecycle(true)

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
        keepScreenOn = keepScreenOn,
        onKeepScreenOnToggle = { container.setKeepScreenOn(it) },
        webcamEnabled = webcamEnabled,
        onWebcamToggle = { container.setWebcamEnabled(it) },
        babystepOn = babystepOn,
        onBabystepToggle = { container.setBabystepEnabled(it) },
        babystepLayers = babystepLayers,
        onBabystepLayers = { container.setBabystepLayers(it) },
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
 * drives every theme + selection state without a live session.
 */
@Composable
fun AppSettingsContent(
    fontScale: FontScale,
    onFontScale: (FontScale) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnToggle: (Boolean) -> Unit,
    webcamEnabled: Boolean,
    onWebcamToggle: (Boolean) -> Unit,
    babystepOn: Boolean,
    onBabystepToggle: (Boolean) -> Unit,
    babystepLayers: Int,
    onBabystepLayers: (Int) -> Unit,
    isExempt: Boolean,
    onRequestExempt: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
    initialSelected: AppSetting? = null,
) {
    val t = LocalTokens.current
    var selected by rememberSaveable { mutableStateOf(initialSelected) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            focus = {
                AppSettingsFocus(
                    selected = selected,
                    fontScale = fontScale,
                    onFontScale = onFontScale,
                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnToggle = onKeepScreenOnToggle,
                    webcamEnabled = webcamEnabled,
                    onWebcamToggle = onWebcamToggle,
                    babystepOn = babystepOn,
                    onBabystepToggle = onBabystepToggle,
                    babystepLayers = babystepLayers,
                    onBabystepLayers = onBabystepLayers,
                    isExempt = isExempt,
                    onRequestExempt = onRequestExempt,
                    uDp = grid.uDp,
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    modifier = Modifier.fillMaxSize(),
                )
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    item {
                        AppSettingRow(
                            selected = selected == AppSetting.TextSize,
                            onClick = { selected = if (selected == AppSetting.TextSize) null else AppSetting.TextSize },
                            icon = DinghyIcons.TextSize,
                            label = stringResource(R.string.settings_text_size),
                            indicator = stringResource(textSizeIndicatorRes(fontScale)),
                            uDp = grid.uDp,
                        )
                    }
                    item {
                        AppSettingRow(
                            selected = selected == AppSetting.KeepAwake,
                            onClick = { selected = if (selected == AppSetting.KeepAwake) null else AppSetting.KeepAwake },
                            icon = DinghyIcons.Fluorescent,
                            label = stringResource(R.string.settings_keep_screen_on),
                            indicator = stringResource(onOffRes(keepScreenOn)),
                            uDp = grid.uDp,
                        )
                    }
                    item {
                        AppSettingRow(
                            selected = selected == AppSetting.Webcam,
                            onClick = { selected = if (selected == AppSetting.Webcam) null else AppSetting.Webcam },
                            icon = DinghyIcons.LauncherWebcam,
                            label = stringResource(R.string.settings_webcam),
                            indicator = stringResource(onOffRes(webcamEnabled)),
                            uDp = grid.uDp,
                        )
                    }
                    item {
                        AppSettingRow(
                            selected = selected == AppSetting.Babystep,
                            onClick = { selected = if (selected == AppSetting.Babystep) null else AppSetting.Babystep },
                            icon = DinghyIcons.LineWeight,
                            label = stringResource(R.string.settings_babystep),
                            indicator = if (babystepOn) {
                                stringResource(R.string.settings_babystep_layers_count, babystepLayers)
                            } else {
                                stringResource(R.string.common_off)
                            },
                            uDp = grid.uDp,
                        )
                    }
                    item {
                        AppSettingRow(
                            selected = selected == AppSetting.Battery,
                            onClick = { selected = if (selected == AppSetting.Battery) null else AppSetting.Battery },
                            icon = DinghyIcons.ShieldLock,
                            label = stringResource(R.string.settings_battery_optimization),
                            indicator = stringResource(
                                if (isExempt) R.string.settings_battery_exempt_short
                                else R.string.settings_battery_optimized_short,
                            ),
                            indicatorColor = if (isExempt) t.go else t.text2,
                            uDp = grid.uDp,
                        )
                    }
                }
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            icon = DinghyIcons.Back,
                            onClick = onBack,
                            intent = Intent.Accent,
                            contentDescription = stringResource(R.string.cd_back),
                        ),
                    ),
                )
            },
        )
    }
}

/** S/M/L row indicator string for the current [fontScale]. */
private fun textSizeIndicatorRes(fontScale: FontScale): Int = when (fontScale) {
    FontScale.S -> R.string.settings_text_size_s
    FontScale.M -> R.string.settings_text_size_m
    FontScale.L -> R.string.settings_text_size_l
}

private fun onOffRes(on: Boolean): Int = if (on) R.string.common_on else R.string.common_off

/**
 * A pure-selector App Settings row: leading icon + label + a right-aligned READ-ONLY text indicator.
 * No control lives in the row (R5 lists-first law) — tapping selects the row to load its Focus detail.
 */
@Composable
private fun AppSettingRow(
    selected: Boolean,
    onClick: () -> Unit,
    icon: DinghyIcon,
    label: String,
    indicator: String,
    uDp: Dp,
    indicatorColor: Color? = null,
) {
    val t = LocalTokens.current
    ListRow(
        selected = selected,
        onClick = onClick,
        uDp = uDp,
        leadingContent = { ListRowIcon(icon = icon, uDp = uDp, tint = t.text) },
        trailingContent = {
            Text(
                text = indicator,
                color = indicatorColor ?: t.text2,
                style = DinghyType.caption.toTextStyle(t),
            )
        },
    ) {
        // Canonical 1U-safe list label (maxLines=1 + ellipsis) — ListRow already weights this slot.
        ListRowLabel(label)
    }
}

/**
 * The FocusFrame that swaps its body by the selected [AppSetting]. Each branch passes the e-stop
 * wiring so the docked-e-stop morph works on every state.
 */
@Composable
private fun AppSettingsFocus(
    selected: AppSetting?,
    fontScale: FontScale,
    onFontScale: (FontScale) -> Unit,
    keepScreenOn: Boolean,
    onKeepScreenOnToggle: (Boolean) -> Unit,
    webcamEnabled: Boolean,
    onWebcamToggle: (Boolean) -> Unit,
    babystepOn: Boolean,
    onBabystepToggle: (Boolean) -> Unit,
    babystepLayers: Int,
    onBabystepLayers: (Int) -> Unit,
    isExempt: Boolean,
    onRequestExempt: () -> Unit,
    uDp: Dp,
    isPrinting: Boolean,
    onEmergencyStop: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // Local helper: applies the Focus-body layout law (owner 2026-06-14 / LAYOUT.md §"Focus with a
    // docked action region") — the [description] is vertically CENTERED in the weighted body and the
    // [control] group is pinned to the BOTTOM (never top-stacked). Also shares the FocusFrame
    // boilerplate (+ e-stop wiring) across the branches. A null [control] (the no-selection overview)
    // just centers the description in the whole body.
    @Composable
    fun frame(
        title: String,
        icon: DinghyIcon,
        description: String,
        control: (@Composable ColumnScope.() -> Unit)? = null,
    ) {
        FocusFrame(
            title = title,
            icon = icon,
            uDp = uDp,
            modifier = modifier,
            isPrinting = isPrinting,
            onEmergencyStop = onEmergencyStop,
            onPanic = onEmergencyStop,
        ) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(text = description, color = t.text2, style = DinghyType.body.toTextStyle(t))
            }
            if (control != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = control,
                )
            }
        }
    }

    when (selected) {
        null -> frame(
            stringResource(R.string.system_row_app_settings),
            DinghyIcons.AppSettings,
            stringResource(R.string.settings_app_settings_placeholder),
        )
        AppSetting.TextSize -> frame(
            stringResource(R.string.settings_text_size),
            DinghyIcons.TextSize,
            stringResource(R.string.settings_text_size_focus),
        ) {
            TextSizeSelector(selected = fontScale, onSelect = onFontScale, modifier = Modifier.fillMaxWidth())
        }
        AppSetting.KeepAwake -> frame(
            stringResource(R.string.settings_keep_screen_on),
            DinghyIcons.Fluorescent,
            stringResource(R.string.settings_keep_screen_on_focus),
        ) {
            ToggleRow(
                label = stringResource(R.string.settings_keep_screen_on),
                checked = keepScreenOn,
                onToggle = onKeepScreenOnToggle,
                uDp = uDp,
            )
        }
        AppSetting.Webcam -> frame(
            stringResource(R.string.settings_webcam),
            DinghyIcons.LauncherWebcam,
            stringResource(R.string.settings_webcam_focus),
        ) {
            ToggleRow(
                label = stringResource(R.string.settings_webcam),
                checked = webcamEnabled,
                onToggle = onWebcamToggle,
                uDp = uDp,
            )
        }
        AppSetting.Babystep -> frame(
            stringResource(R.string.settings_babystep),
            DinghyIcons.LineWeight,
            stringResource(R.string.settings_babystep_focus),
        ) {
            ToggleRow(
                label = stringResource(R.string.settings_babystep),
                checked = babystepOn,
                onToggle = onBabystepToggle,
                uDp = uDp,
            )
            StepperRow(
                onDecrement = { onBabystepLayers((babystepLayers - 1).coerceAtLeast(1)) },
                onIncrement = { onBabystepLayers(babystepLayers + 1) },
                uDp = uDp,
                enabled = babystepOn,
                center = {
                    Text(
                        text = stringResource(R.string.settings_babystep_layers_count, babystepLayers),
                        color = t.text,
                        style = DinghyType.dataInline.toTextStyle(t),
                    )
                },
            )
        }
        AppSetting.Battery -> frame(
            stringResource(R.string.settings_battery_optimization),
            DinghyIcons.ShieldLock,
            stringResource(
                if (isExempt) R.string.settings_battery_exempt else R.string.settings_battery_optimized,
            ),
        ) {
            OutlinedControl(
                label = stringResource(R.string.settings_battery_request),
                onClick = onRequestExempt,
                enabled = !isExempt,
                modifier = Modifier.fillMaxWidth(),
                intent = Intent.Accent,
            )
        }
    }
}
