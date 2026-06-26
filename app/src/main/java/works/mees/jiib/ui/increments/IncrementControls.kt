package works.mees.jiib.ui.increments

import works.mees.jiib.command.PrinterCommands
import works.mees.jiib.config.formatIncrementList
import works.mees.jiib.designsystem.icons.DinghyIcon
import works.mees.jiib.designsystem.icons.DinghyIcons
import works.mees.jiib.ui.finetune.ALL_FINE_TUNE_PARAMS

/**
 * One user-configurable increment selector.
 *
 * @param key           stable persistence key (FineTuneTuner name, or "move_microstep"/"babystep"/"probe_testz").
 * @param group         display group ("Fine-Tune", "Move", "Print", "Calibrate").
 * @param controlTitle  per-control label when the group has multiple selectors (Fine-Tune); null otherwise.
 * @param icon          the control's existing Focus-header icon (never auto-picked).
 * @param maxCount      fixed value count (3 = Fine-Tune) or null = unlimited (min 1).
 * @param defaultValues the jiib default step list.
 */
data class IncrementControlSpec(
    val key: String,
    val group: String,
    val controlTitle: String?,
    val icon: DinghyIcon,
    val maxCount: Int?,
    val defaultValues: List<Double>,
)

object IncrementControls {

    /** The 13 Fine-Tune controls, derived from the live param descriptors (single source of truth). */
    private val fineTune: List<IncrementControlSpec> = ALL_FINE_TUNE_PARAMS.map { p ->
        IncrementControlSpec(
            key = p.tuner.name,
            group = "Fine-Tune",
            controlTitle = p.name,
            icon = p.icon,
            maxCount = 3,
            defaultValues = p.steps.toList(),
        )
    }

    /** The three unlimited (min-1) controls. Babystep is setting-only this phase (no live selector). */
    private val unlimited: List<IncrementControlSpec> = listOf(
        IncrementControlSpec(
            key = "move_microstep", group = "Move", controlTitle = null,
            icon = DinghyIcons.FineTune, maxCount = null,
            defaultValues = listOf(0.01, 0.025, 0.1, 0.25, 1.0, 2.5, 10.0),
        ),
        IncrementControlSpec(
            // Babystep is setting-only this phase (no live selector yet). Owner-chosen `stacks` icon.
            // Default mirrors PrinterCommands.BABYSTEP_STEPS.
            key = "babystep", group = "Print", controlTitle = null,
            icon = DinghyIcons.Babystep, maxCount = null,
            defaultValues = PrinterCommands.BABYSTEP_STEPS.toList(),
        ),
        IncrementControlSpec(
            key = "probe_testz", group = "Calibrate", controlTitle = null,
            icon = DinghyIcons.RoutineProbeCalibrate, maxCount = null,
            defaultValues = listOf(0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 5.0, 10.0),
        ),
    )

    val ALL: List<IncrementControlSpec> = fineTune + unlimited

    fun specFor(key: String): IncrementControlSpec? = ALL.firstOrNull { it.key == key }

    /** key → canonical default string, for new-printer seeding. */
    fun defaultStringMap(): Map<String, String> =
        ALL.associate { it.key to formatIncrementList(it.defaultValues) }

    /** key → default value list, for read-path fallback. */
    fun defaultValueMap(): Map<String, List<Double>> =
        ALL.associate { it.key to it.defaultValues }
}
