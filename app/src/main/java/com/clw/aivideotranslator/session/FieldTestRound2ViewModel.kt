package com.clw.aivideotranslator.session

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.TranslationEntry
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class FieldTestRound2Phase {
    RESUMING,
    NO_SOURCE,
    SOURCE_READY,
    STT_RUNNING,
    STT_READY,
    TRANSLATING,
    TRANSLATED,
    STT_UNKNOWN_REMOTE_OUTCOME,
    TRANSLATION_BLOCKED,
    FAILED,
}

internal data class FieldTestRound2UiState(
    val phase: FieldTestRound2Phase,
    val sessionId: String? = null,
    val sourceUri: String? = null,
    val sttResult: NvidiaSttResult? = null,
    val sttProgress: FieldTestSttProgress? = null,
    val units: List<SourceUnit> = emptyList(),
    val entries: List<TranslationEntry> = emptyList(),
    val message: String? = null,
) {
    val busy: Boolean
        get() = phase == FieldTestRound2Phase.RESUMING ||
            phase == FieldTestRound2Phase.STT_RUNNING ||
            phase == FieldTestRound2Phase.TRANSLATING

    companion object {
        fun resuming() = FieldTestRound2UiState(FieldTestRound2Phase.RESUMING)
        fun noSource() = FieldTestRound2UiState(FieldTestRound2Phase.NO_SOURCE)
    }
}

/**
 * Isolated round-2 field-test controller.
 *
 * It deliberately does not create a canonical SourceSnapshot and never reads or writes the
 * production ActiveSessionRegistry pointer. A private field-test pointer remembers only sessions
 * whose ids carry the field-r2 prefix. Full-video STT is recovered from its window journal, while
 * translation reuses the existing per-session durable request/receipt machinery.
 */
internal class FieldTestRound2ViewModel(
    context: Context,
    private val store: TranslationSessionStore,
    private val planStore: TranslationRequestPlanStore,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val fieldPreferences = appContext.getSharedPreferences(FIELD_PREFERENCES, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var activeJob: Job? = null

    private val mutableState = MutableStateFlow(FieldTestRound2UiState.resuming())
    val state = mutableState.asStateFlow()

    init {
        resumeActiveSource()
    }

    fun resumeActiveSource() {
        launchReplacing {
            val resumed = withContext(Dispatchers.IO) {
                val sessionId = readFieldSessionId() ?: return@withContext null
                val attachment = store.readActiveSourceAttachment(sessionId) ?: return@withContext null
                Pair(sessionId, attachment.contentUri)
            }
            mutableState.value = resumed?.let { (sessionId, contentUri) ->
                FieldTestRound2UiState(
                    phase = FieldTestRound2Phase.SOURCE_READY,
                    sessionId = sessionId,
                    sourceUri = contentUri,
                    message = "تم استرداد مصدر الجولة الثانية من pointer مستقل. تشغيل STT سيعيد استخدام RECEIVED journal ولن يعيد إرسال نافذة غير مؤكدة.",
                )
            } ?: FieldTestRound2UiState.noSource()
        }
    }

    fun selectSource(contentUri: String) {
        require(contentUri.isNotBlank()) { "source URI is blank" }
        launchReplacing {
            mutableState.value = FieldTestRound2UiState(
                phase = FieldTestRound2Phase.RESUMING,
                sourceUri = contentUri,
                message = "جارٍ تثبيت هوية الفيديو للجولة الثانية…",
            )
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    val sessionId = "field-r2-${UUID.randomUUID()}"
                    SourceAttachmentBuilder.capture(
                        context = appContext,
                        sessionId = sessionId,
                        contentUri = contentUri,
                    ).getOrThrow().use { captured ->
                        store.createSession(sessionId)
                        store.bindInitialSourceAttachment(sessionId, 0L, captured.attachment)
                    }
                    require(fieldPreferences.edit().putString(FIELD_SESSION_KEY, sessionId).commit()) {
                        "cannot persist field-test session pointer"
                    }
                    sessionId
                }
            }
            outcome.fold(
                onSuccess = { sessionId ->
                    mutableState.value = FieldTestRound2UiState(
                        phase = FieldTestRound2Phase.SOURCE_READY,
                        sessionId = sessionId,
                        sourceUri = contentUri,
                        message = "الفيديو مربوط بجلسة field-test دائمة ومستقلة عن active session الرسمي.",
                    )
                },
                onFailure = { error ->
                    if (error is CancellationException) return@fold
                    mutableState.value = FieldTestRound2UiState(
                        phase = FieldTestRound2Phase.FAILED,
                        sourceUri = contentUri,
                        message = error.message ?: "تعذر تثبيت مصدر الجولة الثانية.",
                    )
                },
            )
        }
    }

    fun runFullVideoStt(apiKey: String) {
        val current = mutableState.value
        val sessionId = current.sessionId
        val sourceUri = current.sourceUri
        if (sessionId == null || sourceUri == null || current.phase == FieldTestRound2Phase.RESUMING) {
            mutableState.value = current.copy(
                phase = FieldTestRound2Phase.FAILED,
                message = "اربط فيديو صالحًا قبل تشغيل full-video STT.",
            )
            return
        }
        if (apiKey.isBlank()) {
            mutableState.value = current.copy(
                phase = FieldTestRound2Phase.FAILED,
                message = "NVIDIA API Key فارغ.",
            )
            return
        }

        launchReplacing {
            mutableState.value = current.copy(
                phase = FieldTestRound2Phase.STT_RUNNING,
                sttResult = null,
                sttProgress = null,
                units = emptyList(),
                entries = emptyList(),
                message = "جارٍ بدء full-video STT…",
            )
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    check(readFieldSessionId() == sessionId) { "field-test session changed before STT" }
                    FieldTestFullVideoSttOperation.transcribe(
                        context = appContext,
                        store = store,
                        sessionId = sessionId,
                        apiKey = apiKey,
                        onProgress = { progress ->
                            val latest = mutableState.value
                            if (latest.phase == FieldTestRound2Phase.STT_RUNNING && latest.sessionId == sessionId) {
                                mutableState.value = latest.copy(
                                    sttProgress = progress,
                                    message = sttProgressMessage(progress),
                                )
                            }
                        },
                    ).getOrThrow()
                }
            }
            outcome.fold(
                onSuccess = { result ->
                    val latestProgress = mutableState.value.sttProgress
                    mutableState.value = FieldTestRound2UiState(
                        phase = FieldTestRound2Phase.STT_READY,
                        sessionId = sessionId,
                        sourceUri = sourceUri,
                        sttResult = result,
                        sttProgress = latestProgress,
                        message = "اكتمل full-video STT وتجميع التوقيت على timeline الفيديو الأصلي.",
                    )
                },
                onFailure = { error ->
                    if (error is CancellationException) return@fold
                    val latestProgress = mutableState.value.sttProgress
                    mutableState.value = FieldTestRound2UiState(
                        phase = if (error is UnknownSttRemoteOutcomeException) {
                            FieldTestRound2Phase.STT_UNKNOWN_REMOTE_OUTCOME
                        } else {
                            FieldTestRound2Phase.FAILED
                        },
                        sessionId = sessionId,
                        sourceUri = sourceUri,
                        sttProgress = latestProgress,
                        message = if (error is UnknownSttRemoteOutcomeException) {
                            "توجد نافذة STT في حالة SENT بنتيجة بعيدة غير مؤكدة؛ تم إيقاف الإرسال التلقائي لمنع تكرار الطلب."
                        } else {
                            error.message ?: "تعذر إكمال full-video STT."
                        },
                    )
                },
            )
        }
    }

    fun runSemanticTranslation(apiKey: String) {
        val current = mutableState.value
        val sessionId = current.sessionId
        val sourceUri = current.sourceUri
        val liveStt = current.sttResult
        if (sessionId == null || sourceUri == null || liveStt == null) {
            mutableState.value = current.copy(
                phase = FieldTestRound2Phase.FAILED,
                message = "يلزم نجاح full-video STT في هذه الجلسة قبل الترجمة.",
            )
            return
        }
        if (apiKey.isBlank()) {
            mutableState.value = current.copy(
                phase = FieldTestRound2Phase.FAILED,
                message = "NVIDIA API Key فارغ.",
            )
            return
        }

        launchReplacing {
            mutableState.value = current.copy(
                phase = FieldTestRound2Phase.TRANSLATING,
                units = emptyList(),
                entries = emptyList(),
                message = "جارٍ ترجمة semantic units على امتداد الفيديو كاملًا…",
            )
            val outcome = runCatching {
                withContext(Dispatchers.IO) {
                    check(readFieldSessionId() == sessionId) { "field-test session changed before translation" }
                    FieldTestRound2TranslationOperation.translate(
                        store = store,
                        planStore = planStore,
                        sessionId = sessionId,
                        apiKey = apiKey,
                        liveStt = liveStt,
                    ).getOrThrow()
                }
            }
            outcome.fold(
                onSuccess = { execution -> publishTranslation(sessionId, sourceUri, liveStt, execution) },
                onFailure = { error ->
                    if (error is CancellationException) return@fold
                    mutableState.value = FieldTestRound2UiState(
                        phase = FieldTestRound2Phase.FAILED,
                        sessionId = sessionId,
                        sourceUri = sourceUri,
                        sttResult = liveStt,
                        sttProgress = current.sttProgress,
                        message = error.message ?: "تعذر إكمال ترجمة الجولة الثانية.",
                    )
                },
            )
        }
    }

    private fun publishTranslation(
        sessionId: String,
        sourceUri: String,
        liveStt: NvidiaSttResult,
        execution: DurableTranslationExecution,
    ) {
        val acceptedEntries = execution.batch.units
            .takeWhile {
                it.disposition == DurableTranslationUnitDisposition.REUSED_ENTRY ||
                    it.disposition == DurableTranslationUnitDisposition.ADOPTED
            }
            .map { result ->
                TranslationEntry(
                    sourceUnitId = result.unitId,
                    translatedText = requireNotNull(result.effectiveText) {
                        "accepted translation unit missing effective text"
                    },
                )
            }

        if (execution.batch.completed) {
            val units = execution.units.map { it.legacyUnit }
            require(units.map { it.id } == acceptedEntries.map { it.sourceUnitId }) {
                "completed field-test translation changed semantic unit identity"
            }
            mutableState.value = FieldTestRound2UiState(
                phase = FieldTestRound2Phase.TRANSLATED,
                sessionId = sessionId,
                sourceUri = sourceUri,
                sttResult = liveStt,
                sttProgress = mutableState.value.sttProgress,
                units = units,
                entries = acceptedEntries,
                message = "اكتملت الترجمة الدلالية للفيديو كاملًا؛ المعاينة وSRT وMP4 جاهزة للبناء.",
            )
            return
        }

        val blocker = execution.batch.units.lastOrNull()?.disposition
        mutableState.value = FieldTestRound2UiState(
            phase = FieldTestRound2Phase.TRANSLATION_BLOCKED,
            sessionId = sessionId,
            sourceUri = sourceUri,
            sttResult = liveStt,
            sttProgress = mutableState.value.sttProgress,
            entries = acceptedEntries,
            message = "توقفت الترجمة بأمان: ${blockerLabel(blocker)}",
        )
    }

    private fun sttProgressMessage(progress: FieldTestSttProgress): String {
        val window = if (progress.currentWindow != null && progress.totalWindows != null) {
            " — نافذة ${progress.currentWindow}/${progress.totalWindows}"
        } else if (progress.totalWindows != null) {
            " — ${progress.totalWindows} نافذة"
        } else {
            ""
        }
        return when (progress.stage) {
            FieldTestSttProgressStage.PREPARING_AUDIO -> "جارٍ فك وتجهيز صوت الفيديو الكامل مرة واحدة…"
            FieldTestSttProgressStage.PREFLIGHT -> "جارٍ فحص journal قبل أي إرسال$window"
            FieldTestSttProgressStage.PREPARING_WINDOW -> "جارٍ تجهيز بصمة نافذة STT$window"
            FieldTestSttProgressStage.REUSING_RECEIVED -> "تم استرداد نتيجة RECEIVED بدون إعادة إرسال$window"
            FieldTestSttProgressStage.SENDING -> "جارٍ تثبيت SENT وإرسال نافذة STT$window"
            FieldTestSttProgressStage.WAITING_RESPONSE -> "تم الإرسال؛ جارٍ انتظار رد NVIDIA$window"
            FieldTestSttProgressStage.RECEIVED -> "وصل رد NVIDIA وتم حفظه$window"
            FieldTestSttProgressStage.ASSEMBLING -> "اكتملت النوافذ؛ جارٍ تجميع التوقيت على الفيديو الأصلي…"
        }
    }

    private fun readFieldSessionId(): String? {
        val sessionId = fieldPreferences.getString(FIELD_SESSION_KEY, null) ?: return null
        if (!sessionId.startsWith(FIELD_SESSION_PREFIX)) return null
        return runCatching {
            store.readManifest(sessionId)
            sessionId
        }.getOrNull()
    }

    private fun blockerLabel(value: DurableTranslationUnitDisposition?): String = when (value) {
        DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME -> "نتيجة بعيدة غير مؤكدة؛ لن يعاد الإرسال تلقائيًا."
        DurableTranslationUnitDisposition.REVIEW_REQUIRED -> "نتيجة تحتاج مراجعة قبل الاعتماد."
        DurableTranslationUnitDisposition.AMBIGUOUS_RECEIPTS -> "سجلات محاولات متعارضة لنفس الوحدة."
        DurableTranslationUnitDisposition.STALE_STATE -> "حالة الجلسة تغيّرت أثناء التنفيذ."
        DurableTranslationUnitDisposition.REJECTED -> "رفض التحقق نتيجة إحدى الوحدات."
        DurableTranslationUnitDisposition.TERMINAL -> "أعاد المزود نتيجة نهائية غير قابلة للاعتماد."
        DurableTranslationUnitDisposition.PENDING -> "إحدى النتائج ما زالت معلقة."
        DurableTranslationUnitDisposition.REUSED_ENTRY,
        DurableTranslationUnitDisposition.ADOPTED,
        null,
        -> "لم تكتمل كل الوحدات."
    }

    private fun launchReplacing(block: suspend () -> Unit) {
        activeJob?.cancel()
        activeJob = scope.launch { block() }
    }

    override fun onCleared() {
        activeJob?.cancel()
        scope.cancel()
        super.onCleared()
    }

    internal class Factory(
        private val context: Context,
        private val store: TranslationSessionStore,
        private val planStore: TranslationRequestPlanStore,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == FieldTestRound2ViewModel::class.java) { "unsupported ViewModel class" }
            return FieldTestRound2ViewModel(
                context = context,
                store = store,
                planStore = planStore,
            ) as T
        }
    }

    private companion object {
        const val FIELD_PREFERENCES = "field_test_round2"
        const val FIELD_SESSION_KEY = "active_field_session_id"
        const val FIELD_SESSION_PREFIX = "field-r2-"
    }
}
