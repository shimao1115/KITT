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
    // M1.2: legacy developer mode must never become the next ordinary journey's source.
    init { prefs.edit().remove("simulation").apply() }
    var credentialUnavailable = false; private set
    fun read(): ProviderConfig {
        val key = prefs.getString("credential", "").orEmpty()
        val plain = if (key.isBlank()) "" else runCatching { secrets.decrypt(key) }.getOrElse { credentialUnavailable = true; "" }
        return ProviderConfig(runCatching { ProviderKind.valueOf(prefs.getString("provider", "FAKE")!!) }.getOrDefault(ProviderKind.FAKE),
            prefs.getString("endpoint", "https://api.openai.com/v1").orEmpty(), prefs.getString("model", "gpt-4.1-mini").orEmpty(),
            prefs.getString("effort", "").orEmpty(), plain)
    }
    fun save(config: ProviderConfig, speechRate: Float, voiceName: String = this.voiceName): Result<Unit> = runCatching {
        val uri = URI(config.endpoint)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "请输入有效的 HTTPS API 地址。" }
        require(config.kind == ProviderKind.FAKE || config.model.isNotBlank()) { "请填写模型名称。" }
        require(config.kind in setOf(ProviderKind.FAKE, ProviderKind.CHATGPT) || config.apiKey.isNotBlank()) { "请填写 API key。" }
        require(config.kind == ProviderKind.CHATGPT || config.effort in setOf("", "low", "medium", "high")) { "不支持的思考强度。" }
        val encrypted = if (config.apiKey.isBlank()) "" else secrets.encrypt(config.apiKey)
        check(prefs.edit().putString("provider", config.kind.name).putString("endpoint", config.endpoint.trimEnd('/'))
            .putString("model", config.model.trim()).putString("effort", if (config.supportsEffort || config.kind == ProviderKind.CHATGPT) config.effort else "")
            .putString("credential", encrypted).putFloat("speech_rate", speechRate.coerceIn(0.5f, 1.5f))
            .putString("tts_voice", voiceName).commit()) { "暂时无法保存设置。" }
        credentialUnavailable = false
    }
    val speechRate get() = prefs.getFloat("speech_rate", 1.0f)
    val voiceName get() = prefs.getString("tts_voice", "").orEmpty()
    /** Optional explicit research-only API configuration; never auto-bills a second Provider. */
    fun readResearch(): ProviderConfig? {
        if (!prefs.getBoolean("separate_research", false)) return null
        val key = prefs.getString("research_credential", "").orEmpty()
        val plain = runCatching { secrets.decrypt(key) }.getOrElse { credentialUnavailable = true; "" }
        return ProviderConfig(ProviderKind.OPENAI, prefs.getString("research_endpoint", "https://api.openai.com/v1").orEmpty(),
            prefs.getString("research_model", "gpt-4.1-mini").orEmpty(), apiKey = plain)
    }
    fun validateResearch(config: ProviderConfig?): Result<Unit> = runCatching {
        if (config != null) {
            val uri = URI(config.endpoint)
            require(config.kind == ProviderKind.OPENAI && uri.scheme == "https" && !uri.host.isNullOrBlank() &&
                uri.userInfo == null && uri.query == null && uri.fragment == null && config.model.isNotBlank() && config.apiKey.isNotBlank()) {
                "独立研究需要有效的 HTTPS Responses 地址、模型和 API key。"
            }
        }
    }
    fun saveResearch(config: ProviderConfig?): Result<Unit> = runCatching {
        validateResearch(config).getOrThrow()
        val edit = prefs.edit().putBoolean("separate_research", config != null)
        if (config == null) edit.remove("research_endpoint").remove("research_model").remove("research_credential")
        else edit.putString("research_endpoint", config.endpoint.trimEnd('/')).putString("research_model", config.model.trim())
            .putString("research_credential", secrets.encrypt(config.apiKey))
        check(edit.commit()) { "暂时无法保存研究配置。" }
    }
    // One atomic encrypted record: host, registration, verified identity and rotating credentials.
    @Synchronized fun readChatGpt(): String? = prefs.getString("chatgpt", null)?.let {
        runCatching { secrets.decrypt(it) }.getOrElse { credentialUnavailable = true; null }
    }
    @Synchronized fun saveChatGpt(record: String) {
        check(prefs.edit().putString("chatgpt", secrets.encrypt(record)).commit()) { "暂时无法保存 ChatGPT 连接。" }
    }
    val acceleration get() = prefs.getFloat("acceleration", 1.0f).toDouble().coerceIn(1.0, 120.0)
    val simulationSpeed get() = prefs.getFloat("simulation_speed", 80.0f).toDouble().coerceIn(1.0, 200.0)
    fun developer(acceleration: Double, speedKmh: Double = simulationSpeed) {
        prefs.edit().putFloat("acceleration", acceleration.toFloat())
            .putFloat("simulation_speed", speedKmh.coerceIn(1.0, 200.0).toFloat()).apply()
    }
}
