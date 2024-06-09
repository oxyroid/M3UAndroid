import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.com.android.application) apply false
    alias(libs.plugins.com.android.library) apply false
    alias(libs.plugins.org.jetbrains.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.com.google.dagger.hilt.android) apply false
    alias(libs.plugins.com.google.devtools.ksp) apply false
    alias(libs.plugins.com.android.test) apply false
    alias(libs.plugins.org.jetbrains.kotlin.serialization) apply false
    alias(libs.plugins.org.jetbrains.kotlin.jvm) apply false
}

subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        kotlinExtension.sourceSets {
            all {
                languageSettings {
                    optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                    optIn("androidx.compose.foundation.ExperimentalFoundationApi")
                    optIn("androidx.compose.foundation.layout.ExperimentalLayoutApi")
                    optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                    optIn("androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi")
                    optIn("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
                    optIn("androidx.tv.material3.ExperimentalTvMaterial3Api")
                    optIn("com.google.accompanist.permissions.ExperimentalPermissionsApi")
                }
            }
        }
    }
    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        configure<ComposeCompilerGradlePluginExtension> {
            enableStrongSkippingMode = true
            enableNonSkippingGroupOptimization = true
            enableIntrinsicRemember = true
            includeSourceInformation = true
            val file = rootProject.layout.projectDirectory.file("compose_compiler_config.conf")
            if (file.asFile.exists()) {
                stabilityConfigurationFile.set(file)
            }
            metricsDestination.set(layout.buildDirectory.dir("compose_metrics"))
            reportsDestination.set(layout.buildDirectory.dir("compose_metrics"))
        }
    }
}
