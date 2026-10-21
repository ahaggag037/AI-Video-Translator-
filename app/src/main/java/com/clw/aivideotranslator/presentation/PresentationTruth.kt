package com.clw.aivideotranslator.presentation

import java.security.MessageDigest

internal data class FrameGeometry(
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(widthPx > 0)
        require(heightPx > 0)
    }
}

internal data class PinnedFontProfile(
    val profileId: String,
    val familyId: String,
    val weight: Int,
    val assetSha256: String,
) {
    init {
        require(profileId.isNotBlank())
        require(familyId.isNotBlank())
        require(weight in 1..1000)
        require(assetSha256.isNotBlank())
    }
}

internal data class PresentationTruth(
    val cueId: String,
    val textRevisionId: String,
    val effectiveText: String,
    val displayStartUs: Long,
    val displayEndUs: Long,
    val style: SubtitleStyle,
    val frameGeometry: FrameGeometry,
    val fontProfile: PinnedFontProfile,
) {
    init {
        require(cueId.isNotBlank())
        require(textRevisionId.isNotBlank())
        require(effectiveText.isNotBlank())
        require(displayStartUs >= 0L)
        require(displayEndUs > displayStartUs)
        require(style.fontFamilyId == fontProfile.familyId) {
            "effective style font family must match pinned font profile"
        }
        require(style.fontWeight == fontProfile.weight) {
            "effective style weight must match pinned font profile"
        }
    }
}

internal enum class LayoutStatus {
    FITS,
    OVERFLOW,
    REVIEW_REQUIRED,
}

internal data class AuthoritativeLayoutSnapshot(
    val key: String,
    val truth: PresentationTruth,
    val status: LayoutStatus,
    val lines: List<String>,
) {
    init {
        require(key.isNotBlank())
        require(lines.isNotEmpty())
    }
}

/**
 * Deterministic cache/freeze key for the exact truth shared by Editor, Fullscreen Preview and MP4.
 * No device/theme/UI state is included.
 */
internal object PresentationSnapshotKey {
    fun from(truth: PresentationTruth): String {
        val style = truth.style
        val canonical = buildString {
            append("cue=").append(truth.cueId).append('\n')
            append("textRevision=").append(truth.textRevisionId).append('\n')
            append("text=").append(truth.effectiveText).append('\n')
            append("startUs=").append(truth.displayStartUs).append('\n')
            append("endUs=").append(truth.displayEndUs).append('\n')
            append("fontFamily=").append(style.fontFamilyId).append('\n')
            append("fontWeight=").append(style.fontWeight).append('\n')
            append("fontSizeSp=").append(style.fontSizeSp).append('\n')
            append("lineHeightSp=").append(style.lineHeightSp).append('\n')
            append("textColorArgb=").append(style.textColorArgb).append('\n')
            append("opacity=").append(style.opacity).append('\n')
            append("alignment=").append(style.alignment.name).append('\n')
            append("outlineWidthDp=").append(style.outlineWidthDp).append('\n')
            append("shadowEnabled=").append(style.shadowEnabled).append('\n')
            append("backgroundEnabled=").append(style.backgroundEnabled).append('\n')
            append("anchor=").append(style.anchor.name).append('\n')
            append("offsetXFraction=").append(style.offsetXFraction).append('\n')
            append("offsetYFraction=").append(style.offsetYFraction).append('\n')
            append("safeMarginFraction=").append(style.safeMarginFraction).append('\n')
            append("maxWidthFraction=").append(style.maxWidthFraction).append('\n')
            append("maxLines=").append(style.maxLines).append('\n')
            append("frameWidth=").append(truth.frameGeometry.widthPx).append('\n')
            append("frameHeight=").append(truth.frameGeometry.heightPx).append('\n')
            append("fontProfileId=").append(truth.fontProfile.profileId).append('\n')
            append("fontAssetSha256=").append(truth.fontProfile.assetSha256)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
    }
}
