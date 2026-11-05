package com.openminis.app.mibot.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** [fork/mibot-feed] File store: order, like, delete, cap, persistence, atomic write. */
class FeedStoreTest {
    private fun tmp(): File = Files.createTempDirectory("feed").toFile()

    @Test fun newestFirstAndPersists() {
        val dir = tmp()
        val s = FeedStore(dir)
        s.add(FeedKind.TASK_RESULT, "a", "body a", "session:1", now = 1000)
        s.add(FeedKind.KEY_ACQUIRED, "b", now = 3000)
        s.add(FeedKind.PLUGIN_EVENT, "c", now = 2000)
        assertEquals(listOf("b", "c", "a"), s.entries.value.map { it.title })
        val reopened = FeedStore(dir)
        assertEquals(listOf("b", "c", "a"), reopened.entries.value.map { it.title })
        val a = reopened.entries.value.last()
        assertEquals("body a", a.body)
        assertEquals("session:1", a.sourceRef)
        assertEquals(FeedKind.TASK_RESULT, a.kind)
        assertFalse(File(dir, FeedStore.FILE_NAME + ".tmp").exists())
    }

    @Test fun blankTitleRejectedAndFieldsTrimmed() {
        val s = FeedStore(tmp())
        assertNull(s.add(FeedKind.TASK_RESULT, "   "))
        val e = s.add(FeedKind.TASK_RESULT, "  t  ", "x".repeat(FeedStore.MAX_BODY + 50))
        assertNotNull(e)
        assertEquals("t", e!!.title)
        assertEquals(FeedStore.MAX_BODY, e.body.length)
    }

    @Test fun likeToggleAndDelete() {
        val dir = tmp()
        val s = FeedStore(dir)
        val e = s.add(FeedKind.GROUP_SUMMARY, "g")!!
        assertTrue(s.toggleLike(e.id))
        assertTrue(FeedStore(dir).entries.value.single().liked)
        assertTrue(s.setLiked(e.id, false))
        assertFalse(s.entries.value.single().liked)
        assertFalse(s.toggleLike("nope"))
        assertTrue(s.delete(e.id))
        assertFalse(s.delete(e.id))
        assertTrue(FeedStore(dir).entries.value.isEmpty())
    }

    @Test fun capDropsOldestUnlikedFirst() {
        val s = FeedStore(tmp(), maxEntries = 3)
        val first = s.add(FeedKind.TASK_RESULT, "1", now = 1)!!
        s.setLiked(first.id, true)
        s.add(FeedKind.TASK_RESULT, "2", now = 2)
        s.add(FeedKind.TASK_RESULT, "3", now = 3)
        s.add(FeedKind.TASK_RESULT, "4", now = 4)
        assertEquals(listOf("4", "3", "1"), s.entries.value.map { it.title })
    }

    @Test fun corruptFileReadsAsEmptyAndRecovers() {
        val dir = tmp()
        File(dir, FeedStore.FILE_NAME).writeText("{not json")
        val s = FeedStore(dir)
        assertTrue(s.entries.value.isEmpty())
        s.add(FeedKind.FEIYU_DIARY, "d")
        assertEquals(1, FeedStore(dir).entries.value.size)
    }

    @Test fun unknownFieldsAndKindsTolerated() {
        val dir = tmp()
        File(dir, FeedStore.FILE_NAME).writeText(
            """{"version":2,"future":1,"entries":[{"id":"x","kind":"new_kind","title":"t","ts":5,"extra":true}]}""",
        )
        val e = FeedStore(dir).entries.value.single()
        assertEquals("new_kind", e.kind)
        assertEquals("", e.body)
        assertFalse(e.liked)
        assertNull(e.sourceRef)
    }

    @Test fun relativeTimeLabels() {
        val now = 10L * 24 * 3_600_000
        assertEquals("刚刚", FeedStore.relativeTime(now - 10_000, now, zh = true))
        assertEquals("5 分钟前", FeedStore.relativeTime(now - 5 * 60_000, now, zh = true))
        assertEquals("3h ago", FeedStore.relativeTime(now - 3 * 3_600_000, now, zh = false))
        assertEquals("昨天", FeedStore.relativeTime(now - 30 * 3_600_000L, now, zh = true))
    }
}
