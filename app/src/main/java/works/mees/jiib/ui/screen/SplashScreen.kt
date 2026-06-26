package works.mees.jiib.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import works.mees.jiib.R
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.di.AppContainer
import works.mees.jiib.net.ConnectionError
import works.mees.jiib.state.ConnectionState
import works.mees.jiib.state.KlippyState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.DinghyType
import works.mees.jiib.theme.brandTint
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * The splash / initializing surface (SHELL-05) — a **hard override** (D-06). `TopRoute.derive()`
 * routes here whenever Klippy is not Ready (or there is no config), and it is the surface the E-stop
 * (04-06) lands on when it drives `klippy_state → shutdown`. Because it has **no reachable App
 * Drawer**, EVERY escape hatch must live on THIS screen or the user is trapped — so the recovery
 * actions are self-contained for each trapped state (D-11/D-12/D-13):
 *
 *  - **First run / no config** (D-11): a single "Set up your printer" → [onEditConnection] (Settings).
 *  - **Klippy Shutdown/Error, Moonraker reachable** (D-12): Retry + Restart firmware + Restart Klipper.
 *  - **Saved connection failing / unreachable** (D-13): Retry + Edit connection (no firmware/restart —
 *    Klippy is not reachable).
 *
 * Per docs/ui_design/LAYOUT.md the splash **omits the gutter**: its Field buttons
 * ARE the navigation, and there is deliberately no drawer affordance (the hard override).
 *
 * Reason text (review #8): prefer the REAL [PrinterState.klippyStateMessage] (the Moonraker/Klippy
 * human reason) when present, falling back to a terse enum-derived label — never blank. The message
 * is server-provided and rendered as plain text only, never interpreted (T-04-05-I).
 *
 * All recovery dispatches go through the narrow [works.mees.jiib.di.SessionControl] contract
 * (review #1) — the splash NEVER touches a raw session object. SessionControl's restart methods
 * route through the CommandDispatcher (busy/debounce/timeout, 04-03) and surface failures via toast.
 *
 * Static styling only (D-13): no looping/breathing animation — the Adreno-320 floor cannot spare it.
 *
 * @param container       the service-locator (provides [AppContainer.sessionControl]).
 * @param hasConfig       whether a usable persisted connection exists (drives the first-run branch).
 * @param state           the live printer state (klippyState + the real reason message).
 * @param onEditConnection opens the Settings screen (the splash does not navigate itself).
 */
@Composable
fun SplashScreen(
    container: AppContainer,
    hasConfig: Boolean,
    state: PrinterState,
    onEditConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val sessionControl = container.sessionControl
    val mode = recoveryMode(hasConfig, state)

    Box(modifier.fillMaxSize().background(t.bg)) {
        // Gutter omitted (LAYOUT.md): the Field's recovery buttons ARE the navigation (hard override).
        ScreenScaffold(
            fieldFramed = false,
            field = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SplashBrandLockup()
                    Text(
                        text = reasonText(hasConfig, state),
                        color = t.text2,
                        // GeistMono: the reason often carries a verbatim Klippy/MCU message (tabular).
                        style = DinghyType.dataInline.toTextStyle(t),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        when (mode) {
                            RecoveryMode.FirstRun -> {
                                // D-11: the only escape is into Settings to set up the printer.
                                OutlinedControl(
                                    label = stringResource(R.string.splash_setup_printer),
                                    onClick = onEditConnection,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Go, // R5: the first-run expected action
                                )
                            }

                            RecoveryMode.KlippyDown -> {
                                // D-12: Moonraker reachable but Klipper is shut down / errored — offer
                                // the firmware/host recovery commands (routed via SessionControl).
                                OutlinedControl(
                                    label = stringResource(R.string.splash_retry),
                                    onClick = sessionControl::requestReconnectNow,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Go, // R5: the recovery surface's expected action
                                )
                                OutlinedControl(
                                    label = stringResource(R.string.splash_restart_firmware),
                                    onClick = sessionControl::restartFirmware,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Warn,
                                )
                                OutlinedControl(
                                    label = stringResource(R.string.splash_restart_klipper),
                                    onClick = sessionControl::restartHost,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Warn,
                                )
                            }

                            RecoveryMode.Unreachable -> {
                                // D-13: a saved connection that won't connect — Klippy isn't reachable,
                                // so NO firmware/restart here (they'd never land); offer Retry + Edit.
                                OutlinedControl(
                                    label = stringResource(R.string.splash_retry),
                                    onClick = sessionControl::requestReconnectNow,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Go, // R5: the recovery surface's expected action
                                )
                                OutlinedControl(
                                    label = stringResource(R.string.splash_edit_connection),
                                    onClick = onEditConnection,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Accent, // R5: plain navigation = accent
                                )
                            }

                            RecoveryMode.Connecting -> {
                                // Plain initializing surface (no trap yet): no recovery row, just status.
                            }
                        }
                    }
                }
            },
        )
    }
}

/**
 * The jiib stacked icon+wordmark lockup that fronts the splash (D-04), tinted from the theme accent
 * with the D-06 contrast-floor guard ([brandTint] falls back to `t.text` when the accent would wash
 * out against `t.bg` on a user-custom theme).
 *
 * STATELESS and AppContainer-FREE on purpose — it reads only [LocalTokens] — so it is the preview
 * seam [works.mees.jiib.preview.BrandPreviews] drives across the six theme combos (PREVIEW_AND_TOKENS /
 * D-03), which is how the D-06 contrast risk gets eyeballed before device.
 *
 * Sized by RATIO (LAYOUT.md ratio-only rule), not a magic px: the lockup fills 60% of the available
 * width and wraps its (square-ish 600×600) height so it never stretches; the enclosing Column already
 * centers it horizontally.
 */
@Composable
internal fun SplashBrandLockup() {
    val t = LocalTokens.current
    Icon(
        painter = painterResource(R.drawable.jiib_lockup),
        contentDescription = stringResource(R.string.cd_jiib_logo),
        tint = brandTint(t.accent, t.bg, t.text),
        modifier = Modifier
            .fillMaxWidth(0.6f)
            .wrapContentHeight(),
    )
}

/** The self-contained recovery affordance sets the splash offers, keyed off the same state inputs. */
private enum class RecoveryMode { FirstRun, KlippyDown, Unreachable, Connecting }

/**
 * Choose the recovery action set from the trapped-state inputs (D-11/D-12/D-13):
 *  - no config → [FirstRun];
 *  - Klippy Shutdown/Error AND the socket is up (Moonraker reachable) → [KlippyDown] (firmware/host);
 *  - the socket is not Connected (Disconnected/Error/Connecting that has stalled) → [Unreachable];
 *  - otherwise (still connecting / syncing, Klippy starting) → [Connecting] (no recovery row yet).
 */
private fun recoveryMode(hasConfig: Boolean, state: PrinterState): RecoveryMode {
    if (!hasConfig) return RecoveryMode.FirstRun
    val socketUp = state.connection is ConnectionState.Connected ||
        state.connection is ConnectionState.Syncing
    val klippyDown = state.klippyState == KlippyState.Shutdown || state.klippyState == KlippyState.Error
    return when {
        klippyDown && socketUp -> RecoveryMode.KlippyDown
        state.connection is ConnectionState.Disconnected ||
            state.connection is ConnectionState.Error ||
            state.klippyState == KlippyState.Disconnected -> RecoveryMode.Unreachable
        else -> RecoveryMode.Connecting
    }
}

/**
 * Readable reason for the surface. PREFERS the real [PrinterState.klippyStateMessage] (review #8 —
 * the actual Klippy/Moonraker reason) when non-null; otherwise a terse enum-derived fallback that is
 * NEVER blank.
 */
@Composable
private fun reasonText(hasConfig: Boolean, state: PrinterState): String {
    if (!hasConfig) return stringResource(R.string.splash_setup_printer)
    // R7 (26.5-07): a TLS trust failure on a useSecure (wss/https) connect is the LIVE actionable
    // cause — it outranks any retained klippyStateMessage from a prior session. Distinct message,
    // same Unreachable surface (Retry + Edit connection); never a silent fail or a trust-all bypass.
    val conn = state.connection
    if (conn is ConnectionState.Error && conn.reason == ConnectionError.TlsTrustFailure) {
        return stringResource(R.string.splash_reason_tls)
    }
    // Live Klippy/Moonraker message = pass-through DATA (rendered verbatim — never tokenized).
    state.klippyStateMessage?.takeIf { it.isNotBlank() }?.let { return it }
    return when (state.klippyState) {
        KlippyState.Startup -> stringResource(R.string.splash_reason_startup)
        KlippyState.Shutdown -> stringResource(R.string.splash_reason_shutdown)
        KlippyState.Error -> stringResource(R.string.splash_reason_error)
        KlippyState.Disconnected -> stringResource(R.string.splash_reason_unreachable)
        KlippyState.Ready ->
            if (state.connection is ConnectionState.Connected) stringResource(R.string.splash_reason_connected)
            else stringResource(R.string.splash_reason_unreachable)
    }
}
