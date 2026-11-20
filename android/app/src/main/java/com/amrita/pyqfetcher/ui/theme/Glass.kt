package com.amrita.pyqfetcher.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Colours for the frosted-glass look; one set for dark, one for light. */
@Immutable
data class GlassColors(
    val backdrop: Color,
    val blobs: List<Color>,
    /** Fill of a glass panel (translucent so the backdrop shows through). */
    val fill: Color,
    val fillSelected: Color,
    /** Top edge of the panel border catches the "light"; bottom edge fades out. */
    val edgeTop: Color,
    val edgeBottom: Color,
    /** Tint for blurred bars (bottom navigation). */
    val barTint: Color,
)

val DarkGlass = GlassColors(
    backdrop = Color(0xFF06090F),
    blobs = listOf(Color(0xFF01FAC6).copy(alpha = 0.16f), Color(0xFF5B6CFF).copy(alpha = 0.16f), Color(0xFF10B981).copy(alpha = 0.10f)),
    fill = Color.White.copy(alpha = 0.055f),
    fillSelected = Color(0xFF01FAC6).copy(alpha = 0.14f),
    edgeTop = Color.White.copy(alpha = 0.16f),
    edgeBottom = Color.White.copy(alpha = 0.03f),
    barTint = Color(0xFF0B1018).copy(alpha = 0.62f),
)

val LightGlass = GlassColors(
    backdrop = Color(0xFFEFF3F8),
    blobs = listOf(Color(0xFF01FAC6).copy(alpha = 0.30f), Color(0xFF7C8BFF).copy(alpha = 0.26f), Color(0xFF10B981).copy(alpha = 0.16f)),
    fill = Color.White.copy(alpha = 0.62f),
    fillSelected = Color(0xFF00806A).copy(alpha = 0.12f),
    edgeTop = Color.White.copy(alpha = 0.95f),
    edgeBottom = Color.Black.copy(alpha = 0.05f),
    barTint = Color.White.copy(alpha = 0.66f),
)

val LocalGlass = staticCompositionLocalOf { DarkGlass }

/** The soft colour field every screen sits on; it's what the glass panels "frost". */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val g = LocalGlass.current
    Box(modifier.fillMaxSize().background(g.backdrop)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun blob(color: Color, x: Float, y: Float, r: Float) =
                drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(x, y), radius = r), r, Offset(x, y))
            blob(g.blobs[0], w * 0.05f, h * 0.08f, w * 0.85f)
            blob(g.blobs[1], w * 1.0f, h * 0.45f, w * 0.8f)
            blob(g.blobs[2], w * 0.2f, h * 0.95f, w * 0.9f)
        }
        // Text drawn straight on the backdrop (not inside a Surface) needs a readable default colour.
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onBackground,
        ) { content() }
    }
}

fun glassBorder(g: GlassColors, width: Dp = 1.dp) =
    BorderStroke(width, Brush.verticalGradient(listOf(g.edgeTop, g.edgeBottom)))

/** A frosted panel. Clickable when [onClick] is given. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val g = LocalGlass.current
    val fill = if (selected) g.fillSelected else g.fill
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = fill, contentColor = MaterialTheme.colorScheme.onSurface, border = glassBorder(g), content = content)
    } else {
        Surface(modifier = modifier, shape = shape, color = fill, contentColor = MaterialTheme.colorScheme.onSurface, border = glassBorder(g), content = content)
    }
}
