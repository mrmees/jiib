package works.mees.dinghy.ui.screen

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.designsystem.ColorWheel
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.FontScale
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.PaletteMode
import works.mees.dinghy.theme.StatusSlot
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.theme.toComposeColor
import kotlinx.coroutines.flow.firstOrNull
import kotlin.random.Random

/**
 * The pushed theme-editor sub-page (D-06…D-10) — the one screen with no mockup, authored against
 * `docs/ui_design/` LAW (FFG-exempt, like Settings): a plain token-themed `Column.verticalScroll`.
 * It is reached from BOTH Settings entry paths because [SettingsScreen] OWNS the open-state and renders
 * this when open (so the AppShell `Dest.Settings` route AND the RootController first-run/escape render
 * both reach it — there is no AppShell-level editor flag).
 *
 * ## Sections (top→bottom, 15-UI-SPEC §"New surface")
 *  1. **Seed color** — the [ColorWheel] (settle-regen): `onSettle` → [AppContainer.setActiveSeed].
 *  2. **Presets** — curated preset seed swatches; tap lands a seed (settle semantics); active = accent ring.
 *  3. **Preview** — the generated swatch strip (accent · pool[0..n] · status), each its actual color.
 *  4. **Pool colors** — the per-slot data-pool override grid (D-09); tap → a hue picker → setActiveOverride.
 *  5. **Actions** — Randomize (amber Warn) · Reset (red Danger, ConfirmGuard) · Done (green Go).
 *
 * ## Durable writes (T-15-06-02, [[dinghy-compose-write-scope-cancellation]])
 * EVERY theme write routes through the [AppContainer] intent helpers (writeScope + mutateActiveProfile,
 * or the global idle theme) — NEVER a `rememberCoroutineScope()`. A reseed must not churn the connection
 * spine (theme is excluded from ConnectionConfig, 15-05).
 *
 * ## Carve-outs (precedented)
 * The wheel/handle, the preset seed swatches, the preview strip, and the pool-override swatches render
 * their LITERAL generated/seed/overridden color (theme DATA being previewed — like AccentSwatch / the
 * Spoolman spool-color border / the PromptMarkup author-hex). ALL chrome routes through [LocalTokens].
 *
 * @param container the process-scoped service-locator (constructs nothing here).
 * @param onBack invoked when the user exits the editor (Done/Back) — the screen owner pops to the hub.
 */
@Composable
fun ThemeEditorScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // Whether a profile is active drives the durable write TARGET (active profile vs global idle theme).
    val activeProfile by container.activeProfile.collectAsStateWithLifecycle(null)
    val hasActive = activeProfile != null

    // The hue the wheel handle shows. Seeded from the live seed; tracks the finger mid-drag (cheap), and
    // commits on settle. The settle write rethemes the whole app (the editor sits inside DinghyTheme).
    var hue by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(activeProfile?.id) {
        hue = seedHexToHue(activeProfile?.seedHex)
    }

    // ---- Appearance (dark/light + S/M/L + palette-mode) mirror state -----------------------------
    // (15.2-04 finding 3 — RELOCATED here from the old SettingsScreen "Appearance" section that the
    // 4-tile split deleted without rehoming. The Theme tile is APPEARANCE per its own docstring, so
    // dark/light, text-size, and palette mode belong here alongside seed/pool/status.) Each control
    // drives the LIVE resolver AND persists via the durable AppContainer intents (writeScope, NOT a
    // rememberCoroutineScope — [[dinghy-compose-write-scope-cancellation]]). These mirror vars only
    // drive the selected-chip emphasis; the live resolver + persisted prefs/profile are the authority.
    var dark by remember { mutableStateOf(true) }
    var fsChoice by remember { mutableStateOf(FontScale.M) }
    var paletteMode by remember { mutableStateOf(ThemeResolver.MODE_COLORFUL) }

    // Seed the Appearance mirror from the ACTIVE profile's theme tuple when one exists (so the screen
    // opens reflecting the active printer's look, D-09), else from the global theme tuple (idle default).
    LaunchedEffect(activeProfile?.id) {
        val tuple = activeProfile?.toThemeTuple() ?: container.themePrefs.tupleFlow.firstOrNull()
        if (tuple != null) {
            dark = tuple.dark
            fsChoice = FontScale.entries.firstOrNull { it.multiplier == tuple.fs } ?: FontScale.M
            paletteMode = tuple.paletteMode
        }
    }

    // The Reset confirm guard (D-09) — hoisted over the whole editor, mirrors the Settings delete guard.
    var pendingReset by remember { mutableStateOf(false) }
    // The pool slot whose override picker is open (null = none). The picker reuses the ColorWheel.
    var editingSlot by remember { mutableStateOf<Int?>(null) }
    // The STATUS slot whose override picker is open (null = none). Parallel to editingSlot (D-03).
    var editingStatusSlot by remember { mutableStateOf<StatusSlot?>(null) }

    if (pendingReset) {
        ConfirmGuard(
            title = "Reset theme?",
            message = "This clears your custom pool colors and randomize back to the default.",
            confirmLabel = "Reset",
            cancelLabel = "Keep",
            onConfirm = {
                container.resetActiveTheme(hasActive)
                pendingReset = false
            },
            onCancel = { pendingReset = false },
            destructive = true,
        )
        return
    }

    // Per-slot override picker — a full-screen hue wheel that writes ONE pool slot on settle (D-09).
    val slot = editingSlot
    if (slot != null) {
        // WR-06: seed the wheel from the CURRENT slot color (override or base generated), not hue 0 (red).
        // Re-opening a slot the user set to teal must show the handle at teal, not make them drag from scratch.
        val currentSlotColor = if (t.pool.isNotEmpty()) t.pool[slot % t.pool.size] else t.accent
        var slotHue by remember(slot) { mutableFloatStateOf(colorToHue(currentSlotColor)) }
        Column(
            modifier
                .fillMaxSize()
                .background(t.bg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ScreenTitle("Pool color ${slot + 1}")
            SectionLabel("Pick a color")
            ColorWheel(
                hue = slotHue,
                onHandleMove = { slotHue = it },
                onSettle = { settled ->
                    container.setActiveOverride(hasActive, slot, hueToArgbLong(settled))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedControl(
                    label = "Clear",
                    onClick = {
                        container.setActiveOverride(hasActive, slot, null)
                        editingSlot = null
                    },
                    modifier = Modifier.weight(1f),
                    intent = Intent.Warn,
                )
                OutlinedControl(
                    label = "Done",
                    onClick = { editingSlot = null },
                    modifier = Modifier.weight(1f),
                    intent = Intent.Go,
                )
            }
            Box(Modifier.height(24.dp))
        }
        return
    }

    // Per-status-slot override picker (D-03) — mirrors the pool picker, writes via the durable
    // setActiveStatusOverride intent (writeScope), NEVER a rememberCoroutineScope (see the KDoc above).
    val statusSlot = editingStatusSlot
    if (statusSlot != null) {
        // The EFFECTIVE rendered color for this slot (mode-gated — Simple/High-Contrast ignore the override).
        val effective = effectiveStatusColor(t, statusSlot)
        // The STORED override (the user's pick, if any) — read from the active profile's wire map. In the
        // idle/no-profile case the stored override is not surfaced (mirrors the pool grid's limitation).
        val storedArgb = activeProfile?.poolOverrides?.get(statusSlot.key)
        var slotHue by remember(statusSlot) {
            mutableFloatStateOf(colorToHue(storedArgb?.toComposeColor() ?: effective))
        }
        // Whether the live override actually changes what the app renders in the CURRENT mode (Colorful only).
        val overrideTakesEffect = t.mode == PaletteMode.Colorful
        Column(
            modifier
                .fillMaxSize()
                .background(t.bg)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            ScreenTitle("Status color — ${statusSlot.label()}")
            // Mode-awareness (D-04): tell the truth about whether the edit changes the rendered status now.
            if (overrideTakesEffect) {
                SubLabel("Color is a redundant cue — shape carries the safety meaning. Pick any color.")
            } else {
                SubLabel(
                    "Saved for Colorful mode. This mode (" + t.mode.label() +
                        ") renders status from its fixed safety palette, so the picked color won't show here.",
                )
            }
            SectionLabel("Pick a color")
            ColorWheel(
                hue = slotHue,
                onHandleMove = { slotHue = it },
                onSettle = { settled ->
                    // [[dinghy-compose-write-scope-cancellation]] — durable intent on writeScope, NOT a
                    // composition scope. No editability guard on status (D-03 — shape carries safety).
                    container.setActiveStatusOverride(hasActive, statusSlot, hueToArgbLong(settled))
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedControl(
                    label = "Clear",
                    onClick = {
                        container.setActiveStatusOverride(hasActive, statusSlot, null)
                        editingStatusSlot = null
                    },
                    modifier = Modifier.weight(1f),
                    intent = Intent.Warn,
                )
                OutlinedControl(
                    label = "Done",
                    onClick = { editingStatusSlot = null },
                    modifier = Modifier.weight(1f),
                    intent = Intent.Go,
                )
            }
            Box(Modifier.height(24.dp))
        }
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .background(t.bg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenTitle("Edit theme")

        // ---- 0. APPEARANCE — dark/light + S/M/L + palette-mode (15.2-04 finding 3) ------------------
        // Relocated from the deleted SettingsScreen Appearance section. Each control drives the LIVE
        // resolver AND persists via the durable AppContainer intents (writeScope). The existing Preview
        // strip below (section 3) is the single coherent palette preview — no second competing row.

        // Dark / Light — chrome derives from the seed; this only flips polarity.
        SectionLabel("Mode")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedControl(
                label = "Dark",
                onClick = {
                    dark = true
                    container.themeResolver.setDark(true) // live
                    container.setActiveDark(hasActive, true) // durable
                },
                modifier = Modifier.weight(1f),
                intent = if (dark) Intent.Accent else Intent.Neutral,
            )
            OutlinedControl(
                label = "Light",
                onClick = {
                    dark = false
                    container.themeResolver.setDark(false) // live
                    container.setActiveDark(hasActive, false) // durable
                },
                modifier = Modifier.weight(1f),
                intent = if (!dark) Intent.Accent else Intent.Neutral,
            )
        }

        // S / M / L text size (the --fs authority). Each segment is filled with a POOL color from the
        // active palette so the selector visibly reflects the current mode; the selected segment gets the
        // accent ring. The fill is the literal pool color (data carve-out).
        SectionLabel("Text size")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FontScale.entries.forEachIndexed { i, choice ->
                val poolColor = if (t.pool.isEmpty()) t.accent else t.pool[i % t.pool.size]
                PoolSizeSegment(
                    label = choice.name,
                    fill = poolColor,
                    selected = fsChoice == choice,
                    onClick = {
                        fsChoice = choice
                        container.themeResolver.setFs(choice.multiplier) // live
                        container.setActiveFs(hasActive, choice) // durable (process-lifetime writeScope)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Palette mode (D-15) — Colorful (default) / Simple / High contrast. Active = Intent.Accent.
        SectionLabel("Palette mode")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((mode, label) in PALETTE_MODES) {
                OutlinedControl(
                    label = label,
                    onClick = {
                        paletteMode = mode
                        container.themeResolver.setMode(mode) // live
                        container.setActiveMode(hasActive, mode) // durable
                    },
                    modifier = Modifier.weight(1f),
                    intent = if (paletteMode == mode) Intent.Accent else Intent.Neutral,
                )
            }
        }

        // ---- 1. SEED COLOR — the wheel (settle-regen, D-07) ----------------------------------------
        SectionLabel("Seed color")
        SubLabel("Pick a color or tap a preset")
        ColorWheel(
            hue = hue,
            onHandleMove = { hue = it }, // cheap: handle-only repaint, NO regen (D-07).
            onSettle = { settled ->
                hue = settled
                container.setActiveSeed(hasActive, hueToHex(settled)) // regen + retheme + persist ONCE.
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- 2. PRESETS — curated seed swatches (tap lands seed) ------------------------------------
        SectionLabel("Presets")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (presetHex in PRESET_SEEDS) {
                val presetHue = seedHexToHue(presetHex)
                SeedSwatch(
                    fill = Color(parseHex(presetHex)),
                    selected = approxSameHue(hue, presetHue),
                    onClick = {
                        hue = presetHue
                        container.setActiveSeed(hasActive, presetHex)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ---- 3. PREVIEW — the generated swatch strip (live; the carve-out) --------------------------
        SectionLabel("Preview")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // accent + the first few pool colors + the three status colors, each its actual color.
            DataSwatch(t.accent, Modifier.weight(1f))
            for (c in t.pool.take(4)) DataSwatch(c, Modifier.weight(1f))
            // Status swatches preview the status color WITH its safety shape (D-01/D-02) — disabled_by_default
            // (stop) on stop, warning on caution; go stays shapeless. Makes "color is redundant to shape" visible.
            DataSwatch(t.stop, Modifier.weight(1f), glyphName = "disabled_by_default")
            DataSwatch(t.heat, Modifier.weight(1f), glyphName = "warning")
            DataSwatch(t.go, Modifier.weight(1f))
        }

        // ---- 4. POOL COLORS — per-slot override grid (D-09) ----------------------------------------
        SectionLabel("Pool colors")
        SubLabel("Tap a color to customize")
        val overrides = activeProfile?.poolOverrides ?: emptyMap()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            t.pool.take(4).forEachIndexed { i, c ->
                PoolSlotSwatch(
                    fill = c,
                    overridden = overrides.containsKey(i.toString()),
                    onClick = { editingSlot = i },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ---- 4b. STATUS COLORS — per-slot status override (D-03) -----------------------------------
        SectionLabel("Status colors")
        if (t.mode == PaletteMode.Colorful) {
            SubLabel("Tap to customize — shape carries the meaning, so color is yours")
        } else {
            SubLabel("Tap to customize (applies in Colorful; this mode shows the fixed safety palette)")
        }
        val statusOverrides = activeProfile?.poolOverrides ?: emptyMap()
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in StatusSlot.entries) {
                StatusSlotSwatch(
                    fill = effectiveStatusColor(t, s),
                    slot = s,
                    overridden = statusOverrides.containsKey(s.key),
                    onClick = { editingStatusSlot = s },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // ---- 5. ACTIONS — Randomize / Reset / Done -------------------------------------------------
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedControl(
                label = "Randomize",
                // WR-04: exclusive upper bound → [0,359], matching SHIFT_RANGE (360 ≡ 0 is a duplicate hue).
                onClick = { container.setActiveShift(hasActive, Random.nextInt(0, 360)) },
                modifier = Modifier.weight(1f),
                intent = Intent.Warn, // an unexpected live palette change — proceed-at-peril (amber).
            )
            OutlinedControl(
                label = "Reset",
                onClick = { pendingReset = true },
                modifier = Modifier.weight(1f),
                intent = Intent.Danger, // clears the user's overrides — destructive, guarded.
            )
        }
        OutlinedControl(
            label = "Done",
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            intent = Intent.Go, // backing out is non-destructive (changes already persisted live).
        )

        Box(Modifier.height(24.dp))
    }
}

/** The palette-mode chips (D-15) — `ThemeResolver` mode name → display label. Colorful is the default. */
private val PALETTE_MODES: List<Pair<String, String>> = listOf(
    ThemeResolver.MODE_COLORFUL to "Colorful",
    ThemeResolver.MODE_SIMPLE to "Simple",
    ThemeResolver.MODE_HIGH_CONTRAST to "High contrast",
)

/**
 * A text-size segment whose FILL is a pool ("traffic-light") color from the active palette, so the S/M/L
 * selector visibly reflects the current palette mode (and recolors when the mode flips). The fill is the
 * literal pool color (data carve-out); the SELECTED segment gets an accent ring so the pick is still
 * unambiguous. The label color is contrast-picked against the fill (ink on light fills, light on dark).
 */
@Composable
private fun PoolSizeSegment(
    label: String,
    fill: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val ink = if (fill.luminance() > 0.5f) Color(0xFF101010) else Color(0xFFF5F5F5)
    val base = modifier
        .height(64.dp) // ≥64dp touch floor (UI-02).
        .clip(shape)
        .background(fill)
    val outlined =
        if (selected) base.border(BorderStroke(3.dp, t.accentLine), shape)
        else base.border(BorderStroke(2.dp, t.outline), shape)
    Box(outlined.clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = ink,
            fontFamily = Geist,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
        )
    }
}

/** Curated preset seed swatches (D-06) — validated seeds covering the 90% case. Theme DATA (the seed being chosen). */
private val PRESET_SEEDS: List<String> = listOf(
    "#3f78ff", // signature blue (default)
    "#22c3a6", // teal
    "#8b5cf6", // violet
    "#ff8a3d", // warm orange
    "#e0457b", // magenta
    "#34d399", // green
)

/** A preset seed swatch — renders its literal seed color (carve-out); the selection ring is chromed (THEME-01). */
@Composable
private fun SeedSwatch(
    fill: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rPill)
    Box(
        modifier
            .height(64.dp) // ≥64dp touch floor (UI-02).
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(if (selected) 6.dp else 0.dp)
                .clip(shape)
                .background(fill),
        )
        if (selected) {
            Box(Modifier.fillMaxSize().border(BorderStroke(3.dp, t.accentLine), shape))
        }
    }
}

/**
 * A preview swatch — renders its actual generated color (carve-out). Display-only, no touch target.
 * An optional [glyphName] overlays a status safety shape (disabled_by_default / warning) so the preview
 * shows the status color WITH its shape (D-01/D-02); the glyph is tinted to the background for contrast.
 *
 * 18.1-03 (D-05/D-06): the overlay swapped from a `@DrawableRes glyph` to a Material Symbols ligature
 * by name. These are DECORATIVE preview swatches (no contentDescription), so a bare [MaterialSymbol] is
 * the minimal render path; the contrast [t.bg] tint + fsSp size are preserved 1:1 (THEME-01).
 */
@Composable
private fun DataSwatch(
    fill: Color,
    modifier: Modifier = Modifier,
    glyphName: String? = null,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            .background(fill)
            .border(BorderStroke(2.dp, t.outline), shape),
        contentAlignment = Alignment.Center,
    ) {
        if (glyphName != null) {
            MaterialSymbol(
                name = glyphName,
                tint = t.bg,
                sizeSp = fsSp(20f, t.fs),
            )
        }
    }
}

/** A per-slot pool override swatch (≥64dp, D-09) — its actual/overridden color; overridden = accent marker. */
@Composable
private fun PoolSlotSwatch(
    fill: Color,
    overridden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .height(64.dp) // ≥64dp touch floor (UI-02).
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(fill)
                .border(BorderStroke(2.dp, t.outline), shape),
        )
        if (overridden) {
            // The override-applied marker — an accent dot (NOT a glyph that repeats elsewhere on screen).
            Box(
                Modifier
                    .padding(6.dp)
                    .size(14.dp)
                    .clip(RoundedCornerShape(t.rPill))
                    .background(t.accent),
            )
        }
    }
}

/**
 * A per-status-slot swatch (≥64dp, D-03) — renders the EFFECTIVE status color WITH its safety shape
 * overlaid (octagon = stop, triangle = caution; go is shapeless per D-02), so the editor previews that
 * color is REDUNDANT to shape. `overridden` = the user has set a custom color → accent dot marker.
 */
@Composable
private fun StatusSlotSwatch(
    fill: Color,
    slot: StatusSlot,
    overridden: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier
            .height(64.dp) // ≥64dp touch floor (UI-02).
            .clip(shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(fill)
                .border(BorderStroke(2.dp, t.outline), shape),
        )
        // The safety SHAPE overlay (D-01/D-02) — explicit size (NOT the 96dp vector intrinsic). Tinted to
        // the contrasting background so the glyph reads on top of its own status color. 18.1-03 (D-05/D-06):
        // swapped from drawables to Material Symbols ligatures by name (decorative — bare MaterialSymbol).
        val glyphName = when (slot) {
            StatusSlot.Stop -> "disabled_by_default"
            StatusSlot.Caution -> "warning"
            StatusSlot.Go -> null // go is shapeless (D-02).
        }
        if (glyphName != null) {
            MaterialSymbol(
                name = glyphName,
                tint = t.bg,
                sizeSp = fsSp(28f, t.fs),
            )
        }
        if (overridden) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(14.dp)
                    .clip(RoundedCornerShape(t.rPill))
                    .background(t.accent),
            )
        }
    }
}

/**
 * The EFFECTIVE rendered status color for a slot — the resolved token (already mode-gated by the bridge:
 * a user override only changes these in Colorful; Simple collapses status to text and High-Contrast forces
 * fixed RYG). [Caution] maps to the `heat` token (D-13: dinghy `heat` IS caution).
 */
private fun effectiveStatusColor(t: ThemeTokens, slot: StatusSlot): Color =
    when (slot) {
        StatusSlot.Stop -> t.stop
        StatusSlot.Caution -> t.heat
        StatusSlot.Go -> t.go
    }

/** A human label for a status slot (titles/sub-labels). */
private fun StatusSlot.label(): String = when (this) {
    StatusSlot.Stop -> "Stop"
    StatusSlot.Caution -> "Caution"
    StatusSlot.Go -> "Go"
}

/** A human label for a palette mode (mode-awareness messaging). */
private fun PaletteMode.label(): String = when (this) {
    PaletteMode.Colorful -> "Colorful"
    PaletteMode.Simple -> "Simple"
    PaletteMode.HighContrast -> "High-contrast"
}

@Composable
private fun ScreenTitle(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text,
        fontFamily = Geist,
        fontWeight = FontWeight.Bold,
        fontSize = fsSp(22f, t.fs).sp,
    )
}

@Composable
private fun SectionLabel(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text2,
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = fsSp(20f, t.fs).sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SubLabel(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text3,
        fontFamily = Geist,
        fontSize = fsSp(15f, t.fs).sp,
    )
}

// ---- pure hue/hex helpers (host-trivial; the wheel picks HUE, the generator normalizes L/C) ----------

/** Hue (0..360) → a vivid seed hex "#RRGGBB" (full sat/value; the generator cusp-normalizes L/C). */
internal fun hueToHex(hue: Float): String {
    val argb = Color.hsv(((hue % 360f) + 360f) % 360f, 1f, 1f).toArgb()
    // CR-03: pin Locale.US — a non-Latin-digit locale (e.g. Persian/Arabic) would emit non-ASCII digits
    // that fail the HEX_SEED regex in ThemePrefs, silently dropping the user's seed on next launch.
    return "#%06X".format(java.util.Locale.US, argb and 0xFFFFFF)
}

/** Hue (0..360) → an opaque unsigned-32 ARGB Long (for a pool override). */
internal fun hueToArgbLong(hue: Float): Long {
    val argb = Color.hsv(((hue % 360f) + 360f) % 360f, 1f, 1f).toArgb()
    return argb.toLong() and 0xFFFFFFFFL
}

/** Parse a "#RRGGBB"/"RRGGBB"(/+alpha) hex into an opaque ARGB Int; junk → opaque black (never throws). */
internal fun parseHex(hex: String?): Int {
    if (hex == null) return 0xFF000000.toInt()
    val s = hex.removePrefix("#")
    val rgb = s.take(6).toIntOrNull(16) ?: return 0xFF000000.toInt()
    return 0xFF000000.toInt() or rgb
}

/** A Compose [Color] → its hue (0..360) for positioning the wheel handle (WR-06: seed picker from current). */
internal fun colorToHue(color: Color): Float {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    return hsv[0]
}

/** A seed hex → its hue (0..360) for positioning the wheel handle. Junk → 0. */
internal fun seedHexToHue(hex: String?): Float {
    val argb = parseHex(hex)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(argb, hsv)
    return hsv[0]
}

/** Two hues are "the same preset" within ~8°. */
private fun approxSameHue(a: Float, b: Float): Boolean {
    val d = kotlin.math.abs(((a - b + 540f) % 360f) - 180f)
    return d <= 8f
}
