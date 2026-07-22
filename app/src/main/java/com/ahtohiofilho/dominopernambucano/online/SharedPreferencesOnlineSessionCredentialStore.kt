package com.ahtohiofilho.dominopernambucano.online

import android.content.Context
import android.content.SharedPreferences

private const val ONLINE_SESSION_CREDENTIAL_PREFERENCES_NAME =
    "online_anonymous_session"

private const val ONLINE_SESSION_KIND_KEY = "session_kind"
private const val ONLINE_SESSION_PLAYER_ID_KEY = "player_id"
private const val ONLINE_SESSION_ACCESS_TOKEN_KEY = "access_token"
private const val ONLINE_SESSION_EXPIRES_AT_EPOCH_MILLIS_KEY =
    "expires_at_epoch_millis"
private const val ONLINE_SESSION_ACCOUNT_ID_KEY = "account_id"

class SharedPreferencesOnlineSessionCredentialStore(
    context: Context,
    preferencesName: String =
        ONLINE_SESSION_CREDENTIAL_PREFERENCES_NAME,
) : OnlineSessionCredentialStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            preferencesName,
            Context.MODE_PRIVATE,
        )

    override fun read(): OnlineSessionCredential? {
        val hasAnyStoredValue = listOf(
            ONLINE_SESSION_KIND_KEY,
            ONLINE_SESSION_PLAYER_ID_KEY,
            ONLINE_SESSION_ACCESS_TOKEN_KEY,
            ONLINE_SESSION_EXPIRES_AT_EPOCH_MILLIS_KEY,
            ONLINE_SESSION_ACCOUNT_ID_KEY,
        ).any(preferences::contains)

        if (!hasAnyStoredValue) {
            return null
        }

        val playerId = preferences.getString(
            ONLINE_SESSION_PLAYER_ID_KEY,
            null,
        )
        val accessToken = preferences.getString(
            ONLINE_SESSION_ACCESS_TOKEN_KEY,
            null,
        )
        val hasExpiration = preferences.contains(
            ONLINE_SESSION_EXPIRES_AT_EPOCH_MILLIS_KEY,
        )

        if (
            playerId.isNullOrBlank() ||
            accessToken.isNullOrBlank() ||
            !hasExpiration
        ) {
            clear()
            return null
        }

        val rawSessionKind = preferences.getString(
            ONLINE_SESSION_KIND_KEY,
            null,
        )

        /*
         * Registros anteriores ao P1.F.3.A.2 não possuem session_kind. Eles
         * continuam válidos e são interpretados como sessões anônimas.
         */
        val sessionKind = if (rawSessionKind == null) {
            OnlineSessionKind.ANONYMOUS
        } else {
            runCatching {
                OnlineSessionKind.valueOf(rawSessionKind)
            }.getOrNull() ?: run {
                clear()
                return null
            }
        }

        val accountId = preferences.getString(
            ONLINE_SESSION_ACCOUNT_ID_KEY,
            null,
        )

        if (
            sessionKind == OnlineSessionKind.ACCOUNT &&
            accountId.isNullOrBlank()
        ) {
            clear()
            return null
        }

        return OnlineSessionCredential(
            sessionKind = sessionKind,
            playerId = playerId,
            accessToken = accessToken,
            expiresAtEpochMillis = preferences.getLong(
                ONLINE_SESSION_EXPIRES_AT_EPOCH_MILLIS_KEY,
                0L,
            ),
            accountId = if (
                sessionKind == OnlineSessionKind.ACCOUNT
            ) {
                accountId
            } else {
                null
            },
        )
    }

    override fun write(
        credential: OnlineSessionCredential,
    ): Boolean {
        val previousCredential = read()
        val committed = credentialEditor(credential).commit()

        if (committed) {
            return true
        }

        /*
         * commit() atualiza a visão em memória antes de confirmar o disco.
         * Em uma falha, restauramos a credencial anterior também em memória.
         */
        if (previousCredential == null) {
            clearCredentialEditor().commit()
        } else {
            credentialEditor(previousCredential).commit()
        }

        return false
    }

    override fun clear(): Boolean {
        return clearCredentialEditor().commit()
    }

    private fun credentialEditor(
        credential: OnlineSessionCredential,
    ): SharedPreferences.Editor {
        val editor = preferences.edit()
            .putString(
                ONLINE_SESSION_KIND_KEY,
                credential.sessionKind.name,
            )
            .putString(
                ONLINE_SESSION_PLAYER_ID_KEY,
                credential.playerId,
            )
            .putString(
                ONLINE_SESSION_ACCESS_TOKEN_KEY,
                credential.accessToken,
            )
            .putLong(
                ONLINE_SESSION_EXPIRES_AT_EPOCH_MILLIS_KEY,
                credential.expiresAtEpochMillis,
            )

        if (credential.sessionKind == OnlineSessionKind.ACCOUNT) {
            editor.putString(
                ONLINE_SESSION_ACCOUNT_ID_KEY,
                credential.accountId,
            )
        } else {
            editor.remove(ONLINE_SESSION_ACCOUNT_ID_KEY)
        }

        return editor
    }

    private fun clearCredentialEditor(): SharedPreferences.Editor {
        return preferences.edit()
            .remove(ONLINE_SESSION_KIND_KEY)
            .remove(ONLINE_SESSION_PLAYER_ID_KEY)
            .remove(ONLINE_SESSION_ACCESS_TOKEN_KEY)
            .remove(ONLINE_SESSION_EXPIRES_AT_EPOCH_MILLIS_KEY)
            .remove(ONLINE_SESSION_ACCOUNT_ID_KEY)
    }
}
