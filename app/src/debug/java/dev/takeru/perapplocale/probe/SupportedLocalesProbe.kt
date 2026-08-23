package dev.takeru.perapplocale.probe

import android.content.res.AssetManager

/**
 * Answers "which languages does this APK actually ship resources for?" straight from the resource
 * table, for comparison against what `LocaleConfig` would report.
 *
 * Run as: `... app_process ... SupportedLocalesProbe <apk-path> [<apk-path> ...]`
 *
 * In the app this same list comes from the public
 * `packageManager.getResourcesForApplication(pkg).assets.locales`; here we build the AssetManager
 * by hand because an `app_process` shell has no Context to ask.
 */
object SupportedLocalesProbe {

    @JvmStatic
    fun main(args: Array<String>) {
        for (path in args) {
            print("$path\n  ")
            try {
                val am = AssetManager::class.java.getDeclaredConstructor()
                    .apply { isAccessible = true }
                    .newInstance()
                val addAssetPath = AssetManager::class.java
                    .getDeclaredMethod("addAssetPath", String::class.java)
                val cookie = addAssetPath.invoke(am, path) as Int
                if (cookie == 0) {
                    println("could not open")
                    continue
                }
                val locales = am.locales
                println("${locales.size} locales: ${locales.joinToString(", ")}")
            } catch (t: Throwable) {
                println("ERROR ${t::class.java.name}: ${t.message}")
            }
        }
    }
}
