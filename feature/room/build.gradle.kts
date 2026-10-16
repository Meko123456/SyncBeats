plugins {
    id("syncbeats.kmp.feature")
}

android {
    namespace = "io.github.meko123456.syncbeats.feature.room"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coil.compose)
            implementation(libs.compose.material.icons.extended)
        }
        // Compose UI tests run on the iOS simulator, as in :feature:auth.
        iosTest.dependencies {
            implementation(libs.compose.ui.test)
        }
    }
}
