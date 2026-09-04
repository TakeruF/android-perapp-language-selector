package dev.takeru.perapplocale.data

/** A package installation in one Android user/profile, rather than a package name alone. */
data class AppTarget(
    val packageName: String,
    val userId: Int,
) {
    /** Runtime/UI identity. Persistent assignments use [AppInfo.assignmentKey] instead. */
    val runtimeKey: String get() = "$userId:$packageName"
}

fun AppTarget.isOwnTarget(ownPackageName: String, currentUserId: Int): Boolean =
    packageName == ownPackageName && userId == currentUserId
