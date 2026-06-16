package works.mees.dinghy.ui.printstatus

/**
 * Print Status @Preview matrix anchor (D-01 / 22-04 structural split).
 *
 * The 6-theme × state × orientation @Preview matrix lives in
 * [works.mees.dinghy.preview.PrintStatusPreviews] — the established project-wide convention:
 * all screen @Preview declarations live under the `preview/` package alongside [SampleFixtures]
 * and the [PreviewBox] theming harness. That file references [PrintStatusScreen] (the stateless
 * overload) and composes within a [works.mees.dinghy.preview.PreviewBox] to inject fixtures with
 * no live Moonraker connection.
 *
 * This file exists as the D-01 artifact placeholder. Any package-internal preview helpers or
 * preview-only composables that are too tightly coupled to the `ui.printstatus` internals to live
 * under `preview/` should be added here; currently none exist.
 *
 * @see works.mees.dinghy.preview.PrintStatusPreviews — the actual @Preview matrix
 * @see works.mees.dinghy.preview.PrintStatePreviewProvider — the PrintState parameter provider
 */
// No preview declarations yet — all previews live in works.mees.dinghy.preview.PrintStatusPreviews
