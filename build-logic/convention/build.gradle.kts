plugins {
    `kotlin-dsl`
}

group = "io.github.meko123456.syncbeats.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.compose.multiplatform.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "syncbeats.kmp.library"
            implementationClass = "KmpLibraryConventionPlugin"
        }
        register("kmpLibraryCompose") {
            id = "syncbeats.kmp.library.compose"
            implementationClass = "KmpLibraryComposeConventionPlugin"
        }
        register("kmpFeature") {
            id = "syncbeats.kmp.feature"
            implementationClass = "KmpFeatureConventionPlugin"
        }
    }
}
