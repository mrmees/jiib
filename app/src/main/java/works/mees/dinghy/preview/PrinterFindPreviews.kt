package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import works.mees.dinghy.config.DiscoveredPrinter
import works.mees.dinghy.ui.screen.PrinterFindContent

private val findSample = listOf(
    DiscoveredPrinter(name = "ender5plus", host = "192.168.1.120", port = 7125),
    DiscoveredPrinter(name = "voron", host = "192.168.1.121", port = 7125),
)

@Nexus7Previews
@Composable
private fun FindFound() = PreviewBox(colorfulDark) {
    PrinterFindContent(scanning = false, scanned = true, discovered = findSample, probingHost = null,
        onScan = {}, onPick = {}, onBack = {})
}

@Nexus7Previews
@Composable
private fun FindScanning() = PreviewBox(colorfulDark) {
    PrinterFindContent(scanning = true, scanned = false, discovered = emptyList(), probingHost = null,
        onScan = {}, onPick = {}, onBack = {})
}

@Nexus7Previews
@Composable
private fun FindNone() = PreviewBox(colorfulDark) {
    PrinterFindContent(scanning = false, scanned = true, discovered = emptyList(), probingHost = null,
        onScan = {}, onPick = {}, onBack = {})
}
