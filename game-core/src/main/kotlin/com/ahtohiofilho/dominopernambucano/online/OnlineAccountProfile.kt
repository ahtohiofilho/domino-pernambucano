package com.ahtohiofilho.dominopernambucano.online

import java.text.Normalizer
import java.util.Locale

const val MAX_ONLINE_PUBLIC_DISPLAY_NAME_LENGTH = 60
const val ONLINE_ACCOUNT_TABLE_CODE_LENGTH = 3

private val onlinePublicNameWhitespaceRegex = Regex("\\s+")
private val onlinePublicNameTokenRegex =
    Regex("^[\\p{L}][\\p{L}'’\\-]*$")
private val onlineLegacyAccountTableNameRegex =
    Regex("^[\\p{L}\\p{N}]+$")
private val onlineAccountTableCodeRegex =
    Regex("^[A-Z0-9]{$ONLINE_ACCOUNT_TABLE_CODE_LENGTH}$")
private val onlineProfileLocale = Locale.forLanguageTag("pt-BR")
private val onlineAccountTableCodeCombiningMarkRegex =
    Regex("\\p{M}+")

data class OnlineAccountProfile(
    val publicDisplayName: String,
    val tableName: String,
    val updatedAtEpochMillis: Long,
)

fun normalizeOnlinePublicDisplayName(
    rawName: String,
): String {
    val normalizedName = Normalizer
        .normalize(
            rawName,
            Normalizer.Form.NFC,
        )
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

    require(
        tokens.all { token ->
            token.matches(onlinePublicNameTokenRegex)
        },
    ) {
        "O nome público contém caracteres inválidos."
    }

    return normalizedName
}

fun normalizeOnlineAccountTableCodeInput(
    rawName: String?,
): String {
    val normalizedCode = Normalizer
        .normalize(
            rawName.orEmpty(),
            Normalizer.Form.NFD,
        )
        .replace(
            regex = onlineAccountTableCodeCombiningMarkRegex,
            replacement = "",
        )
        .uppercase(onlineProfileLocale)
        .filter { character ->
            character in 'A'..'Z' ||
                character in '0'..'9'
        }

    return normalizedCode.take(
        ONLINE_ACCOUNT_TABLE_CODE_LENGTH,
    )
}

fun suggestOnlineAccountTableCode(
    publicDisplayName: String,
): String {
    return normalizeOnlineAccountTableCodeInput(
        rawName = publicDisplayName,
    )
}

fun normalizeOnlineAccountTableName(
    rawName: String?,
    publicDisplayName: String,
): String {
    normalizeOnlinePublicDisplayName(
        rawName = publicDisplayName,
    )

    val normalizedTableCode = rawName
        .orEmpty()
        .trim()
        .uppercase(onlineProfileLocale)

    require(
        normalizedTableCode.length ==
            ONLINE_ACCOUNT_TABLE_CODE_LENGTH,
    ) {
        "O nome curto deve ter exatamente 3 caracteres."
    }
    require(
        normalizedTableCode.matches(
            onlineAccountTableCodeRegex,
        ),
    ) {
        "O nome curto deve conter apenas A-Z e 0-9."
    }

    return normalizedTableCode
}

fun isValidOnlineAccountTableCode(
    rawName: String?,
): Boolean {
    val candidate = rawName
        .orEmpty()
        .trim()
        .uppercase(onlineProfileLocale)

    return candidate.length ==
        ONLINE_ACCOUNT_TABLE_CODE_LENGTH &&
        candidate.matches(onlineAccountTableCodeRegex)
}

fun normalizeLegacyOnlineAccountTableName(
    rawName: String,
): String {
    val normalizedTableName = rawName
        .trim()
        .uppercase(onlineProfileLocale)

    require(normalizedTableName.isNotBlank()) {
        "O nome de mesa legado é obrigatório."
    }
    require(
        normalizedTableName.length <=
            MAX_ONLINE_TABLE_NAME_LENGTH,
    ) {
        "O nome de mesa legado excede o limite permitido."
    }
    require(
        normalizedTableName.matches(
            onlineLegacyAccountTableNameRegex,
        ),
    ) {
        "O nome de mesa legado contém caracteres inválidos."
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

fun createLegacyCompatibleOnlineAccountProfile(
    publicDisplayName: String,
    tableName: String,
    updatedAtEpochMillis: Long,
): OnlineAccountProfile {
    require(updatedAtEpochMillis >= 0L) {
        "O instante de atualização do perfil é inválido."
    }

    return OnlineAccountProfile(
        publicDisplayName = normalizeOnlinePublicDisplayName(
            rawName = publicDisplayName,
        ),
        tableName = normalizeLegacyOnlineAccountTableName(
            rawName = tableName,
        ),
        updatedAtEpochMillis = updatedAtEpochMillis,
    )
}
