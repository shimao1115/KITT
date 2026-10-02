package com.kitt.reader

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.Base64
import java.net.URI

interface SecretCipher { fun encrypt(text: String): String; fun decrypt(text: String): String }
class AndroidKeyCipher : SecretCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    override fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }
    override fun decrypt(text: String): String {
        val data = Base64.getDecoder().decode(text); require(data.size >= 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, 12))) }
        return cipher.doFinal(data.copyOfRange(12, data.size)).toString(Charsets.UTF_8)
    }
    companion object { private const val ALIAS = "com.kitt.reader.provider-key" }
}
class SettingsStore(context: Context, private val secrets: SecretCipher = AndroidKeyCipher()) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    var credentialUnavailable = false; private set
    fun read(): ProviderConfig {
        val key = prefs.getString("credential", "").orEmpty()
        val plain = if (key.isBlank()) "" else runCatching { secrets.decrypt(key) }.getOrElse { credentialUnavailable = true; "" }
        return ProviderConfig(runCatching { ProviderKind.valueOf(prefs.getString("provider", "FAKE")!!) }.getOrDefault(ProviderKind.FAKE),
            prefs.getString("endpoint", "https://api.openai.com/v1").orEmpty(), prefs.getString("model", "gpt-4.1-mini").orEmpty(),
            prefs.getString("effort", "").orEmpty(), plain)
    }
    fun save(config: ProviderConfig, speechRate: Float): Result<Unit> = runCatching {
        val uri = URI(config.endpoint)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "请输入有效的 HTTPS API 地址。" }
        require(config.kind == ProviderKind.FAKE || config.model.isNotBlank()) { "请填写模型名称。" }
        require(config.kind == ProviderKind.FAKE || config.apiKey.isNotBlank()) { "请填写 API key。" }
        require(config.effort in setOf("", "low", "medium", "high")) { "不支持的思考强度。" }
        val encrypted = if (config.apiKey.isBlank()) "" else secrets.encrypt(config.apiKey)
        check(prefs.edit().putString("provider", config.kind.name).putString("endpoint", config.endpoint.trimEnd('/'))
            .putString("model", config.model.trim()).putString("effort", if (config.supportsEffort) config.effort else "")
            .putString("credential", encrypted).putFloat("speech_rate", speechRate.coerceIn(0.5f, 1.5f)).commit()) { "暂时无法保存设置。" }
        credentialUnavailable = false
    }
    val speechRate get() = prefs.getFloat("speech_rate", 1.0f)
    val simulation get() = prefs.getBoolean("simulation", false)
    val acceleration get() = prefs.getFloat("acceleration", 1.0f).toDouble().coerceIn(1.0, 120.0)
    fun developer(simulation: Boolean, acceleration: Double) {
        prefs.edit().putBoolean("simulation", simulation).putFloat("acceleration", acceleration.toFloat()).apply()
    }
}
