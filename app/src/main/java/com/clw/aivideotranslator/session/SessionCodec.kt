package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.MachineTranslationRevision
import com.clw.aivideotranslator.semantic.ManualTranslationRevision
import com.clw.aivideotranslator.semantic.TranslationRecord
import com.clw.aivideotranslator.semantic.TranslationReviewState
import org.json.JSONArray
import org.json.JSONObject

object SessionCodec {
    const val MANIFEST_MAX_BYTES = 1_048_576
    const val ENTRY_MAX_BYTES = 262_144

    fun encodeManifest(manifest: SessionManifest): String = JSONObject()
        .put("schemaVersion", manifest.schemaVersion)
        .put("sessionId", manifest.sessionId)
        .put("revision", manifest.revision)
        .put("epoch", manifest.epoch)
        .put("activeEntryRefs", JSONObject().apply {
            manifest.activeEntryRefs.toSortedMap().forEach { (unitId, revisionId) ->
                put(unitId, revisionId)
            }
        })
        .toString()

    fun decodeManifest(json: String): SessionManifest {
        requireUtf8Size(json, MANIFEST_MAX_BYTES, "manifest")
        val root = JSONObject(json)
        require(root.has("schemaVersion")) { "manifest missing schemaVersion" }
        val schema = root.getInt("schemaVersion")
        require(schema == TRANSLATION_SESSION_SCHEMA_VERSION) { "unsupported session schema" }
        val refsObject = root.optJSONObject("activeEntryRefs") ?: JSONObject()
        val refs = buildMap {
            val keys = refsObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, refsObject.getString(key))
            }
        }
        return SessionManifest(
            schemaVersion = schema,
            sessionId = root.getString("sessionId"),
            revision = root.getLong("revision"),
            epoch = root.getLong("epoch"),
            activeEntryRefs = refs,
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
        require(schema == TRANSLATION_SESSION_SCHEMA_VERSION) { "unsupported entry schema" }
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

    private fun JSONObject.optionalString(name: String): String? =
        if (!has(name) || isNull(name)) null else getString(name)

    private fun requireUtf8Size(value: String, maxBytes: Int, label: String) {
        require(value.toByteArray(Charsets.UTF_8).size <= maxBytes) { "$label exceeds size limit" }
    }
}
