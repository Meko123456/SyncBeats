package io.github.meko123456.syncbeats

import android.content.Context
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseOptions
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.database.database
import dev.gitlive.firebase.initialize

/**
 * Points a debug build at the local Firebase emulators when it was built with
 * `-Psyncbeats.firebaseEmulatorHost` (SETUP.md), so the app runs with no Firebase project at all.
 *
 * Such a build leaves google-services.json out, so nothing has set Firebase up by the time this
 * runs. It sets it up with a made-up project: a `demo-` id is one the emulators accept without
 * an account and that no real project can have, so nothing this build does can reach one. The
 * database URL names the namespace the emulator loads database.rules.json into for that project.
 */
internal object FirebaseEmulators {

    private const val PROJECT = "demo-syncbeats"

    /** The ports in firebase.json. */
    private const val AUTH_PORT = 9099
    private const val DATABASE_PORT = 9100

    fun useIfConfigured(context: Context) {
        val host = BuildConfig.FIREBASE_EMULATOR_HOST
        if (host.isEmpty()) return
        Firebase.initialize(
            context,
            FirebaseOptions(
                applicationId = "1:000000000000:android:0000000000000000",
                apiKey = "demo-api-key",
                databaseUrl = "https://$PROJECT-default-rtdb.firebaseio.com",
                projectId = PROJECT,
            ),
        )
        Firebase.auth.useEmulator(host, AUTH_PORT)
        Firebase.database.useEmulator(host, DATABASE_PORT)
    }
}
