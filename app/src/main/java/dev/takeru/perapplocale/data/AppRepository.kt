package dev.takeru.perapplocale.data

import android.app.LocaleConfig
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

/** Reads the installed-package list. Nothing here needs Shizuku. */
class AppRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val packageManager: PackageManager = context.packageManager
    private val supportedLocalesCache = mutableMapOf<String, SupportedLocales>()

    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val flags = PackageManager.ApplicationInfoFlags.of(0L)
        val collator = Collator.getInstance()
        packageManager.getInstalledApplications(flags)
            .asSequence()
            .map { info ->
                AppInfo(
                    packageName = info.packageName,
                    label = info.loadLabel(packageManager).toString(),
                    isSystemApp = isSystemApp(info),
                )
            }
            .sortedWith(compareBy(collator) { it.label })
            .toList()
    }

    /**
     * "System app" means preinstalled. We count an updated system app as a system app too:
     * it still lives on the read-only partition and users do not think of it as theirs.
     */
    private fun isSystemApp(info: ApplicationInfo): Boolean =
        (info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0

    /** Intent used by "Apply & Restart"; null when the app has no launcher entry. */
    fun launchIntentFor(packageName: String) =
        packageManager.getLaunchIntentForPackage(packageName)

    /**
     * Reads the target app's official LocaleConfig through the public Android API. We deliberately
     * do not infer support from AssetManager.locales: library resources can make that list claim
     * languages for which the app itself has no translated UI.
     */
    suspend fun supportedLocalesFor(packageName: String): SupportedLocales = withContext(Dispatchers.IO) {
        synchronized(supportedLocalesCache) { supportedLocalesCache[packageName] }?.let { return@withContext it }

        val result = runCatching {
            val packageContext = appContext.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY)
            val config = LocaleConfig(packageContext)
            when (config.status) {
                LocaleConfig.STATUS_SUCCESS -> {
                    val locales = config.supportedLocales
                    if (locales == null) {
                        SupportedLocales.Invalid
                    } else {
                        SupportedLocales.Declared(
                            buildList(locales.size()) {
                                for (index in 0 until locales.size()) {
                                    locales[index]?.toLanguageTag()?.let(::add)
                                }
                            },
                        )
                    }
                }
                LocaleConfig.STATUS_NOT_SPECIFIED -> SupportedLocales.NotDeclared
                LocaleConfig.STATUS_PARSING_FAILED -> SupportedLocales.Invalid
                else -> SupportedLocales.Invalid
            }
        }.getOrDefault(SupportedLocales.Unavailable)

        synchronized(supportedLocalesCache) { supportedLocalesCache[packageName] = result }
        result
    }
}
