import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuiltArtifactsLoader
import com.android.build.api.variant.FilterConfiguration
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.zip.ZipFile

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

@CacheableTask
abstract class VerifyExtensionSdkRepository : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val repositoryDirectory: DirectoryProperty

    @get:Input
    abstract val sdkGroup: Property<String>

    @get:Input
    abstract val sdkVersion: Property<String>

    @get:Input
    abstract val kotlinCompilerVersion: Property<String>

    @get:Input
    abstract val workspaceRoot: Property<String>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sampleBuildFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sampleSettingsFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val samplePropertiesFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sampleVersionCatalogFile: RegularFileProperty

    @TaskAction
    fun verifyRepository() {
        val repository = repositoryDirectory.get().asFile
        val version = sdkVersion.get()
        val groupDirectory = sdkGroup.get()
            .split('.')
            .fold(repository) { directory, segment -> directory.resolve(segment) }
        val expectedArtifacts = mapOf(
            "extension-api" to listOf("pom", "module", "jar", "sources.jar"),
            "extension-api-jvm" to listOf("pom", "module", "jar", "sources.jar"),
            "extension-transport-protocol-android" to
                listOf("pom", "module", "aar", "sources.jar"),
            "extension-sdk-android" to listOf("pom", "module", "aar", "sources.jar"),
            "extension-conformance" to listOf("pom", "module", "jar", "sources.jar"),
        )
        val publishedArtifactIds = groupDirectory.listFiles()
            ?.filter(File::isDirectory)
            ?.map(File::getName)
            ?.toSet()
            .orEmpty()
        check(publishedArtifactIds == expectedArtifacts.keys) {
            "Unexpected extension SDK artifact set: $publishedArtifactIds"
        }
        expectedArtifacts.forEach { (artifactId, suffixes) ->
            val versionDirectory = groupDirectory.resolve(artifactId).resolve(version)
            suffixes.forEach { suffix ->
                val separator = if (suffix == "sources.jar") "-" else "."
                val expected = versionDirectory.resolve(
                    "$artifactId-$version$separator$suffix"
                )
                check(expected.isFile) {
                    "Extension SDK repository is missing ${expected.relativeTo(repository)}"
                }
            }
        }

        val sdkPom = groupDirectory
            .resolve("extension-sdk-android")
            .resolve(version)
            .resolve("extension-sdk-android-$version.pom")
            .readText()
        listOf(
            "extension-api",
            "extension-transport-protocol-android",
        ).forEach { dependency ->
            check("<artifactId>$dependency</artifactId>" in sdkPom) {
                "Extension SDK POM is missing dependency $dependency"
            }
        }

        val forbiddenArtifactIds = listOf(
            "extension-runtime",
            "extension-transport-android",
            "runtime",
            "transport-android",
            "data",
            "smartphone",
            "tv",
        )
        val canonicalWorkspaceRoot = File(workspaceRoot.get()).canonicalFile
        val workspaceReferences = setOf(
            canonicalWorkspaceRoot.path,
            canonicalWorkspaceRoot.invariantSeparatorsPath,
            canonicalWorkspaceRoot.toURI().toString(),
            canonicalWorkspaceRoot.toURI().toASCIIString(),
        )
        groupDirectory.walkTopDown()
            .filter { file ->
                file.isFile && (file.extension == "pom" || file.extension == "module")
            }
            .forEach { metadata ->
                val content = metadata.readText()
                forbiddenArtifactIds.forEach { artifactId ->
                    val pomReference = "<artifactId>$artifactId</artifactId>"
                    val moduleReference = "\"module\": \"$artifactId\""
                    check(pomReference !in content && moduleReference !in content) {
                        "${metadata.relativeTo(repository)} leaks host artifact $artifactId"
                    }
                }
                val normalizedContent = content.replace('\\', '/')
                check(
                    "project :" !in content &&
                        "/Users/" !in content &&
                        "/home/" !in content &&
                        "file:/" !in content &&
                        workspaceReferences.none { reference ->
                            reference in content ||
                                reference.replace('\\', '/') in normalizedContent
                        }
                ) {
                    "${metadata.relativeTo(repository)} contains a workspace reference"
                }
                val pomProjectDependencies = Regex(
                    """<dependency>\s*<groupId>${Regex.escape(sdkGroup.get())}</groupId>""" +
                        """\s*<artifactId>([^<]+)</artifactId>"""
                ).findAll(content).map { match -> match.groupValues[1] }
                val moduleProjectReferences = Regex(
                    """"group": "${Regex.escape(sdkGroup.get())}",\s*""" +
                        """"module": "([^"]+)""""
                ).findAll(content).map { match -> match.groupValues[1] }
                (pomProjectDependencies + moduleProjectReferences).forEach { artifactId ->
                    check(artifactId in expectedArtifacts) {
                        "${metadata.relativeTo(repository)} references unpublished host " +
                            "artifact $artifactId"
                    }
                }
            }

        val conformanceJar = groupDirectory
            .resolve("extension-conformance")
            .resolve(version)
            .resolve("extension-conformance-$version.jar")
        ZipFile(conformanceJar).use { archive ->
            listOf(
                "golden-wire/v1/README.md",
                "golden-wire/v1/README.zh-CN.md",
                "golden-wire/v1/envelopes/invocation-current.json",
                "golden-wire/v1/hooks/background.task.run/schema-2/request.json",
            ).forEach { fixture ->
                check(archive.getEntry(fixture) != null) {
                    "Conformance artifact is missing $fixture"
                }
            }
        }

        val sampleBuild = sampleBuildFile.get().asFile.readText()
        val sampleSettings = sampleSettingsFile.get().asFile.readText()
        check("project(" !in sampleBuild) {
            "Hello extension must consume the published SDK coordinate"
        }
        check("includeBuild" !in sampleSettings) {
            "Hello extension must not substitute SDK projects from the host checkout"
        }
        check("../../gradle" !in sampleSettings) {
            "Hello extension must own its build-tool version catalog"
        }
        val sampleProperties = samplePropertiesFile.get().asFile.readLines()
            .mapNotNull { line ->
                val key = line.substringBefore('=')
                if (key in setOf("extensionSdkGroup", "extensionSdkVersion")) {
                    key to line.substringAfter('=')
                } else {
                    null
                }
            }
            .toMap()
        check(sampleProperties["extensionSdkGroup"] == sdkGroup.get()) {
            "Hello extension SDK group ${sampleProperties["extensionSdkGroup"]} does not match " +
                sdkGroup.get()
        }
        check(sampleProperties["extensionSdkVersion"] == version) {
            "Hello extension SDK version ${sampleProperties["extensionSdkVersion"]} does not " +
                "match $version"
        }
        val sampleVersionCatalog = sampleVersionCatalogFile.get().asFile.readText()
        check("kotlin = \"${kotlinCompilerVersion.get()}\"" in sampleVersionCatalog) {
            "Hello extension must use a compiler compatible with the published Kotlin metadata"
        }
    }
}

@CacheableTask
abstract class VerifyExtensionSdkBundle : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val archiveFile: RegularFileProperty

    @get:Input
    abstract val sdkGroup: Property<String>

    @get:Input
    abstract val sdkVersion: Property<String>

    @get:OutputFile
    abstract val checksumFile: RegularFileProperty

    @TaskAction
    fun verifyBundle() {
        val archive = archiveFile.get().asFile
        val version = sdkVersion.get()
        val groupPath = sdkGroup.get().replace('.', '/')
        val expectedArtifacts = mapOf(
            "extension-api" to listOf("pom", "module", "jar", "sources.jar"),
            "extension-api-jvm" to listOf("pom", "module", "jar", "sources.jar"),
            "extension-transport-protocol-android" to
                listOf("pom", "module", "aar", "sources.jar"),
            "extension-sdk-android" to listOf("pom", "module", "aar", "sources.jar"),
            "extension-conformance" to listOf("pom", "module", "jar", "sources.jar"),
        )
        ZipFile(archive).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            check(entries.size == entries.toSet().size) {
                "Extension SDK bundle contains duplicate entries"
            }
            listOf(
                "LICENSE",
                "README.md",
                "README.zh-CN.md",
                "golden-wire/v1/README.md",
                "golden-wire/v1/README.zh-CN.md",
            ).forEach { path ->
                check(zip.getEntry(path) != null) {
                    "Extension SDK bundle is missing $path"
                }
            }
            expectedArtifacts.forEach { (artifactId, suffixes) ->
                suffixes.forEach { suffix ->
                    val separator = if (suffix == "sources.jar") "-" else "."
                    val path = "repository/$groupPath/$artifactId/$version/" +
                        "$artifactId-$version$separator$suffix"
                    check(zip.getEntry(path) != null) {
                        "Extension SDK bundle is missing $path"
                    }
                }
            }
            check(entries.none { path -> "maven-metadata.xml" in path }) {
                "Extension SDK bundle must not contain timestamped Maven metadata"
            }
            listOf("README.md", "README.zh-CN.md").forEach { path ->
                val readme = zip.getInputStream(zip.getEntry(path))
                    .bufferedReader()
                    .use { it.readText() }
                check(version in readme) {
                    "$path does not identify SDK version $version"
                }
                check(
                    "${sdkGroup.get()}:extension-sdk-android:$version" in readme
                ) {
                    "$path does not contain the SDK dependency coordinate"
                }
                val brokenRelativeLink = Regex("""\[[^]]+]\((?!https?://|#)([^)]+)\)""")
                    .find(readme)
                check(brokenRelativeLink == null) {
                    "$path contains a bundle-relative link that may be broken: " +
                        brokenRelativeLink?.groupValues?.get(1)
                }
            }
        }

        val digest = MessageDigest.getInstance("SHA-256")
        archive.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val checksum = digest.digest().joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
        val checksumOutput = checksumFile.get().asFile
        checksumOutput.parentFile.mkdirs()
        checksumOutput.writeText("$checksum  ${archive.name}\n")
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
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            publications.withType<MavenPublication>().configureEach {
                pom {
                    inceptionYear.set("2026")
                    licenses {
                        license {
                            name.set("GNU General Public License v3.0")
                            url.set("https://www.gnu.org/licenses/gpl-3.0.html")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("oxyroid")
                            name.set("oxyroid")
                            url.set("https://github.com/oxyroid")
                        }
                    }
                    scm {
                        connection.set(
                            "scm:git:https://github.com/oxyroid/M3UAndroid.git"
                        )
                        developerConnection.set(
                            "scm:git:ssh://git@github.com/oxyroid/M3UAndroid.git"
                        )
                        url.set("https://github.com/oxyroid/M3UAndroid")
                    }
                }
            }
        }
        tasks.matching { task ->
            task.name.startsWith("publish") &&
                task.name.endsWith("ToExtensionSdkRepository")
        }.configureEach {
            dependsOn(rootProject.tasks.named("cleanExtensionSdkRepository"))
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

val extensionSdkGroup = providers.gradleProperty("extensionSdkGroup")
val extensionSdkVersion = providers.gradleProperty("extensionSdkVersion")
val extensionSdkRepository = layout.buildDirectory.dir("extension-sdk/repository")
val extensionSdkProjects = listOf(
    ":extension:api",
    ":extension:transport-protocol-android",
    ":extension:sdk-android",
    ":extension:conformance",
)

tasks.register<Delete>("cleanExtensionSdkRepository") {
    group = "distribution"
    description = "Removes the staged extension SDK Maven repository."
    delete(extensionSdkRepository)
}

val publishExtensionSdkRepository = tasks.register("publishExtensionSdkRepository") {
    group = "distribution"
    description = "Publishes extension SDK artifacts to the staged Maven repository."
    dependsOn(
        extensionSdkProjects.map { projectPath ->
            "$projectPath:publishAllPublicationsToExtensionSdkRepository"
        }
    )
}

val verifyExtensionSdkRepository =
    tasks.register<VerifyExtensionSdkRepository>("verifyExtensionSdkRepository") {
        group = "verification"
        description = "Checks extension SDK artifacts, dependencies, and host isolation."
        dependsOn(publishExtensionSdkRepository)
        repositoryDirectory.set(extensionSdkRepository)
        sdkGroup.set(extensionSdkGroup)
        sdkVersion.set(extensionSdkVersion)
        kotlinCompilerVersion.set(kotlinMetadataVersion)
        workspaceRoot.set(
            providers.provider {
                val directory = layout.projectDirectory.asFile
                directory.canonicalFile.absolutePath
            }
        )
        sampleBuildFile.set(
            layout.projectDirectory.file("samples/hello-extension/build.gradle.kts")
        )
        sampleSettingsFile.set(
            layout.projectDirectory.file("samples/hello-extension/settings.gradle.kts")
        )
        samplePropertiesFile.set(
            layout.projectDirectory.file("samples/hello-extension/gradle.properties")
        )
        sampleVersionCatalogFile.set(
            layout.projectDirectory.file(
                "samples/hello-extension/gradle/libs.versions.toml"
            )
        )
    }

val bundleExtensionSdk = tasks.register<Zip>("bundleExtensionSdk") {
    group = "distribution"
    description = "Builds the standalone extension SDK Maven bundle."
    dependsOn(verifyExtensionSdkRepository)
    archiveBaseName.set("m3u-extension-sdk")
    archiveVersion.set(extensionSdkVersion)
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true

    from(extensionSdkRepository) {
        into("repository")
        exclude("**/maven-metadata.xml*")
    }
    from("LICENSE")
    from("extension/api/src/jvmTest/resources/golden-wire") {
        into("golden-wire")
    }
    from(
        listOf(
            "extension/distribution/README.md",
            "extension/distribution/README.zh-CN.md",
        )
    )
}

tasks.register<VerifyExtensionSdkBundle>("verifyExtensionSdkBundle") {
    group = "verification"
    description = "Checks the final SDK ZIP and writes its SHA-256 checksum."
    dependsOn(bundleExtensionSdk)
    archiveFile.set(bundleExtensionSdk.flatMap { task -> task.archiveFile })
    sdkGroup.set(extensionSdkGroup)
    sdkVersion.set(extensionSdkVersion)
    checksumFile.set(
        layout.buildDirectory.file(
            extensionSdkVersion.map { version ->
                "distributions/m3u-extension-sdk-$version.zip.sha256"
            }
        )
    )
}
