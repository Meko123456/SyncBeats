plugins {
    id("syncbeats.kmp.feature")
}

android {
    namespace = "io.github.meko123456.syncbeats.feature.auth"
}

kotlin {
    sourceSets {
        // Compose UI tests run on the iOS simulator. On Android they would need Robolectric or a
        // device, and the screen is the same common code either way.
        iosTest.dependencies {
            implementation(libs.compose.ui.test)
        }
    }
}
