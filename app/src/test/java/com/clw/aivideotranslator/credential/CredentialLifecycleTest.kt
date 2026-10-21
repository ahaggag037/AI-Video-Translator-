package com.clw.aivideotranslator.credential

import org.junit.Assert.assertEquals
import org.junit.Test

class CredentialLifecycleTest {
    @Test fun savingNewSecretStartsUnverifiedWithoutValidationRequest() {
        val next = CredentialLifecycle.onSecretSaved()
        assertEquals(CredentialStatus.UNVERIFIED, next)
    }

    @Test fun successfulRealProviderUseMarksCredentialUsable() {
        assertEquals(CredentialStatus.USABLE, CredentialLifecycle.onProviderSuccess())
    }

    @Test fun definiteAuthRejectionMarksCredentialRejected() {
        assertEquals(CredentialStatus.REJECTED, CredentialLifecycle.onDefiniteAuthRejection())
    }

    @Test fun temporaryFailureDoesNotDestroyKnownUsableCredential() {
        assertEquals(
            CredentialStatus.USABLE,
            CredentialLifecycle.onNonCredentialFailure(CredentialStatus.USABLE),
        )
    }

    @Test fun removingSecretReturnsMissing() {
        assertEquals(CredentialStatus.MISSING, CredentialLifecycle.onRemoved())
    }
}
