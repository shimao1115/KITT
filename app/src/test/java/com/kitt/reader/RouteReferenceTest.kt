package com.kitt.reader

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
class RouteReferenceTest {
    private val image = ImageInput("image/jpeg", "aW1hZ2U=")
    private fun provider(block: suspend (DirectorRequest) -> String) = object : DirectorProvider {
        override val acceptsImages = true
        override suspend fun direct(request: DirectorRequest) = block(request)
    }
    @Test fun screenshotAnalyzedOnceThenOnlyHintReusedAndClearDropsIt() = runTest {
        var calls = 0; var loads = 0
        val reference = RouteReference(this) { provider {
            calls++; assertSame(image, it.image); assertTrue(it.systemConstitution.contains("GPS 永远优先"))
            DirectorResult(Action.SPEAK_NOW, "路线参考", "已读图", memoryUpdate = "新都→广汉→雎水；方向大致向北").json()
        } }
        reference.analyze { loads++; image }; runCurrent()
        assertEquals(1, calls); assertEquals(1, loads); assertFalse(reference.busy)
        val pipeline = ContextPipeline(); pipeline.routeHint = reference.beginJourney()
        val journey = Journey({ 1000000 }, TestVoice()); journey.start()
        repeat(20) { assertTrue(pipeline.card(journey, 1000000).contains("新都→广汉→雎水")) }
        assertEquals(1, calls); assertEquals("未询问", journey.destination)
        reference.clear(); pipeline.reset(); assertEquals("", reference.hint); assertEquals("", pipeline.routeHint)
    }
    @Test fun unsupportedNeverLoadsOrSilentlyDropsImage() = runTest {
        val reference = RouteReference(this) { FakeProvider() }; var loaded = false
        reference.analyze { loaded = true; image }; runCurrent()
        assertFalse(loaded); assertTrue(reference.notice.contains("不支持看图")); assertEquals("", reference.hint)
        assertTrue(runCatching { FakeProvider().direct(DirectorRequest(contextCard = "", image = image)) }.exceptionOrNull() is UnsupportedImage)
    }
    @Test fun startOrClearCancelsPendingAndLateHintNeverAppears() = runTest {
        val reference = RouteReference(backgroundScope) { provider {
            delay(10000); DirectorResult(Action.SPEAK_NOW, "参考", "确认", memoryUpdate = "旧图").json()
        } }
        reference.analyze { image }; runCurrent(); assertTrue(reference.busy)
        assertEquals("", reference.beginJourney()); advanceTimeBy(15000); runCurrent()
        assertEquals("", reference.hint); assertFalse(reference.busy)
        reference.analyze { image }; runCurrent(); reference.clear(); advanceTimeBy(15000); runCurrent()
        assertEquals("", reference.hint)
    }
    @Test fun timeoutAndMalformedOrModelRejectionAreExplicitWithoutRetries() = runTest {
        var calls = 0
        val reference = RouteReference(backgroundScope) { provider { calls++; delay(40000); "{}" } }
        reference.analyze { image }; runCurrent(); advanceTimeBy(35001); runCurrent()
        assertTrue(reference.notice.contains("超时")); assertEquals(1, calls)
        val failed = RouteReference(this) { provider { calls++; error("model rejects image") } }
        failed.analyze { image }; runCurrent(); assertTrue(failed.notice.contains("支持图片")); assertFalse(failed.busy)
        assertEquals(2, calls); assertEquals("", failed.hint)
    }
    @Test fun apiImagePayloadsUseNativeShapesAndTextRequestsHaveNoOldImage() {
        val request = DirectorRequest(contextCard = "GPS现场", image = image, userUtterance = "参考")
        val responses = ApiProvider(ProviderConfig(ProviderKind.OPENAI))
        val content = responses.payload(request).getValue("input").jsonArray.first().jsonObject.getValue("content").jsonArray
        assertEquals("input_image", content[1].jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(image.dataUrl, content[1].jsonObject["image_url"]!!.jsonPrimitive.content)
        assertFalse(responses.payload(request.copy(image = null)).toString().contains("data:image"))
        val compatible = ApiProvider(ProviderConfig(ProviderKind.COMPATIBLE)).payload(request)
        assertTrue(compatible.toString().contains("image_url")); assertFalse(compatible.toString().contains("input_image"))
        assertEquals("ImageInput(redacted)", image.toString())
        assertTrue(runCatching { ImageInput("text/plain", "a") }.isFailure)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatGptImagePayloadTest {
    @Test fun chatGptUsesExistingAuthorizationAdapterWithImagePartsAndNoStorage() {
        val account = ChatGptAccount(SettingsStore(RuntimeEnvironment.getApplication()))
        val provider = ChatGptProvider(account, ProviderConfig(ProviderKind.CHATGPT, model = "catalog-model"))
        val request = DirectorRequest(contextCard = "当前现场", image = ImageInput("image/jpeg", "aW1hZ2U="))
        val payload = provider.payload(request, ChatGptModel("catalog-model", "模型", emptyList()))
        assertFalse(payload.getValue("store").jsonPrimitive.boolean); assertTrue(payload.getValue("stream").jsonPrimitive.boolean)
        assertTrue(payload.toString().contains("input_image")); assertFalse(provider.payload(request.copy(image = null), ChatGptModel("catalog-model", "模型", emptyList())).toString().contains("data:image"))
    }
}
