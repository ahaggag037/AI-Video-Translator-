package com.clw.aivideotranslator.subtitle.android

import android.graphics.Bitmap
import android.icu.util.VersionInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RasterCoordinatorInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val font by lazy { SubtitleFonts.loadExperimentCandidate(context) }
    private val geometry = FrameGeometry(426, 240)
    private val environment = "api=${Build.VERSION.SDK_INT};icu=${VersionInfo.ICU_VERSION};candidate=full-regular-2.012"

    @Test
    fun currentAndNextAreProducedOnExactlyOneWorkerWithoutDuplicateWork() {
        val active = AtomicInteger(0)
        val maxActive = AtomicInteger(0)
        val calls = AtomicInteger(0)
        val producer = SubtitleRasterProducer { request ->
            calls.incrementAndGet()
            val now = active.incrementAndGet()
            maxActive.updateAndGet { previous -> maxOf(previous, now) }
            try {
                Thread.sleep(25)
                fakeReady(request)
            } finally {
                active.decrementAndGet()
            }
        }
        val coordinator = RasterCoordinator(producer = producer)
        try {
            val current = request("current", "الترجمة الحالية")
            val next = request("next", "الترجمة التالية")
            coordinator.prepareWindow(current, next)
            coordinator.prepareWindow(current, next)

            val currentLease = (coordinator.awaitPrepared("current") as RasterAwaitResult.Ready).lease
            val nextLease = (coordinator.awaitPrepared("next") as RasterAwaitResult.Ready).lease
            currentLease.close()
            nextLease.close()

            assertEquals(2, calls.get())
            assertEquals(1, maxActive.get())
            assertEquals(2, coordinator.retainedEntryCount)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun previewPeekNeverBlocksForPendingRaster() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val producer = SubtitleRasterProducer { request ->
            entered.countDown()
            check(release.await(2, TimeUnit.SECONDS))
            fakeReady(request)
        }
        val coordinator = RasterCoordinator(producer = producer)
        try {
            coordinator.prepareWindow(request("a", "تحميل المعاينة"))
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertNull(coordinator.peekPrepared("a"))
            release.countDown()
            val lease = (coordinator.awaitPrepared("a") as RasterAwaitResult.Ready).lease
            lease.close()
        } finally {
            release.countDown()
            coordinator.close()
        }
    }

    @Test
    fun supersededInFlightRasterCannotPublishStaleContentAndSeekBackRerenders() {
        val enteredA = CountDownLatch(1)
        val releaseA = CountDownLatch(1)
        val staleBitmap = AtomicReference<Bitmap?>()
        val aCalls = AtomicInteger(0)
        val coordinator = RasterCoordinator(
            producer = SubtitleRasterProducer { request ->
                if (request.requestId == "a") {
                    aCalls.incrementAndGet()
                    if (aCalls.get() == 1) {
                        enteredA.countDown()
                        check(releaseA.await(2, TimeUnit.SECONDS))
                    }
                }
                val ready = fakeReady(request)
                if (request.requestId == "a" && aCalls.get() == 1) staleBitmap.set(ready.raster.bitmap)
                ready
            },
        )
        try {
            val a = request("a", "ترجمة قديمة")
            val b = request("b", "ترجمة جديدة")
            coordinator.prepareWindow(a)
            assertTrue(enteredA.await(1, TimeUnit.SECONDS))

            coordinator.prepareWindow(b)
            releaseA.countDown()
            val bLease = (coordinator.awaitPrepared("b") as RasterAwaitResult.Ready).lease
            bLease.close()

            assertNull("superseded request must never be preview-visible", coordinator.peekPrepared("a"))
            assertTrue("superseded produced bitmap must be recycled", requireNotNull(staleBitmap.get()).isRecycled)

            coordinator.prepareWindow(a)
            val seekBack = coordinator.awaitPrepared("a") as RasterAwaitResult.Ready
            seekBack.lease.close()
            assertEquals("seek-back must rerender instead of resurrecting stale ownership", 2, aCalls.get())
        } finally {
            releaseA.countDown()
            coordinator.close()
        }
    }

    @Test
    fun evictedRasterIsNotRecycledUntilOutstandingLeaseCloses() {
        val coordinator = RasterCoordinator(producer = SubtitleRasterProducer(::fakeReady))
        try {
            val a = request("a", "الأول")
            val b = request("b", "الثاني")
            val c = request("c", "الثالث")
            coordinator.prepareWindow(a, b)
            val leaseA = (coordinator.awaitPrepared("a") as RasterAwaitResult.Ready).lease
            val leaseB = (coordinator.awaitPrepared("b") as RasterAwaitResult.Ready).lease
            val bitmapA = leaseA.raster.bitmap
            val bitmapB = leaseB.raster.bitmap
            leaseB.close()

            coordinator.prepareWindow(c)
            assertFalse("leased bitmap must survive eviction", bitmapA.isRecycled)
            assertTrue("unleased superseded bitmap should be recycled", bitmapB.isRecycled)

            leaseA.close()
            assertTrue("evicted bitmap recycles when its final lease closes", bitmapA.isRecycled)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun leaseHeldBudgetPressureRejectsThenCanRetryAfterLeaseRelease() {
        val singleRasterBytes = geometry.uprightWidthPx.toLong() * geometry.uprightHeightPx.toLong() * 4L
        val calls = AtomicInteger(0)
        val coordinator = RasterCoordinator(
            producer = SubtitleRasterProducer { request ->
                calls.incrementAndGet()
                fakeReady(request)
            },
            maxCacheBytes = singleRasterBytes,
        )
        try {
            val a = request("a", "اللقطة الأولى")
            val b = request("b", "اللقطة الثانية")
            coordinator.prepareWindow(a)
            val leaseA = (coordinator.awaitPrepared("a") as RasterAwaitResult.Ready).lease

            coordinator.prepareWindow(b)
            assertEquals(
                RasterAwaitResult.Rejected("RASTER_CACHE_BUDGET_EXCEEDED"),
                coordinator.awaitPrepared("b"),
            )
            leaseA.close()

            coordinator.prepareWindow(b)
            val retried = coordinator.awaitPrepared("b") as RasterAwaitResult.Ready
            retried.lease.close()
            assertEquals("A + rejected B + retried B", 3, calls.get())
            assertTrue(coordinator.cachedByteCount <= singleRasterBytes)
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun requestIdCollisionWithDifferentDescriptorFailsClosed() {
        val coordinator = RasterCoordinator(producer = SubtitleRasterProducer(::fakeReady))
        try {
            coordinator.prepareWindow(request("same", "النص الأول"))
            try {
                coordinator.prepareWindow(request("same", "نص مختلف"))
                throw AssertionError("request id collision must fail closed")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        } finally {
            coordinator.close()
        }
    }

    @Test
    fun exportWaitCannotExceedN31FiveSecondBudget() {
        val coordinator = RasterCoordinator(producer = SubtitleRasterProducer(::fakeReady))
        try {
            coordinator.prepareWindow(request("a", "اختبار المهلة"))
            try {
                coordinator.awaitPrepared("a", RasterCoordinator.DEFAULT_WAIT_BUDGET_MS + 1L)
                throw AssertionError("wait beyond N31 must be rejected")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        } finally {
            coordinator.close()
        }
    }

    private fun request(id: String, text: String): RasterRequest {
        val layout = SubtitleLayoutEngine().layout(text, geometry, font, environment)
        assertTrue("test text must fit", layout is SubtitleLayoutResult.Fits)
        return RasterRequest(id, (layout as SubtitleLayoutResult.Fits).descriptor, geometry, font)
    }

    private fun fakeReady(request: RasterRequest): SubtitleRasterResult.Ready {
        val mutable = Bitmap.createBitmap(
            request.geometry.uprightWidthPx,
            request.geometry.uprightHeightPx,
            Bitmap.Config.ARGB_8888,
        )
        val immutable = requireNotNull(mutable.copy(Bitmap.Config.ARGB_8888, false))
        mutable.recycle()
        return SubtitleRasterResult.Ready(
            ImmutableSubtitleRaster(
                bitmap = immutable,
                descriptor = request.descriptor,
                frameWidthPx = request.geometry.uprightWidthPx,
                frameHeightPx = request.geometry.uprightHeightPx,
            )
        )
    }
}
