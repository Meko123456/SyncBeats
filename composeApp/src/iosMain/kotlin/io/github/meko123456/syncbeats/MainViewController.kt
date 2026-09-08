package io.github.meko123456.syncbeats

import androidx.compose.ui.window.ComposeUIViewController
import io.github.meko123456.syncbeats.core.domain.repository.GoogleAuthController
import io.github.meko123456.syncbeats.di.initKoin
import io.github.meko123456.syncbeats.ui.AppRoot
import org.koin.dsl.module
import platform.UIKit.UIViewController

/**
 * Called from Swift once at startup, after FirebaseApp.configure().
 * [googleAuth] is the Swift GoogleSignIn adapter.
 */
fun initialize(googleAuth: GoogleAuthController) {
    initKoin {
        modules(
            module {
                single<GoogleAuthController> { googleAuth }
            }
        )
    }
}

fun MainViewController(): UIViewController = ComposeUIViewController { AppRoot() }
