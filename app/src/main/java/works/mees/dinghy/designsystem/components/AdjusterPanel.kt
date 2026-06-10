package works.mees.dinghy.designsystem.components

/**
 * Pure predicate: whether to show the inline "was X" baseline label.
 *
 * Returns false when either [value] or [baseline] is null. Returns true only when the values,
 * rounded to [decimalPrecision] decimal places, differ.
 *
 * TODO(26-01 T3): placeholder body — real implementation in Task 3.
 */
internal fun shouldShowBaseline(value: Double?, baseline: Double?, decimalPrecision: Int): Boolean =
    false
