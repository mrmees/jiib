package works.mees.jiib.ui.macros

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.jiib.R
import works.mees.jiib.command.CommandDispatcher
import works.mees.jiib.command.DispatchEvent
import works.mees.jiib.command.MacroInvocation
import works.mees.jiib.command.MacroParamRejected
import works.mees.jiib.command.PrinterCommands
import works.mees.jiib.designsystem.Severity
import works.mees.jiib.designsystem.SeverityToast
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowLabel
import works.mees.jiib.designsystem.components.ToggleRow
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.net.JsonRpcMethods
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.ui.screen.TokenTextField

/**
 * Generously-wide numeric range for macro numeric-param clamping (D-07). Macro bodies never declare
 * their own range, so this is intentionally wide — the IME takeover is the clamp owner. A printer-side
 * range violation surfaces as a DispatchEvent.Failure toast, not blocked here.
 */
private val MACRO_NUMERIC_RANGE = -100_000.0..100_000.0

// ─────────────────────────────────────────────────────────────────────────────
// MacroFieldMode — the Field has TWO modes now (ParamEntry retired; the selected
// macro lives in the FOCUS, the list stays in the Field).
//  - Launcher   : the Bookmarked list (tap-to-SELECT; the selected macro fills the Focus)
//  - ManageMode : the System manage-visibility list (pin/unpin, reveal-hidden)
// ─────────────────────────────────────────────────────────────────────────────

sealed class MacroFieldMode {
    /** The Bookmarked macro launcher — the default state on entry. */
    data object Launcher : MacroFieldMode()

    /** The System manage-visibility list. Reached from the Manage foot button in Launcher. */
    data object ManageMode : MacroFieldMode()
}

// ─────────────────────────────────────────────────────────────────────────────
// Live overload — collects from holder, delegates to MacrosContent
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Merged Macros screen. The Field is the always-visible launcher list (or the Manage list); the FOCUS
 * holds the selected macro's detail — name (FocusFrame title), description, and keyboard-backed param
 * fields (or a single raw-args field, or a loading notice). Execute is a green foot button.
 *
 * Security: all string macro params route through [MacroInvocation.buildTyped]; raw-args macros route
 * through [MacroInvocation.buildRaw]. Both apply the V5 REJECT sanitizer. Execute is disabled until the
 * holder reports the configfile body has landed (cold-connect gate). Every dispatch uses key
 * `macro_<name>`.
 *
 * @param holder     the live macro state holder (reactive param bodies, descriptions, paramsKnown, state).
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
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
) {
    val state by holder.state.collectAsStateWithLifecycle()
    var fieldMode by remember { mutableStateOf<MacroFieldMode>(MacroFieldMode.Launcher) }
    var selectedName by remember { mutableStateOf<String?>(null) }

    MacrosContent(
        state = state,
        fieldMode = fieldMode,
        selectedName = selectedName,
        holder = holder,
        dispatcher = dispatcher,
        onFieldModeChange = { fieldMode = it },
        onSelect = { selectedName = it },
        onToggleBookmark = onToggleBookmark,
        onSetRevealHidden = onSetRevealHidden,
        onBack = onBack,
        isPrinting = isPrinting,
        onEmergencyStop = onEmergencyStop,
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Stateless preview / test seam — no holder, no dispatcher, no Moonraker
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Stateless preview seam. Drives [MacrosContent] from pure fixture state — no holder, no dispatcher, no
 * socket. [selectedName] selects a macro into the Focus; [fieldMode] picks Launcher vs Manage.
 */
@Composable
fun BookmarkedMacrosScreen(
    state: MacroScreensState,
    fieldMode: MacroFieldMode = MacroFieldMode.Launcher,
    selectedName: String? = null,
    modifier: Modifier = Modifier,
    onFieldModeChange: (MacroFieldMode) -> Unit = {},
    onSelect: (String?) -> Unit = {},
    onToggleBookmark: (String) -> Unit = {},
    onSetRevealHidden: (Boolean) -> Unit = {},
    onBack: () -> Unit = {},
) {
    MacrosContent(
        state = state,
        fieldMode = fieldMode,
        selectedName = selectedName,
        holder = null,
        dispatcher = null,
        onFieldModeChange = onFieldModeChange,
        onSelect = onSelect,
        onToggleBookmark = onToggleBookmark,
        onSetRevealHidden = onSetRevealHidden,
        onBack = onBack,
        modifier = modifier,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Internal renderer — owns the hoisted param-entry state shared by Focus + Field
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacrosContent(
    state: MacroScreensState,
    fieldMode: MacroFieldMode,
    selectedName: String?,
    holder: MacroHolder?,
    dispatcher: CommandDispatcher?,
    onFieldModeChange: (MacroFieldMode) -> Unit,
    onSelect: (String?) -> Unit,
    onToggleBookmark: (String) -> Unit,
    onSetRevealHidden: (Boolean) -> Unit,
    onBack: () -> Unit,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val context = LocalContext.current
        val keyboardController = LocalSoftwareKeyboardController.current

        // Screen-level dispatch-failure toast (survives any recomposition of the Focus detail).
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
        LaunchedEffect(failureToast) {
            if (failureToast != null) {
                delay(6_000)
                failureToast = null
            }
        }

        // Resolve the live selected macro from holder state by NAME (so late-arriving bodies populate).
        val liveMacro = remember(state, selectedName) {
            selectedName?.let { name -> state.macros.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        }
        val params = liveMacro?.params ?: emptyList()
        // "configfile body arrived" gate (cold-connect): Execute stays disabled until true.
        val bodyLoaded = remember(state, selectedName) {
            if (selectedName == null) false else holder?.paramsKnown(selectedName) ?: true
        }
        // Raw-args mode: rawparams macros OR a macro with no inferable params (CLI fallback — every macro
        // is runnable, and args can be passed to anything).
        val rawMode = liveMacro != null && (liveMacro.usesRawParams || params.isEmpty())

        val inFlight by (dispatcher?.inFlight ?: remember { MutableStateFlow(emptySet<String>()) })
            .collectAsStateWithLifecycle()
        // Derive the in-flight key from the resolved macro (same source as the dispatch key) so a
        // selectedName/canonical-name casing drift can never desync the "running" indicator.
        val running = liveMacro != null && "macro_${liveMacro.name}" in inFlight

        // Hoisted per-macro entry state — reset whenever the selected macro (or its param set) changes.
        val values = remember(selectedName, params) {
            mutableStateMapOf(*params.map { it.name to (it.default ?: "") }.toTypedArray())
        }
        var rawArgs by remember(selectedName) { mutableStateOf("") }
        var localToast by remember(selectedName) { mutableStateOf<String?>(null) }

        fun execute() {
            val macro = liveMacro ?: return
            val gcode = try {
                if (rawMode) {
                    MacroInvocation.buildRaw(macro.name, rawArgs)
                } else {
                    // buildTyped is the REQUIRED V5 sanitizer path. Params left BLANK are omitted entirely
                    // (emitting KEY= would override the macro's own Jinja default). Numeric params are
                    // clamped HERE on the dispatch path so an Execute that skipped the IME Done-clamp can
                    // never send an unclamped number.
                    MacroInvocation.buildTyped(
                        macro.name,
                        params.mapNotNull { p ->
                            val raw = values[p.name].orEmpty()
                            if (raw.isBlank()) return@mapNotNull null
                            val value = if (p.isNumeric) {
                                val parsed = raw.toDoubleOrNull()
                                if (parsed != null && parsed.isFinite()) {
                                    formatNumeric(
                                        parsed.coerceIn(MACRO_NUMERIC_RANGE.start, MACRO_NUMERIC_RANGE.endInclusive),
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
                }
            } catch (e: MacroParamRejected) {
                localToast = context.getString(R.string.macros_rejected, macro.name, e.reason)
                return
            }
            dispatcher?.dispatch(
                key = "macro_${macro.name}",
                method = JsonRpcMethods.GCODE_SCRIPT,
                params = PrinterCommands.scriptParams(gcode),
            )
            keyboardController?.hide()
        }

        val executeEnabled = selectedName != null && bodyLoaded && !running

        ScreenScaffold(
            focus = {
                val title = liveMacro?.name ?: stringResource(R.string.cd_launcher_macros)
                FocusFrame(
                    title = title,
                    icon = JiibIcons.LauncherMacros,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxSize(),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    val t = LocalTokens.current
                    when {
                        state.unavailable -> MacrosUnavailable(modifier = Modifier.fillMaxWidth())
                        fieldMode is MacroFieldMode.ManageMode -> {
                            // Manage Focus = what bookmarking does + the relocated underscore note
                            // (owner 2026-06-17 — moved here from the top of the manage list).
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.macros_bookmark_explainer),
                                        color = t.text2,
                                        style = JiibType.body.toTextStyle(t),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    Text(
                                        text = stringResource(R.string.macros_helper_hint),
                                        color = t.text2,
                                        // Match the bookmarking explainer's size (owner 2026-06-17).
                                        style = JiibType.body.toTextStyle(t),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                        }
                        liveMacro == null -> {
                            // Launcher empty-state: nothing selected yet.
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = stringResource(R.string.macros_focus_select_prompt),
                                    color = t.text2,
                                    style = JiibType.body.toTextStyle(t),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        else -> MacroDetailFocusBody(
                            macro = liveMacro,
                            bodyLoaded = bodyLoaded,
                            rawMode = rawMode,
                            params = params,
                            values = values,
                            rawArgs = rawArgs,
                            onRawArgsChange = { rawArgs = it },
                            localToast = localToast,
                            running = running,
                            t = t,
                        )
                    }
                }
            },
            field = {
                when (fieldMode) {
                    is MacroFieldMode.Launcher -> MacroLauncherField(
                        state = state,
                        uDp = grid.uDp,
                        selectedName = selectedName,
                        onSelect = onSelect,
                        onManage = { onFieldModeChange(MacroFieldMode.ManageMode) },
                        onBack = onBack,
                        onExecute = { execute() },
                        executeEnabled = executeEnabled,
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
        )

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
// MacroDetailFocusBody — the selected macro's detail inside the FocusFrame (ColumnScope content)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.MacroDetailFocusBody(
    macro: MacroVm,
    bodyLoaded: Boolean,
    rawMode: Boolean,
    params: List<MacroParam>,
    values: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
    rawArgs: String,
    onRawArgsChange: (String) -> Unit,
    localToast: String?,
    running: Boolean,
    t: works.mees.jiib.theme.ThemeTokens,
) {
    if (!macro.description.isNullOrBlank()) {
        Text(
            text = macro.description,
            color = t.text2,
            style = JiibType.body.toTextStyle(t),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    when {
        !bodyLoaded -> {
            Text(
                text = stringResource(R.string.macros_loading_params),
                color = t.text2,
                style = JiibType.caption.toTextStyle(t),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        rawMode -> {
            TokenTextField(
                value = rawArgs,
                onValueChange = onRawArgsChange,
                label = stringResource(R.string.macros_raw_args_label),
                keyboardType = KeyboardType.Text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(R.string.macros_raw_args_hint),
                color = t.text3,
                style = JiibType.caption.toTextStyle(t),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        else -> {
            // This inner Column owns param-list overflow: it scrolls within the bounded Focus region
            // (a high-param macro in a short landscape Focus stays usable).
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                params.forEach { param ->
                    if (param.isNumeric) {
                        MacroNumericParamField(
                            param = param,
                            rawValue = values[param.name].orEmpty(),
                            onValueChange = { raw -> values[param.name] = raw },
                            onValueCommit = { raw ->
                                val parsed = raw.toDoubleOrNull()
                                if (parsed != null) {
                                    values[param.name] = formatNumeric(
                                        parsed.coerceIn(MACRO_NUMERIC_RANGE.start, MACRO_NUMERIC_RANGE.endInclusive),
                                    )
                                } else if (raw.isEmpty()) {
                                    values[param.name] = ""
                                }
                            },
                            t = t,
                        )
                    } else {
                        TokenTextField(
                            value = values[param.name].orEmpty(),
                            onValueChange = { values[param.name] = it },
                            label = paramLabel(param),
                            keyboardType = KeyboardType.Text,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    localToast?.let { msg ->
        SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth().padding(horizontal = 8.dp))
    }
    if (running) {
        SeverityToast(
            Severity.Info,
            stringResource(R.string.macros_running, macro.name),
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
    }
}

/** Param label with a `*` marker when the macro author expects the caller to supply it (required). */
private fun paramLabel(param: MacroParam): String =
    if (param.required) "${param.name} *" else param.name

// ─────────────────────────────────────────────────────────────────────────────
// MacroLauncherField — Bookmarked list (always visible) + foot bar [Back][Manage][Execute]
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.MacroLauncherField(
    state: MacroScreensState,
    uDp: androidx.compose.ui.unit.Dp,
    selectedName: String?,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    onBack: () -> Unit,
    onExecute: () -> Unit,
    executeEnabled: Boolean,
) {
    when {
        state.unavailable -> {
            MacrosUnavailable(modifier = Modifier.weight(1f).padding(24.dp))
        }
        state.bookmarkedMacros.isEmpty() -> {
            MacrosEmptyNotice(modifier = Modifier.weight(1f).padding(24.dp))
        }
        else -> {
            ListBlock(modifier = Modifier.weight(1f)) {
                items(state.bookmarkedMacros, key = { it.name }) { macro ->
                    ListRow(
                        selected = macro.name.equals(selectedName, ignoreCase = true),
                        onClick = { onSelect(macro.name) },
                        uDp = uDp,
                    ) {
                        ListRowLabel(macro.name)
                    }
                }
            }
        }
    }
    // 3 actions → icon-only; execute alpha/disabled modifier passes through FootAction.modifier.
    FootButtonBar(
        uDp = uDp,
        actions = listOf(
            FootAction(
                label = stringResource(R.string.macros_foot_back),
                icon = JiibIcons.Back,
                onClick = onBack,
                intent = Intent.Accent, // R5: Back = accent
            ),
            FootAction(
                label = stringResource(R.string.macros_foot_manage),
                icon = JiibIcons.ManageMacros,
                onClick = onManage,
                intent = Intent.Accent, // R5: plain navigation = accent
                contentDescription = stringResource(R.string.cd_macros_manage),
            ),
            // Execute (R5: Go = the expected action). OutlinedControl has no `enabled` param — use
            // alpha+semantics (the established disabled convention).
            FootAction(
                label = stringResource(R.string.macros_foot_execute),
                icon = JiibIcons.ExecuteMacro,
                onClick = { if (executeEnabled) onExecute() },
                intent = Intent.Go,
                contentDescription = stringResource(R.string.cd_macros_execute),
                modifier = if (!executeEnabled) Modifier.alpha(0.38f).semantics { disabled() } else Modifier,
            ),
        ),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// MacroNumericParamField — inline numeric IME row for a single numeric param
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MacroNumericParamField(
    param: MacroParam,
    rawValue: String,
    onValueChange: (String) -> Unit,
    onValueCommit: (String) -> Unit,
    t: works.mees.jiib.theme.ThemeTokens,
) {
    var editText by remember(param.name) { mutableStateOf(rawValue) }
    LaunchedEffect(rawValue) { if (editText != rawValue) editText = rawValue }

    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = paramLabel(param),
            color = t.text2,
            style = JiibType.caption.toTextStyle(t),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        BasicTextField(
            value = editText,
            onValueChange = { raw ->
                // Accept digits, an optional leading minus, and one decimal point only (no alpha, no
                // NaN/Infinity/exponent forms).
                if (raw.isEmpty() || raw.matches(Regex("-?\\d*\\.?\\d*"))) {
                    editText = raw
                    onValueChange(raw)
                }
            },
            singleLine = true,
            textStyle = JiibType.dataInline.toTextStyle(t).copy(color = t.text),
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
                        style = JiibType.dataInline.toTextStyle(t),
                    )
                }
                innerField()
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MacroManageField — System manage-visibility list (rows are canonical ToggleRow switches)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.MacroManageField(
    state: MacroScreensState,
    uDp: androidx.compose.ui.unit.Dp,
    onToggleBookmark: (String) -> Unit,
    onSetRevealHidden: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    if (state.unavailable) {
        MacrosUnavailable(modifier = Modifier.weight(1f).padding(24.dp))
    } else {
        ListBlock(modifier = Modifier.weight(1f)) {
            items(state.visibleMacros, key = { it.name }) { macro ->
                // No explicit contentDescription: ToggleRow defaults it to the label (the macro name),
                // and its Role.Switch announces the on/off (bookmarked) state — so TalkBack reads
                // "<macro name>, switch, checked/not checked" instead of dropping the name.
                ToggleRow(
                    label = macro.name,
                    checked = macro.isBookmarked,
                    onToggle = { onToggleBookmark(macro.name) },
                    uDp = uDp,
                )
            }
        }
    }
    // 2 actions → icon+text (label visible).
    FootButtonBar(
        uDp = uDp,
        actions = listOf(
            FootAction(
                label = stringResource(R.string.macros_foot_back),
                icon = JiibIcons.Back,
                onClick = onBack,
                intent = Intent.Accent, // R5: Back = accent
            ),
            FootAction(
                label = stringResource(
                    if (state.revealHidden) R.string.macros_foot_hide else R.string.macros_foot_show,
                ),
                icon = if (state.revealHidden) JiibIcons.Visibility else JiibIcons.VisibilityOff,
                onClick = { onSetRevealHidden(!state.revealHidden) },
                intent = if (state.revealHidden) Intent.Accent else Intent.Neutral,
            ),
        ),
    )
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
            style = JiibType.screenTitle.toTextStyle(t),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.macros_empty_body),
            color = t.text2,
            style = JiibType.caption.toTextStyle(t),
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
            style = JiibType.body.toTextStyle(t),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Drop a trailing ".0" on a whole-number numeric value for clean display in the param field. */
private fun formatNumeric(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
