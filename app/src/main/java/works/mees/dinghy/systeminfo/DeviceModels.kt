package works.mees.dinghy.systeminfo

/**
 * The two device kinds the System Info browser lists. The Field renders one ListRow per device;
 * the Focus renders [HostDevice]/[McuDevice] detail via an exhaustive `when`. [key] is the stable
 * selection id ("host" or the raw Klipper object name, e.g. "mcu EBBCan"). All MCU detail fields
 * are nullable — a CAN toolhead board reports a different subset than the mainboard (SYS-04).
 */
sealed interface Device {
    val key: String
    val displayName: String
}

/** The host SBC. Carries the live host flows directly (rebuilt per tick in the composable). */
data class HostDevice(
    override val displayName: String,
    val identity: SystemInfo?,
    val procStats: ProcStatQuery?,
    val live: ProcStatLive?,
    val klipperVersion: String?,
    val moonrakerVersion: String?,
) : Device {
    override val key: String get() = HOST_KEY

    companion object {
        const val HOST_KEY = "host"
    }
}

/** One Klipper MCU (mainboard, sub-board, or the [mcu host] Linux-process MCU). */
data class McuDevice(
    override val key: String,
    override val displayName: String,
    val firmwareVersion: String? = null,
    val chip: String? = null,
    val clockHz: Long? = null,
    val interfaceDesc: String? = null,
    val mcuAwake: Float? = null,
    val taskAvg: Float? = null,
    val taskStddev: Float? = null,
    val bytesWrite: Long? = null,
    val bytesRead: Long? = null,
    val bytesRetransmit: Long? = null,
) : Device

/** Software versions surfaced in the Host detail (lazy-loaded). */
data class Versions(val klipper: String?, val moonraker: String?)
