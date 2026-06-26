package works.mees.jiib.prompt

/**
 * The PURE Macro Prompt Protocol v1 reducer state machine (PROMPT-01 parse/reduce-tolerant; PROMPT-03
 * end/footer/re-entrancy/never-wedge) — a near-verbatim Kotlin port of upstream `packages/js/src/reducer.ts`
 * plus the `view.ts` projection. NO I/O, NO coroutines, NO Compose: `reduce` is a total function over the
 * sealed [PromptEvent] set; the same (state, event) always yields the same fresh, immutable value, and a
 * malformed/out-of-order/garbage stream degrades deterministically and NEVER wedges or crashes (T-12-05).
 *
 * The fixtures (PromptFixtureTest) grade this against the SAME oracle every other prompt frontend uses
 * (D-14): the subtle rules — pending-consumed-while-suppressed, align-preserve-vs-size-clear,
 * container-nested-start-ignored-but-children-still-append, replace-active-prompt — match the JS exactly.
 * DO NOT improvise: the JS is the oracle (Pitfall 1 — subtle deviations fail fixtures cryptically).
 */

/** The engine seed: a fresh idle state at epoch 0 carrying [opts]. Mirrors `initialPromptState`. */
fun initialPromptState(opts: PromptOpts): PromptStateData = freshIdle(0, opts)

/** A clean idle state at [epoch] (title cleared, no items/footer, targets reset to `all`, align center). */
private fun freshIdle(epoch: Int, opts: PromptOpts): PromptStateData = PromptStateData(
    lifecycle = PromptLifecycle.IDLE,
    epoch = epoch,
    title = "",
    size = null,
    activeTargets = listOf("all"),
    items = emptyList(),
    footerButtons = emptyList(),
    activeContainer = null,
    pendingTargets = null,
    pendingSize = null,
    currentAlign = PromptAlign.CENTER,
    opts = opts,
)

/**
 * True if [targets] makes this prompt visible to the configured frontend identity: matches the literal
 * `all`, the lowercased [PromptOpts.frontendId], or any lowercased [PromptOpts.frontendCategories] member.
 * (Jiib: `jiib` + `touch`.) Mirrors `targetsMatch`.
 */
private fun targetsMatch(targets: List<String>, opts: PromptOpts): Boolean {
    if (targets.contains("all")) return true
    if (targets.contains(opts.frontendId.lowercase())) return true
    return opts.frontendCategories.any { targets.contains(it.lowercase()) }
}

/**
 * Fold one [event] into [state], returning a fresh immutable [PromptStateData] (NEVER mutates its input).
 * Exhaustive over [PromptEvent]; out-of-order/garbage events degrade deterministically (no-op or reset).
 * Verbatim port of `reducePrompt` in reducer.ts.
 */
fun reduce(state: PromptStateData, event: PromptEvent): PromptStateData {
    when (event) {
        is PromptEvent.Target -> return state.copy(pendingTargets = event.targets)
        is PromptEvent.Size -> return state.copy(pendingSize = event.size)
        // end / disconnect are identical: no-op only if already clean, else reset WITHOUT bumping epoch.
        PromptEvent.End, PromptEvent.Disconnect ->
            return if (state.lifecycle == PromptLifecycle.IDLE &&
                state.pendingTargets == null &&
                state.pendingSize == null
            ) {
                state
            } else {
                freshIdle(state.epoch, state.opts)
            }
        is PromptEvent.Begin -> {
            val targets = state.pendingTargets ?: listOf("all")
            val matched = targetsMatch(targets, state.opts)
            // Start clean from freshIdle(epoch+1); pending* consumed (reset to null) EVEN when suppressed.
            return freshIdle(state.epoch + 1, state.opts).copy(
                lifecycle = if (matched) PromptLifecycle.BUILDING else PromptLifecycle.SUPPRESSED,
                title = event.title,
                size = state.pendingSize,
                activeTargets = targets,
                currentAlign = PromptAlign.CENTER,
            )
        }
        PromptEvent.Show ->
            return if (state.lifecycle == PromptLifecycle.BUILDING) {
                state.copy(lifecycle = PromptLifecycle.SHOWN)
            } else {
                state
            }
        is PromptEvent.Align ->
            // Non-null updates currentAlign; null (unknown/empty) is a no-op — PRESERVED (asymmetric vs
            // size, which clears on a bad value). This is the align-preserve rule.
            return if (event.align != null) state.copy(currentAlign = event.align) else state
        else -> Unit // content events fall through to the active-prompt guard below
    }

    // Content events below require an active, MATCHING prompt.
    val lc = state.lifecycle
    if (lc == PromptLifecycle.IDLE || lc == PromptLifecycle.SUPPRESSED) return state
    if (!state.opts.liveAppend && lc == PromptLifecycle.SHOWN) return state // snapshot-at-show

    return when (event) {
        is PromptEvent.Text -> appendContent(state, PromptItem(type = PromptItemType.TEXT, text = event.text))
        is PromptEvent.Markup -> appendContent(
            state,
            PromptItem(type = PromptItemType.MARKUP, markup = event.markup, plainText = event.plainText),
        )
        is PromptEvent.Button -> appendContent(
            state,
            PromptItem(type = PromptItemType.BUTTON, label = event.label, gcode = event.gcode, style = event.style),
        )
        is PromptEvent.Image ->
            if (isValidImagePath(event.path)) {
                appendContent(
                    state,
                    PromptItem(type = PromptItemType.IMAGE, path = event.path, alt = event.alt, scale = event.scale),
                )
            } else if (event.alt.isEmpty()) {
                state
            } else {
                // alt-as-text fallback IN the reducer.
                appendContent(state, PromptItem(type = PromptItemType.TEXT, text = event.alt))
            }
        is PromptEvent.FooterButton -> state.copy(
            footerButtons = state.footerButtons + FooterButton(event.label, event.gcode, event.style),
        )
        PromptEvent.RowStart -> openContainer(state, PromptItemType.ROW)
        PromptEvent.RowEnd -> closeContainer(state, PromptItemType.ROW)
        PromptEvent.ButtonGroupStart -> openContainer(state, PromptItemType.BUTTON_GROUP)
        PromptEvent.ButtonGroupEnd -> closeContainer(state, PromptItemType.BUTTON_GROUP)
        else -> state // already handled above (begin/show/end/etc.) — unreachable here, but total.
    }
}

/** Open a `row`/`button_group` container; a nested start (container already open) is IGNORED. */
private fun openContainer(state: PromptStateData, kind: PromptItemType): PromptStateData {
    if (state.activeContainer != null) return state // already open: ignore nested start
    val empty = PromptItem(type = kind)
    return state.copy(activeContainer = kind, items = state.items + stampAlign(empty, state.currentAlign))
}

/** Close a container; a mismatched/stray end (kind != the open container) is IGNORED. */
private fun closeContainer(state: PromptStateData, kind: PromptItemType): PromptStateData {
    if (state.activeContainer != kind) return state // stray end: ignore
    return state.copy(activeContainer = null)
}

/** Stamp [align] onto [item] for a TOP-LEVEL item; center is the default and is omitted (left null). */
private fun stampAlign(item: PromptItem, align: PromptAlign): PromptItem =
    if (align == PromptAlign.CENTER) item else item.copy(align = align)

/**
 * Route an [item] into the open container (row accepts any inline item; button_group accepts only buttons),
 * else append it top-level with the current align stamped. Mirrors `appendContent`.
 */
private fun appendContent(state: PromptStateData, item: PromptItem): PromptStateData {
    when (state.activeContainer) {
        PromptItemType.ROW -> {
            // Containers never nest inside a row; a stray row/button_group item is dropped.
            if (item.type == PromptItemType.ROW || item.type == PromptItemType.BUTTON_GROUP) return state
            return appendToLastContainer(state, item)
        }
        PromptItemType.BUTTON_GROUP -> {
            if (item.type != PromptItemType.BUTTON) return state // group rejects non-button children
            return appendToLastContainer(state, item)
        }
        else -> return state.copy(items = state.items + stampAlign(item, state.currentAlign))
    }
}

/** Append [child] to the children of the last (open container) item. No align stamp on children. */
private fun appendToLastContainer(state: PromptStateData, child: PromptItem): PromptStateData {
    val last = state.items.lastOrNull()
    if (last == null || (last.type != PromptItemType.ROW && last.type != PromptItemType.BUTTON_GROUP)) {
        return state
    }
    val updated = last.copy(children = last.children + child)
    return state.copy(items = state.items.dropLast(1) + updated)
}

/**
 * Project [state] to the 6-key conformance [PromptView] — `visible` iff SHOWN; everything else is the
 * authored title/targets/size/items/footer. No internal field leaks (T-12-07). Mirrors `promptView`.
 * Immutability is by construction (all fields are immutable `List`/`data class`), so no deep clone is
 * needed (the `view.ts` clone exists only because JS objects are mutably shared — Kotlin lists are not).
 */
fun promptView(state: PromptStateData): PromptView = PromptView(
    visible = state.lifecycle == PromptLifecycle.SHOWN,
    title = state.title,
    targets = state.activeTargets,
    size = state.size,
    items = state.items,
    footerButtons = state.footerButtons,
)
