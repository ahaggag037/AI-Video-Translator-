package com.clw.aivideotranslator.subtitle.android

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
}
