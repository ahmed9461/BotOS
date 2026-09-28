package com.ahmed9461.botos.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.util.JsonReader
import android.util.JsonToken
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.ahmed9461.botos.model.MediaInfo
import com.ahmed9461.botos.model.MediaKind
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.StringReader
import java.util.zip.GZIPInputStream
import org.json.JSONObject

internal sealed interface DecodedMedia {
    class Picture(val bitmap: Bitmap, val file: File, val animated: Boolean = false) : DecodedMedia
    class Sticker(val composition: LottieComposition) : DecodedMedia
    class Playback(val file: File, val mimeType: String, val mutedLoop: Boolean = false) : DecodedMedia
}

/** No URI from a message can become a filesystem/network data source. */
internal object MediaDecoder {
    const val AUTO_BYTES = 2L * 1024 * 1024
    const val IMAGE_BYTES = 16L * 1024 * 1024
    const val FILE_BYTES = 256L * 1024 * 1024
    const val TGS_BYTES = 1024 * 1024
    const val TGS_JSON_BYTES = 2 * 1024 * 1024
    const val THUMB_SIDE = 768

    fun supported(info: MediaInfo): Boolean = info.kind in setOf(
        MediaKind.PHOTO, MediaKind.VIDEO, MediaKind.ANIMATION, MediaKind.AUDIO, MediaKind.VOICE_NOTE,
    ) && (info.fileId ?: 0) > 0

    fun limit(info: MediaInfo): Long = when {
        info.mimeType == "application/x-tgsticker" -> TGS_BYTES.toLong()
        info.kind == MediaKind.PHOTO || info.mimeType.startsWith("image/") -> IMAGE_BYTES
        else -> FILE_BYTES
    }

    fun validatedFile(root: File, path: String, maxBytes: Long): File {
        require(maxBytes in 1..FILE_BYTES && path.startsWith('/') && path.none(Char::isISOControl))
        val file = File(path).canonicalFile
        val parent = root.canonicalFile.toPath()
        require(file.toPath().startsWith(parent) && file.toPath() != parent && file.isFile)
        require(file.length() in 1..maxBytes)
        return file
    }

    /** Call only on a bounded IO executor. Lottie receives a validated JSON string, never a URL. */
    fun decode(root: File, path: String, info: MediaInfo): DecodedMedia {
        require(supported(info))
        val file = validatedFile(root, path, limit(info))
        return when {
            info.mimeType == "application/x-tgsticker" -> DecodedMedia.Sticker(decodeTgs(file))
            info.kind == MediaKind.PHOTO || info.mimeType.startsWith("image/") ->
                DecodedMedia.Picture(picture(file, THUMB_SIDE), file, info.kind == MediaKind.ANIMATION)
            else -> DecodedMedia.Playback(file, info.mimeType, info.kind == MediaKind.ANIMATION)
        }
    }

    fun picture(file: File, maximumSide: Int): Bitmap {
        require(maximumSide in 1..1600 && file.length() in 1..IMAGE_BYTES)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        require(options.outWidth in 1..32768 && options.outHeight in 1..32768)
        require(options.outWidth.toLong() * options.outHeight <= 100_000_000L)
        return if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, header, _ ->
                val width = header.size.width
                val height = header.size.height
                require(width in 1..32768 && height in 1..32768 && width.toLong() * height <= 100_000_000L)
                val ratio = minOf(1.0, maximumSide.toDouble() / maxOf(width, height))
                decoder.setTargetSize((width * ratio).toInt().coerceAtLeast(1), (height * ratio).toInt().coerceAtLeast(1))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            var sample = 1
            while (options.outWidth / sample > maximumSide || options.outHeight / sample > maximumSide) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: error("Image unavailable")
        }
    }

    fun decodeTgs(file: File): LottieComposition {
        require(file.length() in 1..TGS_BYTES.toLong())
        val bytes = GZIPInputStream(file.inputStream()).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= TGS_JSON_BYTES)
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        val json = bytes.toString(Charsets.UTF_8)
        validateTgsJson(json)
        return LottieCompositionFactory.fromJsonStringSync(json, null).value ?: error("Sticker unavailable")
    }

    /** Complexity limits precede both JSONObject and the animation parser. */
    fun validateTgsJson(json: String) {
        require(json.toByteArray(Charsets.UTF_8).size <= TGS_JSON_BYTES)
        var remaining = 100_000
        JsonReader(StringReader(json)).use { reader ->
            fun visit(depth: Int) {
                require(depth < 32 && remaining-- > 0)
                when (reader.peek()) {
                    JsonToken.BEGIN_OBJECT -> {
                        reader.beginObject()
                        while (reader.hasNext()) { reader.nextName(); visit(depth + 1) }
                        reader.endObject()
                    }
                    JsonToken.BEGIN_ARRAY -> {
                        reader.beginArray(); while (reader.hasNext()) visit(depth + 1); reader.endArray()
                    }
                    JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
                    JsonToken.BOOLEAN -> reader.nextBoolean()
                    JsonToken.NULL -> reader.nextNull()
                    else -> error("Invalid sticker")
                }
            }
            visit(0)
            require(reader.peek() == JsonToken.END_DOCUMENT)
        }
        val root = JSONObject(json)
        require(root.optInt("w") in 1..1024 && root.optInt("h") in 1..1024)
        val fps = root.optDouble("fr")
        val first = root.optDouble("ip")
        val last = root.optDouble("op")
        require(fps.isFinite() && first.isFinite() && last.isFinite() && fps in 1.0..120.0)
        require(last > first && (last - first) / fps <= 30.0)
        require((root.optJSONArray("layers")?.length() ?: 0) <= 512)
        // TGS vector stickers need no image/font assets. Do not let an asset select a local path.
        require(!root.has("fonts") && !root.has("chars"))
        val assets = root.optJSONArray("assets")
        require((assets?.length() ?: 0) <= 128)
        val assetMap = mutableMapOf<String, JSONObject>()
        if (assets != null) for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            require(!asset.has("p") && !asset.has("u"))
            require((asset.optJSONArray("layers")?.length() ?: 0) <= 512)
            val id = asset.optString("id")
            require(id.isNotEmpty() && assetMap.put(id, asset) == null)
        }
        // Reject recursive precompositions and image/text layers before attaching to a Drawable.
        val visited = mutableSetOf<String>()
        fun layers(item: JSONObject, ancestors: Set<String>, depth: Int) {
            require(depth <= 16)
            val entries = item.optJSONArray("layers") ?: return
            for (i in 0 until entries.length()) {
                val layer = entries.getJSONObject(i)
                require(layer.optInt("ty", -1) !in setOf(2, 5))
                if (layer.optInt("ty", -1) == 0) {
                    val id = layer.optString("refId")
                    require(id !in ancestors)
                    val asset = assetMap[id] ?: error("Unknown precomposition")
                    if (id !in visited) { layers(asset, ancestors + id, depth + 1); visited.add(id) }
                }
            }
        }
        layers(root, emptySet(), 0)
        assetMap.forEach { (id, asset) -> if (id !in visited) layers(asset, setOf(id), 0) }
    }
}
