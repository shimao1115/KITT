package com.kitt.reader.d1

import com.kitt.packtool.AnchorScope
import com.kitt.packtool.ContentDigest
import com.kitt.packtool.CountyBlocks
import com.kitt.packtool.PackBuilder
import com.kitt.packtool.Pem
import com.kitt.packtool.ReleaseContainer
import com.kitt.packtool.ReleaseManifest
import com.kitt.packtool.ReleaseVerifier
import com.kitt.packtool.ReleaseVerdict
import com.kitt.packtool.TrustAnchor
import com.kitt.packtool.TrustStore
import com.kitt.reader.countyRights
import kotlinx.serialization.json.*
import java.io.File
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import kotlin.system.exitProcess

/** D1 command line. Only TEST_ONLY material ever passes through it: no production key, no tag,
 *  no release, no device and no network.
 *
 *  Build :  ./gradlew :app:d1Pack -Pd1.command=build -Pd1.sample=fact-only \
 *               -Pd1.keydir=$TEMP/kitt-d1-keys -Pd1.out=$TEMP/kitt-d1-out -Pd1.commit=<40 hex>
 *  Verify:  ./gradlew :app:d1Pack -Pd1.command=verify \
 *               -Pd1.pack=$TEMP/kitt-d1-out/pack.kcpk -Pd1.keydir=$TEMP/kitt-d1-keys
 */
fun main(args: Array<String>) {
    val code = D1Cli.run(args.toList())
    println("exit_code=$code")
    exitProcess(code)
}

object D1Cli {
    const val ACCEPTED = 0
    const val USAGE_ERROR = 2
    const val REJECTED = 3

    private const val USAGE = """usage:
  --command keygen   --keydir <dir> [--root <repo>] [--keyid <id>]
  --command sample   --root <repo>
  --command build    --root <repo> --sample <dir> --keydir <dir> --out <dir> --commit <40 hex> [--appcode 20] [--sequence 1]
  --command prepare --root <repo> --input <repo-relative-dir> --out <NEW dir> --commit <40 hex> [--keyid <TEST_ONLY id>]
  --command release-preview --pack <file> --keydir <public-only-dir> --area <id> --commit <40 hex> --factversion <version> --narrativeversion <version> --sequence <n> --json <NEW file>
  --command verify   --pack <file> --keydir <dir> [--appcode 20] [--minsequence 1] [--json <file>]
  --command tamper   --pack <file> --out <file> --offset <int>
  --command relabel  --pack <file> --out <file> --keydir <dir> --area <cn-nnnnnn> --parent <cn-nnnn00>
  --command audit-newdu --root <repo>"""

    fun run(arguments: List<String>): Int = try {
        val options = Options(arguments)
        when (val command = options.required("command")) {
            "keygen" -> keygen(options)
            "sample" -> sample(options)
            "build" -> build(options)
            "prepare" -> D2Local.prepare(options)
            "release-preview" -> D2Local.preview(options)
            "release-dry-run" -> D2Local.releaseDryRun(options)
            "verify" -> verify(options)
            "tamper" -> tamper(options)
            "relabel" -> relabel(options)
            "audit-newdu" -> auditNewdu(options)
            else -> {
                println("status=USAGE_ERROR reason=unknown_command detail=$command")
                USAGE_ERROR
            }
        }
    } catch (error: com.kitt.packtool.PackRejection) {
        println("status=REJECTED")
        println("code=${error.code}")
        if (error.detail.matches(Regex("[a-z0-9_,.:-]{1,512}"))) println("detail=${error.detail}")
        REJECTED
    } catch (error: IllegalArgumentException) {
        println("status=USAGE_ERROR reason=arguments")
        println(USAGE)
        USAGE_ERROR
    } catch (error: Exception) {
        println("status=ERROR reason=${error.javaClass.simpleName}")
        USAGE_ERROR
    }

    /** A one-off RSA test key. It is written outside the work tree, never printed, never committed. */
    private fun keygen(options: Options): Int {
        val directory = File(options.required("keydir")).canonicalFile
        val root = File(options.optional("root") ?: ".").canonicalFile
        D2Local.requireExternalKey(directory, root)
        if (directory.toPath().startsWith(root.toPath())) {
            println("status=REFUSED reason=keydir_inside_worktree")
            return USAGE_ERROR
        }
        val privateFile = File(directory, "private.pem")
        if (directory.exists()) {
            println("status=REFUSED reason=keydir_exists")
            return USAGE_ERROR
        }
        directory.mkdirs()
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        privateFile.writeText(Pem.privatePem(pair.private))
        File(directory, "public.pem").writeText(Pem.publicPem(pair.public))
        val keyId = options.optional("keyid") ?: "test-only-d1-local"
        val metadata = buildJsonObject {
            put("key_id", keyId)
            put("scope", "TEST_ONLY")
            put("algorithm", "SHA256withRSA")
            put("bits", 3072)
            put("public_sha256", ContentDigest.sha256(pair.public.encoded))
            put("note", "TEST_ONLY D1 key. Not a publisher root; never reuse for releases or APKs.")
        }
        File(directory, "key.json").writeText(Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), metadata))
        println("status=KEY_CREATED")
        println("key_id=$keyId")
        println("scope=TEST_ONLY")
        println("public_sha256=${ContentDigest.sha256(pair.public.encoded)}")
        return ACCEPTED
    }

    /** Re-renders the committed TEST_ONLY sample so drift is detectable instead of assumed. */
    private fun sample(options: Options): Int {
        val root = File(options.required("root")).absoluteFile
        D1Sample.render().forEach { (path, bytes) ->
            val target = File(root, path)
            target.parentFile?.mkdirs()
            target.writeBytes(bytes)
            println("sample_file=$path bytes=${bytes.size} sha256=${ContentDigest.sha256(bytes)}")
        }
        println("status=SAMPLE_WRITTEN")
        return ACCEPTED
    }

    private fun build(options: Options): Int {
        val root = File(options.required("root")).absoluteFile
        val sample = options.optional("input") ?: options.required("sample")
        val output = File(options.required("out")).absoluteFile
        if (output.exists()) throw com.kitt.packtool.PackRejection("output_exists")
        val keyDirectory = File(options.required("keydir")).absoluteFile
        val commit = options.required("commit")
        val appCode = options.optional("appcode")?.toInt() ?: 20
        val sequence = options.optional("sequence")?.toInt() ?: 1
        if ((options.optional("scope") ?: "TEST_ONLY") != "TEST_ONLY") {
            throw com.kitt.packtool.PackRejection("production_not_authorized")
        }
        D2Local.requireExternalKey(keyDirectory, root)
        val key = readKey(keyDirectory, privateRequired = true)
        val privateKey = key.privateKey ?: run {
            println("status=REFUSED reason=missing_private_key")
            return USAGE_ERROR
        }
        val (factBytes, narrativeBytes) = D2Local.input(options)
        val areaId = Json.parseToJsonElement(factBytes.decodeToString()).jsonObject.getValue("area_id").jsonPrimitive.content
        val prepared = PackBuilder.prepare(areaId, factBytes, narrativeBytes, key.keyId, key.scope,
            commit, appCode, sequence)
        D2Local.contentCheck(prepared)
        val built = PackBuilder.build(areaId, factBytes, narrativeBytes, key.keyId, key.scope,
            commit, appCode, privateKey, sequence)
        java.nio.file.Files.createDirectories(output.toPath().parent)
        java.nio.file.Files.createDirectory(output.toPath())
        val packFile = File(output, "pack.kcpk")
        packFile.writeBytes(built.container)
        val summary = buildJsonObject {
            put("command", "build")
            put("sample", sample)
            put("area_id", built.manifest.areaId)
            put("parent_id", built.manifest.parentId)
            put("content_kind", built.manifest.contentKind)
            put("key_id", key.keyId)
            put("publisher_scope", key.scope)
            put("source_commit", commit)
            put("minimum_code", appCode)
            put("sequence", built.manifest.sequence)
            put("unsigned_sha256", built.unsignedDigest)
            put("container_sha256", ContentDigest.sha256(built.container))
            put("container_bytes", built.container.size)
            put("pack_file", "pack.kcpk")
            put("blocks", JsonArray(built.manifest.blocks.map { block ->
                JsonObject(linkedMapOf("kind" to JsonPrimitive(block.kind), "bytes" to JsonPrimitive(block.bytes),
                    "sha256" to JsonPrimitive(block.sha256)))
            }))
        }
        File(output, "build.json").writeText(Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), summary))
        println("status=BUILT_TEST_ONLY")
        println("area_id=${built.manifest.areaId}")
        println("content_kind=${built.manifest.contentKind}")
        println("sequence=${built.manifest.sequence}")
        println("unsigned_sha256=${built.unsignedDigest}")
        println("container_sha256=${ContentDigest.sha256(built.container)}")
        println("container_bytes=${built.container.size}")
        println("pack_file=pack.kcpk")
        return ACCEPTED
    }

    private fun verify(options: Options): Int {
        val packFile = File(options.required("pack")).absoluteFile
        val keyDirectory = File(options.required("keydir")).absoluteFile
        val appCode = options.optional("appcode")?.toInt() ?: 20
        val minimumSequence = options.optional("minsequence")?.toInt() ?: 1
        if (!packFile.isFile) {
            println("status=USAGE_ERROR reason=missing_pack")
            return USAGE_ERROR
        }
        val key = readKey(keyDirectory)
        val trust = TrustStore(listOf(TrustAnchor(key.keyId, key.publicKey, AnchorScope.of(key.scope))))
        if (packFile.length() > com.kitt.packtool.ReleaseFormat.MAX_CONTAINER) {
            throw com.kitt.packtool.PackRejection("container_too_large")
        }
        val bytes = packFile.readBytes()
        val verdict = ReleaseVerifier(trust, appCode, D0CountyGate, minimumSequence).verify(bytes)
        val report = when (verdict) {
            is ReleaseVerdict.Accepted -> buildJsonObject {
                put("status", "ACCEPTED_TEST_ONLY")
                put("area_id", verdict.manifest.areaId)
                put("parent_id", verdict.manifest.parentId)
                put("content_kind", verdict.manifest.contentKind)
                put("publisher_scope", verdict.manifest.publisherScope)
                put("key_id", verdict.manifest.keyId)
                put("source_commit", verdict.manifest.sourceCommit)
                put("sequence", verdict.manifest.sequence)
                put("minimum_sequence", minimumSequence)
                put("fact_version", verdict.manifest.factVersion)
                put("narrative_version", verdict.manifest.narrativeVersion)
                put("facts", verdict.gate.facts)
                put("sources", verdict.gate.sources)
                put("claims", verdict.gate.claims)
                put("stories", verdict.gate.stories)
                put("blocked", verdict.gate.blocked.size)
                put("container_sha256", ContentDigest.sha256(bytes))
            }
            is ReleaseVerdict.Rejected -> buildJsonObject {
                put("status", "REJECTED")
                put("code", verdict.code)
                put("minimum_sequence", minimumSequence)
                put("detail", verdict.detail)
                put("container_sha256", ContentDigest.sha256(bytes))
            }
        }
        options.optional("json")?.let { File(it).absoluteFile.writeText(
            Json { prettyPrint = true }.encodeToString(JsonElement.serializer(), report)) }
        report.forEach { (field, value) -> println("$field=${value.jsonPrimitive.content}") }
        return if (verdict is ReleaseVerdict.Accepted) ACCEPTED else REJECTED
    }

    /** Evidence helper: flips exactly one bit so a CLI-level refusal can be recorded. */
    private fun tamper(options: Options): Int {
        val source = File(options.required("pack")).readBytes()
        val offset = options.required("offset").toInt()
        if (offset !in source.indices) {
            println("status=USAGE_ERROR reason=offset_out_of_range")
            return USAGE_ERROR
        }
        val mutated = source.copyOf()
        mutated[offset] = (mutated[offset].toInt() xor 1).toByte()
        File(options.required("out")).absoluteFile.writeBytes(mutated)
        println("status=TAMPERED")
        println("offset=$offset")
        println("container_sha256=${ContentDigest.sha256(mutated)}")
        return ACCEPTED
    }

    /** Hostile-input helper: re-signs the very same blocks under a different county label, which is
     *  exactly the "valid signature over the wrong identity" case the verifier must refuse. */
    private fun relabel(options: Options): Int {
        val source = File(options.required("pack")).readBytes()
        val areaId = options.required("area")
        val parentId = options.required("parent")
        val key = readKey(File(options.required("keydir")).absoluteFile, privateRequired = true)
        val privateKey = key.privateKey ?: run {
            println("status=REFUSED reason=missing_private_key")
            return USAGE_ERROR
        }
        val decoded = ReleaseContainer.decode(source)
        val original = ReleaseManifest.parse(decoded.region.manifestBytes)
        val blocks = CountyBlocks(decoded.region.blocks[0], decoded.region.blocks[1],
            decoded.region.blocks[2], decoded.region.blocks[3])
        val built = PackBuilder.assembleBlocks(areaId, parentId, blocks, original.factVersion,
            original.narrativeVersion, original.contentKind, key.keyId, key.scope, original.sourceCommit,
            original.minimumCode, privateKey, original.sequence)
        File(options.required("out")).absoluteFile.writeBytes(built.container)
        println("status=RELABELLED")
        println("area_id=$areaId")
        println("container_sha256=${ContentDigest.sha256(built.container)}")
        return ACCEPTED
    }

    /** Real-content rights audit. It never emits a container: it only reports whether a real
     *  Xindu release candidate could be justified, which D1 cannot. */
    private fun auditNewdu(options: Options): Int {
        val root = File(options.required("root")).absoluteFile
        val facts = Json.parseToJsonElement(
            File(root, "app/src/main/assets/offline/ys-facts.json").readText()).jsonObject
        val narratives = Json.parseToJsonElement(
            File(root, "app/src/main/assets/offline/ys-narratives.json").readText()).jsonObject
        val sources = facts.getValue("sources").jsonArray.map { it.jsonObject }
        val cleared = sources.filter { countyRights(it.getValue("rights").jsonPrimitive.content) }
        val unclear = sources - cleared.toSet()
        val stories = narratives.getValue("narratives").jsonArray.size
        println("command=audit-newdu")
        println("area_id=${facts.getValue("area_id").jsonPrimitive.content}")
        println("sources=${sources.size}")
        println("sources_cleared=${cleared.size}")
        println("sources_unclear=${unclear.size}")
        println("published_stories=$stories")
        unclear.forEach { println("unclear_rights=${it.getValue("source_id").jsonPrimitive.content}") }
        println("status=" + if (unclear.isEmpty()) "RIGHTS_CLEARED" else "BLOCKED")
        if (unclear.isNotEmpty()) {
            println("reason=external_redistribution_rights_unverified")
            return REJECTED
        }
        return ACCEPTED
    }

    internal class KeyMaterial(val keyId: String, val scope: String, val publicKey: PublicKey,
        val privateKey: PrivateKey?)

    internal fun readKey(directory: File, privateRequired: Boolean = false): KeyMaterial {
        val metadata = Json.parseToJsonElement(File(directory, "key.json").readText()).jsonObject
        if (metadata.getValue("scope").jsonPrimitive.content != "TEST_ONLY") {
            throw com.kitt.packtool.PackRejection("production_not_authorized")
        }
        val publicFile = File(directory, "public.pem")
        if (!publicFile.isFile) throw IllegalArgumentException("missing public.pem")
        val privateFile = File(directory, "private.pem")
        return KeyMaterial(
            metadata.getValue("key_id").jsonPrimitive.content,
            metadata.getValue("scope").jsonPrimitive.content,
            Pem.publicKey(publicFile.readText()),
            privateFile.takeIf { privateRequired && it.isFile }?.let { Pem.privateKey(it.readText()) }
        )
    }

    internal class Options(arguments: List<String>) {
        private val values: Map<String, String>
        init {
            val parsed = linkedMapOf<String, String>()
            var index = 0
            while (index < arguments.size) {
                val flag = arguments[index]
                require(flag.startsWith("--") && index + 1 < arguments.size) { flag }
                val name = flag.removePrefix("--")
                require(name in setOf("command", "root", "sample", "out", "keydir", "pack", "commit",
                    "appcode", "json", "offset", "keyid", "sequence", "minsequence", "area", "parent",
                    "input", "scope", "factversion", "narrativeversion", "confirmation", "reviewhash"))
                require(name !in parsed) { "duplicate_option" }
                parsed[name] = arguments[index + 1]
                index += 2
            }
            values = parsed
        }
        fun required(name: String) = values[name] ?: throw IllegalArgumentException("missing --$name")
        fun optional(name: String) = values[name]
    }
}
