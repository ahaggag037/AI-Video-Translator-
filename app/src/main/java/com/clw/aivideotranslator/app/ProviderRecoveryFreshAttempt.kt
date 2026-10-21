package com.clw.aivideotranslator.app

sealed interface ProviderRecoveryFreshAttemptAction {
    data class StartFreshSession(val sourceUri: String) : ProviderRecoveryFreshAttemptAction
    data class SubmitStt(val apiKey: String) : ProviderRecoveryFreshAttemptAction
}

data class ProviderRecoveryFreshAttemptTransition(
    val state: ProviderRecoveryFreshAttempt,
    val action: ProviderRecoveryFreshAttemptAction?,
)

data class ProviderRecoveryFreshAttempt private constructor(
    private val priorSessionId: String,
    private val sourceUri: String,
    private val apiKey: String,
    private val confirmed: Boolean,
    private val submitted: Boolean,
) {
    fun confirm(): ProviderRecoveryFreshAttemptTransition {
        if (confirmed) {
            return ProviderRecoveryFreshAttemptTransition(this, null)
        }
        val next = copy(confirmed = true)
        return ProviderRecoveryFreshAttemptTransition(
            state = next,
            action = ProviderRecoveryFreshAttemptAction.StartFreshSession(sourceUri),
        )
    }

    fun onBoundSession(sessionId: String?): ProviderRecoveryFreshAttemptTransition {
        if (!confirmed || submitted || sessionId.isNullOrBlank() || sessionId == priorSessionId) {
            return ProviderRecoveryFreshAttemptTransition(this, null)
        }
        val next = copy(submitted = true)
        return ProviderRecoveryFreshAttemptTransition(
            state = next,
            action = ProviderRecoveryFreshAttemptAction.SubmitStt(apiKey),
        )
    }

    companion object {
        fun arm(
            priorSessionId: String,
            sourceUri: String,
            apiKey: String,
        ): ProviderRecoveryFreshAttempt = ProviderRecoveryFreshAttempt(
            priorSessionId = priorSessionId,
            sourceUri = sourceUri,
            apiKey = apiKey,
            confirmed = false,
            submitted = false,
        )

        fun armOrNull(
            priorSessionId: String?,
            sourceUri: String?,
            apiKey: String?,
        ): ProviderRecoveryFreshAttempt? {
            val safePriorSessionId = priorSessionId?.takeIf { it.isNotBlank() } ?: return null
            val safeSourceUri = sourceUri?.takeIf { it.isNotBlank() } ?: return null
            val safeApiKey = apiKey?.takeIf { it.isNotBlank() } ?: return null
            return arm(
                priorSessionId = safePriorSessionId,
                sourceUri = safeSourceUri,
                apiKey = safeApiKey,
            )
        }
    }
}
