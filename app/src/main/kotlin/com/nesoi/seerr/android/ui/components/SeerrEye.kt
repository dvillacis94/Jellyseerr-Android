package com.nesoi.seerr.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.nesoi.seerr.android.ui.theme.EyeBottom
import com.nesoi.seerr.android.ui.theme.EyeTop

/** Seerr's eye logo, drawn from the same geometry as the app icon. */
@Composable
fun SeerrEye(modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f).semantics { contentDescription = "Seerr" }) {
        drawPath(
            path = eyePath(size),
            brush = Brush.linearGradient(listOf(EyeTop, EyeBottom), start = Offset.Zero, end = Offset(size.width, size.height)),
        )
    }
}

/**
 * A disc with an off-centre pupil, minus the pupil's highlight: in a 96-unit square,
 * a circle (48, 48) r48, cut by a circle (52, 52) r28 that keeps a circle (38, 38) r14.
 */
private fun eyePath(size: Size): Path {
    val scale = minOf(size.width, size.height) / 96f
    fun circle(x: Float, y: Float, r: Float) = Path().apply {
        addOval(Rect(center = Offset(x * scale, y * scale), radius = r * scale))
    }
    val pupil = Path.combine(PathOperation.Difference, circle(52f, 52f, 28f), circle(38f, 38f, 14f))
    return Path.combine(PathOperation.Difference, circle(48f, 48f, 48f), pupil)
}
