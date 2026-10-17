package com.kitt.reader.d1

import com.kitt.packtool.*
import kotlinx.serialization.json.*
import java.io.File
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption.CREATE_NEW

/** Local-only additions to the existing CLI. No network, release API or production identity. */
internal object D2Local {
    private val discard = File(if (System.getProperty("os.name", "")!!.startsWith("Windows")) "NUL" else "/dev/null")
    fun input(options: D1Cli.Options): Pair<ByteArray, ByteArray> {
        val root = File(options.required("root")).canonicalFile
        val commit = options.required("commit")
        if (!ReleaseFormat.SOURCE_COMMIT.matches(commit)) throw PackRejection("source_commit")
        val directory = options.optional("input") ?: "tools/d1/sample/${options.required("sample")}"
        if (!directory.split('/').all { it.matches(Regex("[a-zA-Z0-9_-][a-zA-Z0-9._-]*")) && it != ".." }) {
            throw PackRejection("input_path")
        }
        // Read committed blobs, never mutable working files or local path-dependent line endings.
        fun git(vararg args: String): ByteArray {
            val process = ProcessBuilder(listOf("git", "-C", root.path) + args)
                .redirectError(discard).start()
            val bytes = process.inputStream.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (output.size() <= ReleaseFormat.MAX_BLOCK) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            if (bytes.size > ReleaseFormat.MAX_BLOCK) {
                process.destroyForcibly()
                throw PackRejection("block_size")
            }
            if (process.waitFor() != 0) throw PackRejection("source_input_unavailable")
            return bytes
        }
        if (git("cat-file", "-t", commit).decodeToString().trim() != "commit") throw PackRejection("source_commit")
        return git("show", "$commit:$directory/facts.json") to
            git("show", "$commit:$directory/narratives.json")
    }

    fun contentCheck(prepared: PackBuilder.Prepared): GateReport {
        InnerBinding.check(prepared.manifest, prepared.blocks)
        val report = D0CountyGate.inspect(prepared.blocks)
        if (!report.accepted) throw PackRejection("content_rejected",
            report.blocked.entries.joinToString(",") { "${it.key}:${it.value}" }.ifBlank {
                report.reason ?: "content_schema"
            })
        return report
    }

    fun requireExternalKey(directory: File, root: File) {
        val key = directory.canonicalFile.toPath()
        if (key.startsWith(root.canonicalFile.toPath())) throw PackRejection("keydir_inside_worktree")
        // Linked worktrees share the original checkout; reject that checkout and all registered trees.
        val process = ProcessBuilder("git", "-C", root.canonicalPath, "worktree", "list", "--porcelain")
            .redirectError(discard).start()
        val lines = process.inputStream.bufferedReader().readLines()
        if (process.waitFor() != 0) throw PackRejection("worktree_inventory")
        lines.filter { it.startsWith("worktree ") }.forEach {
            if (key.startsWith(File(it.removePrefix("worktree ")).canonicalFile.toPath())) {
                throw PackRejection("keydir_inside_worktree")
            }
        }
    }

    private fun testScope(options: D1Cli.Options) {
        if ((options.optional("scope") ?: "TEST_ONLY") != "TEST_ONLY") {
            throw PackRejection("production_not_authorized")
        }
    }

    fun prepare(options: D1Cli.Options): Int {
        testScope(options)
        val (facts, narratives) = input(options)
        val area = Json.parseToJsonElement(facts.decodeToString()).jsonObject.getValue("area_id").jsonPrimitive.content
        val candidate = PackBuilder.prepare(area, facts, narratives,
            options.optional("keyid") ?: "test-only-d1-local", "TEST_ONLY", options.required("commit"),
            options.optional("appcode")?.toInt() ?: 20, options.optional("sequence")?.toInt() ?: 1)
        val gate = contentCheck(candidate)
        val report = report(candidate.manifest, gate, "PREPARED_TEST_ONLY") + mapOf(
            "unsigned_sha256" to JsonPrimitive(candidate.blocks.digest()),
            "signed_region_sha256" to JsonPrimitive(ContentDigest.sha256(candidate.region)),
            "signature_verified" to JsonPrimitive(false))
        // Claim an unused output directory atomically. Existing directories and their files survive.
        val output = File(options.required("out")).canonicalFile.toPath()
        Files.createDirectories(output.parent)
        Files.createDirectory(output)
        Files.write(output.resolve("candidate.unsigned"), candidate.region, CREATE_NEW)
        val names = listOf("fact-manifest.json", "facts.json", "narrative-manifest.json", "narratives.json")
        names.zip(candidate.blocks.list()).forEach { (name, bytes) -> Files.write(output.resolve(name), bytes, CREATE_NEW) }
        Files.write(output.resolve("prepare.json"), JsonObject(report).toString().encodeToByteArray(), CREATE_NEW)
        Files.write(output.resolve("review.md"), review(candidate).encodeToByteArray(), CREATE_NEW)
        println("status=PREPARED_TEST_ONLY")
        println("unsigned_sha256=${candidate.blocks.digest()}")
        println("signed_region_sha256=${ContentDigest.sha256(candidate.region)}")
        println("stories=${gate.stories}")
        println("ready_to_publish=false")
        return D1Cli.ACCEPTED
    }

    /** One simulated human decision, followed by automatic checks/sign/verify/upload planning.
     *  No second confirmation, no real approval mutation and no network process can be invoked. */
    fun releaseDryRun(options: D1Cli.Options): Int {
        testScope(options)
        if (options.required("confirmation") != "SIMULATED_TEST_ONLY") throw PackRejection("confirmation_required")
        val root = File(options.required("root"))
        val keyDirectory = File(options.required("keydir"))
        requireExternalKey(keyDirectory, root)
        val key = D1Cli.readKey(keyDirectory, privateRequired = true)
        val privateKey = key.privateKey ?: throw PackRejection("missing_private_key")
        val (facts, narratives) = input(options)
        val area = Json.parseToJsonElement(facts.decodeToString()).jsonObject.getValue("area_id").jsonPrimitive.content
        val appCode = options.optional("appcode")?.toInt() ?: 20
        val sequence = options.optional("sequence")?.toInt() ?: 1
        val prepared = PackBuilder.prepare(area, facts, narratives, key.keyId, "TEST_ONLY",
            options.required("commit"), appCode, sequence)
        if (options.required("reviewhash") != ContentDigest.sha256(prepared.region)) {
            throw PackRejection("review_changed")
        }
        contentCheck(prepared)
        val built = PackBuilder.build(area, facts, narratives, key.keyId, "TEST_ONLY",
            options.required("commit"), appCode, privateKey, sequence)
        val verifier = ReleaseVerifier(TrustStore(listOf(TrustAnchor(key.keyId, key.publicKey, AnchorScope.TEST_ONLY))),
            appCode, D0CountyGate, options.optional("minsequence")?.toInt() ?: 1)
        val verdict = verifier.verify(built.container)
        if (verdict is ReleaseVerdict.Rejected) throw PackRejection(verdict.code, verdict.detail)
        verdict as ReleaseVerdict.Accepted
        val result = JsonObject(report(verdict.manifest, verdict.gate, "SIMULATED_RELEASE_PREPARED_TEST_ONLY") + mapOf(
            "confirmation" to JsonPrimitive("SIMULATED_TEST_ONLY"),
            "simulated_human_decisions" to JsonPrimitive(1),
            "real_human_approval_recorded" to JsonPrimitive(false),
            "signature_verified" to JsonPrimitive(true),
            "upload_performed" to JsonPrimitive(false),
            "unsigned_sha256" to JsonPrimitive(prepared.blocks.digest()),
            "signed_region_sha256" to JsonPrimitive(ContentDigest.sha256(prepared.region)),
            "container_sha256" to JsonPrimitive(ContentDigest.sha256(built.container)),
            "upload_argv_template" to JsonArray(listOf("gh", "release", "create", "<PRODUCTION_TAG>",
                "<VERIFIED_PRODUCTION_FILE>", "--repo", "shimao1115/KITT", "--title", "<APPROVED_TITLE>",
                "--notes-file", "<REVIEWED_NOTES_FILE>").map(::JsonPrimitive)),
            "upload_template_executable" to JsonPrimitive(false)))
        val output = File(options.required("out")).canonicalFile.toPath()
        Files.createDirectories(output.parent)
        Files.createDirectory(output)
        Files.write(output.resolve("pack.kcpk"), built.container, CREATE_NEW)
        Files.write(output.resolve("release-preview.json"), result.toString().encodeToByteArray(), CREATE_NEW)
        result.forEach { (field, value) -> println("$field=${if (value is JsonPrimitive) value.content else value}") }
        return D1Cli.ACCEPTED
    }

    private fun review(candidate: PackBuilder.Prepared): String = buildString {
        appendLine("# TEST_ONLY 内容预览（不是真实人工批准或正式出版）")
        appendLine("地区：${candidate.manifest.areaId}；源码：${candidate.manifest.sourceCommit}")
        appendLine("版本：${candidate.manifest.factVersion} / ${candidate.manifest.narrativeVersion}；序号：${candidate.manifest.sequence}")
        appendLine("用户一次过目包括正文、事实和下列来源/权利说明；机器检查自动执行。")
        val facts = Json.parseToJsonElement(candidate.blocks.facts.decodeToString()).jsonObject
        facts.getValue("entities").jsonArray.forEach { entity ->
            appendLine("\n## ${entity.jsonObject.getValue("title").jsonPrimitive.content}")
            entity.jsonObject.getValue("basic_facts").jsonArray.forEach { fact ->
                appendLine("- ${fact.jsonObject.getValue("summary").jsonPrimitive.content}")
            }
        }
        appendLine("\n## 来源与权利")
        facts.getValue("sources").jsonArray.forEach { source ->
            val s = source.jsonObject
            appendLine("- ${s.getValue("source_id").jsonPrimitive.content}：${s.getValue("title").jsonPrimitive.content}；${s.getValue("url").jsonPrimitive.content}；${s.getValue("rights").jsonPrimitive.content}")
        }
        val stories = Json.parseToJsonElement(candidate.blocks.narratives.decodeToString()).jsonObject.getValue("narratives").jsonArray
        appendLine("\n## 讲述（${stories.size} 篇；合成测试稿）")
        stories.forEach { story ->
            appendLine("\n### ${story.jsonObject.getValue("title").jsonPrimitive.content}")
            appendLine(story.jsonObject.getValue("text").jsonPrimitive.content)
        }
    }

    fun preview(options: D1Cli.Options): Int {
        testScope(options)
        val file = File(options.required("pack"))
        if (file.length() !in 1..ReleaseFormat.MAX_CONTAINER.toLong()) throw PackRejection("container_too_large")
        // Public-only trust input. private.pem is neither needed nor opened during verification.
        val key = D1Cli.readKey(File(options.required("keydir")))
        val bytes = file.readBytes()
        val verifier = ReleaseVerifier(TrustStore(listOf(TrustAnchor(key.keyId, key.publicKey, AnchorScope.TEST_ONLY))),
            options.optional("appcode")?.toInt() ?: 20, D0CountyGate,
            options.optional("minsequence")?.toInt() ?: 1)
        val verdict = verifier.verify(bytes)
        if (verdict is ReleaseVerdict.Rejected) throw PackRejection(verdict.code, verdict.detail)
        verdict as ReleaseVerdict.Accepted
        val m = verdict.manifest
        // Match the human's intended release, not just a self-consistent signed container.
        val expected = listOf("area" to m.areaId, "commit" to m.sourceCommit,
            "factversion" to m.factVersion, "narrativeversion" to m.narrativeVersion,
            "sequence" to m.sequence.toString())
        expected.forEach { (flag, actual) ->
            if (options.required(flag) != actual) throw PackRejection("expected_$flag")
        }
        val hash = ContentDigest.sha256(bytes)
        val result = JsonObject(report(m, verdict.gate, "PREVIEW_TEST_ONLY") + mapOf(
            "filename" to JsonPrimitive("TEST_ONLY-${m.areaId}-${m.sequence}-${hash.take(12)}.kcpk"),
            "container_sha256" to JsonPrimitive(hash),
            "container_bytes" to JsonPrimitive(bytes.size),
            "signature_verified" to JsonPrimitive(true)))
        options.required("json").let {
            Files.write(File(it).toPath(), result.toString().encodeToByteArray(), CREATE_NEW)
        }
        result.forEach { (field, value) -> println("$field=${value.jsonPrimitive.content}") }
        return D1Cli.ACCEPTED
    }

    private fun report(m: ReleaseManifest, gate: GateReport, status: String): Map<String, JsonElement> = buildJsonObject {
        put("status", status)
        put("ready_to_publish", false)
        put("publish_blocker", "test_only_no_production_authorization")
        put("area_id", m.areaId); put("parent_id", m.parentId)
        put("publisher_scope", m.publisherScope); put("key_id", m.keyId)
        put("source_commit", m.sourceCommit); put("builder", m.builder)
        put("content_kind", m.contentKind); put("minimum_code", m.minimumCode)
        put("fact_version", m.factVersion); put("narrative_version", m.narrativeVersion)
        put("sequence", m.sequence); put("content_gate", "PASS_TEST_ONLY")
        put("facts", gate.facts); put("sources", gate.sources); put("claims", gate.claims); put("stories", gate.stories)
    }
}
