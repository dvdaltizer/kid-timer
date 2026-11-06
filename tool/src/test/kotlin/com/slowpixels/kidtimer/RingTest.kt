package com.slowpixels.kidtimer

import com.slowpixels.kidtimer.sound.Buzz
import com.slowpixels.kidtimer.sound.Calls
import com.slowpixels.kidtimer.sound.Mix
import com.slowpixels.kidtimer.sound.Ring
import com.slowpixels.kidtimer.sound.Sounds
import com.slowpixels.kidtimer.sound.Tunes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** How a ring is put together, since the kit's voice can only play a buffer through. */
class RingTest {
    private val rate = Mix.RATE
    private val loop = ShortArray(5 * rate) { 10_000 }
    private val ring = Ring.of(loop)

    @Test fun aRingIsAtLeastTwentySecondsOfWholeRounds() {
        assertEquals(ring.first.size, ring.rest.size)
        assertTrue(ring.rest.size >= Ring.MIN_SECONDS * rate)
        // A sound that doesn't divide twenty seconds still plays whole rounds.
        val odd = Ring.of(ShortArray(7 * rate) { 1 })
        assertEquals(3 * 7 * rate, odd.rest.count { it.toInt() == 1 })
    }

    /** The next buffer starts inside the quiet at the end, so the voice never lets go of the sound in between. */
    @Test fun theNextBufferStartsBeforeTheVoiceLetsGoAndAfterTheSoundEnds() {
        val endOfSound = 20_000L
        val endOfBuffer = ring.rest.size * 1000L / rate
        assertTrue("${ring.replayAfterMs} is after the sound", ring.replayAfterMs >= endOfSound)
        assertTrue("${ring.replayAfterMs} is before the end", ring.replayAfterMs < endOfBuffer)
        for (i in 20 * rate until ring.rest.size) assertEquals(0, ring.rest[i].toInt())
    }

    @Test fun onlyTheFirstBufferStartsSofter() {
        assertEquals(10_000, ring.rest[0].toInt())
        assertEquals(4_500, ring.first[0].toInt())
        val mid = Ring.FADE_MS * rate / 1000 / 2
        assertEquals(7_250.0, ring.first[mid].toDouble(), 2.0)
        assertEquals(10_000, ring.first[Ring.FADE_MS * rate / 1000].toInt())
        for (i in 1 until Ring.FADE_MS * rate / 1000) assertTrue(ring.first[i] >= ring.first[i - 1])
    }

    /** Each buffer gets its own buzz, so it has to last until the next one starts. */
    @Test fun theBuzzLastsAsLongAsTheBufferAndEndsInARest() {
        val buzz = Buzz.lasting(ring.replayAfterMs)
        assertTrue(buzz.duration.inWholeMilliseconds >= ring.replayAfterMs)
        assertTrue(buzz.duration.inWholeMilliseconds < ring.replayAfterMs + 2_100)
        assertEquals(buzz.durations.size, buzz.amplitudes.size)
        assertEquals(0, buzz.amplitudes.last().value)
        assertEquals(listOf(255, 0, 255, 0, 255, 0), buzz.amplitudes.take(6).map { it.value })
    }

    @Test fun aCharacterCallsThenBreathesThenTheTunePlays() {
        val call = Sounds.pcm(Sounds.CALL + "cat")
        val tune = Sounds.pcm("cuckoo")
        val both = Sounds.pcm(Sounds.ringId("cuckoo", "cat", voice = true))
        assertEquals(call.toList(), both.take(call.size))
        assertEquals(tune.toList(), both.takeLast(tune.size))
        val breath = both.size - call.size - tune.size
        assertEquals(0.35, breath.toDouble() / rate, 0.001)
        for (i in call.size until call.size + breath) assertEquals(0, both[i].toInt())
    }

    @Test fun withCharacterSoundsOffItIsJustTheTune() {
        assertEquals("farm", Sounds.ringId("farm", "bear", voice = false))
        assertEquals("farm+bear", Sounds.ringId("farm", "bear", voice = true))
    }

    /** A timer saved by an older version may name a sound that's gone. */
    @Test fun anUnknownSoundOrCharacterStillRings() = assertEquals("jungle+smiley", Sounds.ringId("gone", "gone", voice = true))

    @Test fun everyRingFitsInMemoryAndStaysUnderClipping() {
        for (t in Tunes.all) for (c in Calls.all) {
            val pcm = Sounds.pcm(Sounds.ringId(t.id, c.id, voice = true))
            val r = Ring.of(pcm)
            assertTrue("${t.id}+${c.id} is ${r.rest.size / rate} s", r.rest.size < 40 * rate)
            assertTrue(r.first.indices.all { abs(r.first[it].toInt()) <= abs(r.rest[it].toInt()) })
        }
    }
}
