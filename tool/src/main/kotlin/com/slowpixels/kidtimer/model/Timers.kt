package com.slowpixels.kidtimer.model

/** The grown-up's last choices: the next timer starts with them. */
data class Prefs(
    /** The length last chosen, in seconds (a quick choice is whole minutes; Other can be any minutes and seconds). */
    val seconds: Int = 600,
    /** The end sound's id (see `sound/Tunes.kt`). */
    val sound: String = "jungle",
    /** The character's id (see `model/Pictures.kt`). */
    val picture: String = "smiley",
    /** The character makes its sound (see `sound/Calls.kt`) before the tune. */
    val voices: Boolean = true,
)

/**
 * A timer running or ringing: its [name], its whole length and when it ends (ms since 1970, so it survives the tool being
 * closed), with the sound and character it was started with, and whether the character makes its sound.
 */
data class Timer(val id: Int, val name: String, val totalMs: Long, val endsAt: Long, val sound: String, val picture: String,
                 val voice: Boolean = true) {
    fun left(now: Long) = (endsAt - now).coerceAtLeast(0)
    fun done(now: Long) = now >= endsAt
    /** The kid screen's disc: a whole turn is the whole time, so it starts full. */
    val face get() = Dial(totalMs)
}

/** Lengths as they're chosen, typed and said. */
object Lengths {
    /** The quick choices, in minutes. */
    val QUICK = listOf(1, 2, 5, 10, 15, 20, 30, 60)
    /** Three hours is the longest a timer can be typed. */
    const val MAX_SECONDS = 180 * 60

    /** The length [digits] typed from the right stand for, in seconds: the last two are seconds, the rest minutes (2 3 0 is 2:30). */
    fun seconds(digits: String): Int {
        val d = digits.padStart(3, '0')
        return d.dropLast(2).toInt() * 60 + d.takeLast(2).toInt()
    }

    /** The digits typed so far with one more [digit], or null if that's past three hours (a leading 0 is dropped). */
    fun typed(digits: String, digit: Int): String? {
        val v = (digits + digit).trimStart('0')
        return if (v.isNotEmpty() && v.length <= 5 && seconds(v) <= MAX_SECONDS) v else null
    }

    /** A length in words, as short as it can be said: "10 min", "45 sec", "2:30". */
    fun words(seconds: Int): String = when {
        seconds % 60 == 0 -> "${seconds / 60} min"
        seconds < 60 -> "$seconds sec"
        else -> "%d:%02d".format(seconds / 60, seconds % 60)
    }

    /** What's left in words a child hears: whole minutes rounded up ("7 min"), and under a minute the seconds ("45 sec"). */
    fun leftWords(ms: Long): String = if (ms >= Dial.MINUTE) "${Dial.minutesLeft(ms)} min" else "${(ms.coerceAtLeast(0) + 999) / 1000} sec"

    /** A time left as a clock counts it, rounded up to the second: 9:05, or 1:02:00 past an hour. */
    fun countdown(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        val h = s / 3600
        val m = s % 3600 / 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s % 60) else "%d:%02d".format(m, s % 60)
    }
}
