package works.mees.jiib.ui.console

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.jiib.R
import works.mees.jiib.designsystem.components.FocusFrame

import works.mees.jiib.designsystem.components.FootAction
import androidx.compose.ui.text.style.TextOverflow
import works.mees.jiib.theme.compose.FocusText
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.RegisteredRegion
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * The read-only Console screen (CONS-02 / D-01..D-05). A SINGLE Focus pane: one [FocusFrame]
 * (outline + mandatory 1U header carrying the e-stop morph) whose content IS the console feed
 * ([ConsoleListView], edge-to-edge via `contentInset = 0`), with a [FootButtonBar] (Back + the three
 * noise-filter toggles) beneath it. Built as a plain `Column`, NOT a two-region `ScreenScaffold`:
 * the console is a single-pane special-use screen, so it renders the same stacked layout in portrait
 * and landscape (the old `ScreenScaffold(focus, field)` split it side-by-side in landscape).
 *
 * ## Toolkit: Views (spike verdict — D-02)
 * The 25-01 spike returned a ~8× p90 regression (73.35 ms vs 9.26 ms baseline) for a Compose
 * `LazyColumn` under live-churn on Adreno 320. `ConsoleListView` (RecyclerView/Views with
 * `stackFromEnd` + the `isSingleAppend`/`isAppendEvict` incremental paths) is RETAINED and visually
 * conformed to the jiib design-kit. A class-equivalent exception is recorded in COMPONENTS.md.
 *
 * ## Filter design (D-15)
 * The 3 filter toggles (Hide-temperatures / Hide-timelapse / Hide-prompts) + Back live in the
 * `FootButtonBar` as always-visible `OutlinedControl` toggles — NOT a Field-takeover, NOT a
 * separate route. Active filter = `Intent.Accent` (accentLine outline + `t.surface2` fill via
 * OutlinedControl's filled convention); inactive = `Intent.Neutral`.
 *
 * ## Raw-holder invariant (D-04 / D-15 — LOAD-BEARING)
 * [ConsoleHolder] stores the RAW unfiltered lines. [ConsoleFilters.apply] is called AT RENDER
 * only — toggling a filter OFF re-reveals the previously-hidden lines from the same raw source.
 * NEVER move the filter call into the holder.
 *
 * D-01: read-only — NO TextField, NO keyboard, NO send affordance.
 *
 * NOTE: the global swipe-up App Drawer gesture is suppressed for this screen in `AppShell` (08-07);
 * the Back control in the FootButtonBar is the explicit exit.
 *
 * @param holder the [ConsoleHolder] exposing the RAW [ConsoleLine] state.
 * @param onBack dismiss the screen.
 * @param backfillFailed when true, surface the "History unavailable" notice without blanking the list.
 */
@Composable
fun ConsoleScreen(
    holder: ConsoleHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backfillFailed: Boolean = false,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
) {
    val rawLines by holder.state.collectAsStateWithLifecycle()

    // The three opt-in noise filters — default OFF (D-03). Local UI state; applied at render only.
    var hideTemps by remember { mutableStateOf(false) }
    var hideTimelapse by remember { mutableStateOf(false) }
    var hidePrompt by remember { mutableStateOf(false) }

    // D-04/D-15 (LOAD-BEARING): filter is applied HERE off rawLines — never in the holder.
    val filtered = ConsoleFilters.apply(
        lines = rawLines,
        hideTemperatures = hideTemps,
        hideTimelapse = hideTimelapse,
        hidePrompt = hidePrompt,
    )

    ConsoleContent(
        lines = filtered,
        rawLineCount = rawLines.size,
        backfillFailed = backfillFailed,
        hideTemps = hideTemps,
        hideTimelapse = hideTimelapse,
        hidePrompt = hidePrompt,
        onToggleTemps = { hideTemps = !hideTemps },
        onToggleTimelapse = { hideTimelapse = !hideTimelapse },
        onTogglePrompt = { hidePrompt = !hidePrompt },
        onBack = onBack,
        isPrinting = isPrinting,
        onEmergencyStop = onEmergencyStop,
        modifier = modifier,
    )
}

/**
 * Stateless preview overload. Receives pre-filtered [lines] (the live overload computes filtered
 * from raw; the preview seam supplies them directly) and all toggle states + callbacks with no-op
 * defaults. No holder, no [collectAsStateWithLifecycle], no live Moonraker.
 *
 * @param lines the ALREADY-FILTERED lines to display (caller is responsible for filtering).
 * @param rawLineCount total raw line count (used to detect the empty-before-backfill state).
 * @param backfillFailed surface the "History unavailable" notice when true.
 * @param hideTemps current state of the hide-temperatures filter toggle.
 * @param hideTimelapse current state of the hide-timelapse filter toggle.
 * @param hidePrompt current state of the hide-prompts filter toggle.
 */
@Composable
fun ConsoleScreen(
    lines: List<ConsoleLine>,
    modifier: Modifier = Modifier,
    rawLineCount: Int = lines.size,
    backfillFailed: Boolean = false,
    hideTemps: Boolean = false,
    hideTimelapse: Boolean = false,
    hidePrompt: Boolean = false,
    onToggleTemps: () -> Unit = {},
    onToggleTimelapse: () -> Unit = {},
    onTogglePrompt: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    ConsoleContent(
        lines = lines,
        // WR-01: pass the count straight through — ConsoleContent only needs "are there raw lines
        // at all" to distinguish fresh-connect-empty from all-lines-filtered-out.
        rawLineCount = rawLineCount,
        backfillFailed = backfillFailed,
        hideTemps = hideTemps,
        hideTimelapse = hideTimelapse,
        hidePrompt = hidePrompt,
        onToggleTemps = onToggleTemps,
        onToggleTimelapse = onToggleTimelapse,
        onTogglePrompt = onTogglePrompt,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The shared rendering body. Both overloads delegate here.
 *
 * Layout: `Box` → `BoxWithConstraints` → `rememberUnitGrid` → `Column` → `FocusFrame` (weight(1f))
 * + `FootButtonBar` (3 filter toggles + Back). `ConsoleListView` fills the frame edge-to-edge.
 *
 * **ConsoleListView class-equivalent exception (D-02 / 25-SPIKE.md):** the RecyclerView Views
 * scrollback is retained (not replaced with a Compose `ListBlock`/`ListRow`) because the 25-01 spike
 * measured a ~8× p90 frame-time regression under live-churn on Adreno 320. COMPONENTS.md records
 * this surface as a "class-equivalent (Views)" exception — the ListRow-equivalent row styling is
 * applied via [ConsoleRowsAdapter] token routing, not a Compose `ListRow`. See 25-SPIKE.md.
 */
@Composable
private fun ConsoleContent(
    lines: List<ConsoleLine>,
    rawLineCount: Int,
    backfillFailed: Boolean,
    hideTemps: Boolean,
    hideTimelapse: Boolean,
    hidePrompt: Boolean,
    onToggleTemps: () -> Unit,
    onToggleTimelapse: () -> Unit,
    onTogglePrompt: () -> Unit,
    onBack: () -> Unit,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Box(modifier.fillMaxSize().background(t.bg)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
            // One Focus pane (outline + 1U header carrying the e-stop morph) holding the feed,
            // with the FootButtonBar beneath it. NOT a two-region ScreenScaffold — the console is a
            // single-pane special-use screen; a plain Column is the honest structure and renders the
            // same in portrait and landscape (no side-by-side split).
            RegisteredRegion(Modifier.fillMaxSize()) {
                FocusFrame(
                    title = stringResource(R.string.cd_launcher_console),
                    icon = JiibIcons.LauncherConsole,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                    contentInset = 0.dp, // feed fills the frame edge-to-edge (rows carry their own padding)
                ) {
                    // Pinned-height BoxWithConstraints wrapper — load-bearing (the Files scroll lesson):
                    // pins the RecyclerView so it can't over-measure and composite past the frame.
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                        // .height(maxHeight) is load-bearing — see FileListView / ConsoleListView patterns.
                        ConsoleListView(
                            lines = lines,
                            modifier = Modifier.fillMaxWidth().height(maxHeight),
                        )
                        when {
                            // rawLineCount (not lines.size): "raw lines exist but every one is
                            // filtered out" must NOT show the fresh-connect empty overlay (WR-01).
                            rawLineCount == 0 && !backfillFailed ->
                                EmptyConsole(Modifier.matchParentSize())
                            backfillFailed ->
                                BackfillFailedNotice(Modifier.fillMaxWidth())
                        }
                    }
                }
                // D-15: filter toggles + Back live in the FootButtonBar beneath the Focus pane.
                // 4 actions (Back + 3 toggles) → icon-only; selected semantics pass through
                // FootAction.modifier. Labels are non-blank (required) but blanked at render time.
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        // Back FIRST (accent — R5/R8, supersedes D-10's neutral-Back).
                        FootAction(
                            label = stringResource(R.string.common_back),
                            icon = JiibIcons.Back,
                            onClick = onBack,
                            intent = Intent.Accent,
                            contentDescription = stringResource(R.string.common_back),
                        ),
                        // WR-05: each icon-only toggle gets its cd_* spoken label plus selected-state
                        // semantics — active-filter state is otherwise outline-color-only.
                        // Hide-temperatures toggle
                        FootAction(
                            label = stringResource(R.string.cd_console_hide_temps),
                            icon = JiibIcons.HideTemps,
                            onClick = onToggleTemps,
                            intent = if (hideTemps) Intent.Accent else Intent.Neutral,
                            contentDescription = stringResource(R.string.cd_console_hide_temps),
                            modifier = Modifier.semantics { selected = hideTemps },
                        ),
                        // Hide-timelapse toggle
                        FootAction(
                            label = stringResource(R.string.cd_console_hide_timelapse),
                            icon = JiibIcons.HideTimelapse,
                            onClick = onToggleTimelapse,
                            intent = if (hideTimelapse) Intent.Accent else Intent.Neutral,
                            contentDescription = stringResource(R.string.cd_console_hide_timelapse),
                            modifier = Modifier.semantics { selected = hideTimelapse },
                        ),
                        // Hide-prompts toggle
                        FootAction(
                            label = stringResource(R.string.cd_console_hide_prompts),
                            icon = JiibIcons.HidePrompts,
                            onClick = onTogglePrompt,
                            intent = if (hidePrompt) Intent.Accent else Intent.Neutral,
                            contentDescription = stringResource(R.string.cd_console_hide_prompts),
                            modifier = Modifier.semantics { selected = hidePrompt },
                        ),
                    ),
                )
            }
        }
    }
}

/** Empty-state: no lines yet (fresh connect before backfill). */
@Composable
private fun EmptyConsole(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(modifier.background(t.bg.copy(alpha = 0.86f)).padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        ) {
            FocusText(
                text = stringResource(R.string.console_empty_title),
                role = JiibType.focusHeader,
                t = t,
                color = t.text,
                modifier = Modifier.fillMaxWidth(),
                maxHeightU = 1f,
            )
            FocusText(
                text = stringResource(R.string.console_empty_body),
                role = JiibType.caption,
                t = t,
                color = t.text2,
                modifier = Modifier.fillMaxWidth(),
                maxHeightU = 2f,
            )
        }
    }
}

/** Backfill-failed notice — keep the scrollback stable, do not blank the history. */
@Composable
private fun BackfillFailedNotice(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(
        modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(t.rCtrl))
            .background(t.surface2)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        Text(
            text = stringResource(R.string.console_backfill_failed),
            color = t.heat,
            style = JiibType.caption.toTextStyle(t),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
