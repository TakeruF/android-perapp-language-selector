package dev.takeru.perapplocale.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator

/** Reads the installed-package list. Nothing here needs Shizuku. */
class AppRepository(context: Context) {

    private val packageManager: PackageManager = context.packageManager

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
}
