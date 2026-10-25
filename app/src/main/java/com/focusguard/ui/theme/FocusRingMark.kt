package com.focusguard.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.size

/**
 * The launcher icon's mark (a segmented usage ring, open at the bottom, around a focus dot),
 * drawn in the in-app palette so headers match the app icon in both light and dark themes.
 * Geometry mirrors `drawable/ic_launcher_foreground.xml`, cropped to the mark itself.
 */
@Composable
fun FocusRingMark(modifier: Modifier = Modifier, size: Dp = 22.dp) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val segmentColors = if (dark) {
        listOf(FG_SegOther, FG_Seg3, FG_Seg2, FG_Seg1)
    } else {
        listOf(FG_Seg0, FG_Seg1, FG_Seg2, FG_Seg3)
    }
    val dotColor = if (dark) FG_SegOther else FG_Seg0
    // (start, end) angles in degrees, 0 = 12 o'clock, clockwise; same as the icon
    val segments = listOf(216f to 318f, 327f to 400f, 409f to 452f, 461f to 498f)

    Canvas(modifier = modifier.size(size)) {
        // The mark spans about 60 units of the icon's 108-unit canvas
        val unit = this.size.minDimension / 60f
        val center = Offset(30f * unit, 30f * unit)
        val radius = 21f * unit
        val stroke = Stroke(width = 9f * unit, cap = StrokeCap.Round)
        segments.forEachIndexed { i, (start, end) ->
            drawArc(
                color = segmentColors[i],
                startAngle = start - 90f,
                sweepAngle = end - start,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = stroke
            )
        }
        drawCircle(color = dotColor, radius = 6f * unit, center = center)
    }
}
