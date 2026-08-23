package dev.takeru.perapplocale.data

import android.os.LocaleList
import java.util.Locale

/**
 * A locale the user can pick. [tag] is a BCP 47 language tag, or the empty string for
 * "System Default" (which we send to the framework as an empty [LocaleList]).
 */
data class LocaleOption(val tag: String, val label: String) {

    val isSystemDefault: Boolean get() = tag.isEmpty()

    fun toLocaleList(): LocaleList =
        if (isSystemDefault) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)

    companion object {
        /**
         * Human label for an arbitrary tag: the locale's own endonym, so a tag reads the same
         * way here as it does in the picker. See [LocaleCatalog.labelFor].
         */
        fun labelFor(tag: String): String = LocaleCatalog.labelFor(tag)

        /** First tag of a [LocaleList], or "" when the app follows the system. */
        fun tagOf(locales: LocaleList?): String =
            if (locales == null || locales.isEmpty) "" else locales.get(0).toLanguageTag()
    }
}
