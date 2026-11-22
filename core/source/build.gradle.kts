plugins {
    id("kurenai.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.kurenai.core.source"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
}
