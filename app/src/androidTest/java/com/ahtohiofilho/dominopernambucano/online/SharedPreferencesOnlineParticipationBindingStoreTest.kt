package com.ahtohiofilho.dominopernambucano.online

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesOnlineParticipationBindingStoreTest {
    @Test
    fun write_read_and_clear_round_trip() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        val preferencesName =
            "test_online_participation_binding_${UUID.randomUUID()}"

        val store = SharedPreferencesOnlineParticipationBindingStore(
            context = context,
            preferencesName = preferencesName,
        )

        val binding = OnlineParticipationBinding(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "anonymous-player-1",
            localSeatIndex = 3,
        )

        try {
            assertNull(
                store.read(),
            )

            store.write(
                binding = binding,
            )

            val reopenedStore =
                SharedPreferencesOnlineParticipationBindingStore(
                    context = context,
                    preferencesName = preferencesName,
                )

            assertEquals(
                binding,
                reopenedStore.read(),
            )

            reopenedStore.clear()

            assertNull(
                reopenedStore.read(),
            )
        } finally {
            store.clear()

            context.getSharedPreferences(
                preferencesName,
                android.content.Context.MODE_PRIVATE,
            ).edit()
                .clear()
                .apply()
        }
    }

    @Test
    fun write_without_match_id_removes_previous_match_id() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        val preferencesName =
            "test_online_participation_binding_${UUID.randomUUID()}"

        val store = SharedPreferencesOnlineParticipationBindingStore(
            context = context,
            preferencesName = preferencesName,
        )

        try {
            store.write(
                binding = OnlineParticipationBinding(
                    roomId = "room-1",
                    matchId = "match-1",
                    playerId = "anonymous-player-1",
                    localSeatIndex = 1,
                ),
            )

            val bindingWithoutMatch = OnlineParticipationBinding(
                roomId = "room-1",
                matchId = null,
                playerId = "anonymous-player-1",
                localSeatIndex = 1,
            )

            store.write(
                binding = bindingWithoutMatch,
            )

            assertEquals(
                bindingWithoutMatch,
                store.read(),
            )
        } finally {
            store.clear()

            context.getSharedPreferences(
                preferencesName,
                android.content.Context.MODE_PRIVATE,
            ).edit()
                .clear()
                .apply()
        }
    }
}