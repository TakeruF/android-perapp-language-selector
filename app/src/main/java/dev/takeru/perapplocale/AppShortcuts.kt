package dev.takeru.perapplocale

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon

const val ACTION_RESET_ALL_CUSTOMIZATIONS =
    "dev.takeru.perapplocale.action.RESET_ALL_CUSTOMIZATIONS"

/** Publishes the actions shown when the launcher icon is long-pressed. */
fun installAppShortcuts(context: Context) {
    val shortcutManager = context.getSystemService(ShortcutManager::class.java)
    val resetAll = ShortcutInfo.Builder(context, "reset_all_customizations")
        .setShortLabel(context.getString(R.string.shortcut_reset_all))
        .setLongLabel(context.getString(R.string.shortcut_reset_all_long))
        .setIcon(Icon.createWithResource(context, R.drawable.ic_reset_all))
        .setIntent(
            Intent(context, MainActivity::class.java)
                .setAction(ACTION_RESET_ALL_CUSTOMIZATIONS)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP,
                ),
        )
        .build()

    shortcutManager.dynamicShortcuts = listOf(resetAll)
}
