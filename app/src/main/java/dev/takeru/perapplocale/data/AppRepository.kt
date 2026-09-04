package dev.takeru.perapplocale.data

import android.app.LocaleConfig
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.UserManager
import dev.takeru.perapplocale.core.PackageGateway
import dev.takeru.perapplocale.core.UserGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

/** Reads owner apps publicly and clone-profile apps through Shizuku's system binders. */
class AppRepository(context: Context) {

    private val appContext: Context = context.applicationContext
    private val packageManager: PackageManager = context.packageManager
    // LocaleConfig is APK metadata rather than profile state, so clones intentionally share it.
    private val supportedLocalesCache = mutableMapOf<String, SupportedLocales>()

    suspend fun loadInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val flags = PackageManager.ApplicationInfoFlags.of(0L)
        val collator = Collator.getInstance()
        val currentUserId = Process.myUid() / 100_000
        val currentUserSerial = appContext.getSystemService(UserManager::class.java)
            .getSerialNumberForUser(Process.myUserHandle())
        val owner = packageManager.getInstalledApplications(flags).map { Triple(it, false, currentUserId to currentUserSerial) }
        val clones = UserGateway.cloneProfiles(currentUserId).flatMap { profile ->
            PackageGateway.installedApplications(profile.userId).map { Triple(it, true, profile.userId to profile.serialNumber) }
        }
        (owner + clones)
            .asSequence()
            .map { (info, isClone, profile) ->
                AppInfo(
                    packageName = info.packageName,
                    userId = profile.first,
                    userSerialNumber = profile.second,
                    isClone = isClone,
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
