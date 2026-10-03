package com.kitt.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max

object AndroidImageReader {
    /** Run on IO. Bounds source/decode/output; re-encoding removes location/EXIF metadata. */
    fun read(context: Context, uri: Uri): ImageInput {
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer); if (count < 0) break
                require(out.size() + count <= 16_000_000) { "图片太大，请选择小于 16 MB 的图片。" }
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } ?: error("图片无法读取，请重新选择。")
        // Reject non-image streams even on platforms whose decoder is permissive.
        val prefix = raw.take(12).toByteArray()
        val signature = prefix.toString(Charsets.ISO_8859_1)
        require((raw.size >= 3 && raw[0] == 0xff.toByte() && raw[1] == 0xd8.toByte() && raw[2] == 0xff.toByte()) ||
            signature.startsWith("\u0089PNG\r\n\u001a\n") || signature.startsWith("GIF87a") || signature.startsWith("GIF89a") ||
            (signature.startsWith("RIFF") && signature.endsWith("WEBP")) ||
            (prefix.size >= 12 && signature.substring(4, 8) == "ftyp")) { "请选择普通照片或截图。" }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "无法识别图片格式，请选择普通照片或截图。" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1536) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("图片无法解码。")
        var bitmap = decoded
        try {
            val orientation = runCatching { ExifInterface(ByteArrayInputStream(raw)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f)
                    3 -> setRotate(180f)
                    4 -> { setRotate(180f); postScale(-1f, 1f) }
                    5 -> { setRotate(90f); postScale(-1f, 1f) }
                    6 -> setRotate(90f)
                    7 -> { setRotate(270f); postScale(-1f, 1f) }
                    8 -> setRotate(270f)
                }
            }
            if (!matrix.isIdentity) bitmap = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            val out = ByteArrayOutputStream()
            require(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out))
            require(out.size() <= 1_500_000) { "图片仍然太大，请选择较小图片。" }
            return ImageInput("image/jpeg", Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
        } finally { if (bitmap !== decoded) bitmap.recycle(); decoded.recycle() }
    }
}
