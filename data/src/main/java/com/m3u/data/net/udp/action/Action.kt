package com.m3u.data.net.udp.action

sealed interface Action {
    fun find(metadata: String, arg: String): String? {
        val matches = REGEX_GROUP_METADATA.findAll(metadata)
        val iterator = matches.iterator()
        while (iterator.hasNext()) {
            val match = iterator.next()
            if (match.groups[1]?.value == arg) {
                return match.groups[2]?.value?.ifEmpty { null }
            }
        }
        return null
    }

    fun createMetadata(map: Map<String, *>): String = buildString {
        map.forEach { (k, v) ->
            v ?: return@forEach
            append("$k=$v")
        }
    }

    companion object {
        val REGEX_GROUP_METADATA = """([\w-_.]+)=\s*(?:"([^"]*)"|(\S+))""".toRegex()
    }
}