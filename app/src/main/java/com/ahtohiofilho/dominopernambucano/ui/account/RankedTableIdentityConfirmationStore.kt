package com.ahtohiofilho.dominopernambucano.ui.account

import android.content.Context
import com.ahtohiofilho.dominopernambucano.online.isValidOnlineAccountTableCode
import com.ahtohiofilho.dominopernambucano.online.normalizeOnlineAccountTableCodeInput

private const val RANKED_TABLE_IDENTITY_CONFIRMATION_PREFERENCES =
    "ranked_table_identity_confirmation"
private const val CONFIRMATION_VERSION_KEY = "version"
private const val CONFIRMED_ACCOUNT_ID_KEY = "account_id"
private const val CONFIRMED_TABLE_CODE_KEY = "table_code"
private const val CURRENT_CONFIRMATION_VERSION = 1

class RankedTableIdentityConfirmationStore(
    context: Context,
) {
    private val preferences =
        context.applicationContext.getSharedPreferences(
            RANKED_TABLE_IDENTITY_CONFIRMATION_PREFERENCES,
            Context.MODE_PRIVATE,
        )

    fun isConfirmed(
        accountId: String,
        tableCode: String,
    ): Boolean {
        val normalizedAccountId = accountId.trim()
        val normalizedTableCode =
            normalizeOnlineAccountTableCodeInput(tableCode)

        if (
            normalizedAccountId.isBlank() ||
            !isValidOnlineAccountTableCode(normalizedTableCode)
        ) {
            return false
        }

        return preferences.getInt(
            CONFIRMATION_VERSION_KEY,
            0,
        ) == CURRENT_CONFIRMATION_VERSION &&
            preferences.getString(
                CONFIRMED_ACCOUNT_ID_KEY,
                null,
            ) == normalizedAccountId &&
            preferences.getString(
                CONFIRMED_TABLE_CODE_KEY,
                null,
            ) == normalizedTableCode
    }

    fun confirm(
        accountId: String,
        tableCode: String,
    ) {
        val normalizedAccountId = accountId.trim()
        val normalizedTableCode =
            normalizeOnlineAccountTableCodeInput(tableCode)

        require(normalizedAccountId.isNotBlank())
        require(
            isValidOnlineAccountTableCode(normalizedTableCode),
        )

        preferences.edit()
            .putInt(
                CONFIRMATION_VERSION_KEY,
                CURRENT_CONFIRMATION_VERSION,
            )
            .putString(
                CONFIRMED_ACCOUNT_ID_KEY,
                normalizedAccountId,
            )
            .putString(
                CONFIRMED_TABLE_CODE_KEY,
                normalizedTableCode,
            )
            .apply()
    }
}
