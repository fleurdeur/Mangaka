plugins {
    id("kurenai.android.library")
    id("kurenai.android.compose")
}

android {
    namespace = "app.kurenai.core.designsystem"
}

dependencies {
    api(project(":core:model"))
    api(libs.coil.compose)
    api(libs.coil.network.okhttp)
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.material.icons.extended)
    api(libs.androidx.compose.animation)
    api(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.core.ktx)
}
