package works.mees.dinghy.designsystem.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.dinghy.theme.compose.LocalTokens

/**
 * The horizontal screen-frame inset — THE single source for the "list ↔ button-bar edge alignment"
 * rule (owner ruling, 2026-06-12).
 *
 * Rule: a button bar ([works.mees.dinghy.designsystem.components.FootButtonBar]) stacked vertically
 * with a list-format field area ([ListBlock]) — above OR below it — must have its outer left/right
 * edges aligned with the list's outer edges. Both components read THIS constant for their horizontal
 * frame, so they always agree. Change the frame in one place and the list and its button bar move
 * together; no per-screen padding to chase.
 *
 * Scope: this is the list/button content frame ONLY. Reading-column screens (About/Settings/Splash)
 * and custom-grid focus regions intentionally use other insets and do NOT read this.
 */
val ListFrameInset: Dp = 8.dp

/**
 * The edge-faded, scrollbar-less `LazyColumn` wrapper used by every list screen in the jiib redesign.
 *
 * ## Purpose
 *
 * The lists-first grammar (LAYOUT.md §"Content vs controls — fill convention") uses the Field region
 * as a scrollable content area. `ListBlock` is the standard wrapper that:
 *  - Holds the `rememberLazyListState` internally (caller does not manage list state)
 *  - Provides `Arrangement.spacedBy(8.dp)` — the inter-row spacing set for the design system
 *  - Shows top and bottom gradient edge fades driven by scroll position
 *  - Does NOT show a scrollbar indicator (the edge fades are the scroll hint)
 *
 * ## Edge fades (cheap, Adreno-320-safe)
 *
 * The top and bottom fades are drawn as gradient `Box` overlays OVER the `LazyColumn`. They are
 * driven by `LazyListState.firstVisibleItemIndex`, `LazyListState.firstVisibleItemScrollOffset`,
 * and `LazyListState.canScrollForward` — all derived via `derivedStateOf` to minimize unnecessary
 * recomposition on scroll.
 *
 * Crucially, the fade `Box` composables carry **no pointer-input modifier** (no `clickable`,
 * `pointerInput`, or `scrollable`). A plain `Box` with only a `Modifier.background(brush)` does
 * NOT intercept gestures — touch passes through to the `LazyColumn` beneath. This makes the list
 * scrollable right up to the fade edges, which is essential UX (a fade that blocks scrolling looks
 * and feels broken).
 *
 * The fade approach uses `Box` gradient overlays rather than `Modifier.drawWithContent` /
 * `ComposeShader` because the gradient `Box` approach is cheaper on Adreno 320 fill rate (no
 * per-pixel shader; plain linear gradient baked into the Brush).
 *
 * ## Call-site pattern
 *
 * The caller supplies the keyed `items(...)` block via the `content` lambda. `ListBlock` does not
 * own the items. Example:
 *
 * ```kotlin
 * ListBlock(modifier = Modifier.weight(1f).padding(top = 8.dp)) {   // horizontal frame owned by ListBlock
 *     items(state.spools, key = { it.id }) { spool ->
 *         ListRow(selected = spool.id == selected?.id, onClick = { onRowClick(spool) }, uDp = grid.uDp) {
 *             SpoolRowContent(spool, t)
 *         }
 *     }
 * }
 * ```
 *
 * Always provide a stable `key` on `items(...)` — missing keys cause whole-list recomposition on
 * every state change (jank on Adreno 320 — see 23-PATTERNS anti-pattern 2).
 *
 * @param modifier Applied to the outer `Box` container.
 * @param content The `LazyListScope` lambda; the caller builds `items(...)` blocks here.
 */
@Composable
fun ListBlock(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val t = LocalTokens.current
    val listState = rememberLazyListState()

    // Derive scroll edge signals in derivedStateOf to avoid recomposing the whole tree on every
    // scroll event — only the fade visibility changes, not the list content.
    val showTopFade by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val showBottomFade by remember {
        derivedStateOf { listState.canScrollForward }
    }

    // Fade height in dp — 32dp is visible enough without eating too much content area.
    val fadeHeight = 32.dp

    // The horizontal frame is OWNED here (ListFrameInset) so the list's outer edges always match a
    // stacked FootButtonBar's — callers pass only vertical/weight, never start/end (owner rule).
    Box(modifier.padding(horizontal = ListFrameInset)) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
            content = content,
        )

        // Top edge fade — visible when the list is scrolled down from the top.
        // NO pointer-input modifier: touch passes through to the LazyColumn below.
        if (showTopFade) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(t.bg, t.bg.copy(alpha = 0f)),
                        ),
                    ),
            )
        }

        // Bottom edge fade — visible when there is more content to scroll to.
        // NO pointer-input modifier: touch passes through to the LazyColumn below.
        if (showBottomFade) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fadeHeight)
                    .align(androidx.compose.ui.Alignment.BottomStart)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(t.bg.copy(alpha = 0f), t.bg),
                        ),
                    ),
            )
        }
    }
}
