package dev.takeru.perapplocale.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppTargetTest {
    @Test fun runtimeKeyIncludesUserId() {
        assertEquals("999:com.tencent.mm", AppTarget("com.tencent.mm", 999).runtimeKey)
    }

    @Test fun samePackageInDifferentProfilesIsDistinct() {
        assertNotEquals(AppTarget("com.tencent.mm", 0), AppTarget("com.tencent.mm", 999))
        assertEquals(2, setOf(AppTarget("com.tencent.mm", 0), AppTarget("com.tencent.mm", 999)).size)
    }

    @Test fun ownAppRequiresBothPackageAndUser() {
        assertTrue(AppTarget("dev.takeru.perapplocale", 0).isOwnTarget("dev.takeru.perapplocale", 0))
        assertFalse(AppTarget("dev.takeru.perapplocale", 999).isOwnTarget("dev.takeru.perapplocale", 0))
    }

    @Test fun legacyAssignmentsMigrateOnlyToCurrentUser() {
        val migrated = migrateLegacyAssignmentKeys(mapOf("com.tencent.mm" to "ja", "s43:com.tencent.mobileqq" to "zh-CN"), 42)
        assertEquals("ja", migrated["s42:com.tencent.mm"])
        assertEquals("zh-CN", migrated["s43:com.tencent.mobileqq"])
    }

    @Test fun localeUpdateTouchesOnlySelectedProfileTarget() {
        val owner = AppInfo("com.tencent.mm", 0, 0, false, "WeChat", false)
        val clone = AppInfo("com.tencent.mm", 999, 42, true, "WeChat", false)
        val updated = listOf(owner, clone).withLocaleFor(clone.target, "zh-CN")
        assertEquals("", updated[0].localeTag)
        assertEquals("zh-CN", updated[1].localeTag)
    }

    @Test fun recreatedProfileDoesNotReuseOldPersistentAssignmentKey() {
        assertNotEquals(
            AppInfo("com.tencent.mm", 999, 42, true, "WeChat", false).assignmentKey,
            AppInfo("com.tencent.mm", 999, 43, true, "WeChat", false).assignmentKey,
        )
    }
}
