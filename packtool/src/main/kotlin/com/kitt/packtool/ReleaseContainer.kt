package com.kitt.packtool

import java.nio.ByteBuffer

/** The signed region of a container: magic, format version, manifest and the four content blocks.
 *  The signature section is appended after it, so the signature never covers itself. */
data class SignedRegion(val bytes: ByteArray, val manifestBytes: ByteArray, val blocks: List<ByteArray>)

data class DecodedContainer(
    val region: SignedRegion,
    val algorithm: String,
    val keyId: String,
    val signature: ByteArray
)

/** Fixed-frame codec. Lengths are big-endian and bounded before allocation, so a hostile header can
 *  neither overflow nor trigger a huge allocation. */
object ReleaseContainer {
    private const val HEADER = 13 // magic 8 + version 1 + manifest length 4

    fun unsignedRegion(manifestBytes: ByteArray, blocks: List<ByteArray>): ByteArray {
        if (manifestBytes.size !in 1..ReleaseFormat.MAX_MANIFEST) throw PackRejection("manifest_size")
        if (blocks.size != BlockKind.ORDER.size) throw PackRejection("block_count")
        val total = HEADER + manifestBytes.size + blocks.sumOf { 5 + it.size }
        if (total > ReleaseFormat.MAX_CONTAINER) throw PackRejection("container_too_large")
        val buffer = ByteBuffer.allocate(total)
        buffer.put(ReleaseFormat.MAGIC.encodeToByteArray())
        buffer.put(ReleaseFormat.FORMAT_VERSION.toByte())
        buffer.putInt(manifestBytes.size)
        buffer.put(manifestBytes)
        blocks.forEachIndexed { index, block ->
            if (block.size !in 1..ReleaseFormat.MAX_BLOCK) throw PackRejection("block_size", BlockKind.name(BlockKind.ORDER[index]))
            buffer.put(BlockKind.ORDER[index].toByte())
            buffer.putInt(block.size)
            buffer.put(block)
        }
        return buffer.array()
    }

    fun assemble(region: SignedRegion, algorithm: String, keyId: String, signature: ByteArray): ByteArray {
        if (algorithm != ReleaseFormat.SIGNATURE_ALGORITHM) throw PackRejection("signature_algorithm")
        if (algorithm.length > ReleaseFormat.MAX_ALGORITHM) throw PackRejection("signature_algorithm")
        if (!ReleaseFormat.KEY_ID.matches(keyId)) throw PackRejection("field_size", "key_id")
        if (signature.size !in 1..ReleaseFormat.MAX_SIGNATURE) throw PackRejection("signature_size")
        val section = 1 + algorithm.length + 1 + keyId.length + 2 + signature.size
        if (region.bytes.size + section > ReleaseFormat.MAX_CONTAINER) throw PackRejection("container_too_large")
        return ByteBuffer.allocate(region.bytes.size + section).apply {
            put(region.bytes)
            put(algorithm.length.toByte()); put(algorithm.encodeToByteArray())
            put(keyId.length.toByte()); put(keyId.encodeToByteArray())
            putShort(signature.size.toShort()); put(signature)
        }.array()
    }

    fun decode(bytes: ByteArray): DecodedContainer {
        if (bytes.size > ReleaseFormat.MAX_CONTAINER) throw PackRejection("container_too_large")
        if (bytes.size < HEADER + 1) throw PackRejection("container_truncated")
        val buffer = ByteBuffer.wrap(bytes)
        val magic = ByteArray(8).also { buffer.get(it) }
        if (String(magic, Charsets.US_ASCII) != ReleaseFormat.MAGIC) throw PackRejection("magic")
        val version = buffer.get().toInt() and 0xff
        if (version != ReleaseFormat.FORMAT_VERSION) throw PackRejection("format_version", "$version")
        val manifestLength = buffer.int
        if (manifestLength !in 1..ReleaseFormat.MAX_MANIFEST || manifestLength > buffer.remaining()) {
            throw PackRejection("manifest_size")
        }
        val manifestBytes = ByteArray(manifestLength).also { buffer.get(it) }
        val seen = mutableListOf<Int>()
        val blocks = mutableListOf<ByteArray>()
        BlockKind.ORDER.forEach { expected ->
            if (buffer.remaining() < 5) throw PackRejection("missing_block", BlockKind.name(expected))
            val kind = buffer.get().toInt() and 0xff
            if (kind !in BlockKind.ORDER) throw PackRejection("unknown_block", "$kind")
            if (kind != expected) {
                throw PackRejection(if (kind in seen) "duplicate_block" else "block_kind", BlockKind.name(kind))
            }
            seen += kind
            val length = buffer.int
            if (length !in 1..ReleaseFormat.MAX_BLOCK || length > buffer.remaining()) throw PackRejection("block_size")
            blocks += ByteArray(length).also { buffer.get(it) }
        }
        val algorithm = buffer.sized(ReleaseFormat.MAX_ALGORITHM, "signature_section").decodeToString()
        if (algorithm != ReleaseFormat.SIGNATURE_ALGORITHM) throw PackRejection("signature_algorithm", algorithm)
        val keyId = buffer.sized(ReleaseFormat.MAX_KEY_ID, "signature_section").decodeToString()
        if (!ReleaseFormat.KEY_ID.matches(keyId)) throw PackRejection("field_size", "key_id")
        if (buffer.remaining() < 2) throw PackRejection("signature_section")
        val signatureLength = buffer.short.toInt() and 0xffff
        if (signatureLength !in 1..ReleaseFormat.MAX_SIGNATURE || signatureLength > buffer.remaining()) {
            throw PackRejection("signature_size")
        }
        val signature = ByteArray(signatureLength).also { buffer.get(it) }
        if (buffer.hasRemaining()) throw PackRejection("trailing_bytes", "${buffer.remaining()}")
        val region = SignedRegion(bytes.copyOf(bytes.size - (4 + algorithm.length + keyId.length + signature.size)),
            manifestBytes, blocks)
        return DecodedContainer(region, algorithm, keyId, signature)
    }

    /** One-byte length prefix, bounded before allocation. */
    private fun ByteBuffer.sized(max: Int, code: String): ByteArray {
        if (remaining() < 1) throw PackRejection(code)
        val length = get().toInt() and 0xff
        if (length > max || length > remaining()) throw PackRejection(code)
        return ByteArray(length).also { get(it) }
    }
}
