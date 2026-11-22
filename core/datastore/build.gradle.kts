plugins {
    id("kurenai.android.library")
    id("kurenai.android.hilt")
}

android {
    namespace = "app.kurenai.core.datastore"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.security.crypto)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
}
