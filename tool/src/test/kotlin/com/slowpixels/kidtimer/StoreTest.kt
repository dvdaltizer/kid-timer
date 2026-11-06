package com.slowpixels.kidtimer

import com.slowpixels.kidtimer.model.Lengths
import com.slowpixels.kidtimer.model.Prefs
import com.slowpixels.kidtimer.model.Saved
import com.slowpixels.kidtimer.model.Store
import com.slowpixels.kidtimer.model.Timer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The little file everything is kept in. */
class StoreTest {
    private val dir = Files.createTempDirectory("kid").toFile()
    private val file = File(dir, "timers.properties")
    private val store = Store(file)

    @Test fun nothingSavedMeansTheDefaults() = assertEquals(Saved(), store.load())

    @Test fun whatsSavedComesBack() {
        val saved = Saved(Prefs(150, "farm", "bear", false),
            listOf(Timer(3, "Bath", 60_000, 1_000_000, "alarm", "cat", voice = false), Timer(4, "Timer 2", 120_000, 2_000_000, "jungle", "smiley")), 5)
        store.save(saved)
        assertEquals(saved.copy(timers = saved.timers.sortedBy { it.id }), store.load().let { it.copy(timers = it.timers.sortedBy { t -> t.id }) })
    }

    @Test fun anyNameSurvives() {
        for (name in listOf("Bath | teeth", "a=b: c", "Ménage ✨", "  spaces  ", "line\nbreak")) {
            store.save(Saved(timers = listOf(Timer(1, name, 1, 2, "jungle", "smiley"))))
            assertEquals(name, store.load().timers.single().name)
        }
    }

    @Test fun aDamagedFileLosesOnlyWhatsDamaged() {
        file.writeText("seconds=abc\nsound=farm\ntimer.1=nonsense\ntimer.2=2|60000|100|jungle|smiley|true|Good\ntimer.3=3|x|100|jungle|smiley|true|Bad\n")
        val s = store.load()
        assertEquals(Prefs().seconds, s.prefs.seconds)
        assertEquals("farm", s.prefs.sound)
        assertEquals(listOf("Good"), s.timers.map { it.name })
    }

    @Test fun aLengthOutOfRangeIsBroughtBack() {
        file.writeText("seconds=999999\n")
        assertEquals(Lengths.MAX_SECONDS, store.load().prefs.seconds)
    }

    @Test fun idsAreNeverReusedEvenIfTheCountWasLost() {
        file.writeText("timer.7=7|60000|100|jungle|smiley|true|Seven\n")
        assertEquals(8, store.load().nextId)
    }

    @Test fun savingLeavesNoLooseFiles() {
        store.save(Saved())
        assertEquals(listOf("timers.properties"), dir.list()!!.toList())
    }
}
