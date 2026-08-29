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
     * We need two hidden entry points: `android.os.ServiceManager#getService` (Shizuku's
     * `SystemServiceHelper` reflects on it to obtain a raw service binder) and
     * `android.app.ILocaleManager$Stub#asInterface`. Without an exemption both would fail with
     * `NoSuchMethodException` — every version we support enforces the restriction.
     *
     * This is best-effort on purpose: if a future release or an OEM build blocks the exemption,
     * [dev.takeru.perapplocale.core.LocaleGateway] still has its raw-transaction path.
     */
    private fun exemptHiddenApis() {
        // "L" is the prefix of every JNI class signature, so this exempts everything.
        runCatching { HiddenApiBypass.addHiddenApiExemptions("L") }
            .onSuccess { if (!it) Log.w(TAG, "Hidden API exemption was refused by the runtime") }
            .onFailure { Log.w(TAG, "Could not lift hidden API restrictions", it) }
    }

    private companion object {
        const val TAG = "PerAppLocaleApp"
    }
}
