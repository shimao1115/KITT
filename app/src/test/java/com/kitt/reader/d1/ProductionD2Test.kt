package com.kitt.reader.d1

import com.kitt.packtool.*
import com.kitt.reader.countyRevision
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclEntryPermission

/** Everything lives in JUnit temporary directories. All gh/network commands are injected fakes.
 * Synthetic content and virtual registries never enter the real repository or official trust list. */
class ProductionD2Test {
    @get:Rule val temp = TemporaryFolder()

    private fun JsonObject.text(k: String) = getValue(k).jsonPrimitive.content
    private fun edit(raw: ByteArray, f: (JsonObject) -> JsonObject) =
        f(Json.parseToJsonElement(raw.decodeToString()).jsonObject).toString().encodeToByteArray()
    private fun revised(row: JsonObject, field: String) =
        JsonObject(row + (field to JsonPrimitive(countyRevision(row, field))))

    /** Rebuild every revision after changing the fixture labels, rather than faking revision PASS. */
    private fun content(stories: Boolean = true): Pair<ByteArray, ByteArray> {
        val f = Json.parseToJsonElement(D1Sample.facts().toString().replace("TEST_ONLY", "SIMULATED")).jsonObject
        val sources = f.getValue("sources").jsonArray.map { revised(it.jsonObject, "registry_revision") }
        val claims = f.getValue("claims").jsonArray.map { raw ->
            val c = raw.jsonObject
            val evidence = c.getValue("evidence").jsonArray.map { e ->
                val row = e.jsonObject
                JsonObject(row + ("accepted_source_revision" to sources.single { it.text("source_id") == row.text("source_id") }.getValue("registry_revision")))
            }
            revised(JsonObject(c + ("evidence" to JsonArray(evidence))), "revision")
        }
        val facts = JsonObject(f + mapOf("content_version" to JsonPrimitive("v1.0.0"),
            "sources" to JsonArray(sources), "claims" to JsonArray(claims)))
        val n = Json.parseToJsonElement((if (stories) D1Sample.storyNarratives() else D1Sample.emptyNarratives())
            .toString().replace("TEST_ONLY", "SIMULATED")).jsonObject
        val narratives = n.getValue("narratives").jsonArray.map { raw ->
            val s = raw.jsonObject
            JsonObject(s + mapOf("text_sha256" to JsonPrimitive(ContentDigest.sha256(s.text("text").encodeToByteArray())),
                "supported_claim_refs" to JsonArray(s.getValue("supported_claim_refs").jsonArray.map {
                    val ref = it.jsonObject
                    JsonObject(ref + ("accepted_revision" to claims.single { c -> c.text("claim_id") == ref.text("claim_id") }.getValue("revision")))
                })))
        }
        return facts.toString().encodeToByteArray() to JsonObject(n + mapOf("content_version" to JsonPrimitive("v1.0.0"),
            "narratives" to JsonArray(narratives))).toString().encodeToByteArray()
    }

    private inner class Fixture(val payload: Pair<ByteArray, ByteArray> = content()) {
        var root = temp.newFolder()
        val keys = File(temp.root, "identity-${System.nanoTime()}")
        val output = File(temp.root, "published-${System.nanoTime()}")
        lateinit var commit: String
        lateinit var hash: String
        var sequence = 1
        var decisions = 0
        var answer: String? = null
        var afterDecision: () -> Unit = {}
        var beforeUploadCheck: () -> Unit = {}
        var failure: String? = null
        var brokenReadback = false
        var remoteTags = ""
        var history: List<ReleaseManifest> = emptyList()
        var draftHistory = false
        var truncated = false
        val calls = mutableListOf<List<String>>()
        val uploads get() = calls.count { it.take(3) == listOf("gh", "release", "create") }
        var listCount = 0
        private var published: JsonObject? = null
        val runtime = D2Production.Runtime(command = ::fake, decision = {
            decisions++
            val input = ByteArrayInputStream(((answer ?: "可以发布 $hash") + "\n").encodeToByteArray())
            val result = input.bufferedReader().readLine()
            afterDecision()
            result
        }, simulated = true)

        init {
            git("init", "-q"); git("config", "user.name", "SIMULATED"); git("config", "user.email", "test@example.invalid")
            git("config", "core.autocrlf", "false")
            D2Production.initialize(options("identity-init"), runtime)
            File(root, "input").mkdir(); File(root, "config").mkdir()
            File(root, "input/facts.json").writeBytes(payload.first)
            File(root, "input/narratives.json").writeBytes(payload.second)
            File(root, "config/pack-publishers.json").writeText("[${File(keys, "registration.json").readText()}]")
            saveCommit()
        }
        fun git(vararg args: String) = D2Production.command(listOf("git", "-C", root.path) + args).trim()
        fun saveCommit() { git("add", "."); git("-c", "commit.gpgsign=false", "commit", "--allow-empty", "-qm", "SIMULATED source"); commit = git("rev-parse", "HEAD") }
        fun options(command: String, vararg extra: Pair<String, String>): D1Cli.Options {
            val flags = linkedMapOf("command" to command, "root" to root.path, "keydir" to keys.path,
                "input" to "input", "out" to output.path, "sequence" to sequence.toString())
            if (::commit.isInitialized) flags["commit"] = commit
            if (::hash.isInitialized) flags["reviewhash"] = hash
            flags.putAll(extra)
            return D1Cli.Options(flags.flatMap { listOf("--${it.key}", it.value) })
        }
        fun prepare() {
            val review = File(temp.root, "review-${System.nanoTime()}")
            assertEquals(0, D2Production.prepare(options("production-prepare", "out" to review.path), runtime))
            hash = Json.parseToJsonElement(File(review, "prepare.json").readText()).jsonObject.text("signed_region_sha256")
            assertEquals(ContentDigest.sha256(File(review, "candidate.unsigned").readBytes()), hash)
            assertTrue(File(review, "review.md").readText().contains(hash))
            assertFalse(File(review, "review.md").readText().contains("PRIVATE KEY"))
        }
        fun publish(vararg extra: Pair<String, String>) = D2Production.publish(options("publish", *extra), runtime)
        fun old(sequence: Int = 1, facts: ByteArray = payload.first, narratives: ByteArray = payload.second): ReleaseManifest =
            PackBuilder.prepare(D1Sample.AREA_ID, facts, narratives, "kitt-old-publisher", "PRODUCTION", commit, 20, sequence).manifest
        private fun record(m: ReleaseManifest, bytes: ByteArray? = null) = buildJsonObject {
            put("tagName", "kitt-pack-${m.areaId}-s${m.sequence}")
            put("body", m.canonicalBytes().decodeToString()); put("isDraft", false); put("isPrerelease", false)
            put("url", "https://github.com/shimao1115/KITT/releases/tag/kitt-pack-${m.areaId}-s${m.sequence}")
            put("assets", JsonArray(listOf(buildJsonObject {
                put("name", "${m.areaId}-s${m.sequence}.kcpk"); put("size", bytes?.size ?: 1)
                put("digest", "sha256:${if (brokenReadback) "0".repeat(64) else ContentDigest.sha256(bytes ?: byteArrayOf(1))}")
            })))
        }
        private fun fake(args: List<String>): String {
            calls += args
            if (args.take(3) == listOf("git", "ls-remote", "--tags")) {
                assertEquals(D2Production.REMOTE, args[3]); return remoteTags
            }
            if (args.first() != "gh") return D2Production.command(args)
            assertEquals(listOf("--repo", D2Production.REPO), args.takeLast(2))
            assertEquals("release", args[1])
            if (failure == args[2]) throw PackRejection("fake_gh_failure")
            return when (args[2]) {
                "list" -> {
                    listCount++
                    if (listCount == 2) beforeUploadCheck()
                    JsonArray((if (truncated) (1..1000).map { "v$it" to false } else history.map {
                        "kitt-pack-${it.areaId}-s${it.sequence}" to draftHistory
                    }).map { (tag, draft) -> buildJsonObject { put("tagName", tag); put("isDraft", draft); put("isPrerelease", false) } }).toString()
                }
                "view" -> published?.toString() ?: record(history.single { "kitt-pack-${it.areaId}-s${it.sequence}" == args[3] }).toString()
                "create" -> {
                    assertEquals(1, decisions)
                    val bytes = File(args[4]).readBytes()
                    val metadata = Json.parseToJsonElement(File(keys, "registration.json").readText()).jsonObject
                    val trust = TrustStore(listOf(TrustAnchor(metadata.text("key_id"), Pem.publicKey(metadata.text("public_key")), AnchorScope.PRODUCTION)))
                    val accepted = D2Production.checked(bytes, trust, hash, 20)
                    assertEquals("kitt-pack-${accepted.manifest.areaId}-s${accepted.manifest.sequence}", args[3])
                    assertEquals(commit, args[args.indexOf("--target") + 1])
                    assertFalse(args.any { it in setOf("--clobber", "--draft", "--prerelease") })
                    assertTrue(args.contains("--latest=false"))
                    assertEquals(accepted.manifest.canonicalBytes().decodeToString(), File(args[args.indexOf("--notes-file") + 1]).readText())
                    published = record(accepted.manifest, bytes)
                    remoteTags = "$commit\trefs/tags/${args[3]}\n"
                    "https://github.com/shimao1115/KITT/releases/tag/${args[3]}\n"
                }
                else -> error("Unexpected gh mutation")
            }
        }
    }

    private fun rejects(code: String, f: Fixture, uploaded: Int = 0, action: () -> Unit = { f.publish() }) {
        try { action(); fail("Expected $code") } catch (e: PackRejection) { assertEquals(e.detail, code, e.code) }
        assertEquals(uploaded, f.uploads)
        assertFalse(File(f.output, "publish.json").exists())
        println("expected_refusal=$code;fake_create_calls=$uploaded")
    }

    @Test fun oneSimulatedStdinDecisionSignsVerifiesAndPublishesWithFakeGh() {
        val f = Fixture(); f.prepare(); assertEquals(0, f.publish()); assertEquals(1, f.decisions); assertEquals(1, f.uploads)
        assertTrue(File(f.output, "publish.json").readText().contains("SIMULATED_PUBLISHED"))
        val argv = f.calls.single { it.take(3) == listOf("gh", "release", "create") }.mapIndexed { index, value ->
            when (index) { 4 -> "<VERIFIED_TEMP_PACK>"; 10 -> "<MANIFEST_NOTES>"; else -> value }
        }
        File("build/acceptance/d2-production-fake.json").apply { parentFile?.mkdirs() }.writeText(buildJsonObject {
            put("publish", Json.parseToJsonElement(File(f.output, "publish.json").readText()))
            put("gh_create_argv", JsonArray(argv.map(::JsonPrimitive)))
            put("private_material_exported", false); put("real_human_approval", false)
        }.toString())
    }
    @Test fun factOnlyProductionFlowKeepsZeroStories() {
        val f = Fixture(content(false)); f.prepare(); assertEquals(0, f.publish())
        val pack = File(f.output, "cn-420502-s1.kcpk").readBytes()
        val manifest = ReleaseManifest.parse(ReleaseContainer.decode(pack).region.manifestBytes)
        assertEquals("fact_only", manifest.contentKind)
    }
    @Test fun twoIndependentVirtualIdentitiesEachCompleteWithoutSharingPrivateKeys() {
        val office = Fixture(); val home = Fixture()
        File(office.root, "config/pack-publishers.json").writeText("[${File(office.keys, "registration.json").readText()},${File(home.keys, "registration.json").readText()}]")
        office.saveCommit()
        home.root = office.root; home.commit = office.commit
        office.prepare(); home.prepare(); assertEquals(0, office.publish()); assertEquals(0, home.publish())
        assertNotEquals(File(office.keys, "public.pem").readText(), File(home.keys, "public.pem").readText())
        assertArrayEquals(office.payload.first, home.payload.first)
        assertEquals(office.commit, home.commit); assertNotEquals(office.hash, home.hash)
        assertEquals(1, office.decisions); assertEquals(1, home.decisions)
    }
    @Test fun initializedIdentityIsOwnerOnlyAndCannotBeOverwritten() {
        val f = Fixture()
        val before = File(f.keys, "private.pem").readBytes()
        val acl = Files.getFileAttributeView(f.keys.toPath(), AclFileAttributeView::class.java)
        assertTrue(acl.acl.all { it.type() != AclEntryType.ALLOW || it.principal() == acl.owner })
        try { D2Production.initialize(f.options("identity-init"), f.runtime); fail() } catch (_: java.nio.file.FileAlreadyExistsException) { }
        assertArrayEquals(before, File(f.keys, "private.pem").readBytes())
    }
    @Test fun virtualIdentityCannotEnterTheRealDefaultRuntime() {
        val f = Fixture()
        rejects("virtual_identity_forbidden", f) { D2Production.prepare(f.options("production-prepare")) }
    }
    @Test fun identityInsideWorkingTreeRejectedBeforeKeyWrite() {
        val f = Fixture(); val path = File(f.root, "bad-key")
        rejects("keydir_inside_worktree", f) { D2Production.initialize(f.options("identity-init", "keydir" to path.path), f.runtime) }
        assertFalse(path.exists())
    }
    @Test fun identityInsideAnotherRegisteredWorktreeIsRejected() {
        val f = Fixture(); val linked = File(temp.root, "linked")
        f.git("worktree", "add", "--detach", linked.path, f.commit)
        rejects("keydir_inside_worktree", f) { D2Production.initialize(f.options("identity-init", "keydir" to File(linked, "keys").path), f.runtime) }
    }
    private fun junction(target: File): File {
        val alias = File(temp.root, "junction-${System.nanoTime()}")
        val p = ProcessBuilder("cmd.exe", "/c", "mklink", "/J", alias.path, target.path).redirectErrorStream(true).start()
        p.inputStream.use { it.readBytes() }
        assertEquals(0, p.waitFor())
        return alias
    }
    @Test fun junctionIntoWorkingTreeCannotHideKeyLocation() {
        val f = Fixture(); val alias = junction(f.root)
        rejects("keydir_inside_worktree", f) { D2Production.initialize(f.options("identity-init", "keydir" to File(alias, "keys").path), f.runtime) }
        assertFalse(File(f.root, "keys").exists())
    }
    @Test fun junctionToExternalIdentityChecksTargetDirectoryPermissions() {
        val f = Fixture(); val alias = junction(f.keys)
        val acl = Files.getFileAttributeView(f.keys.toPath(), AclFileAttributeView::class.java)
        val everyone = f.keys.toPath().fileSystem.userPrincipalLookupService.lookupPrincipalByName("Everyone")
        acl.acl = acl.acl + AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(everyone).setPermissions(AclEntryPermission.READ_DATA).build()
        rejects("identity_permissions", f) { D2Production.prepare(f.options("production-prepare", "keydir" to alias.path), f.runtime) }
    }
    @Test fun groupReadablePrivateKeyRejected() {
        val f = Fixture(); val path = File(f.keys, "private.pem").toPath()
        val acl = Files.getFileAttributeView(path, AclFileAttributeView::class.java)
        val everyone = path.fileSystem.userPrincipalLookupService.lookupPrincipalByName("Everyone")
        acl.acl = acl.acl + AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(everyone).setPermissions(AclEntryPermission.READ_DATA).build()
        rejects("identity_permissions", f) { f.prepare() }
    }
    @Test fun unknownKeyCannotSelfRegisterUsingLocalKeyFile() {
        val f = Fixture(); File(f.root, "config/pack-publishers.json").writeText("[]"); f.saveCommit()
        rejects("unknown_key_id", f) { f.prepare() }
    }
    @Test fun registryMustBeCommittedNotDirtyLocalTrust() {
        val f = Fixture(); val good = File(f.root, "config/pack-publishers.json").readText()
        File(f.root, "config/pack-publishers.json").writeText("[]"); f.saveCommit()
        File(f.root, "config/pack-publishers.json").writeText(good)
        rejects("unknown_key_id", f) { f.prepare() }
    }
    @Test fun mismatchingPinnedPublicKeyRejected() {
        val f = Fixture(); val other = Fixture()
        File(f.keys, "public.pem").writeText(File(other.keys, "public.pem").readText())
        rejects("publisher_key_mismatch", f) { f.prepare() }
    }
    @Test fun testOnlyIdentityCannotBePromotedByScopeArgument() {
        val f = Fixture(); File(f.keys, "key.json").writeText(File(f.keys, "key.json").readText().replace("PRODUCTION", "TEST_ONLY"))
        rejects("test_only_identity", f) { f.prepare() }
    }
    @Test fun testOnlyPayloadCannotBeUploaded() {
        val f = Fixture(D1Sample.facts().toString().encodeToByteArray() to D1Sample.emptyNarratives().toString().encodeToByteArray())
        rejects("test_only_content", f) { f.prepare() }
    }
    @Test fun testScopeCannotReachProductionUpload() {
        val f = Fixture(); f.prepare(); rejects("test_only_upload", f) { f.publish("scope" to "TEST_ONLY") }
    }
    @Test fun simulatedConfirmationFlagCannotReplaceHumanInput() {
        val f = Fixture(); f.prepare(); rejects("stdin_decision_required", f) { f.publish("confirmation" to "SIMULATED_TEST_ONLY") }
    }
    @Test fun refusingHumanInputDoesNotSignOrUpload() {
        val f = Fixture(); f.prepare(); f.answer = "不发布"; rejects("confirmation_required", f); assertEquals(1, f.decisions); assertFalse(f.output.exists())
    }
    @Test fun approvalForAnotherHashRejected() {
        val f = Fixture(); f.prepare(); f.answer = "可以发布 ${"0".repeat(64)}"; rejects("confirmation_required", f)
    }
    @Test fun reviewHashMustMatchBeforeReadingHumanInput() {
        val f = Fixture(); f.prepare(); rejects("review_changed", f) { f.publish("reviewhash" to "0".repeat(64)) }; assertEquals(0, f.decisions)
    }
    @Test fun differentSequenceChangesReviewedObject() {
        val f = Fixture(); f.prepare(); rejects("review_changed", f) { f.publish("sequence" to "2") }
    }
    @Test fun differentCountyChangesReviewedObject() {
        val payload = content(false)
        val f = Fixture(payload); f.prepare()
        File(f.root, "input/facts.json").writeText(payload.first.decodeToString().replace("cn-420502", "cn-420503"))
        File(f.root, "input/narratives.json").writeText(payload.second.decodeToString().replace("cn-420502", "cn-420503"))
        f.saveCommit(); rejects("review_changed", f)
    }
    @Test fun differentContentVersionChangesReviewedObject() {
        val f = Fixture(); f.prepare(); File(f.root, "input/facts.json").writeText(f.payload.first.decodeToString().replace("v1.0.0", "v2.0.0")); f.saveCommit()
        rejects("review_changed", f)
    }
    @Test fun dirtyFilesAreIgnoredAndTheFixedGitBlobIsReReadAfterDecision() {
        val f = Fixture(); f.prepare()
        f.afterDecision = { File(f.root, "input/facts.json").writeText("malicious dirty content") }
        assertEquals(0, f.publish()); assertEquals(1, f.decisions)
    }
    @Test fun identityChangedAfterDecisionRejectedBeforeSigning() {
        val f = Fixture(); val other = Fixture(); f.prepare()
        f.afterDecision = { File(f.keys, "public.pem").writeText(File(other.keys, "public.pem").readText()) }
        rejects("publisher_key_mismatch", f)
    }
    @Test fun duplicateSequenceRejectedWithoutHumanPrompt() {
        val f = Fixture(); f.prepare(); f.history = listOf(f.old()); rejects("release_sequence_rollback", f); assertEquals(0, f.decisions)
    }
    @Test fun olderSequenceRejected() {
        val f = Fixture(); f.prepare(); f.history = listOf(f.old(2)); rejects("release_sequence_rollback", f)
    }
    @Test fun previousFactVersionCannotBeRolledBackWithHigherSequence() {
        val f = Fixture(); f.sequence = 2; f.prepare()
        f.history = listOf(f.old(facts = f.payload.first.decodeToString().replace("v1.0.0", "v2.0.0").encodeToByteArray()))
        rejects("release_version_rollback", f)
    }
    @Test fun previousNarrativeVersionCannotBeRolledBack() {
        val f = Fixture(); f.sequence = 2; f.prepare()
        f.history = listOf(f.old(narratives = f.payload.second.decodeToString().replace("v1.0.0", "v2.0.0").encodeToByteArray()))
        rejects("release_version_rollback", f)
    }
    @Test fun sameVersionWithDifferentBytesIsRejected() {
        val f = Fixture(); f.sequence = 2; f.prepare()
        f.history = listOf(f.old(facts = (f.payload.first.decodeToString() + " ").encodeToByteArray()))
        rejects("release_version_reused", f)
    }
    @Test fun nextSequenceWithUnchangedContentPasses() {
        val f = Fixture(); f.sequence = 2; f.prepare(); f.history = listOf(f.old()); assertEquals(0, f.publish())
    }
    @Test fun tagWithoutReleaseCannotBeOverwritten() {
        val f = Fixture(); f.prepare(); f.remoteTags = "${f.commit}\trefs/tags/kitt-pack-cn-420502-s1\n"
        rejects("release_tag_conflict", f)
    }
    @Test fun incompleteOlderReleaseStopsPublication() {
        val f = Fixture(); f.sequence = 2; f.prepare(); f.remoteTags = "${f.commit}\trefs/tags/kitt-pack-cn-420502-s1\n"
        rejects("release_history_incomplete", f)
    }
    @Test fun draftReleaseReservesSequenceAndStopsPublication() {
        val f = Fixture(); f.sequence = 2; f.prepare(); f.history = listOf(f.old()); f.draftHistory = true
        rejects("release_history_incomplete", f)
    }
    @Test fun boundedHistoryCannotSilentlyIgnoreOlderRecords() {
        val f = Fixture(); f.prepare(); f.truncated = true; rejects("release_history_truncated", f)
    }
    @Test fun offlineOrUnauthorizedGhFailsWithoutCreatingAnything() {
        val f = Fixture(); f.prepare(); f.failure = "list"; rejects("fake_gh_failure", f); assertEquals(0, f.decisions)
    }
    @Test fun createFailureReportsUnknownOutcomeAndNeverRetries() {
        val f = Fixture(); f.prepare(); f.failure = "create"; rejects("release_outcome_unknown_check_remote", f, 1)
    }
    @Test fun failedPostCreateReadbackNeverClaimsSuccess() {
        val f = Fixture(); f.prepare(); f.failure = "view"; rejects("release_outcome_unknown_check_remote", f, 1)
    }
    @Test fun wrongRemoteAssetDigestNeverClaimsSuccess() {
        val f = Fixture(); f.prepare(); f.brokenReadback = true; rejects("release_outcome_unknown_check_remote", f, 1)
    }
    @Test fun tamperedStagedSignatureRejectedBeforeGhCreate() {
        val f = Fixture(); f.prepare()
        f.beforeUploadCheck = {
            val pack = File(f.output, "cn-420502-s1.kcpk"); val bytes = pack.readBytes(); bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte(); pack.writeBytes(bytes)
        }
        rejects("bad_signature", f)
    }
    @Test fun changedReleaseNotesRejectedBeforeGhCreate() {
        val f = Fixture(); f.prepare(); f.beforeUploadCheck = { File(f.output, "manifest.json").writeText("changed") }
        rejects("release_notes_changed", f)
    }
    @Test fun existingOutputIsNeverOverwritten() {
        val f = Fixture(); f.prepare(); f.output.mkdir(); val sentinel = File(f.output, "sentinel"); sentinel.writeText("keep")
        try { f.publish(); fail() } catch (_: java.nio.file.FileAlreadyExistsException) { }
        assertEquals("keep", sentinel.readText()); assertEquals(0, f.uploads)
    }
    @Test fun unknownCopyrightIsRejected() {
        val p = content(); val f = Fixture(p.first.decodeToString().replaceFirst("CC-BY-4.0", "unknown").encodeToByteArray() to p.second)
        rejects("content_rejected", f) { f.prepare() }
    }
    @Test fun missingSourceIsRejected() {
        val p = content(); val f = Fixture(edit(p.first) { JsonObject(it + ("sources" to JsonArray(emptyList()))) } to p.second)
        rejects("content_rejected", f) { f.prepare() }
    }
    @Test fun missingClaimIsRejected() {
        val p = content(); val f = Fixture(edit(p.first) { JsonObject(it + ("claims" to JsonArray(emptyList()))) } to p.second)
        rejects("content_rejected", f) { f.prepare() }
    }
    @Test fun candidateStoryIsRejected() {
        val p = content(); val f = Fixture(p.first to p.second.decodeToString().replace("\"approved\"", "\"candidate\"").encodeToByteArray())
        rejects("content_rejected", f) { f.prepare() }
    }
    @Test fun visitorPendingStoryIsRejected() {
        val p = content(); val f = Fixture(p.first to p.second.decodeToString().replace("\"visitor_gate\":\"passed\"", "\"visitor_gate\":\"pending\"").encodeToByteArray())
        rejects("content_rejected", f) { f.prepare() }
    }
    @Test fun privateKeyMismatchIsDetectedByThePinnedVerifier() {
        val f = Fixture(); val other = Fixture(); f.prepare()
        File(f.keys, "private.pem").writeText(File(other.keys, "private.pem").readText())
        rejects("bad_signature", f)
    }
    @Test fun newRemoteConflictAfterDecisionStopsBeforeCreate() {
        val f = Fixture(); f.prepare(); f.beforeUploadCheck = { f.history = listOf(f.old()) }
        rejects("release_sequence_rollback", f); assertEquals(1, f.decisions)
    }
    @Test fun unrelatedSourceCommitIsRejectedAsRollback() {
        val f = Fixture(); f.sequence = 2; f.prepare()
        f.history = listOf(f.old().copy(sourceCommit = "0".repeat(40)))
        rejects("release_source_rollback_or_missing", f)
    }
    @Test fun publicRegistrationWithTestScopeIsRejected() {
        val f = Fixture()
        val registry = File(f.root, "config/pack-publishers.json")
        registry.writeText(registry.readText().replace("PRODUCTION", "TEST_ONLY")); f.saveCommit()
        rejects("publisher_registry_scope", f) { f.prepare() }
    }
    @Test fun invalidProductionVersionIsRejected() {
        val p = content(); val f = Fixture(p.first.decodeToString().replace("v1.0.0", "unknown-version").encodeToByteArray() to p.second)
        rejects("production_version", f) { f.prepare() }
    }
    @Test fun subprocessMissingExecutableIsClearAndSafe() {
        try { D2Production.command(listOf("kitt-no-such-executable-${System.nanoTime()}")); fail() }
        catch (e: PackRejection) { assertEquals("command_unavailable", e.code) }
    }
    @Test fun subprocessAuthenticationFailureIsClassifiedWithoutPrintingStderr() {
        try { D2Production.command(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "[Console]::Error.WriteLine('HTTP 403 secret-not-for-logs'); exit 1")); fail() }
        catch (e: PackRejection) { assertEquals("command_authentication", e.code); assertEquals("", e.detail) }
    }
    @Test fun subprocessOfflineFailureIsClassified() {
        try { D2Production.command(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "[Console]::Error.WriteLine('could not resolve host'); exit 1")); fail() }
        catch (e: PackRejection) { assertEquals("command_network", e.code) }
    }
    @Test fun subprocessTimeoutStopsAndDoesNotBecomeSuccess() {
        try { D2Production.command(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", "Start-Sleep -Seconds 20"), 1); fail() }
        catch (e: PackRejection) { assertEquals("command_timeout", e.code) }
    }
    @Test fun evenCorrectlySignedTestOnlyContainerIsRejectedAtUploadBoundary() {
        val f = Fixture()
        val pair = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val built = PackBuilder.build(D1Sample.AREA_ID, D1Sample.facts().toString().encodeToByteArray(),
            D1Sample.emptyNarratives().toString().encodeToByteArray(), "test-only-boundary", "TEST_ONLY", f.commit, 20, pair.private)
        val trust = TrustStore(listOf(TrustAnchor("test-only-boundary", pair.public, AnchorScope.TEST_ONLY)))
        rejects("test_only_upload", f) { D2Production.checked(built.container, trust, "0".repeat(64), 20) }
    }
}
