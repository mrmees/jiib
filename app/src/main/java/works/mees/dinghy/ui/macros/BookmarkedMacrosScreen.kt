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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The Bookmarked launcher (D-07, MACRO-01) — the everyday macro screen the drawer "Macros" tile opens.
 * Field is a grid of tappable tiles for ONLY the user's pinned macros ([MacroScreensState.
 * bookmarkedMacros]); tapping a tile opens its [MacroExecutionPopup] via [onRunMacro]. The Gutter
 * carries a `Manage macros` control → [onManage] (the System list) and a green **Back**.
 *
 * Empty (no bookmarks): the `No macros pinned` copy + a route to Manage. Capability-unavailable
 * ([MacroScreensState.unavailable]): the no-macros copy — never dead tiles.
 *
 * @param holder       the macro state holder.
 * @param onRunMacro   open the Execution popup for the tapped macro.
 * @param onManage     open the System (manage-visibility) list.
 * @param onBack       leave the macro surface.
 */
@Composable
fun BookmarkedMacrosScreen(
    holder: MacroHolder,
    onRunMacro: (MacroVm) -> Unit,
    onManage: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by holder.state.collectAsStateWithLifecycle()
    val t = LocalTokens.current

    ScreenScaffold(
        modifier = modifier.fillMaxSize(),
        focus = null,
        field = {
            Box(Modifier.fillMaxSize().padding(8.dp)) {
                when {
                    state.unavailable -> MacrosUnavailable(Modifier.fillMaxSize())
                    state.bookmarkedMacros.isEmpty() -> EmptyBookmarks(onManage, Modifier.fillMaxSize())
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.bookmarkedMacros, key = { it.name }) { macro ->
                            MacroTile(macro = macro, onClick = { onRunMacro(macro) })
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
                    label = "Manage macros",
                    onClick = onManage,
                    modifier = Modifier.weight(1f),
                    intent = Intent.Neutral,
                    symbol = "tune",
                )
            }
        },
    )
}

/** One launcher tile — a tappable outlined card showing the macro name in GeistMono. */
@Composable
private fun MacroTile(macro: MacroVm, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.accentLine), shape)
            .background(t.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = macro.name,
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(16f, t.fs).sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyBookmarks(onManage: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Column(
        modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "No macros pinned",
                    color = t.text,
                    fontFamily = Geist,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = fsSp(22f, t.fs).sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Pick the macros you use most in Manage macros, and they'll launch from here.",
                    color = t.text2,
                    fontFamily = Geist,
                    fontSize = fsSp(15f, t.fs).sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        OutlinedControl(
            label = "Manage macros",
            onClick = onManage,
            modifier = Modifier.fillMaxWidth(),
            intent = Intent.Accent,
            symbol = "tune",
        )
    }
}

@Composable
internal fun MacrosUnavailable(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Text(
            text = "This printer reports no gcode macros. Add gcode_macro entries in your config to use this screen.",
            color = t.text2,
            fontFamily = Geist,
            fontWeight = FontWeight.Medium,
            fontSize = fsSp(16f, t.fs).sp,
            textAlign = TextAlign.Center,
        )
    }
}
