plugins {
    id("kurenai.android.library")
    id("kurenai.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.kurenai.core.parsers"
}

dependencies {
    api(project(":core:source"))
    api(project(":core:model"))
    implementation(project(":core:network"))
    implementation(libs.kotatsu.parsers) {
        exclude(group = "org.json", module = "json")
    }
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
