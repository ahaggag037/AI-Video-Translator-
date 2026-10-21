package com.clw.aivideotranslator.editor

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DisplayTimingStateStoreTest {
    private fun root(): File = Files.createTempDirectory("display-timing-store").toFile().also { root ->
        check(File(root, "s1").mkdirs())
    }

    private fun store(root: File) = DisplayTimingStateStore(root) { _ -> Unit }
    private val source = DisplayTimingInterval(startUs = 1_000_000L, endUs = 2_000_000L)

    @Test fun pendingDraftSurvivesReopenButIsNotExportTruth() {
        val root = root()
        try {
            val store = store(root)
            val draft = DisplayTimingInterval(1_100_000L, 2_100_000L)
            store.setPendingDraft("s1", "u1", source, draft)

            val reopened = store(root).readUnit("s1", "u1")
            assertEquals(draft, reopened.pendingDraft)
            assertEquals(draft, reopened.visibleInterval(source))
            assertEquals(source, reopened.exportInterval(source))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun validCommittedOverrideBecomesExportTruth() {
        val root = root()
        try {
            val store = store(root)
            val draft = DisplayTimingInterval(1_100_000L, 1_900_000L)
            store.setPendingDraft("s1", "u1", source, draft)
            store.commitPendingDraft(
                sessionId = "s1",
                unitId = "u1",
                sourceInterval = source,
                videoDurationUs = 5_000_000L,
                previousEffectiveEndUs = 900_000L,
                nextEffectiveStartUs = 2_500_000L,
            )

            val reopened = store(root).readUnit("s1", "u1")
            assertNull(reopened.pendingDraft)
            assertEquals(draft, reopened.committedOverride)
            assertEquals(draft, reopened.exportInterval(source))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun resetToSourceRemovesOverrideWithoutMutatingSourceTiming() {
        val root = root()
        try {
            val store = store(root)
            store.setPendingDraft("s1", "u1", source, DisplayTimingInterval(1_100_000L, 1_900_000L))
            store.commitPendingDraft("s1", "u1", source, 5_000_000L, null, null)
            store.resetToSource("s1", "u1")

            val reopened = store(root).readUnit("s1", "u1")
            assertNull(reopened.pendingDraft)
            assertNull(reopened.committedOverride)
            assertEquals(source, reopened.exportInterval(source))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun draftsAreIsolatedByCueIdentity() {
        val root = root()
        try {
            val store = store(root)
            val u1 = DisplayTimingInterval(1_100_000L, 1_900_000L)
            val u2 = DisplayTimingInterval(3_100_000L, 3_900_000L)
            store.setPendingDraft("s1", "u1", source, u1)
            store.setPendingDraft("s1", "u2", DisplayTimingInterval(3_000_000L, 4_000_000L), u2)

            assertEquals(u1, store(root).readUnit("s1", "u1").pendingDraft)
            assertEquals(u2, store(root).readUnit("s1", "u2").pendingDraft)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun invalidOrOverlappingDraftCannotBecomeCommittedTruth() {
        val root = root()
        try {
            val store = store(root)
            store.setPendingDraft("s1", "u1", source, DisplayTimingInterval(800_000L, 1_900_000L))

            assertThrows(IllegalArgumentException::class.java) {
                store.commitPendingDraft(
                    sessionId = "s1",
                    unitId = "u1",
                    sourceInterval = source,
                    videoDurationUs = 5_000_000L,
                    previousEffectiveEndUs = 900_000L,
                    nextEffectiveStartUs = 2_500_000L,
                )
            }
            assertNull(store(root).readUnit("s1", "u1").committedOverride)
        } finally {
            root.deleteRecursively()
        }
    }
}
