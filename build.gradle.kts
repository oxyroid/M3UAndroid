import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.com.android.application) apply false
    alias(libs.plugins.com.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.com.google.dagger.hilt.android) apply false
    alias(libs.plugins.com.google.devtools.ksp) apply false
    alias(libs.plugins.com.android.test) apply false
    alias(libs.plugins.org.jetbrains.kotlin.serialization) apply false
    alias(libs.plugins.org.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.org.jetbrains.kotlin.multiplatform) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.com.squareup.wire) apply false
}
val kotlinMetadataVersion = extensions
    .getByType<VersionCatalogsExtension>()
    .named("libs")
    .findVersion("kotlin")
    .orElseThrow()
    .requiredVersion

subprojects {
    val projectWideKotlinOptIns = listOf(
        "kotlinx.coroutines.ExperimentalCoroutinesApi",
        "androidx.compose.ui.ExperimentalComposeUiApi",
        "androidx.compose.foundation.ExperimentalFoundationApi",
        "androidx.compose.foundation.layout.ExperimentalLayoutApi",
        "androidx.compose.animation.ExperimentalSharedTransitionApi",
        "androidx.compose.material3.ExperimentalMaterial3Api",
        "androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi",
        "androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi",
        "androidx.tv.material3.ExperimentalTvMaterial3Api",
        "com.google.accompanist.permissions.ExperimentalPermissionsApi",
    )
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    configurations
        .matching { it.name.startsWith("hiltAnnotationProcessor") }
        .configureEach {
            resolutionStrategy.force(
                "org.jetbrains.kotlin:kotlin-metadata-jvm:$kotlinMetadataVersion"
            )
        }
    fun configureKotlinOptIns() {
        kotlinExtension.sourceSets.configureEach {
            languageSettings {
                projectWideKotlinOptIns.forEach(::optIn)
            }
        }
    }
    fun configureBuiltInAndroidKotlin() {
        extensions.configure<KotlinAndroidProjectExtension> {
            compilerOptions {
                jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                optIn.addAll(projectWideKotlinOptIns)
            }
        }
    }
    plugins.withId("com.android.application") {
        configureBuiltInAndroidKotlin()
    }
    plugins.withId("com.android.library") {
        configureBuiltInAndroidKotlin()
    }
    plugins.withId("com.android.test") {
        configureBuiltInAndroidKotlin()
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        configureKotlinOptIns()
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
            compileSdk = 37
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
