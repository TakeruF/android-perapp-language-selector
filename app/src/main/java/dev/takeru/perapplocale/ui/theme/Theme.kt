package dev.takeru.perapplocale.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material You throughout: dynamic color exists from API 31 and our minSdk is 33, so there is no
 * device we support that would need a hand-rolled fallback palette. On skins that do not expose a
 * wallpaper palette, `dynamic*ColorScheme` returns the platform's default tonal palette, which is
 * a coherent scheme in its own right.
 */
@Composable
fun PerAppLocaleTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme =
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    MaterialTheme(colorScheme = colorScheme, content = content)
}
