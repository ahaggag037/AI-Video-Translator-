package com.clw.aivideotranslator.credential

internal enum class CredentialStatus {
    MISSING,
    UNVERIFIED,
    USABLE,
    REJECTED,
}

/** Pure lifecycle policy. Saving never performs a separate provider validation request. */
internal object CredentialLifecycle {
    fun onSecretSaved(): CredentialStatus = CredentialStatus.UNVERIFIED

    fun onProviderSuccess(): CredentialStatus = CredentialStatus.USABLE

    fun onDefiniteAuthRejection(): CredentialStatus = CredentialStatus.REJECTED

    fun onNonCredentialFailure(current: CredentialStatus): CredentialStatus = current

    fun onRemoved(): CredentialStatus = CredentialStatus.MISSING
}
