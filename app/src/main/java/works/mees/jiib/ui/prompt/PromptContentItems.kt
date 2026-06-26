package works.mees.jiib.ui.prompt

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import works.mees.jiib.prompt.MarkupNode
import works.mees.jiib.prompt.PromptAlign
import works.mees.jiib.prompt.PromptItem
import works.mees.jiib.prompt.PromptItemType
import works.mees.jiib.prompt.PromptStyle
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * The per-item renderers for the prompt Field (12-04 Task 2). A [PromptItem] is rendered by [type]:
 *  - `text`         → plain [Text] at `--text` 18sp Geist (UI-SPEC body tier).
 *  - `markup`       → [PromptMarkupText] (the AST parsed from the item's raw markup), author-hex carve-out.
 *  - `image`        → [PromptImageItem] (Coil-bounded, alt-text fallback).
 *  - `button`       → an outlined ≥64dp control whose outline+label tint is `promptStyleColor(style)`,
 *                     filling its cell, firing `onButton(buttonIndex)`.
 *  - `row`/`button_group` → equal-width cells (`Row` of `weight(1f)` children, centered).
 *
 * ## The button-index contract
 * Content buttons are visited DEPTH-FIRST (descending into `row`/`button_group`), each assigned a stable
 * sequential `buttonIndex` 0,1,2,… in that order — the SAME order [flattenContentButtons] produces. The
 * renderer threads a single [ButtonCounter] so a button nested in a container gets its flattened index
 * (NOT a flat `items` index), guaranteeing the renderer and 12-05's dispatch resolve the SAME gcode.
 */

/** A shared depth-first running index so nested-container buttons get the same index as the flatten helper. */
internal class ButtonCounter(var next: Int = 0)

/**
 * Render one top-level [PromptItem] into the Field. Honors the item's [PromptItem.align] (left/right;
 * `null` = center default → no [TextAlign]/alignment field). Threads [counter] so any content button it
 * (or its container children) renders gets the correct depth-first [PromptButtonFlatten] index.
 */
@Composable
internal fun PromptContentItem(
    item: PromptItem,
    counter: ButtonCounter,
    onButton: (Int) -> Unit,
    httpBase: String,
    modifier: Modifier = Modifier,
) {
    val align = item.align
    when (item.type) {
        PromptItemType.TEXT -> PromptTextItem(item.text.orEmpty(), align, modifier.fillMaxWidth())
        PromptItemType.MARKUP -> PromptMarkupItem(item.markup.orEmpty(), align, modifier.fillMaxWidth())
        PromptItemType.IMAGE -> PromptImageItem(
            path = item.path.orEmpty(),
            alt = item.alt.orEmpty(),
            scale = item.scale,
            httpBase = httpBase,
            modifier = modifier,
        )
        PromptItemType.BUTTON -> {
            // A standalone (top-level) content button fills the full content width of its Field row.
            val index = counter.next++
            PromptButtonControl(
                label = item.label.orEmpty(),
                style = item.style ?: PromptStyle.SECONDARY,
                onClick = { onButton(index) },
                modifier = modifier.fillMaxWidth(),
            )
        }
        PromptItemType.ROW, PromptItemType.BUTTON_GROUP ->
            PromptContainerRow(item.children, counter, onButton, httpBase, modifier.fillMaxWidth())
    }
}

/** A `row`/`button_group`: equal-width cells (`weight(1f)`), content centered, children in source order. */
@Composable
private fun PromptContainerRow(
    children: List<PromptItem>,
    counter: ButtonCounter,
    onButton: (Int) -> Unit,
    httpBase: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (child in children) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when (child.type) {
                    PromptItemType.BUTTON -> {
                        // A content button INSIDE a container fills its equal-width cell (D-12).
                        val index = counter.next++
                        PromptButtonControl(
                            label = child.label.orEmpty(),
                            style = child.style ?: PromptStyle.SECONDARY,
                            onClick = { onButton(index) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    PromptItemType.TEXT ->
                        PromptTextItem(child.text.orEmpty(), child.align, Modifier.fillMaxWidth())
                    PromptItemType.MARKUP ->
                        PromptMarkupItem(child.markup.orEmpty(), child.align, Modifier.fillMaxWidth())
                    PromptItemType.IMAGE -> PromptImageItem(
                        path = child.path.orEmpty(),
                        alt = child.alt.orEmpty(),
                        scale = child.scale,
                        httpBase = httpBase,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // Containers are never nested (reducer guarantee); ignore defensively.
                    PromptItemType.ROW, PromptItemType.BUTTON_GROUP -> Unit
                }
            }
        }
    }
}

/** Plain text content item — `--text`, 18sp Geist Regular; alignment honored. */
@Composable
private fun PromptTextItem(text: String, align: PromptAlign?, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text,
        style = JiibType.body.toTextStyle(t),
        textAlign = align.toTextAlign(),
        modifier = modifier,
    )
}

/** Markup content item — the AST builder; alignment wrapping the whole run. */
@Composable
private fun PromptMarkupItem(rawMarkup: String, align: PromptAlign?, modifier: Modifier = Modifier) {
    val nodes: List<MarkupNode> = works.mees.jiib.prompt.parseMarkup(rawMarkup)
    val align2 = align.toTextAlign()
    if (align2 == TextAlign.Center || align2 == TextAlign.End) {
        // For non-default alignment, push the run to the requested edge of the full-width cell.
        val box = if (align2 == TextAlign.End) Alignment.CenterEnd else Alignment.Center
        Box(modifier, contentAlignment = box) {
            PromptMarkupText(nodes, Modifier.wrapContentWidth())
        }
    } else {
        PromptMarkupText(nodes, modifier)
    }
}

/**
 * A content/group button — the outline-led ≥64dp control (OutlinedControl language) whose 2px outline +
 * label tint = [promptStyleColor]([style]). Fills its cell width; label is 18sp Geist SemiBold, maxLines 2.
 * Buttons do NOT auto-close — [onClick] just fires the dispatch callback (12-05 sends the gcode).
 */
@Composable
internal fun PromptButtonControl(
    label: String,
    style: PromptStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val color = promptStyleColor(style, t)
    Box(
        modifier
            .heightIn(min = 64.dp) // ≥64dp touch floor (UI-02) — a sanctioned fixed value.
            .clip(shape)
            .border(BorderStroke(2.dp, color), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            style = JiibType.buttonLabel.toTextStyle(t),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** `left`→Start, `right`→End, `center`/null→Center (the SPEC default). */
private fun PromptAlign?.toTextAlign(): TextAlign = when (this) {
    PromptAlign.LEFT -> TextAlign.Start
    PromptAlign.RIGHT -> TextAlign.End
    PromptAlign.CENTER, null -> TextAlign.Center
}
