import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.googleServices) apply false
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(project(":core:data"))
            implementation(project(":core:designsystem"))
            implementation(project(":feature:auth"))
            implementation(project(":feature:lobby"))
            implementation(project(":feature:home"))
            implementation(project(":feature:room"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.extended)

            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
            implementation(libs.jb.navigation.compose)

            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.gitlive.firebase.auth)
            implementation(libs.gitlive.firebase.database)

            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        androidMain.dependencies {
            // GitLive's Android artifacts leave Firebase versions to the BoM.
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.androidx.activity.compose)
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
        commonTest.dependencies {
            implementation(kotlin("test"))
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
    }
}

android {
    namespace = "io.github.meko123456.syncbeats"
    compileSdk = 36

    defaultConfig {
        // Still com.example: the applicationId is the identity Firebase and the Play Store key
        // off, so changing it needs the new package registered in the Firebase console and a
        // fresh google-services.json first, and it makes the installed app a different app.
        // Tracked on issue #3. The source package and namespace are already renamed.
        applicationId = "com.example.myapplicationmusicsharing"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

// Firebase config is developer-specific; skip the plugin until the file is added
// so a fresh clone still compiles. The app still needs it to run.
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.googleServices.get().pluginId)
} else {
    logger.warn("composeApp/google-services.json missing — Firebase will not work at runtime. See README.md.")
}
