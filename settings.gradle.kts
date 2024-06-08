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
include(
    ":features:foryou",
    ":features:setting",
    ":features:channel",
    ":features:playlist",
    ":features:playlist-configuration",
    ":features:favorite",
    ":features:crash"
)
include(":benchmark")
include(":i18n")
include(":ui")
include(":codec:lite", ":codec:rich")
