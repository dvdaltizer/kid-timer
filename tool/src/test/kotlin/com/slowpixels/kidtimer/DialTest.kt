package com.slowpixels.kidtimer

import com.slowpixels.kidtimer.model.Dial
import com.slowpixels.kidtimer.model.Dial.Companion.MINUTE
import com.slowpixels.kidtimer.model.Lengths
import com.slowpixels.kidtimer.model.Timer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The disc's maths: how big the slice is, when the face changes, the marks around it; and the timer's sums. */
class DialTest {
    @Test fun theDialStartsFullAndEmptiesToNothing() {
        val d = Dial(10 * MINUTE)
        assertEquals(360f, d.sweep(10 * MINUTE), 0.001f)
        assertEquals(180f, d.sweep(5 * MINUTE), 0.001f)
        assertEquals(0f, d.sweep(0), 0.001f)
        // Never more than a whole turn, never less than none.
        assertEquals(360f, d.sweep(11 * MINUTE), 0.001f)
        assertEquals(0f, d.sweep(-5), 0.001f)
    }

    @Test fun theFaceStepsAboutADegreeButNeverFasterThanAQuarterSecond() {
        assertEquals(250L, Dial(MINUTE).stepMs)
        assertEquals(500L, Dial(2 * MINUTE).stepMs)
        assertEquals(2_000L, Dial(10 * MINUTE).stepMs)
        assertEquals(5_000L, Dial(20 * MINUTE).stepMs)
        assertEquals(10_000L, Dial(Dial.HOUR).stepMs)
        assertEquals(30_000L, Dial(180 * MINUTE).stepMs)
        // Every step divides a minute, so a whole-minute timer starts on a step and the minutes figure changes with the face.
        for (m in 1..(Lengths.MAX_SECONDS / 60)) assertEquals(0L, MINUTE % Dial(m * MINUTE).stepMs)
    }

    @Test fun theFaceNeverShowsLessThanIsLeftAndIsEmptyExactlyAtTheEnd() {
        val d = Dial(7 * MINUTE)
        for (left in listOf(7 * MINUTE, 7 * MINUTE - 1, 200_001L, 200_000L, 1_999L, 1L)) {
            val s = d.shown(left)
            assertTrue("shown $s for $left", s >= left && s - left < d.stepMs)
        }
        assertEquals(0L, d.shown(0))
        assertEquals(0L, d.shown(-100))
        assertEquals(7 * MINUTE, d.shown(7 * MINUTE))
    }

    @Test fun aWholeTimerRedrawsOnlyWhenTheSliceMoves() {
        for (total in listOf(10 * MINUTE, 20 * MINUTE, 3 * MINUTE, 150 * MINUTE)) {
            val d = Dial(total)
            var left = total
            var shown = d.shown(left)
            var redraws = 0
            while (left > 0) {
                val wait = d.nextChangeIn(left)
                assertTrue("wait $wait", wait in 1..d.stepMs)
                left -= wait
                val next = d.shown(left)
                assertEquals("one step at a time", d.stepMs, shown - next)
                shown = next
                redraws++
            }
            assertEquals(0L, shown)
            assertEquals(total / d.stepMs, redraws.toLong())
            // Four redraws a second at the most, and only on the shortest timers.
            assertTrue(total / redraws >= 250)
        }
    }

    @Test fun hourDialMarksAreAClockOfMinutes() {
        val marks = Dial(Dial.HOUR).marks()
        assertEquals(60, marks.size)
        assertEquals(12, marks.count { it.major })
        assertEquals((0..55 step 5).map { "$it" }, marks.mapNotNull { it.label })
    }

    @Test fun shortDialsCountMinutesWithFinerTicks() {
        val two = Dial(2 * MINUTE).marks()
        assertEquals(12, two.size)
        assertEquals(listOf("0", "1"), two.mapNotNull { it.label })
        // A one-minute dial has ticks but no numbers.
        assertTrue(Dial(MINUTE).marks().all { it.label == null })
        assertEquals(listOf("0", "1", "2", "3", "4", "5", "6"), Dial(7 * MINUTE).marks().mapNotNull { it.label })
    }

    @Test fun numbersNeverCrowdTheTop() {
        // 61 minutes: the 60 would sit almost on the 0, so it's left off.
        assertEquals(listOf("0", "15", "30", "45"), Dial(61 * MINUTE).marks().mapNotNull { it.label })
        for (m in 1..(Lengths.MAX_SECONDS / 60)) {
            val d = Dial(m * MINUTE)
            assertTrue("$m min: ${Dial.labelGap(d)}", Dial.labelGap(d) >= Dial.LABEL_GAP_DEGREES)
            assertTrue("$m min: ${d.marks().size} ticks", d.marks().size in 4..72)
        }
    }

    @Test fun minutesLeftRoundUp() {
        assertEquals(0L, Dial.minutesLeft(0))
        assertEquals(1L, Dial.minutesLeft(1))
        assertEquals(1L, Dial.minutesLeft(59_000))
        assertEquals(1L, Dial.minutesLeft(MINUTE))
        assertEquals(2L, Dial.minutesLeft(MINUTE + 1))
    }

    @Test fun aTimerCountsDownFromItsEnd() {
        val r = Timer(1, "Timer 1", 5 * MINUTE, endsAt = 1_000_000, sound = "farm", picture = "bear")
        assertEquals(5 * MINUTE, r.left(1_000_000 - 5 * MINUTE))
        assertEquals(1_000L, r.left(999_000))
        assertEquals(0L, r.left(1_000_500))
        assertTrue(r.done(1_000_000))
        assertEquals(5 * MINUTE, r.face.spanMs)
    }

    /** A length is typed from the right, as Clock's timers: the last two figures are seconds, the rest minutes. */
    @Test fun typingMinutesAndSecondsFillsFromTheRightUpToThreeHours() {
        assertEquals("2", Lengths.typed("", 2))
        assertEquals("23", Lengths.typed("2", 3))
        assertEquals("230", Lengths.typed("23", 0))
        assertEquals(150, Lengths.seconds("230"))
        assertEquals(45, Lengths.seconds("45"))
        assertEquals(90, Lengths.seconds("90"))
        assertEquals(10_800, Lengths.seconds("18000"))
        assertEquals(0, Lengths.seconds(""))
        assertNull("no leading zero", Lengths.typed("", 0))
        assertNull("over three hours", Lengths.typed("1800", 1))
        assertNull("six figures", Lengths.typed("10000", 0))
    }

    @Test fun lengthsAreSaidShortly() {
        assertEquals(listOf("10 min", "45 sec", "2:30", "1:05"), listOf(600, 45, 150, 65).map { Lengths.words(it) })
    }

    /** Under the disc: minutes rounded up, and in the last minute the seconds. */
    @Test fun whatsLeftIsMinutesThenSeconds() {
        assertEquals(listOf("7 min", "2 min", "1 min", "59 sec", "1 sec"),
            listOf(6 * MINUTE + 20_000, MINUTE + 1, MINUTE, 58_200L, 1L).map { Lengths.leftWords(it) })
    }
}
