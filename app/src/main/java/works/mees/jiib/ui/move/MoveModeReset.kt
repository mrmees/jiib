package works.mees.jiib.ui.move

/**
 * Transient Move Hub modes — the [MoveMode.Bookmark] detail and the [MoveMode.SaveDialog] save form —
 * are only reachable from the Field menu while all axes are homed (their rows are gated on
 * `vm.allHomed`). When the printer un-homes (e.g. the user taps Disable Motors), fall back to the hub
 * default [MoveMode.TouchMove] so we never strand the user on a Focus whose menu row just vanished.
 *
 * Only [MoveMode.Bookmark] and [MoveMode.SaveDialog] are reset, because this pass's bookmark-group
 * gating is what newly makes them homed-only. Motion modes (TouchMove/XY/Z/Microstep) and
 * [MoveMode.Endstops] are returned unchanged — their un-homed behavior is pre-existing and out of
 * scope here (their own Focus bodies already handle it; the guards differ — Microstep on homed
 * state, TouchMove/XY/Z on bounds availability).
 */
fun moveModeAfterHomedChange(mode: MoveMode, allHomed: Boolean): MoveMode =
    if (!allHomed && (mode is MoveMode.Bookmark || mode == MoveMode.SaveDialog)) MoveMode.TouchMove else mode
