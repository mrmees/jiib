package works.mees.dinghy.ui.macros

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The System macro list (manage visibility — D-06 / MACRO-03). Field is a scrollable list of every
 * discovered macro the holder exposes ([MacroScreensState.visibleMacros] — already underscore-filtered
 * by the holder unless reveal is on). Each row toggles whether the macro is pinned to the Bookmarked
 * launcher via [onToggleBookmark]; a selected row reads `--accent` (checkmark + outline).
 *
 * A `Show hidden` Gutter toggle drives [onSetRevealHidden] (underscore-prefixed helper macros are
 * hidden by default — MACRO-03). The Gutter also carries a green **Back**. (Drawer-swipe suppression
 * on this finger-scrollable list is wired in 08-07.)
 *
 * @param holder            the macro state holder.
 * @param onToggleBookmark  flip a macro's pinned state (wired to MacroPrefs.toggleBookmark in 08-07).
 * @param onSetRevealHidden persist the reveal toggle (wired to MacroPrefs.setRevealHidden in 08-07).
 * @param onBack            leave the System list (back to the launcher).
 */
@Composable
fun SystemMacrosScreen(
    holder: MacroHolder,
    onToggleBookmark: (String) -> Unit,
    onSetRevealHidden: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by holder.state.collectAsStateWithLifecycle()
    val t = LocalTokens.current

    ScreenScaffold(
        modifier = modifier.fillMaxSize(),
        focus = null,
        field = {
            Column(
                Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.unavailable) {
                    MacrosUnavailable(Modifier.fillMaxSize())
                } else {
                    Text(
                        text = "Underscore-prefixed helper macros are hidden. Show hidden to reveal them.",
                        color = t.text2,
                        fontFamily = Geist,
                        fontSize = fsSp(14f, t.fs).sp,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.visibleMacros, key = { it.name }) { macro ->
                            MacroSelectRow(
                                macro = macro,
                                onToggle = { onToggleBookmark(macro.name) },
                            )
                        }
                    }
                }
            }
        },
        gutter = {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedControl(
                    label = "Back",
                    onClick = onBack,
                    modifier = Modifier.weight(1f),
                    intent = Intent.Neutral, // D-10: plain nav spends no safety color (matches Move).
                    symbol = "arrow_back",
                )
                OutlinedControl(
                    label = "Show hidden",
                    onClick = { onSetRevealHidden(!state.revealHidden) },
                    modifier = Modifier.weight(1f),
                    intent = if (state.revealHidden) Intent.Accent else Intent.Neutral,
                    symbol = if (state.revealHidden) "visibility" else "visibility_off",
                )
            }
        },
    )
}

/** One System-list row: macro name + a check/select control; selected = accent checkmark + outline. */
@Composable
private fun MacroSelectRow(macro: MacroVm, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val edge = if (macro.isBookmarked) t.accentLine else t.outline
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, edge), shape)
            .background(if (macro.isBookmarked) t.surface2 else t.surface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = macro.name,
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(16f, t.fs).sp,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // The check/select affordance — accent filled glyph when pinned, neutral outline when not.
        Box(
            Modifier.size(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            MaterialSymbol(
                name = if (macro.isBookmarked) "check_circle" else "radio_button_unchecked",
                tint = if (macro.isBookmarked) t.accent else t.text3,
                sizeSp = fsSp(24f, t.fs),
            )
        }
    }
}
