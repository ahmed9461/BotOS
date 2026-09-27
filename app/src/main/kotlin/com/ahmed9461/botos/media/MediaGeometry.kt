package com.ahmed9461.botos.media

/** Pixel/dp agnostic geometry. Never distort or clamp the source aspect ratio. */
internal data class MediaExtent(val width: Float, val height: Float)
internal fun fitMedia(width: Int?, height: Int?, maxWidth: Float, maxHeight: Float): MediaExtent {
    require(maxWidth.isFinite() && maxHeight.isFinite() && maxWidth >= 0f && maxHeight >= 0f)
    if (maxWidth == 0f || maxHeight == 0f) return MediaExtent(0f, 0f)
    val known = width != null && height != null && width > 0 && height > 0
    val w = if (known) width!!.toDouble() else 4.0
    val h = if (known) height!!.toDouble() else 3.0
    val factor = minOf(maxWidth / w, maxHeight / h)
    return MediaExtent((w * factor).toFloat(), (h * factor).toFloat())
}
internal fun mediaPanLimit(content: Float, viewport: Float, scale: Float): Float =
    ((content * scale - viewport) / 2f).coerceAtLeast(0f)
