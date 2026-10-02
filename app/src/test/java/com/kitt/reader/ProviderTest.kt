package com.kitt.reader

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ProviderTest {
    @Test fun fakeHonorsContract() = runTest {
        val fake = FakeProvider()
        assertEquals(Action.ASK_USER, DirectorContract.parse(fake.direct(DirectorRequest(contextCard = "目的地：未询问"))).action)
        assertEquals(Action.SPEAK_NOW, DirectorContract.parse(fake.direct(DirectorRequest(contextCard = "目的地：绵阳"))).action)
        assertEquals(Action.SILENT, DirectorContract.parse(fake.direct(DirectorRequest(contextCard = "最近讲过：平地上的道路"))).action)
        assertEquals(Action.SPEAK_NOW, DirectorContract.parse(fake.direct(DirectorRequest(contextCard = "", userUtterance = "再讲一点"))).action)
    }
    @Test fun strictSchemaAndActionSemantics() {
        val valid = listOf(DirectorResult(Action.SILENT), DirectorResult(Action.ASK_USER, question = "去哪儿？"),
            DirectorResult(Action.PREPARE, topic = "目标", prepareHint = "接近后重新确认"),
            DirectorResult(Action.SPEAK_NOW, topic = "题", narration = "内容"))
        valid.forEach { assertEquals(it, DirectorContract.parse(it.json())) }
        listOf("{}", "[]", "```json {} ```", valid[0].json().replace("SILENT", "OTHER"),
            valid[0].json().replace("\"topic\":\"\"", "\"topic\":1"),
            DirectorResult(Action.SPEAK_NOW).json(), DirectorResult(Action.PREPARE, "x", "old", prepareHint = "hint").json(),
            valid[0].json().dropLast(1) + ",\"extra\":\"\"}").forEach { raw ->
            assertTrue("Must reject $raw", runCatching { DirectorContract.parse(raw) }.isFailure)
        }
    }
    @Test fun apiBuildsThreeLayersAndExtractsValidatedResponse() = runTest {
        val result = DirectorResult(Action.SILENT).json()
        val config = ProviderConfig(ProviderKind.OPENAI, model = "gpt-5-mini", effort = "low", apiKey = "test")
        val provider = ApiProvider(config, JsonTransport { url, key, body ->
            assertTrue(url.endsWith("/responses")); assertEquals("test", key)
            assertTrue(body.contains("session preference") && body.contains("现场") && body.contains("low"))
            """{"status":"completed","output":[{"content":[{"type":"output_text","text":${kotlinx.serialization.json.JsonPrimitive(result)}}]}]}"""
        })
        assertEquals(result, provider.direct(DirectorRequest(sessionInstructions = "session preference", contextCard = "现场")))
        assertFalse(ApiProvider(config.copy(model = "gpt-4.1-mini")).payload(DirectorRequest(contextCard = "")).containsKey("reasoning"))
    }
}
