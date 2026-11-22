plugins {
    id("kurenai.android.library")
}

android {
    namespace = "app.kurenai.core.common"
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
}
