package com.kitt.reader.d1

import com.kitt.packtool.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.security.KeyPairGenerator
import java.security.KeyPair

/** Real CLI calls with isolated local Git inputs; all content and keys are TEST_ONLY. */
class ReleaseD2Test {
    @get:Rule val temp = TemporaryFolder()
    private val facts get() = File("../tools/d1/sample/fact-only/facts.json").readBytes()
    private val narratives get() = File("../tools/d1/sample/fact-only/narratives.json").readBytes()
    private val story get() = File("../tools/d1/sample/with-story/narratives.json").readBytes()
    private val keyId = "test-only-d1-local"

    companion object {
        private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    }

    private data class Source(val root: File, val commit: String)
    private fun source(f: ByteArray = facts, n: ByteArray = narratives): Source {
        val root = temp.newFolder()
        File(root, "input").mkdir()
        File(root, "input/facts.json").writeBytes(f)
        File(root, "input/narratives.json").writeBytes(n)
        fun git(vararg args: String): String {
            val p = ProcessBuilder(listOf("git", "-C", root.path) + args).redirectErrorStream(true).start()
            val output = p.inputStream.bufferedReader().readText()
            check(p.waitFor() == 0) { "fixture_git_failed" }
            return output.trim()
        }
        git("init", "-q"); git("config", "user.name", "TEST_ONLY"); git("config", "user.email", "test@example.invalid")
        git("config", "core.autocrlf", "false"); git("add", ".")
        git("commit", "-qm", "TEST_ONLY input")
        return Source(root, git("rev-parse", "HEAD"))
    }

    private fun cli(vararg options: String): Pair<Int, String> {
        val previous = System.out
        val buffer = ByteArrayOutputStream()
        return try {
            System.setOut(PrintStream(buffer, true, "UTF-8"))
            D1Cli.run(options.toList()) to buffer.toString("UTF-8")
        } finally { System.setOut(previous) }
    }

    private fun prepare(s: Source, output: File, vararg more: String): Pair<Int, String> {
        val options = linkedMapOf("command" to "prepare", "root" to s.root.path, "input" to "input",
            "commit" to s.commit, "out" to output.path)
        more.toList().chunked(2).forEach { options[it[0].removePrefix("--")] = it[1] }
        return cli(*options.flatMap { listOf("--${it.key}", it.value) }.toTypedArray())
    }

    private fun preview(f: ByteArray = facts, n: ByteArray = narratives,
                        mutate: (ByteArray) -> ByteArray = { it }, vararg more: String): Pair<Int, String> {
        val commit = "a".repeat(40)
        val keydir = temp.newFolder()
        File(keydir, "key.json").writeText("""{"key_id":"$keyId","scope":"TEST_ONLY"}""")
        File(keydir, "public.pem").writeText(Pem.publicPem(pair.public))
        // Deliberately invalid: verification must not open or parse private material.
        File(keydir, "private.pem").writeText("must never be opened by preview")
        val built = PackBuilder.build(D1Sample.AREA_ID, f, n, keyId, "TEST_ONLY", commit, 20, pair.private)
        val pack = temp.newFile()
        pack.writeBytes(mutate(built.container))
        val options = linkedMapOf("command" to "release-preview", "pack" to pack.path, "keydir" to keydir.path,
            "area" to D1Sample.AREA_ID, "commit" to commit, "factversion" to built.manifest.factVersion,
            "narrativeversion" to built.manifest.narrativeVersion, "sequence" to "1",
            "json" to File(temp.root, "preview-${System.nanoTime()}.json").path)
        more.toList().chunked(2).forEach { options[it[0].removePrefix("--")] = it[1] }
        return cli(*options.flatMap { listOf("--${it.key}", it.value) }.toTypedArray())
    }

    private fun refuses(result: Pair<Int, String>, code: String) {
        assertEquals(result.second, D1Cli.REJECTED, result.first)
        assertTrue(result.second, result.second.contains("code=$code"))
        assertFalse(result.second.contains("ready_to_publish=true"))
    }

    @Test fun preparesWithoutKeysAndReadsFrozenBlobsDespiteDirtyFiles() {
        val s = source()
        File(s.root, "input/facts.json").writeText("dirty invalid local content")
        val output = File(temp.root, "prepare")
        assertEquals(0, prepare(s, output).first)
        assertArrayEquals(facts, File(output, "facts.json").readBytes())
        val summary = Json.parseToJsonElement(File(output, "prepare.json").readText()).jsonObject
        assertEquals(0, summary.getValue("stories").jsonPrimitive.int)
        assertFalse(summary.getValue("ready_to_publish").jsonPrimitive.boolean)
        val signed = PackBuilder.build(D1Sample.AREA_ID, facts, narratives, keyId, "TEST_ONLY", s.commit, 20, pair.private)
        assertArrayEquals(ReleaseContainer.decode(signed.container).region.bytes,
            File(output, "candidate.unsigned").readBytes())
    }

    @Test fun existingOutputAndSentinelSurvive() {
        val s = source(); val output = temp.newFolder(); val sentinel = File(output, "sentinel")
        sentinel.writeText("preserve")
        assertNotEquals(0, prepare(s, output).first)
        assertEquals("preserve", sentinel.readText())
        assertEquals(listOf("sentinel"), output.list()!!.toList())
    }

    @Test fun unknownCommitAndTraversalFail() {
        val s = source(); val output = File(temp.root, "absent")
        refuses(prepare(s.copy(commit = "0".repeat(40)), output), "source_input_unavailable")
        refuses(prepare(s, output, "--input", "../outside"), "input_path")
        assertFalse(output.exists())
    }

    @Test fun previewPublicOnlyReportsZeroStoriesButNeverPublishable() {
        val result = preview()
        assertEquals(result.second, 0, result.first)
        assertTrue(result.second.contains("status=PREVIEW_TEST_ONLY"))
        assertTrue(result.second.contains("stories=0"))
        assertTrue(result.second.contains("ready_to_publish=false"))
        assertFalse(result.second.contains(temp.root.path))
    }

    @Test fun syntheticStoriesRemainTestOnly() {
        val result = preview(n = story)
        assertEquals(result.second, 0, result.first)
        assertTrue(result.second.contains("stories=2"))
        assertTrue(result.second.contains("ready_to_publish=false"))
    }

    @Test fun wrongHumanReleaseIntentIsRejected() {
        listOf("area" to "cn-510114", "commit" to "b".repeat(40), "factversion" to "wrong",
            "narrativeversion" to "wrong", "sequence" to "2").forEach { (field, value) ->
            refuses(preview(more = arrayOf("--$field", value)), "expected_$field")
        }
    }

    @Test fun tamperedPackAndRollbackAreRejected() {
        refuses(preview(mutate = { bytes -> bytes.copyOf().also {
            it[it.size / 2] = (it[it.size / 2].toInt() xor 1).toByte()
        } }), "bad_signature")
        refuses(preview(more = arrayOf("--minsequence", "2")), "sequence_rollback")
        refuses(preview(more = arrayOf("--appcode", "19")), "minimum_code")
    }

    @Test fun unknownPublicAnchorFails() {
        val dir = temp.newFolder()
        File(dir, "key.json").writeText("""{"key_id":"unknown-test-key","scope":"TEST_ONLY"}""")
        File(dir, "public.pem").writeText(Pem.publicPem(pair.public))
        refuses(preview(more = arrayOf("--keydir", dir.path)), "unknown_key_id")
    }

    @Test fun relabelledProductionAndProductionRequestAreRefused() {
        val dir = temp.newFolder()
        File(dir, "key.json").writeText("""{"key_id":"$keyId","scope":"PRODUCTION"}""")
        File(dir, "public.pem").writeText(Pem.publicPem(pair.public))
        refuses(preview(more = arrayOf("--keydir", dir.path)), "production_not_authorized")
        refuses(preview(more = arrayOf("--scope", "PRODUCTION")), "production_not_authorized")
        refuses(prepare(source(), File(temp.root, "no-output"), "--scope", "PRODUCTION"), "production_not_authorized")
    }

    @Test fun unknownRightsMissingSourcesAndClaimsFailBeforePrepareWrites() {
        listOf(facts.decodeToString().replace("CC-BY-4.0", "unknown").encodeToByteArray(),
            edit(facts) { JsonObject(it + ("sources" to JsonArray(emptyList()))) },
            edit(facts) { JsonObject(it + ("claims" to JsonArray(emptyList()))) }).forEachIndexed { i, bad ->
            val output = File(temp.root, "bad-$i")
            refuses(prepare(source(bad, story), output), "content_rejected")
            assertFalse(output.exists())
            refuses(preview(f = bad, n = story), "content_rejected")
        }
    }

    @Test fun pendingVisitorAndCandidateStoriesFailAtBothEntrypoints() {
        listOf(story.decodeToString().replace("\"passed\"", "\"pending\""),
            story.decodeToString().replace("\"approved\"", "\"candidate\"")).forEachIndexed { i, bad ->
            val n = bad.encodeToByteArray()
            val output = File(temp.root, "pending-$i")
            refuses(prepare(source(n = n), output), "content_rejected")
            assertFalse(output.exists())
            refuses(preview(n = n), "content_rejected")
        }
    }

    @Test fun keysInsideWorktreeOrRootAreRefusedWithoutFiles() {
        val s = source()
        listOf(s.root, File(s.root, "nested-keys"), File(s.root, "input/../keys")).forEach { directory ->
            refuses(cli("--command", "keygen", "--root", s.root.path, "--keydir", directory.path),
                "keydir_inside_worktree")
            assertFalse(File(directory, "private.pem").exists())
        }
    }

    @Test fun buildAlsoRunsContentGateBeforeSigningOrWriting() {
        val s = source(n = story.decodeToString().replace("\"approved\"", "\"candidate\"").encodeToByteArray())
        val keys = temp.newFolder()
        File(keys, "key.json").writeText("""{"key_id":"$keyId","scope":"TEST_ONLY"}""")
        File(keys, "public.pem").writeText(Pem.publicPem(pair.public))
        File(keys, "private.pem").writeText(Pem.privatePem(pair.private))
        val output = File(temp.root, "bad-build")
        refuses(cli("--command", "build", "--root", s.root.path, "--input", "input", "--commit", s.commit,
            "--keydir", keys.path, "--out", output.path), "content_rejected")
        assertFalse(output.exists())
    }

    @Test fun failedPreviewLeavesAnExistingApprovalFileUntouched() {
        val sentinel = temp.newFile(); sentinel.writeText("preserve")
        refuses(preview(more = arrayOf("--sequence", "9", "--json", sentinel.path)), "expected_sequence")
        assertEquals("preserve", sentinel.readText())
        assertNotEquals(0, preview(more = arrayOf("--json", sentinel.path)).first)
        assertEquals("preserve", sentinel.readText())
    }

    private lateinit var pipelineOutput: File
    private fun dryRun(s: Source, localKeyId: String = keyId, localPair: KeyPair = pair,
                       vararg more: String): Pair<Int, String> {
        val keys = temp.newFolder()
        File(keys, "key.json").writeText("""{"key_id":"$localKeyId","scope":"TEST_ONLY"}""")
        File(keys, "public.pem").writeText(Pem.publicPem(localPair.public))
        File(keys, "private.pem").writeText(Pem.privatePem(localPair.private))
        val prepared = PackBuilder.prepare(D1Sample.AREA_ID, File(s.root, "input/facts.json").readBytes(),
            File(s.root, "input/narratives.json").readBytes(), localKeyId, "TEST_ONLY", s.commit, 20)
        pipelineOutput = File(temp.root, "pipeline-${System.nanoTime()}")
        val options = linkedMapOf("command" to "release-dry-run", "root" to s.root.path, "input" to "input",
            "commit" to s.commit, "keydir" to keys.path, "out" to pipelineOutput.path,
            "confirmation" to "SIMULATED_TEST_ONLY", "reviewhash" to ContentDigest.sha256(prepared.region))
        more.toList().chunked(2).forEach { options[it[0].removePrefix("--")] = it[1] }
        return cli(*options.flatMap { listOf("--${it.key}", it.value) }.toTypedArray())
    }

    @Test fun singleSimulatedDecisionAutomaticallySignsVerifiesAndPlansUpload() {
        val result = dryRun(source(n = story))
        assertEquals(result.second, 0, result.first)
        val report = Json.parseToJsonElement(File(pipelineOutput, "release-preview.json").readText()).jsonObject
        assertEquals(1, report.getValue("simulated_human_decisions").jsonPrimitive.int)
        assertTrue(report.getValue("signature_verified").jsonPrimitive.boolean)
        assertFalse(report.getValue("upload_performed").jsonPrimitive.boolean)
        assertFalse(report.getValue("real_human_approval_recorded").jsonPrimitive.boolean)
        assertFalse(report.getValue("ready_to_publish").jsonPrimitive.boolean)
        assertEquals("<VERIFIED_PRODUCTION_FILE>", report.getValue("upload_argv_template").jsonArray[4].jsonPrimitive.content)
        assertTrue(File(pipelineOutput, "pack.kcpk").isFile)
    }

    @Test fun missingDecisionOrChangedReviewCannotSignOrEmitAnUploadPlan() {
        val s = source()
        refuses(dryRun(s, more = arrayOf("--confirmation", "not-confirmed")), "confirmation_required")
        assertFalse(pipelineOutput.exists())
        refuses(dryRun(s, more = arrayOf("--reviewhash", "0".repeat(64))), "review_changed")
        assertFalse(pipelineOutput.exists())
        refuses(dryRun(s, more = arrayOf("--scope", "PRODUCTION")), "production_not_authorized")
        assertFalse(pipelineOutput.exists())
    }

    @Test fun oneDecisionDoesNotOverrideRightsOrPendingStories() {
        val bad = source(n = story.decodeToString().replace("\"approved\"", "\"candidate\"").encodeToByteArray())
        val result = dryRun(bad)
        refuses(result, "content_rejected")
        assertTrue(result.second.contains("story-0:"))
        assertFalse(pipelineOutput.exists())
        val unclear = source(f = facts.decodeToString().replace("CC-BY-4.0", "unknown").encodeToByteArray())
        refuses(dryRun(unclear), "content_rejected")
        assertFalse(pipelineOutput.exists())
    }

    @Test fun eitherWorkstationCompletesWithItsOwnKeyAndSameContent() {
        val s = source()
        val second = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        assertEquals(0, dryRun(s, "test-only-office", pair).first)
        val firstBytes = File(pipelineOutput, "pack.kcpk").readBytes()
        assertEquals(0, dryRun(s, "test-only-home", second).first)
        val secondBytes = File(pipelineOutput, "pack.kcpk").readBytes()
        val trust = TrustStore(listOf(TrustAnchor("test-only-office", pair.public, AnchorScope.TEST_ONLY),
            TrustAnchor("test-only-home", second.public, AnchorScope.TEST_ONLY)))
        val verifier = ReleaseVerifier(trust, 20, D0CountyGate)
        val firstAccepted = verifier.verify(firstBytes) as ReleaseVerdict.Accepted
        val secondAccepted = verifier.verify(secondBytes) as ReleaseVerdict.Accepted
        assertEquals(firstAccepted.blocks.digest(), secondAccepted.blocks.digest())
        assertFalse(firstBytes.contentEquals(secondBytes))
    }

    @Test fun explicitLegalSubsetPassesWithoutApprovingOrMutatingBlockedOriginal() {
        val badFacts = facts.decodeToString().replaceFirst("CC-BY-4.0", "unknown").encodeToByteArray()
        val s = source(f = badFacts, n = story)
        refuses(prepare(s, File(temp.root, "blocked-full")), "content_rejected")
        val original = Json.parseToJsonElement(badFacts.decodeToString()).jsonObject
        val subset = JsonObject(original + mapOf(
            "content_version" to JsonPrimitive("TEST_ONLY-explicit-subset-1"),
            "sources" to JsonArray(original.getValue("sources").jsonArray.filter { it.jsonObject.getValue("source_id").jsonPrimitive.content == "s1" }),
            "source_permissions" to buildJsonObject { put("s1", "allowed") },
            "entities" to JsonArray(original.getValue("entities").jsonArray.filter { it.jsonObject.getValue("entity_id").jsonPrimitive.content == "e1" }),
            "claims" to JsonArray(emptyList()), "claim_permissions" to JsonObject(emptyMap())))
        File(s.root, "subset").mkdir()
        File(s.root, "subset/facts.json").writeText(subset.toString())
        File(s.root, "subset/narratives.json").writeBytes(narratives)
        fun git(vararg flags: String): String {
            val p = ProcessBuilder(listOf("git", "-C", s.root.path) + flags).redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            check(p.waitFor() == 0)
            return text.trim()
        }
        git("add", "subset"); git("commit", "-qm", "TEST_ONLY explicit legal subset")
        val committed = s.copy(commit = git("rev-parse", "HEAD"))
        val out = File(temp.root, "legal-subset")
        assertEquals(0, prepare(committed, out, "--input", "subset").first)
        val report = Json.parseToJsonElement(File(out, "prepare.json").readText()).jsonObject
        assertEquals(2, report.getValue("facts").jsonPrimitive.int)
        assertEquals(1, report.getValue("sources").jsonPrimitive.int)
        assertEquals(0, report.getValue("stories").jsonPrimitive.int)
        assertArrayEquals(badFacts, File(s.root, "input/facts.json").readBytes())
        refuses(prepare(committed, File(temp.root, "still-blocked-full")), "content_rejected")
    }

    private fun edit(bytes: ByteArray, transform: (JsonObject) -> JsonObject): ByteArray =
        transform(Json.parseToJsonElement(bytes.decodeToString()).jsonObject).toString().encodeToByteArray()
}
