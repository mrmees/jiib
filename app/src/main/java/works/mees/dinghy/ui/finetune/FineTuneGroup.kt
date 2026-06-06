package works.mees.dinghy.ui.finetune

/**
 * The Fine-Tune live-adjust groups (D-01 failure-mode split). TOP-LEVEL + public so BOTH the screens
 * in this package AND `AppShell` (17-06) reference it without the P16-style private-symbol compile
 * blocker (echo of P16 `PresetSelector` being `private`). 17-06 read_firsts this file to wire the route.
 *
 *  - [MOTION] — what moves the print head: speed %, max velocity, max accel, minimum cruise ratio,
 *    square-corner velocity.
 *  - [EXTRUSION] — what affects the filament: flow %, pressure advance, smooth time, part-cooling fan,
 *    and the FW-retraction entry (when present).
 *  - [FW_RETRACTION] — the firmware-retraction mini-screen (build-blind; reachable from [EXTRUSION]
 *    only when the printer reports a `firmware_retraction` object).
 */
enum class FineTuneGroup {
    MOTION,
    EXTRUSION,
    FW_RETRACTION,
}
