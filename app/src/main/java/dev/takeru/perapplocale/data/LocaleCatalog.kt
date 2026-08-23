package dev.takeru.perapplocale.data

import android.content.res.Resources
import android.icu.util.ULocale
import android.os.LocaleList
import java.text.Collator
import java.util.concurrent.ConcurrentHashMap
import java.util.Locale

/** Where an entry sits in the picker. The declaration order is the display order. */
enum class LocaleGroup(val title: String) {
    /** A tag that is already applied to the app but is not part of the catalog. */
    CURRENT("Current setting"),

    /** "System Default" plus the device's primary language. */
    SYSTEM("System"),

    /** The extra languages the user added under Settings → System → Languages. */
    ADDED("Your languages"),

    /** Widely spoken languages, most speakers first. */
    COMMON("Common languages"),

    /** Everything else Android knows, in dictionary order. */
    ALL("All languages"),
}

/**
 * One row of the locale picker.
 *
 * [label] is the endonym ("日本語"), [englishName] the English name ("Japanese (Japan)"), and
 * [searchKeys] holds every lowercased string the row can be found by, so a search for 日本語,
 * Japanese or ja-JP all land on the same entry.
 */
data class LocaleEntry(
    val tag: String,
    val label: String,
    val englishName: String,
    val group: LocaleGroup,
    /** Display name in the device's own UI language; what "dictionary order" sorts on. */
    val sortKey: String,
    val searchKeys: List<String>,
) {
    val isSystemDefault: Boolean get() = tag.isEmpty()

    val subtitle: String
        get() = when {
            isSystemDefault -> "Follows the device language"
            englishName.isEmpty() || englishName == label -> tag
            else -> "$englishName · $tag"
        }

    fun toOption(): LocaleOption = LocaleOption(tag, label)

    /**
     * Lower is better; -1 means "no match". An exact tag hit beats a name that starts with the
     * needle, which beats a name that merely contains it — so typing "ja" puts 日本語 above
     * Punjabi (pa-IN, "Panjabi") even though both contain the letters.
     */
    fun score(needle: String): Int {
        if (needle.isEmpty()) return 0
        var best = -1
        for (key in searchKeys) {
            val rank = when {
                key == needle -> 0
                key.startsWith(needle) -> 1
                key.contains(needle) -> 2
                else -> continue
            }
            if (best == -1 || rank < best) best = rank
            if (best == 0) break
        }
        return best
    }
}

/**
 * The full list of locales the picker offers, ordered the way a person is most likely to want
 * them: the device language first, then the languages the user added in Android's own language
 * settings, then the world's most spoken languages, then everything else alphabetically.
 *
 * Building it walks every locale ICU knows (~800 on a current device) and asks for several
 * display names each, so callers should build it off the main thread; the result is cached until
 * the device's language list changes.
 */
object LocaleCatalog {

    @Volatile private var cached: List<LocaleEntry>? = null
    @Volatile private var cachedKey: String = ""

    /** Representative tags for the most spoken languages, most speakers first. */
    private val COMMON_TAGS = listOf(
        "en-US", "en-GB",
        "zh-CN", "zh-TW", "zh-HK",
        "hi-IN",
        "es-ES", "es-MX",
        "ar-EG",
        "fr-FR", "fr-CA",
        "bn-BD",
        "pt-BR", "pt-PT",
        "ru-RU",
        "ur-PK",
        "id-ID",
        "de-DE",
        "ja-JP",
        "mr-IN",
        "te-IN",
        "tr-TR",
        "ta-IN",
        "vi-VN",
        "ko-KR",
        "fa-IR",
        "it-IT",
        "th-TH",
        "gu-IN",
        "pa-IN",
        "pl-PL",
        "uk-UA",
        "nl-NL",
        "fil-PH",
        "ms-MY",
        "sw-KE",
        "my-MM",
        "he-IL",
    )

    val SYSTEM_DEFAULT = LocaleEntry(
        tag = "",
        label = "System Default",
        englishName = "",
        group = LocaleGroup.SYSTEM,
        sortKey = "",
        searchKeys = listOf("system default", "system", "default", "システム", "デフォルト", "端末"),
    )

    private val labelCache = ConcurrentHashMap<String, String>()

    /**
     * Endonym for a single tag, without building the whole catalog — cheap enough to call from a
     * list row. Memoised because the app list asks for the same handful of tags over and over.
     */
    fun labelFor(tag: String): String {
        if (tag.isEmpty()) return SYSTEM_DEFAULT.label
        return labelCache.getOrPut(tag) {
            val locale = Locale.forLanguageTag(tag)
            if (locale.language.isNullOrEmpty()) return@getOrPut tag
            displayName(locale, locale).ifBlank { tag }
        }
    }

    fun entries(): List<LocaleEntry> {
        val key = cacheKey()
        cached?.let { if (cachedKey == key) return it }
        val built = build()
        cached = built
        cachedKey = key
        return built
    }

    /** An entry for a tag outside the catalog — one already applied to an app, say. */
    fun entryFor(tag: String, group: LocaleGroup): LocaleEntry =
        if (tag.isEmpty()) SYSTEM_DEFAULT else entryFor(Locale.forLanguageTag(tag), group, tag)

    private fun cacheKey(): String = systemLocales().toLanguageTags() + "|" + Locale.getDefault().toLanguageTag()

    /**
     * The device's language list, in the order the user put it in Android's settings. We read the
     * system configuration rather than [LocaleList.getDefault] so a per-app locale on *this* app
     * cannot disguise itself as the device language.
     */
    private fun systemLocales(): LocaleList {
        val fromSystem = Resources.getSystem().configuration.locales
        return if (!fromSystem.isEmpty) fromSystem else LocaleList.getDefault()
    }

    private fun build(): List<LocaleEntry> {
        val available = LinkedHashMap<String, Locale>()
        for (locale in Locale.getAvailableLocales()) {
            if (locale.language.isNullOrEmpty()) continue
            // Variants and -u- extensions are calendar/collation flavours of a locale we already
            // list; they would only pad the list with near-duplicates.
            if (locale.variant.isNotEmpty() || locale.extensionKeys.isNotEmpty()) continue
            val tag = locale.toLanguageTag()
            if (tag.isEmpty() || tag == "und" || tag.contains('#')) continue
            available.putIfAbsent(tag, locale)
        }

        val taken = LinkedHashSet<String>()
        val result = ArrayList<LocaleEntry>(available.size + 2)
        result += SYSTEM_DEFAULT

        val system = systemLocales()
        for (i in 0 until system.size()) {
            val locale = system.get(i) ?: continue
            val tag = locale.toLanguageTag()
            if (tag.isEmpty() || tag == "und" || !taken.add(tag)) continue
            val group = if (i == 0) LocaleGroup.SYSTEM else LocaleGroup.ADDED
            result += entryFor(available[tag] ?: locale, group, tag)
        }

        for (wanted in COMMON_TAGS) {
            val locale = representative(available, wanted) ?: continue
            // Apply the short curated tag (zh-CN), not the script-qualified locale ICU matched it
            // to (zh-Hans-CN) — but claim both, so the long form does not turn up again below.
            val tag = Locale.forLanguageTag(wanted).toLanguageTag()
            if (!taken.add(tag)) continue
            taken.add(locale.toLanguageTag())
            result += entryFor(locale, LocaleGroup.COMMON, tag)
        }

        val collator = Collator.getInstance()
        val rest = available.values
            .filter { it.toLanguageTag() !in taken }
            .map { entryFor(it, LocaleGroup.ALL, it.toLanguageTag()) }
            .sortedWith(compareBy(collator) { it.sortKey })
        result += rest

        return result
    }

    /**
     * The locale a curated tag should take its name from. Devices list Chinese only in its
     * script-qualified forms, so ICU decides what zh-CN and zh-HK actually mean rather than us
     * picking whichever script sorts first. After that we settle for any locale of the same
     * language, since not every device ships every regional flavour (`he-IL` is `iw-IL` on some
     * builds, and `sw-KE` may only exist as `sw-TZ`).
     */
    private fun representative(available: Map<String, Locale>, wanted: String): Locale? {
        val canonical = Locale.forLanguageTag(wanted)
        available[canonical.toLanguageTag()]?.let { return it }

        runCatching { ULocale.addLikelySubtags(ULocale.forLanguageTag(wanted)).toLanguageTag() }
            .getOrNull()
            ?.let { likely -> available[likely]?.let { return it } }

        val byTag = compareBy<Locale>({ it.toLanguageTag().length }, { it.toLanguageTag() })
        val sameLanguage = available.values.filter { it.language == canonical.language }
        if (canonical.country.isNotEmpty()) {
            sameLanguage.filter { it.country == canonical.country }.minWithOrNull(byTag)
                ?.let { return it }
        }
        return sameLanguage.minWithOrNull(byTag)
    }

    /**
     * "中文 (简体, 中国)" — ICU assembles language, script and region the way each language writes
     * them, and uses the in-context script name. [java.util.Locale.getDisplayName] on Android
     * uses the standalone one instead, which reads as "中文 (简体中文,中国)".
     */
    private fun displayName(locale: Locale, target: Locale): String =
        runCatching { ULocale.forLocale(locale).getDisplayName(ULocale.forLocale(target)) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: locale.getDisplayName(target)

    private fun entryFor(locale: Locale, group: LocaleGroup, tag: String): LocaleEntry {
        val ui = Locale.getDefault()
        // getDisplayName already assembles "language (script, region)" the way each language
        // writes it — and picks the short script form (中文 (简体, 中国)), which composing the
        // parts by hand does not (中文 (简体中文, 中国)).
        val label = displayName(locale, locale).ifBlank { tag }
        val english = displayName(locale, Locale.ENGLISH)
        val uiName = displayName(locale, ui)

        val keys = linkedSetOf(
            label, english, uiName,
            locale.getDisplayLanguage(locale),
            locale.getDisplayLanguage(Locale.ENGLISH),
            locale.getDisplayLanguage(ui),
            tag, locale.toLanguageTag(), locale.language, locale.country, locale.script,
        ).filter { it.isNotBlank() }.map { it.lowercase(Locale.ROOT) }

        return LocaleEntry(tag, label, english, group, uiName.ifBlank { label }, keys)
    }

    /** Normalises what the user typed so `ja_JP`, `JA-jp` and `ja-JP` all search the same way. */
    fun normalizeQuery(input: String): String =
        input.trim().replace('_', '-').lowercase(Locale.ROOT)
}
