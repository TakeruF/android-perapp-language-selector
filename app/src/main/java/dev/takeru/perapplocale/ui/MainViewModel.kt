package dev.takeru.perapplocale.ui

import android.app.Application
import android.app.LocaleManager
import android.content.Intent
import android.os.LocaleList
import android.os.Process
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
import dev.takeru.perapplocale.data.AppTarget
import dev.takeru.perapplocale.data.isOwnTarget
import dev.takeru.perapplocale.data.withLocaleFor
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
import kotlinx.coroutines.flow.first
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
    val busyTargets: Set<AppTarget> = emptySet(),
    val resettingAll: Boolean = false,
) {
    val shizukuReady: Boolean get() = shizuku == ShizukuState.READY
}

/** One-shot things the UI should show but not keep in state. */
sealed interface UiEvent {
    data class Message(val text: String) : UiEvent
    data class Error(val text: String) : UiEvent
    data class Launch(val intent: Intent, val appliedMessage: String) : UiEvent
    /** Open via LauncherApps with this explicit profile, never a current-user Intent. */
    data class LaunchProfile(val packageName: String, val userId: Int, val appliedMessage: String) : UiEvent
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
    private val busyTargets = MutableStateFlow<Set<AppTarget>>(emptySet())
    private val resettingAll = MutableStateFlow(false)

    private val events = Channel<UiEvent>(Channel.BUFFERED)
    val uiEvents = events.receiveAsFlow()

    private var localeScanJob: Job? = null
    private var lastScannedForReady = false

    val uiState: StateFlow<MainUiState> =
        combine(
            shizukuRepository.state,
            rawApps,
            settingsStore.settings,
            combine(query, filter, busyTargets) { q, f, busy -> Triple(q, f, busy) },
            combine(loadingApps, readingLocales, resettingAll) { loading, reading, resetting ->
                Triple(loading, reading, resetting)
            },
        ) { shizuku, apps, settings, (q, f, busy), (loading, reading, resetting) ->
            buildState(shizuku, apps, settings, q, f, busy, loading, reading, resetting)
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
                if (ready && !lastScannedForReady) {
                    refreshApps()
                    if (rawApps.value.isNotEmpty()) scanLocales()
                }
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
        busy: Set<AppTarget>,
        loading: Boolean,
        reading: Boolean,
        resetting: Boolean,
    ): MainUiState {
        // Until the system has answered, fall back to the local mirror so configured apps are
        // still recognisable immediately after launch.
        val merged = apps.map { app ->
            if (app.localeKnown) app
            else settings.assignments[app.assignmentKey]?.let { app.copy(localeTag = it) } ?: app
        }

        val visible = merged.asSequence()
            .filter { settings.showSystemApps || !it.isSystemApp }
            .filter { f == AppFilter.ALL || it.isConfigured }
            .filter { matchesQuery(it, q) }
            .toList()

        val collator = Collator.getInstance()
        val ownPackageName = getApplication<Application>().packageName
        val currentUserId = Process.myUid() / 100_000
        val ordered = if (settings.configuredFirst) {
            visible.sortedWith(
                compareBy<AppInfo> { if (it.target.isOwnTarget(ownPackageName, currentUserId)) 0 else 1 }
                    .thenByDescending { it.isConfigured }
                    .thenBy(collator) { it.label }.thenBy { it.packageName }.thenBy { it.isClone }.thenBy { it.userId },
            )
        } else {
            visible.sortedWith(
                compareBy<AppInfo> { if (it.target.isOwnTarget(ownPackageName, currentUserId)) 0 else 1 }
                    .thenBy(collator) { it.label }.thenBy { it.packageName }.thenBy { it.isClone }.thenBy { it.userId },
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
            busyTargets = busy,
            resettingAll = resetting,
        )
    }

    private fun matchesQuery(app: AppInfo, q: String): Boolean {
        if (q.isBlank()) return true
        val needle = q.trim()
        return app.label.contains(needle, ignoreCase = true) ||
            app.packageName.contains(needle, ignoreCase = true) ||
            (app.isClone && "clone".contains(needle, ignoreCase = true))
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

    /** Clears every per-app locale override after the Activity has obtained confirmation. */
    fun resetAllCustomizations() {
        if (resettingAll.value || busyTargets.value.isNotEmpty()) return
        viewModelScope.launch {
            if (shizukuRepository.state.value != ShizukuState.READY) {
                events.send(UiEvent.Error(text(R.string.reset_all_requires_shizuku)))
                return@launch
            }

            resettingAll.value = true
            try {
                // The shortcut can cold-start the app. Wait for package discovery, then query the
                // system directly so overrides created outside this app are included as well.
                val apps = combine(rawApps, loadingApps) { installed, loading -> installed to loading }
                    .first { (_, loading) -> !loading }
                    .first
                val cachedAssignments = settingsStore.settings.first().assignments.keys
                val ownPackage = getApplication<Application>().packageName
                val currentUserId = Process.myUid() / 100_000
                val emptyLocales = LocaleList.getEmptyLocaleList()
                val resetTargets = mutableListOf<AppTarget>()
                var failed = 0

                withContext(Dispatchers.IO) {
                    for (app in apps) {
                        if (app.target.isOwnTarget(ownPackage, currentUserId)) continue
                        val currentTag = runCatching {
                            LocaleOption.tagOf(LocaleGateway.getApplicationLocales(app.packageName, app.userId))
                        }.getOrNull()
                        if (currentTag.isNullOrEmpty() && app.assignmentKey !in cachedAssignments) continue

                        runCatching {
                            LocaleGateway.setApplicationLocales(app.packageName, app.userId, emptyLocales)
                        }.onSuccess {
                            resetTargets += app.target
                        }.onFailure {
                            failed += 1
                        }
                    }
                }

                val ownConfigured = getApplication<Application>()
                    .getSystemService(LocaleManager::class.java)
                    .applicationLocales
                    .isEmpty
                    .not() || apps.firstOrNull { it.target.isOwnTarget(ownPackage, currentUserId) }
                    ?.assignmentKey in cachedAssignments
                if (ownConfigured) {
                    runCatching {
                        getApplication<Application>()
                            .getSystemService(LocaleManager::class.java)
                            .applicationLocales = emptyLocales
                    }.onSuccess {
                        resetTargets += AppTarget(ownPackage, currentUserId)
                    }.onFailure {
                        failed += 1
                    }
                }

                settingsStore.forgetAssignments(
                    resetTargets.mapNotNull { target -> apps.firstOrNull { it.target == target }?.assignmentKey },
                )
                val resetSet = resetTargets.toSet()
                rawApps.value = rawApps.value.map { app ->
                    if (app.target in resetSet) {
                        app.copy(localeTag = "", localeKnown = true)
                    } else {
                        app
                    }
                }

                when {
                    failed > 0 -> events.send(
                        UiEvent.Error(
                            text(R.string.reset_all_partial, resetTargets.size, failed),
                        ),
                    )
                    resetTargets.isEmpty() -> events.send(
                        UiEvent.Message(text(R.string.reset_all_nothing_to_reset)),
                    )
                    else -> events.send(
                        UiEvent.Message(text(R.string.reset_all_complete, resetTargets.size)),
                    )
                }
            } catch (e: Exception) {
                events.send(
                    UiEvent.Error(text(R.string.error_unexpected, e.message ?: e.javaClass.simpleName)),
                )
            } finally {
                resettingAll.value = false
            }
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
                val targets = rawApps.value.map { it.target }
                if (targets.isEmpty()) return@launch

                var firstFailure: Throwable? = null
                val found = withContext(Dispatchers.IO) {
                    buildMap {
                        for (target in targets) {
                            runCatching { LocaleGateway.getApplicationLocales(target.packageName, target.userId) }
                                .onSuccess { put(target, LocaleOption.tagOf(it)) }
                                .onFailure { if (firstFailure == null) firstFailure = it }
                        }
                    }
                }
                rawApps.value = rawApps.value.map { app ->
                    val tag = found[app.target]
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
    fun apply(target: AppTarget, option: LocaleOption, restart: Boolean) {
        if (resettingAll.value || busyTargets.value.isNotEmpty()) return
        viewModelScope.launch {
            busyTargets.value = setOf(target)
            try {
                val ownPackageName = getApplication<Application>().packageName
                val currentUserId = Process.myUid() / 100_000
                if (target.isOwnTarget(ownPackageName, currentUserId)) {
                    // Force-stopping ourselves would kill this process before it could relaunch.
                    // The public API is also the canonical path for changing the calling app.
                    settingsStore.recordAssignment(assignmentKeyFor(target), option.tag)
                    updateLocalTag(target, option.tag)
                    getApplication<Application>()
                        .getSystemService(LocaleManager::class.java)
                        .applicationLocales = option.toLocaleList()
                    events.send(UiEvent.Message(appliedMessage(option)))
                    return@launch
                }

                withContext(Dispatchers.IO) {
                    LocaleGateway.setApplicationLocales(target.packageName, target.userId, option.toLocaleList())
                }
                settingsStore.recordAssignment(assignmentKeyFor(target), option.tag)
                updateLocalTag(target, option.tag)

                if (!restart) {
                    events.send(UiEvent.Message(appliedMessage(option)))
                    return@launch
                }

                val stopped = withContext(Dispatchers.IO) { ProcessGateway.forceStop(target.packageName, target.userId) }
                when {
                    !stopped -> events.send(
                        UiEvent.Error(
                            text(R.string.error_force_stop_refused),
                        ),
                    )
                    target.userId != currentUserId -> events.send(
                        UiEvent.LaunchProfile(target.packageName, target.userId, appliedMessage(option)),
                    )
                    else -> appRepository.launchIntentFor(target.packageName)?.let {
                        events.send(UiEvent.Launch(it, appliedMessage(option)))
                    } ?: events.send(UiEvent.Message(text(R.string.applied_no_launcher, appliedMessage(option))))
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
                busyTargets.value = emptySet()
            }
        }
    }

    /** Applies one locale to every target, even when a target already has another override. */
    fun applyBulk(targets: Set<AppTarget>, option: LocaleOption) {
        if (targets.isEmpty() || resettingAll.value || busyTargets.value.isNotEmpty()) return
        viewModelScope.launch {
            busyTargets.value = targets
            val ownPackageName = getApplication<Application>().packageName
            val currentUserId = Process.myUid() / 100_000
            val succeeded = mutableSetOf<AppTarget>()

            try {
                // Do our own package last because changing it can recreate the Activity.
                for (target in targets.sortedBy { it.isOwnTarget(ownPackageName, currentUserId) }) {
                    runCatching {
                        if (target.isOwnTarget(ownPackageName, currentUserId)) {
                            getApplication<Application>()
                                .getSystemService(LocaleManager::class.java)
                                .applicationLocales = option.toLocaleList()
                        } else {
                            withContext(Dispatchers.IO) {
                                LocaleGateway.setApplicationLocales(target.packageName, target.userId, option.toLocaleList())
                            }
                        }
                    }.onSuccess {
                        succeeded += target
                        updateLocalTag(target, option.tag)
                    }
                }

                if (succeeded.isNotEmpty()) {
                    settingsStore.recordAssignments(succeeded.map(::assignmentKeyFor), option.tag)
                }

                val total = targets.size
                when (succeeded.size) {
                    total -> events.send(
                        UiEvent.Message(text(R.string.bulk_change_complete, total)),
                    )
                    0 -> events.send(
                        UiEvent.Error(text(R.string.bulk_change_failed, total)),
                    )
                    else -> events.send(
                        UiEvent.Error(
                            text(
                                R.string.bulk_change_partial,
                                succeeded.size,
                                total,
                                total - succeeded.size,
                            ),
                        ),
                    )
                }
            } catch (e: Exception) {
                events.send(
                    UiEvent.Error(text(R.string.error_unexpected, e.message ?: e.javaClass.simpleName)),
                )
            } finally {
                busyTargets.value = emptySet()
            }
        }
    }

    private fun appliedMessage(option: LocaleOption): String =
        if (option.isSystemDefault) text(R.string.reset_to_system_locale)
        else text(R.string.applied_language, option.label)

    private fun text(id: Int, vararg args: Any?): String =
        getApplication<Application>().getString(id, *args)

    private fun updateLocalTag(target: AppTarget, tag: String) {
        rawApps.value = rawApps.value.withLocaleFor(target, tag)
    }

    private fun assignmentKeyFor(target: AppTarget): String =
        rawApps.value.firstOrNull { it.target == target }?.assignmentKey
            ?: error("Target disappeared before its assignment could be saved: $target")

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
