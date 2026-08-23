package dev.takeru.perapplocale.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.takeru.perapplocale.shizuku.ShizukuState

/**
 * Explains the current Shizuku situation and, where there is one, offers the single action that
 * fixes it. When Shizuku is fine this collapses to nothing so the app list gets the full screen.
 */
@Composable
fun ShizukuStatusCard(
    state: ShizukuState,
    onRequestPermission: () -> Unit,
    onOpenSetup: () -> Unit,
    onRecheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state == ShizukuState.READY) return

    val (icon, title, body, actionLabel, action) = when (state) {
        ShizukuState.PERMISSION_REQUIRED -> StatusContent(
            Icons.Filled.Lock,
            "Shizuku permission needed",
            "Shizuku is running. Grant this app permission so it can talk to the system locale service.",
            "Grant permission",
            onRequestPermission,
        )
        ShizukuState.NOT_RUNNING -> StatusContent(
            Icons.Filled.PlayArrow,
            "Shizuku is not running",
            "Start the Shizuku service from the Shizuku app — over Wireless debugging, or from a computer with adb. " +
                "It must be restarted after every reboot.",
            "How to start it",
            onOpenSetup,
        )
        ShizukuState.NOT_INSTALLED -> StatusContent(
            Icons.Filled.Warning,
            "Shizuku is not installed",
            "This app cannot change another app's locale on its own. Install Shizuku, then start its service.",
            "Setup guide",
            onOpenSetup,
        )
        ShizukuState.READY -> StatusContent(Icons.Filled.CheckCircle, "", "", "", {})
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(icon, contentDescription = null)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = action) { Text(actionLabel) }
                TextButton(onClick = onRecheck) { Text("Re-check") }
            }
        }
    }
}

private data class StatusContent(
    val icon: ImageVector,
    val title: String,
    val body: String,
    val actionLabel: String,
    val action: () -> Unit,
)
