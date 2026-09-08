plugins {
    id("syncbeats.kmp.feature")
}

android {
    namespace = "io.github.meko123456.syncbeats.feature.home"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.coil.compose)
            implementation(libs.compose.material.icons.extended)
        }
    }
}
