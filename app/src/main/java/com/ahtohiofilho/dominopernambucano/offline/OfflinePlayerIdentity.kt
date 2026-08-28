package com.ahtohiofilho.dominopernambucano.offline

import android.content.Context
import java.text.Normalizer

private const val OFFLINE_IDENTITY_PREFERENCES_NAME = "offline_player_identity"
private const val OFFLINE_DISPLAY_NAME_KEY = "display_name"
private const val OFFLINE_TABLE_CODE_KEY = "table_code"

private val OFFLINE_TABLE_CODE_REGEX = Regex("^[A-Z0-9]{3}$")

private val OFFLINE_BOT_TABLE_CODE_POOL = listOf(
    "B01",
    "B02",
    "B03",
    "B04",
)

data class OfflinePlayerIdentity(
    val displayName: String,
    val tableCode: String,
)

fun normalizeOfflineDisplayName(value: String): String {
    return Normalizer.normalize(
        value,
        Normalizer.Form.NFC,
    ).trim()
}

fun normalizeOfflineTableCode(value: String): String {
    return value
        .trim()
        .uppercase()
        .filter { character -> character in 'A'..'Z' || character in '0'..'9' }
        .take(3)
}

fun isValidOfflineTableCode(value: String): Boolean {
    return OFFLINE_TABLE_CODE_REGEX.matches(value)
}

fun buildOfflinePlayerTableCodes(
    identity: OfflinePlayerIdentity,
): List<String> {
    require(identity.displayName.isNotBlank())
    require(isValidOfflineTableCode(identity.tableCode))

    val botCodes = OFFLINE_BOT_TABLE_CODE_POOL
        .filterNot { botCode -> botCode == identity.tableCode }
        .take(3)

    check(botCodes.size == 3)

    return listOf(identity.tableCode) + botCodes
}

class SharedPreferencesOfflinePlayerIdentityStore(
    context: Context,
    private val preferencesName: String = OFFLINE_IDENTITY_PREFERENCES_NAME,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        preferencesName,
        Context.MODE_PRIVATE,
    )

    fun read(): OfflinePlayerIdentity? {
        val displayName = normalizeOfflineDisplayName(
            preferences.getString(
                OFFLINE_DISPLAY_NAME_KEY,
                null,
            ).orEmpty(),
        )

        val tableCode = preferences.getString(
            OFFLINE_TABLE_CODE_KEY,
            null,
        )?.trim()?.uppercase().orEmpty()

        if(displayName.isBlank() || !isValidOfflineTableCode(tableCode)) {
            return null
        }

        return OfflinePlayerIdentity(
            displayName = displayName,
            tableCode = tableCode,
        )
    }

    fun save(identity: OfflinePlayerIdentity) {
        val normalizedName = normalizeOfflineDisplayName(identity.displayName)
        val normalizedCode = normalizeOfflineTableCode(identity.tableCode)

        require(normalizedName.isNotBlank())
        require(isValidOfflineTableCode(normalizedCode))

        preferences.edit()
            .putString(
                OFFLINE_DISPLAY_NAME_KEY,
                normalizedName,
            )
            .putString(
                OFFLINE_TABLE_CODE_KEY,
                normalizedCode,
            )
            .apply()
    }
}