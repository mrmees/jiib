package works.mees.dinghy.dev

import android.graphics.Bitmap
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.theme.compose.DinghyTheme
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

// ─────────────────────────────────────────────────────────────────────────────
// Spike constants (file-level so they are accessible from all composable fns)
// ─────────────────────────────────────────────────────────────────────────────

/** Console scrollback cap — mirrors ConsoleScrollback.DEFAULT_CAPACITY. */
private const val CONSOLE_CAP = 300

/** Seed line count for the Console scene (pre-filled before ticker starts). */
private const val CONSOLE_SEED = 300

/** Append rate in ms per line — ~10 lines/sec to match Klipper live burst rate. */
private const val CONSOLE_APPEND_INTERVAL_MS = 100L

/** Number of synthetic file rows in the Files scene. */
private const val FILES_ROW_COUNT = 60

/**
 * Gcode-style console line templates. Indexed modulo 10 in [syntheticConsoleLine].
 * File-level val — not in a companion — so the [ConsoleSpike] composable can access it directly.
 */
private val CONSOLE_LINE_TEMPLATES = listOf(
    "Send: G1 X{X}.{x2} Y{Y}.{y2} F{F}",
    "Recv: ok P{p} B{b}",
    "Send: M105",
    "Recv: ok T:{T}.{t2} /195.0 B:{B}.{b2} /60.0",
    "Send: G28",
    "Recv: // probe at {px},{py} is z=-{pz}.{p4}",
    "Send: SAVE_GCODE_STATE NAME=PAUSE_STATE",
    "Recv: // toolhead: X:{X}.{x2} Y:{Y}.{y2} Z:{Z}.{z2} E:{E}.{e2}",
    "!! Error: Probing failed! Requested position ({ex},{ey})",
    "// probe: {state}",
)

/** Generates a gcode-style synthetic console line for the given [index]. */
private fun syntheticConsoleLine(index: Int): String = when (index % 10) {
    0 -> "Send: G1 X${index % 200}.${"%02d".format(index % 100)} Y${(index * 3) % 200}.${"%02d".format((index * 7) % 100)} F${3000 + (index % 1000)}"
    1 -> "Recv: ok P${index % 16} B${index % 16}"
    2 -> "Send: M105"
    3 -> "Recv: ok T:${195 + (index % 5)}.${index % 10} /195.0 B:${60 + (index % 5)}.${index % 10} /60.0"
    4 -> "Send: G28"
    5 -> "Recv: // probe at ${index % 10},${index % 8} is z=${-(index % 3)}.${index % 9999}"
    6 -> "Send: SAVE_GCODE_STATE NAME=PAUSE_STATE"
    7 -> "Recv: // toolhead: X:${index % 200}.${"%02d".format(index % 100)} Y:${(index * 3) % 200}.${"%02d".format((index * 7) % 100)} Z:${index % 30}.${index % 100} E:${index % 50}.${index % 100}"
    8 -> if (index % 20 == 0) "!! Error: Probing failed! Requested position (${index % 5}, ${index % 3})" else "Recv: // Klipper state: Ready"
    else -> "// probe: ${if (index % 2 == 0) "open" else "triggered"}"
}

/**
 * Throwaway toolkit-spike harness for the Files and Console list surfaces.
 *
 * THROWAWAY — this file is deleted in plan 25-07 after the spike decision is locked.
 * Do NOT add Moonraker, navigation, or production feature code here.
 *
 * ## Launch contract
 *
 *   adb shell am start -n works.mees.dinghy/.dev.BrowseSpikeActivity --es spike_surface files
 *   adb shell am start -n works.mees.dinghy/.dev.BrowseSpikeActivity --es spike_surface console
 *
 * ## Files scene (Issue 5a — representative thumbnail cost)
 * A [ListBlock] of [FILES_ROW_COUNT] synthetic file rows. Each row carries a decoded Bitmap image
 * at the same target dimensions as FileRowsAdapter's thumbnail ImageView (48×48dp → 96×96px on
 * flox). The bitmaps are programmatically generated ARGB_8888 solids with varied colors — forcing
 * per-row image draw cost rather than an empty Box, without requiring an HTTP base or Coil network.
 *
 * ## Console scene (Issue 5b — live append+evict churn)
 * A [LazyColumn] seeded with [CONSOLE_SEED] synthetic lines driven by a live-append+evict coroutine
 * at [CONSOLE_APPEND_INTERVAL_MS] (≈10 lines/sec). Eviction once the cap is reached mirrors the
 * ConsoleListView's isAppendEvict path. Measurement MUST be taken while the ticker is running.
 *
 * @see works.mees.dinghy.bench.BenchActivity the existing throwaway-activity precedent
 */
class BrowseSpikeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val surface = intent?.getStringExtra(EXTRA_SURFACE) ?: SURFACE_FILES
        val resolver = ThemeResolver()

        setContent {
            DinghyTheme(resolver = resolver) {
                when (surface) {
                    SURFACE_CONSOLE -> ConsoleSpike(modifier = Modifier.fillMaxSize())
                    else -> FilesSpike(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    companion object {
        /** Intent extra key selecting which spike surface to render. */
        const val EXTRA_SURFACE = "spike_surface"

        /** Render the Files list spike (thumbnail rows). Default if extra is absent. */
        const val SURFACE_FILES = "files"

        /** Render the Console list spike (live append+evict churn). */
        const val SURFACE_CONSOLE = "console"
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Files spike scene
// ─────────────────────────────────────────────────────────────────────────────

/** Synthetic data for a single file row in the spike. */
private data class SpikeFileRow(
    val id: String,
    val name: String,
    val meta: String,
    val thumbBitmap: Bitmap,
)

/**
 * Builds [FILES_ROW_COUNT] synthetic file rows with varied names and programmatically generated
 * ARGB_8888 thumbnail bitmaps (96×96px — 48dp × ~2x density on flox).
 *
 * Colors vary per row so each bitmap is visually distinct and Compose measures real per-row image
 * draw cost (not a zero-cost empty surface).
 */
private fun buildSyntheticFiles(): List<SpikeFileRow> {
    val gcodeNames = listOf(
        "benchy_pla_0.2mm.gcode", "calibration_cube.gcode", "lithophane_frame.gcode",
        "voron_panel_clip.gcode", "bracket_abs_0.15.gcode", "phone_stand_petg.gcode",
        "cooling_duct_v3.gcode", "nozzle_wipe_macro.gcode", "bed_probe_test.gcode",
        "retraction_tower.gcode", "temp_tower_pla.gcode", "speed_test_100mm.gcode",
        "part_a_left_side.gcode", "part_b_right_side.gcode", "enclosure_latch.gcode",
        "ender5_fang_v2.gcode", "cable_chain_link.gcode", "spool_holder_remix.gcode",
        "logo_jiib_small.gcode", "cable_tie_mount.gcode",
    )
    val sizes = listOf("2.3 MB", "1.1 MB", "4.8 MB", "890 KB", "3.2 MB", "640 KB", "5.1 MB")
    val dates = listOf(
        "Jun 8, 14:22", "Jun 7, 09:15", "Jun 6, 22:00",
        "Jun 5, 17:34", "Jun 4, 11:50", "Jun 3, 08:20", "Jun 2, 19:47",
    )
    // Varied hues so each bitmap is a distinct color (represents varied thumbnail content).
    val colors = intArrayOf(
        0xFF1A3A5C.toInt(), 0xFF2E5C3A.toInt(), 0xFF5C3A1A.toInt(), 0xFF3A1A5C.toInt(),
        0xFF1A5C4E.toInt(), 0xFF5C1A2E.toInt(), 0xFF4E5C1A.toInt(), 0xFF1A4E5C.toInt(),
    )
    return (0 until FILES_ROW_COUNT).map { i ->
        val baseName = gcodeNames[i % gcodeNames.size]
        val name = if (i >= gcodeNames.size) "copy${i / gcodeNames.size}_$baseName" else baseName
        val color = colors[i % colors.size]
        // 96px = 48dp × ~2x density (flox Nexus 7 2013). ARGB_8888 so the per-pixel
        // cost matches a real decoded JPEG thumbnail (same Bitmap format Coil decodes to).
        val bmp = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).also { it.eraseColor(color) }
        SpikeFileRow(
            id = "file_$i",
            name = name,
            meta = "${sizes[i % sizes.size]}  ${dates[i % dates.size]}",
            thumbBitmap = bmp,
        )
    }
}

/**
 * Files spike scene — a literal `ListBlock { items(fakeFiles, key) { ListRow } }` with per-row
 * thumbnail images. Represents what plan 25-03 would ship as the Compose Files list.
 *
 * Each row uses the [ListRow] kit class (not a hand-rolled row) with a leading [Image] slot sized to
 * match the existing FileRowsAdapter ImageView (48×48dp). Stable `key = { it.id }`.
 */
@Composable
private fun FilesSpike(modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val files = remember { buildSyntheticFiles() }
    var selectedId by remember { mutableStateOf<String?>(null) }

    BoxWithConstraints(modifier) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ListBlock(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            items(files, key = { it.id }) { file ->
                ListRow(
                    selected = file.id == selectedId,
                    onClick = { selectedId = file.id },
                    uDp = grid.uDp,
                    leadingContent = {
                        Image(
                            bitmap = file.thumbBitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).padding(end = 8.dp),
                        )
                    },
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.name,
                            color = t.text,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fsSp(17f, t.fs).sp,
                            maxLines = 1,
                        )
                        Text(
                            text = file.meta,
                            color = t.text2,
                            fontSize = fsSp(13f, t.fs).sp,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Console spike scene
// ─────────────────────────────────────────────────────────────────────────────

/** Builds the initial [CONSOLE_SEED] synthetic console lines. */
private fun buildSeedLines(): List<String> = (0 until CONSOLE_SEED).map { syntheticConsoleLine(it) }

/**
 * Console spike scene — a [LazyColumn] seeded with ~300 lines driven by a live-append+evict ticker.
 *
 * Uses `reverseLayout = false` with programmatic scroll-to-bottom (equivalent to RecyclerView's
 * `stackFromEnd = true` + scrollToPosition on new append). The [LaunchedEffect] ticker appends one
 * new synthetic gcode line per [CONSOLE_APPEND_INTERVAL_MS] and evicts the oldest once the list
 * exceeds [CONSOLE_CAP] — exactly the steady-state append+evict path that the real ConsoleListView
 * handles via `isAppendEvict`.
 *
 * MEASUREMENT NOTE: capture gfxinfo WHILE this ticker is running and the list is auto-scrolling.
 * A static frozen list under-tests the real append+evict work (Issue 5b per the plan).
 *
 * Uses a [LazyColumn] directly (not [ListBlock]) because [ListBlock] holds internal
 * [rememberLazyListState] — the Console spike needs an externally-controlled [listState] for the
 * programmatic auto-scroll. Stable `key` derivation: index-based — line content can repeat across
 * the ring, so a content-hash alone would collide; combining index ensures uniqueness.
 */
@Composable
private fun ConsoleSpike(modifier: Modifier = Modifier) {
    val t = LocalTokens.current

    var lines by remember { mutableStateOf(buildSeedLines()) }
    var lineCounter by remember { mutableStateOf(CONSOLE_SEED) }

    // Live append+evict ticker — fires at ~10 lines/sec. Runs for the duration of the activity.
    LaunchedEffect(Unit) {
        while (true) {
            delay(CONSOLE_APPEND_INTERVAL_MS)
            val newLine = syntheticConsoleLine(lineCounter++)
            lines = if (lines.size >= CONSOLE_CAP) {
                // Evict oldest (drop index 0), append newest — mirrors isAppendEvict steady-state.
                lines.drop(1) + newLine
            } else {
                lines + newLine
            }
        }
    }

    val listState = rememberLazyListState()

    // Auto-scroll to last item on every new append — mirrors ConsoleListView wasAtBottom logic.
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.scrollToItem(lines.size - 1)
        }
    }

    BoxWithConstraints(modifier) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        ) {
            // Stable key: combine list position (unique per evict-cycle) with a content fragment.
            // Position-only keys would collide during eviction; content-hash alone can collide
            // when lines repeat. Together they are unique across the live-churn ring.
            items(lines.size, key = { idx -> idx.toLong() * 10007L + lines[idx].length }) { idx ->
                val line = lines[idx]
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        text = line,
                        color = when {
                            line.startsWith("!!") -> t.stop
                            line.startsWith("// ") -> t.text2
                            line.startsWith("Send:") -> t.text
                            else -> t.text
                        },
                        fontSize = fsSp(14f, t.fs).sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
