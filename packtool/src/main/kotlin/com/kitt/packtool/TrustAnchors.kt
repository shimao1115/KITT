package com.kitt.packtool

import java.security.PublicKey

enum class AnchorScope { TEST_ONLY, PRODUCTION;
    companion object {
        fun of(value: String): AnchorScope = when (value) {
            ReleaseFormat.SCOPE_TEST_ONLY -> TEST_ONLY
            ReleaseFormat.SCOPE_PRODUCTION -> PRODUCTION
            else -> throw PackRejection("publisher_scope", value)
        }
    }
    val scope get() = name
}

/** A caller-supplied trust anchor. This is the *only* source of publisher trust in D1: a pack can
 *  never nominate its own key, and a SHA-256 digest only proves integrity, never authorship. */
data class TrustAnchor(val keyId: String, val publicKey: PublicKey, val scope: AnchorScope)

/** Empty by default. An empty store accepts nothing, which is the production behaviour in D1. */
class TrustStore(anchors: List<TrustAnchor> = emptyList()) {
    private val byId: Map<String, TrustAnchor>

    init {
        anchors.forEach {
            if (!ReleaseFormat.KEY_ID.matches(it.keyId)) throw PackRejection("field_size", "key_id")
        }
        val duplicates = anchors.groupBy { it.keyId }.filterValues { group ->
            group.map { it.scope to it.publicKey.encoded.toList() }.distinct().size > 1
        }.keys
        if (duplicates.isNotEmpty()) throw PackRejection("ambiguous_anchor", duplicates.first())
        byId = anchors.associateBy { it.keyId }
    }

    /** The anchor must exist *and* be pinned to exactly the scope the pack declares. A TEST_ONLY
     *  anchor can therefore never authenticate a pack claiming production scope, or the reverse. */
    fun resolve(keyId: String, scope: String): TrustAnchor? =
        byId[keyId]?.takeIf { it.scope.scope == scope }

    val size get() = byId.size
    val isEmpty get() = byId.isEmpty()

    companion object {
        val EMPTY = TrustStore(emptyList())
    }
}
