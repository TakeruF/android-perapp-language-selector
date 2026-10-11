package com.takeruf.perapplocale.data

sealed interface AppFilter {
    data object ALL : AppFilter
    data object CONFIGURED : AppFilter
    data object UNCONFIGURED : AppFilter
    data class Language(val tag: String) : AppFilter

    fun matches(app: AppInfo): Boolean = when (this) {
        ALL -> true
        CONFIGURED -> app.isConfigured
        // An unread locale is not evidence that the app follows the system default.
        UNCONFIGURED -> app.localeKnown && !app.isConfigured
        is Language -> app.localeTag == tag
    }
}

data class AppFilterCounts(
    val configured: Int,
    val unconfigured: Int,
    val languages: Map<String, Int>,
) {
    fun availableFilter(filter: AppFilter): AppFilter =
        if (filter is AppFilter.Language && filter.tag !in languages) AppFilter.ALL else filter
}

/** Count before searching, so typing a query does not remove the selected language tab. */
internal fun List<AppInfo>.filterCounts(): AppFilterCounts = AppFilterCounts(
    configured = count { it.isConfigured },
    unconfigured = count { AppFilter.UNCONFIGURED.matches(it) },
    languages = filter { it.isConfigured }.groupingBy { it.localeTag }.eachCount().toSortedMap(),
)
