import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@CacheableTask
abstract class CopyPublishedApks : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Internal
    abstract val builtArtifactsLoader: Property<BuiltArtifactsLoader>

    @get:Input
    abstract val fileNamePrefix: Property<String>

    @get:Input
    abstract val includeAbiSuffix: Property<Boolean>

    @TaskAction
    fun copyApks() {
        val input = inputDirectory.get()
        val builtArtifacts = builtArtifactsLoader.get().load(input)
            ?: error("Cannot load APK metadata from ${input.asFile}")
        check(builtArtifacts.elements.isNotEmpty()) {
            "No APK outputs were produced in ${input.asFile}"
        }
        val output = outputDirectory.get().asFile
        check(!output.exists() || output.deleteRecursively()) {
            "Cannot clear previously published APKs from $output"
        }
        Files.createDirectories(output.toPath())

        val publishedNames = mutableSetOf<String>()
        builtArtifacts.elements.forEach { artifact ->
            val versionName = artifact.versionName
                ?.takeIf(String::isNotBlank)
                ?: error("APK ${artifact.outputFile} does not declare a version name")
            val abi = artifact.filters
                .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                ?.identifier
            val abiSuffix = if (includeAbiSuffix.get() && abi != null) "_$abi" else ""
            val publishedName = "${fileNamePrefix.get()}$versionName$abiSuffix.apk"
            check(publishedNames.add(publishedName)) {
                "Multiple APK outputs map to $publishedName"
            }
            Files.copy(
                File(artifact.outputFile).toPath(),
                output.resolve(publishedName).toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}

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
    val coroutineOptInProjects = setOf(
        ":app:smartphone",
        ":business:channel",
        ":business:favorite",
        ":business:foryou",
        ":business:playlist",
        ":business:playlist-configuration",
        ":business:setting",
        ":core:foundation",
        ":data",
    )
    val projectWideKotlinOptIns = buildList {
        if (path in coroutineOptInProjects) {
            add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
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
                jvmTarget.set(JvmTarget.JVM_17)
                optIn.addAll(projectWideKotlinOptIns)
            }
        }
    }
    plugins.withId("com.android.application") {
        configureBuiltInAndroidKotlin()
        val publishedApkPrefix = when (path) {
            ":app:smartphone" -> ""
            ":app:tv" -> "tv-"
            else -> null
        }
        if (publishedApkPrefix != null) {
            val publishedApkIncludesAbi = path == ":app:smartphone"
            val androidComponents =
                extensions.getByType<ApplicationAndroidComponentsExtension>()
            androidComponents.onVariants(
                androidComponents.selector().withBuildType("release")
            ) { variant ->
                val variantName = variant.name.replaceFirstChar { character ->
                    if (character.isLowerCase()) character.titlecase() else character.toString()
                }
                val copyTask = tasks.register<CopyPublishedApks>(
                    "copy${variantName}PublishedApks"
                ) {
                    outputDirectory.set(
                        layout.buildDirectory.dir("outputs/published-apk/${variant.name}")
                    )
                    builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
                    fileNamePrefix.set(publishedApkPrefix)
                    includeAbiSuffix.set(publishedApkIncludesAbi)
                }
                variant.artifacts
                    .use(copyTask)
                    .wiredWith(CopyPublishedApks::inputDirectory)
                    .toListenTo(SingleArtifact.APK)
            }
        }
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
