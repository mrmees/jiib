package works.mees.jiib.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicText
import works.mees.jiib.theme.DinghyType
import works.mees.jiib.theme.FontScale
import works.mees.jiib.theme.ThemeOverride
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import kotlin.math.roundToInt

// ---- Pure MERGE-onto-current combo-stepping logic (HIGH-4 + atomic-stepping transform shape) --------
//
// These are PURE `(ThemeOverride?) -> ThemeOverride` functions, exactly the transform shape that
// `AppContainer.updateThemeOverride { next*Override(it) }` consumes (so the atomic stepping path reads
// the LIVE current value, never a Compose-captured snapshot — MEDIUM). Each steps ONLY its own axis
// and carries the OTHER axis forward via `.copy(...)`, so tapping one cycler never reverts the other
// (HIGH-4 — the two axes are runtime-independent). Mode strings MUST match ThemePrefs.VALID_MODES
// ("Colorful"/"Simple"/"HighContrast") and DEFAULT_MODE — do NOT invent new names.

/** The 6 {dark,light} x {Colorful,Simple,HighContrast} style combos, in cycle order. */
private val STYLE_COMBOS: List<Pair<Boolean, String>> = listOf(
    true to "Colorful",
    true to "Simple",
    true to "HighContrast",
    false to "Colorful",
    false to "Simple",
    false to "HighContrast",
)

/** The S/M/L font-scale multipliers, in cycle order. */
private val SIZE_STEPS: List<Float> = listOf(
    FontScale.S.multiplier,
    FontScale.M.multiplier,
    FontScale.L.multiplier,
)

/**
 * Advance the ACTIVE printer-profile id to the NEXT id in [ids] (wrap-around) — the pure stepper behind
 * the dev printer-switcher cycler (15.2-04 finding 2). This is a GENUINE active-profile switch (reused via
 * `AppContainer.setActiveProfile`), not a transient theme override, so it directly accelerates the
 * upcoming 15.2-06 cross-printer conformance sweep.
 *
 * Contract:
 *  - 0 or 1 ids → returns [currentId] unchanged (single-profile / empty = no-op, no crash).
 *  - currentId not found in [ids] (null or dangling) → returns the FIRST id (so the first tap lands a
 *    valid active profile rather than no-opping forever).
 *  - otherwise → the id one position past [currentId], wrapping after the last.
 *
 * @param ids the ordered profile ids (the live `profileStore.profiles` order).
 * @param currentId the live active-profile id (or null when none).
 * @return the id to switch to, or null only when [ids] is empty.
 */
fun nextProfileId(ids: List<String>, currentId: String?): String? {
    if (ids.isEmpty()) return null
    if (ids.size == 1) return currentId // single-profile no-op (keeps whatever is active).
    val pos = ids.indexOf(currentId)
    // Unknown/dangling current → land on the first id; otherwise advance with wrap.
    val nextIndex = if (pos < 0) 0 else (pos + 1) % ids.size
    return ids[nextIndex]
}

/**
 * Advance the STYLE axis (dark + paletteMode) to the next of the 6 combos, wrapping after 6, while
 * carrying `current.fs` forward UNCHANGED (HIGH-4: an active size override survives a style tap). The
 * current style position is derived from `current?.dark`/`current?.paletteMode`; an absent/unknown
 * style defaults to the FIRST combo's predecessor so the first tap lands on combo 0.
 */
fun nextStyleOverride(current: ThemeOverride?): ThemeOverride {
    val pos = STYLE_COMBOS.indexOfFirst { (d, m) -> d == current?.dark && m == current?.paletteMode }
    // pos == -1 (no/unknown style) -> start at index 0; otherwise advance with wrap.
    val nextIndex = if (pos < 0) 0 else (pos + 1) % STYLE_COMBOS.size
    val (nextDark, nextMode) = STYLE_COMBOS[nextIndex]
    return (current ?: ThemeOverride()).copy(dark = nextDark, paletteMode = nextMode)
}

/**
 * Advance the SIZE axis (`fs`) to the next of FontScale.S/M/L, wrapping after L, while carrying
 * `current.dark`/`current.paletteMode` forward UNCHANGED (HIGH-4: an active style override survives a
 * size tap). An absent/unknown fs defaults so the first tap lands on the first size.
 */
fun nextSizeOverride(current: ThemeOverride?): ThemeOverride {
    val pos = SIZE_STEPS.indexOfFirst { it == current?.fs }
    val nextIndex = if (pos < 0) 0 else (pos + 1) % SIZE_STEPS.size
    val nextFs = SIZE_STEPS[nextIndex]
    return (current ?: ThemeOverride()).copy(fs = nextFs)
}

/** Short human label for the current style combo (for the widget face). */
private fun styleLabel(o: ThemeOverride?): String {
    val dark = o?.dark
    val mode = o?.paletteMode
    val polarity = when (dark) { true -> "Dark"; false -> "Light"; null -> "—" }
    val m = mode ?: "—"
    return "$polarity · $m"
}

/** Short human label for the current size (for the widget face). */
private fun sizeLabel(o: ThemeOverride?): String = when (o?.fs) {
    FontScale.S.multiplier -> "S"
    FontScale.M.multiplier -> "M"
    FontScale.L.multiplier -> "L"
    else -> "—"
}

/**
 * The two floating dev theme cyclers (D-06 style + D-07 size), gated on the dev-enable boolean upstream
 * (rendered by AppShell only when on). Each is a >=64dp tap target showing its current axis label; a
 * separate dismiss control clears the override back to the real saved theme.
 *
 * MOTION LAW (D-08 / CLAUDE.md / OutlinedControl): STATIC ONLY — no continuous/looping/breathing
 * animation (the Adreno-320 fill-rate floor). The widget is a draggable static chip (offset moves only
 * while a finger drags it); there is NO `rememberInfiniteTransition`/`infiniteRepeatable`. Every color
 * reads `LocalTokens.current` (themes WITH the app — never a raw Color literal). This is a normal
 * Compose overlay above all routes, NOT a WindowManager TYPE_APPLICATION_OVERLAY (avoids
 * SYSTEM_ALERT_WINDOW).
 *
 * ## Printer cycler (15.2-04 finding 2)
 * A THIRD chip advances the ACTIVE printer profile to the next available one (wrap-around) via the same
 * `AppContainer.setActiveProfile` intent the drawer/Printers screen use — a GENUINE switch, not a
 * transient override. It identifies the current printer (so the human can tell which is active mid-sweep)
 * and is a no-op / disabled when fewer than two profiles exist. It accelerates the 15.2-06 cross-printer
 * conformance walk. Same static-only motion + role-token rules as the other chips.
 *
 * @param currentOverride the live override (from `container.themeOverride`) — labels the active combo.
 * @param onCycleStyle    advance the style axis (wired to `updateThemeOverride { nextStyleOverride(it) }`).
 * @param onCycleSize     advance the size axis (wired to `updateThemeOverride { nextSizeOverride(it) }`).
 * @param onDismiss       clear the override (wired to `setThemeOverride(null)`).
 * @param printerLabel    the active printer's display name/host (the chip face); null when no profile.
 * @param printerSwitchable whether ≥2 profiles exist (false → the printer chip reads disabled/no-op).
 * @param onCyclePrinter  advance to the next active profile (wired to `setActiveProfile(nextProfileId(...))`).
 */
@Composable
fun DevThemeCyclerOverlay(
    currentOverride: ThemeOverride?,
    onCycleStyle: () -> Unit,
    onCycleSize: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    printerLabel: String? = null,
    printerSwitchable: Boolean = false,
    onCyclePrinter: () -> Unit = {},
) {
    val t = LocalTokens.current
    val density = LocalDensity.current

    // Draggable position (ScrubberPage/ColorWheel precedent: offset moves only during an active drag —
    // a STATIC chip otherwise). Seeded to a top-start inset so it never hides under the status bar.
    var offset by remember { mutableStateOf<Offset?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .onSizeChanged { boxSize = it },
    ) {
        val startInsetPx = with(density) { 12.dp.toPx() }
        val pos = offset ?: Offset(startInsetPx, startInsetPx)
        Column(
            modifier = Modifier
                .offset { IntOffset(pos.x.roundToInt(), pos.y.roundToInt()) }
                .background(t.surface2, RoundedCornerShape(10.dp))
                .border(2.dp, t.outline, RoundedCornerShape(10.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // D-20 DRAG STRIP — a bare-surface region that owns the panel-drag gesture.
            // The bug was that chip taps consumed DOWN before the Column's awaitFirstDown could see it,
            // so the whole panel stopped relocating when a press landed on a chip. The fix: move the
            // drag to a DEDICATED inert strip separate from the chips. Chip taps still consume their own
            // events (preventing bleed) and the drag strip sees its own independent gesture.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .pointerInput(Unit) {
                        // Drag the panel via this dedicated strip. This is the ONLY motion in the widget —
                        // it moves only while a finger drags it (no looping/continuous animation; Adreno-320 motion LAW).
                        //
                        // WR-01 ([[dinghy-compose-write-scope-cancellation]] sibling — the pointerInput
                        // stale-closure trap): deltas must accumulate onto the LIVE `offset` MutableState,
                        // never the composition-scope `pos` val. `pointerInput` only re-captures its lambda
                        // when the key changes, so a captured `pos` froze at its first-layout value — every
                        // drag event computed oldCapturedPos + thisEvent'sDelta and the panel never followed
                        // the finger. Reading `offset`/`boxSize` (both MutableState delegates) inside the
                        // handler is live, which also lets the key drop to Unit.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            down.consume()
                            drag(down.id) { change ->
                                val delta = change.positionChange()
                                val cur = offset ?: Offset(startInsetPx, startInsetPx)
                                // keep it on screen (best-effort clamp to parent bounds)
                                val maxX = (boxSize.width - 1).coerceAtLeast(0).toFloat()
                                val maxY = (boxSize.height - 1).coerceAtLeast(0).toFloat()
                                offset = Offset(
                                    (cur.x + delta.x).coerceIn(0f, maxX),
                                    (cur.y + delta.y).coerceIn(0f, maxY),
                                )
                                change.consume()
                            }
                        }
                    },
            )
            // Header + dismiss (below the drag strip — taps consumed by DismissChip, not by the drag strip)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(
                    text = "THEME",
                    style = DinghyType.caption.toTextStyle(t).copy(color = t.text3),
                )
                DismissChip(onDismiss = onDismiss)
            }
            // Style cycler (>=64dp tap target)
            CyclerChip(
                title = "Style",
                value = styleLabel(currentOverride),
                onTap = onCycleStyle,
            )
            // Size cycler (>=64dp tap target) — separate axis (D-07)
            CyclerChip(
                title = "Size",
                value = sizeLabel(currentOverride),
                onTap = onCycleSize,
            )
            // Printer switcher cycler (15.2-04 finding 2) — a GENUINE active-profile switch (wrap-around).
            // Disabled / no-op when <2 profiles exist; shows the active printer so the human can tell which
            // is live during the cross-printer conformance sweep.
            CyclerChip(
                title = "Printer",
                value = printerLabel ?: "—",
                onTap = onCyclePrinter,
                enabled = printerSwitchable,
            )
        }
    }
}

@Composable
private fun CyclerChip(
    title: String,
    value: String,
    onTap: () -> Unit,
    enabled: Boolean = true,
) {
    val t = LocalTokens.current
    // Disabled chips read greyed (hairline outline) and swallow no taps (single-profile no-op, finding 2).
    val outline = if (enabled) t.accentLine else t.outline
    val base = Modifier
        // >=64dp tap target (touch floor) — the only sanctioned fixed px (LAYOUT.md NON-NEGOTIABLE 3).
        .sizeIn(minWidth = 96.dp, minHeight = 64.dp)
        .background(t.surface3, RoundedCornerShape(8.dp))
        .border(2.dp, outline, RoundedCornerShape(8.dp))
    val tappable = if (enabled) {
        base.pointerInput(Unit) {
            awaitEachGesture {
                // Consume the DOWN so the parent panel-drag (Column `drag(down.id)`) never starts from a
                // press that lands on a chip — without this, finger movement on a chip bleeds through to
                // the parent and the whole overlay slides (15.2-04 finding-2 drag regression).
                val down = awaitFirstDown(requireUnconsumed = false)
                down.consume()
                // wait for up; if no significant drag, treat as a tap (a static one-shot, no loop).
                var dragged = false
                drag(down.id) { change ->
                    if (change.positionChange().getDistanceSquared() > 64f) dragged = true
                    // Consume each move so it does not propagate to the parent panel-drag handler. A chip
                    // owns its own pointer; the panel only relocates from a press that misses every chip.
                    change.consume()
                }
                if (!dragged) onTap()
            }
        }
    } else {
        base
    }
    Column(
        modifier = tappable.padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(
            text = title,
            style = DinghyType.caption.toTextStyle(t).copy(color = t.text3),
        )
        BasicText(
            text = value,
            style = DinghyType.caption.toTextStyle(t).copy(color = if (enabled) t.text else t.text3),
        )
    }
}

@Composable
private fun DismissChip(onDismiss: () -> Unit) {
    val t = LocalTokens.current
    Box(
        modifier = Modifier
            .sizeIn(minWidth = 64.dp, minHeight = 32.dp)
            .background(t.stopSoft, RoundedCornerShape(6.dp))
            .border(2.dp, t.stop, RoundedCornerShape(6.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Consume DOWN + each move so a press/drag on Dismiss never bleeds into the parent
                    // panel-drag (same fix as the cycler chips — finding-2 drag regression).
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    var dragged = false
                    drag(down.id) { change ->
                        if (change.positionChange().getDistanceSquared() > 64f) dragged = true
                        change.consume()
                    }
                    if (!dragged) onDismiss()
                }
            }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Dismiss",
            style = DinghyType.caption.toTextStyle(t).copy(color = t.stop),
        )
    }
}
