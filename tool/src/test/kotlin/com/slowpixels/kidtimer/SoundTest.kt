package com.slowpixels.kidtimer

import com.slowpixels.kidtimer.sound.Calls
import com.slowpixels.kidtimer.sound.Mix
import com.slowpixels.kidtimer.sound.Tunes
import com.slowpixels.kidtimer.sound.Voice
import com.slowpixels.kidtimer.sound.score
import com.slowpixels.kidtimer.model.Pictures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.sqrt

/** The end sounds render, sound alike in loudness, loop without a click, and land in build/sounds as WAVs to hear. */
class SoundTest {
    @Test fun notesHaveTheirPitches() {
        assertEquals(440.0, Voice.hz("A4"), 1e-9)
        assertEquals(261.63, Voice.hz("C4"), 0.01)
        assertEquals(Voice.hz("F#5"), Voice.hz("Gb5"), 1e-9)
        assertEquals(233.08, Voice.hz("Bb3"), 0.01)
    }

    @Test fun scoresCountHeldNotesRestsAndChords() {
        val notes = score("C5 E5:2 - | G5+C6 -:3 A5", 1.0, 0.5)
        assertEquals(listOf(1.0, 1.5, 3.0, 3.0, 5.0), notes.map { it.t })
        assertEquals(1.0, notes[1].dur, 1e-9)
    }

    @Test fun everySoundRendersAsAQuietSeamlessLoop() {
        val dir = File("build/sounds").apply { mkdirs() }
        for (tune in Tunes.all) {
            val started = System.nanoTime()
            val pcm = tune.make().render()
            val ms = (System.nanoTime() - started) / 1_000_000
            val seconds = pcm.size.toDouble() / Mix.RATE
            val rms = sqrt(pcm.sumOf { (it / 32768.0) * (it / 32768.0) } / pcm.size)
            val peak = pcm.maxOf { abs(it.toInt()) } / 32768.0
            // Where the loop goes round, the step is no bigger than the biggest inside it (a note starting).
            val seam = abs(pcm.last() - pcm.first())
            val usual = (1 until pcm.size).maxOf { abs(pcm[it] - pcm[it - 1]) }
            println("${tune.id}: %.2f s, rms %.3f, peak %.2f, seam %d (biggest step %d), %d ms".format(seconds, rms, peak, seam, usual, ms))
            assertTrue("${tune.id} length", seconds in 3.5..12.0)
            assertTrue("${tune.id} loudness $rms", rms in 0.08..0.2)
            assertTrue("${tune.id} peak $peak", peak < 0.98)
            assertTrue("${tune.id} seam $seam", seam <= usual)
            wav(dir.resolve("${tune.id}.wav"), pcm)
        }
    }

    /** Every character has its call: short, from silence, as loud as the tunes at most, never clipped. */
    @Test fun everyCharacterHasACall() {
        val dir = File("build/sounds").apply { mkdirs() }
        assertEquals(Pictures.all.map { it.id }, Calls.all.map { it.id })
        for (call in Calls.all) {
            val pcm = call.make().render()
            val seconds = pcm.size.toDouble() / Mix.RATE
            val rms = sqrt(pcm.sumOf { (it / 32768.0) * (it / 32768.0) } / pcm.size)
            val peak = pcm.maxOf { abs(it.toInt()) } / 32768.0
            // Nothing from its end rings on into its start (the mix wraps what runs past its length).
            val start = (0 until 40).maxOf { abs(pcm[it].toInt()) } / 32768.0
            println("${call.id}: %.2f s, rms %.3f, peak %.2f, start %.3f".format(seconds, rms, peak, start))
            assertTrue("${call.id} length $seconds", seconds in 0.6..2.6)
            assertTrue("${call.id} loudness $rms", rms in 0.05..0.2)
            assertTrue("${call.id} peak $peak", peak < 0.98)
            assertTrue("${call.id} start $start", start < 0.02)
            wav(dir.resolve("call-${call.id}.wav"), pcm)
        }
    }

    @Test fun soundsRenderTheSameEveryTime() {
        val a = Tunes.byId("farm").make().render()
        val b = Tunes.byId("farm").make().render()
        assertTrue(a.contentEquals(b))
    }

    @Test fun unknownSoundFallsBackToTheFirst() = assertEquals("jungle", Tunes.byId("gone").id)

    private fun wav(file: File, pcm: ShortArray) {
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            fun le32(v: Int) = f.write(byteArrayOf(v.toByte(), (v shr 8).toByte(), (v shr 16).toByte(), (v shr 24).toByte()))
            fun le16(v: Int) = f.write(byteArrayOf(v.toByte(), (v shr 8).toByte()))
            f.writeBytes("RIFF"); le32(36 + pcm.size * 2); f.writeBytes("WAVEfmt ")
            le32(16); le16(1); le16(1); le32(Mix.RATE); le32(Mix.RATE * 2); le16(2); le16(16)
            f.writeBytes("data"); le32(pcm.size * 2)
            val bytes = ByteArray(pcm.size * 2)
            for (i in pcm.indices) { bytes[2 * i] = pcm[i].toInt().toByte(); bytes[2 * i + 1] = (pcm[i].toInt() shr 8).toByte() }
            f.write(bytes)
        }
    }
}
