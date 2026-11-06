package com.slowpixels.kidtimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.slowpixels.kidtimer.model.Lengths
import com.slowpixels.kidtimer.model.Picture
import com.slowpixels.kidtimer.model.Pictures
import com.slowpixels.kidtimer.run.Hub
import com.slowpixels.kidtimer.sound.Sounds
import com.slowpixels.kidtimer.sound.Tunes
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable

/**
 * Other: a length typed on keys from the right: the last two digits are seconds, the rest minutes (2 3 0 is 2:30, 4 5 is
 * 0:45), up to three hours. Seconds past 59 count on (9 0 is 1:30).
 */
class LengthScreen(sealed: SealedLightActivity) : SimpleLightScreen<Unit>(sealed) {
    @Composable
    override fun Content() = Themed {
        var typed by remember { mutableStateOf("") }
        val d = typed.padStart(3, '0')
        val total = Lengths.seconds(typed)
        BackBar("Time") { goBack() }
        Box(Modifier.fillMaxWidth().padding(vertical = 0.5f.gridUnitsAsDp()), contentAlignment = Alignment.Center) {
            Figures(d.dropLast(2).trimStart('0').ifEmpty { "0" } + ":" + d.takeLast(2), size = 90f,
                modifier = Modifier.semantics { contentDescription = "${total / 60} minutes ${total % 60} seconds" })
        }
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 2f.gridUnitsAsDp())) {
            for (row in listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -2))) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    for (k in row) Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        when {
                            k >= 0 -> Box(Modifier.fillMaxSize().lightClickable { Lengths.typed(typed, k)?.let { typed = it } }, contentAlignment = Alignment.Center) {
                                LightText("$k", LightTextVariant.Heading)
                            }
                            k == -2 && typed.isNotEmpty() -> Box(Modifier.fillMaxSize().lightClickable { typed = typed.dropLast(1) }
                                .semantics { contentDescription = "Delete" }, contentAlignment = Alignment.Center) {
                                LightIcon(LightIcons.DELETE, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
        LightBottomBar(listOf(if (total > 0) LightBarButton.Text("DONE", onClick = {
            Hub.board.update { it.copy(seconds = total.coerceIn(1, Lengths.MAX_SECONDS)) }
            goBack()
        }) else null))
    }
}

/** The new timer's name. Left blank, it's "Timer" and the next free number. */
class NameScreen(sealed: SealedLightActivity) : SimpleLightScreen<Unit>(sealed) {
    @Composable
    override fun Content() {
        val options = rememberKeyboardOptions()
        val name by Hub.name.collectAsState()
        val text = rememberTextFieldState(name)
        Themed {
            LightTextInputEditor(
                title = "Name", state = text, keyboardOptionsFlow = options, submitLabel = "SAVE", singleLine = true, initialCaps = true,
                onSubmit = { Hub.name.value = it.toString().trim().take(30); goBack() },
                onBack = { goBack() },
                modifier = Modifier.background(LightThemeTokens.colors.background),
            )
        }
    }
}

/**
 * The end sounds, each played through once when chosen; and above them whether the character makes its sound first
 * (turned on, it plays it).
 */
class SoundScreen(sealed: SealedLightActivity) : SimpleLightScreen<Unit>(sealed) {
    @Composable
    override fun Content() {
        val prefs by Hub.board.prefs.collectAsState()
        DisposableEffect(Unit) { onDispose { Hub.stopListening() } }
        ListPage("Sound", back = { goBack() }) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 0.6f.gridUnitsAsDp()).lightClickable {
                    val on = !prefs.voices
                    Hub.board.update { it.copy(voices = on) }
                    if (on) Hub.listen(Sounds.CALL + prefs.picture) else Hub.stopListening()
                }.semantics(mergeDescendants = true) { selected = prefs.voices },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LightText("CHARACTER SOUNDS", LightTextVariant.Copy, Modifier.weight(1f))
                LightIcon(if (prefs.voices) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF, size = 2.5f,
                    contentDescription = if (prefs.voices) "On" else "Off")
            }
            for (t in Tunes.all) Choice(t.name, prefs.sound == t.id) {
                Hub.board.update { it.copy(sound = t.id) }
                Hub.listen(t.id)
            }
        }
    }
}

/**
 * The characters, three across, each moving at its own pace so they look alive (and never in step); the chosen one is
 * solid. Choosing one plays its sound, if character sounds are on.
 */
class CharacterScreen(sealed: SealedLightActivity) : SimpleLightScreen<Unit>(sealed) {
    @Composable
    override fun Content() {
        val prefs by Hub.board.prefs.collectAsState()
        val clock = motionClock(true)
        DisposableEffect(Unit) { onDispose { Hub.stopListening() } }
        Themed {
            BackBar("Character") { goBack() }
            Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.5f.gridUnitsAsDp()),
                verticalArrangement = Arrangement.SpaceEvenly) {
                for ((r, row) in Pictures.all.chunked(3).withIndex()) Row(Modifier.fillMaxWidth().weight(1f)) {
                    for ((c, p) in row.withIndex()) Tile(p, prefs.picture == p.id, clock, seed = r * 3 + c, Modifier.weight(1f)) {
                        Hub.board.update { it.copy(picture = p.id) }
                        if (prefs.voices) Hub.listen(Sounds.CALL + p.id)
                    }
                }
            }
        }
    }
}

@Composable
private fun Tile(picture: Picture, chosen: Boolean, clock: State<Long>, seed: Int, modifier: Modifier, onClick: () -> Unit) {
    val colors = LightThemeTokens.colors
    Column(modifier.fillMaxHeight().lightClickable(onClick = onClick).semantics(mergeDescendants = true) { selected = chosen },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(4.4f.gridUnitsAsDp()).then(if (chosen) Modifier.background(colors.content, RoundedCornerShape(0.5f.gridUnitsAsDp())) else Modifier)
            .padding(0.3f.gridUnitsAsDp())) {
            MovingPixelArt(picture, clock, seed, Modifier.fillMaxSize(), inverted = chosen)
        }
        LightText(picture.name, LightTextVariant.Detail, Modifier.padding(top = 0.15f.gridUnitsAsDp()), align = TextAlign.Center, maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
}

class AboutScreen(sealed: SealedLightActivity) : SimpleLightScreen<Unit>(sealed) {
    @Composable
    override fun Content() {
        ListPage("About", back = { goBack() }) {
            val gap = Modifier.padding(bottom = 1f.gridUnitsAsDp())
            LightText("A timer a small child can read: the slice shrinks as the time runs out, and a character says when it's done.", LightTextVariant.Paragraph, gap)
            LightText("To leave the timer, hold the arrow at its top left for three seconds. The timer keeps running.", LightTextVariant.Paragraph, gap)
            LightText("It rings at the phone's alarm volume. If the timer isn't on the screen, open Kid Timer to stop the sound, or it stops by itself after five minutes.", LightTextVariant.Paragraph, gap)
            LightText("Every sound and character was made for this tool. Works with no connection, and nothing you do here leaves the phone.", LightTextVariant.Paragraph, gap)
            LightText("Made by Slow Pixels", LightTextVariant.Paragraph, lighten = true)
        }
    }
}
