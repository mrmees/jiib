package works.mees.jiib.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.jiib.R
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.AppFont
import works.mees.jiib.theme.FontCatalog
import works.mees.jiib.theme.FontKind
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.previewTextStyle

/**
 * ONE parameterized screen for both the interface-font and data-font pickers (DRY).
 * Wired as two separate [works.mees.jiib.ui.route.NavDest] routes in AppShell, each passing
 * a different [FontKind].
 *
 * Selection applies live app-wide (AppContainer.setInterfaceFont / setDataFont route through
 * DataStore writeScope — never a composition scope, [[dinghy-compose-write-scope-cancellation]]).
 * The picker's own chrome immediately re-renders in the chosen face — that IS the live preview.
 */
@Composable
fun FontPickerScreen(
    container: AppContainer,
    kind: FontKind,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected by (if (kind == FontKind.Ui) container.interfaceFont else container.dataFont)
        .collectAsStateWithLifecycle(if (kind == FontKind.Ui) FontCatalog.DEFAULT_UI else FontCatalog.DEFAULT_DATA)
    val fonts = if (kind == FontKind.Ui) FontCatalog.ui else FontCatalog.data
    val titleRes = if (kind == FontKind.Ui) R.string.settings_interface_font else R.string.settings_data_font
    val icon: JiibIcon = if (kind == FontKind.Ui) JiibIcons.Serif else JiibIcons.DataFont
    val onSelect: (AppFont) -> Unit = if (kind == FontKind.Ui) container::setInterfaceFont else container::setDataFont

    // Mirror ThemeScreen: derive isPrinting + onEmergencyStop from container and thread into FocusFrame.
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused

    FontPickerContent(
        title = stringResource(titleRes),
        icon = icon,
        fonts = fonts,
        selected = selected,
        onSelect = onSelect,
        onBack = onBack,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        modifier = modifier,
    )
}

/**
 * Stateless content seam (PREVIEW_AND_TOKENS preview-first LAW) — pure inputs, no AppContainer,
 * so a @Preview matrix can drive every font + theme state without a live session.
 */
@Composable
internal fun FontPickerContent(
    title: String,
    icon: JiibIcon,
    fonts: List<AppFont>,
    selected: AppFont,
    onSelect: (AppFont) -> Unit,
    onBack: () -> Unit,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = title,
                    icon = icon,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxSize(),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    // The Focus body IS the live preview: the selected font's name, rendered in its own
                    // face. Selection updates the whole app immediately (tokenized fonts) so the chrome
                    // re-renders too — no separate preview block needed.
                    Box(
                        Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = selected.displayName,
                            style = selected.previewTextStyle(t),
                            color = t.text,
                        )
                    }
                }
            },
            field = {
                ListBlock(Modifier.weight(1f)) {
                    items(fonts, key = { it.id }) { font ->
                        ListRow(
                            selected = font.id == selected.id,
                            onClick = { onSelect(font) },
                            uDp = grid.uDp,
                        ) {
                            Text(
                                text = font.displayName,
                                style = font.previewTextStyle(t),
                                color = t.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            icon = JiibIcons.Back,
                            onClick = onBack,
                            intent = Intent.Accent,
                        ),
                    ),
                )
            },
        )
    }
}
