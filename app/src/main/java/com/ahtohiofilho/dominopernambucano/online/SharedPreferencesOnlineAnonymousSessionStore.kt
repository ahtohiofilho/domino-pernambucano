package com.ahtohiofilho.dominopernambucano.online

import android.content.Context
import android.content.SharedPreferences

private const val ONLINE_ANONYMOUS_SESSION_PREFERENCES_NAME =
    "online_anonymous_session"

private const val ONLINE_ANONYMOUS_SESSION_PLAYER_ID_KEY =
    "player_id"

private const val ONLINE_ANONYMOUS_SESSION_ACCESS_TOKEN_KEY =
    "access_token"

private const val ONLINE_ANONYMOUS_SESSION_EXPIRES_AT_EPOCH_MILLIS_KEY =
    "expires_at_epoch_millis"

class SharedPreferencesOnlineAnonymousSessionStore(
    context: Context,
    preferencesName: String =
        ONLINE_ANONYMOUS_SESSION_PREFERENCES_NAME,
) : OnlineAnonymousSessionStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            preferencesName,
            Context.MODE_PRIVATE,
        )

    override fun read(): OnlineAnonymousSessionDto? {
        val hasAnyStoredValue =
            preferences.contains(
                ONLINE_ANONYMOUS_SESSION_PLAYER_ID_KEY,
            ) ||
                    preferences.contains(
                        ONLINE_ANONYMOUS_SESSION_ACCESS_TOKEN_KEY,
                    ) ||
                    preferences.contains(
                        ONLINE_ANONYMOUS_SESSION_EXPIRES_AT_EPOCH_MILLIS_KEY,
                    )

        if (!hasAnyStoredValue) {
            return null
        }

        val playerId = preferences.getString(
            ONLINE_ANONYMOUS_SESSION_PLAYER_ID_KEY,
            null,
        )

        val accessToken = preferences.getString(
            ONLINE_ANONYMOUS_SESSION_ACCESS_TOKEN_KEY,
            null,
        )

        val hasExpiration = preferences.contains(
            ONLINE_ANONYMOUS_SESSION_EXPIRES_AT_EPOCH_MILLIS_KEY,
        )

        if (
            playerId == null ||
            accessToken == null ||
            !hasExpiration
        ) {
            clear()
            return null
        }

        return OnlineAnonymousSessionDto(
            playerId = playerId,
            accessToken = accessToken,
            expiresAtEpochMillis = preferences.getLong(
                ONLINE_ANONYMOUS_SESSION_EXPIRES_AT_EPOCH_MILLIS_KEY,
                0L,
            ),
        )
    }

    override fun write(
        session: OnlineAnonymousSessionDto,
    ) {
        preferences.edit()
            .putString(
                ONLINE_ANONYMOUS_SESSION_PLAYER_ID_KEY,
                session.playerId,
            )
            .putString(
                ONLINE_ANONYMOUS_SESSION_ACCESS_TOKEN_KEY,
                session.accessToken,
            )
            .putLong(
                ONLINE_ANONYMOUS_SESSION_EXPIRES_AT_EPOCH_MILLIS_KEY,
                session.expiresAtEpochMillis,
            )
            .apply()
    }

    override fun clear() {
        preferences.edit()
            .remove(
                ONLINE_ANONYMOUS_SESSION_PLAYER_ID_KEY,
            )
            .remove(
                ONLINE_ANONYMOUS_SESSION_ACCESS_TOKEN_KEY,
            )
            .remove(
                ONLINE_ANONYMOUS_SESSION_EXPIRES_AT_EPOCH_MILLIS_KEY,
            )
            .apply()
    }
}