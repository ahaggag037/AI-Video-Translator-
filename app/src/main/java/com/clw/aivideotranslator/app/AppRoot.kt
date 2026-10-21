package com.clw.aivideotranslator.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.clw.aivideotranslator.ui.home.HomeScreen
import com.clw.aivideotranslator.ui.home.HomeStatus
import com.clw.aivideotranslator.ui.source.SourceReviewPresentation
import com.clw.aivideotranslator.ui.source.SourceReviewScreen
import com.clw.aivideotranslator.ui.theme.QuietProTheme

/**
 * Final product root shell. It deliberately receives already-derived UI facts/callbacks rather than
 * reading stores directly; durable session state remains the semantic source of truth outside UI.
 */
@Composable
internal fun AppRoot(
    route: ProductRoute,
    homeStatus: HomeStatus,
    sourceSelectionEnabled: Boolean,
    sourceReviewPresentation: SourceReviewPresentation,
    onChooseVideo: () -> Unit,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    onBackFromSourceReview: () -> Unit,
    onChangeVideo: () -> Unit,
    onAdoptVideo: () -> Unit,
    showDeveloperTools: Boolean = false,
    onOpenDeveloperTools: () -> Unit = {},
) {
    QuietProTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            when (route) {
                ProductRoute.HOME -> HomeScreen(
                    status = homeStatus,
                    sourceSelectionEnabled = sourceSelectionEnabled,
                    onChooseVideo = onChooseVideo,
                    onContinue = onContinue,
                    onOpenSettings = onOpenSettings,
                    showDeveloperTools = showDeveloperTools,
                    onOpenDeveloperTools = onOpenDeveloperTools,
                )

                ProductRoute.SOURCE_REVIEW -> SourceReviewScreen(
                    presentation = sourceReviewPresentation,
                    onBack = onBackFromSourceReview,
                    onChangeVideo = onChangeVideo,
                    onAdoptVideo = onAdoptVideo,
                )
            }
        }
    }
}
