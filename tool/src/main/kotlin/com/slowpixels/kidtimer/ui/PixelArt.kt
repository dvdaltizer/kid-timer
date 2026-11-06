package com.slowpixels.kidtimer.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.slowpixels.kidtimer.model.Picture
import com.slowpixels.kidtimer.model.frameAt
import com.thelightphone.sdk.ui.LightThemeTokens
import kotlin.math.floor
import kotlinx.coroutines.delay

/** How often a moving picture's clock is read; a frame only redraws when it changes. */
private const val TICK_MS = 50L

/** Milliseconds since [active] last turned on, read every [TICK_MS] while it's on; −1 while it's off. */
@Composable
fun motionClock(active: Boolean): State<Long> {
    val t = remember { mutableLongStateOf(-1L) }
    LaunchedEffect(active) {
        if (!active) { t.longValue = -1; return@LaunchedEffect }
        val start = SystemClock.uptimeMillis()
        while (true) {
            t.longValue = SystemClock.uptimeMillis() - start
            delay(TICK_MS)
        }
    }
    return t
}

/** [picture] moving to [clock] at its own pace for [seed] (see [frameAt]); only a change of frame redraws it. */
@Composable
fun MovingPixelArt(picture: Picture, clock: State<Long>, seed: Int, modifier: Modifier = Modifier, inverted: Boolean = false) {
    val frame by remember(picture, seed) { derivedStateOf { picture.frameAt(clock.value, seed) } }
    PixelArt(picture, modifier, frame, inverted)
}

/**
 * [picture]'s [frame], as big as fits in whole screen pixels per block (so every block is crisp), with its drawn part
 * in the middle: a picture that doesn't fill its 16 × 16 (the shark, the car) still sits centred, and stays put as its
 * frames change. [inverted] swaps ink and paper, for a chosen tile.
 */
@Composable
fun PixelArt(picture: Picture, modifier: Modifier = Modifier, frame: Int = 0, inverted: Boolean = false) {
    val colors = LightThemeTokens.colors
    val ink = if (inverted) colors.background else colors.content
    Canvas(modifier.semantics { contentDescription = picture.name }) {
        drawPixelArt(picture, frame, ink, Offset.Zero, size)
    }
}

/** Draws [picture]'s [frame] in [ink], as big as fits in the [area] at [topLeft] (see [PixelArt]). */
fun DrawScope.drawPixelArt(picture: Picture, frame: Int, ink: Color, topLeft: Offset, area: Size) {
    val rows = picture.frames[frame % picture.frames.size]
    val cell = floor(area.minDimension / Picture.SIZE).coerceAtLeast(1f)
    val (l, t, r, b) = picture.bounds.toList()
    val left = floor(topLeft.x + (area.width - (r - l) * cell) / 2) - l * cell
    val top = floor(topLeft.y + (area.height - (b - t) * cell) / 2) - t * cell
    // The checker's squares, in whole pixels: a quarter of a block, so a big picture shows a blocky texture.
    val sub = floor(cell / 4).coerceAtLeast(1f)
    for (y in rows.indices) for (x in rows[y].indices) {
        val ox = left + x * cell
        val oy = top + y * cell
        when (rows[y][x]) {
            '#' -> drawRect(ink, Offset(ox, oy), Size(cell, cell))
            // The checker runs on one grid across the whole picture, so neighbouring blocks join without a seam.
            ':' -> {
                var gy = floor(oy / sub) * sub
                while (gy < oy + cell) {
                    var gx = floor(ox / sub) * sub
                    while (gx < ox + cell) {
                        if (Math.floorMod((gx / sub).toInt() + (gy / sub).toInt(), 2) == 0) {
                            val x0 = maxOf(gx, ox)
                            val y0 = maxOf(gy, oy)
                            drawRect(ink, Offset(x0, y0), Size(minOf(gx + sub, ox + cell) - x0, minOf(gy + sub, oy + cell) - y0))
                        }
                        gx += sub
                    }
                    gy += sub
                }
            }
        }
    }
}
