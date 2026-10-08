package com.clw.aivideotranslator.semantic

object TranslationDefaults {
    const val SEGMENTATION_VERSION = "semantic-v1"
    const val STRONG_GAP_US = 700_000L
    const val CLAUSE_GAP_US = 300_000L
    const val SOFT_DURATION_US = 6_000_000L
    const val HARD_DURATION_US = 10_000_000L
    const val SOFT_WORDS = 20
    const val HARD_WORDS = 32
    const val SOFT_SCALARS = 160
    const val HARD_SCALARS = 280
}

data class SegmenterConfig(
    val strongGapUs: Long = TranslationDefaults.STRONG_GAP_US,
    val clauseGapUs: Long = TranslationDefaults.CLAUSE_GAP_US,
    val softDurationUs: Long = TranslationDefaults.SOFT_DURATION_US,
    val hardDurationUs: Long = TranslationDefaults.HARD_DURATION_US,
    val softWords: Int = TranslationDefaults.SOFT_WORDS,
    val hardWords: Int = TranslationDefaults.HARD_WORDS,
    val softScalars: Int = TranslationDefaults.SOFT_SCALARS,
    val hardScalars: Int = TranslationDefaults.HARD_SCALARS,
) {
    init {
        require(strongGapUs >= clauseGapUs && clauseGapUs >= 0L)
        require(hardDurationUs >= softDurationUs && softDurationUs > 0L)
        require(hardWords >= softWords && softWords > 0)
        require(hardScalars >= softScalars && softScalars > 0)
    }
}
