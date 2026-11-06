package com.slowpixels.kidtimer.sound

import com.thelightphone.sdk.LightVibrationWaveform
import com.thelightphone.sdk.VibrationAmplitude
import kotlin.time.Duration.Companion.milliseconds

/**
 * The end sounds as samples, made by [Tunes] and [Calls] the first time each is needed and kept in memory while the tool
 * runs. A timer asks for its sound when it starts, so it's ready for the end.
 *
 * An id is a tune's ("jungle"), a character's call ([CALL] and its picture, "call-bear"), or a ring of both
 * ("jungle+bear": the call, a breath, then the tune), which is put together from the other two.
 */
object Sounds {
    const val CALL = "call-"
    /** The breath between a character's call and its tune. */
    private const val GAP_S = 0.35
    private val made = mutableMapOf<String, ShortArray>()

    /** What a timer with [sound] and [picture] plays when it's up: the tune, after the character's call if [voice] is on. */
    fun ringId(sound: String, picture: String, voice: Boolean) =
        if (voice) "${Tunes.byId(sound).id}+${Calls.byId(picture).id}" else Tunes.byId(sound).id

    /**
     * [id]'s samples. Nothing is locked while a sound is made, so asking for one never waits behind another; two threads
     * may make the same one at once, which costs only time.
     */
    fun pcm(id: String): ShortArray {
        synchronized(made) { made[id] }?.let { return it }
        val pcm = if ('+' in id) {
            val (tune, picture) = id.split('+', limit = 2)
            val call = pcm(CALL + picture)
            val loop = pcm(tune)
            ShortArray(call.size + (GAP_S * Mix.RATE).toInt() + loop.size).also {
                call.copyInto(it)
                loop.copyInto(it, it.size - loop.size)
            }
        } else make(id).render()
        synchronized(made) { made[id] = pcm }
        return pcm
    }

    private fun make(id: String): Mix = if (id.startsWith(CALL)) Calls.byId(id.removePrefix(CALL)).make() else Tunes.byId(id).make()
}

/**
 * A ring, ready to play. The kit's voice plays one buffer and then lets go of the phone's sound, so a ring is two long
 * buffers played one after another: [first] starts a little softer and grows, [rest] goes on at full level.
 *
 * Each ends in a moment of quiet, and the next is started inside that quiet ([replayAfterMs]), so the voice never lets go
 * between them: a podcast paused for the ring stays paused until Stop.
 */
class Ring(val first: ShortArray, val rest: ShortArray, val replayAfterMs: Long) {
    companion object {
        /** Each buffer is as many rounds of the sound as last this long. */
        const val MIN_SECONDS = 20
        private const val TAIL_MS = 200
        private const val OVERLAP_MS = 150
        const val FADE_MS = 6_000
        const val START_LEVEL = 0.45

        /** The ring for [loop], one round of a timer's sound. */
        fun of(loop: ShortArray, rate: Int = Mix.RATE): Ring {
            val rounds = (MIN_SECONDS * rate + loop.size - 1) / loop.size
            val tail = TAIL_MS * rate / 1000
            val rest = ShortArray(loop.size * rounds + tail)
            for (r in 0 until rounds) loop.copyInto(rest, r * loop.size)
            val first = rest.copyOf()
            val fade = FADE_MS * rate / 1000
            for (i in 0 until minOf(fade, first.size)) first[i] = (first[i] * (START_LEVEL + (1 - START_LEVEL) * i / fade)).toInt().toShort()
            return Ring(first, rest, rest.size * 1000L / rate - OVERLAP_MS)
        }
    }
}

/** The buzz that goes with a ring, bouncy like the sounds: two short and a longer one, then a rest, round and round. */
object Buzz {
    /** On and off in turn, in ms. */
    private val ROUND = listOf(180L, 140L, 180L, 140L, 500L, 900L)

    /** As many whole rounds as last at least [ms]: the kit plays a waveform through once, so a ring asks for one per buffer. */
    fun lasting(ms: Long): LightVibrationWaveform {
        val rounds = ((ms + ROUND.sum() - 1) / ROUND.sum()).toInt().coerceAtLeast(1)
        val times = List(rounds) { ROUND }.flatten()
        return LightVibrationWaveform(times.map { it.milliseconds }, times.indices.map { VibrationAmplitude(if (it % 2 == 0) 255 else 0) })
    }
}
