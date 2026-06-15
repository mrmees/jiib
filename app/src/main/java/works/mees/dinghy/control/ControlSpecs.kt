package works.mees.dinghy.control

import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.designsystem.control.ControlKey
import works.mees.dinghy.designsystem.control.ControlSpec
import works.mees.dinghy.designsystem.control.ControlType
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIcons

/**
 * The named-control catalog (control baseline audit, Phase 2 — 2026-06-14). Composes the three existing
 * registries: labels/content-descriptions from `R.string`, glyphs from [DinghyIcons], dispatch from
 * [CommandRegistry] (referenced by `catalogId`, never copied). This is the APP-level registry (depends on
 * `R` + `CommandRegistry`); the presentation-only [ControlSpec] type lives in the designsystem package.
 *
 * SEED policy: only the unambiguous, already-iconed SHARED named controls are catalogued here. The rest
 * are added by their consuming phases as those screens migrate — do NOT over-catalog. The conflicted /
 * per-screen-variant controls (common.cancel*, common.done, common.save) are deliberately EXCLUDED this
 * phase — they need per-screen handling. One-off contextual controls stay inline; they never get a spec.
 *
 * Icon-only entries (`labelRes == null`) MUST carry a [ControlSpec.contentDescriptionRes] (a11y law,
 * enforced by `ControlCatalogDriftTest`). Entries are alphabetized by [ControlKey.value].
 */
object ControlSpecs {

    val commonBack = ControlSpec(
        key = ControlKey("common.back"),
        labelRes = R.string.common_back,
        contentDescriptionRes = R.string.cd_back,
        icon = DinghyIcons.Back,
        intent = Intent.Accent,
        type = ControlType.Button,
    )

    val commonHome = ControlSpec(
        key = ControlKey("common.home"),
        labelRes = null, // icon-only
        contentDescriptionRes = R.string.cd_spool_home,
        icon = DinghyIcons.Home,
        intent = Intent.Accent,
        type = ControlType.Button,
    )

    val filesDelete = ControlSpec(
        key = ControlKey("files.delete"),
        labelRes = R.string.files_foot_delete,
        contentDescriptionRes = R.string.cd_files_delete,
        icon = DinghyIcons.Delete,
        intent = Intent.Danger,
        type = ControlType.Button,
    )

    val filesPrint = ControlSpec(
        key = ControlKey("files.print"),
        labelRes = R.string.files_foot_print,
        contentDescriptionRes = R.string.cd_files_print,
        icon = DinghyIcons.Print,
        intent = Intent.Go,
        type = ControlType.Button,
        commandCatalogId = CommandRegistry.printStart.catalogId,
    )

    val macrosExecute = ControlSpec(
        key = ControlKey("macros.execute"),
        labelRes = R.string.macros_foot_execute,
        contentDescriptionRes = R.string.cd_macros_execute,
        icon = DinghyIcons.ExecuteMacro,
        intent = Intent.Go,
        type = ControlType.Button,
    )

    val macrosManage = ControlSpec(
        key = ControlKey("macros.manage"),
        labelRes = R.string.macros_foot_manage,
        contentDescriptionRes = R.string.cd_macros_manage,
        icon = DinghyIcons.ManageMacros,
        intent = Intent.Accent,
        type = ControlType.Button,
    )

    val printerEstop = ControlSpec(
        key = ControlKey("printer.estop"),
        labelRes = null, // icon-only
        contentDescriptionRes = R.string.cd_emergency_stop,
        icon = DinghyIcons.StatusStop,
        intent = Intent.Danger,
        type = ControlType.EStop,
        commandCatalogId = CommandRegistry.emergencyStop.catalogId,
    )

    val spoolLoad = ControlSpec(
        key = ControlKey("spool.load"),
        labelRes = null, // icon-only
        contentDescriptionRes = R.string.cd_spool_load,
        icon = DinghyIcons.ExpandCircleUp,
        intent = Intent.Go,
        type = ControlType.Button,
    )

    val spoolScan = ControlSpec(
        key = ControlKey("spool.scan"),
        labelRes = null, // icon-only (ONE token per master-list §f #5: DinghyIcons.QrCode)
        contentDescriptionRes = R.string.cd_spool_scan,
        icon = DinghyIcons.QrCode,
        intent = Intent.Accent,
        type = ControlType.Button,
    )

    val spoolUnload = ControlSpec(
        key = ControlKey("spool.unload"),
        labelRes = null, // icon-only
        contentDescriptionRes = R.string.cd_spool_unload,
        icon = DinghyIcons.ExpandCircleDown,
        intent = Intent.Go,
        type = ControlType.Button,
    )

    val stepperDecrease = ControlSpec(
        key = ControlKey("stepper.decrease"),
        labelRes = null, // icon-only
        contentDescriptionRes = R.string.cd_decrement,
        icon = DinghyIcons.Decrease,
        intent = Intent.Accent,
        type = ControlType.Stepper,
    )

    val stepperIncrease = ControlSpec(
        key = ControlKey("stepper.increase"),
        labelRes = null, // icon-only
        contentDescriptionRes = R.string.cd_increment,
        icon = DinghyIcons.Increase,
        intent = Intent.Accent,
        type = ControlType.Stepper,
    )

    /** Hand-rolled list of every spec above (grep-auditable; no reflection). Alphabetized by key. */
    val all: List<ControlSpec> = listOf(
        commonBack,
        commonHome,
        filesDelete,
        filesPrint,
        macrosExecute,
        macrosManage,
        printerEstop,
        spoolLoad,
        spoolScan,
        spoolUnload,
        stepperDecrease,
        stepperIncrease,
    )
}
