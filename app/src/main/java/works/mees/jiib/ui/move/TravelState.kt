package works.mees.jiib.ui.move

import kotlin.math.abs

/** True while the live position is still further than [epsilon] from the target on either axis. */
fun travelPending(curX: Double, curY: Double, tgtX: Double, tgtY: Double, epsilon: Double = 0.5): Boolean =
    abs(curX - tgtX) > epsilon || abs(curY - tgtY) > epsilon
