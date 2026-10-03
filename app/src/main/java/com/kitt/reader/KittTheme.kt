package com.kitt.reader

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Black/red at night; a high-contrast warm light surface in daylight. */
@Composable
fun KittTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFFF737B), onPrimary = Color(0xFF30070C),
        primaryContainer = Color(0xFF421A20), onPrimaryContainer = Color(0xFFFFDADD),
        secondary = Color(0xFFC5C8D0), tertiary = Color(0xFFFF4B57),
        background = Color(0xFF090B0F), surface = Color(0xFF101218),
        onSurface = Color(0xFFF3F1F3), onSurfaceVariant = Color(0xFFC3BBC0), outline = Color(0xFF93868D)
    ) else lightColorScheme(
        primary = Color(0xFF9F2535), onPrimary = Color.White,
        primaryContainer = Color(0xFFFFDADD), onPrimaryContainer = Color(0xFF3B0710),
        secondary = Color(0xFF555E6E), tertiary = Color(0xFFBB2535),
        background = Color(0xFFF6F3F3), surface = Color(0xFFF6F3F3),
        onSurface = Color(0xFF1E1A20), onSurfaceVariant = Color(0xFF594B52), outline = Color(0xFF86747D)
    )
    MaterialTheme(colorScheme = colors, content = content)
}
