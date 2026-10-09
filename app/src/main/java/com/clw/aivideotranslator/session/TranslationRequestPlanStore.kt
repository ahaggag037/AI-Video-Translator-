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

    /**
     * Bounded restart index. Hidden temp files from a process death are ignored; every published
     * visible path is strict and self-validating. Multiple historical request profiles may exist for
     * one unit, so callers still select the plan whose signature matches the durable receipt/entry.
     */
    fun list(sessionId: String): List<TranslationRequestPlan> = synchronized(writerLock) {
        require(isSafeId(sessionId)) { "invalid session id" }
        validateSession(sessionId)
        val plansRoot = File(sessionsRoot, "$sessionId/plans")
        if (!plansRoot.exists()) return@synchronized emptyList()
        require(plansRoot.isDirectory) { "request-plan root is not a directory" }
        val unitDirectories = plansRoot.listFiles()?.sortedBy { it.name } ?: emptyList()
        require(unitDirectories.size <= MAX_UNIT_DIRECTORIES) { "too many request-plan unit directories" }

        val result = mutableListOf<TranslationRequestPlan>()
        for (unitDirectory in unitDirectories) {
            require(unitDirectory.isDirectory && isSafeId(unitDirectory.name)) {
                "unexpected request-plan unit entry"
            }
            val visibleFiles = unitDirectory.listFiles()
                ?.filterNot { it.name.startsWith('.') && it.name.endsWith(".tmp") }
                ?.sortedBy { it.name }
                ?: emptyList()
            require(visibleFiles.size <= MAX_PLANS_PER_UNIT) { "too many request plans for unit" }
            for (file in visibleFiles) {
                require(file.isFile && file.name.endsWith(".json")) { "unexpected request-plan entry" }
                val signature = file.name.removeSuffix(".json")
                require(isSafeId(signature)) { "invalid request-plan file identity" }
                result += readUnlocked(sessionId, unitDirectory.name, signature)
                require(result.size <= MAX_PLANS_PER_SESSION) { "too many request plans in session" }
            }
        }
        result
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

    private companion object {
        const val MAX_UNIT_DIRECTORIES = 512
        const val MAX_PLANS_PER_UNIT = 16
        const val MAX_PLANS_PER_SESSION = 1_024
    }
}
