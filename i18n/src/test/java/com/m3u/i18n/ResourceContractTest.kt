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

    @Test
    fun `theme preview copy is localized and presentation neutral`() {
        val resourceRoot = resourceRoot()
        val directories = listOf(resourceRoot.resolve("values")) + localeDirectories(resourceRoot)
        val failures = buildList {
            directories.forEach { directory ->
                val entries = readEntries(directory)
                THEME_PREVIEW_KEYS.forEach { key ->
                    val text = entries[key]?.text
                    when {
                        text == null -> add("${directory.name}: missing $key")
                        text.isBlank() -> add("${directory.name}: $key is blank")
                        "**" in text -> add("${directory.name}: $key contains Markdown markup")
                        text.trim().lowercase() in THEME_PREVIEW_PLACEHOLDERS ->
                            add("${directory.name}: $key still contains placeholder copy")
                    }
                }
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString(separator = "\n"))
    }

    @Test
    fun `provider added feedback is count independent`() {
        val resourceRoot = resourceRoot()
        val defaults = readEntries(resourceRoot.resolve("values"))
        assertTrue(
            PROVIDER_ADDED_KEY in defaults,
            "$PROVIDER_ADDED_KEY must have a default resource",
        )
        val directories = listOf(resourceRoot.resolve("values")) + localeDirectories(resourceRoot)
        val failures = buildList {
            directories.forEach { directory ->
                val entry = readEntries(directory)[PROVIDER_ADDED_KEY] ?: return@forEach
                if (entry.formatSignature.isNotEmpty()) {
                    add(
                        "${directory.name}: $PROVIDER_ADDED_KEY must not depend on a count argument"
                    )
                }
            }
        }

        assertTrue(failures.isEmpty(), failures.joinToString(separator = "\n"))
    }

    @Test
    fun `extension and provider resources exist in every supported locale`() {
        val resourceRoot = resourceRoot()
        val requiredKeys = readEntries(resourceRoot.resolve("values"))
            .keys
            .filterTo(sortedSetOf()) { key ->
                key.startsWith(PROVIDER_KEY_PREFIX) ||
                    key.startsWith(EXTENSION_KEY_PREFIX) ||
                    key in EXTENSION_PROVIDER_UI_KEYS
            }
        val failures = buildList {
            localeDirectories(resourceRoot).forEach { directory ->
                val localized = readEntries(directory)
                val missing = requiredKeys - localized.keys
                if (missing.isNotEmpty()) {
                    add("${directory.name}: missing ${missing.joinToString()}")
                }
                requiredKeys
                    .filter { key -> localized[key]?.text?.isBlank() == true }
                    .forEach { key ->
                        add("${directory.name}: $key is blank")
                    }
            }
        }

        assertTrue(requiredKeys.isNotEmpty(), "No extension or provider resources were found")
        assertTrue(failures.isEmpty(), failures.joinToString(separator = "\n"))
    }

    @Test
    fun `remote control resources exist in every supported locale`() {
        val resourceRoot = resourceRoot()
        val defaults = readEntries(resourceRoot.resolve("values"))
        val requiredKeys = defaults.keys
            .filterTo(sortedSetOf()) { key ->
                key.startsWith(REMOTE_CONTROL_UI_PREFIX) ||
                    key.startsWith(REMOTE_CONTROL_SETTING_PREFIX)
            }
        val failures = buildList {
            localeDirectories(resourceRoot).forEach { directory ->
                val localized = readEntries(directory)
                val missing = requiredKeys - localized.keys
                if (missing.isNotEmpty()) {
                    add("${directory.name}: missing ${missing.joinToString()}")
                }
                requiredKeys.forEach keyLoop@ { key ->
                    val localizedEntry = localized[key] ?: return@keyLoop
                    if (localizedEntry.text.isBlank()) {
                        add("${directory.name}: $key is blank")
                    }
                    val defaultEntry = defaults.getValue(key)
                    if (localizedEntry.formatSignature != defaultEntry.formatSignature) {
                        add(
                            "${directory.name}: $key has ${localizedEntry.formatSignature} " +
                                "but default has ${defaultEntry.formatSignature}"
                        )
                    }
                }
            }
        }

        assertEquals(
            REMOTE_CONTROL_RESOURCE_COUNT,
            requiredKeys.size,
            "The Remote Control resource contract changed; update every supported locale",
        )
        assertTrue(failures.isEmpty(), failures.joinToString(separator = "\n"))
    }

    @Test
    fun `playlist management resources exist in every supported locale`() {
        val resourceRoot = resourceRoot()
        val defaults = readEntries(resourceRoot.resolve("values"))
        val requiredStrings = defaults.keys
            .filterTo(sortedSetOf()) { key ->
                key.startsWith("string/$PLAYLIST_KEY_PREFIX") ||
                    key.startsWith("string/$PLAYLIST_CONFIGURATION_KEY_PREFIX")
            }
            .apply { addAll(PLAYLIST_CROSS_PREFIX_KEYS) }
        val requiredPlurals = defaults.keys
            .asSequence()
            .filter { it.startsWith("plurals/$PLAYLIST_KEY_PREFIX") }
            .map { it.substringBeforeLast("/") }
            .toCollection(sortedSetOf())
        val failures = buildList {
            localeDirectories(resourceRoot).forEach { directory ->
                val localized = readEntries(directory)
                val missingStrings = requiredStrings - localized.keys
                if (missingStrings.isNotEmpty()) {
                    add("${directory.name}: missing ${missingStrings.joinToString()}")
                }

                val localizedPlurals = localized.keys
                    .asSequence()
                    .filter { it.startsWith("plurals/$PLAYLIST_KEY_PREFIX") }
                    .map { it.substringBeforeLast("/") }
                    .toSet()
                val missingPlurals = requiredPlurals - localizedPlurals
                if (missingPlurals.isNotEmpty()) {
                    add("${directory.name}: missing ${missingPlurals.joinToString()}")
                }

                requiredStrings
                    .filter { key -> localized[key]?.text?.isBlank() == true }
                    .forEach { key ->
                        add("${directory.name}: $key is blank")
                    }
                requiredStrings.forEach keyLoop@ { key ->
                    val localizedEntry = localized[key] ?: return@keyLoop
                    val defaultEntry = defaults.getValue(key)
                    if (localizedEntry.formatSignature != defaultEntry.formatSignature) {
                        add(
                            "${directory.name}: $key has ${localizedEntry.formatSignature} " +
                                "but default has ${defaultEntry.formatSignature}"
                        )
                    }
                }

                localized
                    .filterKeys { key -> key.startsWith("plurals/$PLAYLIST_KEY_PREFIX") }
                    .forEach { (key, entry) ->
                        if (entry.text.isBlank()) {
                            add("${directory.name}: $key is blank")
                        }
                        val defaultEntry = defaults[key]
                            ?: defaults["${key.substringBeforeLast("/")}/other"]
                            ?: error("No default plural entry for $key")
                        if (entry.formatSignature != defaultEntry.formatSignature) {
                            add(
                                "${directory.name}: $key has ${entry.formatSignature} " +
                                    "but default has ${defaultEntry.formatSignature}"
                            )
                        }
                    }
            }
        }

        assertTrue(requiredStrings.isNotEmpty(), "No playlist management strings were found")
        assertTrue(requiredPlurals.isNotEmpty(), "No playlist management plurals were found")
        assertTrue(failures.isEmpty(), failures.joinToString(separator = "\n"))
    }

    @Test
    fun `playlist backup filename templates are file safe`() {
        val resourceRoot = resourceRoot()
        val directories = listOf(resourceRoot.resolve("values")) + localeDirectories(resourceRoot)
        val failures = buildList {
            directories.forEach directoryLoop@ { directory ->
                val template = readEntries(directory)[PLAYLIST_BACKUP_FILENAME_KEY]?.text
                if (template == null) {
                    add("${directory.name}: missing $PLAYLIST_BACKUP_FILENAME_KEY")
                    return@directoryLoop
                }
                val token = template
                    .removePrefix(PLAYLIST_BACKUP_FILENAME_PREFIX)
                    .removeSuffix(PLAYLIST_BACKUP_FILENAME_SUFFIX)
                val isSafe = template.startsWith(PLAYLIST_BACKUP_FILENAME_PREFIX) &&
                    template.endsWith(PLAYLIST_BACKUP_FILENAME_SUFFIX) &&
                    token.isNotEmpty() &&
                    token.all { character ->
                        character.isLetterOrDigit() || character == '_' || character == '-'
                    }
                if (!isSafe) {
                    add(
                        "${directory.name}: $PLAYLIST_BACKUP_FILENAME_KEY is not a safe " +
                            "filename template"
                    )
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
        const val PROVIDER_ADDED_KEY = "string/feat_setting_provider_added"
        const val PROVIDER_KEY_PREFIX = "string/feat_setting_provider_"
        const val EXTENSION_KEY_PREFIX = "string/feat_setting_extension_"
        const val REMOTE_CONTROL_UI_PREFIX = "string/ui_remote_control_"
        const val REMOTE_CONTROL_SETTING_PREFIX = "string/feat_setting_remote_"
        const val REMOTE_CONTROL_RESOURCE_COUNT = 23
        const val PLAYLIST_KEY_PREFIX = "feat_setting_playlist_"
        const val PLAYLIST_CONFIGURATION_KEY_PREFIX = "feat_playlist_configuration_"
        const val PLAYLIST_BACKUP_FILENAME_KEY =
            "string/feat_setting_playlist_backup_filename"
        const val PLAYLIST_BACKUP_FILENAME_PREFIX = "M3UAndroid_"
        const val PLAYLIST_BACKUP_FILENAME_SUFFIX = "_%1\$d.txt"
        val PLAYLIST_CROSS_PREFIX_KEYS = setOf(
            "string/feat_setting_playlist_management",
            "string/feat_setting_label_add_playlist",
            "string/feat_setting_label_epg_playlists",
            "string/feat_setting_label_hidden_channels",
            "string/feat_setting_label_hidden_playlist_groups",
            "string/feat_setting_label_subscribe",
            "string/feat_setting_label_subscribing",
            "string/feat_setting_label_backup",
            "string/feat_setting_label_restore",
            "string/feat_setting_placeholder_title",
            "string/feat_setting_placeholder_url",
            "string/feat_setting_placeholder_epg_title",
            "string/feat_setting_placeholder_epg",
            "string/feat_setting_placeholder_basic_url",
            "string/feat_setting_placeholder_username",
            "string/feat_setting_placeholder_password",
            "string/feat_setting_local_storage",
            "string/feat_setting_label_select_from_local_storage",
            "string/feat_setting_subscribe_for_tv",
            "string/feat_setting_label_parse_from_clipboard",
            "string/feat_setting_warning_xtream_takes_much_more_time",
            "string/ui_action_delete_epg",
            "string/ui_state_loading",
            "string/data_worker_backup_notification_title",
            "string/data_worker_backup_notification_channel_name",
            "string/data_worker_backup_notification_channel_description",
            "string/data_worker_restore_notification_title",
            "string/data_worker_restore_notification_channel_name",
            "string/data_worker_restore_notification_channel_description",
        )
        val EXTENSION_PROVIDER_UI_KEYS = setOf(
            "string/feat_setting_external_extensions",
            "string/feat_setting_external_extensions_description",
            "string/feat_setting_data_source_provider",
            "string/feat_setting_data_source_selector_description",
            "string/feat_setting_data_source_selector_with_identifier_description",
            "string/feat_setting_label_subscribe",
            "string/feat_setting_label_subscribing",
            "string/tv_extensions_subtitle",
            "string/tv_extensions_enable_developer_mode",
            "string/tv_extensions_disable_developer_mode",
            "string/ui_state_loading",
        )
        val THEME_PREVIEW_KEYS = setOf(
            "string/ui_theme_card_left",
            "string/ui_theme_card_right",
        )
        val THEME_PREVIEW_PLACEHOLDERS = setOf("ho", "la", "stanga", "dreapta")
        val FORMAT_ARGUMENT =
            Regex("%(?:(\\d+)\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?([a-zA-Z%])")
        val BIDI_CONTROL = Regex("[\\u061C\\u200E\\u200F\\u202A-\\u202E\\u2066-\\u2069]")
        val LOCALE_DIRECTORY = Regex("values-[a-z]{2,3}(?:-r[A-Z]{2})?")
    }
}
