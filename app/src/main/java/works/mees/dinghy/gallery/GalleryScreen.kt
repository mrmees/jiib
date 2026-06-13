package works.mees.dinghy.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.bench.SyntheticFeed
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.render.GraphViewHost
import works.mees.dinghy.render.ProgressRing
import works.mees.dinghy.render.RingBuffer
import works.mees.dinghy.state.PrinterStateStore
import works.mees.dinghy.theme.DEFAULT_SEED_HEX
import works.mees.dinghy.theme.FontScale
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The in-APK component gallery (D-07) — the primary ON-DEVICE preview/sign-off surface. It renders
 * the full token × component × theme × `--fs` matrix so Matthew can install the debug APK, eyeball
 * every primitive against the `docs/ui_design/images` mockups, and flip dark/light/custom + S/M/L on the
 * real flox tablet to confirm BOTH the Compose surfaces AND the classic-Views [GraphViewHost] graph
 * re-theme together (the cross-toolkit token-remap proof, success criterion #1).
 *
 * ## Dependency injection — the screen OWNS nothing (Phase-4 boundary)
 * `GalleryScreen` ACCEPTS every dependency and CONSTRUCTS none of them. Its parameters are:
 *  - [resolver] — the live [ThemeResolver]; every theme control (base / sample custom delta / S-M-L)
 *    drives THIS resolver so the whole matrix re-themes live. The screen never builds a resolver.
 *  - [printerStateStore] — the NULLABLE live Phase-2 spine (D-14). When non-null the feed-source
 *    toggle offers a "Live" source that reads heater temps off [PrinterStateStore.printerState];
 *    when null only the synthetic source is offered. The screen CONSUMES this StateFlow — it NEVER
 *    opens a Moonraker connection / socket / app-bootstrap (that is Phase 4, and [GalleryActivity]
 *    is the sole assembler). This screen contains no transport/socket/client code whatsoever.
 *  - [feedSource] / [onFeedSourceChange] — the hoisted feed-source selector; the host owns the choice
 *    and the screen renders a toggle that calls back. [FeedSource.LIVE] is only selectable when a
 *    live store was injected.
 *
 * ## Ring/graph fed from BOTH sources (D-14)
 * One [RingBuffer] backs the [ProgressRing] (last sample → a fake 0..1 fraction) and the
 * [GraphViewHost] (full snapshot). A [LaunchedEffect] keyed on the active source drives it:
 *  - [FeedSource.SYNTHETIC] collects `SyntheticFeed().events()` at ~3 Hz (the deterministic perf
 *    feed, reused — NOT a second feed) and pushes the extruder temp.
 *  - [FeedSource.LIVE] collects the injected `printerStateStore.printerState` and pushes the
 *    `extruder` heater's live temperature — real data off the already-assembled spine.
 *
 * The active [LocalTokens] are collected and passed to [GraphViewHost] so a theme flip recolors the
 * Views Canvas at the same instant the Compose surfaces remap — one source of truth, two toolkits.
 */
enum class FeedSource { SYNTHETIC, LIVE }

@Composable
fun GalleryScreen(
    resolver: ThemeResolver,
    printerStateStore: PrinterStateStore?,
    feedSource: FeedSource,
    onFeedSourceChange: (FeedSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTokens.current

    // ---- theme-control state mirrored locally so the toggles can show the current pick ----------
    var dark by remember { mutableStateOf(true) }
    var altSeed by remember { mutableStateOf(false) }
    var fsChoice by remember { mutableStateOf(FontScale.M) }

    // ---- the ONE ring buffer both render primitives draw from, fed by the selected source -------
    val ring = remember { RingBuffer() }
    // A small piece of recomposition-visible state so a push triggers a redraw of the Compose ring.
    var graphSnapshot by remember { mutableStateOf(FloatArray(0)) }
    var ringFraction by remember { mutableFloatStateOf(0f) }

    // SyntheticFeed (D-14 source A) — the deterministic ~3 Hz perf feed, REUSED (not re-authored).
    LaunchedEffect(feedSource, printerStateStore) {
        ring.clear()
        when (feedSource) {
            FeedSource.SYNTHETIC -> {
                SyntheticFeed().events().collect { event ->
                    ring.push(event.extruderTemp.toFloat())
                    graphSnapshot = ring.snapshot()
                    // Map the synthetic progress straight onto the ring fraction for the preview.
                    ringFraction = event.progress.toFloat()
                }
            }
            FeedSource.LIVE -> {
                // D-14 source B — CONSUME the injected live spine; never open a connection here.
                val store = printerStateStore ?: return@LaunchedEffect
                store.printerState.collect { state ->
                    val extruder = state.heaters["extruder"]?.temperature ?: 0.0
                    ring.push(extruder.toFloat())
                    graphSnapshot = ring.snapshot()
                    ringFraction = state.progress.toFloat()
                }
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SectionHeader("jiib — Component Gallery (debug)")

        // ---- THEME CONTROLS: every toggle drives the INJECTED resolver so the matrix re-themes ---
        SectionLabel("Theme controls")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedControl(
                label = "Dark",
                onClick = { dark = true; resolver.setDark(true) },
                modifier = Modifier.weight(1f),
                intent = if (dark) Intent.Accent else Intent.Neutral,
            )
            OutlinedControl(
                label = "Light",
                onClick = { dark = false; resolver.setDark(false) },
                modifier = Modifier.weight(1f),
                intent = if (!dark) Intent.Accent else Intent.Neutral,
            )
            // D-04: chrome is seed-derived now (no per-role override). The "Seed" toggle flips between
            // the default seed and a sample alternate seed so the whole generated palette re-themes —
            // proving the generate-and-cache seed model (replaces the retired custom-delta toggle).
            OutlinedControl(
                label = if (altSeed) "Seed ✓" else "Seed",
                onClick = {
                    altSeed = !altSeed
                    resolver.setSeed(if (altSeed) SAMPLE_ALT_SEED else DEFAULT_SEED_HEX)
                },
                modifier = Modifier.weight(1f),
                intent = if (altSeed) Intent.Go else Intent.Neutral,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for (choice in FontScale.entries) {
                OutlinedControl(
                    label = choice.name,
                    onClick = { fsChoice = choice; resolver.setFs(choice.multiplier) },
                    modifier = Modifier.weight(1f),
                    intent = if (fsChoice == choice) Intent.Accent else Intent.Neutral,
                )
            }
        }

        // ---- FEED-SOURCE SELECTOR (D-14): synthetic always; live only when a store was injected ---
        SectionLabel("Render feed source")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedControl(
                label = "Synthetic",
                onClick = { onFeedSourceChange(FeedSource.SYNTHETIC) },
                modifier = Modifier.weight(1f),
                intent = if (feedSource == FeedSource.SYNTHETIC) Intent.Accent else Intent.Neutral,
            )
            OutlinedControl(
                label = if (printerStateStore == null) "Live (n/a)" else "Live",
                onClick = { if (printerStateStore != null) onFeedSourceChange(FeedSource.LIVE) },
                modifier = Modifier.weight(1f),
                intent = if (feedSource == FeedSource.LIVE) Intent.Accent else Intent.Neutral,
            )
        }

        // ---- RENDER PRIMITIVES driven by the ring buffer (both toolkits) -------------------------
        SectionLabel("ProgressRing (Compose Canvas) + GraphView (Views Canvas)")
        Row(
            Modifier.fillMaxWidth().height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
                ProgressRing(progress = ringFraction, modifier = Modifier.fillMaxSize())
            }
            // The Views graph receives the CURRENT tokens — a theme flip recolors the Canvas too.
            GraphViewHost(
                tokens = tokens,
                snapshot = graphSnapshot,
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        }

        // ---- OUTLINED CONTROL — all five intents ------------------------------------------------
        SectionLabel("OutlinedControl — five intents")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedControl("Neutral", {}, Modifier.weight(1f), Intent.Neutral)
            OutlinedControl("Accent", {}, Modifier.weight(1f), Intent.Accent)
            OutlinedControl("Warn", {}, Modifier.weight(1f), Intent.Warn)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedControl("Danger", {}, Modifier.weight(1f), Intent.Danger)
            OutlinedControl("Go", {}, Modifier.weight(1f), Intent.Go)
        }

        // ---- DINGHY ICON REGISTRY — every DinghyIcons.all entry ---------------------------------
        // The on-device proof (D-07) that every registered icon resolves + shows its remap-handle
        // (`alternate`) name. A simple chunked Row grid (NOT LazyVerticalGrid — this Column is
        // already inside a verticalScroll). Labels use the established fsSp(13f) scale (>=13sp floor,
        // per CLAUDE.md font LAW) — NEVER a raw unscaled .sp literal.
        SectionLabel("DinghyIcon registry — every DinghyIcons.all entry")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (row in DinghyIcons.all.chunked(4)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (icon in row) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            DinghyIconView(
                                icon = icon,
                                tint = tokens.text,
                                sizeDp = 28.dp,
                                contentDescription = null,
                            )
                            Text(
                                text = icon.alternate,
                                color = tokens.text2,
                                fontFamily = GeistMono,
                                fontSize = fsSp(13f, tokens.fs).sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // ---- SEVERITY TOAST — all four severities -----------------------------------------------
        SectionLabel("SeverityToast — four severities")
        SeverityToast(Severity.Info, "Info — informational notice", Modifier.fillMaxWidth())
        SeverityToast(Severity.Success, "Success — print started", Modifier.fillMaxWidth())
        SeverityToast(Severity.Warning, "Warning — proceed at peril", Modifier.fillMaxWidth())
        SeverityToast(Severity.Error, "Error — connection lost", Modifier.fillMaxWidth())

        // ---- SCREEN SCAFFOLD (Focus/Field) — orientation-responsive in place --------------------
        SectionLabel("ScreenScaffold — Focus / Field + foot bar (rotate device for orientation)")
        Box(Modifier.fillMaxWidth().height(280.dp)) {
            ScreenScaffold(
                focus = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Box(Modifier.aspectRatio(1f).padding(12.dp)) {
                            ProgressRing(progress = ringFraction, modifier = Modifier.fillMaxSize())
                        }
                    }
                },
                field = {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Field region",
                            color = tokens.text,
                            fontFamily = GeistMono,
                            fontSize = fsSp(16f, tokens.fs).sp,
                        )
                        OutlinedControl("Field action", {}, Modifier.fillMaxWidth(), Intent.Accent)
                        Box(Modifier.weight(1f))
                        // Foot-of-list action bar — the retired gutter slot's successor.
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedControl("Back", {}, Modifier.weight(1f), Intent.Accent)
                            OutlinedControl("Set", {}, Modifier.weight(1f), Intent.Warn)
                            OutlinedControl("Go", {}, Modifier.weight(1f), Intent.Go)
                        }
                    }
                },
            )
        }

        // ---- CONFIRM GUARD (PRIM-03) — full-screen decision ------------------------------------
        SectionLabel("ConfirmGuard — full-screen safety gate")
        var showGuard by remember { mutableStateOf(false) }
        if (showGuard) {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                ConfirmGuard(
                    title = "Stop print?",
                    message = "The current print will be cancelled and cannot be resumed.",
                    confirmLabel = "Stop",
                    onConfirm = { showGuard = false },
                    onCancel = { showGuard = false },
                )
            }
        } else {
            OutlinedControl(
                label = "Show Confirm guard",
                onClick = { showGuard = true },
                modifier = Modifier.fillMaxWidth(),
                intent = Intent.Danger,
            )
        }

        // Bottom breathing room so the last component clears the scroll edge.
        Box(Modifier.height(24.dp))
    }
}

/** A sample ALTERNATE seed (D-04) — a vivid violet so the "Seed" toggle visibly re-themes the whole palette. */
private const val SAMPLE_ALT_SEED: String = "#8b5cf6"

@Composable
private fun SectionHeader(text: String) {
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
        fontFamily = GeistMono,
        fontWeight = FontWeight.Medium,
        fontSize = fsSp(13f, t.fs).sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}
