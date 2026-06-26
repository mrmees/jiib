package works.mees.jiib.ui.move

/** Which Field rows are shown/enabled for a given homed state. */
data class MoveRowAvailability(
    val homeAll: Boolean,
    val homeXY: Boolean,
    val homeZ: Boolean,
    val touchMove: Boolean,
    val xy: Boolean,
    val z: Boolean,
    val microstep: Boolean,
)

fun moveRowAvailability(xHomed: Boolean, yHomed: Boolean, zHomed: Boolean): MoveRowAvailability {
    val xy = xHomed && yHomed
    val all = xy && zHomed
    val any = xHomed || yHomed || zHomed
    return MoveRowAvailability(
        homeAll = true,
        homeXY = !xy,
        homeZ = !zHomed,
        touchMove = all,
        xy = xy,
        z = zHomed,
        microstep = any,
    )
}
