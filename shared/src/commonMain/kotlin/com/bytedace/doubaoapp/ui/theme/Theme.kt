package com.bytedace.doubaoapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = DoubaoBlue,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = ChatBackgroundDark,
    surface = ChatBackgroundDark,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    onPrimary = Color.White,
    onSecondary = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = DoubaoBlue,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = ChatBackground,
    surface = ChatBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onPrimary = Color.White,
    onSecondary = Color.White,
)

@Composable
fun DoubaoAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
