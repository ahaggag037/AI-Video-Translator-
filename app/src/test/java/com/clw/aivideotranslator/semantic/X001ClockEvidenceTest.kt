package com.clw.aivideotranslator.semantic

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X001ClockEvidenceTest {
    private fun fixture(): JSONObject {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("translation_v1/x001_raw_timing_vectors.json"))
        return stream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    }

    @Test fun ambiguousMagnitudeDoesNotEstablishRawOffsetUnit() {
        val item = fixture().getJSONArray("cases").getJSONObject(0)
        assertEquals("ambiguous-20-80", item.getString("id"))
        assertTrue(item.isNull("declaredUnit"))
        val rawStart = item.getDouble("rawStart")
        val rawEnd = item.getDouble("rawEnd")

        val ifMilliseconds = rawIntervalToUs(rawStart, rawEnd, RawUnit.MILLISECONDS)
        val ifSeconds = rawIntervalToUs(rawStart, rawEnd, RawUnit.SECONDS)

        assertEquals(AudioIntervalUs(AudioTimeUs(20_000), AudioTimeUs(80_000)), ifMilliseconds)
        assertEquals(AudioIntervalUs(AudioTimeUs(20_000_000), AudioTimeUs(80_000_000)), ifSeconds)
        assertNotEquals(ifMilliseconds, ifSeconds)
    }

    @Test fun declaredMillisecondsPlusNonzeroOriginMapsWithoutRetimingOrClamp() {
        val item = fixture().getJSONArray("cases").getJSONObject(1)
        assertEquals("ms", item.getString("declaredUnit"))
        val interval = rawIntervalToUs(item.getDouble("rawStart"), item.getDouble("rawEnd"), RawUnit.MILLISECONDS)
        val origin = item.getLong("presentationOriginUs")
        val mapped = PresentationIntervalUs(
            PresentationTimeUs(Math.addExact(origin, interval.start.value)),
            PresentationTimeUs(Math.addExact(origin, interval.end.value)),
        )
        assertEquals(item.getLong("expectedPresentationStartUs"), mapped.start.value)
        assertEquals(item.getLong("expectedPresentationEndUs"), mapped.end.value)
    }

    @Test fun declaredSecondsReferenceMapsExactlyWithoutEstablishingProviderUnits() {
        val item = fixture().getJSONArray("cases").getJSONObject(2)
        assertEquals("declared-seconds-reference", item.getString("id"))
        assertEquals("s", item.getString("declaredUnit"))

        val interval = rawIntervalToUs(item.getDouble("rawStart"), item.getDouble("rawEnd"), RawUnit.SECONDS)

        assertEquals(item.getLong("expectedAudioStartUs"), interval.start.value)
        assertEquals(item.getLong("expectedAudioEndUs"), interval.end.value)
        assertTrue(item.getString("purpose").contains("does not establish NVIDIA provider units"))
    }

    @Test fun n24ToleranceUsesLargerOfLocalFrameDurationAndFortyMilliseconds() {
        val n24 = fixture().getJSONObject("n24")
        val floorUs = n24.getLong("minimumToleranceUs")
        assertEquals(40_000L, floorUs)
        assertEquals(40_000L, n24MarkerToleranceUs(localFrameDurationUs = 33_367L, floorUs = floorUs))
        assertEquals(41_667L, n24MarkerToleranceUs(localFrameDurationUs = 41_667L, floorUs = floorUs))
        assertEquals(250_000L, n24.getLong("containerDurationToleranceUs"))
    }

    private enum class RawUnit { MILLISECONDS, SECONDS }

    private fun rawIntervalToUs(start: Double, end: Double, unit: RawUnit): AudioIntervalUs {
        require(start >= 0.0 && end > start)
        val scale = when (unit) {
            RawUnit.MILLISECONDS -> 1_000.0
            RawUnit.SECONDS -> 1_000_000.0
        }
        fun checked(value: Double): Long {
            val scaled = value * scale
            require(scaled.isFinite() && scaled >= 0.0 && scaled <= Long.MAX_VALUE.toDouble())
            return scaled.toLong()
        }
        return AudioIntervalUs(AudioTimeUs(checked(start)), AudioTimeUs(checked(end)))
    }

    private fun n24MarkerToleranceUs(localFrameDurationUs: Long, floorUs: Long): Long {
        require(localFrameDurationUs > 0L && floorUs > 0L)
        return maxOf(localFrameDurationUs, floorUs)
    }
}
