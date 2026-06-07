package works.mees.dinghy.ui.screen

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.ConnectionState
import works.mees.dinghy.state.KlippyState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.brandTint
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

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
 * Per docs/ui_design/LAYOUT.md the splash **omits the gutter** (`gutter = null`): its Field buttons
 * ARE the navigation, and there is deliberately no drawer affordance (the hard override).
 *
 * Reason text (review #8): prefer the REAL [PrinterState.klippyStateMessage] (the Moonraker/Klippy
 * human reason) when present, falling back to a terse enum-derived label — never blank. The message
 * is server-provided and rendered as plain text only, never interpreted (T-04-05-I).
 *
 * All recovery dispatches go through the narrow [works.mees.dinghy.di.SessionControl] contract
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
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Normal,
                        fontSize = fsSp(16f, t.fs).sp,
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
                                    label = "Set up your printer",
                                    onClick = onEditConnection,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Accent,
                                )
                            }

                            RecoveryMode.KlippyDown -> {
                                // D-12: Moonraker reachable but Klipper is shut down / errored — offer
                                // the firmware/host recovery commands (routed via SessionControl).
                                OutlinedControl(
                                    label = "Retry",
                                    onClick = sessionControl::requestReconnectNow,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Accent,
                                )
                                OutlinedControl(
                                    label = "Restart firmware",
                                    onClick = sessionControl::restartFirmware,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Warn,
                                )
                                OutlinedControl(
                                    label = "Restart Klipper",
                                    onClick = sessionControl::restartHost,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Warn,
                                )
                            }

                            RecoveryMode.Unreachable -> {
                                // D-13: a saved connection that won't connect — Klippy isn't reachable,
                                // so NO firmware/restart here (they'd never land); offer Retry + Edit.
                                OutlinedControl(
                                    label = "Retry",
                                    onClick = sessionControl::requestReconnectNow,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Accent,
                                )
                                OutlinedControl(
                                    label = "Edit connection",
                                    onClick = onEditConnection,
                                    modifier = Modifier.weight(1f),
                                    intent = Intent.Neutral,
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
 * seam [works.mees.dinghy.preview.BrandPreviews] drives across the six theme combos (PREVIEW_AND_TOKENS /
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
private fun reasonText(hasConfig: Boolean, state: PrinterState): String {
    if (!hasConfig) return "Set up your printer"
    state.klippyStateMessage?.takeIf { it.isNotBlank() }?.let { return it }
    return when (state.klippyState) {
        KlippyState.Startup -> "Printer starting up…"
        KlippyState.Shutdown -> "Printer is shut down"
        KlippyState.Error -> "Printer firmware error"
        KlippyState.Disconnected -> "Can't reach the printer"
        KlippyState.Ready ->
            if (state.connection is ConnectionState.Connected) "Connected" else "Can't reach the printer"
    }
}
