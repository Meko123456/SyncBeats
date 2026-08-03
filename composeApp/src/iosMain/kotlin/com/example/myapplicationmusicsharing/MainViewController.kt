package com.example.myapplicationmusicsharing

import androidx.compose.ui.window.ComposeUIViewController
import com.example.myapplicationmusicsharing.data.GoogleAuthController
import com.example.myapplicationmusicsharing.di.initKoin
import com.example.myapplicationmusicsharing.ui.AppRoot
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
