package dev.takeru.perapplocale.ui

import android.os.Build
import android.os.Process
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.takeru.perapplocale.R
import dev.takeru.perapplocale.core.LocaleGateway
import dev.takeru.perapplocale.shizuku.ShizukuState
import java.util.Locale

private const val PROJECT_URL = "https://github.com/TakeruF/android-perapp-language-selector"

/**
 * What the app is, what it cannot be, and how to read a result that looks like nothing happened.
 * The limitations are the point of this screen: almost every "it didn't work" report is one of
 * them rather than a bug.
 */
@Composable
fun HelpScreen(
    shizuku: ShizukuState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onCopy: (String) -> Unit,
) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }
    val diagnostics = remember(context, version, shizuku) {
        val shizukuVersion = runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0).versionName
        }.getOrNull() ?: "not installed"
        val localeServiceReachable = runCatching { LocaleGateway.isServiceReachable() }.getOrDefault(false)
        buildString {
            appendLine("App: ${version ?: "unknown"} (${context.packageName})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Manufacturer: ${Build.MANUFACTURER}")
            appendLine("Model: ${Build.MODEL}")
            appendLine("Profile userId: ${Process.myUid() / 100_000}")
            appendLine("Shizuku: ${shizuku.name.lowercase(Locale.ROOT)} ($shizukuVersion)")
            appendLine("Locale service: ${if (localeServiceReachable) "reachable" else "not reachable"}")
            append("Gateway: ${LocaleGateway.lastPath.name.lowercase(Locale.ROOT)}")
        }
    }

    DocScaffold(
        title = stringResource(R.string.help),
        subtitle = stringResource(R.string.help_subtitle),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) {
        DocSection(stringResource(R.string.help_what_title)) {
            DocBody(stringResource(R.string.help_what_body_1))
            DocBody(stringResource(R.string.help_what_body_2))
        }

        DocCallout(
            stringResource(R.string.not_translator_title),
            stringResource(R.string.not_translator_body),
        )

        DocSection(stringResource(R.string.help_using_title)) {
            DocStep(1, stringResource(R.string.help_pick_app_title), stringResource(R.string.help_pick_app_body))
            DocStep(2, stringResource(R.string.help_choose_language_title), stringResource(R.string.help_choose_language_body))
            DocStep(
                3,
                stringResource(R.string.help_apply_title),
                stringResource(R.string.help_apply_body),
            )
            DocBody(stringResource(R.string.help_system_default_body))
        }

        DocSection(stringResource(R.string.requirements_title)) {
            DocBullet(stringResource(R.string.requirement_android_title), stringResource(R.string.requirement_android_body))
            DocBullet(stringResource(R.string.requirement_shizuku_title), stringResource(R.string.requirement_shizuku_body))
            DocBullet(stringResource(R.string.requirement_no_root_title), stringResource(R.string.requirement_no_root_body))
        }

        DocSection(stringResource(R.string.limitations_title)) {
            DocBullet(
                stringResource(R.string.limitation_language_title),
                stringResource(R.string.limitation_language_body),
            )
            DocBullet(
                stringResource(R.string.limitation_own_language_title),
                stringResource(R.string.limitation_own_language_body),
            )
            DocBullet(
                stringResource(R.string.limitation_web_title),
                stringResource(R.string.limitation_web_body),
            )
            DocBullet(
                stringResource(R.string.limitation_force_stop_title),
                stringResource(R.string.limitation_force_stop_body),
            )
            DocBullet(
                stringResource(R.string.limitation_profile_title),
                stringResource(R.string.limitation_profile_body),
            )
            DocBullet(
                stringResource(R.string.limitation_reboot_title),
                stringResource(R.string.limitation_reboot_body),
            )
        }

        DocSection(stringResource(R.string.privacy_title)) {
            DocBody(stringResource(R.string.privacy_body))
        }

        DocSection(stringResource(R.string.about_title)) {
            DocBody(
                if (version != null) stringResource(R.string.about_version, version)
                else stringResource(R.string.about_without_version),
            )
            DocBody(stringResource(R.string.diagnostics_body))
            DocCommand(diagnostics) { onCopy(diagnostics) }
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onOpenSetup) { Text(stringResource(R.string.shizuku_setup_guide)) }
                OutlinedButton(onClick = { onOpenUrl(PROJECT_URL) }) { Text(stringResource(R.string.source_code)) }
            }
        }
    }
}

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
