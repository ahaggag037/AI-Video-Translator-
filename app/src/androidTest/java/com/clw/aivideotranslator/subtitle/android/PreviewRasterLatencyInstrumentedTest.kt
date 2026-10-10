package com.clw.aivideotranslator.subtitle.android

import android.icu.util.VersionInfo
import android.os.Build
import android.os.SystemClock
import android.view.Choreographer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.MAX_PREVIEW_CUE_LAG_MS
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewRasterLatencyInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val font by lazy { SubtitleFonts.loadExperimentCandidate(context) }
    private val geometry = FrameGeometry(426, 240)
    private val environment = "api=${Build.VERSION.SDK_INT};icu=${VersionInfo.ICU_VERSION};font=${font.profile.profileId}"

    @Test
    fun prefetchedNextCueIsAvailableWithinN27DisplayFrameBudget() {
        val coordinator = RasterCoordinator()
        try {
            val current = request("preview-current", "الترجمة الحالية")
            val next = request("preview-next", "الترجمة التالية")
            coordinator.prepareWindow(current, next)
            val currentReady = coordinator.awaitPrepared(current.requestId)
            assertTrue(currentReady is RasterAwaitResult.Ready)
            (currentReady as RasterAwaitResult.Ready).lease.close()

            val completed = CountDownLatch(1)
            val becameReady = AtomicBoolean(false)
            val measuredLagMs = AtomicLong(Long.MAX_VALUE)
            instrumentation.runOnMainSync {
                val choreographer = Choreographer.getInstance()
                val boundaryObservedAt = SystemClock.uptimeMillis()
                val callback = object : Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        val lease = coordinator.peekPrepared(next.requestId)
                        val elapsed = SystemClock.uptimeMillis() - boundaryObservedAt
                        if (lease != null) {
                            becameReady.set(true)
                            measuredLagMs.set(elapsed)
                            lease.close()
                            completed.countDown()
                        } else if (elapsed > MAX_PREVIEW_CUE_LAG_MS) {
                            measuredLagMs.set(elapsed)
                            completed.countDown()
                        } else {
                            choreographer.postFrameCallback(this)
                        }
                    }
                }
                choreographer.postFrameCallback(callback)
            }

            assertTrue("display-frame probe did not complete", completed.await(1, TimeUnit.SECONDS))
            assertTrue("prefetched raster was not ready by the N27 boundary", becameReady.get())
            assertTrue(
                "preview raster lag ${measuredLagMs.get()}ms exceeded ${MAX_PREVIEW_CUE_LAG_MS}ms",
                measuredLagMs.get() <= MAX_PREVIEW_CUE_LAG_MS,
            )
        } finally {
            coordinator.close()
        }
    }

    private fun request(id: String, text: String): RasterRequest {
        val layout = SubtitleLayoutEngine().layout(text, geometry, font, environment)
        val descriptor = (layout as? SubtitleLayoutResult.Fits)?.descriptor
            ?: error("fixture text must fit")
        return RasterRequest(id, descriptor, geometry, font)
    }
}
