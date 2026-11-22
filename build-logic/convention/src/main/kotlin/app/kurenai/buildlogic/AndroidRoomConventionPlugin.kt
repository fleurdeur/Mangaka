package app.kurenai.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.provideDelegate

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
        dependencies {
            add("implementation", libs.findLibrary("androidx-room-runtime").get())
            add("implementation", libs.findLibrary("androidx-room-ktx").get())
            add("implementation", libs.findLibrary("androidx-room-paging").get())
            add("ksp", libs.findLibrary("androidx-room-compiler").get())
        }
        extensions.findByType(com.android.build.api.dsl.CommonExtension::class.java)?.let {
            file("schemas").mkdirs()
        }
        Unit
    }
}
