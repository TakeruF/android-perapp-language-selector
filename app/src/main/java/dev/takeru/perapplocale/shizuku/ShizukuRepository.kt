package dev.takeru.perapplocale.shizuku

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/**
 * Wraps the Shizuku client API in a [StateFlow] so Compose can react to the binder
 * appearing/disappearing and to permission results.
 *
 * Every call here is defensive: Shizuku's static API throws [IllegalStateException] when the
 * binder has never been received, and we must never crash just because Shizuku is absent.
 */
class ShizukuRepository(private val context: Context) {

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val REQUEST_CODE = 4919

        /**
         * Shizuku pre-v11 used a completely different (and now removed) authorization model.
         * We only support v11+, which is what every current Shizuku/Sui build ships.
         */
        private const val MIN_SUPPORTED_VERSION = 11
    }

    private val _state = MutableStateFlow(ShizukuState.NOT_INSTALLED)
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener { refresh() }
    private val permissionResult =
        Shizuku.OnRequestPermissionResultListener { _, _ -> refresh() }

    fun register() {
        Shizuku.addBinderReceivedListenerSticky(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
        refresh()
    }

    fun unregister() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionResult)
    }

    fun refresh() {
        _state.value = computeState()
    }

    private fun computeState(): ShizukuState {
        val alive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!alive) {
            return if (isShizukuInstalled()) ShizukuState.NOT_RUNNING else ShizukuState.NOT_INSTALLED
        }
        // Pre-v11 cannot grant per-app permission; treat it as "not running" so the user
        // is pushed towards updating Shizuku instead of hitting confusing failures later.
        val preV11 = runCatching { Shizuku.isPreV11() }.getOrDefault(true)
        val version = runCatching { Shizuku.getVersion() }.getOrDefault(0)
        if (preV11 || version < MIN_SUPPORTED_VERSION) return ShizukuState.NOT_RUNNING

        val granted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return if (granted) ShizukuState.READY else ShizukuState.PERMISSION_REQUIRED
    }

    fun requestPermission() {
        runCatching { Shizuku.requestPermission(REQUEST_CODE) }
    }

    /** True when the user ticked "don't ask again" and we can no longer show the dialog. */
    fun isPermanentlyDenied(): Boolean =
        runCatching {
            Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED &&
                !Shizuku.shouldShowRequestPermissionRationale()
        }.getOrDefault(false)

    private fun isShizukuInstalled(): Boolean {
        // Shizuku and Sui both expose the manager package; Sui users have it installed too.
        return runCatching {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        }.getOrDefault(false)
    }
}
