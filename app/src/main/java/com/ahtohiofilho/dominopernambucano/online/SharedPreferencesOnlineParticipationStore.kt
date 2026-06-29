package com.ahtohiofilho.dominopernambucano.online

import android.content.Context
import android.content.SharedPreferences

private const val ONLINE_PARTICIPATION_PREFERENCES_NAME =
    "online_participation"

private const val ONLINE_PARTICIPATION_BACKEND_SCOPE_KEY =
    "backend_scope"

private const val ONLINE_PARTICIPATION_ROOM_ID_KEY =
    "room_id"

private const val ONLINE_PARTICIPATION_MATCH_ID_KEY =
    "match_id"

private const val ONLINE_PARTICIPATION_PLAYER_ID_KEY =
    "player_id"

private const val ONLINE_PARTICIPATION_SEAT_INDEX_KEY =
    "seat_index"

private const val NO_ONLINE_PARTICIPATION_SEAT_INDEX = -1

class SharedPreferencesOnlineParticipationStore(
    context: Context,
) : OnlineParticipationStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(
            ONLINE_PARTICIPATION_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

    override fun read(): OnlineParticipationBinding? {
        return OnlineParticipationBinding(
            backendScope = preferences.getString(
                ONLINE_PARTICIPATION_BACKEND_SCOPE_KEY,
                null,
            ).orEmpty(),
            roomId = preferences.getString(
                ONLINE_PARTICIPATION_ROOM_ID_KEY,
                null,
            ).orEmpty(),
            matchId = preferences.getString(
                ONLINE_PARTICIPATION_MATCH_ID_KEY,
                null,
            ),
            playerId = preferences.getString(
                ONLINE_PARTICIPATION_PLAYER_ID_KEY,
                null,
            ).orEmpty(),
            seatIndex = preferences.getInt(
                ONLINE_PARTICIPATION_SEAT_INDEX_KEY,
                NO_ONLINE_PARTICIPATION_SEAT_INDEX,
            ),
        ).normalizedOrNull()
    }

    override fun write(
        binding: OnlineParticipationBinding,
    ) {
        val normalizedBinding = binding.normalizedOrNull()

        if (normalizedBinding == null) {
            clear()
            return
        }

        /*
         * Este estado é mínimo e deve sobreviver até a próxima abertura. O
         * commit síncrono reduz a janela entre a entrada aceita e a morte do
         * processo, sem colocar dados sensíveis nesse arquivo.
         */
        preferences.edit()
            .putString(
                ONLINE_PARTICIPATION_BACKEND_SCOPE_KEY,
                normalizedBinding.backendScope,
            )
            .putString(
                ONLINE_PARTICIPATION_ROOM_ID_KEY,
                normalizedBinding.roomId,
            )
            .putString(
                ONLINE_PARTICIPATION_MATCH_ID_KEY,
                normalizedBinding.matchId,
            )
            .putString(
                ONLINE_PARTICIPATION_PLAYER_ID_KEY,
                normalizedBinding.playerId,
            )
            .putInt(
                ONLINE_PARTICIPATION_SEAT_INDEX_KEY,
                normalizedBinding.seatIndex,
            )
            .commit()
    }

    override fun clear() {
        preferences.edit()
            .clear()
            .commit()
    }
}
