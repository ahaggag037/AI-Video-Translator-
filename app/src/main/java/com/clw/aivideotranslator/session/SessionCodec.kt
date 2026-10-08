package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.provider.ContentValidationOutcome
import com.clw.aivideotranslator.provider.PolicyOutcome
import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TransportOutcome
import com.clw.aivideotranslator.semantic.MachineTranslationRevision
import com.clw.aivideotranslator.semantic.ManualTranslationRevision
import com.clw.aivideotranslator.semantic.TranslationRecord
import com.clw.aivideotranslator.semantic.TranslationReviewState
import org.json.JSONArray
import org.json.JSONObject

object SessionCodec {
    const val MANIFEST_MAX_BYTES = 1_048_576
    const val ENTRY_MAX_BYTES = 262_144
    const val RECEIPT_MAX_BYTES = 262_144

    fun encodeManifest(manifest: SessionManifest): String = JSONObject()
        .put("schemaVersion", manifest.schemaVersion)
        .put("sessionId", manifest.sessionId)
        .put("revision", manifest.revision)
        .put("epoch", manifest.epoch)
        .put("activeEntryRefs", JSONObject().apply {
            manifest.activeEntryRefs.toSortedMap().forEach { (unitId, revisionId) -> put(unitId, revisionId) }
        })
        .put("sourceBindingState", manifest.sourceBindingState.name)
        .put("activeSourceAttachmentRef", manifest.activeSourceAttachmentRef ?: JSONObject.NULL)
        .put("activeSourceSnapshotRef", manifest.activeSourceSnapshotRef ?: JSONObject.NULL)
        .toString()

    /**
     * Decodes manifest schema v2 and migrates legacy v1 manifests in memory. A v1 manifest had no
     * durable source ownership, so migration is intentionally explicit LEGACY_UNBOUND rather than
     * pretending that source-dependent work can be resumed safely.
     */
    fun decodeManifest(json: String): SessionManifest {
        requireUtf8Size(json, MANIFEST_MAX_BYTES, "manifest")
        val root = JSONObject(json)
        require(root.has("schemaVersion")) { "manifest missing schemaVersion" }
        val sourceSchema = root.getInt("schemaVersion")
        require(sourceSchema == 1 || sourceSchema == TRANSLATION_MANIFEST_SCHEMA_VERSION) {
            "unsupported manifest schema"
        }
        val refsObject = root.optJSONObject("activeEntryRefs") ?: JSONObject()
        val refs = buildMap {
            val keys = refsObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, refsObject.getString(key))
            }
        }
        val sourceState: SourceBindingState
        val attachmentRef: String?
        val snapshotRef: String?
        if (sourceSchema == 1) {
            sourceState = SourceBindingState.LEGACY_UNBOUND
            attachmentRef = null
            snapshotRef = null
        } else {
            require(root.has("sourceBindingState")) { "manifest missing sourceBindingState" }
            sourceState = SourceBindingState.valueOf(root.getString("sourceBindingState"))
            attachmentRef = root.optionalString("activeSourceAttachmentRef")
            snapshotRef = root.optionalString("activeSourceSnapshotRef")
        }
        return SessionManifest(
            schemaVersion = TRANSLATION_MANIFEST_SCHEMA_VERSION,
            sessionId = root.getString("sessionId"),
            revision = root.getLong("revision"),
            epoch = root.getLong("epoch"),
            activeEntryRefs = refs,
            sourceBindingState = sourceState,
            activeSourceAttachmentRef = attachmentRef,
            activeSourceSnapshotRef = snapshotRef,
        )
    }

    fun encodeEntry(entry: StoredTranslationEntry): String {
        val record = entry.record
        val root = JSONObject()
            .put("schemaVersion", entry.schemaVersion)
            .put("revisionId", entry.revisionId)
            .put("unitId", record.unitId)
            .put("activeMachineRevisionId", record.activeMachineRevisionId ?: JSONObject.NULL)
            .put("reviewState", record.reviewState.name)
            .put("machineRevisions", JSONArray().apply {
                record.machineRevisions.forEach { machine ->
                    put(JSONObject()
                        .put("id", machine.id)
                        .put("text", machine.text)
                        .put("requestSignature", machine.requestSignature))
                }
            })
        record.manualRevision?.let { manual ->
            root.put("manualRevision", JSONObject()
                .put("id", manual.id)
                .put("text", manual.text)
                .put("basedOnSourceTextHash", manual.basedOnSourceTextHash)
                .put("basedOnMachineRevisionId", manual.basedOnMachineRevisionId ?: JSONObject.NULL))
        }
        return root.toString().also { requireUtf8Size(it, ENTRY_MAX_BYTES, "entry") }
    }

    fun decodeEntry(json: String): StoredTranslationEntry {
        requireUtf8Size(json, ENTRY_MAX_BYTES, "entry")
        val root = JSONObject(json)
        val schema = root.getInt("schemaVersion")
        require(schema == TRANSLATION_ENTRY_SCHEMA_VERSION) { "unsupported entry schema" }
        val machinesJson = root.getJSONArray("machineRevisions")
        val machines = (0 until machinesJson.length()).map { index ->
            val machine = machinesJson.getJSONObject(index)
            MachineTranslationRevision(
                id = machine.getString("id"),
                text = machine.getString("text"),
                requestSignature = machine.getString("requestSignature"),
            )
        }
        val manual = root.optJSONObject("manualRevision")?.let { value ->
            ManualTranslationRevision(
                id = value.getString("id"),
                text = value.getString("text"),
                basedOnSourceTextHash = value.getString("basedOnSourceTextHash"),
                basedOnMachineRevisionId = value.optionalString("basedOnMachineRevisionId"),
            )
        }
        val record = TranslationRecord(
            unitId = root.getString("unitId"),
            machineRevisions = machines,
            activeMachineRevisionId = root.optionalString("activeMachineRevisionId"),
            manualRevision = manual,
            reviewState = TranslationReviewState.valueOf(root.getString("reviewState")),
        )
        return StoredTranslationEntry(
            schemaVersion = schema,
            revisionId = root.getString("revisionId"),
            record = record,
        )
    }

    fun encodeReceipt(receipt: RequestReceipt): String {
        val root = JSONObject()
            .put("schemaVersion", receipt.schemaVersion)
            .put("attemptId", receipt.attemptId)
            .put("sessionId", receipt.sessionId)
            .put("unitId", receipt.unitId)
            .put("epoch", receipt.epoch)
            .put("requestSignature", receipt.requestSignature)
            .put("expectedManifestRevision", receipt.expectedManifestRevision)
            .put("expectedActiveEntryRevisionId", receipt.expectedActiveEntryRevisionId ?: JSONObject.NULL)
            .put("phase", receipt.phase.name)
        receipt.outcome?.let { outcome ->
            root.put("outcome", JSONObject()
                .put("transport", outcome.transport.name)
                .put("protocol", outcome.protocol.name)
                .put("policy", outcome.policy.name)
                .put("contentValidation", outcome.contentValidation.name)
                .put("candidateText", outcome.candidateText ?: JSONObject.NULL)
                .put("httpStatus", outcome.httpStatus ?: JSONObject.NULL)
                .put("requestId", outcome.requestId ?: JSONObject.NULL)
                .put("resolvedModel", outcome.resolvedModel ?: JSONObject.NULL)
                .put("finishReason", outcome.finishReason ?: JSONObject.NULL)
                .put("retryAfterMs", outcome.retryAfterMs ?: JSONObject.NULL)
                .put("diagnosticCode", outcome.diagnosticCode ?: JSONObject.NULL))
        }
        return root.toString().also { requireUtf8Size(it, RECEIPT_MAX_BYTES, "receipt") }
    }

    fun decodeReceipt(json: String): RequestReceipt {
        requireUtf8Size(json, RECEIPT_MAX_BYTES, "receipt")
        val root = JSONObject(json)
        val schema = root.getInt("schemaVersion")
        require(schema == TRANSLATION_RECEIPT_SCHEMA_VERSION) { "unsupported receipt schema" }
        val outcome = root.optJSONObject("outcome")?.let { value ->
            TranslationProviderOutcome(
                transport = TransportOutcome.valueOf(value.getString("transport")),
                protocol = ProtocolOutcome.valueOf(value.getString("protocol")),
                policy = PolicyOutcome.valueOf(value.getString("policy")),
                contentValidation = ContentValidationOutcome.valueOf(value.getString("contentValidation")),
                candidateText = value.optionalString("candidateText"),
                httpStatus = value.optionalInt("httpStatus"),
                requestId = value.optionalString("requestId"),
                resolvedModel = value.optionalString("resolvedModel"),
                finishReason = value.optionalString("finishReason"),
                retryAfterMs = value.optionalLong("retryAfterMs"),
                diagnosticCode = value.optionalString("diagnosticCode"),
            )
        }
        return RequestReceipt(
            schemaVersion = schema,
            attemptId = root.getString("attemptId"),
            sessionId = root.getString("sessionId"),
            unitId = root.getString("unitId"),
            epoch = root.getLong("epoch"),
            requestSignature = root.getString("requestSignature"),
            expectedManifestRevision = root.getLong("expectedManifestRevision"),
            expectedActiveEntryRevisionId = root.optionalString("expectedActiveEntryRevisionId"),
            phase = RequestReceiptPhase.valueOf(root.getString("phase")),
            outcome = outcome,
        )
    }

    private fun JSONObject.optionalString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun JSONObject.optionalLong(name: String): Long? =
        if (!has(name) || isNull(name)) null else getLong(name)

    private fun JSONObject.optionalInt(name: String): Int? =
        if (!has(name) || isNull(name)) null else getInt(name)

    private fun requireUtf8Size(value: String, maxBytes: Int, label: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= maxBytes) { "$label exceeds size limit" }
    }
}
