package com.ahtohiofilho.dominopernambucano.online

import java.util.Locale

const val DEFAULT_ONLINE_DISPLAY_NAME = "Jogador"
const val DEFAULT_ONLINE_TABLE_NAME = "JOGADOR"
const val MAX_ONLINE_DISPLAY_NAME_LENGTH = 30
const val MAX_ONLINE_TABLE_NAME_LENGTH = 10

private val onlineNameWhitespaceRegex = Regex("\\s+")
private val brazilianPortugueseLocale = Locale.forLanguageTag("pt-BR")

fun normalizeOnlineDisplayName(
    rawName: String?,
): String {
    val normalizedName = rawName
        .orEmpty()
        .trim()
        .replace(
            regex = onlineNameWhitespaceRegex,
            replacement = " ",
        )
        .take(MAX_ONLINE_DISPLAY_NAME_LENGTH)

    return normalizedName.ifBlank {
        DEFAULT_ONLINE_DISPLAY_NAME
    }
}

fun normalizeOnlineTableName(
    rawName: String?,
    fallbackDisplayName: String?,
): String {
    val normalizedName = rawName
        .orEmpty()
        .uppercase(brazilianPortugueseLocale)
        .filter { character ->
            character.isLetterOrDigit()
        }
        .take(MAX_ONLINE_TABLE_NAME_LENGTH)

    return normalizedName.ifBlank {
        createDefaultOnlineTableName(
            displayName = normalizeOnlineDisplayName(
                rawName = fallbackDisplayName,
            ),
        )
    }
}

fun createDefaultOnlineTableName(
    displayName: String,
): String {
    val normalizedDisplayName = normalizeOnlineDisplayName(
        rawName = displayName,
    )

    val tokens = normalizedDisplayName
        .split(onlineNameWhitespaceRegex)
        .filter { token -> token.isNotBlank() }

    val firstToken = tokens
        .firstOrNull()
        .orEmpty()
        .uppercase(brazilianPortugueseLocale)
        .filter { character ->
            character.isLetterOrDigit()
        }

    val secondToken = tokens
        .getOrNull(1)
        .orEmpty()
        .uppercase(brazilianPortugueseLocale)
        .filter { character ->
            character.isLetterOrDigit()
        }

    val candidate = if (
        firstToken == DEFAULT_ONLINE_TABLE_NAME &&
        secondToken.isNotBlank()
    ) {
        firstToken + secondToken
    } else {
        firstToken
    }

    return candidate
        .take(MAX_ONLINE_TABLE_NAME_LENGTH)
        .ifBlank {
            DEFAULT_ONLINE_TABLE_NAME
        }
}

val OnlineRoomPlayerDto.resolvedDisplayName: String
    get() = normalizeOnlineDisplayName(
        rawName = name,
    )

val OnlineRoomPlayerDto.resolvedTableName: String
    get() = createDefaultOnlineTableName(
        displayName = resolvedDisplayName,
    )
