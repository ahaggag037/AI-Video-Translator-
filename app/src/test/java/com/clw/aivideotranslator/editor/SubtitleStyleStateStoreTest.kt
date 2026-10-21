package com.clw.aivideotranslator.editor

import com.clw.aivideotranslator.presentation.CueStyleOverride
import com.clw.aivideotranslator.presentation.EffectiveSubtitleStyleResolver
import com.clw.aivideotranslator.presentation.SubtitleAnchor
import com.clw.aivideotranslator.presentation.SubtitleStyle
import com.clw.aivideotranslator.presentation.SubtitleTextAlignment
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleStyleStateStoreTest {
    private fun root(): File = Files.createTempDirectory("subtitle-style-store").toFile().also { root ->
        check(File(root, "s1").mkdirs())
    }

    private fun store(root: File) = SubtitleStyleStateStore(root) { _ -> Unit }

    private val project = SubtitleStyle(
        fontFamilyId = "noto-sans-arabic",
        fontWeight = 600,
        fontSizeSp = 42f,
        lineHeightSp = 52f,
        textColorArgb = 0xFFFFFFFF,
        opacity = 1f,
        alignment = SubtitleTextAlignment.CENTER,
        outlineWidthDp = 2f,
        shadowEnabled = true,
        backgroundEnabled = false,
        anchor = SubtitleAnchor.BOTTOM_CENTER,
        offsetXFraction = 0f,
        offsetYFraction = 0f,
        safeMarginFraction = 0.05f,
        maxWidthFraction = 0.86f,
        maxLines = 2,
    )

    @Test fun missingStateUsesProductionDefaultStyle() {
        val root = root()
        try {
            assertEquals(SubtitleStyleDefaults.PROJECT, store(root).read("s1").projectStyle)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun committedProjectStyleSurvivesReopen() {
        val root = root()
        try {
            store(root).setProjectStyle("s1", project)

            val reopened = store(root).read("s1")
            assertEquals(project, reopened.projectStyle)
            assertEquals(project, reopened.effective("u1"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun cueOverrideSurvivesReopenWithoutChangingProjectStyle() {
        val root = root()
        try {
            store(root).setProjectStyle("s1", project)
            store(root).setCueOverride(
                sessionId = "s1",
                cueId = "u0001",
                override = CueStyleOverride(fontSizeSp = 48f, backgroundEnabled = true),
            )

            val reopened = store(root).read("s1")
            val effective = reopened.effective("u0001")
            assertEquals(project, reopened.projectStyle)
            assertEquals(48f, effective.fontSizeSp)
            assertEquals(true, effective.backgroundEnabled)
            assertEquals(project.lineHeightSp, effective.lineHeightSp)
            assertNull(reopened.cueOverrides["u0002"])
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun clearingCueOverrideRestoresProjectStyle() {
        val root = root()
        try {
            store(root).setProjectStyle("s1", project)
            store(root).setCueOverride("s1", "u0001", CueStyleOverride(opacity = 0.5f))
            store(root).clearCueOverride("s1", "u0001")

            val reopened = store(root).read("s1")
            assertNull(reopened.cueOverrides["u0001"])
            assertEquals(project, reopened.effective("u0001"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun effectiveStyleMatchesFieldLevelResolver() {
        val root = root()
        try {
            val override = CueStyleOverride(anchor = SubtitleAnchor.TOP_CENTER, maxLines = 3)
            store(root).setProjectStyle("s1", project)
            store(root).setCueOverride("s1", "u0001", override)

            assertEquals(
                EffectiveSubtitleStyleResolver.resolve(project, override),
                store(root).read("s1").effective("u0001"),
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
