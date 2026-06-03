package works.mees.dinghy.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import works.mees.dinghy.calibration.BedMeshVm
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.BedMeshProfileArgs
import works.mees.dinghy.command.DispatchEvent
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.render.BedMeshHeatmapHost
import works.mees.dinghy.ui.screen.TokenTextField
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The Bed-Mesh page (CALIB-04 / UI-SPEC §4) — the overhead heatmap + scale toggle + profile management.
 *
 *  - Focus = [BedMeshHeatmapHost] (passing the collected [tokens] + [BedMeshVm.model] + scaleMode),
 *    `aspectRatio(1f)` centered, with the scale-mode toggle overlaid top-left (white/setting intent,
 *    taps [onCycleScaleMode] — pure color re-map, no re-probe, D-09). Empty-state (Pitfall 4) shows
 *    "No active mesh" copy with Activate/Load still present.
 *  - Field = two rows. Row 1 is the full-width primary action: **Activate** (blue, dispatches
 *    [CommandRegistry.bedMeshCalibrate] — bare, KAMP defaults) when homed, else **Home All** (blue,
 *    [CommandRegistry.homeAll]) — BED_MESH_CALIBRATE probes the bed, so it is homed-gated (D-13). Row 2 is
 *    two equal columns: **Save** (blue → full-screen Save dialog → [CommandRegistry.bedMeshProfileSave] →
 *    amber [ConfirmGuard] SAVE_CONFIG restart gate → [CommandRegistry.saveConfig]) and **Load** (full-screen
 *    scrollable selector → per-row red Remove + Apply → [CommandRegistry.bedMeshProfileLoad] /
 *    [CommandRegistry.bedMeshProfileRemove]).
 *  - Gutter = single green **Back**.
 *
 * The Save-name field is keyboard-editable (the owner carve-out) — it is validated with
 * [PrinterCommands.isValidProfileName] (the 09-02 allowlist) BEFORE the dialog's Save gutter enables, so
 * an invalid name never reaches [bedMeshProfileSave] (T-09-05-03). Ratio-only sizing; token-only color;
 * the screen renders [BedMeshVm] verbatim (no raw-JSON re-walk).
 *
 * @param vm                the resolved [BedMeshVm] (heatmap model + scale mode + profiles + error).
 * @param tokens            the active resolved tokens (passed to the Views heatmap host; THEME-01).
 * @param dispatcher        the live session dispatcher; all actions go through it (null until a session).
 * @param onCycleScaleMode  cycle the holder's color-scale mode (D-09 — re-color only, no re-probe).
 * @param onBack            leave the page (green Back).
 */
@Composable
fun BedMeshScreen(
    vm: BedMeshVm,
    tokens: ThemeTokens,
    dispatcher: CommandDispatcher?,
    onCycleScaleMode: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<MeshDialog?>(null) }
    var saveName by remember { mutableStateOf("") }
    var removeTarget by remember { mutableStateOf<String?>(null) }
    var successText by remember { mutableStateOf<String?>(null) }

    // In-flight guard (the sibling ScrewsTilt pattern): while BED_MESH_CALIBRATE is running the Activate
    // button shows a wait glyph and is disabled, so it can't be fired twice before the mesh comes back.
    val inFlight by remember(dispatcher) {
        dispatcher?.inFlight ?: MutableStateFlow(emptySet())
    }.collectAsStateWithLifecycle(initialValue = emptySet())
    val calibrating = CommandRegistry.bedMeshCalibrate.dispatchKey(Unit) in inFlight

    // Auto-dismiss the success toast (Save confirmation) so it doesn't linger on the page.
    LaunchedEffect(successText) {
        if (successText != null) {
            delay(3_500)
            successText = null
        }
    }

    // Dismissable error toast — drive from local state so a rejected Activate/Save/Load can be dismissed
    // (tap) and auto-clears; the holder's folded errorText persists, which previously stuck the popup on
    // screen. Seeded from live dispatcher Failures AND the holder fold (a failure predating this collector).
    var toastError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dispatcher) {
        val d = dispatcher ?: return@LaunchedEffect
        d.events.collect { event -> if (event is DispatchEvent.Failure) toastError = event.message }
    }
    LaunchedEffect(vm.errorText) { vm.errorText?.let { toastError = it } }
    LaunchedEffect(toastError) {
        if (toastError != null) {
            delay(5_000)
            toastError = null
        }
    }

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            focus = {
                BedMeshFocus(
                    vm = vm,
                    tokens = tokens,
                    onCycleScaleMode = onCycleScaleMode,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            },
            field = {
                val t = LocalTokens.current
                Column(
                    Modifier.fillMaxSize().padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Current mesh name (D-07) — a small line above the primary action.
                    Text(
                        text = if (vm.isEmpty) "No active mesh" else "Mesh: ${vm.model.profileName}",
                        color = t.text2,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = fsSp(14f, t.fs).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                    // Row 1 — primary action, full width. BED_MESH_CALIBRATE probes the bed, so it is
                    // homed-gated (D-13): unhomed → Home-All (G28); while calibrating → a DISABLED wait
                    // glyph so it can't be fired twice before the mesh returns; otherwise Activate.
                    when {
                        !vm.homed -> MeshFieldButton(
                            label = "Home All",
                            symbol = "home",
                            onClick = { dispatcher?.dispatch(CommandRegistry.homeAll, Unit) },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            intent = Intent.Accent,
                            enabled = dispatcher != null,
                        )
                        calibrating -> MeshFieldButton(
                            label = "Calibrating…",
                            symbol = "hourglass_top",
                            onClick = {},
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            intent = Intent.Accent,
                            enabled = false,
                        )
                        else -> MeshFieldButton(
                            label = "Activate",
                            symbol = "blur_on",
                            onClick = { dispatcher?.dispatch(CommandRegistry.bedMeshCalibrate, Unit) },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            intent = Intent.Accent,
                            enabled = dispatcher != null,
                        )
                    }
                    // Row 2 — Save | Load (two equal columns, both blue/accent).
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MeshFieldButton(
                            label = "Save",
                            symbol = "save",
                            onClick = {
                                saveName = defaultProfileName()
                                dialog = MeshDialog.Save
                            },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            intent = Intent.Accent,
                            enabled = dispatcher != null,
                        )
                        MeshFieldButton(
                            label = "Load",
                            symbol = "folder_open",
                            onClick = { dialog = MeshDialog.Load },
                            modifier = Modifier.weight(1f).fillMaxSize(),
                            intent = Intent.Accent,
                            enabled = dispatcher != null && vm.profileNames.isNotEmpty(),
                        )
                    }
                }
            },
            gutter = {
                Row(Modifier.fillMaxWidth().padding(8.dp)) {
                    MeshGutterButton(
                        label = "Back",
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth(),
                        intent = Intent.Go,
                    )
                }
            },
        )

        // --- Error / success toasts (bottom overlay) --- success auto-dismisses; error is tap-dismissable
        // (and auto-clears) so a rejected routine no longer leaves a stuck popup.
        val toast = successText?.let { Severity.Success to it } ?: toastError?.let { Severity.Error to it }
        if (toast != null) {
            Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                SeverityToast(
                    toast.first,
                    toast.second,
                    Modifier.fillMaxWidth().clickable { toastError = null },
                )
            }
        }

        // --- Save name dialog (full-screen) ---
        if (dialog == MeshDialog.Save) {
            val valid = PrinterCommands.isValidProfileName(saveName)
            SaveNameDialog(
                name = saveName,
                valid = valid,
                onNameChange = { saveName = it },
                onSave = {
                    if (valid) {
                        // BED_MESH_PROFILE SAVE persists the named profile and it is usable immediately —
                        // meshes load dynamically, so NO SAVE_CONFIG / firmware restart (owner decision).
                        dispatcher?.dispatch(CommandRegistry.bedMeshProfileSave, BedMeshProfileArgs(saveName))
                        successText = "Profile $saveName saved"
                        dialog = null
                    }
                },
                onCancel = { dialog = null },
            )
        }

        // --- Load selector (full-screen scrollable) ---
        if (dialog == MeshDialog.Load) {
            LoadSelectorDialog(
                profiles = vm.profileNames,
                onApply = { name ->
                    dispatcher?.dispatch(CommandRegistry.bedMeshProfileLoad, BedMeshProfileArgs(name))
                    dialog = null
                },
                onRemove = { name -> removeTarget = name },
                onCancel = { dialog = null },
            )
        }

        // --- Red Remove-profile guard (D-10) ---
        removeTarget?.let { name ->
            ConfirmGuard(
                title = "Remove profile?",
                message = "Delete saved mesh profile \"$name\". This cannot be undone.",
                confirmLabel = "Remove",
                destructive = true,
                onConfirm = {
                    dispatcher?.dispatch(CommandRegistry.bedMeshProfileRemove, BedMeshProfileArgs(name))
                    removeTarget = null
                },
                onCancel = { removeTarget = null },
            )
        }
    }
}

private enum class MeshDialog { Save, Load }

/** App-generated default profile name `YY.MM.DD_HH.MM` (D-10), pre-filled into the editable Save field. */
private fun defaultProfileName(): String =
    SimpleDateFormat("yy.MM.dd_HH.mm", Locale.US).format(Date())

@Composable
private fun BedMeshFocus(
    vm: BedMeshVm,
    tokens: ThemeTokens,
    onCycleScaleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Box(Modifier.aspectRatio(1f), contentAlignment = Alignment.Center) {
            if (vm.isEmpty) {
                // Empty-state (Pitfall 4): "No active mesh" copy; Activate/Load remain in the Field.
                Column(
                    Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(t.rCard))
                        .border(BorderStroke(2.dp, t.outline), RoundedCornerShape(t.rCard))
                        .background(t.surface)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    MaterialSymbol("grid_off", tint = t.text3, sizeSp = fsSp(48f, t.fs))
                    Text(
                        "No active mesh",
                        color = t.text,
                        fontFamily = Geist,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fsSp(22f, t.fs).sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "Activate a bed mesh, or load a saved profile.",
                        color = t.text2,
                        fontFamily = Geist,
                        fontSize = fsSp(15f, t.fs).sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            } else {
                BedMeshHeatmapHost(
                    tokens = tokens,
                    model = vm.model,
                    scaleMode = vm.scaleMode,
                    modifier = Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(t.rCard))
                        .border(BorderStroke(2.dp, t.outline), RoundedCornerShape(t.rCard)),
                )
            }

            // Scale-mode toggle overlay inset top-left (white/setting intent, ≥64dp, taps cycle, D-09).
            Box(
                Modifier.align(Alignment.TopStart).padding(8.dp),
            ) {
                ScaleToggle(label = vm.scaleMode.displayLabel(), onClick = onCycleScaleMode)
            }
        }
    }
}

/** The scale-mode toggle: `expand` glyph + current mode (Mono for numeric), white/setting intent. */
@Composable
private fun ScaleToggle(label: String, onClick: () -> Unit) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        Modifier
            .heightIn(min = 64.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, t.outline), shape)
            .background(t.surface2)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MaterialSymbol("expand", tint = t.text, sizeSp = fsSp(22f, t.fs))
        Text(
            text = label,
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(16f, t.fs).sp,
        )
    }
}

@Composable
private fun SaveNameDialog(
    name: String,
    valid: Boolean,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val t = LocalTokens.current
    Box(Modifier.fillMaxSize().background(t.bg)) {
        ScreenScaffold(
            field = {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MaterialSymbol("save", tint = t.accent2, sizeSp = fsSp(28f, t.fs))
                        Text(
                            "Save mesh profile",
                            color = t.text,
                            fontFamily = Geist,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = fsSp(24f, t.fs).sp,
                        )
                    }
                    TokenTextField(
                        value = name,
                        onValueChange = onNameChange,
                        label = "Profile name",
                        isError = !valid,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                    if (!valid) {
                        Text(
                            "Use letters, numbers, . _ - only (no spaces).",
                            color = t.stop,
                            fontFamily = Geist,
                            fontSize = fsSp(14f, t.fs).sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MeshGutterButton("Cancel", onCancel, Modifier.weight(1f), Intent.Neutral)
                    MeshGutterButton("Save", onSave, Modifier.weight(1f), Intent.Accent, enabled = valid)
                }
            },
        )
    }
}

@Composable
private fun LoadSelectorDialog(
    profiles: List<String>,
    onApply: (String) -> Unit,
    onRemove: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val t = LocalTokens.current
    var selected by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(t.bg)) {
        ScreenScaffold(
            field = {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text(
                        "Load mesh profile",
                        color = t.text,
                        fontFamily = Geist,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = fsSp(22f, t.fs).sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(profiles, key = { it }) { name ->
                            ProfileRow(
                                name = name,
                                selected = selected == name,
                                onSelect = { selected = name },
                                onRemove = { onRemove(name) },
                            )
                        }
                    }
                }
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MeshGutterButton("Cancel", onCancel, Modifier.weight(1f), Intent.Neutral)
                    MeshGutterButton(
                        "Apply",
                        onClick = { selected?.let(onApply) },
                        modifier = Modifier.weight(1f),
                        intent = Intent.Accent,
                        enabled = selected != null,
                    )
                }
            },
        )
    }
}

@Composable
private fun ProfileRow(
    name: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.weight(1f).heightIn(min = 64.dp)
                .clip(shape)
                .border(BorderStroke(2.dp, if (selected) t.accentLine else t.outline), shape)
                .background(if (selected) t.surface2 else Color.Transparent)
                .clickable(onClick = onSelect)
                .padding(horizontal = 14.dp, vertical = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = name,
                color = t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(17f, t.fs).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Per-row red Remove (routes through the red ConfirmGuard at the caller, D-10).
        Box(
            Modifier.heightIn(min = 64.dp)
                .clip(shape)
                .border(BorderStroke(2.dp, t.stop), shape)
                .clickable(onClick = onRemove)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            MaterialSymbol("delete", tint = t.stop, sizeSp = fsSp(22f, t.fs))
        }
    }
}

@Composable
private fun MeshFieldButton(
    label: String,
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: Intent = Intent.Neutral,
    enabled: Boolean = true,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = intentColor(intent, t)
    val base = modifier
        .heightIn(min = 64.dp)
        .clip(shape)
        .border(BorderStroke(2.dp, if (enabled) outline else t.hair), shape)
        .background(if (enabled) Color.Transparent else t.surface)
        .padding(12.dp)
    val box = if (enabled) base.clickable(onClick = onClick) else base
    Column(
        box,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MaterialSymbol(symbol, tint = if (enabled) outline else t.text3, sizeSp = fsSp(44f, t.fs))
        Text(
            text = label,
            color = if (enabled) t.text else t.text3,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(16f, t.fs).sp,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun MeshGutterButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: Intent = Intent.Neutral,
    enabled: Boolean = true,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = intentColor(intent, t)
    val base = modifier
        .heightIn(min = 64.dp)
        .clip(shape)
        .border(BorderStroke(2.dp, if (enabled) outline else t.hair), shape)
        .background(if (enabled) Color.Transparent else t.surface)
        .padding(horizontal = 12.dp, vertical = 18.dp)
    val box = if (enabled) base.clickable(onClick = onClick) else base
    Box(box, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = if (enabled) t.text else t.text3,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
            maxLines = 1,
        )
    }
}

/** The scale-mode display label (Mono numeric for the ± modes). */
private fun works.mees.dinghy.render.BedMeshHeatmapView.ScaleMode.displayLabel(): String = when (this) {
    works.mees.dinghy.render.BedMeshHeatmapView.ScaleMode.RELATIVE -> "RELATIVE"
    works.mees.dinghy.render.BedMeshHeatmapView.ScaleMode.PLATE -> "PLATE"
    works.mees.dinghy.render.BedMeshHeatmapView.ScaleMode.PM_010 -> "±0.10"
    works.mees.dinghy.render.BedMeshHeatmapView.ScaleMode.PM_025 -> "±0.25"
    works.mees.dinghy.render.BedMeshHeatmapView.ScaleMode.PM_050 -> "±0.50"
    works.mees.dinghy.render.BedMeshHeatmapView.ScaleMode.PM_100 -> "±1.00"
}
