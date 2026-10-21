package com.clw.aivideotranslator.editor

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTextStateStoreTest {
    private fun createStoreRoot(): File = Files.createTempDirectory("editor-text-store").toFile().also { root ->
        check(File(root, "s1").mkdirs())
    }

    private fun store(root: File) = EditorTextStateStore(root) { _ -> Unit }

    @Test fun pendingDraftIsDurableButNotExportTruth() {
        val root = createStoreRoot()
        try {
            val store = store(root)
            store.setPendingDraft("s1", "u1", "machine text", "مسودة")

            val reopened = store(root).readUnit("s1", "u1")
            assertEquals("مسودة", reopened.pendingDraft)
            assertEquals("مسودة", reopened.visibleText("machine text"))
            assertEquals("machine text", reopened.exportText("machine text"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun committedManualRevisionBecomesDurableExportTruth() {
        val root = createStoreRoot()
        try {
            val store = store(root)
            store.setPendingDraft("s1", "u1", "machine text", "تصحيح يدوي")
            store.commitPendingDraft("s1", "u1", "source text", "machine text")

            val reopened = store(root).readUnit("s1", "u1")
            assertNull(reopened.pendingDraft)
            assertEquals("تصحيح يدوي", reopened.visibleText("machine text"))
            assertEquals("تصحيح يدوي", reopened.exportText("machine text"))
            assertTrue(reopened.hasManualTruth)
            assertTrue(reopened.revisions.single().basedOnSourceTextHash.isNotBlank())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun undoRedoMovesOnlyAcrossCommittedRevisionHistory() {
        val root = createStoreRoot()
        try {
            val store = store(root)
            store.setPendingDraft("s1", "u1", "machine", "يدوي 1")
            store.commitPendingDraft("s1", "u1", "source", "machine")
            store.setPendingDraft("s1", "u1", "machine", "يدوي 2")
            store.commitPendingDraft("s1", "u1", "source", "machine")

            assertEquals("يدوي 2", store.readUnit("s1", "u1").exportText("machine"))
            store.undo("s1", "u1")
            assertEquals("يدوي 1", store.readUnit("s1", "u1").exportText("machine"))
            store.undo("s1", "u1")
            assertEquals("machine", store.readUnit("s1", "u1").exportText("machine"))
            store.redo("s1", "u1")
            assertEquals("يدوي 1", store.readUnit("s1", "u1").exportText("machine"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun newCommitAfterUndoDropsRedoBranchDeterministically() {
        val root = createStoreRoot()
        try {
            val store = store(root)
            fun commit(text: String) {
                store.setPendingDraft("s1", "u1", "machine", text)
                store.commitPendingDraft("s1", "u1", "source", "machine")
            }
            commit("واحد")
            commit("اثنان")
            store.undo("s1", "u1")
            commit("فرع جديد")

            val state = store.readUnit("s1", "u1")
            assertEquals(listOf("واحد", "فرع جديد"), state.revisions.map { it.text })
            assertFalse(store.redo("s1", "u1"))
            assertEquals("فرع جديد", store.readUnit("s1", "u1").exportText("machine"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun restoreMachineIsDurableAndDoesNotDeleteManualHistory() {
        val root = createStoreRoot()
        try {
            val store = store(root)
            store.setPendingDraft("s1", "u1", "machine", "يدوي")
            store.commitPendingDraft("s1", "u1", "source", "machine")
            store.restoreMachine("s1", "u1")

            val reopened = store(root).readUnit("s1", "u1")
            assertEquals("machine", reopened.exportText("machine"))
            assertFalse(reopened.hasManualTruth)
            assertEquals(1, reopened.revisions.size)
            assertTrue(store(root).redo("s1", "u1"))
            assertEquals("يدوي", store(root).readUnit("s1", "u1").exportText("machine"))
        } finally {
            root.deleteRecursively()
        }
    }
}
