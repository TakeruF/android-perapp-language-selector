package dev.takeru.perapplocale.core

import android.annotation.SuppressLint
import android.os.Build
import android.os.IBinder
import android.os.LocaleList
import android.os.Parcel
import android.os.Process
import android.util.Log

/**
 * Talks to the framework's hidden `ILocaleManager` (service name `"locale"`) through Shizuku.
 *
 * ### Why this route
 *
 * The public [android.app.LocaleManager.setApplicationLocales] only ever writes the *calling*
 * package's locales. The system service behind it also exposes a package-scoped variant:
 *
 * ```
 * // frameworks/base/core/java/android/app/ILocaleManager.aidl
 * void setApplicationLocales(String packageName, int userId, in LocaleList locales);              // API 33
 * void setApplicationLocales(String packageName, int userId, in LocaleList locales, boolean fromDelegate); // API 34+
 * LocaleList getApplicationLocales(String packageName, int userId);
 * ```
 *
 * `LocaleManagerService` lets the call through when the caller either owns the package or holds
 * `android.permission.CHANGE_CONFIGURATION` (reads need `READ_APP_SPECIFIC_LOCALES`). Both are
 * `signature|privileged`, so a normal app can never hold them — but `com.android.shell` declares
 * both in its manifest, and Shizuku executes our transactions as exactly that uid.
 *
 * ### Two implementations on purpose
 *
 * 1. **Reflection on `ILocaleManager$Stub`** is the primary path. It adapts itself to whatever
 *    signature the device's framework actually has, which is what makes it survive both the
 *    API 33 → 34 `fromDelegate` change and OEM tweaks.
 * 2. **Hand-written `Parcel` transactions** are the fallback for when hidden-API reflection is
 *    blocked. It needs no hidden class at all, at the cost of hard-coding the transaction codes.
 *
 * Nothing here guesses: the descriptor, argument order and transaction order are those of the
 * AIDL above, which has kept the same method ordering from API 33 through today.
 */
object LocaleGateway {

    private const val TAG = "LocaleGateway"
    private const val SERVICE_NAME = "locale"
    private const val DESCRIPTOR = "android.app.ILocaleManager"

    // Transaction ids follow AIDL declaration order, which has been stable since API 33.
    private const val TX_SET_APPLICATION_LOCALES = IBinder.FIRST_CALL_TRANSACTION + 0
    private const val TX_GET_APPLICATION_LOCALES = IBinder.FIRST_CALL_TRANSACTION + 1

    /** userId of the profile we are running in; `UserHandle.myUserId()` is not public API. */
    private val myUserId: Int get() = Process.myUid() / 100_000

    /** Cached lazily; `asInterface` is only reachable when hidden-API exemptions took effect. */
    internal var reflectionUnavailable = false

    @Volatile
    var lastPath: GatewayPath = GatewayPath.UNKNOWN
        private set

    /**
     * How we obtain the service binder. A seam, not a configuration knob: the on-device probe in
     * the debug variant swaps in [SystemBinder.raw] so it can exercise this class as the shell uid
     * without Shizuku in the picture. Production never changes it.
     */
    internal var binderProvider: (String) -> IBinder? = SystemBinder::wrapped

    fun isServiceReachable(): Boolean = binderProvider(SERVICE_NAME) != null

    /**
     * @return the locales currently forced onto [packageName], or an empty list when the app
     *         simply follows the system locale.
     */
    @Throws(LocaleGatewayException::class)
    fun getApplicationLocales(packageName: String): LocaleList {
        val binder = requireBinder()
        reflectiveGet(binder, packageName)?.let {
            lastPath = GatewayPath.REFLECTION
            return it
        }
        return transactGet(binder, packageName)
    }

    /**
     * Forces [locales] onto [packageName]. Pass [LocaleList.getEmptyLocaleList] to hand the app
     * back to the system locale.
     */
    @Throws(LocaleGatewayException::class)
    fun setApplicationLocales(packageName: String, locales: LocaleList) {
        val binder = requireBinder()
        if (reflectiveSet(binder, packageName, locales)) {
            lastPath = GatewayPath.REFLECTION
            return
        }
        transactSet(binder, packageName, locales)
    }

    private fun requireBinder(): IBinder = binderProvider(SERVICE_NAME)
        ?: throw LocaleGatewayException(
            "The \"locale\" system service is not reachable. " +
                "Make sure Shizuku is running and this device ships the standard LocaleManagerService.",
        )

    // ------------------------------------------------------------------ reflection path

    // Reaching a non-SDK interface by name is the whole point of this class; the transactGet /
    // transactSet pair below is the deliberate answer to "may not work on all devices".
    @SuppressLint("PrivateApi")
    private fun localeManagerInterface(binder: IBinder): Any? {
        if (reflectionUnavailable) return null
        return try {
            val stub = Class.forName("android.app.ILocaleManager\$Stub")
            stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)
        } catch (t: Throwable) {
            Log.i(TAG, "ILocaleManager\$Stub is not reachable, falling back to raw transactions", t)
            reflectionUnavailable = true
            null
        }
    }

    private fun reflectiveGet(binder: IBinder, packageName: String): LocaleList? {
        val service = localeManagerInterface(binder) ?: return null
        val method = service.javaClass.methods.firstOrNull {
            it.name == "getApplicationLocales" && it.parameterTypes.size == 2
        } ?: return null
        return try {
            method.invoke(service, packageName, myUserId) as? LocaleList
                ?: LocaleList.getEmptyLocaleList()
        } catch (t: Throwable) {
            throw asGatewayException("read the locale of", packageName, t)
        }
    }

    private fun reflectiveSet(binder: IBinder, packageName: String, locales: LocaleList): Boolean {
        val service = localeManagerInterface(binder) ?: return false
        val method = service.javaClass.methods.firstOrNull {
            it.name == "setApplicationLocales" && it.parameterTypes.size in 3..4
        } ?: return false
        return try {
            // API 34 appended `boolean fromDelegate`; false means "the user chose this", which is
            // what we want — a delegate-flagged change is attributed to another app.
            val args: Array<Any> = if (method.parameterTypes.size == 4) {
                arrayOf(packageName, myUserId, locales, false)
            } else {
                arrayOf(packageName, myUserId, locales)
            }
            method.invoke(service, *args)
            true
        } catch (t: Throwable) {
            throw asGatewayException("change the locale of", packageName, t)
        }
    }

    // ------------------------------------------------------------------ raw transaction path

    private fun transactGet(binder: IBinder, packageName: String): LocaleList {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeString(packageName)
            data.writeInt(myUserId)
            binder.transact(TX_GET_APPLICATION_LOCALES, data, reply, 0)
            reply.readException()
            val result = if (reply.readInt() != 0) {
                LocaleList.CREATOR.createFromParcel(reply)
            } else {
                LocaleList.getEmptyLocaleList()
            }
            lastPath = GatewayPath.RAW_TRANSACTION
            return result
        } catch (t: Throwable) {
            throw asGatewayException("read the locale of", packageName, t)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    private fun transactSet(binder: IBinder, packageName: String, locales: LocaleList) {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeString(packageName)
            data.writeInt(myUserId)
            data.writeInt(1)
            locales.writeToParcel(data, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                data.writeInt(0) // fromDelegate = false
            }
            binder.transact(TX_SET_APPLICATION_LOCALES, data, reply, 0)
            reply.readException()
            lastPath = GatewayPath.RAW_TRANSACTION
        } catch (t: Throwable) {
            throw asGatewayException("change the locale of", packageName, t)
        } finally {
            reply.recycle()
            data.recycle()
        }
    }

    // ------------------------------------------------------------------ errors

    private fun asGatewayException(verb: String, packageName: String, t: Throwable): Throwable {
        if (t is LocaleGatewayException) return t
        val cause = (t as? java.lang.reflect.InvocationTargetException)?.targetException ?: t
        val hint = when {
            cause is SecurityException ->
                "Shizuku is running but the shell uid was refused. Re-grant Shizuku permission " +
                    "and make sure the Shizuku service was started fresh after the last reboot."
            cause is IllegalArgumentException ->
                "The system does not know package \"$packageName\" for this user. " +
                    "It may have been uninstalled, or it belongs to another profile."
            else -> cause.message ?: cause.javaClass.simpleName
        }
        return LocaleGatewayException("Could not $verb $packageName. $hint", cause)
    }
}

enum class GatewayPath { UNKNOWN, REFLECTION, RAW_TRANSACTION }

class LocaleGatewayException(message: String, cause: Throwable? = null) : Exception(message, cause)
