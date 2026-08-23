package dev.takeru.perapplocale

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.takeru.perapplocale.data.AppInfo
import dev.takeru.perapplocale.ui.HelpScreen
import dev.takeru.perapplocale.ui.LocaleSheet
import dev.takeru.perapplocale.ui.MainScreen
import dev.takeru.perapplocale.ui.MainViewModel
import dev.takeru.perapplocale.ui.SetupScreen
import dev.takeru.perapplocale.ui.UiEvent
import dev.takeru.perapplocale.ui.theme.PerAppLocaleTheme

/** The three top-level destinations. Small enough that a navigation library would be overhead. */
private enum class Screen { LIST, SETUP, HELP }

class MainActivity : ComponentActivity() {

    // Process-scoped: the retained ViewModel must keep observing the same instance across
    // configuration changes. See PerAppLocaleApp.shizukuRepository.
    private val shizukuRepository get() = (application as PerAppLocaleApp).shizukuRepository

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, shizukuRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PerAppLocaleTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val context = LocalContext.current

                var sheetApp by remember { mutableStateOf<AppInfo?>(null) }
                var screen by rememberSaveable { mutableStateOf(Screen.LIST) }
                // The setup guide is reachable from the list and from Help, and back has to undo
                // whichever step was actually taken. One level deep is the whole hierarchy.
                var setupOrigin by rememberSaveable { mutableStateOf(Screen.LIST) }
                val goBack = { screen = if (screen == Screen.SETUP) setupOrigin else Screen.LIST }

                BackHandler(enabled = screen != Screen.LIST) { goBack() }

                LaunchedEffect(Unit) {
                    viewModel.uiEvents.collect { event ->
                        when (event) {
                            is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
                            is UiEvent.Error -> snackbarHostState.showSnackbar(
                                message = event.text,
                                duration = SnackbarDuration.Long,
                            )
                            is UiEvent.Launch -> runCatching {
                                context.startActivity(
                                    event.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }.onFailure {
                                snackbarHostState.showSnackbar("Could not relaunch the app: ${it.message}")
                            }
                        }
                    }
                }

                when (screen) {
                    Screen.LIST -> {
                        MainScreen(
                            state = state,
                            snackbarHostState = snackbarHostState,
                            onQueryChange = viewModel::onQueryChange,
                            onFilterChange = viewModel::onFilterChange,
                            onShowSystemAppsChange = viewModel::setShowSystemApps,
                            onConfiguredFirstChange = viewModel::setConfiguredFirst,
                            onRefresh = viewModel::refresh,
                            onRequestPermission = viewModel::requestShizukuPermission,
                            onOpenSetup = {
                                setupOrigin = Screen.LIST
                                screen = Screen.SETUP
                            },
                            onOpenHelp = { screen = Screen.HELP },
                            onRecheckShizuku = viewModel::refreshShizuku,
                            onAppClick = { sheetApp = it },
                        )

                        sheetApp?.let { app ->
                            // Re-read from state so the sheet reflects a locale applied moments ago.
                            val live = state.apps.firstOrNull { it.packageName == app.packageName } ?: app
                            LocaleSheet(
                                app = live,
                                enabled = state.shizukuReady,
                                busy = state.busyPackage == live.packageName,
                                onDismiss = { sheetApp = null },
                                onApply = { option, restart ->
                                    sheetApp = null
                                    viewModel.apply(live.packageName, option, restart)
                                },
                            )
                        }
                    }

                    Screen.SETUP -> SetupScreen(
                        shizuku = state.shizuku,
                        snackbarHostState = snackbarHostState,
                        onBack = goBack,
                        onRequestPermission = viewModel::requestShizukuPermission,
                        onRecheck = viewModel::refreshShizuku,
                        onOpenShizukuApp = ::openShizuku,
                        onOpenUrl = ::openUrl,
                        onCopy = ::copyToClipboard,
                    )

                    Screen.HELP -> HelpScreen(
                        snackbarHostState = snackbarHostState,
                        onBack = goBack,
                        onOpenSetup = {
                            setupOrigin = Screen.HELP
                            screen = Screen.SETUP
                        },
                        onOpenUrl = ::openUrl,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Users typically leave to start Shizuku and come straight back.
        shizukuRepository.refresh()
    }

    /** Opens the Shizuku manager, or its Play listing when it is not installed yet. */
    private fun openShizuku() {
        val intent = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
        if (intent != null) {
            startActivity(intent)
        } else {
            openUrl("https://play.google.com/store/apps/details?id=$SHIZUKU_PACKAGE")
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Shizuku start command", text))
        // Android 13+ shows its own copy confirmation, so saying it twice would be noise.
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show()
        }
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
