package com.slowpixels.kidtimer.run

import com.slowpixels.kidtimer.model.Board
import com.slowpixels.kidtimer.model.Store
import com.slowpixels.kidtimer.model.Timer
import com.slowpixels.kidtimer.sound.Buzz
import com.slowpixels.kidtimer.sound.Mix
import com.slowpixels.kidtimer.sound.Ring
import com.slowpixels.kidtimer.sound.Sounds
import com.thelightphone.sdk.LightJob
import com.thelightphone.sdk.LightJobHandler
import com.thelightphone.sdk.LightJobResult
import com.thelightphone.sdk.LightJobState
import com.thelightphone.sdk.LightVibrationUsage
import com.thelightphone.sdk.LightVibrator
import com.thelightphone.sdk.LightWork
import com.thelightphone.sdk.SealedLightContext
import com.thelightphone.sdk.audio.LightAudio
import com.thelightphone.sdk.audio.LightAudioUsage
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The timers, for the life of the process, so they outlive the screens: LightOS can drop and reopen a screen whenever it
 * likes, and a back swipe can take one down. [Board] decides what happens; this reads the clock and makes the sound.
 *
 * Tools can't set an alarm, so while a timer runs a background job keeps the tool awake (see [keepAwake]) and this clock
 * goes on ticking with the screen off or another tool in front. It takes nothing from anything else: a podcast keeps
 * playing until the ring.
 */
object Hub {
    lateinit var board: Board
        private set
    /** The name typed for the next timer. */
    val name = MutableStateFlow("")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var voice: Speaker? = null
    private var vibrator: LightVibrator? = null
    private var ticker: Job? = null
    private var ringing: Job? = null
    private var ringOut: Speaker? = null
    private var listening: Job? = null
    /** Background jobs running in this process. */
    private val jobs = AtomicInteger()

    private fun now() = System.currentTimeMillis()

    /** Loads what's saved the first time, and starts the clock if there's something to count. */
    private fun open(filesDir: File) {
        if (!::board.isInitialized) board = Board(Store(File(filesDir, "timers.properties")))
        run()
    }

    /** The tool's first screen is up. */
    fun attach(audio: LightAudio, vibrator: LightVibrator, context: SealedLightContext) {
        open(context.filesDir)
        if (voice == null) voice = VoiceSpeaker(audio.newVoice(LightAudioUsage.Alarm, Mix.RATE))
        this.vibrator = vibrator
        if (board.busy(now())) stayAwake(context)
    }

    private fun stayAwake(context: SealedLightContext) {
        if (jobs.get() == 0) LightWork.enqueue(context, FIRST_JOB)
    }

    /** Starts a timer with the choices on the board, and puts it on the kid screen. */
    fun start(context: SealedLightContext) {
        val t = board.start(name.value, now())
        name.value = ""
        board.show(t.id)
        run()
        stayAwake(context)
        scope.launch(Dispatchers.Default) { runCatching { Sounds.pcm(Sounds.ringId(t.sound, t.picture, t.voice)) } }
    }

    fun extend(id: Int) {
        board.extend(id, Board.EXTRA_MS, now())
        run()
    }

    fun cancel(id: Int) { if (board.cancel(id)) quiet() }

    fun stop() {
        board.stop()
        quiet()
    }

    private fun run() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (true) {
                val t = board.tick(now())
                t.sound?.let(::ring)
                if (t.silence) quiet()
                delay(t.nextInMs ?: break)
            }
        }
    }

    private fun ring(t: Timer) {
        stopListening()
        ringing?.cancel()
        ringing = scope.launch {
            val ring = withContext(Dispatchers.Default) { Ring.of(Sounds.pcm(Sounds.ringId(t.sound, t.picture, t.voice))) }
            val out = voice ?: TrackSpeaker()
            val buzz = Buzz.lasting(ring.replayAfterMs)
            ringOut = out
            out.play(ring.first)
            while (true) {
                vibrator?.vibrate(buzz, LightVibrationUsage.Alarm)
                delay(ring.replayAfterMs)
                out.play(ring.rest)
            }
        }
    }

    private fun quiet() {
        ringing?.cancel()
        ringing = null
        ringOut?.stop()
        ringOut = null
        vibrator?.cancel()
    }

    /** Plays sound [id] once, as a grown-up tries it; never over a ring. */
    fun listen(id: String) {
        if (ringing != null) return
        listening?.cancel()
        listening = scope.launch {
            val pcm = withContext(Dispatchers.Default) { Sounds.pcm(id) }
            voice?.play(pcm)
        }
    }

    fun stopListening() {
        listening?.cancel()
        listening = null
        if (ringing == null) voice?.stop()
    }

    /**
     * The background job. While a job runs, Android keeps the processor awake and the tool alive, whatever the screen is
     * doing; this one just waits for the timers to finish. A job may only run for ten minutes, so before that it starts
     * [next] and hands over, staying up until the new one runs so there's no gap to fall asleep in.
     */
    suspend fun keepAwake(context: SealedLightContext, next: String): LightJobResult {
        withContext(Dispatchers.Main) { open(context.filesDir) }
        jobs.incrementAndGet()
        try {
            val until = now() + HANDOVER_MS
            var handing = false
            while (board.busy(now())) {
                if (!handing && now() >= until) {
                    LightWork.enqueue(context, next)
                    handing = true
                }
                if (handing && LightWork.getState(context, next) is LightJobState.Running) break
                delay(1_000)
            }
        } finally {
            jobs.decrementAndGet()
        }
        return LightJobResult.Success(outputFilePath = null, message = null)
    }

    private const val FIRST_JOB = "keep-awake-a"
    private const val HANDOVER_MS = 8 * 60_000L
}

@LightJob("keep-awake-a")
val keepAwakeA: LightJobHandler = { context, _ -> Hub.keepAwake(context, "keep-awake-b") }

@LightJob("keep-awake-b")
val keepAwakeB: LightJobHandler = { context, _ -> Hub.keepAwake(context, "keep-awake-a") }
