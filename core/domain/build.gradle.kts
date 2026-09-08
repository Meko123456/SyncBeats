plugins {
    id("syncbeats.kmp.library")
}

android {
    namespace = "io.github.meko123456.syncbeats.core.domain"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            implementation(libs.kotlinx.coroutines.core)
            // For the Koin qualifiers both the features and the data layer name.
            api(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
