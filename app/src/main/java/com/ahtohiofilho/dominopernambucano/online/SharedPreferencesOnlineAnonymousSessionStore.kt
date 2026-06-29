package com.ahtohiofilho.dominopernambucano.online

import android.content.Context
import android.content.SharedPreferences

private const val ONLINE_ANONYMOUS_SESSION_PREFERENCES_NAME =
    "online_anonymous_session"

private const val ONLINE_ANONYMOUS_SESSION_PLAYER_ID_KEY =
    "player_id"

private const val ONLINE_ANONYMOUS_SESSION_ACCESS_TOKEN_KEY =
    "access_token"

private const val ONLINE_ANONYMOUS_SESSION_EXPIRES_AT_KEY =
    "expires_at_epoch_millis"

class SharedPreferencesOnlineAnonymousSessionStore(
    context: Context,
) : OnlineAnonymousSessionStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            ONLINE_ANONYMOUS_SESSION_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    override fun read(): OnlineAnonymousSessionDto? {
        val playerId = preferences.getString(
            ONLINE_ANONYMOUS_SESSION_PLAYER_ID_KEY,
            null,
        )
            ?.trim()
            .orEmpty()

        val accessToken = preferences.getString(
            ONLINE_ANONYMOUS_SESSION_ACCESS_TOKEN_KEY,
            null,
        )
            ?.trim()
            .orEmpty()

        val expiresAtEpochMillis = preferences.getLong(
            ONLINE_ANONYMOUS_SESSION_EXPIRES_AT_KEY,
            0L,
        )

        if (
            playerId.isBlank() ||
            accessToken.isBlank() ||
            expiresAtEpochMillis <= 0L
        ) {
            return null
        }

        return OnlineAnonymousSessionDto(
            playerId = playerId,
            accessToken = accessToken,
            expiresAtEpochMillis = expiresAtEpochMillis,
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
                ONLINE_ANONYMOUS_SESSION_EXPIRES_AT_KEY,
                session.expiresAtEpochMillis,
            )
            .apply()
    }

    override fun clear() {
        preferences.edit()
            .clear()
            .apply()
    }
}
