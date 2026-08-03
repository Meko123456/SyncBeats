package com.example.myapplicationmusicsharing.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // No system back button on iOS; leaving a room goes through the Leave action.
}
