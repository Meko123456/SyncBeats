import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * The base for every SyncBeats library module: a Kotlin Multiplatform library with the same three
 * targets the app ships — Android, iosArm64 and iosSimulatorArm64.
 *
 * Every module is multiplatform rather than JVM-only on purpose. The domain has to be reachable
 * from the iOS framework, so a JVM-only module (which is what an Android-only app would use) would
 * quietly exclude iOS. Keeping the target list here means adding a platform later is a one-line
 * change in one file instead of a sweep across nine build scripts.
 *
 * Each module still sets its own `android { namespace }` — that is genuinely per-module.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        pluginManager.apply("com.android.library")

        extensions.configure(KotlinMultiplatformExtension::class.java) {
            androidTarget {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_17)
                }
            }
            iosArm64()
            iosSimulatorArm64()
        }

        extensions.configure(LibraryExtension::class.java) {
            compileSdk = 36
            defaultConfig {
                minSdk = 26
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
}
