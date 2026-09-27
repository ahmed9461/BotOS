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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

enum class Glyph { SPACE, LIBRARY, ADD, APPEARANCE, BACK, MORE, SEND, CLOSE, CHECK, UP, DOWN, LINK,
    CHAT, SEARCH, PHOTO, VIDEO, AUDIO, FILE, MIC, PLAY, PAUSE, STOP, REFRESH, COMMAND, EDIT, TRASH, CLOCK, ERROR }

/** Original, consistent 24-unit rounded symbols. No Apple/SF Symbols or Telegram assets. */
@Composable
fun BotGlyph(glyph: Glyph, description: String? = null, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.size(24.dp).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(tint, Offset(x1, y1), Offset(x2, y2), 1.7f, StrokeCap.Round)
            fun box(x: Float, y: Float, w: Float, h: Float, radius: Float = 3f) =
                drawRoundRect(tint, Offset(x, y), Size(w, h), CornerRadius(radius), style = stroke)
            fun path(vararg points: Pair<Float, Float>) {
                val p = Path(); points.forEachIndexed { i, (x,y) -> if (i == 0) p.moveTo(x,y) else p.lineTo(x,y) }
                drawPath(p, tint, style = stroke)
            }
            when (glyph) {
                Glyph.SPACE -> listOf(3f to 3f, 14f to 3f, 3f to 14f, 14f to 14f).forEach { (x,y) -> box(x,y,7f,7f,2.4f) }
                Glyph.CHAT -> {
                    val p = Path().apply { moveTo(6f,4f); lineTo(18f,4f); quadraticTo(21f,4f,21f,7f)
                        lineTo(21f,15f); quadraticTo(21f,18f,18f,18f); lineTo(9f,18f); lineTo(4f,21f)
                        lineTo(4f,17f); quadraticTo(3f,16f,3f,14f); lineTo(3f,7f); quadraticTo(3f,4f,6f,4f); close() }
                    drawPath(p,tint,style=stroke); line(7f,9f,17f,9f); line(7f,13f,13f,13f)
                }
                Glyph.LIBRARY -> { box(3f,4f,7f,16f,2f); box(13f,4f,7f,16f,2f); line(5.5f,8f,7.5f,8f); line(15.5f,16f,17.5f,16f) }
                Glyph.ADD -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
                Glyph.APPEARANCE -> { drawCircle(tint,8.5f,Offset(12f,12f),style=stroke)
                    drawArc(tint,-90f,180f,true,Offset(3.5f,3.5f),Size(17f,17f)) }
                Glyph.BACK -> if (rtl) path(9f to 5f,16f to 12f,9f to 19f) else path(15f to 5f,8f to 12f,15f to 19f)
                Glyph.MORE -> listOf(5f,12f,19f).forEach { drawCircle(tint,1.5f,Offset(it,12f)) }
                Glyph.SEND -> { line(12f,20f,12f,4f); path(5f to 11f,12f to 4f,19f to 11f) }
                Glyph.CLOSE -> { line(6f,6f,18f,18f); line(18f,6f,6f,18f) }
                Glyph.CHECK -> path(4f to 12f,9f to 17f,20f to 6f)
                Glyph.UP -> path(5f to 15f,12f to 8f,19f to 15f)
                Glyph.DOWN -> path(5f to 9f,12f to 16f,19f to 9f)
                Glyph.LINK -> { line(5f,19f,19f,5f); path(11f to 5f,19f to 5f,19f to 13f) }
                Glyph.SEARCH -> { drawCircle(tint,6.5f,Offset(10.5f,10.5f),style=stroke); line(15.5f,15.5f,21f,21f) }
                Glyph.PHOTO -> { box(3f,4f,18f,16f); drawCircle(tint,1.4f,Offset(8f,9f))
                    path(4f to 17f,10f to 12f,14f to 15f,17f to 12f,21f to 16f) }
                Glyph.VIDEO -> { box(3f,6f,12f,13f,2.5f); path(15f to 10f,21f to 7f,21f to 18f,15f to 15f) }
                Glyph.AUDIO -> { path(10f to 17f,10f to 5f,20f to 3f,20f to 15f)
                    line(10f,8f,20f,6f); drawOval(tint,Offset(4f,16f),Size(6f,4f),style=stroke)
                    drawOval(tint,Offset(14f,14f),Size(6f,4f),style=stroke) }
                Glyph.FILE -> { path(6f to 3f,14f to 3f,20f to 9f,20f to 21f,4f to 21f,4f to 3f,6f to 3f)
                    path(14f to 3f,14f to 9f,20f to 9f); line(8f,13f,16f,13f); line(8f,17f,13f,17f) }
                Glyph.MIC -> { box(9f,3f,6f,12f,3f); drawArc(tint,0f,180f,false,Offset(5f,5f),Size(14f,14f),style=stroke)
                    line(12f,19f,12f,22f); line(9f,22f,15f,22f) }
                Glyph.PLAY -> path(9f to 5f,19f to 12f,9f to 19f,9f to 5f)
                Glyph.PAUSE -> { line(8f,5f,8f,19f); line(16f,5f,16f,19f) }
                Glyph.STOP -> drawRoundRect(tint,Offset(6f,6f),Size(12f,12f),CornerRadius(3f))
                Glyph.REFRESH -> { drawArc(tint,40f,285f,false,Offset(4f,4f),Size(16f,16f),style=stroke)
                    path(15f to 3f,20f to 3f,20f to 8f) }
                Glyph.COMMAND -> { box(4f,4f,16f,16f,4f); line(14.5f,7f,9.5f,17f) }
                Glyph.EDIT -> { path(14f to 5f,19f to 10f,9f to 20f,4f to 21f,5f to 16f,17f to 4f,20f to 7f)
                    line(13f,6f,18f,11f) }
                Glyph.TRASH -> { box(6f,7f,12f,14f,2f); line(4f,7f,20f,7f); path(9f to 6f,9f to 3f,15f to 3f,15f to 6f)
                    line(10f,11f,10f,17f); line(14f,11f,14f,17f) }
                Glyph.CLOCK -> { drawCircle(tint,9f,Offset(12f,12f),style=stroke); path(12f to 6f,12f to 12f,16f to 14f) }
                Glyph.ERROR -> { drawCircle(tint,9f,Offset(12f,12f),style=stroke); line(12f,7f,12f,12f); drawCircle(tint,1f,Offset(12f,16f)) }
            }
        }
    }
}
