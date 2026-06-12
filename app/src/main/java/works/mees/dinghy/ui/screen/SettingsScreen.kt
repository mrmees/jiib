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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The **Settings** drawer destination (SET-01) — dense C6-exempt restyle (28-07, D-11/D-12).
 * A single `verticalScroll` Column of dense toggle + numeric-field rows; fits one page at M text
 * size in portrait and landscape. No section-header words — grouping by outline/fill only.
 *
 * ## Layout (D-11/D-12)
 * Field-only `ScreenScaffold(gutter = null)`. Back is the last control inside the scrolling Column.
 * All toggles use the `ListRow` anatomy with a trailing `Switch` token-tinted via
 * `t.accent` for the checked state. The numeric babystep field uses `TokenTextField`
 * with a numeric keyboard. All persistence routes through the process-lifetime `container.set*`
 * writeScope intent helpers — never a composition scope (T-28-07-02).
 *
 * ## Feature toggles (D-14 — boundary is final)
 * Every existing feature toggle survives restyled in-place. Nothing moves to another screen.
 *
 * @param container the process-scoped service-locator.
 * @param onBack the explicit neutral Back exit.
 */
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The active profile drives the per-profile toggle state. Null when no printer is configured —
    // the toggles fall back to the default so the screen still composes.
    val activeProfile by container.activeProfile.collectAsStateWithLifecycle(null)
    val webcamOn = activeProfile?.webcamEnabled ?: true

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

    SettingsContent(
        webcamOn = webcamOn,
        webcamEnabled = activeProfile != null,
        onWebcamToggle = { container.setActiveWebcamEnabled(it) },
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
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The STATELESS content seam (PREVIEW_AND_TOKENS preview-first LAW) — pure inputs, no
 * AppContainer/Moonraker, so the `@Preview` matrix in [works.mees.dinghy.preview.SettingsPreviews]
 * drives every theme + toggle state without a live session.
 */
@Composable
fun SettingsContent(
    webcamOn: Boolean,
    webcamEnabled: Boolean,
    onWebcamToggle: (Boolean) -> Unit,
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
            field = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // ── Webcam toggle ─────────────────────────────────────────────────────────
                    // Per-profile (MEDIUM-4, D-04): flipping this greys/lights the Webcam drawer tile.
                    DenseToggleRow(
                        label = stringResource(R.string.settings_webcam),
                        checked = webcamOn,
                        enabled = webcamEnabled,
                        onToggle = onWebcamToggle,
                        uDp = grid.uDp,
                    )

                    // ── Babystep enable ───────────────────────────────────────────────────────
                    // Process-scoped (D-06) — durable writeScope intent (T-28-07-02).
                    DenseToggleRow(
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
                            fontFamily = Geist,
                            fontSize = fsSp(17f, t.fs).sp,
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

                    // ── Keep screen on ────────────────────────────────────────────────────────
                    // App-global — durable writeScope intent (T-28-07-02).
                    DenseToggleRow(
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
                            fontFamily = Geist,
                            fontSize = fsSp(17f, t.fs).sp,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (isExempt) {
                                stringResource(R.string.settings_battery_exempt)
                            } else {
                                stringResource(R.string.settings_battery_optimized)
                            },
                            color = if (isExempt) t.go else t.text2,
                            fontFamily = Geist,
                            fontSize = fsSp(15f, t.fs).sp,
                        )
                    }

                    // ── Back foot ─────────────────────────────────────────────────────────────
                    // Neutral — plain nav spends no safety color (D-10). Inside the Column so it
                    // is always visible at the foot of the single-page scroll.
                    OutlinedControl(
                        label = stringResource(R.string.common_back),
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        intent = Intent.Neutral,
                    )
                }
            },
            gutter = null,
        )
    }
}

/**
 * A dense toggle row (C6 exempt, D-11) — `label + Spacer + Switch`. The Switch is token-tinted
 * via `t.accent` for the checked state; `t.accentSoft` for the track. When [enabled] is false
 * the row reads greyed and is inert.
 *
 * All persistence is the caller's responsibility — this composable only calls [onToggle].
 */
@Composable
private fun DenseToggleRow(
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
            fontFamily = Geist,
            fontSize = fsSp(17f, t.fs).sp,
        )
        Spacer(Modifier.weight(1f))
    }
}
