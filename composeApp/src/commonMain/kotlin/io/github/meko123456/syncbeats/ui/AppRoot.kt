package io.github.meko123456.syncbeats.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.github.meko123456.syncbeats.core.designsystem.theme.SyncBeatsTheme
import io.github.meko123456.syncbeats.feature.auth.AuthIntent
import io.github.meko123456.syncbeats.feature.auth.AuthScreen
import io.github.meko123456.syncbeats.feature.auth.AuthViewModel
import io.github.meko123456.syncbeats.ui.room.RoomScreen
import kotlinx.serialization.Serializable
import org.koin.compose.viewmodel.koinViewModel

@Serializable
private object AuthRoute

@Serializable
private object MainRoute

@Serializable
private data class RoomRoute(val roomId: String, val autoplay: Boolean = false)

@Composable
fun AppRoot() {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }

    SyncBeatsTheme {
        val authVm: AuthViewModel = koinViewModel()
        val state by authVm.state.collectAsState()
        val nav = rememberNavController()

        NavHost(
            navController = nav,
            startDestination = if (state.signedIn) MainRoute else AuthRoute,
        ) {
            composable<AuthRoute> {
                AuthScreen(
                    state = state,
                    onSignIn = { email, password -> authVm.onIntent(AuthIntent.SignIn(email, password)) },
                    onSignUp = { email, password, username -> authVm.onIntent(AuthIntent.SignUp(email, password, username)) },
                    onGoogleSignIn = { authVm.onIntent(AuthIntent.SignInWithGoogle) },
                    navigateNext = {
                        nav.navigate(MainRoute) {
                            popUpTo(AuthRoute) { inclusive = true }
                        }
                    },
                )
            }
            composable<MainRoute> {
                MainScreen(
                    onEnterRoom = { roomId, autoplay ->
                        nav.navigate(RoomRoute(roomId, autoplay))
                    },
                    onSignOut = {
                        authVm.onIntent(AuthIntent.SignOut)
                        nav.navigate(AuthRoute) {
                            popUpTo(MainRoute) { inclusive = true }
                        }
                    },
                )
            }
            composable<RoomRoute> { entry ->
                val route = entry.toRoute<RoomRoute>()
                RoomScreen(
                    roomId = route.roomId,
                    autoplay = route.autoplay,
                    onLeave = { nav.popBackStack() },
                )
            }
        }
    }
}
