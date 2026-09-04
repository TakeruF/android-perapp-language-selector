package dev.takeru.perapplocale.core

import android.annotation.SuppressLint
import android.os.IBinder
import android.os.Process
import android.util.Log

/**
 * Stops a running app so it picks the new locale up on its next launch.
 *
 * A locale change is delivered to a *running* app as a configuration change, and plenty of apps
 * (especially ones that never expected per-app locales) cache strings at startup and simply
 * ignore it. Force-stopping is the only reliable way to make such an app re-read its resources.
 *
 * `IActivityManager.forceStopPackage(String, int)` is guarded by
 * `android.permission.FORCE_STOP_PACKAGES` (`signature|privileged`), which `com.android.shell`
 * declares — so it works over Shizuku for the same reason the locale calls do.
 *
 * Unlike [LocaleGateway] there is no raw-transaction fallback here: `IActivityManager` has
 * hundreds of methods and its transaction ids genuinely do shift between releases, so guessing
 * one would be a good way to call something else entirely. Reflection or nothing.
 */
object ProcessGateway {

    private const val TAG = "ProcessGateway"
    private const val SERVICE_NAME = "activity"

    private val myUserId: Int get() = Process.myUid() / 100_000

    /** Seam for the debug on-device probe; see [LocaleGateway.binderProvider]. */
    internal var binderProvider: (String) -> IBinder? = SystemBinder::wrapped

    /**
     * @return true when the app was stopped, false when the device refused (some OEM builds
     *         restrict force-stop even for shell). Callers should treat false as "locale is
     *         applied, but the user has to close the app themselves".
     */
    @SuppressLint("PrivateApi") // Deliberate: see the class comment. Failure is handled, not fatal.
    fun forceStop(packageName: String): Boolean {
        return forceStop(packageName, myUserId)
    }

    fun forceStop(packageName: String, userId: Int): Boolean {
        val binder: IBinder = binderProvider(SERVICE_NAME) ?: return false
        return try {
            val stub = Class.forName("android.app.IActivityManager\$Stub")
            val service = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
                ?: return false
            val method = service.javaClass.methods.firstOrNull {
                it.name == "forceStopPackage" && it.parameterTypes.size == 2
            } ?: return false
            method.invoke(service, packageName, userId)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "forceStopPackage($packageName, user=$userId) failed", t)
            false
        }
    }
}
