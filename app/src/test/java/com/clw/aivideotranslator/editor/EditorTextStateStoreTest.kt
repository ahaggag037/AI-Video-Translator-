package com.clw.aivideotranslator.editor

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorTextStateStoreTest {
    @Test fun pendingDraftIsDurableButNotExportTruth() {
        val root = Files.createTempDirectory("editor-text-store").toFile()
        try {
            val store = EditorTextStateStore(root) { }
            store.setPendingDraft("s1", "u1", "machine text", "مسودة")

            val reopened = EditorTextStateStore(root) { }.readUnit("s1", "u1")
            assertEquals("مسودة", reopened.pendingDraft)
            assertEquals("مسودة", reopened.visibleText("machine text"))
            assertEquals("machine text", reopened.exportText("machine text"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun committedManualRevisionBecomesDurableExportTruth() {
        val root = Files.createTempDirectory("editor-text-store").toFile()
        try {
            val store = EditorTextStateStore(root) { }
            store.setPendingDraft("s1", "u1", "machine text", "تصحيح يدوي")
            store.commitPendingDraft("s1", "u1", "machine text")

            val reopened = EditorTextStateStore(root) { }.readUnit("s1", "u1")
            assertNull(reopened.pendingDraft)
            assertEquals("تصحيح يدوي", reopened.visibleText("machine text"))
            assertEquals("تصحيح يدوي", reopened.exportText("machine text"))
            assertTrue(reopened.hasManualTruth)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun undoRedoMovesOnlyAcrossCommittedRevisionHistory() {
        val root = Files.createTempDirectory("editor-text-store").toFile()
        try {
            val store = EditorTextStateStore(root) { }
            store.setPendingDraft("s1", "u1", "machine", "يدوي 1")
            store.commitPendingDraft("s1", "u1", "machine")
            store.setPendingDraft("s1", "u1", "machine", "يدوي 2")
            store.commitPendingDraft("s1", "u1", "machine")

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
        val root = Files.createTempDirectory("editor-text-store").toFile()
        try {
            val store = EditorTextStateStore(root) { }
            fun commit(text: String) {
                store.setPendingDraft("s1", "u1", "machine", text)
                store.commitPendingDraft("s1", "u1", "machine")
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
        val root = Files.createTempDirectory("editor-text-store").toFile()
        try {
            val store = EditorTextStateStore(root) { }
            store.setPendingDraft("s1", "u1", "machine", "يدوي")
            store.commitPendingDraft("s1", "u1", "machine")
            store.restoreMachine("s1", "u1")

            val reopened = EditorTextStateStore(root) { }.readUnit("s1", "u1")
            assertEquals("machine", reopened.exportText("machine"))
            assertFalse(reopened.hasManualTruth)
            assertEquals(1, reopened.revisions.size)
            assertTrue(EditorTextStateStore(root) { }.redo("s1", "u1"))
            assertEquals("يدوي", EditorTextStateStore(root) { }.readUnit("s1", "u1").exportText("machine"))
        } finally {
            root.deleteRecursively()
        }
    }
}
