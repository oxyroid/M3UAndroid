package com.m3u.data.extension

/**
 * Validates untrusted extension text before it reaches host-owned UI or persistence.
 *
 * This deliberately rejects direction-formatting and line-separator code points instead of
 * rewriting them. Natural RTL text remains valid because ordinary Arabic and Hebrew characters
 * are not formatting controls.
 */
internal fun String.isSafeExtensionText(
    maximumLength: Int,
    maximumUtf8Bytes: Int = Int.MAX_VALUE,
    allowBlank: Boolean = false,
): Boolean =
    (allowBlank || isNotBlank()) &&
        length <= maximumLength &&
        encodeToByteArray().size <= maximumUtf8Bytes &&
        none { character ->
            character.isISOControl() ||
                character.code == ARABIC_LETTER_MARK ||
                character.code in BIDI_EMBEDDING_AND_OVERRIDE_RANGE ||
                character.code in BIDI_ISOLATE_RANGE ||
                character.code == LEFT_TO_RIGHT_MARK ||
                character.code == RIGHT_TO_LEFT_MARK ||
                character.code == LINE_SEPARATOR ||
                character.code == PARAGRAPH_SEPARATOR
        }

private const val ARABIC_LETTER_MARK = 0x061C
private const val LEFT_TO_RIGHT_MARK = 0x200E
private const val RIGHT_TO_LEFT_MARK = 0x200F
private const val LINE_SEPARATOR = 0x2028
private const val PARAGRAPH_SEPARATOR = 0x2029
private val BIDI_EMBEDDING_AND_OVERRIDE_RANGE = 0x202A..0x202E
private val BIDI_ISOLATE_RANGE = 0x2066..0x2069
