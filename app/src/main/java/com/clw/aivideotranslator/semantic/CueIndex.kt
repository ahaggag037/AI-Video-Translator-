package com.clw.aivideotranslator.semantic

class CueIndex<T>(
    cues: List<T>,
    private val intervalOf: (T) -> PresentationIntervalUs,
) {
    private val cues = cues.toList()
    private val starts = LongArray(cues.size)

    init {
        var previousEnd = -1L
        this.cues.forEachIndexed { index, cue ->
            val interval = intervalOf(cue)
            require(interval.start.value >= previousEnd) { "cue intervals overlap or are unsorted" }
            starts[index] = interval.start.value
            previousEnd = interval.end.value
        }
    }

    fun activeAt(time: PresentationTimeUs): T? {
        var low = 0
        var high = starts.lastIndex
        var candidate = -1
        while (low <= high) {
            val mid = (low + high).ushr(1)
            if (starts[mid] <= time.value) {
                candidate = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        if (candidate < 0) return null
        val cue = cues[candidate]
        return if (time.value < intervalOf(cue).end.value) cue else null
    }

    val size: Int get() = cues.size
}
