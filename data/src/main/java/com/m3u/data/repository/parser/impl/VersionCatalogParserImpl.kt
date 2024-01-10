package com.m3u.data.repository.parser.impl

import com.m3u.core.architecture.logger.Logger
import com.m3u.data.repository.parser.VersionCatalogParser
import java.io.InputStream
import javax.inject.Inject

class VersionCatalogParserImpl @Inject constructor(
    private val logger: Logger
) : VersionCatalogParser {
    override suspend fun execute(input: InputStream): List<VersionCatalogParser.Entity> {
        var parser: LineParser<*>? = null
        val result = mutableListOf<VersionCatalogParser.Entity>()
        input.reader()
            .readLines()
            .onEach { line ->
                when {
                    line.isEmpty() -> {}
                    line.startsWith("[versions]") -> parser = LineParser.VERSION
                    line.startsWith("[libraries]") -> parser = LineParser.LIBRARY
                    line.startsWith("[plugins]") -> parser = LineParser.PLUGIN
                    line.startsWith("[bundles]") -> parser = null
                    else -> {
                        try {
                            if (!line.startsWith("#")) {
                                parser?.parse(line)?.let { result += it }
                            }
                        } catch (e: Exception) {
                            logger.log("cannot decode line: $line")
                            logger.log(e)
                        }
                    }
                }
            }
        return result
    }
}

private interface LineParser<E : VersionCatalogParser.Entity> {
    fun parse(line: String): E

    object VERSION : LineParser<VersionCatalogParser.Entity.Version> {
        override fun parse(line: String): VersionCatalogParser.Entity.Version {
            val (key, value) = line.split("=")
            return VersionCatalogParser.Entity.Version(key.trim(), value.trim().drop(1).dropLast(1))
        }
    }

    object LIBRARY : LineParser<VersionCatalogParser.Entity.Library> {
        override fun parse(line: String): VersionCatalogParser.Entity.Library {
            val keyEnd = line.indexOf("=")
            val key = line.take(keyEnd)
            val body = line.drop(keyEnd + 1)

            val groupStart = body.indexOf("=") + 1
            val groupEnd = body.indexOf(",", groupStart)
            val group = body.substring(groupStart, groupEnd).trim().drop(1).dropLast(1)

            val nameStart = body.indexOf("=", groupEnd) + 1
            val nameEnd = body.indexOf(",", nameStart)
            val name = body.substring(nameStart, nameEnd).trim().drop(1).dropLast(1)

            val refStart = body.indexOf("=", nameEnd) + 1
            val ref = body.drop(refStart)
                // drop "}"
                .trim()
                .dropLast(1)
                // drop "\""
                .trim()
                .drop(1)
                .dropLast(1)

            return VersionCatalogParser.Entity.Library(key, group, name, ref)
        }
    }

    object PLUGIN : LineParser<VersionCatalogParser.Entity.Plugin> {
        override fun parse(line: String): VersionCatalogParser.Entity.Plugin {
            val keyEnd = line.indexOf("=")
            val key = line.take(keyEnd)
            val body = line.drop(keyEnd + 1)

            val idStart = body.indexOf("=") + 1
            val idEnd = body.indexOf(",", idStart)
            val id = body.substring(idStart, idEnd).trim()

            val refStart = body.indexOf("=", idEnd) + 1
            val ref = body.drop(refStart).trim()

            return VersionCatalogParser.Entity.Plugin(key, id, ref)
        }
    }
}
