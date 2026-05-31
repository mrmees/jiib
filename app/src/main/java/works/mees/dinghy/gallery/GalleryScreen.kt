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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.bench.SyntheticFeed
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.ScrubberPage
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.render.GraphViewHost
import works.mees.dinghy.render.ProgressRing
import works.mees.dinghy.render.RingBuffer
import works.mees.dinghy.state.PrinterStateStore
import works.mees.dinghy.theme.FontScale
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeBase
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.theme.TokenDelta
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
    var base by remember { mutableStateOf(ThemeBase.Dark) }
    var custom by remember { mutableStateOf(false) }
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
        SectionHeader("Dinghy Display — Component Gallery (debug)")

        // ---- THEME CONTROLS: every toggle drives the INJECTED resolver so the matrix re-themes ---
        SectionLabel("Theme controls")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedControl(
                label = "Dark",
                onClick = { base = ThemeBase.Dark; resolver.setBase(ThemeBase.Dark) },
                modifier = Modifier.weight(1f),
                intent = if (base == ThemeBase.Dark) Intent.Accent else Intent.Neutral,
            )
            OutlinedControl(
                label = "Light",
                onClick = { base = ThemeBase.Light; resolver.setBase(ThemeBase.Light) },
                modifier = Modifier.weight(1f),
                intent = if (base == ThemeBase.Light) Intent.Accent else Intent.Neutral,
            )
            OutlinedControl(
                label = if (custom) "Custom ✓" else "Custom",
                onClick = {
                    custom = !custom
                    resolver.setDeltas(if (custom) SAMPLE_CUSTOM_DELTA else TokenDelta.EMPTY)
                },
                modifier = Modifier.weight(1f),
                intent = if (custom) Intent.Go else Intent.Neutral,
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

        // ---- SEVERITY TOAST — all four severities -----------------------------------------------
        SectionLabel("SeverityToast — four severities")
        SeverityToast(Severity.Info, "Info — informational notice", Modifier.fillMaxWidth())
        SeverityToast(Severity.Success, "Success — print started", Modifier.fillMaxWidth())
        SeverityToast(Severity.Warning, "Warning — proceed at peril", Modifier.fillMaxWidth())
        SeverityToast(Severity.Error, "Error — connection lost", Modifier.fillMaxWidth())

        // ---- SCREEN SCAFFOLD (Focus/Field/Gutter) — orientation-responsive in place -------------
        SectionLabel("ScreenScaffold — Focus / Field / Gutter (rotate device for orientation)")
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
                    }
                },
                gutter = {
                    Row(
                        Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedControl("Back", {}, Modifier.weight(1f), Intent.Danger)
                        OutlinedControl("Set", {}, Modifier.weight(1f), Intent.Neutral)
                        OutlinedControl("Go", {}, Modifier.weight(1f), Intent.Go)
                    }
                },
            )
        }

        // ---- SCRUBBER PAGE (PRIM-01) — keyboard-free numeric entry ------------------------------
        SectionLabel("ScrubberPage — keyboard-free setpoint")
        var nozzle by remember { mutableFloatStateOf(200f) }
        Box(Modifier.fillMaxWidth().height(320.dp)) {
            ScrubberPage(
                label = "Nozzle",
                value = nozzle,
                range = 0f..300f,
                step = 5f,
                unit = "°C",
                onValueChange = { nozzle = it },
                onCancel = {},
                onApply = { nozzle = it },
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

/** A sample custom-theme delta (D-01) recoloring the four signature roles + bg so "Custom" is obvious. */
private val SAMPLE_CUSTOM_DELTA: TokenDelta = TokenDelta.of(
    TokenDelta.Role.Accent to 0xFF8B5CF6.toInt(), // violet
    TokenDelta.Role.Heat to 0xFFFB923C.toInt(),   // warm orange
    TokenDelta.Role.Go to 0xFF34D399.toInt(),     // teal-green
    TokenDelta.Role.Stop to 0xFFF472B6.toInt(),   // pink-red
)

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
