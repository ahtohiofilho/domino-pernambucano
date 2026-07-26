package com.ahtohiofilho.dominopernambucano.online

import java.util.Locale

const val MAX_ONLINE_PUBLIC_DISPLAY_NAME_LENGTH = 60

private val onlinePublicNameWhitespaceRegex = Regex("\\s+")
private val onlinePublicNameTokenRegex =
    Regex("^[\\p{L}][\\p{L}'’\\-]*$")
private val onlineAccountTableNameRegex =
    Regex("^[\\p{L}\\p{N}]+$")
private val onlineProfileLocale = Locale.forLanguageTag("pt-BR")

data class OnlineAccountProfile(
    val publicDisplayName: String,
    val tableName: String,
    val updatedAtEpochMillis: Long,
)

fun normalizeOnlinePublicDisplayName(
    rawName: String,
): String {
    val normalizedName = rawName
        .trim()
        .replace(
            regex = onlinePublicNameWhitespaceRegex,
            replacement = " ",
        )

    require(normalizedName.isNotBlank()) {
        "O nome público é obrigatório."
    }
    require(
        normalizedName.length <=
            MAX_ONLINE_PUBLIC_DISPLAY_NAME_LENGTH,
    ) {
        "O nome público excede o limite permitido."
    }

    val tokens = normalizedName
        .split(onlinePublicNameWhitespaceRegex)
        .filter { token -> token.isNotBlank() }

    require(tokens.size >= 2) {
        "Informe nome e sobrenome."
    }
    require(
        tokens.all { token ->
            token.matches(onlinePublicNameTokenRegex)
        },
    ) {
        "O nome público contém caracteres inválidos."
    }

    return normalizedName
}

fun normalizeOnlineAccountTableName(
    rawName: String?,
    publicDisplayName: String,
): String {
    val normalizedPublicName = normalizeOnlinePublicDisplayName(
        rawName = publicDisplayName,
    )
    val candidate = rawName
        .orEmpty()
        .trim()

    if (candidate.isBlank()) {
        return createDefaultOnlineTableName(
            displayName = normalizedPublicName,
        )
    }

    val normalizedTableName = candidate.uppercase(
        onlineProfileLocale,
    )

    require(
        normalizedTableName.length <=
            MAX_ONLINE_TABLE_NAME_LENGTH,
    ) {
        "O nome de mesa excede o limite permitido."
    }
    require(
        normalizedTableName.matches(
            onlineAccountTableNameRegex,
        ),
    ) {
        "O nome de mesa deve conter apenas letras e números."
    }

    return normalizedTableName
}

fun createOnlineAccountProfile(
    publicDisplayName: String,
    tableName: String?,
    updatedAtEpochMillis: Long,
): OnlineAccountProfile {
    require(updatedAtEpochMillis >= 0L) {
        "O instante de atualização do perfil é inválido."
    }

    val normalizedPublicName =
        normalizeOnlinePublicDisplayName(
            rawName = publicDisplayName,
        )

    return OnlineAccountProfile(
        publicDisplayName = normalizedPublicName,
        tableName = normalizeOnlineAccountTableName(
            rawName = tableName,
            publicDisplayName = normalizedPublicName,
        ),
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}
