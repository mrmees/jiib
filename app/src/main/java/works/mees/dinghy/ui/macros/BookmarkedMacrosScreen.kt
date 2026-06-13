package works.mees.dinghy.ui.macros

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.MacroInvocation
import works.mees.dinghy.command.MacroParamRejected
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.net.JsonRpcMethods
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.ui.screen.TokenTextField

/**
 * Generously-wide numeric range for macro numeric-param clamping (D-07). Macro bodies never declare
 * their own range, so this is intentionally wide — the IME takeover is the clamp owner (T-26-07-01).
 * A printer-side range violation surfaces as a DispatchEvent.Failure toast, not blocked here.
 */
private val MACRO_NUMERIC_RANGE = -100_000.0..100_000.0

// ─────────────────────────────────────────────────────────────────────────────
// MacroFieldMode (D-09)
//
// The three in-screen modes the merged Macros screen can be in:
//  - Launcher   : the Bookmarked list (tap-to-run, or Manage to switch)
//  - ParamEntry : per-param Field-takeover for a specific macro (D-12)
//  - ManageMode : the System manage-visibility list (D-09)
// ─────────────────────────────────────────────────────────────────────────────

sealed class MacroFieldMode {
    /** The Bookmarked macro launcher — the default state on entry. */
    data object Launcher : MacroFieldMode()

    /**
     * Per-param Field-takeover for [macro]. The launcher transitions here on tap; the Execute foot
     * button (or Back) exits back to Launcher. Absorbs the old MacroExecutionPopup logic entirely.
     */
    data class ParamEntry(val macro: MacroVm) : MacroFieldMode()

    /** The System manage-visibility list. Reached from the Manage foot button in Launcher. */
    data object ManageMode : MacroFieldMode()
}

// ─────────────────────────────────────────────────────────────────────────────
// Live overload — collects from holder, delegates to MacrosContent
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Merged Macros screen (25-05 / D-09/D-10/D-11/D-12) — ONE screen replacing the old
 * BookmarkedMacrosScreen + SystemMacrosScreen + MacroExecutionPopup triple.
 *
 * Field modes:
 *  - [MacroFieldMode.Launcher]   — the Bookmarked ListRow list (D-10)
 *  - [MacroFieldMode.ParamEntry] — per-param Field-takeover with numeric IME / TokenTextField (D-12/D-07)
 *  - [MacroFieldMode.ManageMode] — the System manage-visibility ListRow list (D-09)
 *
 * Security: all macro string params route through [MacroInvocation.buildTyped] (the V5 REJECT sanitizer,
 * T-25-05-01). Execute is disabled until [MacroHolder.paramsKnown] returns true (WR-03 cold-connect
 * gate). Every dispatch uses key `macro_<name>` (PRIM-05).
 *
 * PROMPT-protocol dialogs ([works.mees.dinghy.ui.prompt.PromptDialog]) remain UNAFFECTED — they are
 * AppShell-level overlays independent of this screen (D-13).
 *
 * @param holder     the live macro state holder (reactive param bodies, paramsKnown, state).
 * @param dispatcher the shared command dispatcher (busy key tracking, Failure events).
 * @param onBack     leave the Macros surface (NavHost pops).
 */
@Composable
fun BookmarkedMacrosScreen(
    holder: MacroHolder,
    dispatcher: CommandDispatcher?,
    onToggleBookmark: (String) -> Unit,
    onSetRevealHidden: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by holder.state.collectAsStateWithLifecycle()
    var fieldMode by remember { mutableStateOf<MacroFieldMode>(MacroFieldMode.Launcher) }

    MacrosContent(
        state = state,
        fieldMode = fieldMode,
        holder = holder,
        dispatcher = dispatcher,
        onFieldModeChange = { fieldMode = it },
        onToggleBookmark = onToggleBookmark,
        onSetRevealHidden = onSetRevealHidden,
        onBack = onBack,
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Stateless preview / test seam — no holder, no dispatcher, no Moonraker
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stateless preview seam (D-20 / SC-4). Drives [MacrosContent] from pure fixture state — no
 * [MacroHolder], no [CommandDispatcher], no socket. The [fieldMode] param lets previews cover both
 * the Launcher and ParamEntry modes in a single matrix.
 *
 * All callbacks default to `{}` so preview compositions compile with no wiring.
 */
@Composable
fun BookmarkedMacrosScreen(
    state: MacroScreensState,
    fieldMode: MacroFieldMode = MacroFieldMode.Launcher,
    modifier: Modifier = Modifier,
    onFieldModeChange: (MacroFieldMode) -> Unit = {},
    onToggleBookmark: (String) -> Unit = {},
    onSetRevealHidden: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
) {
    MacrosContent(
        state = state,
        fieldMode = fieldMode,
        holder = null,
        dispatcher = null,
        onFieldModeChange = onFieldModeChange,
        onToggleBookmark = onToggleBookmark,
        onSetRevealHidden = onSetRevealHidden,
        onBack = onBack,
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal renderer
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacrosContent(
    state: MacroScreensState,
    fieldMode: MacroFieldMode,
    holder: MacroHolder?,
    dispatcher: CommandDispatcher?,
    onFieldModeChange: (MacroFieldMode) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onSetRevealHidden: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        // WR-02: dispatch-failure feedback is collected at SCREEN level — execute() flips fieldMode
        // back to Launcher in the same frame as the dispatch, so a printer-side rejection (e.g. a
        // MACRO_NUMERIC_RANGE violation) arrives AFTER MacroParamEntryField has left composition.
        // `dispatcher.events` is a hot flow with no replay; a collector inside the param surface
        // would never see it. Keyed on the `macro_` busy-key prefix (PRIM-05).
        val context = LocalContext.current
        var failureToast by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(dispatcher) {
            dispatcher?.events?.collect { event ->
                if (event is DispatchEvent.Failure && event.key.startsWith("macro_")) {
                    failureToast = context.getString(
                        R.string.macros_rejected,
                        event.key.removePrefix("macro_"),
                        event.message,
                    )
                }
            }
        }
        // Auto-dismiss so the overlay never permanently covers the FootButtonBar.
        LaunchedEffect(failureToast) {
            if (failureToast != null) {
                delay(6_000)
                failureToast = null
            }
        }

        ScreenScaffold(
            focus = null,
            field = {
                when (val mode = fieldMode) {
                    is MacroFieldMode.Launcher -> MacroLauncherField(
                        state = state,
                        uDp = grid.uDp,
                        onTap = { macro -> onFieldModeChange(MacroFieldMode.ParamEntry(macro)) },
                        onManage = { onFieldModeChange(MacroFieldMode.ManageMode) },
                        onBack = onBack,
                    )
                    is MacroFieldMode.ParamEntry -> MacroParamEntryField(
                        macro = mode.macro,
                        holder = holder,
                        dispatcher = dispatcher,
                        uDp = grid.uDp,
                        onBack = { onFieldModeChange(MacroFieldMode.Launcher) },
                        onExecuted = { onFieldModeChange(MacroFieldMode.Launcher) },
                    )
                    is MacroFieldMode.ManageMode -> MacroManageField(
                        state = state,
                        uDp = grid.uDp,
                        onToggleBookmark = onToggleBookmark,
                        onSetRevealHidden = onSetRevealHidden,
                        onBack = { onFieldModeChange(MacroFieldMode.Launcher) },
                    )
                }
            },
            gutter = null,
        )

        // Screen-level failure toast (WR-02) — survives the ParamEntry → Launcher mode flip.
        failureToast?.let { msg ->
            SeverityToast(
                Severity.Error,
                msg,
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MacroLauncherField — Bookmarked list (D-10)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.MacroLauncherField(
    state: MacroScreensState,
    uDp: androidx.compose.ui.unit.Dp,
    onTap: (MacroVm) -> Unit,
    onManage: () -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTokens.current
    when {
        state.unavailable -> {
            MacrosUnavailable(modifier = Modifier.weight(1f).padding(24.dp))
        }
        state.bookmarkedMacros.isEmpty() -> {
            MacrosEmptyNotice(modifier = Modifier.weight(1f).padding(24.dp))
        }
        else -> {
            // D-10: ListBlock of ListRows — never a LazyVerticalGrid or MacroTile
            ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
                items(state.bookmarkedMacros, key = { it.name }) { macro ->
                    ListRow(
                        selected = false,    // tap-to-execute, not select
                        onClick = { onTap(macro) },
                        uDp = uDp,
                    ) {
                        // Canonical list label (R22) — same normalize-font ruling as BedMesh
                        // profile names; the macro name is the row's LABEL, not a value readout.
                        ListRowLabel(macro.name)
                    }
                }
            }
        }
    }
    FootButtonBar(
        uDp = uDp,
    ) {
        OutlinedControl(
            label = stringResource(R.string.macros_foot_back),
            onClick = onBack,
            modifier = Modifier.weight(1f),
            intent = Intent.Accent, // R5: Back = accent
            icon = DinghyIcons.Back,
        )
        OutlinedControl(
            label = stringResource(R.string.macros_foot_manage),
            onClick = onManage,
            modifier = Modifier.weight(1f),
            intent = Intent.Accent, // R5: plain navigation = accent
            icon = DinghyIcons.ManageMacros,
            contentDescription = stringResource(R.string.cd_macros_manage),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MacroParamEntryField — per-param Field-takeover (D-12)
//
// Absorbs the entire MacroExecutionPopup logic (WR-03, PRIM-05, V5 sanitizer).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.MacroParamEntryField(
    macro: MacroVm,
    holder: MacroHolder?,
    dispatcher: CommandDispatcher?,
    uDp: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit,
    onExecuted: () -> Unit,
) {
    val t = LocalTokens.current
    val context = LocalContext.current
    val busyKey = "macro_${macro.name}"
    val inFlight by (dispatcher?.inFlight ?: remember { MutableStateFlow(emptySet<String>()) })
        .collectAsStateWithLifecycle()
    val running = busyKey in inFlight

    // WR-03: re-resolve the live VM from holder by NAME each recomposition so late-arriving bodies
    // populate params (a cold-connect parametered macro tapped before its body landed would show
    // "No parameters" and allow a bare Execute without this live re-resolve).
    val holderState by (holder?.state ?: remember { MutableStateFlow(MacroScreensState()) })
        .collectAsStateWithLifecycle()
    val liveMacro = remember(holderState, macro.name) {
        holderState.macros.firstOrNull { it.name.equals(macro.name, ignoreCase = true) } ?: macro
    }
    // paramsKnown: "configfile body arrived" gate. Until this is true Execute stays disabled (WR-03).
    val bodyLoaded = remember(holderState, macro.name) {
        holder?.paramsKnown(macro.name) ?: true  // preview: treat as loaded
    }
    val params = liveMacro.params

    // Per-param current values, seeded from param.default. Re-seeded when the param SET changes so a
    // cold-connect body arrival populates the fields (keyed on macro.name + params, not name alone).
    val values = remember(macro.name, params) {
        mutableStateMapOf(*params.map { it.name to (it.default ?: "") }.toTypedArray())
    }

    // Toast message for LOCAL rejection (forbidden char — synchronous, so this surface is still
    // composed when it fires). Printer-side DispatchEvent.Failure rejections are collected at the
    // SCREEN level in [MacrosContent] (WR-02): execute() leaves this surface in the same frame as
    // the dispatch, so a collector here would never see the (no-replay) failure event.
    var toast by remember(macro.name) { mutableStateOf<String?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current

    fun execute() {
        val gcode = try {
            // T-25-05-01: buildTyped is the REQUIRED V5 sanitizer path — never bypass with scriptParams directly.
            // WR-04: params left BLANK are omitted from the line entirely — emitting `KEY=` / `KEY=""`
            // would override the macro's own Jinja default with a malformed/empty token; omitting the
            // key lets the macro's default apply server-side.
            MacroInvocation.buildTyped(
                macro.name,
                params.mapNotNull { p ->
                    val raw = values[p.name].orEmpty()
                    if (raw.isBlank()) return@mapNotNull null
                    // WR-09 (26-rev): clamp ownership is real — numeric params are clamped HERE, on
                    // the dispatch path itself, so Execute-without-Done (which skips onValueCommit's
                    // ImeAction.Done clamp) can never send an unclamped number. Non-finite or
                    // unparseable text falls through unchanged for buildTyped's rejectNonNumeric to
                    // reject (toast), never silently coerced.
                    val value = if (p.isNumeric) {
                        val parsed = raw.toDoubleOrNull()
                        if (parsed != null && parsed.isFinite()) {
                            formatNumeric(
                                parsed.coerceIn(MACRO_NUMERIC_RANGE.start, MACRO_NUMERIC_RANGE.endInclusive)
                            )
                        } else {
                            raw
                        }
                    } else {
                        raw
                    }
                    Triple(p.name, value, p.isNumeric)
                },
            )
        } catch (e: MacroParamRejected) {
            // WR-03: resolved via context.getString — execute() is not a composable context.
            toast = context.getString(R.string.macros_rejected, macro.name, e.reason)
            return
        }
        dispatcher?.dispatch(
            key = busyKey,                             // PRIM-05: per-macro busy key
            method = JsonRpcMethods.GCODE_SCRIPT,
            params = PrinterCommands.scriptParams(gcode),
        )
        keyboardController?.hide()
        onExecuted()
    }

    // Macro name title
    Text(
        text = macro.name,
        color = t.text,
        fontFamily = GeistMono,
        fontWeight = FontWeight.SemiBold,
        fontSize = fsSp(22f, t.fs).sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )

    // Param list (or loading/empty notice)
    ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
        if (params.isEmpty()) {
            item(key = "empty_state") {
                Text(
                    text = if (bodyLoaded) {
                        stringResource(R.string.macros_no_params)
                    } else {
                        stringResource(R.string.macros_loading_params)
                    },
                    color = t.text2,
                    fontFamily = Geist,
                    fontSize = fsSp(15f, t.fs).sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
        }
        items(params, key = { it.name }) { param ->
            if (param.isNumeric) {
                // Numeric param → inline numeric IME field (D-07/D-12). Values map updated live on
                // each change; clamped to MACRO_NUMERIC_RANGE on ImeAction.Done (T-26-07-01).
                MacroNumericParamField(
                    param = param,
                    rawValue = values[param.name].orEmpty(),
                    onValueChange = { raw -> values[param.name] = raw },
                    onValueCommit = { raw ->
                        val parsed = raw.toDoubleOrNull()
                        if (parsed != null) {
                            values[param.name] = formatNumeric(
                                parsed.coerceIn(MACRO_NUMERIC_RANGE.start, MACRO_NUMERIC_RANGE.endInclusive)
                            )
                        } else if (raw.isEmpty()) {
                            values[param.name] = ""
                        }
                    },
                    t = t,
                )
            } else {
                // String param → TokenTextField (the ONE sanctioned alpha keyboard in printer controls — D-12)
                TokenTextField(
                    value = values[param.name].orEmpty(),
                    onValueChange = { values[param.name] = it },
                    label = param.name,
                    keyboardType = KeyboardType.Text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                )
            }
        }
    }

    // Toast area: local rejection or in-flight indicator
    toast?.let { msg ->
        SeverityToast(
            Severity.Error,
            msg,
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
    }
    if (running) {
        SeverityToast(
            Severity.Info,
            stringResource(R.string.macros_running, macro.name),
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
        )
    }

    FootButtonBar(
        uDp = uDp,
    ) {
        OutlinedControl(
            label = stringResource(R.string.macros_foot_back),
            onClick = onBack,
            modifier = Modifier.weight(1f),
            intent = Intent.Accent, // R5: Back = accent
            icon = DinghyIcons.Back,
        )
        // Execute: disabled while !bodyLoaded (WR-03) OR running (PRIM-05).
        // OutlinedControl has no `enabled` param — use alpha+semantics (25-03 FilesScreen convention).
        val executeEnabled = bodyLoaded && !running
        OutlinedControl(
            label = stringResource(R.string.macros_foot_execute),
            onClick = { if (executeEnabled) execute() },
            modifier = Modifier
                .weight(1f)
                .then(
                    if (!executeEnabled) {
                        Modifier
                            .alpha(0.38f)
                            .semantics { disabled() }
                    } else {
                        Modifier
                    },
                ),
            intent = Intent.Go, // R5: Execute = the expected action
            icon = DinghyIcons.ExecuteMacro,
            contentDescription = stringResource(R.string.cd_macros_execute),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MacroNumericParamField — inline numeric IME row for a single numeric param (D-07)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * An inline-row numeric-IME entry for ONE numeric macro parameter (D-07/D-12). Displays the param name
 * as a label and the current value in a [BasicTextField] with [KeyboardType.Decimal] + [ImeAction.Done].
 * [onValueChange] is called on every keystroke (so the parent values map stays current for Execute).
 * [onValueCommit] is called on Done with the final text (caller applies clamp — T-26-07-01).
 */
@Composable
private fun MacroNumericParamField(
    param: MacroParam,
    rawValue: String,
    onValueChange: (String) -> Unit,
    onValueCommit: (String) -> Unit,
    t: works.mees.dinghy.theme.ThemeTokens,
) {
    // Local editing text; seeded from rawValue, synced when the parent updates it (e.g. on commit)
    var editText by remember(param.name) { mutableStateOf(rawValue) }
    LaunchedEffect(rawValue) { if (editText != rawValue) editText = rawValue }

    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = param.name,
            color = t.text2,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(15f, t.fs).sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BasicTextField(
            value = editText,
            onValueChange = { raw ->
                // Accept digits, an optional leading minus, and one decimal point only (no alpha).
                // WR-09 (26-rev): the old `raw.toDoubleOrNull() != null` branch admitted "NaN",
                // "Infinity", and exponent forms like "1e5" — the digits-only regex is the sole
                // gate now (it already covers every legitimate keystroke sequence, incl. "-").
                if (raw.isEmpty() || raw.matches(Regex("-?\\d*\\.?\\d*"))) {
                    editText = raw
                    onValueChange(raw)  // keep parent values map current for Execute
                }
            },
            singleLine = true,
            textStyle = TextStyle(
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(18f, t.fs).sp,
            ),
            cursorBrush = SolidColor(t.accent2),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onValueCommit(editText) },
            ),
            modifier = Modifier
                .clip(shape)
                .border(BorderStroke(2.dp, t.outline), shape)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            decorationBox = { innerField ->
                if (editText.isEmpty()) {
                    Text(
                        text = "0",
                        color = t.text3,
                        fontFamily = GeistMono,
                        fontSize = fsSp(18f, t.fs).sp,
                    )
                }
                innerField()
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MacroManageField — System manage-visibility list (D-09)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.MacroManageField(
    state: MacroScreensState,
    uDp: androidx.compose.ui.unit.Dp,
    onToggleBookmark: (String) -> Unit,
    onSetRevealHidden: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val t = LocalTokens.current
    if (state.unavailable) {
        MacrosUnavailable(modifier = Modifier.weight(1f).padding(24.dp))
    } else {
        Text(
            text = stringResource(R.string.macros_helper_hint),
            color = t.text2,
            fontFamily = Geist,
            fontSize = fsSp(15f, t.fs).sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        )
        ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp)) {
            items(state.visibleMacros, key = { it.name }) { macro ->
                val isBookmarked = macro.isBookmarked
                ListRow(
                    selected = isBookmarked,
                    onClick = { onToggleBookmark(macro.name) },
                    uDp = uDp,
                    trailingContent = {
                        DinghyIconView(
                            icon = if (isBookmarked) DinghyIcons.CheckCircle else DinghyIcons.UnbookmarkedMacro,
                            tint = if (isBookmarked) t.accent else t.text3,
                            sizeDp = fsSp(24f, t.fs).dp,
                            modifier = Modifier.padding(start = 8.dp),
                            // WR-05: speak the bookmark state — it is otherwise color/glyph-only.
                            contentDescription = stringResource(
                                if (isBookmarked) R.string.cd_macros_bookmarked else R.string.cd_macros_unbookmarked,
                            ),
                        )
                    },
                ) {
                    Text(
                        text = macro.name,
                        color = t.text,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fsSp(18f, t.fs).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
    FootButtonBar(
        uDp = uDp,
    ) {
        OutlinedControl(
            label = stringResource(R.string.macros_foot_back),
            onClick = onBack,
            modifier = Modifier.weight(1f),
            intent = Intent.Accent, // R5: Back = accent
            icon = DinghyIcons.Back,
        )
        // Show hidden toggle: the old SystemMacrosScreen glyph pair, now routed through the registry
        // (WR-07 — same glyphs, no new icon choice; raw ligature strings bypassed the subset gate).
        OutlinedControl(
            label = stringResource(R.string.macros_foot_show_hidden),
            onClick = { onSetRevealHidden(!state.revealHidden) },
            modifier = Modifier.weight(1f),
            intent = if (state.revealHidden) Intent.Accent else Intent.Neutral,
            icon = if (state.revealHidden) DinghyIcons.Visibility else DinghyIcons.VisibilityOff,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Empty / unavailable states
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacrosEmptyNotice(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(
            text = stringResource(R.string.macros_empty_title),
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(22f, t.fs).sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.macros_empty_body),
            color = t.text2,
            fontFamily = Geist,
            fontSize = fsSp(15f, t.fs).sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )
    }
}

@Composable
internal fun MacrosUnavailable(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Column(modifier, verticalArrangement = Arrangement.Center) {
        Text(
            text = stringResource(R.string.macros_unavailable),
            color = t.text2,
            fontFamily = Geist,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(16f, t.fs).sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Drop a trailing ".0" on a whole-number numeric value for clean display in the param field. */
private fun formatNumeric(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
