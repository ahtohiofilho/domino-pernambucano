package com.ahtohiofilho.dominopernambucano.online

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesOnlineSessionCredentialStoreTest {
    @Test
    fun legacy_record_without_kind_is_read_as_anonymous() {
        withStore { context, preferencesName, store ->
            context.getSharedPreferences(
                preferencesName,
                Context.MODE_PRIVATE,
            ).edit()
                .putString("player_id", "anonymous-player-1")
                .putString("access_token", "legacy-token")
                .putLong("expires_at_epoch_millis", 2_000L)
                .commit()

            assertEquals(
                OnlineSessionCredential(
                    sessionKind = OnlineSessionKind.ANONYMOUS,
                    playerId = "anonymous-player-1",
                    accessToken = "legacy-token",
                    expiresAtEpochMillis = 2_000L,
                ),
                store.read(),
            )
        }
    }

    @Test
    fun account_write_is_atomic_and_survives_store_reopen() {
        withStore { context, preferencesName, store ->
            val account = OnlineSessionCredential(
                sessionKind = OnlineSessionKind.ACCOUNT,
                accountId = "account-1",
                playerId = "anonymous-player-1",
                accessToken = "account-token",
                expiresAtEpochMillis = 3_000L,
            )

            assertTrue(store.write(account))

            val reopened = SharedPreferencesOnlineSessionCredentialStore(
                context = context,
                preferencesName = preferencesName,
            )

            assertEquals(account, reopened.read())
            assertTrue(reopened.clear())
            assertNull(reopened.read())
        }
    }

    private fun withStore(
        block: (
            Context,
            String,
            SharedPreferencesOnlineSessionCredentialStore,
        ) -> Unit,
    ) {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesName =
            "test_online_session_credential_${UUID.randomUUID()}"
        val store = SharedPreferencesOnlineSessionCredentialStore(
            context = context,
            preferencesName = preferencesName,
        )

        try {
            block(context, preferencesName, store)
        } finally {
            context.getSharedPreferences(
                preferencesName,
                Context.MODE_PRIVATE,
            ).edit().clear().commit()
        }
    }
}
