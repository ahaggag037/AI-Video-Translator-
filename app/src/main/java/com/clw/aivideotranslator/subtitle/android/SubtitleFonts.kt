package com.clw.aivideotranslator.subtitle.android

import android.content.Context
import android.graphics.Typeface
import com.clw.aivideotranslator.R
import java.security.MessageDigest

data class SubtitleFontProfile(
    val profileId: String,
    val familyLabel: String,
    val assetSha256: String,
    val weight: Int,
) {
    init {
        require(profileId.isNotBlank())
        require(familyLabel.isNotBlank())
        require(assetSha256.matches(Regex("[0-9a-f]{64}"))) { "font asset must have a pinned SHA-256" }
        require(weight in 100..900)
    }
}

object SubtitleFonts {
    const val ARCHITECTURE_VERSION = "bundled-font-v1"

    fun requirePinned(profile: SubtitleFontProfile): SubtitleFontProfile = profile

    /** Experimental candidate only. AR-03 leaves the production family/weight X003-gated. */
    fun loadExperimentCandidate(context: Context): LoadedSubtitleFont = LoadedSubtitleFont.load(context)
}

/** Cannot be assembled from an arbitrary device Typeface plus a claimed asset hash. */
class LoadedSubtitleFont private constructor(
    val typeface: Typeface,
    val profile: SubtitleFontProfile,
) {
    fun supports(text: String): Boolean = BundledArabicCoverage.supports(text)

    companion object {
        internal fun load(context: Context): LoadedSubtitleFont {
            val resource = R.font.tv1_noto_sans_arabic_regular
            val digest = MessageDigest.getInstance("SHA-256")
            context.resources.openRawResource(resource).use { stream ->
                val buffer = ByteArray(8_192)
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            check(actualHash == BundledArabicCoverage.ASSET_SHA256) { "bundled font integrity failure" }
            val typeface = context.resources.getFont(resource)
            check(typeface.weight == 400 && !typeface.isItalic) { "unexpected bundled font style" }
            return LoadedSubtitleFont(typeface, SubtitleFontProfile(
                profileId = "x003-noto-sans-arabic-full-regular-2.012",
                familyLabel = "Noto Sans Arabic", assetSha256 = actualHash, weight = 400,
            ))
        }
    }
}

