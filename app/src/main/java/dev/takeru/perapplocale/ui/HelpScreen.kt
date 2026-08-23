package dev.takeru.perapplocale.ui

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
import androidx.compose.ui.unit.dp

private const val PROJECT_URL = "https://github.com/TakeruF/android-perapp-language-selector"

/**
 * What the app is, what it cannot be, and how to read a result that looks like nothing happened.
 * The limitations are the point of this screen: almost every "it didn't work" report is one of
 * them rather than a bug.
 */
@Composable
fun HelpScreen(
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenSetup: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }

    DocScaffold(
        title = "Help",
        subtitle = "What this app does, and where it stops",
        onBack = onBack,
        snackbarHostState = snackbarHostState,
    ) {
        DocSection("What this app does") {
            DocBody(
                "Android 13 lets the system keep a separate language for each app. Settings → " +
                    "Apps → App language only lists apps that ship a locale config, so most apps " +
                    "never appear there even though the underlying override works for them too.",
            )
            DocBody(
                "This app writes that same per-app override directly, for any installed package. " +
                    "Your phone can stay in Japanese while one app runs in English and another in " +
                    "简体中文.",
            )
        }

        DocCallout(
            "This is not a translator",
            "It only changes the language an app is told to use. If the app does not ship " +
                "resources for that language, Android falls back to the app's default and nothing " +
                "visible changes.",
        )

        DocSection("Using it") {
            DocStep(1, "Pick an app", "Search by app name or package name. Use the menu to include system apps.")
            DocStep(2, "Choose a language", "Pick a preset or type any BCP 47 tag, such as pt-BR or zh-Hant-TW.")
            DocStep(
                3,
                "Apply, or Apply & Restart",
                "Apply writes the locale; a running app may not notice until it restarts. " +
                    "Apply & Restart force-stops the app and reopens it, which is what makes the " +
                    "change visible in most apps.",
            )
            DocBody(
                "Choosing System default removes the override and hands the app back to the " +
                    "phone's language. Apps with an override are marked with a dot and can be " +
                    "listed on their own with the Configured filter.",
            )
        }

        DocSection("Requirements") {
            DocBullet("Android 13 or newer.", "Per-app locales did not exist before Android 13, and cannot be emulated on Android 12.")
            DocBullet("Shizuku, running.", "Or Sui on a rooted device. The setup guide covers this.")
            DocBullet("No root.", "Shizuku's own service does the privileged call; this app holds no special permission.")
        }

        DocSection("Limitations") {
            DocBullet(
                "Apps without that language stay unchanged.",
                "Setting zh-CN on an English-only app changes nothing visible.",
            )
            DocBullet(
                "Some apps choose their own language.",
                "Where the language lives in an in-app setting or in your account on their server, " +
                    "the app ignores the system locale entirely. Several Chinese super-apps work this way.",
            )
            DocBullet(
                "Web content usually ignores it.",
                "Screens rendered from the server, and much of what appears in a WebView, follow " +
                    "the account or the browser's Accept-Language header instead.",
            )
            DocBullet(
                "Force-stop can be refused.",
                "Some OEM builds reject it. The locale is still written — close the app from " +
                    "Recents yourself. The app says so when this happens.",
            )
            DocBullet(
                "One user profile at a time.",
                "Changes apply to the user this app is installed in; a work profile has its own copy of everything.",
            )
            DocBullet(
                "Shizuku must be restarted after every reboot.",
                "Until it is, the list still shows what is configured but nothing can be changed.",
            )
        }

        DocSection("Privacy") {
            DocBody(
                "Everything happens on the device. The app has no internet permission and sends " +
                    "nothing anywhere. It reads the installed app list to show it to you, and " +
                    "stores your choices locally so configured apps are recognisable at launch — " +
                    "the system itself remains the source of truth for locales.",
            )
        }

        DocSection("About") {
            DocBody(
                buildString {
                    append("Per-App Language")
                    if (version != null) append(" $version")
                    append(" · Apache License 2.0")
                },
            )
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onOpenSetup) { Text("Shizuku setup guide") }
                OutlinedButton(onClick = { onOpenUrl(PROJECT_URL) }) { Text("Source code") }
            }
        }
    }
}
