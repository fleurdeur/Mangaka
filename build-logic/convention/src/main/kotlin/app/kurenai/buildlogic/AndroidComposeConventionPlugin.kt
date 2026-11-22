package app.kurenai.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<CommonExtension> {
                buildFeatures.compose = true
            }
        }
        pluginManager.withPlugin("com.android.application") {
            extensions.configure<CommonExtension> {
                buildFeatures.compose = true
            }
        }
    }
}
