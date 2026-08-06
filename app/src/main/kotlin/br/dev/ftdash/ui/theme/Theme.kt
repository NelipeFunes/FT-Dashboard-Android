package br.dev.ftdash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Escuro sempre. Nada de dynamic color: o painel tem que ficar igual em
 * qualquer aparelho e em qualquer hora do dia.
 */
private val FtColorScheme = darkColorScheme(
    primary = Emerald500,
    onPrimary = Zinc950,
    secondary = Sky400,
    background = Zinc950,
    onBackground = Zinc100,
    surface = Zinc900,
    onSurface = Zinc100,
    surfaceVariant = Zinc850,
    onSurfaceVariant = Zinc400,
    outline = Zinc800,
    error = Red500,
    onError = Zinc100,
)

@Composable
fun FtDashTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FtColorScheme,
        typography = FtTypography,
        content = content,
    )
}
