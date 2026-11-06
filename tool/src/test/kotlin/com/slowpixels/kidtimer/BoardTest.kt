package com.slowpixels.kidtimer

import com.slowpixels.kidtimer.model.Board
import com.slowpixels.kidtimer.model.Dial.Companion.MINUTE
import com.slowpixels.kidtimer.model.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** What happens to the timers: the same rules as the Kompakt's, plus what Light's limits changed. */
class BoardTest {
    private val file = File(Files.createTempDirectory("kid").toFile(), "timers.properties")
    private val board = Board(Store(file))
    private val t0 = 1_000_000_000L

    private fun minutes(n: Int) = board.update { it.copy(seconds = n * 60) }

    @Test fun aTimerStartsWithTheChoicesOnTheBoard() {
        board.update { it.copy(seconds = 150, sound = "farm", picture = "bear", voices = false) }
        val t = board.start("Bath ", t0)
        assertEquals("Bath", t.name)
        assertEquals(150_000L, t.totalMs)
        assertEquals(t0 + 150_000, t.endsAt)
        assertEquals(listOf("farm", "bear"), listOf(t.sound, t.picture))
        assertFalse(t.voice)
    }

    @Test fun anUnnamedTimerTakesTheLowestFreeNumber() {
        assertEquals("Timer 1", board.start("", t0).name)
        val second = board.start("", t0)
        assertEquals("Timer 2", second.name)
        board.cancel(board.timers.value.first().id)
        assertEquals("Timer 1", board.nextName())
    }

    @Test fun theSoonestTimerIsListedFirst() {
        minutes(10); val late = board.start("Late", t0)
        minutes(2); val soon = board.start("Soon", t0)
        assertEquals(listOf(soon.id, late.id), board.timers.value.map { it.id })
    }

    @Test fun nothingRingsBeforeItsEnd() {
        val t = board.start("", t0)
        assertNull(board.tick(t.endsAt - 1).sound)
        assertEquals(emptyList<Int>(), board.ringing.value)
    }

    /** The clock is never told to sleep past an end, and looks at least once a second. */
    @Test fun theNextLookLandsOnTheEnd() {
        val t = board.start("", t0)
        assertEquals(Board.MAX_WAIT_MS, board.tick(t0).nextInMs)
        assertEquals(40L, board.tick(t.endsAt - 40).nextInMs)
    }

    @Test fun aTimerRingsOnceAtItsEnd() {
        val t = board.start("", t0)
        assertEquals(t, board.tick(t.endsAt).sound)
        assertEquals(listOf(t.id), board.ringing.value)
        assertEquals("It goes on ringing, without starting again", null, board.tick(t.endsAt + 500).sound)
    }

    /** With the tool in front, the ring brings the kid screen up over the grown-up's list. */
    @Test fun aRingTakesOverTheKidScreen() {
        val t = board.start("", t0)
        assertNull(board.kid.value)
        board.tick(t.endsAt)
        assertEquals(t.id, board.kid.value)
    }

    @Test fun aTimerEndingDuringARingJoinsItAndStopEndsBoth() {
        minutes(2); val a = board.start("A", t0)
        minutes(3); val b = board.start("B", t0)
        minutes(9); val c = board.start("C", t0)
        assertEquals(a, board.tick(a.endsAt).sound)
        assertNull("One sound goes on", board.tick(b.endsAt).sound)
        assertEquals(listOf(a.id, b.id), board.ringing.value)
        assertEquals(b.id, board.kid.value)
        board.stop()
        assertEquals(listOf(c.id), board.timers.value.map { it.id })
        assertEquals(emptyList<Int>(), board.ringing.value)
        assertNull(board.kid.value)
    }

    @Test fun addingThirtySecondsMovesTheEndAndGrowsTheDisc() {
        minutes(5); val t = board.start("", t0)
        board.extend(t.id, Board.EXTRA_MS, t0 + 1000)
        val longer = board.get(t.id)!!
        assertEquals(t.endsAt + 30_000, longer.endsAt)
        assertEquals(5 * MINUTE + 30_000, longer.face.spanMs)
        assertNull(board.tick(t.endsAt).sound)
        assertNotNull(board.tick(longer.endsAt).sound)
    }

    @Test fun nothingIsAddedToATimerThatsUp() {
        val t = board.start("", t0)
        board.tick(t.endsAt)
        board.extend(t.id, Board.EXTRA_MS, t.endsAt + 100)
        assertEquals(t.endsAt, board.get(t.id)!!.endsAt)
    }

    @Test fun cancellingARunningTimerLeavesTheRingAlone() {
        minutes(1); val a = board.start("A", t0)
        minutes(5); val b = board.start("B", t0)
        board.tick(a.endsAt)
        assertFalse(board.cancel(b.id))
        assertEquals(listOf(a.id), board.ringing.value)
    }

    @Test fun cancellingARingingTimerStopsTheSound() {
        val t = board.start("", t0)
        board.tick(t.endsAt)
        assertTrue(board.cancel(t.id))
        assertEquals(emptyList<Int>(), board.ringing.value)
        assertTrue(board.timers.value.isEmpty())
    }

    /** A ring in the background can't be stopped from the lock screen, so it can't go on for ever. */
    @Test fun aRingNobodyStopsEndsByItself() {
        val t = board.start("", t0)
        board.show(t.id)
        board.tick(t.endsAt)
        assertFalse(board.tick(t.endsAt + Board.SILENCE_AFTER_MS - 1).silence)
        val last = board.tick(t.endsAt + Board.SILENCE_AFTER_MS)
        assertTrue(last.silence)
        assertNull(last.nextInMs)
        assertTrue(board.timers.value.isEmpty())
        assertNull(board.kid.value)
    }

    /** A back swipe takes the kid screen down, but only a grown-up's hold clears this. */
    @Test fun onlyLeavingTakesATimerOffTheKidScreenAndItKeepsRunning() {
        val t = board.start("", t0)
        board.show(t.id)
        board.tick(t0 + 1000)
        assertEquals(t.id, board.kid.value)
        board.leave()
        assertNull(board.kid.value)
        assertEquals(listOf(t.id), board.timers.value.map { it.id })
        assertTrue(board.busy(t0 + 1000))
        assertEquals(t, board.tick(t.endsAt).sound)
    }

    @Test fun aTimerThatIsGoneCantBeShown() {
        board.show(42)
        assertNull(board.kid.value)
    }

    @Test fun theToolIsBusyWhileAnythingCountsOrRings() {
        val t = board.start("", t0)
        assertTrue(board.busy(t0))
        board.tick(t.endsAt)
        assertTrue("Still ringing", board.busy(t.endsAt + 1000))
        board.stop()
        assertFalse(board.busy(t.endsAt + 1000))
    }

    /** The process can die with the timers running; they're all in the file. */
    @Test fun timersAndChoicesSurviveARestart() {
        board.update { it.copy(seconds = 120, sound = "farm", picture = "cat", voices = false) }
        val t = board.start("Bath", t0)
        val again = Board(Store(file))
        assertEquals(listOf(t), again.timers.value)
        assertEquals(board.prefs.value, again.prefs.value)
        assertNull(again.tick(t.endsAt - 1).sound)
        assertEquals(t, again.tick(t.endsAt).sound)
        assertTrue("Ids aren't reused", again.start("", t0).id > t.id)
    }

    /** Woken late, as a job restarted after being stopped could be: a late ring beats none. */
    @Test fun aTimerFoundALittleLateStillRings() {
        val t = board.start("", t0)
        val again = Board(Store(file))
        assertEquals(t, again.tick(t.endsAt + Board.LATE_MS).sound)
    }

    /** The phone was off, or the tool was stopped: no ringing out of nowhere, but it isn't hidden either. */
    @Test fun aTimerFoundLongPastItsEndStaysListedAndQuiet() {
        val t = board.start("", t0)
        val again = Board(Store(file))
        val now = t.endsAt + Board.LATE_MS + 1
        val tick = again.tick(now)
        assertNull(tick.sound)
        assertNull("Nothing to wait for", tick.nextInMs)
        assertEquals(emptyList<Int>(), again.ringing.value)
        assertEquals(listOf(t.id), again.timers.value.map { it.id })
        assertFalse(again.busy(now))
        assertFalse(again.cancel(t.id))
        assertTrue(again.timers.value.isEmpty())
    }

    @Test fun aQuietTimerDoesNotHoldUpTheNextOne() {
        val old = board.start("Old", t0)
        minutes(60); val next = board.start("Next", t0 + Board.LATE_MS)
        val again = Board(Store(file))
        val now = old.endsAt + Board.LATE_MS + 1
        assertEquals(1000L, again.tick(now).nextInMs)
        assertEquals(next, again.tick(next.endsAt).sound)
    }
}
