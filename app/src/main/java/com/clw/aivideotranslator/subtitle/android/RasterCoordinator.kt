package com.clw.aivideotranslator.subtitle.android

import java.io.Closeable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Shadow-only X004/X006 coordinator.
 *
 * Raster work happens on exactly one worker. Frame callbacks may only [peekPrepared]; export may
 * [awaitPrepared] for the bounded N31 wait. The coordinator owns each bitmap and hands consumers a
 * lease so eviction cannot recycle a bitmap while UI/GL is still using it.
 */
class RasterCoordinator(
    private val producer: SubtitleRasterProducer = SubtitleRasterProducer { request ->
        SubtitleRasterizer().rasterize(request.descriptor, request.geometry, request.font)
    },
    private val maxCacheBytes: Long = MAX_CACHE_BYTES,
    private val waitBudgetMs: Long = DEFAULT_WAIT_BUDGET_MS,
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "subtitle-raster-worker").apply { isDaemon = true }
    },
) : Closeable {
    private val lock = Any()
    private val entries = linkedMapOf<String, Entry>()
    private var closed = false

    init {
        require(maxCacheBytes > 0L)
        require(waitBudgetMs > 0L)
    }

    /**
     * Declares the only rasters worth retaining: current and optional next. Missing rasters are
     * queued on the single worker. Superseded in-flight work is allowed to finish but is discarded.
     */
    fun prepareWindow(current: RasterRequest, next: RasterRequest? = null) {
        val desired = listOfNotNull(current, next)
        require(desired.map { it.requestId }.toSet().size == desired.size) {
            "current and next raster request ids must differ"
        }
        synchronized(lock) {
            checkOpen()
            val desiredIds = desired.mapTo(linkedSetOf()) { it.requestId }
            entries.values.filter { it.request.requestId !in desiredIds }.forEach { markEvictedLocked(it) }
            desired.forEach { request ->
                var existing = entries[request.requestId]
                if (existing != null) {
                    require(existing.request.sameIdentityAs(request)) {
                        "raster request id collision with different descriptor/profile"
                    }
                    if (existing.retryable && existing.completion.isDone && existing.leaseCount == 0) {
                        recycleEntryLocked(existing)
                        entries.remove(request.requestId, existing)
                        existing = null
                    }
                }
                if (existing == null) {
                    val entry = Entry(request)
                    entries[request.requestId] = entry
                    scheduleLocked(entry)
                } else {
                    existing.evicted = false
                }
            }
            removeRecyclableEvictedLocked()
        }
    }

    /** No blocking and no raster/layout work on the caller. Suitable for a preview frame callback. */
    fun peekPrepared(requestId: String): RasterLease? = synchronized(lock) {
        if (closed) return@synchronized null
        val entry = entries[requestId] ?: return@synchronized null
        if (entry.evicted) return@synchronized null
        val ready = entry.acceptedRaster ?: return@synchronized null
        if (ready.bitmap.isRecycled) return@synchronized null
        acquireLeaseLocked(entry, ready)
    }

    /**
     * Bounded export-side wait. It never runs the producer on the caller thread. A nonempty cue that
     * is missing/rejected/timed-out must make export fail rather than silently substituting empty.
     */
    fun awaitPrepared(
        requestId: String,
        timeoutMs: Long = waitBudgetMs,
    ): RasterAwaitResult {
        require(timeoutMs in 1..waitBudgetMs) { "wait exceeds N31 budget" }
        val entry = synchronized(lock) {
            if (closed) return RasterAwaitResult.Rejected("COORDINATOR_CLOSED")
            entries[requestId]?.takeUnless { it.evicted }
                ?: return RasterAwaitResult.Rejected("RASTER_NOT_PREPARED")
        }
        val result = try {
            entry.completion.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            return RasterAwaitResult.TimedOut
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
            return RasterAwaitResult.Rejected("RASTER_WAIT_INTERRUPTED")
        } catch (error: Exception) {
            return RasterAwaitResult.Rejected("RASTER_WORKER_FAILED:${error.javaClass.simpleName}")
        }

        return synchronized(lock) {
            if (closed || entry.evicted || entries[requestId] !== entry) {
                return@synchronized RasterAwaitResult.Rejected("RASTER_SUPERSEDED")
            }
            when (result) {
                is SubtitleRasterResult.Ready -> {
                    val raster = entry.acceptedRaster
                        ?: return@synchronized RasterAwaitResult.Rejected("RASTER_NOT_ADMITTED")
                    RasterAwaitResult.Ready(acquireLeaseLocked(entry, raster))
                }
                is SubtitleRasterResult.Rejected -> RasterAwaitResult.Rejected(result.reason)
            }
        }
    }

    val cachedByteCount: Long
        get() = synchronized(lock) { ownedBytesLocked() }

    val retainedEntryCount: Int
        get() = synchronized(lock) { entries.values.count { !it.evicted } }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            entries.values.forEach { markEvictedLocked(it) }
            removeRecyclableEvictedLocked()
        }
        executor.shutdownNow()
    }

    private fun scheduleLocked(entry: Entry) {
        executor.execute {
            val produced = try {
                producer.produce(entry.request)
            } catch (error: Exception) {
                synchronized(lock) {
                    entry.completion.completeExceptionally(error)
                    if (entry.evicted && entry.leaseCount == 0) entries.remove(entry.request.requestId, entry)
                }
                return@execute
            }
            synchronized(lock) {
                when (produced) {
                    is SubtitleRasterResult.Ready -> {
                        val raster = produced.raster
                        val canAdmit = !closed && !entry.evicted &&
                            ownedBytesLocked() + raster.byteCount <= maxCacheBytes
                        if (canAdmit) {
                            entry.acceptedRaster = raster
                            entry.completion.complete(produced)
                        } else {
                            raster.bitmap.recycle()
                            val reason = if (closed || entry.evicted) {
                                "RASTER_SUPERSEDED"
                            } else {
                                entry.retryable = true
                                "RASTER_CACHE_BUDGET_EXCEEDED"
                            }
                            entry.completion.complete(SubtitleRasterResult.Rejected(reason))
                        }
                    }
                    is SubtitleRasterResult.Rejected -> entry.completion.complete(produced)
                }
                if (entry.evicted && entry.leaseCount == 0) entries.remove(entry.request.requestId, entry)
            }
        }
    }

    private fun acquireLeaseLocked(entry: Entry, raster: ImmutableSubtitleRaster): RasterLease {
        entry.leaseCount += 1
        return RasterLease(raster) {
            synchronized(lock) {
                check(entry.leaseCount > 0) { "raster lease underflow" }
                entry.leaseCount -= 1
                if (entry.evicted && entry.leaseCount == 0) {
                    recycleEntryLocked(entry)
                    entries.remove(entry.request.requestId, entry)
                }
            }
        }
    }

    private fun markEvictedLocked(entry: Entry) {
        if (entry.evicted) return
        entry.evicted = true
        if (entry.leaseCount == 0 && entry.acceptedRaster != null) recycleEntryLocked(entry)
    }

    private fun removeRecyclableEvictedLocked() {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (entry.evicted && entry.leaseCount == 0 && entry.completion.isDone) {
                recycleEntryLocked(entry)
                iterator.remove()
            }
        }
    }

    private fun recycleEntryLocked(entry: Entry) {
        entry.acceptedRaster?.bitmap?.let { bitmap ->
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        entry.acceptedRaster = null
    }

    private fun ownedBytesLocked(): Long = entries.values.sumOf { entry ->
        entry.acceptedRaster?.takeUnless { it.bitmap.isRecycled }?.byteCount ?: 0L
    }

    private fun checkOpen() = check(!closed) { "raster coordinator is closed" }

    private class Entry(val request: RasterRequest) {
        val completion = CompletableFuture<SubtitleRasterResult>()
        var acceptedRaster: ImmutableSubtitleRaster? = null
        var leaseCount: Int = 0
        var evicted: Boolean = false
        var retryable: Boolean = false
    }

    companion object {
        const val MAX_CACHE_BYTES: Long = 16L * 1024L * 1024L
        const val DEFAULT_WAIT_BUDGET_MS: Long = 5_000L
    }
}

fun interface SubtitleRasterProducer {
    fun produce(request: RasterRequest): SubtitleRasterResult
}

data class RasterRequest(
    val requestId: String,
    val descriptor: SubtitleLayoutDescriptor,
    val geometry: FrameGeometry,
    val font: LoadedSubtitleFont,
) {
    init { require(requestId.isNotBlank()) }

    internal fun sameIdentityAs(other: RasterRequest): Boolean =
        requestId == other.requestId &&
            descriptor == other.descriptor &&
            geometry == other.geometry &&
            font.profile == other.font.profile
}

class RasterLease internal constructor(
    val raster: ImmutableSubtitleRaster,
    private val releaseAction: () -> Unit,
) : Closeable {
    private val lock = Any()
    private var released = false

    override fun close() {
        synchronized(lock) {
            if (released) return
            released = true
        }
        releaseAction()
    }
}

sealed interface RasterAwaitResult {
    data class Ready(val lease: RasterLease) : RasterAwaitResult
    data class Rejected(val reason: String) : RasterAwaitResult {
        init { require(reason.isNotBlank()) }
    }
    data object TimedOut : RasterAwaitResult
}
