package works.mees.dinghy.ui.macros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.MacroInvocation
import works.mees.dinghy.command.MacroParamRejected
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.designsystem.NumpadPage
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
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
 * Generously-wide numeric range for a macro NumpadPage. Macro bodies never declare their own range, so
 * this is intentionally wide — NumpadPage is the sole clamp owner (S4). A printer-side range violation
 * surfaces as a DispatchEvent.Failure toast, not blocked here.
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
 *  - [MacroFieldMode.ParamEntry] — per-param Field-takeover with NumpadPage / TokenTextField (D-12)
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
    }
    FootButtonBar(
        uDp = uDp,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        OutlinedControl(
            label = stringResource(R.string.macros_foot_back),
            onClick = onBack,
            modifier = Modifier.weight(1f),
            intent = Intent.Neutral,
            icon = DinghyIcons.Back,
        )
        OutlinedControl(
            label = stringResource(R.string.macros_foot_manage),
            onClick = onManage,
            modifier = Modifier.weight(1f),
            intent = Intent.Neutral,
            icon = DinghyIcons.ManageMacros,
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

    // Toast message for local rejection (forbidden char) or dispatcher failure (printer rejection).
    var toast by remember(macro.name) { mutableStateOf<String?>(null) }

    // Which numeric param (if any) has its NumpadPage sub-page open — null = show the param list.
    var numpadParam by remember(macro.name) { mutableStateOf<MacroParam?>(null) }

    // Collect DispatchEvent.Failure for our busy key → toast (PRIM-05 / T-25-05-01).
    LaunchedEffect(dispatcher, macro.name) {
        dispatcher?.events?.collect { event ->
            if (event is DispatchEvent.Failure && event.key == busyKey) {
                toast = "${macro.name} was rejected: ${event.message}"
            }
        }
    }

    fun execute() {
        val gcode = try {
            // T-25-05-01: buildTyped is the REQUIRED V5 sanitizer path — never bypass with scriptParams directly.
            MacroInvocation.buildTyped(
                macro.name,
                params.map { p -> Triple(p.name, values[p.name].orEmpty(), p.isNumeric) },
            )
        } catch (e: MacroParamRejected) {
            toast = "${macro.name} was rejected: ${e.reason}"
            return
        }
        dispatcher?.dispatch(
            key = busyKey,                             // PRIM-05: per-macro busy key
            method = JsonRpcMethods.GCODE_SCRIPT,
            params = PrinterCommands.scriptParams(gcode),
        )
        onExecuted()
    }

    // NumpadPage Field-takeover: while numpadParam != null the ENTIRE field is replaced.
    val editing = numpadParam
    if (editing != null) {
        NumpadPage(
            label = editing.name,
            initial = values[editing.name].orEmpty().toDoubleOrNull() ?: 0.0,
            range = MACRO_NUMERIC_RANGE,
            onCancel = { numpadParam = null },
            onSet = { committed ->
                values[editing.name] = formatNumeric(committed)
                numpadParam = null
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )
        // Foot is not shown while NumpadPage owns the field — NumpadPage provides its own onCancel/onSet
        return
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
                // Numeric param → tappable row opens NumpadPage (keyboard-free — D-12)
                ListRow(
                    selected = false,
                    onClick = { numpadParam = param },
                    uDp = uDp,
                    trailingContent = {
                        Text(
                            text = values[param.name].orEmpty().ifEmpty { "—" },
                            color = t.text,
                            fontFamily = GeistMono,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = fsSp(18f, t.fs).sp,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    },
                ) {
                    Text(
                        text = param.name,
                        color = t.text2,
                        fontFamily = Geist,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fsSp(15f, t.fs).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                    )
                }
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
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        OutlinedControl(
            label = stringResource(R.string.macros_foot_back),
            onClick = onBack,
            modifier = Modifier.weight(1f),
            intent = Intent.Neutral,
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
            intent = Intent.Accent,
            icon = DinghyIcons.ExecuteMacro,
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
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        OutlinedControl(
            label = stringResource(R.string.macros_foot_back),
            onClick = onBack,
            modifier = Modifier.weight(1f),
            intent = Intent.Neutral,
            icon = DinghyIcons.Back,
        )
        // Show hidden toggle: raw symbol strings (verbatim from old SystemMacrosScreen — pre-registry glyphs).
        // These are preserved "as-is" — not a new icon choice, just the pre-existing code.
        OutlinedControl(
            label = stringResource(R.string.macros_foot_show_hidden),
            onClick = { onSetRevealHidden(!state.revealHidden) },
            modifier = Modifier.weight(1f),
            intent = if (state.revealHidden) Intent.Accent else Intent.Neutral,
            symbol = if (state.revealHidden) "visibility" else "visibility_off",
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

/** Drop a trailing ".0" on a whole-number numeric value (matches NumpadPage display). */
private fun formatNumeric(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
