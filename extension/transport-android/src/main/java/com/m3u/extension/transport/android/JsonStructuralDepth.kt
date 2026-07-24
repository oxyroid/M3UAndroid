package com.m3u.extension.transport.android

/**
 * Rejects JSON whose container nesting is unsafe to hand to a recursive serializer.
 *
 * This is a linear structural preflight, not a JSON parser. The serializer remains responsible
 * for syntax and schema validation.
 */
fun String.requireSafeExtensionJsonDepth(
    maximumDepth: Int = MAX_EXTENSION_JSON_STRUCTURAL_DEPTH,
) {
    require(maximumDepth > 0) { "JSON depth limit must be positive" }
    var depth = 0
    var inString = false
    var escaped = false
    for (character in this) {
        if (inString) {
            when {
                escaped -> escaped = false
                character == '\\' -> escaped = true
                character == '"' -> inString = false
            }
            continue
        }
        when (character) {
            '"' -> inString = true
            '{', '[' -> {
                depth++
                require(depth <= maximumDepth) {
                    "Extension JSON exceeds the structural depth limit"
                }
            }
            '}', ']' -> {
                depth--
                require(depth >= 0) { "Extension JSON has an invalid container structure" }
            }
        }
    }
}

const val MAX_EXTENSION_JSON_STRUCTURAL_DEPTH: Int = 64
