package dev.takeru.perapplocale.ui

import android.app.Application
import android.app.LocaleManager
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.takeru.perapplocale.R
import dev.takeru.perapplocale.core.LocaleGateway
import dev.takeru.perapplocale.core.LocaleGatewayException
import dev.takeru.perapplocale.core.ProcessGateway
import dev.takeru.perapplocale.data.AppInfo
import dev.takeru.perapplocale.data.AppRepository
import dev.takeru.perapplocale.data.LocaleOption
import dev.takeru.perapplocale.data.Settings
import dev.takeru.perapplocale.data.SettingsStore
import dev.takeru.perapplocale.data.SupportedLocales
import dev.takeru.perapplocale.shizuku.ShizukuRepository
import dev.takeru.perapplocale.shizuku.ShizukuState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator

enum class AppFilter { ALL, CONFIGURED }

data class MainUiState(
    val shizuku: ShizukuState = ShizukuState.NOT_INSTALLED,
    val loadingApps: Boolean = true,
    val readingLocales: Boolean = false,
    val apps: List<AppInfo> = emptyList(),
    val configuredCount: Int = 0,
    val query: String = "",
    val filter: AppFilter = AppFilter.ALL,
    val showSystemApps: Boolean = false,
    val configuredFirst: Boolean = true,
    val busyPackage: String? = null,
) {
    val shizukuReady: Boolean get() = shizuku == ShizukuState.READY
}

/** One-shot things the UI should show but not keep in state. */
sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class Error(val text: String) : UiEvent
    data class Launch(val intent: Intent, val appliedMessage: String) : UiEvent
}

class MainViewModel(
    application: Application,
    private val shizukuRepository: ShizukuRepository,
) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val settingsStore = SettingsStore(application)

    private val rawApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val loadingApps = MutableStateFlow(true)
    private val readingLocales = MutableStateFlow(false)
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(AppFilter.ALL)
    private val busyPackage = MutableStateFlow<String?>(null)

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    private var localeScanJob: Job? = null
    private var lastScannedForReady = false

    val uiState: StateFlow<MainUiState> =
        combine(
            shizukuRepository.state,
            rawApps,
            settingsStore.settings,
            combine(query, filter, busyPackage) { q, f, busy -> Triple(q, f, busy) },
            combine(loadingApps, readingLocales) { loading, reading -> loading to reading },
        ) { shizuku, apps, settings, (q, f, busy), (loading, reading) ->
            buildState(shizuku, apps, settings, q, f, busy, loading, reading)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    init {
        viewModelScope.launch {
            refreshApps()
            // Shizuku may already have been ready before the package list existed, in which case
            // the collector below saw nothing to scan. Cover that ordering here.
            if (shizukuRepository.state.value == ShizukuState.READY) scanLocales()
        }
        viewModelScope.launch {
            // Re-read locales when Shizuku becomes usable, and only then.
            shizukuRepository.state.collect { state ->
                val ready = state == ShizukuState.READY
                if (ready && !lastScannedForReady && rawApps.value.isNotEmpty()) scanLocales()
                lastScannedForReady = ready
            }
        }
    }

    private fun buildState(
        shizuku: ShizukuState,
        apps: List<AppInfo>,
        settings: Settings,
        q: String,
        f: AppFilter,
        busy: String?,
        loading: Boolean,
        reading: Boolean,
    ): MainUiState {
        // Until the system has answered, fall back to the local mirror so configured apps are
        // still recognisable immediately after launch.
        val merged = apps.map { app ->
            if (app.localeKnown) app
            else settings.assignments[app.packageName]?.let { app.copy(localeTag = it) } ?: app
        }

        val visible = merged.asSequence()
            .filter { settings.showSystemApps || !it.isSystemApp }
            .filter { f == AppFilter.ALL || it.isConfigured }
            .filter { matchesQuery(it, q) }
            .toList()

        val collator = Collator.getInstance()
        val ownPackageName = getApplication<Application>().packageName
        val ordered = if (settings.configuredFirst) {
            visible.sortedWith(
                compareBy<AppInfo> { if (it.packageName == ownPackageName) 0 else 1 }
                    .thenByDescending { it.isConfigured }
                    .thenBy(collator) { it.label },
            )
        } else {
            visible.sortedWith(
                compareBy<AppInfo> { if (it.packageName == ownPackageName) 0 else 1 }
                    .thenBy(collator) { it.label },
            )
        }

        return MainUiState(
            shizuku = shizuku,
            loadingApps = loading,
            readingLocales = reading,
            apps = ordered,
            configuredCount = merged.count { it.isConfigured },
            query = q,
            filter = f,
            showSystemApps = settings.showSystemApps,
            configuredFirst = settings.configuredFirst,
            busyPackage = busy,
        )
    }

    private fun matchesQuery(app: AppInfo, q: String): Boolean {
        if (q.isBlank()) return true
        val needle = q.trim()
        return app.label.contains(needle, ignoreCase = true) ||
            app.packageName.contains(needle, ignoreCase = true)
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: AppFilter) {
        filter.value = value
    }

    fun setShowSystemApps(value: Boolean) {
        viewModelScope.launch { settingsStore.setShowSystemApps(value) }
    }

    fun setConfiguredFirst(value: Boolean) {
        viewModelScope.launch { settingsStore.setConfiguredFirst(value) }
    }

    fun requestShizukuPermission() = shizukuRepository.requestPermission()

    fun refreshShizuku() = shizukuRepository.refresh()

    fun refresh() {
        viewModelScope.launch {
            refreshApps()
            if (shizukuRepository.state.value == ShizukuState.READY) scanLocales()
        }
    }

    private suspend fun refreshApps() {
        loadingApps.value = true
        rawApps.value = runCatching { appRepository.loadInstalledApps() }
            .onFailure { events.send(UiEvent.Error(text(R.string.error_read_apps, it.message))) }
            .getOrDefault(emptyList())
        loadingApps.value = false
    }

    /**
     * Asks the locale service for every package's current override.
     *
     * This is one binder round-trip per package, so it runs off the main thread and publishes
     * partial results as it goes — the list stays usable while the scan finishes.
     */
    private fun scanLocales() {
        localeScanJob?.cancel()
        localeScanJob = viewModelScope.launch {
            readingLocales.value = true
            try {
                val packages = rawApps.value.map { it.packageName }
                if (packages.isEmpty()) return@launch

                var firstFailure: Throwable? = null
                val found = withContext(Dispatchers.IO) {
                    buildMap {
                        for (pkg in packages) {
                            runCatching { LocaleGateway.getApplicationLocales(pkg) }
                                .onSuccess { put(pkg, LocaleOption.tagOf(it)) }
                                .onFailure { if (firstFailure == null) firstFailure = it }
                        }
                    }
                }
                rawApps.value = rawApps.value.map { app ->
                    val tag = found[app.packageName]
                    if (tag == null) app else app.copy(localeTag = tag, localeKnown = true)
                }

                // A per-package failure is normal (an app can be uninstalled mid-scan). Every
                // package failing means something systemic, and staying silent about it would
                // leave the user staring at a list that claims nothing is configured.
                val failure = firstFailure
                if (found.isEmpty() && failure != null) {
                    events.send(
                        UiEvent.Error(
                            text(
                                R.string.error_read_locales,
                                failure.message ?: failure.javaClass.simpleName,
                            ),
                        ),
                    )
                }
            } finally {
                readingLocales.value = false
            }
        }
    }

    /**
     * Applies [option] to [packageName]. When [restart] is set we also force-stop the app and
     * relaunch it, which is what makes the change visible in apps that cache their strings.
     */
    fun apply(packageName: String, option: LocaleOption, restart: Boolean) {
        if (busyPackage.value != null) return
        viewModelScope.launch {
            busyPackage.value = packageName
            try {
                if (packageName == getApplication<Application>().packageName) {
                    // Force-stopping ourselves would kill this process before it could relaunch.
                    // The public API is also the canonical path for changing the calling app.
                    settingsStore.recordAssignment(packageName, option.tag)
                    updateLocalTag(packageName, option.tag)
                    getApplication<Application>()
                        .getSystemService(LocaleManager::class.java)
                        .applicationLocales = option.toLocaleList()
                    events.send(UiEvent.Message(appliedMessage(option)))
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    LocaleGateway.setApplicationLocales(packageName, option.toLocaleList())
                }
                settingsStore.recordAssignment(packageName, option.tag)
                updateLocalTag(packageName, option.tag)

                if (!restart) {
                    events.send(UiEvent.Message(appliedMessage(option)))
                    return@launch
                }

                val stopped = withContext(Dispatchers.IO) { ProcessGateway.forceStop(packageName) }
                val launchIntent = appRepository.launchIntentFor(packageName)
                when {
                    !stopped -> events.send(
                        UiEvent.Error(
                            text(R.string.error_force_stop_refused),
                        ),
                    )
                    launchIntent == null -> events.send(
                        UiEvent.Message(text(R.string.applied_no_launcher, appliedMessage(option))),
                    )
                    else -> {
                        events.send(UiEvent.Launch(launchIntent, appliedMessage(option)))
                    }
                }
            } catch (e: LocaleGatewayException) {
                events.send(
                    UiEvent.Error(
                        e.message?.let { text(R.string.error_locale_change_detail, it) }
                            ?: text(R.string.error_locale_change),
                    ),
                )
            } catch (e: Exception) {
                events.send(
                    UiEvent.Error(
                        text(R.string.error_unexpected, e.message ?: e.javaClass.simpleName),
                    ),
                )
            } finally {
                busyPackage.value = null
            }
        }
    }

    private fun appliedMessage(option: LocaleOption): String =
        if (option.isSystemDefault) text(R.string.reset_to_system_locale)
        else text(R.string.applied_language, option.label)

    private fun text(id: Int, vararg args: Any?): String =
        getApplication<Application>().getString(id, *args)

    private fun updateLocalTag(packageName: String, tag: String) {
        rawApps.value = rawApps.value.map { app ->
            if (app.packageName == packageName) app.copy(localeTag = tag, localeKnown = true) else app
        }
    }

    suspend fun supportedLocalesFor(packageName: String): SupportedLocales =
        appRepository.supportedLocalesFor(packageName)

    class Factory(
        private val application: Application,
        private val shizukuRepository: ShizukuRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(application, shizukuRepository) as T
    }
}
