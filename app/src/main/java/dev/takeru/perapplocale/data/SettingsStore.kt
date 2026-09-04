package dev.takeru.perapplocale.data

import android.content.Context
import android.os.Process
import android.os.UserManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "per_app_locale")

/** UI preferences plus a local mirror of what we have assigned. */
data class Settings(
    val showSystemApps: Boolean = false,
    val configuredFirst: Boolean = true,
    /** Stable user-serial + package key -> BCP 47 tag. A cache of system state, not source of truth. */
    val assignments: Map<String, String> = emptyMap(),
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val SHOW_SYSTEM_APPS = booleanPreferencesKey("show_system_apps")
        val CONFIGURED_FIRST = booleanPreferencesKey("configured_first")
        val ASSIGNMENTS = stringPreferencesKey("assignments")
    }

    val settings: Flow<Settings> = context.dataStore.data
        .catch { cause ->
            // A corrupted preferences file must not take the app down; start over instead.
            if (cause is IOException) emit(emptyPreferences()) else throw cause
        }
        .map { prefs ->
            Settings(
                showSystemApps = prefs[Keys.SHOW_SYSTEM_APPS] ?: false,
                configuredFirst = prefs[Keys.CONFIGURED_FIRST] ?: true,
                assignments = migrateLegacyAssignmentKeys(decodeAssignments(prefs[Keys.ASSIGNMENTS]), currentUserSerialNumber()),
            )
        }

    suspend fun setShowSystemApps(value: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_SYSTEM_APPS] = value }
    }

    suspend fun setConfiguredFirst(value: Boolean) {
        context.dataStore.edit { it[Keys.CONFIGURED_FIRST] = value }
    }

    /** Records (or, for an empty [tag], forgets) what we last applied to [target]. */
    suspend fun recordAssignment(assignmentKey: String, tag: String) {
        recordAssignments(listOf(assignmentKey), tag)
    }

    /** Records one bulk operation atomically so partial DataStore writes cannot split the batch. */
    suspend fun recordAssignments(assignmentKeys: Collection<String>, tag: String) {
        context.dataStore.edit { prefs ->
            val current = migrateLegacyAssignmentKeys(
                decodeAssignments(prefs[Keys.ASSIGNMENTS]), currentUserSerialNumber(),
            ).toMutableMap()
            for (key in assignmentKeys) {
                if (tag.isEmpty()) current.remove(key) else current[key] = tag
            }
            prefs[Keys.ASSIGNMENTS] = encodeAssignments(current)
        }
    }

    /** Forgets successfully reset packages without discarding unrelated UI preferences. */
    suspend fun forgetAssignments(assignmentKeys: Collection<String>) {
        if (assignmentKeys.isEmpty()) return
        context.dataStore.edit { prefs ->
            val current = migrateLegacyAssignmentKeys(
                decodeAssignments(prefs[Keys.ASSIGNMENTS]), currentUserSerialNumber(),
            ).toMutableMap()
            assignmentKeys.forEach(current::remove)
            prefs[Keys.ASSIGNMENTS] = encodeAssignments(current)
        }
    }

    private fun decodeAssignments(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            buildMap {
                for (key in json.keys()) {
                    val value = json.optString(key)
                    if (value.isNotEmpty()) put(key, value)
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeAssignments(map: Map<String, String>): String =
        JSONObject(map as Map<*, *>).toString()

    private fun currentUserSerialNumber(): Long = context.getSystemService(UserManager::class.java)
        .getSerialNumberForUser(Process.myUserHandle())

}

/** Old records used only the package name; they unambiguously belong to the app's current user. */
internal fun migrateLegacyAssignmentKeys(assignments: Map<String, String>, currentUserSerialNumber: Long): Map<String, String> =
    assignments.mapKeys { (key, _) ->
        if (key.startsWith("s") && key.substringBefore(':').drop(1).toLongOrNull() != null) key
        else "s$currentUserSerialNumber:${key.substringAfter(':', key)}"
    }
