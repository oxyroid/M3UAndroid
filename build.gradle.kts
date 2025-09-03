import com.android.build.gradle.LibraryExtension
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
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.com.squareup.wire) apply false
    
    // Code quality and security plugins
    alias(libs.plugins.com.github.ben.manes.versions) apply true
    alias(libs.plugins.org.owasp.dependencycheck) apply true
    alias(libs.plugins.io.gitlab.arturbosch.detekt) apply true
    alias(libs.plugins.com.diffplug.spotless) apply true
}

// Dependency updates configuration
dependencyUpdates {
    checkForGradleUpdate = true
    outputFormatter = "json"
    outputDir = "build/dependencyUpdates"
    reportfileName = "report"
    
    rejectVersionIf {
        candidate.version.isNonStable()
    }
}

// Security configuration
dependencyCheck {
    analyzers {
        experimentalEnabled = true
        archiveEnabled = true
        jarEnabled = true
        centralEnabled = true
    }
    format = "ALL"
    outputDirectory = "build/reports"
    scanConfigurations = ["implementation", "api", "kapt", "runtimeOnly"]
}

// Code formatting
spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**/*.kt")
        ktlint("1.0.1")
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.0.1")
    }
}

// Static analysis
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/config/detekt/detekt.yml")
    baseline = file("$projectDir/config/detekt/baseline.xml")
    
    reports {
        html.required = true
        xml.required = true
        txt.required = true
        sarif.required = true
    }
}

// Extension function for version stability check
fun String.isNonStable(): Boolean {
    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { 
        uppercase().contains(it) 
    }
    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
    val isStable = stableKeyword || regex.matches(this)
    return isStable.not()
}
subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xcontext-parameters"
            )
        }
        kotlinExtension.sourceSets.all {
            languageSettings {
                optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
                optIn("androidx.compose.ui.ExperimentalComposeUiApi")
                optIn("androidx.compose.foundation.ExperimentalFoundationApi")
                optIn("androidx.compose.foundation.layout.ExperimentalLayoutApi")
                optIn("androidx.compose.animation.ExperimentalSharedTransitionApi")
                optIn("androidx.compose.material3.ExperimentalMaterial3Api")
                optIn("androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi")
                optIn("androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi")
                optIn("androidx.tv.material3.ExperimentalTvMaterial3Api")
                optIn("com.google.accompanist.permissions.ExperimentalPermissionsApi")

            }
        }
    }
    plugins.withId("org.jetbrains.kotlin.plugin.compose") {
        configure<ComposeCompilerGradlePluginExtension> {
            includeSourceInformation = true
            val file = rootProject.layout.projectDirectory.file("compose_compiler_config.conf")
            if (file.asFile.exists()) {
                stabilityConfigurationFiles.add(file)
            }
            metricsDestination = layout.buildDirectory.dir("compose_metrics")
            reportsDestination = layout.buildDirectory.dir("compose_metrics")
        }
    }
    plugins.withId("com.android.library") {
        configure<LibraryExtension> {
            compileSdk = 35
            defaultConfig {
                minSdk = 26
                testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                consumerProguardFiles("consumer-rules.pro")
            }
            buildTypes {
                release {
                    isMinifyEnabled = false
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro"
                    )
                }
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }
}
