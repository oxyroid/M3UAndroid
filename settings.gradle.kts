pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://plugins.gradle.org/m2/")
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
    }
}
rootProject.name = "M3U"
include(":androidApp")
include(":core")
include(":data")
include(":material")
include(":ui")
include(
    ":feature:foryou",
    ":feature:favorite",
    ":feature:setting",
    ":feature:playlist",
    ":feature:playlist-configuration",
    ":feature:channel",
    ":feature:crash"
)
include(":benchmark")
include(":i18n")
include(":codec:lite", ":codec:rich")
include(":annotation")
include(":processor")
