package com.ahtohiofilho.dominopernambucano.online

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesOnlineAnonymousSessionStoreTest {
    @Test
    fun write_read_and_clear_round_trip() {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        val preferencesName =
            "test_online_anonymous_session_${UUID.randomUUID()}"

        val store = SharedPreferencesOnlineAnonymousSessionStore(
            context = context,
            preferencesName = preferencesName,
        )

        val session = OnlineAnonymousSessionDto(
            playerId = "anonymous-player-1",
            accessToken = "test-access-token",
            expiresAtEpochMillis = 2_000L,
        )

        try {
            assertNull(
                store.read(),
            )

            store.write(
                session = session,
            )

            val reopenedStore =
                SharedPreferencesOnlineAnonymousSessionStore(
                    context = context,
                    preferencesName = preferencesName,
                )

            assertEquals(
                session,
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
}