package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val BottomBlackGradientBrush = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black)
)

/**
 * Reusable non-blocking black bottom gradient scrim (transparent at top to pitch black at bottom).
 * Used at the bottom of every screen and overlaid above bottom sheets / queue / widgets
 * without intercepting touch gestures.
 */
@Composable
fun BottomBlackGradientScrim(
    modifier: Modifier = Modifier,
    extraHeight: Dp = 64.dp,
    minHeight: Dp = 76.dp
) {
    val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height((navBarBottom + extraHeight).coerceAtLeast(minHeight))
            .background(BottomBlackGradientBrush)
    )
}
