package dev.takeru.perapplocale.core

import android.os.IBinder
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Looks up a system service binder and wraps it so that every transaction is executed by the
 * Shizuku server process instead of by us.
 *
 * The wrapper matters: [raw] hands back the *real* binder, and calling it directly would be a
 * transaction from our own uid, which has none of the signature/privileged permissions the locale
 * service demands. [ShizukuBinderWrapper] forwards the parcel through Shizuku, so the system sees
 * uid 2000 (`com.android.shell`) as the caller.
 */
object SystemBinder {

    /** The unwrapped service binder. Transactions on it run as *our* uid. */
    fun raw(name: String): IBinder? =
        runCatching { SystemServiceHelper.getSystemService(name) }.getOrNull()

    /** Returns a Shizuku-backed binder for [name], or null if the service is unavailable. */
    fun wrapped(name: String): IBinder? {
        val original = raw(name) ?: return null
        return runCatching { ShizukuBinderWrapper(original) }.getOrNull()
    }
}
