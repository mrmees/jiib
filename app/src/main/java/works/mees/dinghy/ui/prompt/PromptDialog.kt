package works.mees.dinghy.ui.prompt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.prompt.PromptView
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

/**
 * The full-screen Macro Prompt overlay (PROMPT-02, 12-04). An AppShell-level overlay — NOT a Dest (D-05);
 * it is ALWAYS full-screen (Dinghy clamps the protocol `prompt_size` to full-screen, D-06) and occludes
 * the screen with an OPAQUE `Box(t.bg)`, following the [works.mees.dinghy.ui.macros.MacroExecutionPopup]
 * precedent. Built from the design-system primitives:
 *
 *  - **Header band** — the `prompt_begin` title at 28sp Geist SemiBold (maxLines 3, ellipsis). Empty but
 *    reserved when the title is blank (UI-SPEC degenerate states).
 *  - **Field** — a weighted, [verticalScroll]able column rendering [PromptView.items] with the 16dp default
 *    gap (author content count is unbounded → scrolls rather than overflowing, T-12-16). When `items` is
 *    empty, a quiet "This prompt has no content." placeholder. A [SeverityToast] slot surfaces
 *    [errorText] (button-rejected) and the in-flight "Sending…" info.
 *  - **Footer action bar** — a weighted [Row] of [PromptView.footerButtons] (each fires
 *    `onFooterButton(footerIndex)`) PLUS an ALWAYS-present close control (the "always an exit" guarantee,
 *    accent-tinted, `close` symbol) → `onClose()` (emits `prompt_end` in 12-05).
 *
 * ## Button-index contract
 * Content buttons fire `onButton(buttonIndex)` where `buttonIndex` is the DEPTH-FIRST
 * [flattenContentButtons] position (NOT a flat `items` index) — so a button nested in a `row`/`button_group`
 * dispatches the right gcode. Footer buttons are a SEPARATE `footerButtons` sequence. The close control is
 * in NEITHER list. (The 12-05 dispatch resolves gcode via the SAME `flattenContentButtons()[buttonIndex]`.)
 *
 * This overlay is parameter-driven (no AppShell/dispatcher coupling — that hoist is 12-05) so it composes
 * standalone in a preview / UI test. Buttons do NOT auto-close.
 *
 * @param view          the 6-key conformance view from the reducer (the render source).
 * @param httpBase      the Moonraker base URL for `prompt_image` loads (Files-style plumbing).
 * @param onButton      fired with a CONTENT-button `buttonIndex` (the `flattenContentButtons` position).
 * @param onFooterButton fired with a `footerButtons` index (a separate sequence).
 * @param onClose       fired by the always-present close control (emits `prompt_end`).
 * @param errorText     a button-rejection toast message (null = none).
 * @param inFlight      true while a button gcode is in flight (shows the "Sending…" info toast).
 */
@Composable
fun PromptDialog(
    view: PromptView,
    httpBase: String,
    onButton: (Int) -> Unit,
    onFooterButton: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    errorText: String? = null,
    inFlight: Boolean = false,
) {
    val t = LocalTokens.current

    // A fresh depth-first counter PER composition so content-button indices match flattenContentButtons.
    // (Recomputed every recomposition — the walk is deterministic over `view.items`.)
    val counter = ButtonCounter()

    Box(modifier.fillMaxSize().background(t.bg)) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header band — always present; empty-but-reserved when the title is blank.
            Text(
                text = view.title,
                color = t.text,
                style = DinghyType.screenTitle.toTextStyle(t),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            // Field — scrollable content flow (unbounded author content scrolls, T-12-16).
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (view.items.isEmpty()) {
                    Text(
                        text = "This prompt has no content.",
                        color = t.text2,
                        style = DinghyType.caption.toTextStyle(t),
                    )
                } else {
                    for (item in view.items) {
                        PromptContentItem(
                            item = item,
                            counter = counter,
                            onButton = onButton,
                            httpBase = httpBase,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // Severity slots — button rejection then in-flight info (mirrors MacroExecutionPopup copy).
            errorText?.let { msg ->
                SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth())
            }
            if (inFlight) {
                SeverityToast(
                    Severity.Info,
                    "Sending… waiting for the printer.",
                    Modifier.fillMaxWidth(),
                )
            }

            // Footer action bar — the protocol footer buttons + an ALWAYS-present close (the exit guarantee).
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                view.footerButtons.forEachIndexed { footerIndex, fb ->
                    PromptButtonControl(
                        label = fb.label,
                        style = fb.style,
                        onClick = { onFooterButton(footerIndex) },
                        modifier = Modifier.weight(1f),
                    )
                }
                PromptCloseControl(
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The always-present close control (UI-SPEC "always an exit"): an accent-tinted outlined ≥64dp control
 * with a `close` Material Symbol + "Close" label → [onClick] (emits `prompt_end`). In NEITHER the content
 * nor footer button index list.
 */
@Composable
private fun PromptCloseControl(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    works.mees.dinghy.designsystem.control.OutlinedControl(
        label = "Close",
        onClick = onClick,
        intent = works.mees.dinghy.designsystem.control.Intent.Accent,
        symbol = "close",
        modifier = modifier,
    )
}
