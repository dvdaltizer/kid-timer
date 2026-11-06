package com.slowpixels.kidtimer.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the clock wants done, from [Board.tick]. */
data class Tick(
    /** A ring has just begun: play this timer's sound. */
    val sound: Timer? = null,
    /** Nobody stopped the ring in time, so it's over and the sound stops. */
    val silence: Boolean = false,
    /** When to ask again, or null when nothing is running. */
    val nextInMs: Long? = null,
)

/**
 * All the timers and what's happening to them, in plain Kotlin with the time passed in, so the tests can run it. [Hub]
 * calls [tick] with the clock and does what it says.
 *
 * Several timers can run at once. If one ends while another rings, the one sound goes on and Stop ends them both, as on
 * the Kompakt. A timer that ended more than [LATE_MS] ago is one the tool couldn't ring for (it was stopped, or the phone
 * was off): it never rings out of nowhere, and stays listed as ended until a grown-up clears it.
 *
 * [kid] is the timer on the kid screen. It's kept here, not on the screen, because the screen doesn't last: a back swipe
 * takes it down and the tool can't refuse, so it has to be able to come straight back (see `HomeScreen`).
 */
class Board(private val store: Store) {
    private var saved = store.load().let { it.copy(timers = sorted(it.timers)) }
    private val _prefs = MutableStateFlow(saved.prefs)
    private val _timers = MutableStateFlow(saved.timers)
    private val _ringing = MutableStateFlow<List<Int>>(emptyList())
    private val _kid = MutableStateFlow<Int?>(null)
    private var ringStarted = 0L

    val prefs: StateFlow<Prefs> = _prefs
    /** The timers running, ringing or ended, the soonest to end first. */
    val timers: StateFlow<List<Timer>> = _timers
    /** The timers ringing now, by id, the latest to end last. */
    val ringing: StateFlow<List<Int>> = _ringing
    val kid: StateFlow<Int?> = _kid

    /** Whether a timer is still counting or ringing, so the tool has something to stay awake for. */
    fun busy(now: Long) = _ringing.value.isNotEmpty() || _timers.value.any { !it.done(now) }

    fun get(id: Int?): Timer? = _timers.value.firstOrNull { it.id == id }

    fun update(change: (Prefs) -> Prefs) {
        val p = change(_prefs.value)
        _prefs.value = p
        keep(saved.copy(prefs = p))
    }

    /** The name a timer left unnamed gets: "Timer" and the lowest number no timer here has. */
    fun nextName(): String {
        val taken = _timers.value.map { it.name }.toSet()
        return generateSequence(1) { it + 1 }.map { "Timer $it" }.first { it !in taken }
    }

    /** Starts a timer of the chosen length, sound and character, called [name] or, left blank, "Timer 1", "Timer 2"… */
    fun start(name: String, now: Long): Timer {
        val p = _prefs.value
        val t = Timer(saved.nextId, name.trim().ifEmpty { nextName() }, p.seconds * 1000L, now + p.seconds * 1000L, p.sound, p.picture, p.voices)
        setTimers(_timers.value + t, saved.nextId + 1)
        return t
    }

    /** Timer [id] runs [ms] longer: it ends later, and its disc's whole turn grows with it, so the disc stays true. */
    fun extend(id: Int, ms: Long, now: Long) = setTimers(_timers.value.map {
        if (it.id == id && !it.done(now)) it.copy(totalMs = it.totalMs + ms, endsAt = it.endsAt + ms) else it
    })

    /** Timer [id] is gone. If it was ringing, so is every timer that rang with it; the answer says the sound must stop. */
    fun cancel(id: Int): Boolean {
        if (id in _ringing.value) { stop(); return true }
        remove(listOf(id))
        return false
    }

    /** Stop: the timers that were ringing are over. */
    fun stop() {
        val ids = _ringing.value
        _ringing.value = emptyList()
        remove(ids)
    }

    /** Puts timer [id] on the kid screen. */
    fun show(id: Int) { if (get(id) != null) _kid.value = id }

    /** A grown-up left the kid screen. */
    fun leave() { _kid.value = null }

    /** Looks at the clock: rings what's due, silences a ring nobody stopped, and says when to look again. */
    fun tick(now: Long): Tick {
        if (_ringing.value.isNotEmpty() && now - ringStarted >= SILENCE_AFTER_MS) {
            stop()
            return Tick(silence = true, nextInMs = next(now))
        }
        val ring = _ringing.value
        val up = _timers.value.filter { it.done(now) && it.id !in ring && now - it.endsAt <= LATE_MS }
        var sound: Timer? = null
        if (up.isNotEmpty()) {
            if (ring.isEmpty()) { ringStarted = now; sound = up.last() }
            _ringing.value = ring + up.map { it.id }
            _kid.value = up.last().id
        }
        return Tick(sound = sound, nextInMs = next(now))
    }

    private fun next(now: Long): Long? {
        val waiting = _timers.value.firstOrNull { !it.done(now) }
        return when {
            waiting != null -> (waiting.endsAt - now).coerceIn(1, MAX_WAIT_MS)
            _ringing.value.isNotEmpty() -> MAX_WAIT_MS
            else -> null
        }
    }

    private fun remove(ids: Collection<Int>) {
        if (ids.isEmpty()) return
        if (_kid.value in ids) _kid.value = null
        setTimers(_timers.value.filter { it.id !in ids })
    }

    private fun setTimers(list: List<Timer>, nextId: Int = saved.nextId) {
        val s = sorted(list)
        _timers.value = s
        keep(saved.copy(timers = s, nextId = nextId))
    }

    private fun sorted(list: List<Timer>) = list.sortedWith(compareBy({ it.endsAt }, { it.id }))

    private fun keep(s: Saved) { saved = s; store.save(s) }

    companion object {
        const val EXTRA_MS = 30_000L
        /** A ring nobody stops ends by itself after this long. */
        const val SILENCE_AFTER_MS = 5 * 60_000L
        /** A timer found past its end by more than this doesn't ring. */
        const val LATE_MS = 10 * 60_000L
        /** The clock is read at least this often: a wall clock can jump, and a ring must be able to end. */
        const val MAX_WAIT_MS = 1_000L
    }
}
