package dev.takeru.perapplocale.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.takeru.perapplocale.shizuku.ShizukuState

const val ADB_START_COMMAND: String =
    "adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh"

/**
 * The whole Shizuku story on one screen: where the app currently stands, the four steps that get
 * it to READY, and the two things people trip over (reboots, and the wireless-debugging pairing).
 */
@Composable
fun SetupScreen(
    shizuku: ShizukuState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    onRecheck: () -> Unit,
    onOpenShizukuApp: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    DocScaffold(
        title = "Shizuku setup guide",
        subtitle = "Four steps, no root, no computer required",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) {
        CurrentStatus(
            shizuku = shizuku,
            onRequestPermission = onRequestPermission,
            onRecheck = onRecheck,
            onOpenShizukuApp = onOpenShizukuApp,
        )

        DocSection("Why Shizuku is needed") {
            DocBody(
                "Writing another app's locale requires system permissions that are reserved for " +
                    "the platform, so no ordinary app can hold them. Shizuku runs a small service " +
                    "as the shell user — the same user behind adb — and lets this app route that " +
                    "one call through it. Nothing here needs root, and the app gains no permission " +
                    "of its own.",
            )
        }

        DocSection("Steps") {
            DocStep(
                1,
                "Install Shizuku",
                "From Google Play, F-Droid, or the GitHub releases page. On a rooted device you " +
                    "can install Sui instead — it starts by itself and survives reboots.",
            )
            DocStep(
                2,
                "Start the service on-device (Android 11+)",
                "Enable Developer options → Wireless debugging, keep that screen open, then open " +
                    "Shizuku and tap \"Start via Wireless debugging\". The first time, Shizuku asks " +
                    "you to pair using the pairing code shown under Wireless debugging.",
            )
            DocStep(
                3,
                "…or start it from a computer instead",
                "Enable USB debugging, connect the phone, and run this once:",
            )
            DocCommand(ADB_START_COMMAND) { onCopy(ADB_START_COMMAND) }
            DocStep(
                4,
                "Grant this app permission",
                "Come back here and accept the Shizuku prompt. The status at the top of this " +
                    "screen says Connected as soon as everything is in place.",
            )
        }

        DocCallout(
            "Shizuku stops at every reboot",
            "The service is not a background app; it dies when the device restarts, and the app " +
                "list here goes back to being read-only until you repeat step 2 or 3. Sui, on a " +
                "rooted device, is the only way around that.",
        )

        DocSection("If it still does not connect") {
            DocBullet(
                "Wireless debugging turned itself off.",
                "Some ROMs disable it when the screen locks or the Wi-Fi network changes. Turn it " +
                    "back on and start Shizuku again.",
            )
            DocBullet(
                "The permission prompt never appeared.",
                "Open Shizuku, find this app under \"Authorized applications\", and grant it there.",
            )
            DocBullet(
                "Battery optimisation killed Shizuku.",
                "Exclude Shizuku from battery optimisation so the service is not stopped while it is idle.",
            )
        }

        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { onOpenUrl("https://shizuku.rikka.app/guide/setup/") }) {
                Text("Shizuku documentation")
            }
        }
    }
}

@Composable
private fun CurrentStatus(
    shizuku: ShizukuState,
    onRequestPermission: () -> Unit,
    onRecheck: () -> Unit,
    onOpenShizukuApp: () -> Unit,
) {
    val ready = shizuku == ShizukuState.READY
    val (title, body) = when (shizuku) {
        ShizukuState.READY ->
            "Connected" to "Shizuku is running and this app is authorised. Nothing else to do."
        ShizukuState.PERMISSION_REQUIRED ->
            "Waiting for permission" to "Shizuku is running. Step 4 is all that is left."
        ShizukuState.NOT_RUNNING ->
            "Shizuku is installed but not running" to "Start the service — step 2 or step 3 below."
        ShizukuState.NOT_INSTALLED ->
            "Shizuku is not installed" to "Start at step 1."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (ready) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer,
            contentColor = if (ready) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (shizuku == ShizukuState.PERMISSION_REQUIRED) {
                    TextButton(onClick = onRequestPermission) { Text("Grant permission") }
                }
                TextButton(onClick = onOpenShizukuApp) { Text("Open Shizuku") }
                TextButton(onClick = onRecheck) { Text("Re-check") }
            }
        }
    }
}
