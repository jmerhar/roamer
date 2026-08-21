package si.merhar.roamer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RewriteLogTest {

    @Test
    fun `prepends entry to empty log`() {
        assertEquals("first", RewriteLog.prepend("", "first"))
    }

    @Test
    fun `newest entry comes first`() {
        val log = RewriteLog.prepend(RewriteLog.prepend("", "older"), "newer")
        assertEquals("newer\nolder", log)
    }

    @Test
    fun `retains entries up to the maximum`() {
        var log = ""
        for (i in 1..5) log = RewriteLog.prepend(log, "entry $i", maxEntries = 5)
        assertEquals(5, log.lines().size)
        assertEquals("entry 5", log.lines().first())
        assertEquals("entry 1", log.lines().last())
    }

    @Test
    fun `drops the oldest entry once the maximum is exceeded`() {
        var log = ""
        for (i in 1..6) log = RewriteLog.prepend(log, "entry $i", maxEntries = 5)
        val lines = log.lines()
        assertEquals(5, lines.size)
        assertEquals("entry 6", lines.first())
        assertEquals("entry 2", lines.last())
        assertTrue("entry 1" !in lines, "oldest entry should have been dropped")
    }

    @Test
    fun `never exceeds the maximum however many entries are added`() {
        var log = ""
        for (i in 1..100) log = RewriteLog.prepend(log, "entry $i", maxEntries = 3)
        assertEquals(3, log.lines().size)
        assertEquals(listOf("entry 100", "entry 99", "entry 98"), log.lines())
    }

    @Test
    fun `discards blank lines already present in the stored value`() {
        assertEquals("new\nkept", RewriteLog.prepend("\nkept\n\n", "new"))
    }

    @Test
    fun `ignores a blank entry rather than storing an empty line`() {
        assertEquals("kept", RewriteLog.prepend("kept", "   "))
        assertEquals("kept", RewriteLog.prepend("kept", ""))
    }

    @Test
    fun `trims an over-long stored value even when the entry is blank`() {
        val overLong = (1..10).joinToString("\n") { "entry $it" }
        assertEquals(3, RewriteLog.prepend(overLong, "", maxEntries = 3).lines().size)
    }

    @Test
    fun `default retention is the documented maximum`() {
        var log = ""
        for (i in 1..(RewriteLog.MAX_ENTRIES + 10)) log = RewriteLog.prepend(log, "entry $i")
        assertEquals(RewriteLog.MAX_ENTRIES, log.lines().size)
    }
}
