package works.mees.dinghy.ui.finetune

import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.TrailingCommitBatcher
import works.mees.dinghy.command.dispatch
import androidx.compose.ui.res.stringResource
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.AdjusterPanel
import works.mees.dinghy.designsystem.components.DetailCard
import works.mees.dinghy.designsystem.components.FloatingEStop
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.IncrementPicker
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * Fine-Tune flat-list screen — the canonical sketch-003 adjustment archetype (D-01).
 *
 * Collapses the four old screens (Hub + Extrusion + Motion + FwRetraction) into ONE flat list:
 *  - Field = scrollable [ListRow] list of all visible params, leading icon tinted to the group's
 *    pool hue (D-02), trailing current-value readout in GeistMono, `hasFwRetraction`-gated (D-08).
 *  - Focus = [DetailCard] wrapping an [AdjusterPanel] (D-05 stepper, D-21 inline baseline).
 *  - `gutter = null` (redesigned screen — FootButtonBar in the field lambda).
 *
 * ## Session memory (D-04)
 * The last-adjusted [FineTuneTuner] is remembered for the session (composition lifetime); the
 * first param in the list is the fresh-entry fallback.
 *
 * ## Trailing-commit batching + clamp authority (quick-rmr / D-22 / 17-07 invariant)
 * Stepper taps accumulate a CLAMPED working value locally via [TrailingCommitBatcher.tap]
 * ([clampForTuner] per tap — the display can never show an un-clamped value); ONE wire command
 * dispatches per quiet window through [commitTunerValue] (re-clamp → [FineTuneHolder.markPending]
 * → dispatch). The screen NEVER calls markPending directly. Resets cancel the pending working
 * value and commit immediately; leaving the screen mid-burst COMMITS via dispose (flush).
 *
 * ## FloatingEStop
 * Wired to [container.printerState]; visible only when printing (PrintState.Printing or Paused).
 * Fine-Tune is a printing-list-valid destination (P24 D-04).
 *
 * This LIVE overload resolves flows from [AppContainer] + [FineTuneHolder] and delegates rendering
 * to the stateless [FineTuneContent] — the same surface the stateless preview overload calls.
 */
@Composable
fun FineTuneScreen(
    holder: FineTuneHolder,
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val vm by holder.vm.collectAsStateWithLifecycle()
    val printerState by container.printerState.collectAsStateWithLifecycle(
        initialValue = PrinterState(),
    )
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    var failureText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(inFlight) { holder.setInFlight(inFlight) }
    val groupBusy = inFlight.isNotEmpty() || holder.pendingStateFlip != null || vm.groupBusy

    // quick-rmr: trailing-commit batcher — taps accumulate a clamped working value locally; ONE
    // wire dispatch per ~500ms quiet window via commitTunerValue. Keys = FineTuneTuner.name (the
    // four retraction tuners SHARE a dispatch key — keying by tuner keeps their working values
    // independent; canCommit translates tuner → dispatch key at fire time).
    val batcher = remember(dispatcher) {
        TrailingCommitBatcher(
            canCommit = { tunerName ->
                // Fire-time read of the in-flight set (never a composition snapshot): the commit
                // RESCHEDULES while this tuner's dispatch key is in flight, so markPending never
                // arms for a guaranteed-rejected dispatch (which would re-create the backstop dim).
                dispatchKeyForTuner(FineTuneTuner.valueOf(tunerName)) !in
                    (dispatcher?.inFlight?.value ?: emptySet())
            },
            onCommit = { tunerName, value ->
                // 19-09 stale-closure lesson: resolve holder.vm.value and the dispatcher AT FIRE
                // TIME via the stable holder + delegated state read — never the composed snapshots.
                val tuner = FineTuneTuner.valueOf(tunerName)
                val param = ALL_FINE_TUNE_PARAMS.first { it.tuner == tuner }
                commitTunerValue(param, value, holder.vm.value, holder, dispatcher)
            },
        )
    }
    // Commit-on-dispose (design decision 1): a same-frame nav mid-burst flushes the pending
    // working value; the dispatch rides CommandDispatcher's app-lifetime scope. Also covers the
    // remember(dispatcher) re-key on reconnect.
    DisposableEffect(batcher) { onDispose { batcher.dispose() } }
    val working by batcher.working.collectAsStateWithLifecycle()

    LaunchedEffect(dispatcher) {
        failureText = null
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event ->
            when (event) {
                is DispatchEvent.Failure -> {
                    failureText = event.message
                    holder.clearPending()
                    // Revert working values to live on failure (the SeverityToast explains why).
                    batcher.cancelAll()
                }
            }
        }
    }
    LaunchedEffect(failureText) {
        if (failureText != null) { delay(4_000); failureText = null }
    }

    // R10 (26.5-03): per-dispatch-key rejection tick counters. The dispatcher emits the key of each
    // intentionally-dropped tap (busy/debounce) on [CommandDispatcher.rejectedKey]; each emission
    // bumps that key's counter and the AdjusterPanel showing that key renders a one-shot flash.
    // PersistentMap keeps the param @Stable for Compose skipping (Phase-22 discipline); it only
    // changes on a rejection (rare), never per state tick.
    var rejectTicks by remember { mutableStateOf(persistentMapOf<String, Long>()) }
    // Lifecycle-aware collection (codex review; Part-1 #3 lifecycle-hygiene invariant): no
    // collection while backgrounded; resumes on STARTED. House pattern = AppShell webcam binding.
    val rejectLifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(dispatcher, rejectLifecycleOwner) {
        rejectTicks = persistentMapOf()
        val d = dispatcher ?: return@LaunchedEffect
        rejectLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            d.rejectedKey.collect { key ->
                rejectTicks = rejectTicks.put(key, (rejectTicks[key] ?: 0L) + 1L)
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        FineTuneContent(
            vm = vm,
            isPrinting = isPrinting,
            busy = groupBusy,
            working = working,
            failureText = failureText,
            rejectTicks = rejectTicks,
            onBack = onBack,
            onNudge = { param, _, stepDelta ->
                // TAP path (quick-rmr): clamp per-tap, accumulate locally — NO markPending, NO
                // dispatch. Base = the pending working value, else the live vm value (an
                // unreported value stays a no-op, existing behavior — never fabricate a base).
                val base = working[param.tuner.name] ?: vm.valueForTuner(param.tuner)
                if (base != null) {
                    batcher.tap(param.tuner.name, clampForTuner(param.tuner, base + stepDelta))
                }
            },
            onNudgeToBaseline = { param, baseline ->
                // Resets commit immediately (design decision 3): cancel the pending working value
                // for this tuner, then the existing immediate path.
                batcher.cancel(param.tuner.name)
                nudgeToBaseline(
                    param = param,
                    baseline = baseline,
                    vm = vm,
                    holder = holder,
                    dispatcher = dispatcher,
                )
            },
            onResetAll = {
                // Reset all params that have a baseline back to their baseline value.
                batcher.cancelAll()
                ALL_FINE_TUNE_PARAMS.forEach { param ->
                    val baseline = vm.baselineForTuner(param.tuner) ?: return@forEach
                    nudgeToBaseline(
                        param = param,
                        baseline = baseline,
                        vm = vm,
                        holder = holder,
                        dispatcher = dispatcher,
                    )
                }
            },
            onEmergencyStop = {
                dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit)
            },
        )
    }
}

/**
 * STATELESS preview/host overload — drives [FineTuneContent] from a pure [FineTuneVm] fixture with
 * no [AppContainer], dispatcher, or holder (no live Moonraker). Side-effects default to no-ops.
 *
 * [isPrinting] drives the [FloatingEStop] visibility in previews.
 */
@Composable
fun FineTuneScreen(
    vm: FineTuneVm,
    isPrinting: Boolean = false,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        FineTuneContent(
            vm = vm,
            isPrinting = isPrinting,
            busy = vm.groupBusy,
            failureText = null,
            onBack = onBack,
            onNudge = { _, _, _ -> },
            onNudgeToBaseline = { _, _ -> },
            onResetAll = {},
            onEmergencyStop = {},
        )
    }
}

/**
 * The pure, container-free Fine-Tune rendering surface shared by BOTH [FineTuneScreen] overloads.
 * Holds no flow/dispatcher state; renders identically under `@Preview` and at runtime.
 */
@Composable
private fun FineTuneContent(
    vm: FineTuneVm,
    isPrinting: Boolean,
    busy: Boolean,
    failureText: String?,
    onBack: () -> Unit,
    rejectTicks: ImmutableMap<String, Long> = persistentMapOf(),
    working: ImmutableMap<String, Double> = persistentMapOf(),
    onNudge: (param: FineTuneParam, currentValue: Double?, stepDelta: Double) -> Unit,
    onNudgeToBaseline: (param: FineTuneParam, baseline: Double) -> Unit,
    onResetAll: () -> Unit,
    onEmergencyStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // D-04: session-remember the selected tuner; first param is the fresh-entry fallback.
    var selectedTuner by remember {
        mutableStateOf(ALL_FINE_TUNE_PARAMS.first().tuner)
    }
    // Active step index resets to the param's default when the selection changes.
    val selectedParam = ALL_FINE_TUNE_PARAMS.first { it.tuner == selectedTuner }
    var activeStep by remember(selectedTuner) {
        mutableStateOf(defaultStepFor(selectedParam))
    }

    // D-08: hide-not-grey FW-retraction rows when the printer doesn't have the capability.
    val visibleParams = ALL_FINE_TUNE_PARAMS.filter { param ->
        if (param.requiresFwRetraction) vm.hasFwRetraction else true
    }

    // E-stop confirm guard state.
    var showEstopGuard by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            focus = {
                // D-01: Focus = DetailCard wrapping AdjusterPanel + FloatingEStop as Box sibling.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp),
                ) {
                    DetailCard(modifier = Modifier.fillMaxSize()) {
                        // quick-rmr: the pending WORKING value wins over the live vm value, so a
                        // tap burst follows the thumb instantly (and survives the echo window).
                        val value = working[selectedTuner.name] ?: vm.valueForTuner(selectedTuner)
                        val baseline = vm.baselineForTuner(selectedTuner)
                        AdjusterPanel(
                            icon = selectedParam.icon,
                            name = selectedParam.name,
                            value = value,
                            unit = selectedParam.unit,
                            baseline = baseline,
                            decimals = selectedParam.decimals,
                            onDecrement = {
                                onNudge(selectedParam, value, -activeStep)
                            },
                            onIncrement = {
                                onNudge(selectedParam, value, +activeStep)
                            },
                            onReset = baseline?.let { base ->
                                { onNudgeToBaseline(selectedParam, base) }
                            },
                            // quick-rmr: never lock out during a tap burst — busy only DIMS the
                            // −/+ tiles (taps accumulate); Reset is disabled while busy.
                            enabled = true,
                            busy = busy,
                            incrementPicker = {
                                IncrementPicker(
                                    steps = selectedParam.steps,
                                    activeStep = activeStep,
                                    onSelect = { activeStep = it },
                                    uDp = grid.uDp,
                                )
                            },
                            uDp = grid.uDp,
                            // R10: flash on busy/debounce rejections of THIS param's dispatch key.
                            rejectTick = rejectTicks[dispatchKeyForTuner(selectedTuner)] ?: 0L,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                        )
                    }

                    // FloatingEStop: Box sibling over the DetailCard, printing-only (P24 D-04).
                    FloatingEStop(
                        visible = isPrinting,
                        onClick = { showEstopGuard = true },
                        uDp = grid.uDp,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(14.dp),
                    )
                }
            },
            field = {
                // Field: param list + FootButtonBar (gutter = null; FootButtonBar lives HERE).
                ListBlock(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                ) {
                    // D-02: group identity = pool-color tint on leading icon; no text group labels.
                    items(visibleParams, key = { it.tuner.name }) { param ->
                        val groupColor = groupColorFor(param.group, t.pool)
                        ListRow(
                            selected = param.tuner == selectedTuner,
                            onClick = {
                                selectedTuner = param.tuner
                                activeStep = defaultStepFor(param)
                            },
                            uDp = grid.uDp,
                            leadingContent = {
                                // UAT-1 exception: dense list-pane icon stays small
                                DinghyIconView(
                                    icon = param.icon,
                                    tint = groupColor,
                                    sizeDp = 22.dp,
                                )
                            },
                            trailingContent = {
                                // Trailing: current-value readout in GeistMono (D-23 fsSp floor).
                                val displayValue = vm.valueForTuner(param.tuner)
                                Text(
                                    text = if (displayValue == null) DASH
                                           else fmtValue(displayValue, param.decimals) + param.unit,
                                    fontFamily = GeistMono,
                                    fontSize = fsSp(17f, t.fs).sp,
                                    color = t.text2,
                                )
                            },
                        ) {
                            Text(
                                text = param.name,
                                fontFamily = Geist,
                                fontWeight = FontWeight.Normal,
                                fontSize = fsSp(18f, t.fs).sp,
                                color = t.text,
                            )
                        }
                    }
                }

                // FootButtonBar is INSIDE the field lambda (gutter = null — D-23 / shared pattern).
                FootButtonBar(
                    uDp = grid.uDp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    // Back (neutral) + Reset All (warn/amber).
                    OutlinedControl(
                        label = "",
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Neutral,
                        icon = DinghyIcons.Back,
                    )
                    OutlinedControl(
                        label = stringResource(R.string.finetune_reset_all),
                        onClick = onResetAll,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Warn,
                    )
                }
            },
            // MANDATORY: gutter = null on redesigned screens (shared pattern — D-23).
            gutter = null,
        )

        // E-stop ConfirmGuard: full-screen overlay when the FloatingEStop is tapped.
        if (showEstopGuard) {
            ConfirmGuard(
                title = stringResource(R.string.printstatus_estop_guard_title),
                message = stringResource(R.string.printstatus_estop_guard_message),
                confirmLabel = stringResource(R.string.printstatus_estop_guard_confirm),
                cancelLabel = stringResource(R.string.common_cancel),
                onConfirm = {
                    onEmergencyStop()
                    showEstopGuard = false
                },
                onCancel = { showEstopGuard = false },
                destructive = true,
            )
        }

        // Non-fatal failure toast (dispatch failures from MotionScreen/ExtrusionScreen pattern).
        if (failureText != null) {
            SeverityToast(
                severity = Severity.Error,
                text = failureText ?: "",
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
