package app.kurenai.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import java.util.Properties

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")
        extensions.configure<ApplicationExtension> {
            configureAndroid(this)
            defaultConfig {
                targetSdk = 37
                versionCode = (findProperty("kurenai.versionCode") as? String)?.toIntOrNull() ?: 1
                versionName = (findProperty("kurenai.versionName") as? String) ?: "1.0.0"
                vectorDrawables.useSupportLibrary = true
            }
            buildTypes {
                debug {
                    applicationIdSuffix = ".debug"
                    isMinifyEnabled = false
                }
                release {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                    val signingProps = rootProject.file("signing.properties")
                    if (signingProps.isFile) {
                        val props = Properties().apply { signingProps.inputStream().use(::load) }
                        signingConfig = signingConfigs.create("release") {
                            storeFile = rootProject.file(props.getProperty("storeFile"))
                            storePassword = props.getProperty("storePassword")
                            keyAlias = props.getProperty("keyAlias")
                            keyPassword = props.getProperty("keyPassword")
                            enableV1Signing = false
                            enableV2Signing = true
                            enableV3Signing = true
                        }
                    }
                }
            }
            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
            lint {
                abortOnError = false
                checkReleaseBuilds = false
            }
            packaging {
                resources.excludes += setOf(
                    "/META-INF/{AL2.0,LGPL2.1}",
                    "META-INF/DEPENDENCIES",
                    "META-INF/LICENSE*",
                    "META-INF/NOTICE*",
                    "META-INF/*.kotlin_module",
                )
            }
        }
    }
}
