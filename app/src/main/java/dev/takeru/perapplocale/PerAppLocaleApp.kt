package dev.takeru.perapplocale

import android.app.Application
import android.util.Log
import dev.takeru.perapplocale.shizuku.ShizukuRepository
import org.lsposed.hiddenapibypass.HiddenApiBypass

class PerAppLocaleApp : Application() {

    /**
     * Process-scoped on purpose.
     *
     * Shizuku's listeners are static, so registering them per-Activity would both leak duplicates
     * across configuration changes and — worse — leave the retained [dev.takeru.perapplocale.ui.MainViewModel]
     * holding a repository whose listeners the destroyed Activity just removed. One instance for
     * the whole process sidesteps both.
     */
    val shizukuRepository: ShizukuRepository by lazy { ShizukuRepository(this) }

    override fun onCreate() {
        super.onCreate()
        exemptHiddenApis()
        shizukuRepository.register()
        installAppShortcuts(this)
    }

    /**
     * Lifts the non-SDK interface restrictions for this process.
     *
     * We need a small set of hidden entry points: `ServiceManager` (Shizuku's
     * `SystemServiceHelper`), the framework Binder interfaces used by the gateways, and
     * `UserHandle.of`. Without an exemption these reflective calls fail with
     * `NoSuchMethodException` — every version we support enforces the restriction.
     *
     * This is best-effort on purpose: if a future release or an OEM build blocks the exemption,
     * [dev.takeru.perapplocale.core.LocaleGateway] still has its raw-transaction path.
     */
    private fun exemptHiddenApis() {
        // Keep the exemption list scoped to the framework classes this app reflects on. The
        // prefix intentionally omits the trailing semicolon so nested `$Stub` classes match too.
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/os/ServiceManager",
                "Landroid/app/ILocaleManager",
                "Landroid/app/IActivityManager",
                "Landroid/content/pm/IPackageManager",
                "Landroid/os/IUserManager",
                "Landroid/os/UserHandle",
            )
        }
            .onSuccess { if (!it) Log.w(TAG, "Hidden API exemption was refused by the runtime") }
            .onFailure { Log.w(TAG, "Could not lift hidden API restrictions", it) }
    }

    private companion object {
        const val TAG = "PerAppLocaleApp"
    }
}
