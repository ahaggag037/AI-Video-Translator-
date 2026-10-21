package com.clw.aivideotranslator.ui.editor

import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.editor.DisplayTimingInterval
import com.clw.aivideotranslator.editor.UnitDisplayTimingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimingRuntimeResolverTest {
    private val units = listOf(
        SourceUnit("u1", 1_000L, 2_000L, "one"),
        SourceUnit("u2", 2_000L, 3_000L, "two"),
        SourceUnit("u3", 3_000L, 4_000L, "three"),
    )

    @Test fun selectedCueUsesSourceIntervalAndCommittedNeighborTruth() {
        val states = mapOf(
            "u1" to UnitDisplayTimingState(
                committedOverride = DisplayTimingInterval(1_000_000L, 1_900_000L),
                pendingDraft = DisplayTimingInterval(1_000_000L, 1_950_000L),
            ),
            "u2" to UnitDisplayTimingState(
                committedOverride = DisplayTimingInterval(2_050_000L, 2_950_000L),
            ),
            "u3" to UnitDisplayTimingState(
                committedOverride = DisplayTimingInterval(3_100_000L, 4_000_000L),
                pendingDraft = DisplayTimingInterval(3_050_000L, 4_000_000L),
            ),
        )

        val runtime = TimingRuntimeResolver.resolve(units, "u2", states)

        assertEquals(DisplayTimingInterval(2_000_000L, 3_000_000L), runtime?.sourceInterval)
        assertEquals(1_900_000L, runtime?.previousEffectiveEndUs)
        assertEquals(3_100_000L, runtime?.nextEffectiveStartUs)
        assertEquals(states.getValue("u2"), runtime?.selectedState)
    }

    @Test fun firstAndLastCueHaveOnlyExistingNeighborBounds() {
        val first = TimingRuntimeResolver.resolve(units, "u1", emptyMap())
        assertNull(first?.previousEffectiveEndUs)
        assertEquals(3_000_000L, TimingRuntimeResolver.resolve(units, "u3", emptyMap())?.previousEffectiveEndUs)
        assertNull(TimingRuntimeResolver.resolve(units, "u3", emptyMap())?.nextEffectiveStartUs)
    }

    @Test fun unknownSelectedIdentityDoesNotInventRuntime() {
        assertNull(TimingRuntimeResolver.resolve(units, "missing", emptyMap()))
    }
}
