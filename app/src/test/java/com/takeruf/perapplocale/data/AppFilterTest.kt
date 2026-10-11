package com.takeruf.perapplocale.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterTest {
    private fun app(tag: String = "", known: Boolean = true, userId: Int = 0) =
        AppInfo("example.app", userId, userId.toLong(), userId != 0, "Example", false, tag, known)

    @Test fun unconfiguredRequiresAConfirmedSystemDefault() {
        assertTrue(AppFilter.UNCONFIGURED.matches(app()))
        assertFalse(AppFilter.UNCONFIGURED.matches(app(known = false)))
        assertFalse(AppFilter.UNCONFIGURED.matches(app("ja-JP")))
        assertTrue(AppFilter.CONFIGURED.matches(app("ja-JP", known = false)))
    }

    @Test fun languageTabsIncludeOnlyAppliedLocalesAndCountProfilesSeparately() {
        val counts = listOf(app("ja-JP"), app("ja-JP", userId = 999), app("en-US"), app(), app(known = false))
            .filterCounts()
        assertEquals(3, counts.configured)
        assertEquals(1, counts.unconfigured)
        assertEquals(mapOf("ja-JP" to 2, "en-US" to 1), counts.languages)
        assertFalse("fr-FR" in counts.languages)
    }

    @Test fun regionalLocalesHaveTheirOwnMatchingTabs() {
        val us = app("en-US")
        val uk = app("en-GB")
        assertTrue(AppFilter.Language("en-US").matches(us))
        assertFalse(AppFilter.Language("en-US").matches(uk))
        assertEquals(mapOf("en-US" to 1, "en-GB" to 1), listOf(us, uk).filterCounts().languages)
    }

    @Test fun removingLastAssignmentRemovesTabAndReturnsToAll() {
        val original = listOf(app("ja-JP"), app("en-US", userId = 999))
        val selected = AppFilter.Language("ja-JP")
        assertEquals(selected, original.filterCounts().availableFilter(selected))
        val updated = original.withLocaleFor(original[0].target, "")
        assertEquals(mapOf("en-US" to 1), updated.filterCounts().languages)
        assertEquals(AppFilter.ALL, updated.filterCounts().availableFilter(selected))
        assertEquals(AppFilter.UNCONFIGURED, updated.filterCounts().availableFilter(AppFilter.UNCONFIGURED))
    }
}
