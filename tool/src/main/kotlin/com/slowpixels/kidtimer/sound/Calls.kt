package com.slowpixels.kidtimer.sound

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

/** A character's sound for the end of a timer, played before its tune (see [Sounds.ring]). */
class Call(val id: String, val make: () -> Mix)

/**
 * The characters' sounds, one for each picture (`model/Pictures.kt`), all synthesised here like the tunes: voices from a
 * buzz through moving resonances, and noise through moving filters. Nothing is recorded, and none copies a sound from
 * a film or game (the princess's "how do you do" is a voice made for the app).
 */
object Calls {
    val all = listOf(
        Call("smiley", ::hey),
        Call("bear", ::bear),
        Call("dinosaur", ::dinosaur),
        Call("koala", ::koala),
        Call("cat", ::meow),
        Call("dog", ::woof),
        Call("butterfly", ::flutter),
        Call("shark", ::splash),
        Call("rocket", ::launch),
        Call("train", ::chooChoo),
        Call("racecar", ::vroom),
        Call("princess", ::howDoYouDo),
    )

    fun byId(id: String?): Call = all.firstOrNull { it.id == id } ?: all.first()

    /** Vowels as (F1, F2, F3) for a small, bright voice: a child's or a cartoon's, about a fifth above a grown-up's. */
    private val vowels = mapOf(
        "a" to Triple(1000.0, 1550.0, 3300.0), "e" to Triple(620.0, 2350.0, 3300.0), "i" to Triple(380.0, 2900.0, 3700.0),
        "o" to Triple(560.0, 1000.0, 3000.0), "u" to Triple(400.0, 1050.0, 2900.0),
    )

    /** Formants gliding from vowel [from] to [to] over a syllable, with [x] its progress from 0 to 1. */
    private fun glide(from: String, to: String, x: Double, at: Double = 0.35): List<Triple<Double, Double, Double>> {
        val a = vowels.getValue(from)
        val b = vowels.getValue(to)
        val k = ((x - at) / (1 - at)).coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
        fun mix(p: Double, q: Double) = p + (q - p) * k
        return listOf(Triple(mix(a.first, b.first), 110.0, 1.0), Triple(mix(a.second, b.second), 150.0, 0.55),
            Triple(mix(a.third, b.third), 250.0, 0.18))
    }

    /** A breath of noise through a moving band (an "h", a splash, steam, a rocket's roar). */
    private fun hiss(m: Mix, t: Double, dur: Double, gain: Double, center: (Double) -> Double, width: (Double) -> Double,
                     envelope: (Double) -> Double, seed: Long) {
        val rate = m.rate
        val start = m.index(t)
        val n = (dur * rate).toInt()
        val band = BandPass(rate)
        val noise = Noise(seed)
        for (i in 0 until n) {
            val x = i.toDouble() / n
            if (i % 32 == 0) band.tune(center(x), width(x))
            m.add(start + i, band.step(noise.next()) * envelope(x) * gain)
        }
    }

    /** A rough, wobbling pitch for growls and roars: [f] Hz, shaken by two fast wobbles. */
    private fun rough(f: Double, s: Double, depth: Double) = f * (1 + depth * (0.6 * sin(2 * PI * 31 * s) + 0.4 * sin(2 * PI * 47 * s + 1)))

    /** Smiley: a cheerful, bright "hey!", the pitch jumping up. */
    fun hey(): Mix {
        val m = Mix(0.75)
        hiss(m, 0.02, 0.09, 0.5, { 1800.0 }, { 1600.0 }, { Voice.shape(it, 0.3, 0.5) }, seed = 3)
        Voice.voice(m, 0.08, 0.42, 1.0, pitch = { x -> 330 + 190 * sin(PI * minOf(1.0, x * 1.6)) - 40 * x },
            formants = { x -> glide("e", "i", x, at = 0.45) }, envelope = { Voice.shape(it, 0.08, 0.4) }, breath = 0.08, seed = 5)
        return m
    }

    /** Bear: a friendly growl swelling to a roar and rumbling away. */
    fun bear(): Mix {
        val dur = 1.15
        val m = Mix(dur + 0.2)
        Voice.voice(m, 0.03, dur, 1.0, pitch = { x -> rough(150 - 40 * x + 25 * sin(PI * x), x * dur, 0.06) },
            formants = { x -> listOf(Triple(620.0 + 120 * sin(PI * x), 160.0, 1.0), Triple(1050.0, 200.0, 0.45), Triple(2400.0, 350.0, 0.12)) },
            envelope = { x -> Voice.shape(x, 0.3, 0.35) * (0.7 + 0.3 * sin(2 * PI * 24 * x * dur)) }, breath = 0.55, seed = 7)
        return m
    }

    /**
     * T-rex: a huge roar. A deep, rough voice (pitch climbing then sinking, its sound grinding at 22 Hz), a higher,
     * trumpeting layer over its first half, and a low rumble of breath under it all.
     */
    fun dinosaur(): Mix {
        val dur = 1.9
        val m = Mix(dur + 0.3)
        fun pitch(x: Double) = when {
            x < 0.25 -> 105 + 50 * (x / 0.25)
            else -> 155 - 85 * ((x - 0.25) / 0.75).pow(0.8)
        }
        Voice.voice(m, 0.03, dur, 1.0, pitch = { x -> rough(pitch(x), x * dur, 0.1) },
            formants = { x -> listOf(Triple(820.0 - 240 * x, 200.0, 1.0), Triple(1350.0 - 400 * x, 260.0, 0.55), Triple(2500.0, 450.0, 0.2)) },
            envelope = { x -> Voice.shape(x, 0.1, 0.45) * (0.65 + 0.35 * sin(2 * PI * 22 * x * dur)) }, breath = 0.9, seed = 11)
        Voice.voice(m, 0.08, dur * 0.6, 0.4, pitch = { x -> rough(pitch(x * 0.6) * 2.6, x * dur, 0.07) },
            formants = { listOf(Triple(1150.0, 250.0, 1.0), Triple(2100.0, 350.0, 0.5)) },
            envelope = { x -> Voice.shape(x, 0.25, 0.6) }, breath = 0.6, seed = 12)
        hiss(m, 0.03, dur, 0.45, { x -> 420.0 - 120 * x }, { 350.0 }, { x -> Voice.shape(x, 0.15, 0.5) }, seed = 13)
        return m
    }

    /** Koala: a sleepy snore in, then three deep, snorty grunts out, as koalas bellow. */
    fun koala(): Mix {
        val m = Mix(1.55)
        hiss(m, 0.02, 0.4, 0.55, { x -> 500.0 + 300 * x }, { 300.0 }, { x -> Voice.shape(x, 0.4, 0.2) * (0.6 + 0.4 * sin(2 * PI * 38 * x * 0.4)) }, seed = 17)
        for ((k, t) in listOf(0.55, 0.82, 1.09).withIndex()) Voice.voice(m, t, 0.2, 1.0 - 0.15 * k,
            pitch = { x -> rough(165 - 30 * x, x * 0.2, 0.05) },
            formants = { listOf(Triple(330.0, 100.0, 1.0), Triple(950.0, 160.0, 0.35), Triple(2300.0, 300.0, 0.08)) },
            envelope = { x -> Voice.shape(x, 0.12, 0.45) }, breath = 0.6, seed = 21L + k)
        return m
    }

    /**
     * Cat: "Meeeoww". A soft "m", a long "eee" that rises a little (with a slight waver), then the mouth opens wide and
     * rounds to "oww" as the pitch falls away.
     */
    fun meow(): Mix {
        val dur = 1.2
        val m = Mix(dur + 0.15)
        fun mix(a: Double, b: Double, k: Double) = a + (b - a) * k.coerceIn(0.0, 1.0)
        Voice.voice(m, 0.03, dur, 1.0,
            pitch = { x ->
                val s = x * dur
                (if (s < 0.6) 600 + 170 * (s / 0.6) else 770 - 360 * ((s - 0.6) / 0.6).pow(1.2)) * (1 + 0.015 * sin(2 * PI * 6 * s))
            },
            formants = { x ->
                val s = x * dur
                val (f1, f2) = when {
                    s < 0.08 -> 300.0 to 1800.0
                    s < 0.6 -> mix(300.0, 470.0, (s - 0.08) / 0.1) to mix(1800.0, 3000.0, (s - 0.08) / 0.1)
                    s < 0.85 -> mix(470.0, 1050.0, (s - 0.6) / 0.25) to mix(3000.0, 1650.0, (s - 0.6) / 0.25)
                    else -> mix(1050.0, 560.0, (s - 0.85) / 0.35) to mix(1650.0, 950.0, (s - 0.85) / 0.35)
                }
                listOf(Triple(f1, 130.0, 1.0), Triple(f2, 180.0, 0.55), Triple(3800.0, 300.0, 0.15))
            },
            envelope = { x -> Voice.shape(x, 0.07, 0.3) * (if (x * dur < 0.08) 0.45 else 1.0) }, breath = 0.15, seed = 23)
        return m
    }

    /**
     * Dog: "bark! bark!". Each bark is short and open-mouthed ("ah"), jumping up in pitch and dropping, harsh and
     * breathy with a rasp, and starts with a crack of breath.
     */
    fun woof(): Mix {
        val m = Mix(0.75)
        for ((k, t) in listOf(0.03, 0.33).withIndex()) {
            val f = 390.0 + 40 * k
            val len = 0.17
            hiss(m, t, 0.04, 0.6, { 2200.0 }, { 2400.0 }, { x -> Voice.shape(x, 0.1, 0.7) }, seed = 31L + k)
            Voice.voice(m, t + 0.01, len, 1.0, pitch = { x -> rough(f * (1 + 0.35 * sin(PI * minOf(1.0, x * 1.8))) - 120 * x, x * len, 0.08) },
                formants = { x -> listOf(Triple(950.0 - 250 * x, 220.0, 1.0), Triple(1550.0 - 300 * x, 260.0, 0.6), Triple(2700.0, 400.0, 0.25)) },
                envelope = { x -> minOf(1.0, x / 0.04) * Voice.shape(x, 0.04, 0.7) * (0.75 + 0.25 * sin(2 * PI * 90 * x * len)) },
                breath = 0.9, seed = 29L + k)
        }
        return m
    }

    /** Butterfly: soft wing beats fluttering past, and a little sparkle of bells. */
    fun flutter(): Mix {
        val dur = 1.1
        val m = Mix(dur + 1.25)
        hiss(m, 0.02, dur, 1.0, { x -> 1400.0 + 700 * x }, { 1800.0 },
            { x -> Voice.shape(x, 0.25, 0.35) * abs(sin(PI * 13 * x * dur)).pow(3) }, seed = 37)
        for ((k, n) in listOf("E6", "G#6", "B6", "E7").withIndex()) Voice.bell(m, 0.55 + k * 0.08, Voice.hz(n), 0.18, decay = 0.25)
        return m
    }

    /** Shark: a big splash, a smaller one, and bubbles popping up. */
    fun splash(): Mix {
        val m = Mix(1.45)
        for ((k, t) in listOf(0.02 to 1.0, 0.5 to 0.55).withIndex()) {
            val (at, g) = t
            hiss(m, at, 0.45, g, { x -> 3200.0 * exp(-2.2 * x) + 500 }, { x -> 2500.0 * exp(-1.5 * x) + 400 },
                { x -> minOf(1.0, x / 0.03) * exp(-4.5 * x) }, seed = 41L + k)
        }
        // Bubbles: quick rising pips, each a little higher.
        for ((k, t) in listOf(0.78, 0.9, 0.98, 1.1, 1.18).withIndex()) Voice.sweep(m, t, 650.0 + 120 * k, 1500.0 + 200 * k, 0.07, 0.35)
        return m
    }

    /** Rocket: three countdown beeps, the engines roaring up, and a whoosh climbing away. */
    fun launch(): Mix {
        val m = Mix(2.5)
        for (k in 0 until 3) Voice.beep(m, 0.02 + k * 0.32, if (k < 2) 880.0 else 1175.0, 0.12, 0.6)
        hiss(m, 0.95, 1.4, 1.0, { x -> 300.0 + 2600 * x * x }, { x -> 500.0 + 1500 * x }, { x -> Voice.shape(x, 0.25, 0.35) }, seed = 43)
        Voice.sweep(m, 1.05, 180.0, 1100.0, 1.2, 0.35, harmonic = 0.3)
        return m
    }

    /** Train: chuff-chuff-chuff-chuff, then a two-note steam whistle, "choo-choo!". */
    fun chooChoo(): Mix {
        val m = Mix(2.0)
        for ((k, t) in listOf(0.02, 0.22, 0.4, 0.56).withIndex())
            hiss(m, t, 0.13, 0.75, { 1700.0 }, { 1600.0 }, { x -> minOf(1.0, x / 0.08) * exp(-5 * x) }, seed = 47L + k)
        // A steam whistle: three pipes sounding a chord, a little breathy, a short toot and a long one.
        for ((k, t) in listOf(0.8, 1.2).withIndex()) for ((j, f) in listOf(523.0, 659.0, 784.0).withIndex())
            Voice.whistle(m, t, f, if (k == 0) 0.3 else 0.5, 0.38, seed = 53L + 3 * k + j)
        return m
    }

    /** Race car: an engine revving up, a quick change of gear, revving higher, and roaring off. */
    fun vroom(): Mix {
        val dur = 1.6
        val m = Mix(dur + 0.15)
        Voice.voice(m, 0.02, dur, 1.0,
            pitch = { x ->
                val s = x * dur
                when {
                    s < 0.7 -> 95 + 190 * (s / 0.7).pow(0.8)
                    s < 0.8 -> 285 - 110 * ((s - 0.7) / 0.1)
                    else -> 175 + 170 * ((s - 0.8) / 0.8).pow(0.7)
                } * (1 + 0.02 * sin(2 * PI * 9 * s))
            },
            formants = { listOf(Triple(500.0, 400.0, 1.0), Triple(1300.0, 600.0, 0.6), Triple(2700.0, 900.0, 0.25)) },
            envelope = { x -> Voice.shape(x, 0.06, 0.25) * (0.85 + 0.15 * x) }, breath = 0.35, seed = 59)
        return m
    }

    private class Syllable(val at: Double, val len: Double, val onset: Char?, val from: String, val to: String, val p0: Double, val p1: Double)

    /** Princess: a cute, high, sing-song "how do you do?", rising at the end. */
    fun howDoYouDo(): Mix {
        val m = Mix(1.45)
        val syllables = listOf(
            Syllable(0.04, 0.27, 'h', "a", "u", 620.0, 700.0),
            Syllable(0.36, 0.17, 'd', "u", "u", 760.0, 720.0),
            Syllable(0.57, 0.2, 'y', "i", "u", 660.0, 620.0),
            Syllable(0.81, 0.42, 'd', "u", "u", 700.0, 880.0),
        )
        for ((k, s) in syllables.withIndex()) {
            when (s.onset) {
                'h' -> hiss(m, s.at - 0.04, 0.07, 0.4, { 1600.0 }, { 1500.0 }, { Voice.shape(it, 0.3, 0.5) }, seed = 61)
                'd' -> Voice.click(m, s.at, 0.25, 0.004, seed = 63L + k)
            }
            val last = k == syllables.lastIndex
            Voice.voice(m, s.at, s.len, 0.9,
                pitch = { x -> (s.p0 + (s.p1 - s.p0) * x) * (1 + if (last) 0.025 * sin(2 * PI * 6 * x * s.len) else 0.0) },
                formants = { x -> glide(s.from, s.to, x, at = if (s.onset == 'y') 0.2 else 0.35) },
                envelope = { x -> Voice.shape(x, 0.15, 0.3) }, breath = 0.06, seed = 67L + k)
        }
        return m
    }
}
