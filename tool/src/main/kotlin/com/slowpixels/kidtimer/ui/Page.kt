package com.slowpixels.kidtimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.designVerticalPxToSp
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import kotlinx.coroutines.delay

/** LightOS's theme on a full screen. */
@Composable
fun Themed(content: @Composable ColumnScope.() -> Unit) {
    val colors by LightThemeController.colors.collectAsState()
    LightTheme(colors = colors) {
        Column(Modifier.fillMaxSize().background(LightThemeTokens.colors.background), content = content)
    }
}

/** A sub-screen's top: a back arrow and title. */
@Composable
fun BackBar(title: String, back: () -> Unit) = LightTopBar(
    leftButton = LightBarButton.LightIcon(LightIcons.BACK, onClick = back),
    center = LightTopBarCenter.Text(title),
    modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
)

/** A sub-screen: a back arrow and title over a scrolling list. */
@Composable
fun ListPage(title: String, back: () -> Unit, rows: @Composable ColumnScope.() -> Unit) = Themed {
    BackBar(title, back)
    LightScrollView(Modifier.fillMaxSize().padding(horizontal = 1f.gridUnitsAsDp()), content = rows)
}

/** One choice in a list; the one in use has a dot. */
@Composable
fun Choice(label: String, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().lightClickable(onClick = onClick).semantics(mergeDescendants = true) { this.selected = selected }
            .padding(vertical = 0.6f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(label.uppercase(), LightTextVariant.Copy, Modifier.weight(1f))
        if (selected) LightIcon(LightIcons.SELECT_ON, size = 1.5f, contentDescription = "Selected")
    }
}

/** A row that opens a choice: what it is on the left and what it's set to on the right. */
@Composable
fun ValueRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().lightClickable(onClick = onClick).semantics(mergeDescendants = true) {}
            .padding(vertical = 0.55f.gridUnitsAsDp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LightText(label.uppercase(), LightTextVariant.Copy, Modifier.padding(end = 1f.gridUnitsAsDp()))
        LightText(value, LightTextVariant.Copy, Modifier.weight(1f), align = TextAlign.End, lighten = true,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A key with a word on it: plain until it's chosen, then solid, so what's picked shows at a glance. */
@Composable
fun Key(label: String, on: Boolean, modifier: Modifier = Modifier, description: String? = null, onClick: () -> Unit) {
    val colors = LightThemeTokens.colors
    val shape = RoundedCornerShape(0.5f.gridUnitsAsDp())
    Box(
        modifier.then(if (on) Modifier.background(colors.content, shape) else Modifier).lightClickable(onClick = onClick)
            .semantics(mergeDescendants = true) { if (description != null) contentDescription = description; selected = on },
        contentAlignment = Alignment.Center,
    ) {
        LightText(label, LightTextVariant.Copy, color = if (on) colors.background else null, maxLines = 1)
    }
}

/** Big figures that don't shift as they change: each digit as wide as the others. */
@Composable
fun Figures(text: String, modifier: Modifier = Modifier, size: Float = 52f) {
    Text(
        text, modifier, color = LightThemeTokens.colors.content, maxLines = 1,
        style = LightThemeTokens.typography.subtitle.copy(
            fontSize = size.designVerticalPxToSp(), lineHeight = (size * 1.2f).designVerticalPxToSp(), fontFeatureSettings = "tnum",
        ),
    )
}

/** The value of [read], read again every [step] ms on the step (counted from [phase]), for counting screens. */
@Composable
fun ticking(step: Long, key: Any?, phase: Long = 0, read: () -> Long): Long {
    var t by remember(key) { mutableLongStateOf(read()) }
    LaunchedEffect(key, step, phase) {
        while (true) {
            val v = read()
            delay(step - Math.floorMod(v - phase, step) + 2)
            t = read()
        }
    }
    return t
}
