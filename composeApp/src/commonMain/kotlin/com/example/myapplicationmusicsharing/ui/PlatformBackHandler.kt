package com.example.myapplicationmusicsharing.ui

import androidx.compose.runtime.Composable

/** Android system back → onBack. No-op on iOS (no system back gesture to intercept). */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
