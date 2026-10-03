package com.kitt.reader

import android.content.Context
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsTest {
    // Android Keystore is a device boundary; exercise the identical authenticated cipher layout using a JVM key.
    private fun cipher(): SecretCipher = object : SecretCipher {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        override fun encrypt(text: String): String {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
            return Base64.getEncoder().encodeToString(c.iv + c.doFinal(text.toByteArray()))
        }
        override fun decrypt(text: String): String {
            val bytes = Base64.getDecoder().decode(text)
            return Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            }.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        }
    }
    @Test fun settingsRoundtripKeepsKeyEncryptedAndOmitsUnsupportedEffort() {
        val app = RuntimeEnvironment.getApplication(); val store = SettingsStore(app, cipher())
        val config = ProviderConfig(ProviderKind.COMPATIBLE, "https://example.com/v1", "user-model", "low", "example-secret")
        assertTrue(store.save(config, 1.2f).isSuccess)
        assertEquals(config.copy(effort = ""), store.read()); assertEquals(1.2f, store.speechRate, 0.01f)
        val raw = app.getSharedPreferences("settings", Context.MODE_PRIVATE).all.toString()
        assertFalse(raw.contains("example-secret")); assertTrue(raw.contains("credential"))
        store.developer(60.0); assertEquals(60.0, store.acceleration, 0.01)
        store.developer(16.0, 100.0); assertEquals(100.0, store.simulationSpeed, 0.01)
        assertTrue(store.save(config.copy(endpoint = "http://example.com"), 1.0f).isFailure)
        assertTrue(store.save(config.copy(apiKey = ""), 1.0f).isFailure)
        assertTrue(store.save(config.copy(endpoint = "https://user:password@example.com"), 1.0f).isFailure)
    }
    @Test fun lostCredentialFailsClosedAndFakeNeedsNoKey() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("credential", "broken").commit()
        val store = SettingsStore(app, cipher()); assertEquals("", store.read().apiKey); assertTrue(store.credentialUnavailable)
        assertTrue(store.save(ProviderConfig(), 1.0f).isSuccess); assertFalse(store.credentialUnavailable)
    }
    @Test fun researchConfigIsExplicitEncryptedAndIndependentFromNarrationProvider() {
        val app = RuntimeEnvironment.getApplication(); val store = SettingsStore(app, cipher())
        assertNull(store.readResearch())
        assertTrue(store.save(ProviderConfig(), 1.0f).isSuccess)
        val research = ProviderConfig(ProviderKind.OPENAI, model = "research-model", apiKey = "search-secret")
        assertTrue(store.saveResearch(research).isSuccess); assertEquals(research, store.readResearch())
        assertEquals(ProviderKind.FAKE, store.read().kind)
        assertFalse(app.getSharedPreferences("settings", Context.MODE_PRIVATE).all.toString().contains("search-secret"))
        assertTrue(store.saveResearch(research.copy(kind = ProviderKind.COMPATIBLE)).isFailure)
        assertTrue(store.saveResearch(research.copy(apiKey = "")).isFailure)
        assertTrue(store.saveResearch(null).isSuccess); assertNull(store.readResearch())
        assertFalse(app.getSharedPreferences("settings", Context.MODE_PRIVATE).all.keys.any { it.startsWith("research_") })
    }
}
