plugins {
    id("kurenai.android.library")
    id("kurenai.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.kurenai.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:source"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))
    implementation(project(":core:network"))
    implementation(project(":core:image"))
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    implementation(libs.hilt.compiler.ext)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.documentfile)
    implementation(libs.jsoup)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite)
    implementation(libs.okhttp)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    testImplementation(libs.junit)
}
