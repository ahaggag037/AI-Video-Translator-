package com.clw.aivideotranslator.session

import java.nio.ByteBuffer
import java.security.MessageDigest

data class RecoveryRevisionIds(
    val machineRevisionId: String,
    val entryRevisionId: String,
)

object RecoveryRevisionIdentity {
    private const val DOMAIN = "tv1-x005-recovery-v1"

    fun forReceipt(receipt: RequestReceipt): RecoveryRevisionIds {
        val digest = MessageDigest.getInstance("SHA-256")
        listOf(
            DOMAIN,
            receipt.sessionId,
            receipt.unitId,
            receipt.attemptId,
            receipt.epoch.toString(),
            receipt.requestSignature,
            receipt.expectedManifestRevision.toString(),
            receipt.expectedActiveEntryRevisionId.orEmpty(),
        ).forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        val identity = digest.digest().joinToString("") { "%02x".format(it) }
        return RecoveryRevisionIds(
            machineRevisionId = "m-rec-$identity",
            entryRevisionId = "e-rec-$identity",
        )
    }
}
