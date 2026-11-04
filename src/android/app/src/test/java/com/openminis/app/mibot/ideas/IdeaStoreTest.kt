package com.openminis.app.mibot.ideas

import com.openminis.app.scheduled.ScheduledRepeatMode
import com.openminis.app.scheduled.ScheduledTargetMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Calendar

/** [fork/mibot-ideas] Notes store + idea → one-shot task mapping. */
class IdeaStoreTest {
    private fun tmp(): File = Files.createTempDirectory("ideas").toFile()

    @Test fun addEditDeletePersist() {
        val dir = tmp()
        val s = IdeaStore(dir)
        assertNull(s.add("   "))
        val a = s.add("  first idea ", color = 2, now = 100)!!
        assertEquals("first idea", a.text)
        assertEquals(2, a.color)
        val b = s.add("second", now = 200)!!
        assertEquals(listOf(b.id, a.id), s.ideas.value.map { it.id })

        val edited = s.update(a.id, text = "first, edited", color = 5, now = 300)!!
        assertEquals("first, edited", edited.text)
        assertEquals(5, edited.color)
        assertEquals(300, edited.updatedAt)
        assertEquals(listOf(a.id, b.id), s.ideas.value.map { it.id })
        assertNull(s.update(a.id, text = "  "))
        assertNull(s.update("missing", text = "x"))

        val re = IdeaStore(dir)
        assertEquals(listOf("first, edited", "second"), re.ideas.value.map { it.text })
        assertTrue(re.delete(b.id))
        assertFalse(re.delete(b.id))
        assertEquals(1, IdeaStore(dir).ideas.value.size)
        assertFalse(File(dir, IdeaStore.FILE_NAME + ".tmp").exists())
    }

    @Test fun colorsClampedToSix() {
        val s = IdeaStore(tmp())
        assertEquals(6, IdeaStore.COLOR_COUNT)
        assertEquals(0, s.add("x", color = 9)!!.color)
        assertEquals(0, s.add("y", color = -1)!!.color)
        val z = s.add("z", color = 5)!!
        assertEquals(5, z.color)
        assertEquals(0, s.update(z.id, color = 6)!!.color)
    }

    @Test fun pinnedFirstWithoutTouchingUpdatedAt() {
        val dir = tmp()
        val s = IdeaStore(dir)
        val old = s.add("old", now = 1)!!
        val mid = s.add("mid", now = 2)!!
        val new = s.add("new", now = 3)!!
        val pinned = s.setPinned(old.id, true)!!
        assertEquals(1, pinned.updatedAt)
        assertEquals(listOf(old.id, new.id, mid.id), s.ideas.value.map { it.id })
        s.setPinned(mid.id, true)
        assertEquals(listOf(mid.id, old.id, new.id), IdeaStore(dir).ideas.value.map { it.id })
        s.setPinned(mid.id, false)
        assertEquals(listOf(old.id, new.id, mid.id), s.ideas.value.map { it.id })
        assertNull(s.setPinned("missing", true))
    }

    @Test fun corruptFileIsEmpty() {
        val dir = tmp()
        File(dir, IdeaStore.FILE_NAME).writeText("garbage")
        val s = IdeaStore(dir)
        assertTrue(s.ideas.value.isEmpty())
        assertNotNull(s.add("ok"))
    }

    @Test fun oneShotTaskFiresOnceAtPickedTime() {
        val now = System.currentTimeMillis()
        val day = IdeaTasks.startOfDay(now + 2 * 24 * 3_600_000L)
        val task = IdeaTasks.oneShot("  写周报\n记得附上数据 ", day, 9, 30)
        assertEquals("写周报", task.label)
        assertEquals("写周报\n记得附上数据", task.prompt)
        assertEquals(ScheduledRepeatMode.ONCE, task.repeatMode)
        assertEquals(ScheduledTargetMode.NewSession, task.targetMode)
        assertTrue(task.isOneShot)
        assertEquals(IdeaTasks.fireAt(day, 9, 30), task.nextTriggerMs(now))
        val c = Calendar.getInstance().apply { timeInMillis = task.nextTriggerMs(now)!! }
        assertEquals(9, c.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, c.get(Calendar.MINUTE))
    }

    @Test fun defaultFireIsFutureWholeHour() {
        val now = System.currentTimeMillis()
        val f = IdeaTasks.defaultFire(now)
        assertTrue(f >= now + 30 * 60_000L)
        assertTrue(f <= now + 90 * 60_000L)
        val c = Calendar.getInstance().apply { timeInMillis = f }
        assertEquals(0, c.get(Calendar.MINUTE))
        assertEquals("点子", IdeaTasks.label("   "))
    }
}
