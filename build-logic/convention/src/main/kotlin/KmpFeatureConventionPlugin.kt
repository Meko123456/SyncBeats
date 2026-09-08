import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.project

/**
 * A feature module: a Compose Multiplatform library that always depends on the domain, the models
 * and the design system, plus the Material 3 / lifecycle / navigation / Koin set every screen
 * needs. A new feature's build file is then just `plugins { id("syncbeats.kmp.feature") }` and a
 * namespace.
 *
 * Features deliberately get :core:domain and never :core:data — a screen talks to ports, so it
 * cannot reach Firebase, Ktor or the platform players even by accident.
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("syncbeats.kmp.library")
        pluginManager.apply("syncbeats.kmp.library.compose")

        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("commonMainImplementation", project(":core:model"))
            add("commonMainImplementation", project(":core:domain"))
            add("commonMainImplementation", project(":core:designsystem"))

            add("commonMainImplementation", libs.findLibrary("compose-material3").get())
            add("commonMainImplementation", libs.findLibrary("jb-lifecycle-viewmodel-compose").get())
            add("commonMainImplementation", libs.findLibrary("jb-lifecycle-runtime-compose").get())
            add("commonMainImplementation", libs.findLibrary("koin-core").get())
            add("commonMainImplementation", libs.findLibrary("koin-compose-viewmodel").get())

            add("commonTestImplementation", libs.findLibrary("kotlinx-coroutines-test").get())
        }
    }
}
