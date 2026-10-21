package com.clw.aivideotranslator.editor

import com.clw.aivideotranslator.presentation.CueStyleOverride
import com.clw.aivideotranslator.presentation.EffectiveSubtitleStyleResolver
import com.clw.aivideotranslator.presentation.SubtitleAnchor
import com.clw.aivideotranslator.presentation.SubtitleStyle
import com.clw.aivideotranslator.presentation.SubtitleTextAlignment
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties

internal object SubtitleStyleDefaults {
    /**
     * Backward-compatible authored default for the already-qualified raster path. The identifiers
     * describe the bundled asset actually loaded by SubtitleFonts; spatial defaults reproduce the
     * legacy bottom-center, 6% safe-area layout and black box when authored styling is introduced.
     */
    val PROJECT = SubtitleStyle(
        fontFamilyId = "x003-noto-sans-arabic-full-regular-2.012",
        fontWeight = 400,
        fontSizeSp = 42f,
        lineHeightSp = 52f,
        textColorArgb = 0xFFFFFFFF,
        opacity = 1f,
        alignment = SubtitleTextAlignment.CENTER,
        outlineWidthDp = 0f,
        shadowEnabled = false,
        backgroundEnabled = true,
        anchor = SubtitleAnchor.BOTTOM_CENTER,
        offsetXFraction = 0f,
        offsetYFraction = 0f,
        safeMarginFraction = 0.06f,
        maxWidthFraction = 0.88f,
        maxLines = 2,
    )
}

internal data class DurableSubtitleStyleState(
    val projectStyle: SubtitleStyle = SubtitleStyleDefaults.PROJECT,
    val cueOverrides: Map<String, CueStyleOverride> = emptyMap(),
) {
    fun effective(cueId: String): SubtitleStyle =
        EffectiveSubtitleStyleResolver.resolve(projectStyle, cueOverrides[cueId])
}

/**
 * Durable committed style truth. UI drafts intentionally live outside this store so a process
 * restart cannot accidentally promote a pending style edit into preview/export truth.
 */
internal class SubtitleStyleStateStore(
    private val sessionsRoot: File,
    private val validateSession: (String) -> Unit,
) {
    fun read(sessionId: String): DurableSubtitleStyleState = synchronized(lock) {
        validateSession(sessionId)
        readUnlocked(sessionId)
    }

    fun setProjectStyle(sessionId: String, style: SubtitleStyle): DurableSubtitleStyleState = synchronized(lock) {
        validateSession(sessionId)
        val next = readUnlocked(sessionId).copy(projectStyle = style)
        writeUnlocked(sessionId, next)
        next
    }

    fun setCueOverride(
        sessionId: String,
        cueId: String,
        override: CueStyleOverride,
    ): DurableSubtitleStyleState = synchronized(lock) {
        require(cueId.isNotBlank()) { "cueId is blank" }
        validateSession(sessionId)
        val current = readUnlocked(sessionId)
        val next = current.copy(cueOverrides = current.cueOverrides + (cueId to override))
        writeUnlocked(sessionId, next)
        next
    }

    fun clearCueOverride(sessionId: String, cueId: String): DurableSubtitleStyleState = synchronized(lock) {
        require(cueId.isNotBlank()) { "cueId is blank" }
        validateSession(sessionId)
        val current = readUnlocked(sessionId)
        val next = current.copy(cueOverrides = current.cueOverrides - cueId)
        writeUnlocked(sessionId, next)
        next
    }

    private fun readUnlocked(sessionId: String): DurableSubtitleStyleState {
        val file = stateFile(sessionId)
        if (!file.exists()) return DurableSubtitleStyleState()
        val properties = Properties().also { props -> file.inputStream().use(props::load) }
        require(properties.getProperty(KEY_SCHEMA) == SCHEMA) { "unsupported style state schema" }
        val project = decodeStyle(properties, PROJECT_PREFIX)
        val cueIds = properties.getProperty(KEY_CUE_IDS)
            .orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map(::decodeId)
        val overrides = cueIds.associateWith { cueId ->
            decodeOverride(properties, "cue.${encodeId(cueId)}.")
        }
        return DurableSubtitleStyleState(projectStyle = project, cueOverrides = overrides)
    }

    private fun writeUnlocked(sessionId: String, state: DurableSubtitleStyleState) {
        val target = stateFile(sessionId)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        val properties = Properties().apply {
            setProperty(KEY_SCHEMA, SCHEMA)
            encodeStyle(this, PROJECT_PREFIX, state.projectStyle)
            setProperty(KEY_CUE_IDS, state.cueOverrides.keys.sorted().joinToString(",") { encodeId(it) })
            state.cueOverrides.toSortedMap().forEach { (cueId, override) ->
                encodeOverride(this, "cue.${encodeId(cueId)}.", override)
            }
        }
        temp.outputStream().use { out ->
            properties.store(out, "AI Video Translator committed subtitle style state")
            out.flush()
            out.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun stateFile(sessionId: String): File = File(File(sessionsRoot, sessionId), FILE_NAME)

    private fun encodeStyle(p: Properties, prefix: String, style: SubtitleStyle) {
        p.setProperty(prefix + "fontFamilyId", style.fontFamilyId)
        p.setProperty(prefix + "fontWeight", style.fontWeight.toString())
        p.setProperty(prefix + "fontSizeSp", style.fontSizeSp.toString())
        p.setProperty(prefix + "lineHeightSp", style.lineHeightSp.toString())
        p.setProperty(prefix + "textColorArgb", style.textColorArgb.toString())
        p.setProperty(prefix + "opacity", style.opacity.toString())
        p.setProperty(prefix + "alignment", style.alignment.name)
        p.setProperty(prefix + "outlineWidthDp", style.outlineWidthDp.toString())
        p.setProperty(prefix + "shadowEnabled", style.shadowEnabled.toString())
        p.setProperty(prefix + "backgroundEnabled", style.backgroundEnabled.toString())
        p.setProperty(prefix + "anchor", style.anchor.name)
        p.setProperty(prefix + "offsetXFraction", style.offsetXFraction.toString())
        p.setProperty(prefix + "offsetYFraction", style.offsetYFraction.toString())
        p.setProperty(prefix + "safeMarginFraction", style.safeMarginFraction.toString())
        p.setProperty(prefix + "maxWidthFraction", style.maxWidthFraction.toString())
        p.setProperty(prefix + "maxLines", style.maxLines.toString())
    }

    private fun decodeStyle(p: Properties, prefix: String): SubtitleStyle = SubtitleStyle(
        fontFamilyId = p.required(prefix + "fontFamilyId"),
        fontWeight = p.required(prefix + "fontWeight").toInt(),
        fontSizeSp = p.required(prefix + "fontSizeSp").toFloat(),
        lineHeightSp = p.required(prefix + "lineHeightSp").toFloat(),
        textColorArgb = p.required(prefix + "textColorArgb").toLong(),
        opacity = p.required(prefix + "opacity").toFloat(),
        alignment = SubtitleTextAlignment.valueOf(p.required(prefix + "alignment")),
        outlineWidthDp = p.required(prefix + "outlineWidthDp").toFloat(),
        shadowEnabled = p.required(prefix + "shadowEnabled").toBooleanStrict(),
        backgroundEnabled = p.required(prefix + "backgroundEnabled").toBooleanStrict(),
        anchor = SubtitleAnchor.valueOf(p.required(prefix + "anchor")),
        offsetXFraction = p.required(prefix + "offsetXFraction").toFloat(),
        offsetYFraction = p.required(prefix + "offsetYFraction").toFloat(),
        safeMarginFraction = p.required(prefix + "safeMarginFraction").toFloat(),
        maxWidthFraction = p.required(prefix + "maxWidthFraction").toFloat(),
        maxLines = p.required(prefix + "maxLines").toInt(),
    )

    private fun encodeOverride(p: Properties, prefix: String, o: CueStyleOverride) {
        o.fontFamilyId?.let { p.setProperty(prefix + "fontFamilyId", it) }
        o.fontWeight?.let { p.setProperty(prefix + "fontWeight", it.toString()) }
        o.fontSizeSp?.let { p.setProperty(prefix + "fontSizeSp", it.toString()) }
        o.lineHeightSp?.let { p.setProperty(prefix + "lineHeightSp", it.toString()) }
        o.textColorArgb?.let { p.setProperty(prefix + "textColorArgb", it.toString()) }
        o.opacity?.let { p.setProperty(prefix + "opacity", it.toString()) }
        o.alignment?.let { p.setProperty(prefix + "alignment", it.name) }
        o.outlineWidthDp?.let { p.setProperty(prefix + "outlineWidthDp", it.toString()) }
        o.shadowEnabled?.let { p.setProperty(prefix + "shadowEnabled", it.toString()) }
        o.backgroundEnabled?.let { p.setProperty(prefix + "backgroundEnabled", it.toString()) }
        o.anchor?.let { p.setProperty(prefix + "anchor", it.name) }
        o.offsetXFraction?.let { p.setProperty(prefix + "offsetXFraction", it.toString()) }
        o.offsetYFraction?.let { p.setProperty(prefix + "offsetYFraction", it.toString()) }
        o.safeMarginFraction?.let { p.setProperty(prefix + "safeMarginFraction", it.toString()) }
        o.maxWidthFraction?.let { p.setProperty(prefix + "maxWidthFraction", it.toString()) }
        o.maxLines?.let { p.setProperty(prefix + "maxLines", it.toString()) }
    }

    private fun decodeOverride(p: Properties, prefix: String) = CueStyleOverride(
        fontFamilyId = p.getProperty(prefix + "fontFamilyId"),
        fontWeight = p.getProperty(prefix + "fontWeight")?.toInt(),
        fontSizeSp = p.getProperty(prefix + "fontSizeSp")?.toFloat(),
        lineHeightSp = p.getProperty(prefix + "lineHeightSp")?.toFloat(),
        textColorArgb = p.getProperty(prefix + "textColorArgb")?.toLong(),
        opacity = p.getProperty(prefix + "opacity")?.toFloat(),
        alignment = p.getProperty(prefix + "alignment")?.let(SubtitleTextAlignment::valueOf),
        outlineWidthDp = p.getProperty(prefix + "outlineWidthDp")?.toFloat(),
        shadowEnabled = p.getProperty(prefix + "shadowEnabled")?.toBooleanStrict(),
        backgroundEnabled = p.getProperty(prefix + "backgroundEnabled")?.toBooleanStrict(),
        anchor = p.getProperty(prefix + "anchor")?.let(SubtitleAnchor::valueOf),
        offsetXFraction = p.getProperty(prefix + "offsetXFraction")?.toFloat(),
        offsetYFraction = p.getProperty(prefix + "offsetYFraction")?.toFloat(),
        safeMarginFraction = p.getProperty(prefix + "safeMarginFraction")?.toFloat(),
        maxWidthFraction = p.getProperty(prefix + "maxWidthFraction")?.toFloat(),
        maxLines = p.getProperty(prefix + "maxLines")?.toInt(),
    )

    private fun Properties.required(key: String): String =
        requireNotNull(getProperty(key)) { "missing style field: $key" }

    private fun encodeId(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decodeId(value: String): String = String(
        Base64.getUrlDecoder().decode(value),
        Charsets.UTF_8,
    )

    private companion object {
        val lock = Any()
        const val FILE_NAME = "subtitle_style.properties"
        const val SCHEMA = "subtitle-style-v1"
        const val KEY_SCHEMA = "schema"
        const val KEY_CUE_IDS = "cueIds"
        const val PROJECT_PREFIX = "project."
    }
}