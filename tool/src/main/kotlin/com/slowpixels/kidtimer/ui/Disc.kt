package com.slowpixels.kidtimer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.slowpixels.kidtimer.model.Dial
import com.slowpixels.kidtimer.model.Lengths
import com.slowpixels.kidtimer.model.Picture
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToSp
import kotlin.math.cos
import kotlin.math.sin

/**
 * The timer's face, drawn for a small child: a thick round rim, the time left as a solid slice from the top (anticlockwise,
 * so its edge sweeps clockwise back to the top), round dots around the outside (big ones with the minutes beside them)
 * and, in the middle, the timer's [picture] on a plain button, waiting for the end.
 *
 * It fills the width it's given; the rim's radius is what's left inside the dots and numbers.
 */
@Composable
fun Disc(dial: Dial, shownMs: Long, picture: Picture, modifier: Modifier = Modifier) {
    val ink = LightThemeTokens.colors.content
    val paper = LightThemeTokens.colors.background
    val measurer = rememberTextMeasurer(cacheSize = 32)
    val number = LightThemeTokens.typography.heading.copy(color = ink, fontSize = 34f.designVerticalPxToSp())
    val marks = remember(dial) { dial.marks() }
    Canvas(modifier.semantics { contentDescription = if (shownMs <= 0) "Time's up" else "${Lengths.leftWords(shownMs)} left" }) {
        val c = Offset(size.width / 2, size.height / 2)
        val ring = 4.dp.toPx()
        val gap = 6.dp.toPx()
        val dots = 14.dp.toPx()
        val labelRoom = number.fontSize.toPx() * 1.45f
        val r = size.minDimension / 2 - gap - dots - labelRoom
        // The time left.
        val sweep = dial.sweep(shownMs)
        if (sweep > 0f) drawArc(ink, -90f - sweep, sweep, useCenter = true, topLeft = Offset(c.x - r, c.y - r), size = Size(2 * r, 2 * r))
        drawCircle(ink, r, c, style = Stroke(ring))
        fun at(degrees: Float, radius: Float): Offset {
            val a = Math.toRadians(degrees.toDouble())
            return Offset(c.x - radius * sin(a).toFloat(), c.y - radius * cos(a).toFloat())
        }
        // Round dots, not hairline ticks: big ones where a number is, small ones between.
        for (m in marks) drawCircle(ink, (if (m.major) 4.5 else 2.0).dp.toPx(), at(dial.angle(m.ms), r + gap + dots / 2))
        for (m in marks) {
            val label = m.label ?: continue
            val text = measurer.measure(label, number)
            val p = at(dial.angle(m.ms), r + gap + dots + labelRoom / 2)
            drawText(text, topLeft = Offset(p.x - text.size.width / 2f, p.y - text.size.height / 2f))
        }
        // The character in the middle, on a plain button ringed in ink, over the slice.
        val hub = r * 0.36f
        drawCircle(paper, hub, c)
        drawCircle(ink, hub, c, style = Stroke(3.dp.toPx()))
        val side = hub * 1.3f
        drawPixelArt(picture, 0, ink, Offset(c.x - side / 2, c.y - side / 2), Size(side, side))
    }
}
