plugins {
    id("kurenai.android.application")
    id("kurenai.android.compose")
    id("kurenai.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.kurenai"

    defaultConfig {
        applicationId = "app.kurenai"
        testInstrumentationRunner = "app.kurenai.HiltTestRunner"
        resourceConfigurations += listOf("en", "ja", "ko", "zh", "es", "fr", "pt", "ru", "id", "it", "de", "ar", "tr", "hi")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Ship small ABI-specific APKs plus one universal APK. All variants use the
    // release signing config from signing.properties; no debug signing fallback.
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    sourceSets {
        getByName("main") {
            assets.srcDirs("src/main/assets")
        }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(project(":core:source"))
    implementation(project(":core:parsers"))
    implementation(project(":core:image"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))

    implementation(project(":feature:library"))
    implementation(project(":feature:browse"))
    implementation(project(":feature:details"))
    implementation(project(":feature:reader"))
    implementation(project(":feature:updates"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:more"))
    implementation(project(":feature:animator"))
    implementation(project(":feature:translation"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler.ext)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
