package com.slowpixels.kidtimer.model

import kotlin.math.abs

/** A mark around the dial: [ms] of time left, a bigger dot if [major], and a [label] (minutes) on some. */
data class Mark(val ms: Long, val major: Boolean, val label: String?)

/**
 * The kid screen's face: a disc whose solid slice is the time left. Zero is at the top and the slice runs anticlockwise
 * from there, so as time goes its edge sweeps clockwise back up to the top. A whole turn is [spanMs], the timer's whole
 * length: the disc starts full, whatever the time.
 *
 * The face changes in [stepMs] steps, about a degree of turn, and shows the time left rounded up to a step: the slice
 * never looks smaller than the time left, and is gone exactly at the end.
 */
data class Dial(val spanMs: Long) {
    init { require(spanMs > 0) }

    /** The slice's size for [leftMs], in degrees (0 to 360). */
    fun sweep(leftMs: Long): Float = angle(leftMs.coerceIn(0, spanMs))

    /** Where [ms] of time left sits, in degrees anticlockwise from the top. */
    fun angle(ms: Long): Float = (ms.toDouble() / spanMs * 360).toFloat()

    /** How long between changes of the face: a step from [STEPS] (each divides a minute), about a degree of turn. */
    val stepMs: Long get() = STEPS.firstOrNull { it * 360 >= spanMs } ?: STEPS.last()

    /** What the face shows when [leftMs] is left: rounded up to a whole step, and no more than a whole turn. */
    fun shown(leftMs: Long): Long {
        if (leftMs <= 0) return 0
        val step = stepMs
        return minOf((leftMs + step - 1) / step * step, spanMs)
    }

    /** How long after [leftMs] the face next changes. */
    fun nextChangeIn(leftMs: Long): Long {
        if (leftMs <= 0) return Long.MAX_VALUE
        val step = stepMs
        val below = (leftMs - 1) / step * step
        return leftMs - below
    }

    /**
     * The marks around the dial, from the top (0) anticlockwise: small dots, and bigger ones with the minutes beside them.
     * A label too close to the top, where the turn ends, is left off so the two don't run into each other.
     */
    fun marks(): List<Mark> {
        val (minor, major) = scale(spanMs)
        val labelled = spanMs >= 2 * MINUTE
        val out = mutableListOf<Mark>()
        var ms = 0L
        while (ms < spanMs) {
            val isMajor = ms % major == 0L
            val nearEnd = ms > 0 && 360 - angle(ms) < LABEL_GAP_DEGREES
            val label = if (isMajor && labelled && !nearEnd) (ms / MINUTE).toString() else null
            out += Mark(ms, isMajor, label)
            ms += minor
        }
        return out
    }

    companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        private val STEPS = listOf(250L, 500L, 1_000L, 2_000L, 3_000L, 5_000L, 10_000L, 15_000L, 20_000L, 30_000L)
        /** Labels nearer each other than this (in degrees) would touch at the dial's size. */
        const val LABEL_GAP_DEGREES = 16f

        /** (minor, major) mark spacing for a dial of [span]: a few dozen dots at most, a handful of numbers. */
        fun scale(span: Long): Pair<Long, Long> = when {
            span <= MINUTE -> 5_000L to 15_000L
            span <= 3 * MINUTE -> 10_000L to MINUTE
            span <= 10 * MINUTE -> 30_000L to MINUTE
            span <= HOUR -> MINUTE to 5 * MINUTE
            span <= 2 * HOUR -> 5 * MINUTE to 15 * MINUTE
            else -> 5 * MINUTE to 30 * MINUTE
        }

        /** The minutes shown under the disc for [leftMs]: whole minutes, rounded up (59 s left is "1 min"). */
        fun minutesLeft(leftMs: Long): Long = if (leftMs <= 0) 0 else (leftMs + MINUTE - 1) / MINUTE

        /** Smallest gap, in degrees, between two labelled marks (for tests). */
        fun labelGap(dial: Dial): Float {
            val a = dial.marks().filter { it.label != null }.map { dial.angle(it.ms) } + 360f
            return a.zipWithNext { x, y -> abs(y - x) }.minOrNull() ?: 360f
        }
    }
}
