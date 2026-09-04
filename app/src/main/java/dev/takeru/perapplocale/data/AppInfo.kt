package dev.takeru.perapplocale.data

/** One installed package as shown in the list. */
data class AppInfo(
    val packageName: String,
    val userId: Int,
    /** Stable for a profile lifetime; unlike userId it is not reused after deletion. */
    val userSerialNumber: Long,
    val isClone: Boolean,
    val label: String,
    val isSystemApp: Boolean,
    /** BCP 47 tag currently forced on this app, or "" when it follows the system locale. */
    val localeTag: String = "",
    /** False until we have actually asked the system; lets the UI avoid claiming "System Default". */
    val localeKnown: Boolean = false,
) {
    val target: AppTarget get() = AppTarget(packageName, userId)
    val assignmentKey: String get() = "s$userSerialNumber:$packageName"
    val isConfigured: Boolean get() = localeTag.isNotEmpty()
}

internal fun List<AppInfo>.withLocaleFor(target: AppTarget, tag: String): List<AppInfo> = map { app ->
    if (app.target == target) app.copy(localeTag = tag, localeKnown = true) else app
}
