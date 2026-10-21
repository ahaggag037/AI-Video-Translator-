package com.clw.aivideotranslator.app

import com.clw.aivideotranslator.app.resume.ReopenFacts
import com.clw.aivideotranslator.app.resume.ReopenResolver

/**
 * Applies durable recovery truth without turning navigation into a second semantic state store.
 * Critical recovery may preempt ordinary UI; otherwise only Home/Processing auto-follow durable
 * progression. User-owned editor/export navigation remains stable.
 */
internal object ProductRouteSynchronizer {
    fun resolve(
        current: ProductRoute,
        facts: ReopenFacts,
    ): ProductRoute {
        if (facts.hasActiveSession && !facts.sessionReadable) {
            return ProductRoute.LOCAL_RECOVERY
        }
        if (facts.hasActiveSession && facts.hasUnknownProviderOutcome) {
            return ProductRoute.PROVIDER_RECOVERY
        }

        if (facts.hasActiveSession && !facts.sourceAvailable) {
            val textOnlyAllowed = facts.hasAcceptedText && current == ProductRoute.EDITOR_TEXT
            if (!textOnlyAllowed) return ProductRoute.SOURCE_RECOVERY
        }

        if (current == ProductRoute.HOME || current == ProductRoute.PROCESSING) {
            return ReopenProductRouteAdapter.toProductRoute(ReopenResolver.resolve(facts))
        }

        return current
    }
}
