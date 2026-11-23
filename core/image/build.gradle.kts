plugins {
    id("kurenai.android.library")
    id("kurenai.android.hilt")
}

android {
    namespace = "app.kurenai.core.image"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:network"))
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
