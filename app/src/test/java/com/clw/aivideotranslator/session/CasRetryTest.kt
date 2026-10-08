package com.clw.aivideotranslator.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.fail
import org.junit.Test

class CasRetryTest {
    private class FakeStore(var revision: Long = 0L)

    @Test fun succeedsOnFirstAttemptWithoutRereadingExcessively() {
        val store = FakeStore(revision = 7L)
        var actionCalls = 0
        val result = CasRetry.run(
            readRevision = { store.revision },
            action = { expected ->
                actionCalls++
                assertEquals(7L, expected)
                "bound"
            },
        )
        assertEquals("bound", result)
        assertEquals(1, actionCalls)
    }

    @Test fun retriesOnlyWhileRevisionActuallyAdvanced() {
        val store = FakeStore(revision = 0L)
        var actionCalls = 0
        val result = CasRetry.run(
            readRevision = { store.revision },
            action = { expected ->
                actionCalls++
                if (actionCalls == 1) {
                    assertEquals(0L, expected)
                    store.revision = 1L // concurrent writer moved the manifest
                    throw IllegalStateException("stale session revision")
                }
                assertEquals(1L, expected)
                "bound-after-retry"
            },
        )
        assertEquals("bound-after-retry", result)
        assertEquals(2, actionCalls)
    }

    @Test fun invariantFailureWithUnchangedRevisionIsNeverRetried() {
        val store = FakeStore(revision = 3L)
        var actionCalls = 0
        val failure = IllegalStateException("initial source binding requires a new empty session")
        val thrown = try {
            CasRetry.run(
                readRevision = { store.revision },
                action = {
                    actionCalls++
                    throw failure
                },
            )
            fail("must propagate")
        } catch (error: IllegalStateException) {
            error
        }
        assertSame(failure, thrown)
        assertEquals(1, actionCalls)
    }

    @Test fun requireFailuresAreNeverRetried() {
        val store = FakeStore(revision = 0L)
        var actionCalls = 0
        val failure = IllegalArgumentException("source session mismatch")
        val thrown = try {
            CasRetry.run(
                readRevision = { store.revision },
                action = {
                    actionCalls++
                    throw failure
                },
            )
            fail("must propagate")
        } catch (error: IllegalArgumentException) {
            error
        }
        assertSame(failure, thrown)
        assertEquals(1, actionCalls)
    }

    @Test fun permanentlyContendedStoreStopsAfterAttemptBudget() {
        val store = FakeStore(revision = 0L)
        var actionCalls = 0
        try {
            CasRetry.run(
                maxAttempts = 3,
                readRevision = { store.revision },
                action = {
                    actionCalls++
                    store.revision += 1L // every attempt loses the race
                    throw IllegalStateException("stale session revision")
                },
            )
            fail("must give up")
        } catch (error: IllegalStateException) {
            assertEquals("stale session revision", error.message)
        }
        assertEquals(3, actionCalls)
    }

    @Test fun retryingASameEvidenceActionRepublishesIdenticalIdentity() {
        // Mirrors the coordinator contract: identical immutable evidence in, identical identity out,
        // so a CAS retry cannot fork attachment/snapshot identity.
        val store = FakeStore(revision = 0L)
        val publishedIds = mutableListOf<String>()
        val result = CasRetry.run(
            readRevision = { store.revision },
            action = {
                if (publishedIds.isEmpty()) {
                    store.revision = 9L
                    publishedIds += "identity-from-evidence"
                    throw IllegalStateException("stale session revision")
                }
                publishedIds += "identity-from-evidence"
                publishedIds.last()
            },
        )
        assertEquals("identity-from-evidence", result)
        assertEquals(listOf("identity-from-evidence", "identity-from-evidence"), publishedIds)
    }
}
