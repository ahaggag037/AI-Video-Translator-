package com.clw.aivideotranslator.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductOutputNamesTest {
    @Test fun normalizesUnsafeAndBlankNamesWithoutChangingExtensions() {
        assertEquals("translated_video", ProductOutputNames.base("   "))
        assertEquals("my_video_2026", ProductOutputNames.base("my/video\\2026"))
        assertEquals("video", ProductOutputNames.base("..video.."))
        assertEquals("hello_world", ProductOutputNames.base(" hello\u0000  world "))
        assertEquals("movie.srt", ProductOutputNames.srt("movie"))
        assertEquals("movie.mp4", ProductOutputNames.mp4("movie"))
    }

    @Test fun baseNameIsBoundedAndCannotEscapeStoragePath() {
        val value = ProductOutputNames.base("a".repeat(300) + "/../x")
        assertTrue(value.length <= 96)
        assertFalse(value.contains('/'))
        assertFalse(value.contains('\\'))
        assertFalse(value.contains(".."))
    }
}
