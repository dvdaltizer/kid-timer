package com.slowpixels.kidtimer.ui

import android.view.KeyEvent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.slowpixels.kidtimer.model.Dial
import com.slowpixels.kidtimer.model.Lengths
import com.slowpixels.kidtimer.model.Pictures
import com.slowpixels.kidtimer.model.Timer
import com.slowpixels.kidtimer.run.Hub
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.SimpleLightScreen
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.delay

/** How long a grown-up holds the arrow to leave the kid screen. */
const val HOLD_MS = 3_000L

/**
 * What the child sees, kept on and taking the whole screen. A grown-up leaves by holding the arrow for three seconds.
 *
 * LightOS gives a back swipe to the tool's screens without asking, so a child's swipe does take this screen down; it's
 * the `kid` on the board that says it belongs on top, and [HomeScreen] puts it straight back.
 */
class KidScreen(sealed: SealedLightActivity) : SimpleLightScreen<Unit>(sealed) {
    /**
     * Only while on screen. If the keep-on flag is set when a window comes back from the background, Android treats it as
     * the window's own and it can't be cleared, so the phone would stay awake on the grown-up's list after this had gone.
     */
    private val awake = mutableStateOf(false)

    override fun willShow() { awake.value = true }

    override fun onAppPause() { awake.value = false }

    @Composable
    override fun Content() {
        val kid by Hub.board.kid.collectAsState()
        if (kid == null) LaunchedEffect(Unit) { goBack() }
        KidFace(awake.value, leave = { Hub.board.leave() })
    }

    /** The phone's own keys do nothing here, so a child can't turn the end sound down or wander off. While it rings any of them stops it. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode !in SIDE_KEYS) return false
        if (event.repeatCount == 0 && Hub.board.ringing.value.isNotEmpty()) Hub.stop()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent) = keyCode in SIDE_KEYS

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent) = keyCode in SIDE_KEYS

    private companion object {
        /** The volume keys, the shutter half and fully pressed, and the wheel's press. Turning the wheel stays with LightOS. */
        val SIDE_KEYS = setOf(KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_CAMERA, KeyEvent.KEYCODE_FOCUS, 319)
    }
}

/**
 * The kid screen's picture: the timer's name, the disc as big as fits, emptying as time goes, with the timer's character
 * waiting in its middle, and what's left under it in big type ("7 min", then "45 sec"). When the time is up the disc gives
 * way to the character, moving, and "All done!", and a tap anywhere stops the sound.
 */
@Composable
fun KidFace(awake: Boolean, leave: () -> Unit) {
    val kid by Hub.board.kid.collectAsState()
    val timers by Hub.board.timers.collectAsState()
    val ringing by Hub.board.ringing.collectAsState()
    val timer = timers.firstOrNull { it.id == kid }
    Themed {
        if (timer != null) Box(Modifier.fillMaxSize().then(if (awake) Modifier.keepScreenOn() else Modifier)) {
            var held by remember { mutableStateOf(false) }
            val left = leftNow(timer)
            val done = left <= 0
            val stop = timer.id in ringing
            Column(Modifier.fillMaxSize().then(if (stop) Modifier.lightClickable(onClick = Hub::stop) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally) {
                LightTopBar(center = if (done) LightTopBarCenter.Text(timer.name)
                    else LightTopBarCenter.TwoLineDetail(timer.name, if (held) "Keep holding…" else "Grown-ups: hold the arrow"))
                Face(timer, left, done)
                LightText(if (done) "All done!" else Lengths.leftWords(left), LightTextVariant.Subtitle, Modifier.fillMaxWidth(),
                    align = TextAlign.Center, maxLines = 1)
                if (stop) LightBottomBar(listOf(LightBarButton.Text("STOP", onClick = Hub::stop)))
                else Box(Modifier.padding(bottom = 1f.gridUnitsAsDp()))
            }
            if (!done) HoldKey(onHold = { held = it }, onLeave = leave)
        }
    }
}

@Composable
private fun ColumnScope.Face(timer: Timer, left: Long, done: Boolean) {
    val picture = remember(timer.picture) { Pictures.byId(timer.picture) }
    val clock = motionClock(done)
    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 0.5f.gridUnitsAsDp()), contentAlignment = Alignment.Center) {
        if (done) MovingPixelArt(picture, clock, seed = 0, Modifier.fillMaxHeight().aspectRatio(1f).padding(2f.gridUnitsAsDp()))
        else Disc(timer.face, timer.face.shown(left), picture, Modifier.fillMaxHeight().aspectRatio(1f))
    }
}

/**
 * The time left, read when the disc next moves (about a degree of turn) and, in the last minute, every second for the
 * words under it. Nothing else redraws.
 */
@Composable
private fun leftNow(timer: Timer): Long {
    var left by remember(timer) { mutableLongStateOf(timer.left(System.currentTimeMillis())) }
    LaunchedEffect(timer) {
        val dial = timer.face
        while (true) {
            val l = timer.left(System.currentTimeMillis())
            left = l
            if (l <= 0) break
            val nextSecond = if (l <= Dial.MINUTE) (l - 1) % 1000 + 1 else Long.MAX_VALUE
            delay(minOf(dial.nextChangeIn(l), nextSecond) + 5)
        }
    }
    return left
}

/**
 * The way out for a grown-up: the arrow at the top left, held for [HOLD_MS]. A tap, or a finger lifted sooner, does nothing.
 * (Leaving with the finger still down is safe: the next screen never saw it land.)
 */
@Composable
private fun HoldKey(onHold: (Boolean) -> Unit, onLeave: () -> Unit) {
    Box(
        Modifier.size(5f.gridUnitsAsDp())
            .semantics {
                contentDescription = "Grown-ups hold this for three seconds to leave."
                customActions = listOf(CustomAccessibilityAction("Leave") { onLeave(); true })
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onHold(true)
                    var lifted = false
                    withTimeoutOrNull(HOLD_MS) { waitForUpOrCancellation(); lifted = true }
                    onHold(false)
                    if (!lifted) onLeave()
                }
            },
    ) { LightIcon(LightIcons.BACK, Modifier.padding(start = 1f.gridUnitsAsDp(), top = 0.5f.gridUnitsAsDp()), contentDescription = null) }
}
