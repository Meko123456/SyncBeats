plugins {
    id("syncbeats.kmp.library")
    alias(libs.plugins.kotlinxSerialization)
}

android {
    namespace = "io.github.meko123456.syncbeats.core.model"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The models are the Firebase wire format, so they carry @Serializable. That is a
            // multiplatform, framework-agnostic library — it does not pull Firebase or Android in,
            // so it does not breach the dependency rule the way a Room entity would.
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
