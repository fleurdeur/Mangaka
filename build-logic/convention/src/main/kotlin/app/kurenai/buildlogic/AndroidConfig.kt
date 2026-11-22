package app.kurenai.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType

internal fun Project.configureAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = 37

        defaultConfig.minSdk = 24
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        buildFeatures.buildConfig = true
        testOptions.unitTests.isIncludeAndroidResources = true
    }
}

internal fun Project.libs() = extensions.getByType<VersionCatalogsExtension>().named("libs")
