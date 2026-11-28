package com.nesoi.seerr.android.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Material's breakpoint between compact and medium windows. Tablets, and phones turned on their side or unfolded, are wider. */
const val WIDE_WINDOW_DP = 600

/** Whether the window is wide enough for a navigation rail rather than a bar along the bottom. */
@Composable
fun isWideWindow(): Boolean = LocalConfiguration.current.screenWidthDp >= WIDE_WINDOW_DP

/** Text and forms stop reading well far past this width. */
val ReadingWidth: Dp = 640.dp

/** Lists of rows with a poster or avatar can be a little wider. */
val ListWidth: Dp = 720.dp

/** Keeps a page's content to [max] wide and centred, so it doesn't stretch across a tablet. A phone is unaffected. */
@Composable
fun ContentWidth(max: Dp = ReadingWidth, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = max).fillMaxSize()) { content() }
    }
}
