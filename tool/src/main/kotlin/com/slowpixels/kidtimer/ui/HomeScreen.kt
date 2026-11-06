package com.slowpixels.kidtimer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.slowpixels.kidtimer.model.Lengths
import com.slowpixels.kidtimer.model.Pictures
import com.slowpixels.kidtimer.model.Timer
import com.slowpixels.kidtimer.run.Hub
import com.slowpixels.kidtimer.sound.Tunes
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.audio.DefaultLightAudio
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The grown-up's screen. With no timer running, a new timer's choices and Start. With timers running, each one's time
 * left, counting down, with keys to add 30 seconds and to cancel it; tapping one shows it to the children again.
 *
 * While a timer is on the kid screen this shows the same picture and puts [KidScreen] back on top. That's what makes a
 * back swipe harmless: the tool can't refuse it, but the swipe lands here, on what the child was already looking at, and
 * the kid screen is up again before the next frame.
 */
@InitialScreen
class HomeScreen(sealed: SealedLightActivity) : SimpleLightScreen<Unit>(sealed) {
    init { Hub.attach(DefaultLightAudio(sealed), sealed.vibrator, lightContext) }

    @Composable
    override fun Content() {
        val kid by Hub.board.kid.collectAsState()
        val timers by Hub.board.timers.collectAsState()
        if (kid != null) {
            KidFace(awake = false, leave = {})
            LaunchedEffect(Unit) { navigateTo(::KidScreen) }
        } else Themed {
            LightTopBar(center = LightTopBarCenter.Text("Kid Timer"),
                rightButton = LightBarButton.Text("ABOUT", onClick = { navigateTo(::AboutScreen) }))
            if (timers.isEmpty()) {
                Choices(open = { navigateTo(it) })
                LightBottomBar(listOf(LightBarButton.Text("START", onClick = { Hub.start(lightContext) })))
            } else {
                TimerList(timers)
                LightBottomBar(listOf(LightBarButton.Text("NEW TIMER", onClick = { navigateTo(::NewTimerScreen) })))
            }
        }
    }
}

/** Another timer, with some already running. */
class NewTimerScreen(sealed: SealedLightActivity) : SimpleLightScreen<Unit>(sealed) {
    @Composable
    override fun Content() = Themed {
        BackBar("New timer") { goBack() }
        Choices(open = { navigateTo(it) })
        LightBottomBar(listOf(LightBarButton.Text("START", onClick = {
            Hub.start(lightContext)
            goBack()
        })))
    }
}

/**
 * A new timer's length (a quick choice, or Other to type one), and its name, sound and character. It fits one screen, so
 * a stressed parent has nothing to look for.
 */
@Composable
private fun ColumnScope.Choices(open: ((SealedLightActivity) -> SimpleLightScreen<Unit>) -> Unit) {
    val prefs by Hub.board.prefs.collectAsState()
    val timers by Hub.board.timers.collectAsState()
    val name by Hub.name.collectAsState()
    val typed = prefs.seconds % 60 != 0 || prefs.seconds / 60 !in Lengths.QUICK
    Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp())) {
        for (row in (Lengths.QUICK + 0).chunked(3)) Row(Modifier.fillMaxWidth().height(3.5f.gridUnitsAsDp())) {
            for (q in row) {
                val wide = Modifier.weight(1f).height(3.5f.gridUnitsAsDp())
                if (q > 0) Key("$q MIN", prefs.seconds == q * 60, wide, if (q == 1) "1 minute" else "$q minutes") {
                    Hub.board.update { it.copy(seconds = q * 60) }
                } else Key(if (typed) Lengths.words(prefs.seconds).uppercase() else "OTHER", typed, wide, "Other length") { open(::LengthScreen) }
            }
        }
        Box(Modifier.height(0.5f.gridUnitsAsDp()))
        ValueRow("Name", name.ifEmpty { remember(timers) { Hub.board.nextName() } }) { open(::NameScreen) }
        ValueRow("Sound", Tunes.byId(prefs.sound).name) { open(::SoundScreen) }
        ValueRow("Character", Pictures.byId(prefs.picture).name) { open(::CharacterScreen) }
    }
}

@Composable
private fun ColumnScope.TimerList(timers: List<Timer>) {
    val ringing by Hub.board.ringing.collectAsState()
    val first = timers.firstOrNull { it.id !in ringing && !it.done(System.currentTimeMillis()) }
    // Counts on the soonest timer's seconds, so it reaches 0:00 as it rings.
    val now = ticking(1_000, first?.id to first?.endsAt, phase = first?.endsAt ?: 0) { System.currentTimeMillis() }
    var is24 by remember { mutableStateOf(false) }
    // The phone's 12 or 24-hour choice, which only this widget can read from inside a tool.
    AndroidView(factory = { android.widget.TextClock(it) }, modifier = Modifier.size(0.dp), update = { is24 = it.is24HourModeEnabled })
    val clock = remember(is24) { SimpleDateFormat(if (is24) "H:mm" else "h:mm a", Locale.getDefault()) }
    LightScrollView(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp())) {
        for (t in timers) TimerRow(t, up = t.done(now), now, clock)
    }
}

@Composable
private fun TimerRow(t: Timer, up: Boolean, now: Long, clock: SimpleDateFormat) {
    Column(Modifier.fillMaxWidth().lightClickable(enabled = !up, onClick = { Hub.board.show(t.id) }).padding(vertical = 0.5f.gridUnitsAsDp())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Figures(if (up) "Time's up" else Lengths.countdown(t.left(now)), Modifier.weight(1f))
            val key = Modifier.size(5f.gridUnitsAsDp(), 3.5f.gridUnitsAsDp())
            if (up) Key("STOP", on = false, key) { Hub.cancel(t.id) }
            else {
                Key("+30 S", on = false, key, "Add 30 seconds to ${t.name}") { Hub.extend(t.id) }
                Box(Modifier.size(3.5f.gridUnitsAsDp()).lightClickable { Hub.cancel(t.id) }
                    .semantics { contentDescription = "Cancel ${t.name}" }, contentAlignment = Alignment.Center) {
                    LightIcon(LightIcons.CLOSE, size = 1.5f, contentDescription = null)
                }
            }
        }
        LightText(t.name + " · " + (if (up) "Ended " else "Ends ") + clock.format(Date(t.endsAt)), LightTextVariant.Fine, lighten = true,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
