package works.mees.dinghy.control

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.designsystem.icons.DinghyIcons

/**
 * Drift-guard for the named-control catalog (control baseline audit, Phase 2 — 2026-06-14). Pure-data
 * assertions over [ControlSpecs.all] — host JUnit, no Compose runtime. Each check defends a composition
 * invariant: a spec must not duplicate the registries it references, and an icon-only spec must carry an
 * a11y label.
 */
class ControlCatalogDriftTest {

    @Test
    fun catalog_isNotEmpty() {
        assertFalse("ControlSpecs.all must not be empty", ControlSpecs.all.isEmpty())
    }

    /** Keys are the stable semantic identity — a duplicate makes a lookup ambiguous. */
    @Test
    fun keys_areUnique() {
        val keys = ControlSpecs.all.map { it.key.value }
        val distinct = keys.toSet()
        assertEquals(
            "every ControlSpec.key.value must be unique; duplicates: " +
                "${keys.groupingBy { it }.eachCount().filter { it.value > 1 }.keys}",
            keys.size,
            distinct.size,
        )
    }

    /** Every glyph a spec names must be a registered DinghyIcon (no ad-hoc icons). */
    @Test
    fun everyIcon_isRegisteredInDinghyIcons() {
        val registered = DinghyIcons.all.toSet()
        ControlSpecs.all.forEach { spec ->
            spec.icon?.let { icon ->
                assertTrue(
                    "ControlSpec '${spec.key.value}' references an unregistered DinghyIcon: $icon",
                    icon in registered,
                )
            }
        }
    }

    /** Every commandCatalogId must resolve to a real CommandRegistry spec (reference, never copied). */
    @Test
    fun everyCommandCatalogId_resolvesInCommandRegistry() {
        val catalogIds = CommandRegistry.all.map { it.catalogId }.toSet()
        ControlSpecs.all.forEach { spec ->
            spec.commandCatalogId?.let { id ->
                assertTrue(
                    "ControlSpec '${spec.key.value}' commandCatalogId '$id' does not resolve in " +
                        "CommandRegistry.all",
                    id in catalogIds,
                )
            }
            spec.longPressCommandCatalogId?.let { id ->
                assertTrue(
                    "ControlSpec '${spec.key.value}' longPressCommandCatalogId '$id' does not resolve " +
                        "in CommandRegistry.all",
                    id in catalogIds,
                )
            }
        }
    }

    /** Icon-only controls (no label) MUST supply a contentDescription (a11y law). */
    @Test
    fun iconOnlyControls_haveContentDescription() {
        ControlSpecs.all
            .filter { it.labelRes == null }
            .forEach { spec ->
                assertNotNull(
                    "icon-only ControlSpec '${spec.key.value}' (labelRes == null) must supply a " +
                        "contentDescriptionRes (a11y law)",
                    spec.contentDescriptionRes,
                )
            }
    }
}
