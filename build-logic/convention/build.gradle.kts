plugins {
    `kotlin-dsl`
}

group = "app.kurenai.buildlogic"

dependencies {
    compileOnly("com.android.tools.build:gradle:9.4.1")
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    compileOnly("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.3.14")
    compileOnly("com.google.dagger:hilt-android-gradle-plugin:2.60.1")
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "kurenai.android.library"
            implementationClass = "app.kurenai.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "kurenai.android.application"
            implementationClass = "app.kurenai.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = "kurenai.android.compose"
            implementationClass = "app.kurenai.buildlogic.AndroidComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "kurenai.android.hilt"
            implementationClass = "app.kurenai.buildlogic.AndroidHiltConventionPlugin"
        }
        register("androidRoom") {
            id = "kurenai.android.room"
            implementationClass = "app.kurenai.buildlogic.AndroidRoomConventionPlugin"
        }
        register("androidFeature") {
            id = "kurenai.android.feature"
            implementationClass = "app.kurenai.buildlogic.AndroidFeatureConventionPlugin"
        }
    }
}
