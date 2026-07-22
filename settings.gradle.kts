pluginManagement {
    includeBuild("native-load-gradle-plugin")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
    }
}
includeBuild("parser")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
    }
}
rootProject.name = "M3U"
include(
    ":app:smartphone",
    ":app:tv"
)
include(":core", ":core:foundation")
include(
    ":extension:api",
    ":extension:runtime",
    ":extension:transport-android",
    ":extension:sdk-android",
)
include(":data")
include(
    ":business:foryou",
    ":business:favorite",
    ":business:setting",
    ":business:playlist",
    ":business:playlist-configuration",
    ":business:channel",
)
include(
    ":baselineprofile:smartphone",
    ":baselineprofile:tv"
)
include(":i18n")
include(":testing:device-benchmark")
include(":testing:mock-server")
include(":testing:extension-reference")
include(
    ":lint:annotation",
    ":lint:processor"
)
