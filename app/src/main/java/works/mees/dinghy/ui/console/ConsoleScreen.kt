package works.mees.dinghy.ui.console

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The read-only Console screen (CONS-02 / D-01..D-05). A **Field-only** `ScreenScaffold`: Focus is
 * omitted so the freed height goes to the scrollback ("fill the usable space"). The Field IS the
 * [ConsoleListView] scrollback; the Gutter carries the three opt-in noise filters + a green **Back**.
 *
 * D-04 (LOAD-BEARING): the holder stores the RAW lines; this screen applies [ConsoleFilters] at
 * RENDER ONLY — toggling a filter off re-reveals the hidden lines (the raw stream is never starved).
 *
 * D-01: read-only — NO TextField, NO keyboard, NO send affordance.
 *
 * NOTE: the global swipe-up App Drawer gesture is suppressed for this screen in `AppShell` (08-07);
 * the green Back here is the explicit exit.
 *
 * @param holder the [ConsoleHolder] exposing the RAW [ConsoleLine] state.
 * @param onBack dismiss the screen (Gutter Back).
 * @param backfillFailed when true, surface the "History unavailable" notice without blanking the list.
 */
@Composable
fun ConsoleScreen(
    holder: ConsoleHolder,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backfillFailed: Boolean = false,
) {
    val t = LocalTokens.current
    val rawLines by holder.state.collectAsStateWithLifecycle()

    // The three opt-in noise filters — default OFF (D-03). Local UI state; applied at render only.
    var hideTemps by remember { mutableStateOf(false) }
    var hideTimelapse by remember { mutableStateOf(false) }
    var hidePrompt by remember { mutableStateOf(false) }

    val filtered = ConsoleFilters.apply(
        lines = rawLines,
        hideTemperatures = hideTemps,
        hideTimelapse = hideTimelapse,
        hidePrompt = hidePrompt,
    )

    Box(modifier.fillMaxSize().background(t.bg)) {
        ScreenScaffold(
            focus = null,
            field = {
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                        // .height(maxHeight) is load-bearing — pins the RecyclerView so it can't
                        // over-measure and composite over the gutter (the Files scroll lesson, D-05).
                        ConsoleListView(
                            lines = filtered,
                            modifier = Modifier.fillMaxWidth().height(maxHeight),
                        )
                        when {
                            rawLines.isEmpty() && !backfillFailed -> EmptyConsole(Modifier.matchParentSize())
                            backfillFailed -> BackfillFailedNotice(Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterToggle(
                        symbol = "thermostat",
                        label = "Hide temperatures",
                        active = hideTemps,
                        onToggle = { hideTemps = !hideTemps },
                        modifier = Modifier.weight(1f),
                    )
                    FilterToggle(
                        symbol = "videocam",
                        label = "Hide Timelapse",
                        active = hideTimelapse,
                        onToggle = { hideTimelapse = !hideTimelapse },
                        modifier = Modifier.weight(1f),
                    )
                    FilterToggle(
                        symbol = "chat_bubble",
                        label = "Hide prompt commands",
                        active = hidePrompt,
                        onToggle = { hidePrompt = !hidePrompt },
                        modifier = Modifier.weight(1f),
                    )
                    BackControl(
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                    )
                }
            },
        )
    }
}

/**
 * One opt-in filter toggle: ≥64dp, 2px outline. Active edge = `t.accentLine` (D-03); inactive =
 * neutral `t.outline`. Distinct glyph per toggle (icon-no-repeat law).
 */
@Composable
private fun FilterToggle(
    symbol: String,
    label: String,
    active: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val edge = if (active) t.accentLine else t.outline
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, edge), shape)
            .background(if (active) t.surface2 else t.surface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MaterialSymbol(symbol, tint = if (active) t.accent else t.text2, sizeSp = fsSp(22f, t.fs))
            Text(
                text = label,
                color = if (active) t.text else t.text2,
                fontFamily = Geist,
                fontWeight = FontWeight.Medium,
                fontSize = fsSp(13f, t.fs).sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** The neutral Back exit (D-10: plain nav spends no safety color → `t.outline`, matching Move). */
@Composable
private fun BackControl(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.outline), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            MaterialSymbol("arrow_back", tint = t.outline, sizeSp = fsSp(22f, t.fs))
            Text(
                text = "Back",
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(13f, t.fs).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Empty-state: no lines yet (fresh connect before backfill). UI-SPEC copy, verbatim. */
@Composable
private fun EmptyConsole(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(modifier.background(t.bg.copy(alpha = 0.86f)).padding(16.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Console is quiet",
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(20f, t.fs).sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Printer responses appear here. Run a command or macro and the reply shows up live.",
                color = t.text2,
                fontFamily = Geist,
                fontSize = fsSp(15f, t.fs).sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Backfill-failed notice — keep the scrollback stable, do not blank the history. UI-SPEC copy. */
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
            text = "History unavailable. Check the printer connection — live responses will still appear.",
            color = t.heat,
            fontFamily = Geist,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(14f, t.fs).sp,
        )
    }
}
