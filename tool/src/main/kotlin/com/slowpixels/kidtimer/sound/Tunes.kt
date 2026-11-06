package com.slowpixels.kidtimer.sound

import kotlin.math.PI
import kotlin.math.sin

/** A note in a tune: when it starts (s), its pitch (Hz), and how long it's held (s). */
data class Note(val t: Double, val f: Double, val dur: Double)

/**
 * A line of music written as text, one token per [unit] seconds from [start]: "E5" is a note, "E5:2" one held for two
 * units, "C5+E5" a chord, "-" (or "-:3") a rest. "|" marks a bar and takes no time.
 */
fun score(text: String, start: Double, unit: Double): List<Note> {
    val out = mutableListOf<Note>()
    var at = 0
    for (token in text.split(' ').filter { it.isNotBlank() && it != "|" }) {
        val (body, len) = token.split(':').let { it[0] to (it.getOrNull(1)?.toInt() ?: 1) }
        if (body != "-") for (name in body.split('+')) out += Note(start + at * unit, Voice.hz(name), len * unit)
        at += len
    }
    return out
}

/** One of the end sounds: its id (kept in settings), its name in the list, and how to make it. */
class Tune(val id: String, val name: String, val make: () -> Mix)

/**
 * The end sounds. Every melody here was written for this app, and every sound is synthesised by [Voice]: the
 * animals too (a buzz shaped by mouth-like resonances, the way a voice is made), so nothing is recorded or borrowed.
 * Each is one loop of a few seconds that plays round until Stop.
 */
object Tunes {
    val all = listOf(
        Tune("jungle", "Jungle", ::jungle),
        Tune("farm", "Farm", ::farm),
        Tune("xylophone", "Xylophone", ::xylophone),
        Tune("musicbox", "Music box", ::musicBox),
        Tune("cuckoo", "Cuckoo clock", ::cuckoo),
        Tune("alarm", "Toy alarm", ::toyAlarm),
    )

    fun byId(id: String?): Tune = all.firstOrNull { it.id == id } ?: all.first()

    /**
     * Jungle: hand drums and a shaker under a marimba tune in C pentatonic, birds chirping in the gaps, and a monkey's
     * "ooh ooh aah aah" to end the loop. 104 beats a minute, four bars, 9.2 s.
     */
    fun jungle(): Mix {
        val e = 60.0 / 104 / 2
        val m = Mix(32 * e)
        for (n in score("E5 G5 A5 G5 E5 - G5 - | A5 C6 A5 G5 E5:2 - - | D5 E5 G5 E5 D5 C5 D5 - | E5 D5 C5:2 - - - -", 0.0, e))
            Voice.marimba(m, n.t, n.f, 0.9)
        for (n in score("C4:4 G3:4 | F3:4 C4:4 | G3:4 D4:4 | C4:8", 0.0, e)) Voice.marimba(m, n.t, n.f, 0.75)
        // Low conga and high bongo, the same groove each bar, quieter under the monkey.
        val groove = listOf(0 to 180.0, 2 to 360.0, 3 to 360.0, 5 to 180.0, 6 to 360.0)
        for (bar in 0 until 4) for ((i, f) in groove) {
            val quiet = if (bar == 3 && i >= 4) 0.5 else 1.0
            val low = f < 200
            Voice.drum(m, (bar * 8 + i) * e, f, (if (low) 0.75 else 0.5) * quiet, decay = if (low) 0.2 else 0.11, seed = bar * 8L + i)
        }
        for (i in 0 until 32) Voice.click(m, i * e, if (i % 2 == 1) 0.1 else 0.05, 0.025, seed = 100L + i)
        // Birds: three quick chirps up, then a falling trill.
        for (k in 0 until 3) Voice.sweep(m, 14 * e + k * 0.11, 2600.0, 3900.0, 0.07, 0.5)
        Voice.sweep(m, 23 * e, 4200.0, 2900.0, 0.35, 0.4, trill = 250.0, trillRate = 32.0)
        monkey(m, 28 * e + 0.05)
        return m
    }

    /** "Ooh ooh aah aah", each call sliding up, the "aah"s higher and more open. */
    private fun monkey(m: Mix, t: Double) {
        val calls = listOf(Triple(0.0, 380.0, false), Triple(0.25, 420.0, false), Triple(0.5, 520.0, true), Triple(0.78, 580.0, true))
        for ((dt, f, open) in calls) {
            val (f1, f2) = if (open) 850.0 to 1350.0 else 420.0 to 900.0
            Voice.voice(m, t + dt, 0.2, 0.55, pitch = { x -> f * (0.85 + 0.35 * x) },
                formants = { x -> listOf(Triple(f1 * (0.9 + 0.2 * x), 130.0, 1.0), Triple(f2, 170.0, 0.6), Triple(2800.0, 300.0, 0.15)) },
                envelope = { Voice.shape(it, 0.15, 0.35) }, breath = 0.15, seed = (dt * 100).toLong())
        }
    }

    /**
     * Farm: a banjo picking a tune in G over a boom-chick bass, with a duck's "quack quack", a sheep's "baa" and, to
     * close the loop, a cow's "moo". 126 beats a minute, five bars, 9.5 s.
     */
    fun farm(): Mix {
        val e = 60.0 / 126 / 2
        val m = Mix(40 * e)
        var seed = 1L
        for (n in score("G4 B4 D5 B4 G4 B4 D5:2 | E5 D5 B4 A4 G4:2 - - | C5 E5 D5 B4 A4 B4 G4:2 | A4 B4 C5 A4 D5:2 - -", 0.0, e))
            Voice.pluck(m, n.t, n.f, 0.9, length = 1.0, bright = 0.75, seed = seed++)
        // Boom (bass) on 1 and 3, chick (the chord, softer) on 2 and 4.
        val bars = listOf(
            Triple("G2", "D3", "G3+B3+D4"), Triple("G2", "D3", "G3+B3+D4"),
            Triple("C3", "G2", "C4+E4+G4"), Triple("D3", "A2", "D4+F#4+A4"),
        )
        bars.forEachIndexed { bar, (one, three, chord) ->
            val b = bar * 8 * e
            Voice.pluck(m, b, Voice.hz(one) * 2, 0.7, length = 0.8, bright = 0.45, seed = seed++)
            Voice.pluck(m, b + 4 * e, Voice.hz(three) * 2, 0.7, length = 0.8, bright = 0.45, seed = seed++)
            for (beat in listOf(2, 6)) chord.split('+').forEachIndexed { k, name ->
                Voice.pluck(m, b + beat * e + k * 0.012, Voice.hz(name), 0.3, length = 0.35, bright = 0.5, seed = seed++)
            }
        }
        quack(m, 14 * e)
        quack(m, 15 * e + 0.02)
        baa(m, 30 * e)
        // The last bar: a strum of G, then the cow.
        "G3+B3+D4+G4".split('+').forEachIndexed { k, name -> Voice.pluck(m, 33 * e + k * 0.02, Voice.hz(name), 0.55, length = 1.2, seed = seed++) }
        moo(m, 34.5 * e)
        return m
    }

    private fun quack(m: Mix, t: Double) = Voice.voice(m, t, 0.17, 0.8, pitch = { x -> 300 - 90 * x },
        formants = { x -> listOf(Triple(1050.0 - 250 * x, 220.0, 1.0), Triple(1700.0, 260.0, 0.8), Triple(2700.0, 350.0, 0.4)) },
        envelope = { Voice.shape(it, 0.08, 0.5) }, breath = 0.3, seed = (t * 1000).toLong())

    private fun baa(m: Mix, t: Double) {
        val dur = 0.6
        fun bleat(x: Double) = sin(2 * PI * 7.5 * x * dur)
        Voice.voice(m, t, dur, 0.7, pitch = { x -> 340 * (1 + 0.05 * bleat(x)) * (1 - 0.08 * x) },
            formants = { x -> listOf(Triple(650.0 + 100 * x, 130.0, 1.0), Triple(1700.0 - 450 * x, 180.0, 0.6), Triple(2600.0, 300.0, 0.25)) },
            envelope = { x -> Voice.shape(x, 0.08, 0.3) * (0.75 + 0.25 * bleat(x)) }, breath = 0.2, seed = 41)
    }

    /** "Mmm-ooo": the mouth opens over the first third, the pitch climbs and falls. */
    private fun moo(m: Mix, t: Double) = Voice.voice(m, t, 1.25, 1.1,
        pitch = { x -> 105 + 30 * sin(PI * minOf(1.0, x * 1.3)) - 15 * x },
        formants = { x ->
            val open = minOf(1.0, x / 0.35)
            listOf(Triple(260.0 + 90 * open, 90.0, 1.0), Triple(650.0 + 200 * open, 120.0, 0.25 + 0.45 * open), Triple(2300.0, 250.0, 0.06 + 0.1 * open))
        },
        envelope = { Voice.shape(it, 0.12, 0.3) }, breath = 0.05, seed = 43)

    /** Xylophone: a bouncy tune in D pentatonic over a marimba bass, ending with a run up the bars. 132 beats a minute, 7.3 s. */
    fun xylophone(): Mix {
        val e = 60.0 / 132 / 2
        val m = Mix(32 * e)
        for (n in score("D5 F#5 A5 F#5 B5 A5 F#5 - | E5 F#5 A5 B5 A5:2 - - | B5 A5 F#5 A5 D6 B5 A5 - | F#5 E5 D5:2 - - - -", 0.0, e))
            Voice.xylophone(m, n.t, n.f, 0.85)
        for (n in score("D6 E6 F#6 A6 B6 D7 E7 F#7", 28 * e, e / 2)) Voice.xylophone(m, n.t, n.f, 0.4)
        for (n in score("D4:4 A3:4 | G3:4 D4:4 | A3:4 E4:4 | D4:4 -:4", 0.0, e)) Voice.marimba(m, n.t, n.f, 0.65)
        return m
    }

    /** Music box: a slow waltz in F, high and clear, with the comb's low notes turning under it. 96 beats a minute, 11.3 s. */
    fun musicBox(): Mix {
        val e = 60.0 / 96 / 2
        val m = Mix(36 * e)
        for (n in score("A5:2 C6 F6:2 C6 | D6:2 C6 A5:3 | Bb5:2 A5 G5:2 A5 | C6:4 - - | A5:2 G5 F5:2 G5 | F5:6", 0.0, e))
            Voice.tine(m, n.t, n.f, 0.9)
        for (n in score("F4:2 A4:2 C5:2 | D4:2 F4:2 A4:2 | Bb3:2 D4:2 F4:2 | C4:2 E4:2 G4:2 | C4:2 G4:2 Bb4:2 | F4:2 A4:2 C5:2", 0.0, e))
            Voice.tine(m, n.t, n.f, 0.42)
        return m
    }

    /** Cuckoo clock: tick, tock, tick, tock, then three calls of "cuck-oo", and a little bell. 5 s. */
    fun cuckoo(): Mix {
        val m = Mix(5.0)
        for (i in 0 until 4) Voice.block(m, i * 0.45, if (i % 2 == 0) 1250.0 else 950.0, 0.6)
        for (k in 0 until 3) {
            val t = 1.9 + k * 0.75
            Voice.whistle(m, t, Voice.hz("B5"), 0.2, 0.8, seed = 17L + k)
            Voice.whistle(m, t + 0.25, Voice.hz("G5"), 0.32, 0.8, seed = 29L + k)
        }
        Voice.bell(m, 4.25, Voice.hz("G6"), 0.3, decay = 0.5)
        return m
    }

    /**
     * Toy alarm: an alarm clock's beeps, made friendly: they climb C, E, G, then a slide whistle goes up and two little
     * bells answer. 4 s.
     */
    fun toyAlarm(): Mix {
        val u = 0.14
        val m = Mix(4.0)
        for (n in score("C6 E6 G6 - C6 E6 G6 - E6 G6 C7 C7:2", 0.0, u)) Voice.beep(m, n.t, n.f, n.dur - 0.04, 0.5)
        Voice.sweep(m, 2.0, 700.0, 1500.0, 0.45, 0.5, trill = 15.0, trillRate = 6.0)
        Voice.bell(m, 2.6, Voice.hz("G6"), 0.45, decay = 0.4)
        Voice.bell(m, 2.85, Voice.hz("C7"), 0.45, decay = 0.6)
        return m
    }
}
