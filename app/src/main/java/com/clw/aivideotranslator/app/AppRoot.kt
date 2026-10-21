package com.clw.aivideotranslator.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import com.clw.aivideotranslator.ui.home.HomeScreen
import com.clw.aivideotranslator.ui.home.HomeStatus
import com.clw.aivideotranslator.ui.theme.QuietProTheme

/**
 * Final product root shell. It deliberately receives already-derived UI facts/callbacks rather than
 * reading stores directly; durable session state remains the semantic source of truth outside UI.
 */
@Composable
internal fun AppRoot(
    homeStatus: HomeStatus,
    sourceSelectionEnabled: Boolean,
    onChooseVideo: () -> Unit,
    onContinue: () -> Unit,
    onOpenSettings: () -> Unit,
    showDeveloperTools: Boolean = false,
    onOpenDeveloperTools: () -> Unit = {},
) {
    QuietProTheme {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HomeScreen(
                status = homeStatus,
                sourceSelectionEnabled = sourceSelectionEnabled,
                onChooseVideo = onChooseVideo,
                onContinue = onContinue,
                onOpenSettings = onOpenSettings,
                showDeveloperTools = showDeveloperTools,
                onOpenDeveloperTools = onOpenDeveloperTools,
            )
        }
    }
}
