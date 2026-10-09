package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.TranslationRequestPlan
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Immutable private storage for exact translation request plans. It intentionally does not touch the
 * session manifest: a plan is derived evidence. Publication happens before PREPARED; a crash may
 * leave an orphan plan, which is safe because receipts/manifest remain the authority for execution.
 */
internal class TranslationRequestPlanStore(
    private val sessionsRoot: File,
    private val validateSession: (String) -> Unit,
) {
    private val writerLock = Any()

    init {
        require(sessionsRoot.mkdirs() || sessionsRoot.isDirectory) { "cannot create session root" }
    }

    fun publish(sessionId: String, plan: TranslationRequestPlan): TranslationRequestPlan = synchronized(writerLock) {
        require(isSafeId(sessionId) && isSafeId(plan.unitId) && isSafeId(plan.requestSignature)) {
            "invalid request-plan identity"
        }
        validateSession(sessionId)
        val directory = File(sessionsRoot, "$sessionId/plans/${plan.unitId}").apply {
            require(mkdirs() || isDirectory) { "cannot create request-plan directory" }
        }
        val destination = File(directory, "${plan.requestSignature}.json")
        val encoded = TranslationRequestPlanCodec.encode(plan)
        val bytes = encoded.toByteArray(Charsets.UTF_8)
        if (destination.exists()) {
            val existing = readUnlocked(sessionId, plan.unitId, plan.requestSignature)
            require(existing == plan) { "immutable request-plan collision" }
            return@synchronized existing
        }

        val temp = File(directory, ".${plan.requestSignature}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            check(temp.renameTo(destination)) { "cannot publish request plan" }
        } finally {
            if (temp.exists()) temp.delete()
        }
        plan
    }

    fun read(sessionId: String, unitId: String, requestSignature: String): TranslationRequestPlan =
        synchronized(writerLock) {
            require(isSafeId(sessionId) && isSafeId(unitId) && isSafeId(requestSignature)) {
                "invalid request-plan identity"
            }
            validateSession(sessionId)
            readUnlocked(sessionId, unitId, requestSignature)
        }

    private fun readUnlocked(
        sessionId: String,
        unitId: String,
        requestSignature: String,
    ): TranslationRequestPlan {
        val file = File(sessionsRoot, "$sessionId/plans/$unitId/$requestSignature.json")
        require(file.isFile) { "request plan missing" }
        val bytes = file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4_096)
            while (true) {
                val remaining = TranslationRequestPlanCodec.MAX_BYTES + 1 - output.size()
                require(remaining > 0) { "request plan exceeds size limit" }
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count == -1) break
                output.write(buffer, 0, count)
                require(output.size() <= TranslationRequestPlanCodec.MAX_BYTES) {
                    "request plan exceeds size limit"
                }
            }
            output.toByteArray()
        }
        val plan = TranslationRequestPlanCodec.decode(bytes.toString(Charsets.UTF_8))
        require(plan.unitId == unitId && plan.requestSignature == requestSignature) {
            "request-plan path identity mismatch"
        }
        return plan
    }
}
