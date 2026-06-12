package works.mees.dinghy.ui.console

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The read-only Console screen (CONS-02 / D-01..D-05). A **Field-only** `ScreenScaffold`:
 * `focus = null` (D-14) so the freed height goes to the scrollback; `gutter = null` (D-15) because
 * all actions live in the `FootButtonBar` at the foot of the Field.
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
 * Layout: `BoxWithConstraints` → `rememberUnitGrid` → `ScreenScaffold(focus = null, gutter = null)`.
 * Field = `ConsoleListView` (filling weight(1f)) + `FootButtonBar` (3 filter toggles + Back).
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
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Box(modifier.fillMaxSize().background(t.bg)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
            ScreenScaffold(
                focus = null,   // D-14: no Focus; full height goes to the scrollback
                field = {
                    // Pinned-height BoxWithConstraints wrapper — load-bearing (the Files scroll lesson):
                    // pins the RecyclerView so it can't over-measure and composite over the FootButtonBar.
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
                    // D-15: filter toggles + Back live in the FootButtonBar inside the field (gutter = null).
                    FootButtonBar(
                        uDp = grid.uDp,
                    ) {
                        // Back FIRST (accent — R5/R8, supersedes D-10's neutral-Back).
                        OutlinedControl(
                            label = "",
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Accent,
                            icon = DinghyIcons.Back,
                            contentDescription = stringResource(R.string.common_back),
                        )
                        // WR-05: each icon-only toggle gets its cd_* spoken label plus selected-state
                        // semantics — active-filter state is otherwise outline-color-only.
                        // Hide-temperatures toggle
                        OutlinedControl(
                            label = "",
                            onClick = onToggleTemps,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { selected = hideTemps },
                            intent = if (hideTemps) Intent.Accent else Intent.Neutral,
                            icon = DinghyIcons.HideTemps,
                            contentDescription = stringResource(R.string.cd_console_hide_temps),
                        )
                        // Hide-timelapse toggle
                        OutlinedControl(
                            label = "",
                            onClick = onToggleTimelapse,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { selected = hideTimelapse },
                            intent = if (hideTimelapse) Intent.Accent else Intent.Neutral,
                            icon = DinghyIcons.HideTimelapse,
                            contentDescription = stringResource(R.string.cd_console_hide_timelapse),
                        )
                        // Hide-prompts toggle
                        OutlinedControl(
                            label = "",
                            onClick = onTogglePrompt,
                            modifier = Modifier
                                .weight(1f)
                                .semantics { selected = hidePrompt },
                            intent = if (hidePrompt) Intent.Accent else Intent.Neutral,
                            icon = DinghyIcons.HidePrompts,
                            contentDescription = stringResource(R.string.cd_console_hide_prompts),
                        )
                    }
                },
                gutter = null,   // D-15: redesigned screen — all actions in FootButtonBar above
            )
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
            Text(
                text = stringResource(R.string.console_empty_title),
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(20f, t.fs).sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.console_empty_body),
                color = t.text2,
                fontFamily = Geist,
                fontSize = fsSp(15f, t.fs).sp, // 15.2-06: metadata floor 15sp ([[dinghy-font-sizes-too-small]])
                textAlign = TextAlign.Center,
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
            fontFamily = Geist,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(15f, t.fs).sp, // 15.2-06: metadata floor 15sp ([[dinghy-font-sizes-too-small]])
        )
    }
}
