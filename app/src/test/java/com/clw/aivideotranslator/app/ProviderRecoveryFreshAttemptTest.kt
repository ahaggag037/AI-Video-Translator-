package com.clw.aivideotranslator.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderRecoveryFreshAttemptTest {
    @Test
    fun confirmedRecoveryDoesNotSubmitUntilDifferentBoundSessionThenSubmitsOnce() {
        val initial = ProviderRecoveryFreshAttempt.arm(
            priorSessionId = "session-old",
            sourceUri = "content://video/source",
            apiKey = "test-key",
        )

        val confirmed = initial.confirm()
        assertEquals(
            ProviderRecoveryFreshAttemptAction.StartFreshSession("content://video/source"),
            confirmed.action,
        )

        val sameSession = confirmed.state.onBoundSession("session-old")
        assertNull(sameSession.action)

        val newSession = sameSession.state.onBoundSession("session-new")
        assertEquals(
            ProviderRecoveryFreshAttemptAction.SubmitStt("test-key"),
            newSession.action,
        )

        val repeatedBoundEvent = newSession.state.onBoundSession("session-new")
        assertNull(repeatedBoundEvent.action)
    }

    @Test
    fun recoveryNeverStartsWhenRequiredContextIsMissing() {
        assertNull(
            ProviderRecoveryFreshAttempt.armOrNull(
                priorSessionId = null,
                sourceUri = "content://video/source",
                apiKey = "test-key",
            )
        )
        assertNull(
            ProviderRecoveryFreshAttempt.armOrNull(
                priorSessionId = "session-old",
                sourceUri = null,
                apiKey = "test-key",
            )
        )
        assertNull(
            ProviderRecoveryFreshAttempt.armOrNull(
                priorSessionId = "session-old",
                sourceUri = "content://video/source",
                apiKey = "",
            )
        )
    }
}