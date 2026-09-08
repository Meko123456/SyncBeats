plugins {
    id("syncbeats.kmp.library")
    id("syncbeats.kmp.library.compose")
}

android {
    namespace = "io.github.meko123456.syncbeats.core.designsystem"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ErrorCopy turns a domain SyncFailure into a sentence a listener can act on, and it
            // is shared by the room and lobby screens — so it cannot live in either feature.
            // Now-in-Android would split a domain-aware :core:ui from the generic design system;
            // for one shared helper that is ceremony. If more accumulate, split then.
            api(project(":core:domain"))
            implementation(libs.compose.material3)
        }
        androidMain.dependencies {
            // PlatformBackHandler's Android actual delegates to Activity Compose's BackHandler.
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
