package com.slowpixels.kidtimer

import com.slowpixels.kidtimer.model.Picture
import com.slowpixels.kidtimer.model.Pictures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The end pictures: well formed, each with two different frames, and the faces drawn straight. */
class PictureTest {
    @Test fun everyPictureHasTwoDifferentFrames() {
        assertEquals(12, Pictures.all.size)
        assertEquals(Pictures.all.size, Pictures.all.map { it.id }.toSet().size)
        for (p in Pictures.all) {
            assertEquals(p.id, 2, p.frames.size)
            assertNotEquals(p.id, p.frames[0], p.frames[1])
        }
    }

    /** The faces seen from the front are mirror images left to right in every frame, so they sit dead centre. */
    @Test fun frontFacesAreSymmetric() {
        for (id in listOf("smiley", "bear", "koala", "cat", "dog", "butterfly", "princess")) {
            val p = Pictures.byId(id)
            for ((i, f) in p.frames.withIndex()) for (row in f) assertEquals("$id frame $i", row.reversed(), row)
        }
    }

    @Test fun boundsCoverEveryFrame() {
        for (p in Pictures.all) {
            val (l, t, r, b) = p.bounds.toList()
            for (f in p.frames) f.forEachIndexed { y, row ->
                row.forEachIndexed { x, c -> if (c != '.') assertTrue(p.id, x in l until r && y in t until b) }
            }
        }
        assertEquals(Picture.SIZE, Pictures.byId("smiley").bounds[2] - Pictures.byId("smiley").bounds[0])
    }

    @Test fun anUnknownPictureIsTheSmiley() = assertEquals("smiley", Pictures.byId("gone").id)
}
