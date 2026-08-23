package dev.takeru.perapplocale.data

/** One installed package as shown in the list. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    /** BCP 47 tag currently forced on this app, or "" when it follows the system locale. */
    val localeTag: String = "",
    /** False until we have actually asked the system; lets the UI avoid claiming "System Default". */
    val localeKnown: Boolean = false,
) {
    val isConfigured: Boolean get() = localeTag.isNotEmpty()
}
