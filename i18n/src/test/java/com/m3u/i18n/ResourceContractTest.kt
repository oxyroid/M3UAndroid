package com.m3u.i18n

import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.w3c.dom.Element

class ResourceContractTest {
    @Test
    fun `localized strings have defaults and compatible format arguments`() {
        val resourceRoot = resourceRoot()
        val defaults = readEntries(resourceRoot.resolve("values"))
        val failures = mutableListOf<String>()

        localeDirectories(resourceRoot).forEach { localeDirectory ->
            readEntries(localeDirectory).forEach { (key, localized) ->
                val default = defaults[key]
                if (default == null) {
                    failures += "${localeDirectory.name}: $key has no default resource"
                } else if (default.formatSignature != localized.formatSignature) {
                    failures += buildString {
                        append("${localeDirectory.name}: $key has ")
                        append(localized.formatSignature)
                        append(" but default has ")
                        append(default.formatSignature)
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString(separator = "\n"))
    }

    @Test
    fun `non-translatable defaults are not redefined by locales`() {
        val resourceRoot = resourceRoot()
        val nonTranslatableKeys = readEntries(resourceRoot.resolve("values"))
            .filterValues { !it.translatable }
            .keys
        val failures = buildList {
            localeDirectories(resourceRoot).forEach { localeDirectory ->
                readEntries(localeDirectory).keys
                    .filter { it in nonTranslatableKeys }
                    .forEach { key ->
                        add("${localeDirectory.name}: $key overrides a non-translatable default")
                    }
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString(separator = "\n"))
    }

    @Test
    fun `every plural resource defines an other quantity`() {
        val resourceRoot = resourceRoot()
        val failures = buildList {
            (listOf(resourceRoot.resolve("values")) + localeDirectories(resourceRoot))
                .forEach { directory ->
                    val keys = readEntries(directory).keys
                    val pluralNames = keys
                        .filter { it.startsWith("plurals/") }
                        .map { it.substringAfter("plurals/").substringBefore("/") }
                        .toSet()
                    pluralNames.forEach { name ->
                        if ("plurals/$name/other" !in keys) {
                            add("${directory.name}: plurals/$name has no other quantity")
                        }
                    }
                }
        }

        assertTrue(failures.isEmpty(), failures.joinToString(separator = "\n"))
    }

    @Test
    fun `locale config declares every translated resource directory`() {
        val resourceRoot = resourceRoot()
        val expected = buildSet {
            add("en")
            localeDirectories(resourceRoot).forEach { directory ->
                add(directory.name.removePrefix("values-").replace("-r", "-"))
            }
        }
        val localeConfig = parse(resourceRoot.resolve("xml/locales_config.xml"))
        val actual = localeConfig.getElementsByTagName("locale")
            .let { nodes ->
                buildSet {
                    repeat(nodes.length) { index ->
                        val locale = nodes.item(index) as Element
                        add(locale.getAttributeNS(ANDROID_NAMESPACE, "name"))
                    }
                }
            }

        assertEquals(expected, actual)
    }

    @Test
    fun `translations do not contain bidi control characters`() {
        val resourceRoot = resourceRoot()
        val failures = mutableListOf<String>()
        val directories = listOf(resourceRoot.resolve("values")) + localeDirectories(resourceRoot)

        directories.forEach { directory ->
            readEntries(directory).forEach { (key, entry) ->
                if (BIDI_CONTROL.containsMatchIn(entry.text)) {
                    failures += "${directory.name}: $key contains a bidi control character"
                }
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString(separator = "\n"))
    }

    private fun localeDirectories(resourceRoot: Path): List<Path> {
        return Files.list(resourceRoot).use { directories ->
            directories
                .filter { it.isDirectory() && LOCALE_DIRECTORY.matches(it.name) }
                .sorted()
                .toList()
        }
    }

    private fun readEntries(directory: Path): Map<String, ResourceEntry> {
        val result = linkedMapOf<String, ResourceEntry>()
        Files.list(directory).use { files ->
            files
                .filter { it.name.endsWith(".xml") }
                .sorted()
                .forEach { file ->
                    val document = parse(file)
                    val resources = document.documentElement.childNodes
                    repeat(resources.length) { index ->
                        val element = resources.item(index) as? Element ?: return@repeat
                        val name = element.getAttribute("name")
                        if (name.isEmpty()) return@repeat
                        val translatable = element.getAttribute("translatable") != "false"

                        when (element.tagName) {
                            "string" -> {
                                val key = "string/$name"
                                check(
                                    result.put(
                                        key,
                                        ResourceEntry(
                                            text = element.textContent,
                                            translatable = translatable,
                                        ),
                                    ) == null,
                                ) {
                                    "Duplicate resource $key in $directory"
                                }
                            }

                            "plurals" -> {
                                val items = element.getElementsByTagName("item")
                                repeat(items.length) { itemIndex ->
                                    val item = items.item(itemIndex) as Element
                                    val quantity = item.getAttribute("quantity")
                                    val key = "plurals/$name/$quantity"
                                    check(
                                        result.put(
                                            key,
                                            ResourceEntry(
                                                text = item.textContent,
                                                translatable = translatable,
                                            ),
                                        ) == null,
                                    ) {
                                        "Duplicate resource $key in $directory"
                                    }
                                }
                            }
                        }
                    }
                }
        }
        return result
    }

    private fun parse(path: Path) = DocumentBuilderFactory.newInstance()
        .apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
            setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
        }
        .newDocumentBuilder()
        .parse(path.toFile())

    private fun resourceRoot(): Path = sequenceOf(
        Path.of("src/main/res"),
        Path.of("i18n/src/main/res"),
    ).firstOrNull(Path::isDirectory)
        ?: error("Could not locate i18n/src/main/res")

    private data class ResourceEntry(
        val text: String,
        val translatable: Boolean,
    ) {
        val formatSignature: List<Pair<Int, Char>> = buildList {
            var implicitIndex = 1
            FORMAT_ARGUMENT.findAll(text).forEach { match ->
                val type = match.groupValues[2].single().lowercaseChar()
                if (type == '%') return@forEach
                val explicitIndex = match.groupValues[1].toIntOrNull()
                add((explicitIndex ?: implicitIndex) to type)
                if (explicitIndex == null) implicitIndex += 1
            }
        }.sortedWith(compareBy<Pair<Int, Char>> { it.first }.thenBy { it.second })
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        val FORMAT_ARGUMENT =
            Regex("%(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])")
        val BIDI_CONTROL = Regex("[\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
        val LOCALE_DIRECTORY = Regex("values-[a-z]{2,3}(?:-r[A-Z]{2})?")
    }
}
