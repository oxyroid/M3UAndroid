pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://androidx.dev/storage/compose-compiler/repository/")
        maven("https://jitpack.io")
    }
}
rootProject.name = "M3U"
include(":androidApp")
include(":core")
include(":data")
include(":ui")
include(
    ":features:main",
    ":features:setting",
    ":features:live",
    ":features:feed",
    ":features:favorite",
    ":features:console",
    ":features:crash",
    ":features:about"
)
include(":benchmark")
include(":lint")
