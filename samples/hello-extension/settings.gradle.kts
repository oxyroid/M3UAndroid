pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        exclusiveContent {
            forRepository {
                maven {
                    name = "m3uExtensionSdk"
                    url = providers.gradleProperty("extensionSdkRepository")
                        .map(::file)
                        .get()
                        .toURI()
                }
            }
            filter {
                includeGroup(providers.gradleProperty("extensionSdkGroup").get())
            }
        }
        mavenCentral()
    }
}

rootProject.name = "M3UHelloExtension"
