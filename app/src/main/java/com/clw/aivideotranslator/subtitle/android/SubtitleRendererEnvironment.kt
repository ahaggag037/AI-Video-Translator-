package com.clw.aivideotranslator.subtitle.android

import android.icu.util.VersionInfo
import android.os.Build
import java.security.MessageDigest

/**
 * Stable identity for the Android native subtitle renderer used to validate persisted descriptors.
 *
 * The raw device build fingerprint is intentionally not persisted. Its SHA-256 participates in the
 * identity so an OTA/OEM renderer change invalidates derived render snapshots instead of silently
 * trusting layout produced by a different native stack.
 */
object SubtitleRendererEnvironment {
    const val PROFILE_VERSION = "android-native-subtitle-renderer-v1"

    fun current(): String = buildString {
        append("profile=").append(PROFILE_VERSION)
        append(";api=").append(Build.VERSION.SDK_INT)
        append(";icu=").append(VersionInfo.ICU_VERSION)
        append(";layout=").append(SubtitleDefaults.VERSION)
        append(";font=").append(SubtitleFonts.ARCHITECTURE_VERSION)
        append(";buildSha256=").append(sha256(Build.FINGERPRINT))
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
