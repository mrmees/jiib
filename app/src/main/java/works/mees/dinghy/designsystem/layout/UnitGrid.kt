package works.mees.dinghy.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The unit grid `U` — the single fixed anchor of the jiib redesign layout system.
 *
 * Every vertical element in the redesign is an integer number of units, derived from the
 * LANDSCAPE-CONSTRAINED SHORT EDGE (the physical screen height in landscape, the physical
 * screen width in portrait — they are the same dimension) and held CONSTANT through rotation.
 *
 * ## What is `U`?
 *
 * U is dp-derived: the 41.dp short-edge target is the density-correct anchor on Android.
 * Because Compose `Dp` is density-independent, the dp target IS the density-correct
 * (DPI-derived) anchor — there is no separate DPI/density parameter. The research
 * `U_target_from_dpi ≈ dpi/2.4` derivation reduces to the constant 41.dp and is
 * documentation of WHY 41.dp, not a runtime density input.
 *
 * ## The FIT-PRESERVING formula (N×U == contentMinDim, no overflow)
 *
 * The naive form `(dim/N).coerceAtLeast(64.dp)` overflows: at 360dp × N=7,
 * 7×64=448dp > 360dp — rows spill out of the screen. The fit-preserving form
 * prevents this by computing how many units of at least 64dp FIT first:
 *
 * ```
 * nTarget = round(contentMinDim / 41.dp).coerceIn(5, 7)    // U anchor → ideal N
 * nMaxFit = (contentMinDim / 64.dp).toInt().coerceAtLeast(5)  // 64dp floor → max N that fits
 * N       = min(nTarget, nMaxFit)                           // fit-preserving pick
 * uDp     = contentMinDim / N                               // U fills the space exactly
 * ```
 *
 * This guarantees:
 *  - `N × uDp == contentMinDim` (no overflow)
 *  - `uDp >= 64.dp` whenever `contentMinDim >= 320.dp` (the phone-landscape floor)
 *  - `N in [5, 7]` (phone landscape floor → tablet ceiling)
 *
 * ## Call-site contract
 *
 * Wrap the screen root in `BoxWithConstraints` and pass `minOf(maxWidth, maxHeight)` —
 * the landscape-constrained short edge — to [rememberUnitGrid] or [unitGridFor]:
 *
 * ```kotlin
 * BoxWithConstraints(Modifier.fillMaxSize()) {
 *     val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
 *     // use grid.uDp as the height of each list row, control tile, etc.
 * }
 * ```
 *
 * `minOf(maxWidth, maxHeight)` equals the landscape height in landscape (smaller dim)
 * and the portrait width in portrait (still the smaller dim) — they are the same
 * physical measurement, so `U` is CONSTANT through rotation (LAYOUT.md §"The unit U").
 *
 * Layout sizing passes `uDp: Dp` explicitly (per Phase-23 Open Q §1 decision). The ONE bounded
 * exception is [LocalUnitDp] — a nullable glyph-sizing channel promoted 2026-06-12 (R24) per
 * COMPONENTS.md §4's "promote if call-chain depth grows" clause, so deep primitives like
 * `OutlinedControl` can size icons at 0.6U without re-plumbing every call site. It is provided
 * by U-aware containers (FootButtonBar; screen roots in the wide pass) and is NEVER used for
 * row/region layout — layout stays explicit.
 *
 * @property uDp The size of one unit in density-independent pixels. Always ≥ 64.dp
 *               for `contentMinDim >= 320.dp`. `N × uDp == contentMinDim` exactly.
 * @property count The number of unit rows that fit the content dimension. Always in [5, 7].
 */
data class UnitGrid(val uDp: Dp, val count: Int)

/**
 * Pure, non-Composable unit-grid formula. Host-testable (no Compose dependency; [Dp]
 * arithmetic is pure Kotlin at the [UnitGridTest] layer).
 *
 * See [UnitGrid] KDoc for the full formula rationale and guarantees.
 *
 * @param contentMinDim The landscape-constrained short edge — caller passes
 *   `minOf(maxWidth, maxHeight)` from a `BoxWithConstraints` scope.
 */
fun unitGridFor(contentMinDim: Dp): UnitGrid {
    // nTarget: ideal N from the 41.dp U-size anchor, clamped to the [5, 7] design range.
    val nTarget = (contentMinDim / 41.dp).roundToInt().coerceIn(5, 7)

    // nMaxFit: the maximum N such that each unit is AT LEAST 64.dp (the touch-target floor).
    // coerceAtLeast(5) ensures N never drops below the floor — when contentMinDim < 320.dp
    // (sub-floor hardware) nMaxFit would be <5 without the clamp; we still guarantee N=5
    // and let uDp drop below 64.dp on truly tiny screens (better than truncating rows).
    val nMaxFit = (contentMinDim / 64.dp).toInt().coerceAtLeast(5)

    // FIT-PRESERVING pick: take whichever of nTarget or nMaxFit is smaller.
    // If nTarget > nMaxFit the naïve U = dim/nTarget would be < 64.dp (touch-floor violation).
    // Taking min(nTarget, nMaxFit) reduces N until the unit is large enough.
    val n = minOf(nTarget, nMaxFit)

    // U fills the dimension exactly: N × uDp == contentMinDim.
    val uDp = contentMinDim / n

    return UnitGrid(uDp = uDp, count = n)
}

/**
 * Composable wrapper around [unitGridFor] with a `remember(contentMinDim)` key.
 *
 * The `remember(contentMinDim)` key is MANDATORY for Adreno-320 perf: it prevents
 * recomputing U on every recomposition and only recalculates when the physical dimension
 * changes (i.e., orientation flip).
 *
 * @param contentMinDim The landscape-constrained short edge — `minOf(maxWidth, maxHeight)`
 *   from the enclosing `BoxWithConstraints`.
 */
@Composable
fun rememberUnitGrid(contentMinDim: Dp): UnitGrid =
    remember(contentMinDim) { unitGridFor(contentMinDim) }

/**
 * Nullable glyph-sizing channel (R24, 2026-06-12) — carries one unit U to deep control
 * primitives so button glyphs can size at the 0.6U icon tier without per-call-site plumbing.
 *
 * Provided by U-aware containers ([works.mees.dinghy.designsystem.components.FootButtonBar];
 * screen roots as the normalization wide pass reaches them). `null` = container hasn't been
 * migrated yet — consumers fall back to their legacy sizing. NOT for layout: rows, regions and
 * touch floors keep taking `uDp` explicitly (Phase-23 Open Q §1).
 */
val LocalUnitDp = compositionLocalOf<Dp?> { null }
