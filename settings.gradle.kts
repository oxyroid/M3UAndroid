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
include(":smartphone", ":tv")
include(":core")
include(":data")
include(":material")
include(":ui")
include(
    ":business:foryou",
    ":business:favorite",
    ":business:setting",
    ":business:playlist",
    ":business:playlist-configuration",
    ":business:channel",
    ":business:crash"
)
include(":baselineprofile")
include(":i18n")
include(":codec:lite", ":codec:rich")
include(
    ":lint:annotation",
    ":lint:processor"
)
