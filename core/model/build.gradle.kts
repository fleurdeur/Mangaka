plugins {
    id("kurenai.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.kurenai.core.model"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.collections.immutable)
}
