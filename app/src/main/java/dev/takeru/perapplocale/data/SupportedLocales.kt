package dev.takeru.perapplocale.data

import android.os.LocaleList
import java.util.Locale

/** The language support an app officially exposes through Android's LocaleConfig API. */
sealed interface SupportedLocales {
    data object Loading : SupportedLocales
    data class Declared(val tags: List<String>) : SupportedLocales {
        fun supports(tag: String): Boolean {
            if (tag.isEmpty()) return true
            val desired = Locale.forLanguageTag(tag)
            return tags.any { supportedTag ->
                val supported = Locale.forLanguageTag(supportedTag)
                LocaleList.matchesLanguageAndScript(supported, desired)
            }
        }
    }

    /** No android:localeConfig (or dynamic override) was declared. This is not non-support. */
    data object NotDeclared : SupportedLocales

    /** A declaration exists but Android could not parse it. */
    data object Invalid : SupportedLocales

    /** The package disappeared or its resources could not be opened. */
    data object Unavailable : SupportedLocales
}
