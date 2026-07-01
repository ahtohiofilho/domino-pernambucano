package com.ahtohiofilho.dominopernambucano.online

import android.content.Context
import android.content.SharedPreferences

private const val ONLINE_PARTICIPATION_BINDING_PREFERENCES_NAME =
    "online_participation_binding"

private const val ONLINE_PARTICIPATION_BINDING_ROOM_ID_KEY =
    "room_id"

private const val ONLINE_PARTICIPATION_BINDING_MATCH_ID_KEY =
    "match_id"

private const val ONLINE_PARTICIPATION_BINDING_PLAYER_ID_KEY =
    "player_id"

private const val ONLINE_PARTICIPATION_BINDING_LOCAL_SEAT_INDEX_KEY =
    "local_seat_index"

class SharedPreferencesOnlineParticipationBindingStore(
    context: Context,
    preferencesName: String =
        ONLINE_PARTICIPATION_BINDING_PREFERENCES_NAME,
) : OnlineParticipationBindingStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            preferencesName,
            Context.MODE_PRIVATE,
        )

    override fun read(): OnlineParticipationBinding? {
        val hasAnyStoredValue =
            preferences.contains(
                ONLINE_PARTICIPATION_BINDING_ROOM_ID_KEY,
            ) ||
                    preferences.contains(
                        ONLINE_PARTICIPATION_BINDING_MATCH_ID_KEY,
                    ) ||
                    preferences.contains(
                        ONLINE_PARTICIPATION_BINDING_PLAYER_ID_KEY,
                    ) ||
                    preferences.contains(
                        ONLINE_PARTICIPATION_BINDING_LOCAL_SEAT_INDEX_KEY,
                    )

        if (!hasAnyStoredValue) {
            return null
        }

        val roomId = preferences.getString(
            ONLINE_PARTICIPATION_BINDING_ROOM_ID_KEY,
            null,
        )

        val playerId = preferences.getString(
            ONLINE_PARTICIPATION_BINDING_PLAYER_ID_KEY,
            null,
        )

        val hasMatchId = preferences.contains(
            ONLINE_PARTICIPATION_BINDING_MATCH_ID_KEY,
        )

        val matchId = if (hasMatchId) {
            preferences.getString(
                ONLINE_PARTICIPATION_BINDING_MATCH_ID_KEY,
                null,
            )
        } else {
            null
        }

        val hasLocalSeatIndex = preferences.contains(
            ONLINE_PARTICIPATION_BINDING_LOCAL_SEAT_INDEX_KEY,
        )

        if (
            roomId == null ||
            playerId == null ||
            !hasLocalSeatIndex ||
            (hasMatchId && matchId == null)
        ) {
            clear()
            return null
        }

        return OnlineParticipationBinding(
            roomId = roomId,
            matchId = matchId,
            playerId = playerId,
            localSeatIndex = preferences.getInt(
                ONLINE_PARTICIPATION_BINDING_LOCAL_SEAT_INDEX_KEY,
                -1,
            ),
        )
    }

    override fun write(
        binding: OnlineParticipationBinding,
    ) {
        val editor = preferences.edit()
            .putString(
                ONLINE_PARTICIPATION_BINDING_ROOM_ID_KEY,
                binding.roomId,
            )
            .putString(
                ONLINE_PARTICIPATION_BINDING_PLAYER_ID_KEY,
                binding.playerId,
            )
            .putInt(
                ONLINE_PARTICIPATION_BINDING_LOCAL_SEAT_INDEX_KEY,
                binding.localSeatIndex,
            )

        if (binding.matchId == null) {
            editor.remove(
                ONLINE_PARTICIPATION_BINDING_MATCH_ID_KEY,
            )
        } else {
            editor.putString(
                ONLINE_PARTICIPATION_BINDING_MATCH_ID_KEY,
                binding.matchId,
            )
        }

        editor.apply()
    }

    override fun clear() {
        preferences.edit()
            .remove(
                ONLINE_PARTICIPATION_BINDING_ROOM_ID_KEY,
            )
            .remove(
                ONLINE_PARTICIPATION_BINDING_MATCH_ID_KEY,
            )
            .remove(
                ONLINE_PARTICIPATION_BINDING_PLAYER_ID_KEY,
            )
            .remove(
                ONLINE_PARTICIPATION_BINDING_LOCAL_SEAT_INDEX_KEY,
            )
            .apply()
    }
}