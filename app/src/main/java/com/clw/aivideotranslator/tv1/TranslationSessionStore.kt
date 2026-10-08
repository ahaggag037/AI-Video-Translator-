package com.clw.aivideotranslator.tv1

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Minimal single-writer durable store for TV1.
 *
 * This is deliberately not a custom database. It provides only:
 * - one atomic manifest per session;
 * - optimistic revision fencing for manifest updates;
 * - immutable JSON artifacts under the session directory.
 *
 * If TV1 later needs relational queries or true multi-writer transactions, Room must be
 * reconsidered instead of expanding this class into a database engine.
 */
class TranslationSessionStore(
    private val rootDir: File,
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    companion object {
        private const val MANIFEST_NAME = "manifest.json"
        private val SESSION_ID = Regex("[A-Za-z0-9_-]{1,96}")

        fun from(context: Context): TranslationSessionStore = TranslationSessionStore(
            File(context.filesDir, "translation_sessions")
        )
    }

    init {
        require(rootDir.exists() || rootDir.mkdirs()) { "cannot create translation session root" }
        require(rootDir.isDirectory) { "translation session root is not a directory" }
    }

    @Synchronized
    fun create(source: SourceAssetRef, requestedSessionId: String? = null): TranslationSessionManifest {
        val sessionId = requestedSessionId ?: UUID.randomUUID().toString()
        validateSessionId(sessionId)
        val dir = sessionDir(sessionId)
        require(!dir.exists()) { "translation session already exists" }
        require(dir.mkdirs()) { "cannot create translation session directory" }

        val now = nowEpochMs()
        require(now >= 0L) { "clock returned negative epoch time" }
        val manifest = TranslationSessionManifest(
            sessionId = sessionId,
            source = source,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            revision = 0L,
        )
        writeManifestFile(dir, manifest)
        return manifest
    }

    @Synchronized
    fun load(sessionId: String): TranslationSessionManifest? {
        validateSessionId(sessionId)
        val file = File(sessionDir(sessionId), MANIFEST_NAME)
        if (!file.exists()) return null
        return decodeManifest(readAtomicUtf8(file))
    }

    /**
     * Compare-and-swap style manifest update. A stale callback cannot overwrite a newer
     * manifest revision because expectedRevision must still match at commit time.
     */
    @Synchronized
    fun update(
        sessionId: String,
        expectedRevision: Long,
        transform: (TranslationSessionManifest) -> TranslationSessionManifest,
    ): TranslationSessionManifest {
        require(expectedRevision >= 0L) { "expected revision must be non-negative" }
        val current = load(sessionId) ?: error("translation session not found")
        require(current.revision == expectedRevision) {
            "stale session manifest revision: expected=$expectedRevision actual=${current.revision}"
        }

        val candidate = transform(current)
        require(candidate.sessionId == current.sessionId) { "session id is immutable" }
        require(candidate.source == current.source) { "source identity is immutable in-place" }
        require(candidate.createdAtEpochMs == current.createdAtEpochMs) { "creation time is immutable" }
        require(candidate.revision == current.revision) {
            "transform must not mutate revision directly"
        }

        val now = maxOf(nowEpochMs(), current.updatedAtEpochMs)
        val committed = candidate.copy(
            updatedAtEpochMs = now,
            revision = Math.addExact(current.revision, 1L),
        )
        writeManifestFile(sessionDir(sessionId), committed)
        return committed
    }

    /**
     * Writes a durable immutable JSON artifact. Existing artifacts are never replaced;
     * revisions should use a new path/id.
     */
    @Synchronized
    fun writeImmutableJson(sessionId: String, relativePath: String, json: JSONObject): File {
        validateSessionId(sessionId)
        val target = safeArtifactFile(sessionDir(sessionId), relativePath)
        require(!target.exists()) { "immutable session artifact already exists" }
        val parent = requireNotNull(target.parentFile)
        require(parent.exists() || parent.mkdirs()) { "cannot create artifact directory" }
        writeAtomicUtf8(target, json.toString())
        return target
    }

    @Synchronized
    fun readJson(sessionId: String, relativePath: String): JSONObject? {
        validateSessionId(sessionId)
        val target = safeArtifactFile(sessionDir(sessionId), relativePath)
        if (!target.exists()) return null
        return JSONObject(readAtomicUtf8(target))
    }

    private fun sessionDir(sessionId: String): File {
        validateSessionId(sessionId)
        return File(rootDir, sessionId)
    }

    private fun safeArtifactFile(sessionDir: File, relativePath: String): File {
        require(relativePath.isNotBlank()) { "artifact path must not be blank" }
        require(!File(relativePath).isAbsolute) { "artifact path must be relative" }
        require(!relativePath.split('/', '\\').any { it == ".." || it.isBlank() }) {
            "artifact path traversal is not allowed"
        }
        require(relativePath != MANIFEST_NAME) { "manifest is managed separately" }
        val canonicalDir = sessionDir.canonicalFile
        val target = File(sessionDir, relativePath).canonicalFile
        require(target.path.startsWith(canonicalDir.path + File.separator)) {
            "artifact path escapes session directory"
        }
        return target
    }

    private fun validateSessionId(sessionId: String) {
        require(SESSION_ID.matches(sessionId)) { "invalid translation session id" }
    }

    private fun writeManifestFile(dir: File, manifest: TranslationSessionManifest) {
        require(dir.exists() && dir.isDirectory) { "translation session directory missing" }
        writeAtomicUtf8(File(dir, MANIFEST_NAME), encodeManifest(manifest).toString())
    }

    private fun writeAtomicUtf8(file: File, text: String) {
        val atomic = AtomicFile(file)
        var output: FileOutputStream? = null
        try {
            output = atomic.startWrite()
            output.write(text.toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
            atomic.finishWrite(output)
            output = null
        } finally {
            if (output != null) atomic.failWrite(output)
        }
    }

    private fun readAtomicUtf8(file: File): String =
        AtomicFile(file).readFully().toString(StandardCharsets.UTF_8)

    private fun encodeManifest(manifest: TranslationSessionManifest): JSONObject = JSONObject()
        .put("schemaVersion", manifest.schemaVersion)
        .put("sessionId", manifest.sessionId)
        .put("source", JSONObject()
            .put("uri", manifest.source.uri)
            .put("displayName", manifest.source.displayName ?: JSONObject.NULL)
            .put("durationUs", manifest.source.durationUs ?: JSONObject.NULL))
        .put("createdAtEpochMs", manifest.createdAtEpochMs)
        .put("updatedAtEpochMs", manifest.updatedAtEpochMs)
        .put("revision", manifest.revision)
        .put("sourceSnapshotId", manifest.sourceSnapshotId ?: JSONObject.NULL)
        .put("segmentationId", manifest.segmentationId ?: JSONObject.NULL)

    private fun decodeManifest(root: JSONObject): TranslationSessionManifest {
        require(root.getInt("schemaVersion") == 1) { "unsupported session manifest schema" }
        val sourceJson = root.getJSONObject("source")
        val source = SourceAssetRef(
            uri = sourceJson.getString("uri"),
            displayName = sourceJson.nullableString("displayName"),
            durationUs = sourceJson.nullableLong("durationUs"),
        )
        return TranslationSessionManifest(
            schemaVersion = 1,
            sessionId = root.getString("sessionId"),
            source = source,
            createdAtEpochMs = root.getLong("createdAtEpochMs"),
            updatedAtEpochMs = root.getLong("updatedAtEpochMs"),
            revision = root.getLong("revision"),
            sourceSnapshotId = root.nullableString("sourceSnapshotId"),
            segmentationId = root.nullableString("segmentationId"),
        )
    }

    private fun JSONObject.nullableString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun JSONObject.nullableLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else getLong(name)
}
