package com.focusguard.ui.extensions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A custom clickable modifier that applies a subtle scale-down effect when pressed,
 * providing immediate tactile visual feedback per the UX specifications.
 */
fun Modifier.clickableScale(
    enabled: Boolean = true,
    scaleDown: Float = 0.97f,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) scaleDown else 1f,
        animationSpec = tween(durationMillis = 150)
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.material.ripple.rememberRipple(
                color = com.focusguard.ui.theme.FG_Seg1
            ),
            enabled = enabled,
            onClick = onClick
        )
}
