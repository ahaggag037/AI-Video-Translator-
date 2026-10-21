package com.clw.aivideotranslator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.clw.aivideotranslator.app.ProductAppHost
import com.clw.aivideotranslator.session.TranslationSessionViewModel
import com.clw.aivideotranslator.source.SourceSelectionViewModel

/** Final product launcher. The legacy engineering Activity remains available only as source code. */
class ProductMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as AiVideoTranslatorApplication
        val sessionViewModel = ViewModelProvider(
            this,
            TranslationSessionViewModel.Factory(
                context = this,
                activeSessionOwner = app.activeTranslationSession,
                store = app.translationSessions,
                planStore = app.translationRequestPlans,
                fullVideoSttWindowJournal = app.fullVideoSttWindowJournal,
                fullVideoSttAggregateStore = app.fullVideoSttAggregates,
            ),
        )[TranslationSessionViewModel::class.java]
        val sourceSelectionViewModel =
            ViewModelProvider(this)[SourceSelectionViewModel::class.java]

        setContent {
            ProductAppHost(
                sessionViewModel = sessionViewModel,
                sourceSelectionViewModel = sourceSelectionViewModel,
                editorTextStore = app.editorTextStates,
                displayTimingStore = app.displayTimingStates,
                subtitleStyleStore = app.subtitleStyleStates,
                credentialStore = app.aiCredentialStore,
            )
        }
    }
}
