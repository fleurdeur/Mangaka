plugins {
    id("kurenai.android.feature")
}

android {
    namespace = "app.kurenai.feature.updates"
}

dependencies {
    implementation(project(":core:data"))
    implementation(project(":core:source"))
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.gif)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
}
