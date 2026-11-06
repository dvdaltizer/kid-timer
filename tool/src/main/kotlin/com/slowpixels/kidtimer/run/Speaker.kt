package com.slowpixels.kidtimer.run

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.slowpixels.kidtimer.sound.Mix
import com.thelightphone.sdk.audio.LightAudioVoice

/** Where a sound comes out. */
interface Speaker {
    fun play(pcm: ShortArray)
    fun stop()
}

/**
 * The kit's voice, at alarm level. It holds the phone's sound only while it plays, so a podcast pauses for the ring and
 * comes back after, and it stays quiet during a phone call.
 */
class VoiceSpeaker(private val voice: LightAudioVoice) : Speaker {
    override fun play(pcm: ShortArray) = voice.play(pcm)
    override fun stop() = voice.stop()
}

/**
 * For a ring in a process no screen has opened yet: the phone restarted the tool for its background job, and only a
 * screen can make a voice. It plays over other sound rather than pausing it.
 */
class TrackSpeaker : Speaker {
    private var track: AudioTrack? = null

    override fun play(pcm: ShortArray) {
        stop()
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(Mix.RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also { it.write(pcm, 0, pcm.size); it.play() }
    }

    override fun stop() {
        track?.let { runCatching { it.stop() }; it.release() }
        track = null
    }
}
