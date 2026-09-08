plugins {
    id("syncbeats.kmp.library")
}

android {
    namespace = "io.github.meko123456.syncbeats.core.testing"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // These fakes implement the domain ports, so they are part of main (not test) sources
            // here — four feature modules consume them, and a test source set is not publishable
            // to other modules.
            api(project(":core:model"))
            api(project(":core:domain"))
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
