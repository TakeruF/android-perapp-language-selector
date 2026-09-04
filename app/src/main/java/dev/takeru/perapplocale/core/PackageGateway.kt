package dev.takeru.perapplocale.core

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.os.IBinder
import android.util.Log

/** Reflection-only IPackageManager adapter; transaction ids are deliberately never guessed. */
object PackageGateway {
    private const val TAG = "PackageGateway"
    private const val SERVICE_NAME = "package"
    internal var binderProvider: (String) -> IBinder? = SystemBinder::wrapped

    @SuppressLint("PrivateApi")
    fun installedApplications(userId: Int): List<ApplicationInfo> {
        val binder = binderProvider(SERVICE_NAME) ?: return emptyList()
        return runCatching {
            val stub = Class.forName("android.content.pm.IPackageManager\$Stub")
            val service = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder) ?: return emptyList()
            val method = service.javaClass.methods.firstOrNull {
                it.name == "getInstalledApplications" && it.parameterTypes.size == 2
            } ?: return emptyList()
            val flags = if (method.parameterTypes[0] == Long::class.javaPrimitiveType) 0L else 0
            val slice = method.invoke(service, flags, userId) ?: return emptyList()
            @Suppress("UNCHECKED_CAST")
            (slice.javaClass.getMethod("getList").invoke(slice) as? List<*>)
                ?.filterIsInstance<ApplicationInfo>().orEmpty()
        }.getOrElse {
            Log.i(TAG, "IPackageManager user-aware enumeration unavailable for user $userId", it)
            emptyList()
        }
    }
}
