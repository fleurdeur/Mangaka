package app.kurenai.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> {
            configureAndroid(this)
            defaultConfig.consumerProguardFiles("consumer-rules.pro")
            buildFeatures.androidResources = true
            buildFeatures.compose = false
            lint {
                abortOnError = false
                checkReleaseBuilds = false
            }
        }
    }
}
