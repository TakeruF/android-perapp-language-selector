package dev.takeru.perapplocale.core

import android.annotation.SuppressLint
import android.os.IBinder
import android.util.Log

/**
 * Reads Android profile metadata through IUserManager.  We intentionally accept only the AOSP
 * CLONE user type: work, private, guest, and secondary users must not leak into this list.
 */
object UserGateway {
    private const val TAG = "UserGateway"
    private const val SERVICE_NAME = "user"
    private const val CLONE_TYPE = "android.os.usertype.profile.CLONE"
    internal var binderProvider: (String) -> IBinder? = SystemBinder::wrapped

    @SuppressLint("PrivateApi")
    data class CloneProfile(val userId: Int, val serialNumber: Long)

    fun cloneProfiles(currentUserId: Int): List<CloneProfile> {
        val binder = binderProvider(SERVICE_NAME) ?: return emptyList()
        return runCatching {
            val stub = Class.forName("android.os.IUserManager\$Stub")
            val service = stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder) ?: return emptyList()
            val profiles = service.javaClass.methods.firstOrNull {
                it.name == "getProfiles" && it.parameterTypes.size == 2
            }?.invoke(service, currentUserId, false) as? List<*> ?: return emptyList()
            profiles.mapNotNull { info ->
                if (info == null) return@mapNotNull null
                val id = readInt(info, "id") ?: return@mapNotNull null
                if (userType(info) == CLONE_TYPE) {
                    userSerialNumber(service, id)?.let { CloneProfile(id, it) }
                } else null
            }.distinct()
        }.getOrElse {
            Log.i(TAG, "IUserManager clone-profile discovery unavailable", it)
            emptyList()
        }
    }

    /** IUserManager has exposed this stable mapping since the early multi-user APIs. */
    private fun userSerialNumber(service: Any, userId: Int): Long? = runCatching {
        val method = service.javaClass.methods.firstOrNull {
            it.name == "getUserSerialNumber" && it.parameterTypes.size == 1
        } ?: return null
        (method.invoke(service, userId) as Number).toLong().takeIf { it >= 0 }
    }.getOrNull()

    private fun userType(info: Any): String? = runCatching {
        info.javaClass.methods.firstOrNull { it.name == "getUserType" && it.parameterTypes.isEmpty() }
            ?.invoke(info) as? String
            ?: info.javaClass.getDeclaredField("userType").apply { isAccessible = true }.get(info) as? String
    }.getOrNull()

    private fun readInt(info: Any, field: String): Int? = runCatching {
        info.javaClass.getDeclaredField(field).apply { isAccessible = true }.getInt(info)
    }.getOrNull()
}
