package com.ahmed9461.botos.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class Glyph { SPACE, ADD, APPEARANCE, BACK, MORE, SEND, CLOSE, CHECK, UP, DOWN, LINK }

/** Original rounded line artwork. No Apple or Telegram assets are distributed. */
@Composable
fun BotGlyph(glyph: Glyph, description: String? = null, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.size(24.dp).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.8f, cap = StrokeCap.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1, y1), Offset(x2, y2), 1.8f, StrokeCap.Round)
            when (glyph) {
                Glyph.SPACE -> {
                    listOf(3f to 3f, 14f to 3f, 3f to 14f, 14f to 14f).forEach { (x, y) ->
                        drawRoundRect(tint, Offset(x, y), Size(7f, 7f), CornerRadius(2.4f), style = stroke)
                    }
                }
                Glyph.ADD -> { line(12f, 4f, 12f, 20f); line(4f, 12f, 20f, 12f) }
                Glyph.APPEARANCE -> {
                    drawCircle(tint, 8.5f, Offset(12f, 12f), style = stroke)
                    drawCircle(tint, 4.2f, Offset(12f, 12f), style = stroke)
                    line(12f, 3.5f, 12f, 7.8f)
                    line(12f, 16.2f, 12f, 20.5f)
                }
                Glyph.BACK -> {
                    val end = if (rtl) 19f else 5f
                    val start = if (rtl) 5f else 19f
                    val middle = 12f
                    line(start, 12f, end, 12f); line(end, 12f, middle, 5f); line(end, 12f, middle, 19f)
                }
                Glyph.MORE -> listOf(5f, 12f, 19f).forEach { drawCircle(tint, 1.8f, Offset(it, 12f)) }
                Glyph.SEND -> { line(12f, 20f, 12f, 4f); line(12f, 4f, 5f, 11f); line(12f, 4f, 19f, 11f) }
                Glyph.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                Glyph.CHECK -> { line(4f, 12f, 9f, 17f); line(9f, 17f, 20f, 6f) }
                Glyph.UP -> { line(5f, 15f, 12f, 8f); line(12f, 8f, 19f, 15f) }
                Glyph.DOWN -> { line(5f, 9f, 12f, 16f); line(12f, 16f, 19f, 9f) }
                Glyph.LINK -> { line(5f, 19f, 19f, 5f); line(11f, 5f, 19f, 5f); line(19f, 5f, 19f, 13f) }
            }
        }
    }
}
