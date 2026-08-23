package dev.takeru.perapplocale.shizuku

/**
 * Coarse description of what Shizuku can currently do for us.
 *
 * We deliberately keep this to four buckets, because those are the four things a user
 * can actually act on: nothing to do, grant a permission, start the service, install the app.
 */
enum class ShizukuState {
    /** Binder is alive and `moe.shizuku.manager.permission.API_V23` is granted. */
    READY,

    /** Binder is alive but we have not been granted permission (yet, or it was denied). */
    PERMISSION_REQUIRED,

    /** Shizuku is installed but its service is not running (needs adb / wireless debugging). */
    NOT_RUNNING,

    /** The Shizuku manager app is not installed at all. */
    NOT_INSTALLED,
}
