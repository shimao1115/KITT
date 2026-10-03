package com.kitt.reader

import kotlinx.serialization.json.*

/** Metadata-free bounded image, held only by its single active request. Never print payloads. */
class ImageInput(val mime: String, private val base64: String) {
    init { require(mime in setOf("image/jpeg", "image/png", "image/webp")); require(base64.length in 1..2_000_000) }
    val dataUrl get() = "data:$mime;base64,$base64"
    override fun toString() = "ImageInput(redacted)"
}
class UnsupportedImage : Exception("所选 Provider 不支持看图，请在设置中选择支持图片的 Provider/模型。")

fun imageContent(text: String, image: ImageInput, responses: Boolean): JsonArray = buildJsonArray {
    add(buildJsonObject { put("type", if (responses) "input_text" else "text"); put("text", text) })
    add(buildJsonObject {
        put("type", if (responses) "input_image" else "image_url")
        if (responses) { put("image_url", image.dataUrl); put("detail", "auto") }
        else put("image_url", buildJsonObject { put("url", image.dataUrl); put("detail", "auto") })
    })
}
