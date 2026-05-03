package com.m3u.build

import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.yaml.snakeyaml.Yaml

class NativeLoadPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val config = NativeLoadYaml.parse(project.rootProject.file("native-load.yml"))

        project.plugins.withId("com.android.library") {
            if (config.distribution.runtimeConfigProject == null || config.distribution.runtimeConfigProject == project.path) {
                val android = project.extensions.getByType(LibraryExtension::class.java)
                android.buildFeatures.buildConfig = true

                val androidComponents = project.extensions.getByType(
                    LibraryAndroidComponentsExtension::class.java
                )
                androidComponents.onVariants { variant ->
                    val enabled = variant.buildType == config.distribution.runtimeVariant
                    variant.buildConfigFields.put(
                        "NATIVE_PACK_ID",
                        BuildConfigField("String", config.pack.id.quoted(), null)
                    )
                    variant.buildConfigFields.put(
                        "NATIVE_PACK_MANIFEST_PREFIX",
                        BuildConfigField("String", config.pack.manifestPrefix.quoted(), null)
                    )
                    variant.buildConfigFields.put(
                        "NATIVE_PACK_ENABLED",
                        BuildConfigField("boolean", enabled.toString(), null)
                    )
                    variant.buildConfigFields.put(
                        "NATIVE_PACK_REPOSITORY",
                        BuildConfigField(
                            "String",
                            if (enabled) config.distribution.repository.quoted() else "\"\"",
                            null
                        )
                    )
                    variant.buildConfigFields.put(
                        "NATIVE_PACK_REF",
                        BuildConfigField(
                            "String",
                            if (enabled) config.distribution.ref.quoted() else "\"\"",
                            null
                        )
                    )
                    variant.buildConfigFields.put(
                        "NATIVE_PACK_SNAPSHOT_PATH",
                        BuildConfigField(
                            "String",
                            if (enabled) config.snapshotPath().quoted() else "\"\"",
                            null
                        )
                    )
                }
            }
        }

        project.plugins.withId("com.android.application") {
            val androidComponents = project.extensions.getByType(
                ApplicationAndroidComponentsExtension::class.java
            )
            androidComponents.onVariants(
                androidComponents.selector().withBuildType(config.distribution.runtimeVariant)
            ) { variant ->
                variant.packaging.jniLibs.excludes.addAll(
                    config.pack.libraries.map { library -> "**/lib$library.so" }
                )
                variant.instrumentation.transformClassesWith(
                    NativeLoadClassVisitorFactory::class.java,
                    InstrumentationScope.ALL
                ) { parameters ->
                    parameters.instrumentedPackages.set(config.instrumentation.packages)
                    parameters.redirectOwner.set(config.instrumentation.redirect.owner)
                    parameters.redirectMethod.set(config.instrumentation.redirect.method)
                }
                variant.instrumentation.setAsmFramesComputationMode(
                    FramesComputationMode.COPY_FRAMES
                )

                val snapshotPath = config.snapshotPath()
                if (config.distribution.producerProject == null || config.distribution.producerProject == project.path) {
                    val taskName = "generate${variant.name.capitalized()}NativePacks"
                    project.tasks.register(
                        taskName,
                        GenerateNativePacksTask::class.java,
                        object : Action<GenerateNativePacksTask> {
                            override fun execute(task: GenerateNativePacksTask) {
                                task.group = "native-load"
                                task.description = "Generates external native library packs for ${variant.name}."
                                task.packId.set(config.pack.id)
                                task.artifacts.set(config.pack.artifacts)
                                task.libraries.set(config.pack.libraries)
                                task.loadOrder.set(config.pack.loadOrder)
                                task.assetPrefix.set(config.pack.assetPrefix)
                                task.manifestPrefix.set(config.pack.manifestPrefix)
                                task.outputDirectory.set(project.rootProject.layout.projectDirectory.dir(snapshotPath))
                            }
                        }
                    )
                }
            }
        }
    }
}

data class NativeLoadConfig(
    val instrumentation: InstrumentationConfig,
    val distribution: DistributionConfig,
    val pack: NativePackConfig
)

data class DistributionConfig(
    val repository: String,
    val ref: String,
    val snapshotDirectory: String,
    val runtimeVariant: String,
    val producerProject: String?,
    val runtimeConfigProject: String?
)

data class InstrumentationConfig(
    val packages: List<String>,
    val redirect: RedirectConfig
)

data class RedirectConfig(
    val owner: String,
    val method: String
)

data class NativePackConfig(
    val id: String,
    val assetPrefix: String,
    val manifestPrefix: String,
    val artifacts: List<String>,
    val libraries: List<String>,
    val loadOrder: List<String>
)

object NativeLoadYaml {
    fun parse(file: File): NativeLoadConfig {
        require(file.isFile) { "Native load config not found: ${file.absolutePath}" }
        val document = yamlMap(Yaml().load(file.readText()), "root")
        val schemaVersion = document.int("schemaVersion")
        require(schemaVersion == 1) { "Unsupported native-load.yml schemaVersion: $schemaVersion" }

        val instrumentation = document.map("instrumentation")
        val redirect = instrumentation.map("redirect")
        val distribution = document.map("distribution")
        val pack = document.map("pack")
        val libraries = pack.stringList("libraries")
        val loadOrder = pack.optionalStringList("loadOrder") ?: libraries
        require(loadOrder.all { library -> library in libraries }) {
            "Every loadOrder item must also be declared in pack.libraries."
        }
        return NativeLoadConfig(
            instrumentation = InstrumentationConfig(
                packages = instrumentation.stringList("packages"),
                redirect = RedirectConfig(
                    owner = redirect.string("owner"),
                    method = redirect.string("method")
                )
            ),
            distribution = DistributionConfig(
                repository = distribution.string("repository"),
                ref = distribution.string("ref"),
                snapshotDirectory = distribution.optionalString("snapshotDirectory") ?: "native-packs",
                runtimeVariant = distribution.optionalString("runtimeVariant") ?: "release",
                producerProject = distribution.optionalString("producerProject"),
                runtimeConfigProject = distribution.optionalString("runtimeConfigProject")
            ),
            pack = NativePackConfig(
                id = pack.string("id"),
                assetPrefix = pack.optionalString("assetPrefix") ?: "native-pack",
                manifestPrefix = pack.optionalString("manifestPrefix") ?: "native-pack",
                artifacts = pack.stringList("artifacts"),
                libraries = libraries,
                loadOrder = loadOrder
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun yamlMap(value: Any?, label: String): Map<String, Any?> {
        return value as? Map<String, Any?> ?: error("Expected YAML object at $label.")
    }

    private fun Map<String, Any?>.map(key: String): Map<String, Any?> {
        return yamlMap(this[key], key)
    }

    private fun Map<String, Any?>.string(key: String): String {
        return this[key] as? String ?: error("Expected string property '$key'.")
    }

    private fun Map<String, Any?>.optionalString(key: String): String? {
        return this[key] as? String
    }

    private fun Map<String, Any?>.int(key: String): Int {
        return when (val value = this[key]) {
            is Int -> value
            is Number -> value.toInt()
            else -> error("Expected integer property '$key'.")
        }
    }

    private fun Map<String, Any?>.stringList(key: String): List<String> {
        return optionalStringList(key) ?: error("Expected string list property '$key'.")
    }

    private fun Map<String, Any?>.optionalStringList(key: String): List<String>? {
        val value = this[key] ?: return null
        return (value as? List<*>)?.map { item ->
            item as? String ?: error("Expected every item in '$key' to be a string.")
        }
    }
}

private fun String.capitalized(): String {
    return replaceFirstChar { char -> char.uppercaseChar() }
}

private fun String.quoted(): String {
    return "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""
}

private fun NativeLoadConfig.snapshotPath(): String {
    return "${distribution.snapshotDirectory}/${pack.id}"
}

abstract class GenerateNativePacksTask : DefaultTask() {
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Input
    abstract val packId: Property<String>

    @get:Input
    abstract val artifacts: ListProperty<String>

    @get:Input
    abstract val libraries: ListProperty<String>

    @get:Input
    abstract val loadOrder: ListProperty<String>

    @get:Input
    abstract val assetPrefix: Property<String>

    @get:Input
    abstract val manifestPrefix: Property<String>

    @TaskAction
    fun generate() {
        val configuration = project.configurations.detachedConfiguration(
            *artifacts.get().map { notation ->
                project.dependencies.create(notation)
            }.toTypedArray()
        ).apply {
            isTransitive = false
        }
        val artifacts = configuration.resolvedConfiguration.resolvedArtifacts
            .filter { artifact -> artifact.extension == "aar" }
            .sortedBy { artifact -> artifact.name }

        require(artifacts.size == this.artifacts.get().size) {
            "Expected AAR artifacts ${this.artifacts.get().joinToString()}, got ${artifacts.joinToString { it.name }}."
        }

        val packId = packId.get()
        val outputRoot = outputDirectory.get().asFile.apply {
            deleteRecursively()
            mkdirs()
        }
        val outputPath = outputRoot.relativeTo(project.rootProject.projectDir).invariantSeparatorsPath
        val stagingRoot = File(temporaryDir, packId).apply {
            deleteRecursively()
            mkdirs()
        }

        artifacts.forEach { artifact -> extractNativeLibraries(artifact.file, stagingRoot) }

        val abiDirectories = stagingRoot.listFiles { file -> file.isDirectory }.orEmpty().sortedBy { it.name }
        require(abiDirectories.isNotEmpty()) { "No native libraries found in configured AAR artifacts." }

        val configuredLibraries = libraries.get()
        val assets = abiDirectories.map { abiDirectory ->
            val assetName = "${assetPrefix.get()}-$packId-${abiDirectory.name}.zip"
            val zipFile = File(outputRoot, assetName)
            val libraryFiles = configuredLibraries.map { library -> File(abiDirectory, "lib$library.so") }
            val missingLibraries = libraryFiles.filterNot { file -> file.isFile }.map { file -> file.name }
            require(missingLibraries.isEmpty()) {
                "Missing native libraries for ${abiDirectory.name}: ${missingLibraries.joinToString()}"
            }
            createZip(libraryFiles, zipFile)
            NativePackAsset(
                abi = abiDirectory.name,
                path = "$outputPath/$assetName",
                fileName = assetName,
                size = zipFile.length(),
                md5 = md5(zipFile),
                libraries = libraryFiles
                    .sortedBy { file -> file.name }
                    .map { file -> NativePackLibrary(file.name, file.length(), md5(file)) }
            )
        }

        val manifestFile = File(outputRoot, "${manifestPrefix.get()}-$packId.json")
        manifestFile.writeText(renderManifest(packId, artifacts, assets))
    }

    private fun extractNativeLibraries(aarFile: File, outputRoot: File) {
        ZipFile(aarFile).use { zip ->
            zip.entries().asSequence()
                .filter { entry -> !entry.isDirectory && entry.name.startsWith("jni/") && entry.name.endsWith(".so") }
                .forEach { entry ->
                    val parts = entry.name.split('/')
                    if (parts.size != 3) return@forEach
                    val output = File(outputRoot, "${parts[1]}/${parts[2]}")
                    output.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input -> output.outputStream().use { outputStream -> input.copyTo(outputStream) } }
                }
        }
    }

    private fun createZip(files: List<File>, output: File) {
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            files.sortedBy { file -> file.name }.forEach { file ->
                val entry = ZipEntry(file.name).apply { time = 0L }
                zip.putNextEntry(entry)
                file.inputStream().use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    private fun renderManifest(
        packId: String,
        artifacts: List<ResolvedArtifact>,
        assets: List<NativePackAsset>
    ): String {
        val artifactJson = artifacts.joinToString(",\n") { artifact ->
            val id = artifact.moduleVersion.id
            """    { "group": "${id.group}", "name": "${artifact.name}", "version": "${id.version}" }"""
        }
        val assetJson = assets.joinToString(",\n") { asset ->
            val libraryJson = asset.libraries.joinToString(",\n") { library ->
                """        { "name": "${library.name}", "size": ${library.size}, "md5": "${library.md5}" }"""
            }
            """    "${asset.abi}": {
      "path": "${asset.path}",
      "fileName": "${asset.fileName}",
      "size": ${asset.size},
      "md5": "${asset.md5}",
      "libraries": [
$libraryJson
      ]
    }"""
        }
        return buildString {
            appendLine("{")
            appendLine("  \"schemaVersion\": 1,")
            appendLine("  \"packId\": \"$packId\",")
            appendLine("  \"loadOrder\": [${loadOrder.get().joinToString(", ") { library -> "\"$library\"" }}],")
            appendLine("  \"artifacts\": [")
            appendLine(artifactJson)
            appendLine("  ],")
            appendLine("  \"assets\": {")
            appendLine(assetJson)
            appendLine("  }")
            appendLine("}")
        }
    }

    private fun md5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private data class NativePackAsset(
        val abi: String,
        val path: String,
        val fileName: String,
        val size: Long,
        val md5: String,
        val libraries: List<NativePackLibrary>
    )

    private data class NativePackLibrary(
        val name: String,
        val size: Long,
        val md5: String
    )
}

interface NativeLoadParameters : InstrumentationParameters {
    @get:Input
    val instrumentedPackages: SetProperty<String>

    @get:Input
    val redirectOwner: Property<String>

    @get:Input
    val redirectMethod: Property<String>
}

abstract class NativeLoadClassVisitorFactory : AsmClassVisitorFactory<NativeLoadParameters> {
    override fun createClassVisitor(
        classContext: ClassContext,
        nextClassVisitor: ClassVisitor
    ): ClassVisitor {
        return NativeLoadClassVisitor(
            nextClassVisitor = nextClassVisitor,
            redirectOwner = parameters.get().redirectOwner.get(),
            redirectMethod = parameters.get().redirectMethod.get()
        )
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return parameters.get().instrumentedPackages.get().any { prefix ->
            classData.className.startsWith(prefix)
        }
    }
}

private class NativeLoadClassVisitor(
    nextClassVisitor: ClassVisitor,
    private val redirectOwner: String,
    private val redirectMethod: String
) :
    ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
    override fun visitMethod(
        access: Int,
        name: String,
        descriptor: String,
        signature: String?,
        exceptions: Array<out String>?
    ): MethodVisitor {
        val visitor = super.visitMethod(access, name, descriptor, signature, exceptions)
        return NativeLoadMethodVisitor(visitor, redirectOwner, redirectMethod)
    }
}

private class NativeLoadMethodVisitor(
    methodVisitor: MethodVisitor,
    private val redirectOwner: String,
    private val redirectMethod: String
) :
    MethodVisitor(Opcodes.ASM9, methodVisitor) {
    override fun visitMethodInsn(
        opcode: Int,
        owner: String,
        name: String,
        descriptor: String,
        isInterface: Boolean
    ) {
        if (
            opcode == Opcodes.INVOKESTATIC &&
            owner == "java/lang/System" &&
            name == "loadLibrary" &&
            descriptor == "(Ljava/lang/String;)V"
        ) {
            super.visitMethodInsn(
                Opcodes.INVOKESTATIC,
                redirectOwner,
                redirectMethod,
                "(Ljava/lang/String;)V",
                false
            )
            return
        }
        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface)
    }
}