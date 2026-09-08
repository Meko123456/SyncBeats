plugins {
    id("syncbeats.kmp.library")
    alias(libs.plugins.kotlinxSerialization)
}

android {
    namespace = "io.github.meko123456.syncbeats.core.data"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
            api(project(":core:domain"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.koin.core)

            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.database)

            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
        }
        androidMain.dependencies {
            // GitLive's Android artifacts leave Firebase versions to the BoM.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.koin.android)
            implementation(libs.ktor.client.okhttp)

            implementation(libs.media3.exoplayer)
            implementation(libs.media3.session)
            implementation(libs.media3.common)

            implementation(libs.okhttp)
            implementation(libs.newpipe.extractor)

            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.googleid)
            implementation(libs.play.services.auth)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.junit)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.junit)
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.session)
            implementation(libs.media3.common)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
