package io.github.meko123456.syncbeats.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No system back button on iOS; leaving a room goes through the Leave action.
}
