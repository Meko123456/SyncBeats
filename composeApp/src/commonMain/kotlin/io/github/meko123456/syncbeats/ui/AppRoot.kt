package io.github.meko123456.syncbeats.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.github.meko123456.syncbeats.ui.auth.AuthScreen
import io.github.meko123456.syncbeats.ui.auth.AuthViewModel
import io.github.meko123456.syncbeats.ui.room.RoomScreen
import io.github.meko123456.syncbeats.ui.theme.SyncBeatsTheme
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
                    onSignIn = authVm::signIn,
                    onSignUp = authVm::signUp,
                    onGoogleSignIn = authVm::signInWithGoogle,
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
                        authVm.signOut()
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
