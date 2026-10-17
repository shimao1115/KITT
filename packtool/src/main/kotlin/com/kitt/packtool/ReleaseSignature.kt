package com.kitt.packtool

import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Standard JCA only: SHA256withRSA (PKCS#1 v1.5). Deterministic for a fixed key, available on
 *  JDK 17 and on Android API 26+, and it needs no provider beyond the platform one. */
object ReleaseSignature {
    fun sign(region: ByteArray, privateKey: PrivateKey): ByteArray =
        Signature.getInstance(ReleaseFormat.SIGNATURE_ALGORITHM).run {
            initSign(privateKey)
            update(region)
            sign()
        }

    fun verify(region: ByteArray, publicKey: PublicKey, signature: ByteArray): Boolean = runCatching {
        Signature.getInstance(ReleaseFormat.SIGNATURE_ALGORITHM).run {
            initVerify(publicKey)
            update(region)
            verify(signature)
        }
    }.getOrDefault(false)
}

/** PEM helpers. Used only by local tooling/tests; no key material is ever read from a pack. */
object Pem {
    fun publicKey(pem: String): PublicKey = KeyFactory.getInstance("RSA")
        .generatePublic(X509EncodedKeySpec(body(pem, "PUBLIC KEY")))

    fun privateKey(pem: String): PrivateKey = KeyFactory.getInstance("RSA")
        .generatePrivate(PKCS8EncodedKeySpec(body(pem, "PRIVATE KEY")))

    fun publicPem(key: PublicKey): String = wrap(key.encoded, "PUBLIC KEY")
    fun privatePem(key: PrivateKey): String = wrap(key.encoded, "PRIVATE KEY")

    private fun body(pem: String, label: String): ByteArray {
        val begin = "-----BEGIN $label-----"
        val end = "-----END $label-----"
        val start = pem.indexOf(begin)
        val stop = pem.indexOf(end)
        if (start < 0 || stop < 0 || stop < start) throw PackRejection("key_material", "pem")
        val base64 = pem.substring(start + begin.length, stop).replace(Regex("\\s"), "")
        return Base64.getDecoder().decode(base64)
    }

    private fun wrap(encoded: ByteArray, label: String): String {
        val base64 = Base64.getEncoder().encodeToString(encoded)
        return "-----BEGIN $label-----\n" + base64.chunked(64).joinToString("\n") + "\n-----END $label-----\n"
    }
}
