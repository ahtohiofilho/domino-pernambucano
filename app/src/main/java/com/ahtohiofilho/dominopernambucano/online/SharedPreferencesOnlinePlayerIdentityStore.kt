package com.ahtohiofilho.dominopernambucano.online

import android.content.Context
import android.content.SharedPreferences

private const val ONLINE_PLAYER_IDENTITY_PREFERENCES_NAME =
    "online_player_identity"

class SharedPreferencesOnlinePlayerIdentityStore(
    context: Context,
) : OnlinePlayerIdentityStore {
    private val repository = OnlinePlayerIdentityRepository(
        storage = SharedPreferencesOnlinePlayerIdentityStorage(
            context = context.applicationContext,
        ),
    )

    override fun getOrCreate(): OnlinePlayerIdentity {
        return repository.getOrCreate()
    }

    override fun updateDisplayName(
        displayName: String,
    ): OnlinePlayerIdentity {
        return repository.updateDisplayName(
            displayName = displayName,
        )
    }

    override fun updateTableName(
        tableName: String,
    ): OnlinePlayerIdentity {
        return repository.updateTableName(
            tableName = tableName,
        )
    }

    override fun resetTableNameToGenerated(): OnlinePlayerIdentity {
        return repository.resetTableNameToGenerated()
    }
}

private class SharedPreferencesOnlinePlayerIdentityStorage(
    context: Context,
) : OnlinePlayerIdentityStorage {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(
            ONLINE_PLAYER_IDENTITY_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    override fun readString(
        key: String,
    ): String? {
        return preferences.getString(
            key,
            null,
        )
    }

    override fun writeString(
        key: String,
        value: String,
    ) {
        preferences.edit()
            .putString(
                key,
                value,
            )
            .apply()
    }
}
