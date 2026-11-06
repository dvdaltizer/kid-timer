package com.slowpixels.kidtimer.sound

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * A small synthesiser for the end sounds. Everything is made here from sine waves, noise and filters: no recordings
 * and nothing borrowed. Plain Kotlin (no Android), so the tests can render the sounds to WAV files to listen to.
 *
 * A [Mix] is one loop of sound. Notes are added into it, and whatever rings on past its end carries on at its start,
 * so the loop goes round without a seam.
 */
class Mix(seconds: Double, val rate: Int = RATE) {
    val buf = FloatArray((seconds * rate).roundToInt())
    val seconds get() = buf.size.toDouble() / rate

    fun index(t: Double) = (t * rate).roundToInt()

    fun add(i: Int, v: Double) {
        val n = buf.size
        val j = if (i >= n || i < 0) Math.floorMod(i, n) else i
        buf[j] += v.toFloat()
    }

    /**
     * The finished loop as 16-bit samples: a gentle cut below 120 Hz (a phone's speaker can't play it and only
     * rattles), brought to one loudness ([LOUDNESS] RMS) with soft-topped peaks so no sound is much louder than another.
     */
    fun render(): ShortArray {
        val x = DoubleArray(buf.size) { buf[it].toDouble() }
        // High-pass, run twice round the loop so it starts settled.
        val a = exp(-2 * PI * 120.0 / rate)
        var prevIn = 0.0
        var prevOut = 0.0
        repeat(2) { pass ->
            for (i in x.indices) {
                val inp = buf[i].toDouble()
                val out = a * (prevOut + inp - prevIn)
                prevIn = inp; prevOut = out
                if (pass == 1) x[i] = out
            }
        }
        val rms = sqrt(x.sumOf { it * it } / x.size).coerceAtLeast(1e-9)
        val peak = x.maxOf { abs(it) }.coerceAtLeast(1e-9)
        // A sound with long rests would otherwise be pushed up until its peaks are flattened.
        val gain = minOf(LOUDNESS / rms, 1.4 / peak)
        return ShortArray(x.size) { (soft(x[it] * gain) * 32767).roundToInt().toShort() }
    }

    companion object {
        const val RATE = 44_100
        /** Every sound's loudness, as RMS of full scale (about −15 dBFS). */
        const val LOUDNESS = 0.18
        /** Peaks above 0.6 are rounded off smoothly, and nothing goes past 0.97. */
        fun soft(v: Double): Double {
            val m = abs(v)
            if (m <= 0.6) return v
            val over = 0.6 + 0.37 * tanh((m - 0.6) / 0.37)
            return if (v < 0) -over else over
        }
    }
}

/** A deterministic noise source (xorshift), so each sound renders the same every time. */
class Noise(seed: Long) {
    private var s = seed or 1L
    /** −1 to 1. */
    fun next(): Double {
        s = s xor (s shl 13); s = s xor (s ushr 7); s = s xor (s shl 17)
        return (s ushr 11).toDouble() / (1L shl 53) * 2 - 1
    }
}

/** A two-pole resonant band-pass (the usual biquad), with a peak gain of 1, retunable as it runs. */
class BandPass(private val rate: Int) {
    private var b0 = 0.0; private var b2 = 0.0; private var a1 = 0.0; private var a2 = 0.0
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

    fun tune(f: Double, bandwidth: Double) {
        val w = 2 * PI * f.coerceIn(20.0, rate * 0.45) / rate
        val q = (f / bandwidth).coerceAtLeast(0.3)
        val alpha = sin(w) / (2 * q)
        val a0 = 1 + alpha
        b0 = alpha / a0; b2 = -alpha / a0; a1 = -2 * cos(w) / a0; a2 = (1 - alpha) / a0
    }

    fun step(x: Double): Double {
        val y = b0 * x + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
}

/** One partial of a struck or plucked thing: [ratio] × the note, [amp] loud, dying away with time constant [decay] s. */
data class Partial(val ratio: Double, val amp: Double, val decay: Double)

/** The instruments. Each adds one note into a [Mix] at [t] seconds. */
object Voice {
    /** A note's frequency: "C5", "F#4", "Bb3" (A4 = 440 Hz). */
    fun hz(name: String): Double {
        val letter = "C D EF G A B".indexOf(name[0].uppercaseChar())
        require(letter >= 0) { "not a note: $name" }
        var i = 1
        var semi = letter
        while (i < name.length && (name[i] == '#' || name[i] == 'b')) { semi += if (name[i] == '#') 1 else -1; i++ }
        val octave = name.substring(i).toInt()
        val midi = 12 * (octave + 1) + semi
        return 440.0 * 2.0.pow((midi - 69) / 12.0)
    }

    /**
     * A struck bar, bell or tine: decaying sine partials, each a recursive oscillator (no sin() per sample), with a
     * [attack] ms rise so the start doesn't click.
     */
    fun modal(m: Mix, t: Double, f: Double, gain: Double, partials: List<Partial>, attack: Double = 0.002) {
        val rate = m.rate
        val start = m.index(t)
        val attackN = (attack * rate).coerceAtLeast(1.0)
        for (p in partials) {
            val fp = f * p.ratio
            if (fp >= rate * 0.45) continue
            val w = 2 * PI * fp / rate
            val k = 2 * cos(w)
            val n = (p.decay * ln(1000.0) * rate).toInt()
            val fall = exp(-1.0 / (p.decay * rate))
            var y1 = 0.0
            var y2 = -sin(w)
            var env = p.amp * gain
            for (i in 0 until n) {
                val y = k * y1 - y2
                y2 = y1; y1 = y
                val rise = if (i < attackN) i / attackN else 1.0
                m.add(start + i, y * env * rise)
                env *= fall
            }
        }
    }

    /** A marimba bar: the fundamental with its tuned 4th and 10th partials, warm and woody. */
    fun marimba(m: Mix, t: Double, f: Double, gain: Double = 1.0) {
        val d = (0.9 * (260.0 / f).pow(0.5)).coerceIn(0.18, 1.2)
        modal(m, t, f, gain, listOf(Partial(1.0, 1.0, d), Partial(3.99, 0.32, d * 0.18), Partial(9.9, 0.12, d * 0.06)))
        click(m, t, gain * 0.12, 0.003, 7)
    }

    /** A xylophone bar: brighter and shorter, its partials tuned at 3 and 6. */
    fun xylophone(m: Mix, t: Double, f: Double, gain: Double = 1.0) {
        val d = (0.45 * (520.0 / f).pow(0.5)).coerceIn(0.12, 0.6)
        modal(m, t, f, gain, listOf(Partial(1.0, 1.0, d), Partial(3.0, 0.45, d * 0.35), Partial(6.02, 0.2, d * 0.12), Partial(10.3, 0.08, d * 0.05)))
        click(m, t, gain * 0.18, 0.002, 11)
    }

    /**
     * A music box's tine: a long clear fundamental, a faint octave, and the short metallic ring above it; two copies a
     * hair apart in pitch give it the slow shimmer of a real comb.
     */
    fun tine(m: Mix, t: Double, f: Double, gain: Double = 1.0) {
        val d = (1.6 * (700.0 / f).pow(0.6)).coerceIn(0.5, 2.4)
        val parts = listOf(Partial(1.0, 1.0, d), Partial(2.0, 0.12, d * 0.4), Partial(5.4, 0.18, 0.05), Partial(8.9, 0.07, 0.025))
        modal(m, t, f, gain * 0.55, parts, attack = 0.001)
        modal(m, t, f * 1.0016, gain * 0.45, parts.take(1), attack = 0.001)
    }

    /** A small bell, by frequency modulation: a bright strike that mellows as it rings. */
    fun bell(m: Mix, t: Double, f: Double, gain: Double = 1.0, decay: Double = 0.9) {
        val rate = m.rate
        val start = m.index(t)
        val n = (decay * 6 * rate).toInt()
        var pc = 0.0
        var pm = 0.0
        for (i in 0 until n) {
            val s = i.toDouble() / rate
            val index = 2.4 * exp(-s / (decay * 0.35))
            val v = sin(pc + index * sin(pm)) * exp(-s / decay) * (if (i < 40) i / 40.0 else 1.0)
            m.add(start + i, v * gain)
            pc += 2 * PI * f / rate
            pm += 2 * PI * f * 3.5 / rate
        }
    }

    /** A plucked string (Karplus–Strong): a burst of noise going round a delay line one wavelength long. */
    fun pluck(m: Mix, t: Double, f: Double, gain: Double = 1.0, length: Double = 1.4, bright: Double = 0.6, seed: Long = 1) {
        val rate = m.rate
        val start = m.index(t)
        val period = (rate / f).roundToInt().coerceAtLeast(2)
        val line = DoubleArray(period)
        val noise = Noise(seed * 7919 + period)
        // The string's starting shape: pulled into a point a fifth of the way along (a soft triangle), roughened with a
        // little smoothed noise; plain noise alone starts each note with a harsh click.
        var last = 0.0
        val pick = 0.2 * period
        for (i in 0 until period) {
            val shape = if (i < pick) i / pick else (period - i) / (period - pick)
            val v = noise.next(); last = bright * v + (1 - bright) * last
            line[i] = 0.75 * shape + 0.35 * last
        }
        val mean = line.average()
        for (i in 0 until period) line[i] -= mean
        val n = (length * rate).toInt()
        val fadeN = (0.03 * rate).toInt()
        val loss = 0.5 * 0.997.pow(220.0 / f)
        var j = 0
        for (i in 0 until n) {
            val v = line[j]
            val next = line[(j + 1) % period]
            line[j] = loss * (v + next)
            j = (j + 1) % period
            val tail = if (i > n - fadeN) (n - i).toDouble() / fadeN else 1.0
            val rise = if (i < 60) i / 60.0 else 1.0
            m.add(start + i, v * gain * tail * rise)
        }
    }

    /** A hand drum (bongo or conga): a sine that drops quickly to its pitch, a thump, and the slap of the hand. */
    fun drum(m: Mix, t: Double, f: Double, gain: Double = 1.0, decay: Double = 0.16, seed: Long = 3) {
        val rate = m.rate
        val start = m.index(t)
        val n = (decay * 7 * rate).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val s = i.toDouble() / rate
            val freq = f * (1 + 0.5 * exp(-s / 0.018))
            phase += 2 * PI * freq / rate
            val v = sin(phase) * exp(-s / decay) * (if (i < 30) i / 30.0 else 1.0)
            m.add(start + i, v * gain)
        }
        click(m, t, gain * 0.35, 0.006, seed)
    }

    /** A shaker or a woody click: a short burst of noise, brightened. */
    fun click(m: Mix, t: Double, gain: Double, decay: Double = 0.03, seed: Long = 5) {
        val rate = m.rate
        val start = m.index(t)
        val noise = Noise(seed * 104729 + start)
        val n = (decay * 7 * rate).toInt()
        var prev = 0.0
        for (i in 0 until n) {
            val s = i.toDouble() / rate
            val w = noise.next()
            m.add(start + i, (w - prev) * 0.5 * exp(-s / decay) * gain)
            prev = w
        }
    }

    /** A wood block: tick or tock. */
    fun block(m: Mix, t: Double, f: Double, gain: Double = 1.0) {
        modal(m, t, f, gain, listOf(Partial(1.0, 1.0, 0.035), Partial(2.7, 0.4, 0.015)), attack = 0.0005)
        click(m, t, gain * 0.3, 0.002, 13)
    }

    /**
     * A sweep of a pure tone from [f0] to [f1] Hz over [dur] s, rising and falling softly, with an optional warble
     * ([trill] Hz deep at [trillRate] Hz): a bird's chirp, a slide whistle.
     */
    fun sweep(m: Mix, t: Double, f0: Double, f1: Double, dur: Double, gain: Double, trill: Double = 0.0, trillRate: Double = 30.0,
              harmonic: Double = 0.0) {
        val rate = m.rate
        val start = m.index(t)
        val n = (dur * rate).toInt()
        var phase = 0.0
        for (i in 0 until n) {
            val x = i.toDouble() / n
            val s = i.toDouble() / rate
            val f = f0 * (f1 / f0).pow(x) + trill * sin(2 * PI * trillRate * s)
            phase += 2 * PI * f / rate
            val env = sin(PI * x).pow(2)
            m.add(start + i, (sin(phase) + harmonic * sin(2 * phase)) * env * gain)
        }
    }

    /** A breathy whistle note (a cuckoo's call): pure tone, a little second harmonic, a little breath, a soft start. */
    fun whistle(m: Mix, t: Double, f: Double, dur: Double, gain: Double = 1.0, seed: Long = 17) {
        val rate = m.rate
        val start = m.index(t)
        val n = (dur * rate).toInt()
        val breath = BandPass(rate).apply { tune(f, f * 0.25) }
        val noise = Noise(seed)
        var phase = 0.0
        for (i in 0 until n) {
            val s = i.toDouble() / rate
            val x = i.toDouble() / n
            val vib = 1 + 0.004 * sin(2 * PI * 5.5 * s)
            phase += 2 * PI * f * vib / rate
            val env = minOf(1.0, s / 0.025) * minOf(1.0, (1 - x) * n / (0.06 * rate))
            val v = sin(phase) + 0.18 * sin(2 * phase) + 0.05 * sin(3 * phase) + 0.3 * breath.step(noise.next())
            m.add(start + i, v * env * gain)
        }
    }

    /** A soft electronic beep: a square wave's first three harmonics, rounded at both ends. */
    fun beep(m: Mix, t: Double, f: Double, dur: Double, gain: Double = 1.0) {
        val rate = m.rate
        val start = m.index(t)
        val n = (dur * rate).toInt()
        val edge = 0.008 * rate
        for (i in 0 until n) {
            val ph = 2 * PI * f * i / rate
            val env = minOf(1.0, i / edge, (n - i) / edge)
            m.add(start + i, (sin(ph) + sin(3 * ph) / 9 + sin(5 * ph) / 25) * env * gain)
        }
    }

    /**
     * A voice, made the way a throat makes one: a buzz (a smooth sawtooth at [pitch] Hz, which moves over the call)
     * shaped by resonances ([formants]: (Hz, bandwidth, level), also moving), under an [envelope]. [breath] adds
     * hiss; [x] runs 0 to 1 over the call. Animal calls are drawn this way, not recorded.
     */
    fun voice(m: Mix, t: Double, dur: Double, gain: Double, pitch: (Double) -> Double, formants: (Double) -> List<Triple<Double, Double, Double>>,
              envelope: (Double) -> Double, breath: Double = 0.0, seed: Long = 23) {
        val rate = m.rate
        val start = m.index(t)
        val n = (dur * rate).toInt()
        val filters = formants(0.0).map { BandPass(rate) }
        var levels = DoubleArray(filters.size)
        val noise = Noise(seed)
        var phase = 0.0
        for (i in 0 until n) {
            val x = i.toDouble() / n
            if (i % 32 == 0) {
                val fs = formants(x)
                levels = DoubleArray(fs.size) { fs[it].third }
                fs.forEachIndexed { k, (f, bw, _) -> filters[k].tune(f, bw) }
            }
            val f0 = pitch(x)
            phase += f0 / rate
            if (phase >= 1) phase -= 1.0
            // A sawtooth with its corner rounded (PolyBLEP), so the buzz doesn't alias into a whine.
            val dt = f0 / rate
            var saw = 2 * phase - 1
            if (phase < dt) { val p = phase / dt; saw -= p + p - p * p - 1 }
            else if (phase > 1 - dt) { val p = (phase - 1) / dt; saw -= p * p + p + p + 1 }
            val src = saw + breath * noise.next()
            var v = 0.0
            for (k in filters.indices) v += filters[k].step(src) * levels[k]
            m.add(start + i, v * envelope(x) * gain)
        }
    }

    /** A rise over the first [a] and a fall over the last [r] of a call (fractions of it). */
    fun shape(x: Double, a: Double, r: Double): Double = minOf(1.0, x / a, (1 - x) / r).coerceAtLeast(0.0)
}
