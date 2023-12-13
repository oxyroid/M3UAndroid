pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://plugins.gradle.org/m2/")
    }
}
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://androidx.dev/storage/compose-compiler/repository/")
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
        maven {
            setUrl("http://4thline.org/m2")
            isAllowInsecureProtocol = true
        }
    }
}
rootProject.name = "M3U"
include(":androidApp")
include(":core")
include(":data")
include(":material")
include(
    ":features:main",
    ":features:setting",
    ":features:stream",
    ":features:playlist",
    ":features:favorite",
    ":features:console",
    ":features:crash",
    ":features:about"
)
include(":benchmark")
include(":i18n")
include(":ui")
include(":dlna")

