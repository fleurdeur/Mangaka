plugins {
    id("kurenai.android.library")
    id("kurenai.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.kurenai.core.network"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
