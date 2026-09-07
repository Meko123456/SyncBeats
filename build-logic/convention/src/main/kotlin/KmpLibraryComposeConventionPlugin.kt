import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.compose.ComposeExtension

/**
 * Adds Compose Multiplatform to a module that already applies `syncbeats.kmp.library`, with the
 * runtime/foundation/ui/material3 set every screen needs. Declared once here so no feature module
 * repeats it — and so a Compose version bump is a single edit.
 */
class KmpLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.compose")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        val compose = extensions.getByType(ComposeExtension::class.java).dependencies
        dependencies {
            add("commonMainImplementation", compose.runtime)
            add("commonMainImplementation", compose.foundation)
            add("commonMainImplementation", compose.ui)
        }
    }
}
