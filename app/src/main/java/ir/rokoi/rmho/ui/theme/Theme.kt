package ir.rokoi.rmho.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val RmhoDarkScheme = darkColorScheme(
    primary = RmhoPrimary,
    onPrimary = RmhoOnPrimary,
    secondary = RmhoSecondary,
    background = RmhoBg,
    onBackground = RmhoText,
    surface = RmhoSurface,
    onSurface = RmhoText,
    surfaceVariant = RmhoSurfaceVariant,
    onSurfaceVariant = RmhoTextDim,
    error = RmhoError
)

@Composable
fun RmhoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RmhoDarkScheme,
        typography = RmhoTypography,
        content = content
    )
}
