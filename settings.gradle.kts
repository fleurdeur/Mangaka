pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io") {
            content { includeGroup("com.github.KotatsuApp") }
        }
    }
}

rootProject.name = "Mangaka"

include(":app")

include(":core:common")
include(":core:model")
include(":core:database")
include(":core:datastore")
include(":core:network")
include(":core:source")
include(":core:parsers")
include(":core:image")
include(":core:data")
include(":core:designsystem")

include(":feature:library")
include(":feature:browse")
include(":feature:details")
include(":feature:reader")
include(":feature:updates")
include(":feature:settings")
include(":feature:more")
include(":feature:animator")
include(":feature:translation")

include(":benchmark")
