package com.kitt.reader.d1

import com.kitt.packtool.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.attribute.*
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.interfaces.RSAKey
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Thin production path on the existing D1 builder/verifier. No application code or new pack schema.
 * Tests inject BOTH a fake command runner and a simulated stdin; no CLI flag enables virtual keys. */
internal object D2Production {
    const val REPO = "github.com/shimao1115/KITT"
    const val REMOTE = "https://github.com/shimao1115/KITT.git"
    private const val REGISTRY = "config/pack-publishers.json"
    internal class Runtime(
        val command: (List<String>) -> String = { D2Production.command(it) },
        val decision: () -> String? = { readlnOrNull() },
        val simulated: Boolean = false
    )

    private fun reject(code: String): Nothing = throw PackRejection(code)
    private fun JsonObject.text(name: String) = getValue(name).jsonPrimitive.content
    private fun keyDirectory(o: D1Cli.Options): File = o.optional("keydir")?.let(::File)
        ?: File(System.getenv("LOCALAPPDATA") ?: reject("windows_identity_location"), "KITT/pack-publisher")

    /** Resolve existing ancestors (including junctions), then forbid any registered or enclosing Git tree. */
    private fun external(directory: File, root: File) {
        var ancestor = directory.absoluteFile.toPath().normalize()
        val suffix = mutableListOf<String>()
        while (!Files.exists(ancestor, NOFOLLOW_LINKS)) {
            suffix.add(ancestor.fileName.toString())
            ancestor = ancestor.parent ?: reject("identity_path")
        }
        var resolved = ancestor.toRealPath()
        suffix.asReversed().forEach { resolved = resolved.resolve(it) }
        D2Local.requireExternalKey(resolved.toFile(), root)
        var parent: Path? = resolved
        while (parent != null) {
            if (Files.exists(parent.resolve(".git"), NOFOLLOW_LINKS)) reject("keydir_inside_worktree")
            parent = parent.parent
        }
    }

    /** Owner-only ACL is installed before key generation/write; inherited group grants are removed. */
    private fun protect(path: Path, create: Boolean) {
        val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java, NOFOLLOW_LINKS)
        if (acl != null) {
            val owner = acl.owner
            if (create) acl.acl = listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW)
                .setPrincipal(owner).setPermissions(AclEntryPermission.values().toSet())
                .setFlags(AclEntryFlag.DIRECTORY_INHERIT, AclEntryFlag.FILE_INHERIT).build())
            if (acl.acl.isEmpty() || acl.acl.any { it.type() == AclEntryType.ALLOW && it.principal() != owner }) {
                reject("identity_permissions")
            }
        } else {
            val posix = Files.getFileAttributeView(path, PosixFileAttributeView::class.java, NOFOLLOW_LINKS)
                ?: reject("identity_permissions")
            if (create) posix.setPermissions(PosixFilePermissions.fromString("rwx------"))
            if (posix.readAttributes().permissions().any { !it.name.startsWith("OWNER_") }) reject("identity_permissions")
        }
    }

    fun initialize(o: D1Cli.Options, r: Runtime = Runtime()): Int {
        val directory = keyDirectory(o)
        external(directory, File(o.required("root")))
        val path = directory.absoluteFile.toPath()
        Files.createDirectories(path.parent)
        Files.createDirectory(path) // Never replace even a partial initialization.
        protect(path, true)
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val id = "kitt-${UUID.randomUUID()}"
        val metadata = buildJsonObject {
            put("key_id", id); put("scope", "PRODUCTION"); put("test_identity", r.simulated)
        }
        Files.write(path.resolve("key.json"), metadata.toString().encodeToByteArray(), CREATE_NEW)
        Files.write(path.resolve("public.pem"), Pem.publicPem(pair.public).encodeToByteArray(), CREATE_NEW)
        Files.write(path.resolve("private.pem"), Pem.privatePem(pair.private).encodeToByteArray(), CREATE_NEW)
        // Public-only registration; private material never becomes a report or process argument.
        Files.write(path.resolve("registration.json"), buildJsonObject {
            put("key_id", id); put("scope", "PRODUCTION"); put("test_identity", r.simulated)
            put("public_key", Pem.publicPem(pair.public))
        }.toString().encodeToByteArray(), CREATE_NEW)
        println("status=" + if (r.simulated) "VIRTUAL_IDENTITY_INITIALIZED" else "LOCAL_IDENTITY_INITIALIZED")
        println("key_id=$id")
        return D1Cli.ACCEPTED
    }

    private data class Identity(val directory: File, val id: String, val public: PublicKey)
    private fun identity(o: D1Cli.Options, r: Runtime): Identity {
        val directory = keyDirectory(o)
        external(directory, File(o.required("root")))
        val path = directory.toPath()
        protect(path, false)
        listOf("key.json", "public.pem", "private.pem").forEach {
            val file = path.resolve(it)
            if (!Files.isRegularFile(file, NOFOLLOW_LINKS)) reject("identity_file")
            protect(file, false)
        }
        val metadata = Json.parseToJsonElement(File(directory, "key.json").readText()).jsonObject
        if (metadata.text("scope") != "PRODUCTION") reject("test_only_identity")
        if (metadata.getValue("test_identity").jsonPrimitive.boolean != r.simulated) reject("virtual_identity_forbidden")
        val id = metadata.text("key_id")
        if (!ReleaseFormat.KEY_ID.matches(id) || id.contains("test", true)) reject("production_key_id")
        val public = Pem.publicKey(File(directory, "public.pem").readText())
        if ((public as RSAKey).modulus.bitLength() < 3072) reject("identity_strength")
        return Identity(directory, id, public)
    }

    private fun registered(o: D1Cli.Options, r: Runtime, identity: Identity): TrustStore {
        // A caller-controlled key directory is NOT a trust store. Registration comes from the
        // same immutable repository commit as the reviewed source, never the pack or local files.
        val raw = r.command(listOf("git", "-C", o.required("root"), "show", "${o.required("commit")}:$REGISTRY"))
        val anchors = Json.parseToJsonElement(raw).jsonArray.map {
            val row = it.jsonObject
            if (row.keys != setOf("key_id", "scope", "test_identity", "public_key")) reject("publisher_registry")
            if (row.text("scope") != "PRODUCTION" || row.getValue("test_identity").jsonPrimitive.boolean != r.simulated) {
                reject("publisher_registry_scope")
            }
            TrustAnchor(row.text("key_id"), Pem.publicKey(row.text("public_key")), AnchorScope.PRODUCTION)
        }
        val store = TrustStore(anchors)
        val trusted = store.resolve(identity.id, "PRODUCTION") ?: reject("unknown_key_id")
        if (!trusted.publicKey.encoded.contentEquals(identity.public.encoded)) reject("publisher_key_mismatch")
        return store
    }

    // D0 versions remain strings. Only the production publisher requires ordered vN.N.N values,
    // so "v10.0.0" is newer than "v2.0.0" and changing bytes cannot silently reuse a version.
    private fun version(text: String): List<Int> {
        if (!text.matches(Regex("v(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})\\.(0|[1-9][0-9]{0,8})"))) reject("production_version")
        return text.drop(1).split('.').map(String::toInt)
    }
    private fun candidate(o: D1Cli.Options, identity: Identity): PackBuilder.Prepared {
        if ((o.optional("scope") ?: "PRODUCTION") != "PRODUCTION") reject("test_only_upload")
        val (facts, narratives) = D2Local.input(o)
        if (listOf(facts, narratives).any { it.decodeToString().contains(Regex("TEST[_-]ONLY", RegexOption.IGNORE_CASE)) }) {
            reject("test_only_content")
        }
        val area = Json.parseToJsonElement(facts.decodeToString()).jsonObject.text("area_id")
        val prepared = PackBuilder.prepare(area, facts, narratives, identity.id, "PRODUCTION",
            o.required("commit"), o.optional("appcode")?.toInt() ?: 20, o.optional("sequence")?.toInt() ?: 1)
        version(prepared.manifest.factVersion); version(prepared.manifest.narrativeVersion)
        D2Local.contentCheck(prepared)
        return prepared
    }

    fun prepare(o: D1Cli.Options, r: Runtime = Runtime()): Int {
        val identity = identity(o, r)
        registered(o, r, identity)
        val prepared = candidate(o, identity)
        val output = File(o.required("out")).toPath()
        Files.createDirectories(output.toAbsolutePath().parent)
        Files.createDirectory(output)
        Files.write(output.resolve("candidate.unsigned"), prepared.region, CREATE_NEW)
        Files.write(output.resolve("review.md"), D2Local.review(prepared).encodeToByteArray(), CREATE_NEW)
        Files.write(output.resolve("prepare.json"), buildJsonObject {
            put("manifest", Json.parseToJsonElement(prepared.manifest.canonicalBytes().decodeToString()))
            put("signed_region_sha256", ContentDigest.sha256(prepared.region))
            put("simulation", r.simulated); put("upload_performed", false)
        }.toString().encodeToByteArray(), CREATE_NEW)
        println("status=" + if (r.simulated) "SIMULATED_PRODUCTION_PREPARED" else "PRODUCTION_PREPARED")
        println("signed_region_sha256=${ContentDigest.sha256(prepared.region)}")
        return D1Cli.ACCEPTED
    }

    private fun tag(m: ReleaseManifest) = "kitt-pack-${m.areaId}-s${m.sequence}"
    private fun filename(m: ReleaseManifest) = "${m.areaId}-s${m.sequence}.kcpk"
    private fun gh(r: Runtime, vararg args: String) = r.command(listOf("gh", "release") + args + listOf("--repo", REPO))

    /** Release records, not a separate version DB. Any unreadable county record fails closed. */
    private fun history(o: D1Cli.Options, r: Runtime, current: ReleaseManifest) {
        val rows = Json.parseToJsonElement(gh(r, "list", "--limit", "1000", "--json", "tagName,isDraft,isPrerelease")).jsonArray
        if (rows.size >= 1000) reject("release_history_truncated")
        val prefix = "kitt-pack-${current.areaId}-s"
        rows.map { it.jsonObject }.filter { it.text("tagName").startsWith(prefix) }.forEach { row ->
            val oldTag = row.text("tagName")
            if (row.getValue("isDraft").jsonPrimitive.boolean || row.getValue("isPrerelease").jsonPrimitive.boolean) {
                reject("release_history_incomplete")
            }
            val record = Json.parseToJsonElement(gh(r, "view", oldTag, "--json", "tagName,body,assets,isDraft,isPrerelease,url")).jsonObject
            val old = ReleaseManifest.parse(record.text("body").encodeToByteArray())
            ManifestRules.checkManifest(old, Int.MAX_VALUE)
            if (old.publisherScope != "PRODUCTION" || old.areaId != current.areaId || tag(old) != oldTag ||
                record.text("tagName") != oldTag || record.getValue("isDraft").jsonPrimitive.boolean ||
                record.getValue("isPrerelease").jsonPrimitive.boolean ||
                record.getValue("assets").jsonArray.none { it.jsonObject.text("name") == filename(old) }) reject("release_history_invalid")
            if (old.sequence >= current.sequence) reject("release_sequence_rollback")
            listOf(Triple(old.factVersion, current.factVersion, "facts"),
                Triple(old.narrativeVersion, current.narrativeVersion, "narratives")).forEach { (before, after, kind) ->
                val comparison = version(before).zip(version(after)).firstOrNull { it.first != it.second }
                if (comparison != null && comparison.first > comparison.second) reject("release_version_rollback")
                if (before == after && old.blocks.single { it.kind == kind }.sha256 != current.blocks.single { it.kind == kind }.sha256) {
                    reject("release_version_reused")
                }
            }
            // Fail if old source is unavailable or not an ancestor. A fresh clone must fetch it.
            r.command(listOf("git", "-C", o.required("root"), "merge-base", "--is-ancestor", old.sourceCommit, current.sourceCommit))
        }
        val tags = r.command(listOf("git", "ls-remote", "--tags", REMOTE))
        tags.lineSequence().mapNotNull { it.substringAfter("refs/tags/", "").removeSuffix("^{}").takeIf(String::isNotEmpty) }
            .filter { it.startsWith(prefix) }.forEach {
                val sequence = it.removePrefix(prefix).toIntOrNull() ?: reject("release_tag_invalid")
                if (sequence >= current.sequence) reject("release_tag_conflict")
                if (rows.none { row -> row.jsonObject.text("tagName") == it }) reject("release_history_incomplete")
            }
    }

    fun publish(o: D1Cli.Options, r: Runtime = Runtime()): Int {
        if (o.optional("confirmation") != null) reject("stdin_decision_required")
        val identity = identity(o, r)
        registered(o, r, identity)
        val reviewed = candidate(o, identity)
        val hash = ContentDigest.sha256(reviewed.region)
        if (o.required("reviewhash") != hash) reject("review_changed")
        history(o, r, reviewed.manifest)
        println("review_area=${reviewed.manifest.areaId}")
        println("review_commit=${reviewed.manifest.sourceCommit}")
        println("review_versions=${reviewed.manifest.factVersion}/${reviewed.manifest.narrativeVersion}")
        println("review_sequence=${reviewed.manifest.sequence}")
        println("请输入唯一一次内容决定：可以发布 $hash")
        System.out.flush()
        if (r.decision() != "可以发布 $hash") reject("confirmation_required")
        // Re-read the immutable Git blobs AND pinned identity after the single decision.
        val now = identity(o, r)
        val trust = registered(o, r, now)
        val prepared = candidate(o, now)
        if (!prepared.region.contentEquals(reviewed.region)) reject("review_changed")
        val privateKey = Pem.privateKey(File(now.directory, "private.pem").readText())
        val built = PackBuilder.build(prepared.manifest.areaId, prepared.blocks.facts, prepared.blocks.narratives,
            now.id, "PRODUCTION", o.required("commit"), prepared.manifest.minimumCode, privateKey, prepared.manifest.sequence)
        val accepted = checked(built.container, trust, hash, prepared.manifest.minimumCode)
        val output = File(o.required("out")).toPath()
        Files.createDirectories(output.toAbsolutePath().parent)
        Files.createDirectory(output)
        val pack = output.resolve(filename(accepted.manifest))
        Files.write(pack, built.container, CREATE_NEW)
        val notes = output.resolve("manifest.json")
        Files.write(notes, accepted.manifest.canonicalBytes(), CREATE_NEW)
        // Repeat the remote check immediately before the single gh create operation.
        history(o, r, accepted.manifest)
        checked(Files.readAllBytes(pack), trust, hash, prepared.manifest.minimumCode)
        if (!Files.readAllBytes(notes).contentEquals(accepted.manifest.canonicalBytes())) reject("release_notes_changed")
        Files.write(output.resolve("decision.json"), buildJsonObject {
            put("signed_region_sha256", hash); put("decision", "可以发布"); put("simulation", r.simulated)
            put("human_identity_authenticated", false) // Host/caller must supply actual human consent.
        }.toString().encodeToByteArray(), CREATE_NEW)
        try {
            gh(r, "create", tag(accepted.manifest), pack.toAbsolutePath().toString(), "--target", o.required("commit"),
                "--title", tag(accepted.manifest), "--notes-file", notes.toAbsolutePath().toString(), "--latest=false")
            val remote = Json.parseToJsonElement(gh(r, "view", tag(accepted.manifest), "--json",
                "tagName,body,assets,isDraft,isPrerelease,url")).jsonObject
            if (remote.text("tagName") != tag(accepted.manifest) || remote.text("body") != accepted.manifest.canonicalBytes().decodeToString() ||
                remote.getValue("isDraft").jsonPrimitive.boolean || remote.getValue("isPrerelease").jsonPrimitive.boolean ||
                remote.text("url") != "https://github.com/shimao1115/KITT/releases/tag/${tag(accepted.manifest)}") reject("release_readback")
            val assets = remote.getValue("assets").jsonArray
            val asset = assets.singleOrNull { it.jsonObject.text("name") == pack.fileName.toString() }?.jsonObject
                ?: reject("release_readback")
            if (asset.getValue("size").jsonPrimitive.long != built.container.size.toLong() ||
                asset.text("digest") != "sha256:${ContentDigest.sha256(built.container)}") reject("release_readback")
            val ref = "refs/tags/${tag(accepted.manifest)}"
            val refs = r.command(listOf("git", "ls-remote", "--tags", REMOTE, ref, "$ref^{}"))
                .lineSequence().filter { it.isNotBlank() }.associate {
                    val fields = it.split(Regex("\\s+")); fields.last() to fields.first()
                }
            if ((refs["$ref^{}"] ?: refs[ref]) != o.required("commit")) reject("release_tag_target")
        } catch (e: Exception) {
            // gh may have left a draft/tag, or published despite a lost response. Never retry/delete.
            throw PackRejection("release_outcome_unknown_check_remote", (e as? PackRejection)?.code ?: "release_readback")
        }
        val report = buildJsonObject {
            put("status", if (r.simulated) "SIMULATED_PUBLISHED" else "PUBLISHED")
            put("simulation", r.simulated); put("upload_performed", !r.simulated)
            put("signed_region_sha256", hash); put("container_sha256", ContentDigest.sha256(built.container))
            put("tag", tag(accepted.manifest)); put("human_decisions", 1); put("signature_verified", true)
        }
        Files.write(output.resolve("publish.json"), report.toString().encodeToByteArray(), CREATE_NEW)
        report.forEach { (k, v) -> println("$k=${v.jsonPrimitive.content}") }
        return D1Cli.ACCEPTED
    }

    internal fun checked(bytes: ByteArray, trust: TrustStore, hash: String, appCode: Int): ReleaseVerdict.Accepted {
        val verdict = ReleaseVerifier(trust, appCode, D0CountyGate).verify(bytes)
        if (verdict is ReleaseVerdict.Rejected) throw PackRejection(verdict.code, verdict.detail)
        verdict as ReleaseVerdict.Accepted
        if (verdict.manifest.publisherScope != "PRODUCTION") reject("test_only_upload")
        if (ContentDigest.sha256(ReleaseContainer.decode(bytes).region.bytes) != hash) reject("review_changed")
        return verdict
    }

    /** Shell-free, bounded subprocess. Raw stderr, paths and credentials are never printed. */
    internal fun command(args: List<String>, timeoutSeconds: Long = 90): String {
        val output = Files.createTempFile("kitt-command-", ".out")
        val error = Files.createTempFile("kitt-command-", ".err")
        try {
            val builder = ProcessBuilder(args).redirectOutput(output.toFile()).redirectError(error.toFile())
            builder.environment()["GH_PROMPT_DISABLED"] = "1"
            builder.environment()["GIT_TERMINAL_PROMPT"] = "0"
            val process = try { builder.start() } catch (e: Exception) { reject("command_unavailable") }
            process.outputStream.close()
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly().waitFor()
                reject("command_timeout")
            }
            if (process.exitValue() != 0) {
                val message = if (Files.size(error) <= 100_000) Files.readAllBytes(error).decodeToString().lowercase() else ""
                reject(when {
                    args.first() == "git" && args.contains("--is-ancestor") -> "release_source_rollback_or_missing"
                    listOf("401", "403", "authentication", "not logged", "gh auth login").any(message::contains) -> "command_authentication"
                    listOf("resolve host", "no such host", "dial tcp", "network", "timed out", "connection").any(message::contains) -> "command_network"
                    listOf("422", "already exists", "conflict").any(message::contains) -> "command_conflict"
                    else -> "command_failed"
                })
            }
            if (Files.size(output) > 4_000_000) reject("command_output_size")
            return Files.readAllBytes(output).decodeToString()
        } finally { Files.deleteIfExists(output); Files.deleteIfExists(error) }
    }
}
