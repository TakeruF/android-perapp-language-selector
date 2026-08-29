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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.takeru.perapplocale.data.AppInfo
import dev.takeru.perapplocale.data.LocaleOption
import dev.takeru.perapplocale.ui.BulkLocaleSheet
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

    private val showResetAllConfirmation = mutableStateOf(false)

    // Process-scoped: the retained ViewModel must keep observing the same instance across
    // configuration changes. See PerAppLocaleApp.shizukuRepository.
    private val shizukuRepository get() = (application as PerAppLocaleApp).shizukuRepository

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(application, shizukuRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeShortcutIntent(intent)
        enableEdgeToEdge()

        setContent {
            PerAppLocaleTheme {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val snackbarHostState = remember { SnackbarHostState() }
                val context = LocalContext.current

                var sheetApp by remember { mutableStateOf<AppInfo?>(null) }
                var selectedPackageNames by remember { mutableStateOf<Set<String>>(emptySet()) }
                var bulkSheetOpen by remember { mutableStateOf(false) }
                var pendingBulkOption by remember { mutableStateOf<LocaleOption?>(null) }
                var screen by rememberSaveable { mutableStateOf(Screen.LIST) }
                // The setup guide is reachable from the list and from Help, and back has to undo
                // whichever step was actually taken. One level deep is the whole hierarchy.
                var setupOrigin by rememberSaveable { mutableStateOf(Screen.LIST) }
                val goBack = { screen = if (screen == Screen.SETUP) setupOrigin else Screen.LIST }

                BackHandler(enabled = screen != Screen.LIST) { goBack() }
                BackHandler(enabled = screen == Screen.LIST && selectedPackageNames.isNotEmpty()) {
                    selectedPackageNames = emptySet()
                }

                LaunchedEffect(state.apps.map { it.packageName }) {
                    selectedPackageNames = selectedPackageNames.intersect(
                        state.apps.mapTo(mutableSetOf()) { it.packageName },
                    )
                }

                LaunchedEffect(Unit) {
                    viewModel.uiEvents.collect { event ->
                        when (event) {
                            is UiEvent.Message -> snackbarHostState.showSnackbar(event.text)
                            is UiEvent.Error -> snackbarHostState.showSnackbar(
                                message = event.text,
                                duration = SnackbarDuration.Long,
                            )
                            is UiEvent.Launch -> runCatching {
                                context.startActivity(event.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }.fold(
                                onSuccess = {
                                    snackbarHostState.showSnackbar(
                                        context.getString(R.string.applied_restarting, event.appliedMessage),
                                    )
                                },
                                onFailure = {
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            R.string.error_relaunch_after_applied,
                                            event.appliedMessage,
                                            it.message ?: it.javaClass.simpleName,
                                        ),
                                        duration = SnackbarDuration.Long,
                                    )
                                },
                            )
                        }
                    }
                }

                if (showResetAllConfirmation.value) {
                    AlertDialog(
                        onDismissRequest = { showResetAllConfirmation.value = false },
                        title = { Text(context.getString(R.string.reset_all_confirmation_title)) },
                        text = {
                            Text(context.getString(R.string.reset_all_confirmation_body))
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showResetAllConfirmation.value = false },
                                enabled = !state.resettingAll,
                            ) {
                                Text(context.getString(R.string.cancel))
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showResetAllConfirmation.value = false
                                    viewModel.resetAllCustomizations()
                                },
                                enabled = !state.resettingAll,
                            ) {
                                Text(context.getString(R.string.reset_all_confirm))
                            }
                        },
                    )
                }

                pendingBulkOption?.let { option ->
                    val count = selectedPackageNames.size
                    val language = if (option.isSystemDefault) {
                        context.getString(R.string.system_default)
                    } else {
                        "${option.label} · ${option.tag}"
                    }
                    AlertDialog(
                        onDismissRequest = { pendingBulkOption = null },
                        title = {
                            Text(context.getString(R.string.bulk_confirmation_title, count))
                        },
                        text = {
                            Text(
                                context.getString(
                                    R.string.bulk_confirmation_body,
                                    language,
                                    count,
                                ),
                            )
                        },
                        dismissButton = {
                            TextButton(onClick = { pendingBulkOption = null }) {
                                Text(context.getString(R.string.cancel))
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val targets = selectedPackageNames
                                    pendingBulkOption = null
                                    selectedPackageNames = emptySet()
                                    viewModel.applyBulk(targets, option)
                                },
                                enabled = selectedPackageNames.isNotEmpty() &&
                                    state.busyPackages.isEmpty() &&
                                    !state.resettingAll,
                            ) {
                                Text(context.getString(R.string.confirm_bulk_change))
                            }
                        },
                    )
                }

                when (screen) {
                    Screen.LIST -> {
                        MainScreen(
                            state = state,
                            snackbarHostState = snackbarHostState,
                            onQueryChange = viewModel::onQueryChange,
                            onFilterChange = {
                                selectedPackageNames = emptySet()
                                viewModel.onFilterChange(it)
                            },
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
                            selectedPackageNames = selectedPackageNames,
                            canChangeSelection = !state.resettingAll &&
                                state.busyPackages.isEmpty() &&
                                (state.shizukuReady || selectedPackageNames.all {
                                    it == context.packageName
                                }),
                            onSelectionToggle = { app ->
                                selectedPackageNames = if (app.packageName in selectedPackageNames) {
                                    selectedPackageNames - app.packageName
                                } else {
                                    selectedPackageNames + app.packageName
                                }
                            },
                            onClearSelection = { selectedPackageNames = emptySet() },
                            onChangeSelectedLanguage = { bulkSheetOpen = true },
                        )

                        if (bulkSheetOpen) {
                            BulkLocaleSheet(
                                appCount = selectedPackageNames.size,
                                enabled = !state.resettingAll &&
                                    state.busyPackages.isEmpty() &&
                                    (state.shizukuReady || selectedPackageNames.all {
                                        it == context.packageName
                                    }),
                                onDismiss = { bulkSheetOpen = false },
                                onContinue = { option ->
                                    bulkSheetOpen = false
                                    pendingBulkOption = option
                                },
                            )
                        }

                        sheetApp?.let { app ->
                            // Re-read from state so the sheet reflects a locale applied moments ago.
                            val live = state.apps.firstOrNull { it.packageName == app.packageName } ?: app
                            LocaleSheet(
                                app = live,
                                enabled = state.shizukuReady || live.packageName == context.packageName,
                                busy = live.packageName in state.busyPackages,
                                loadSupportedLocales = viewModel::supportedLocalesFor,
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
                        shizuku = state.shizuku,
                        snackbarHostState = snackbarHostState,
                        onBack = goBack,
                        onOpenSetup = {
                            setupOrigin = Screen.HELP
                            screen = Screen.SETUP
                        },
                        onOpenUrl = ::openUrl,
                        onCopy = ::copyToClipboard,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShortcutIntent(intent)
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
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.shizuku_start_command), text))
        // Android 13+ shows its own copy confirmation, so saying it twice would be noise.
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, getString(R.string.no_browser_available), Toast.LENGTH_SHORT).show()
        }
    }

    private fun consumeShortcutIntent(intent: Intent?) {
        if (intent?.action != ACTION_RESET_ALL_CUSTOMIZATIONS) return
        // Do not show the destructive confirmation again after a configuration change.
        intent.action = Intent.ACTION_MAIN
        showResetAllConfirmation.value = true
    }

    private companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    }
}
