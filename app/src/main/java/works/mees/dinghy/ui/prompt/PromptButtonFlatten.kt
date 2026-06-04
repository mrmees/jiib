package works.mees.dinghy.ui.prompt

import works.mees.dinghy.prompt.PromptItem
import works.mees.dinghy.prompt.PromptItemType
import works.mees.dinghy.prompt.PromptView

/**
 * THE single shared button-index contract for the Macro Prompt Protocol (12-04 / 12-05). The load-bearing
 * cross-plan agreement: a content button's dispatch index MUST be the SAME on both the renderer
 * (12-04 click-wiring) and the AppShell dispatch lookup (12-05 gcode resolution), or a button nested in a
 * `row`/`button_group` would fire the WRONG gcode (the silent-runtime-defect this helper guards).
 *
 * [flattenContentButtons] walks [PromptView.items] DEPTH-FIRST, descending into `row`/`button_group`
 * children, visiting CONTENT BUTTONS ONLY (text/markup/image items and the container items themselves are
 * skipped), and returns them in that stable order. The Nth element's index N is the button's `buttonIndex`:
 *  - the renderer assigns each content button this index by the SAME walk and fires `onButton(buttonIndex)`,
 *  - 12-05 resolves the gcode via `view.flattenContentButtons()[buttonIndex].gcode`.
 *
 * FOOTER buttons are a SEPARATE sequence ([PromptView.footerButtons] by `footerIndex`) — they are NOT in
 * this list and NOT depth-first interleaved with content. The CLOSE control is in NEITHER list (its own
 * `onClose()` → `prompt_end`).
 *
 * PURE Kotlin — NO Compose import — so 12-05's AppShell can call it off the UI path.
 */
fun PromptView.flattenContentButtons(): List<PromptButton> {
    val out = mutableListOf<PromptButton>()
    fun visit(item: PromptItem) {
        when (item.type) {
            PromptItemType.BUTTON -> out.add(item.toPromptButton())
            PromptItemType.ROW, PromptItemType.BUTTON_GROUP -> item.children.forEach(::visit)
            else -> Unit // text / markup / image — never a content button.
        }
    }
    items.forEach(::visit)
    return out
}

/**
 * The resolved content-button shape: the renderer's label/style + the gcode 12-05 dispatches. A thin,
 * Compose-free projection of a `button` [PromptItem] (label/gcode/style are all non-null on a real button
 * item produced by the reducer; defensive empties keep the helper total over a malformed item).
 */
data class PromptButton(
    val label: String,
    val gcode: String,
    val style: works.mees.dinghy.prompt.PromptStyle,
)

private fun PromptItem.toPromptButton(): PromptButton = PromptButton(
    label = label.orEmpty(),
    gcode = gcode.orEmpty(),
    style = style ?: works.mees.dinghy.prompt.PromptStyle.SECONDARY,
)
