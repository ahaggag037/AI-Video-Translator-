package com.clw.aivideotranslator.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeStatusResolverTest {
    @Test fun noSessionIsEmpty() {
        assertEquals(
            HomeStatus.Empty,
            HomeStatusResolver.resolve(HomeFacts(hasActiveSession = false)),
        )
    }

    @Test fun unknownProviderRequiresActionBeforeOtherActiveStates() {
        val facts = HomeFacts(
            hasActiveSession = true,
            providerUnknown = true,
            sourceUnavailable = true,
            processingActive = true,
        )

        assertEquals(HomeStatus.ActionUnknownProvider, HomeStatusResolver.resolve(facts))
    }

    @Test fun missingSourceRequiresActionBeforeOrdinaryProcessing() {
        val facts = HomeFacts(
            hasActiveSession = true,
            sourceUnavailable = true,
            processingActive = true,
        )

        assertEquals(HomeStatus.ActionSourceMissing, HomeStatusResolver.resolve(facts))
    }

    @Test fun activeProcessingIsVisibleWhenNoHigherPriorityActionExists() {
        assertEquals(
            HomeStatus.ActiveProcessing,
            HomeStatusResolver.resolve(
                HomeFacts(hasActiveSession = true, processingActive = true)
            ),
        )
    }

    @Test fun activeSessionOtherwiseResolvesReady() {
        assertEquals(
            HomeStatus.ActiveReady,
            HomeStatusResolver.resolve(HomeFacts(hasActiveSession = true)),
        )
    }
}
