package com.slowpixels.kidtimer.model

import java.io.File
import java.util.Properties

/** What's kept between uses. */
data class Saved(val prefs: Prefs = Prefs(), val timers: List<Timer> = emptyList(), val nextId: Int = 1)

/** One small file in the tool's own storage. It's written whole and then swapped in, so a kill mid-write loses nothing. */
class Store(private val file: File) {
    fun load(): Saved {
        val p = Properties()
        runCatching { file.inputStream().use(p::load) }
        val d = Prefs()
        val prefs = Prefs(
            seconds = (p.getProperty("seconds")?.toIntOrNull() ?: d.seconds).coerceIn(1, Lengths.MAX_SECONDS),
            sound = p.getProperty("sound") ?: d.sound,
            picture = p.getProperty("picture") ?: d.picture,
            voices = p.getProperty("voices")?.toBooleanStrictOrNull() ?: d.voices,
        )
        val timers = p.stringPropertyNames().filter { it.startsWith(TIMER) }.mapNotNull { key -> timer(p.getProperty(key)) }
        val next = maxOf(p.getProperty("nextId")?.toIntOrNull() ?: 1, (timers.maxOfOrNull { it.id } ?: 0) + 1)
        return Saved(prefs, timers, next)
    }

    fun save(s: Saved) {
        val p = Properties()
        p.setProperty("seconds", s.prefs.seconds.toString())
        p.setProperty("sound", s.prefs.sound)
        p.setProperty("picture", s.prefs.picture)
        p.setProperty("voices", s.prefs.voices.toString())
        p.setProperty("nextId", s.nextId.toString())
        for (t in s.timers) p.setProperty(TIMER + t.id, line(t))
        runCatching {
            val part = File(file.path + ".part")
            part.outputStream().use { p.store(it, null) }
            if (!part.renameTo(file)) part.delete()
        }
    }

    private fun line(t: Timer) = listOf(t.id, t.totalMs, t.endsAt, t.sound, t.picture, t.voice, t.name).joinToString("|")

    private fun timer(line: String?): Timer? {
        val f = line?.split('|', limit = 7)?.takeIf { it.size == 7 } ?: return null
        val total = f[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
        return Timer(f[0].toIntOrNull() ?: return null, f[6], total, f[2].toLongOrNull() ?: return null, f[3], f[4], f[5].toBoolean())
    }

    private companion object { const val TIMER = "timer." }
}
