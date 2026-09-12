package com.ahtohiofilho.dominopernambucano.ui

import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineRankedEntryFlowTest {
    @Test
    fun connected_account_enters_matchmaking_without_account_gate() {
        assertEquals(
            listOf("matchmaking"),
            eventsFor(OnlineGoogleAccountStatus.CONNECTED),
        )
    }

    @Test
    fun expired_saved_account_attempts_restore_before_account_gate() {
        assertEquals(
            listOf("restore"),
            eventsFor(OnlineGoogleAccountStatus.RECOVERY_REQUIRED),
        )
    }

    @Test
    fun disconnected_states_open_account_gate_before_matchmaking() {
        listOf(
            OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL,
            OnlineGoogleAccountStatus.VISITOR,
            OnlineGoogleAccountStatus.UNAVAILABLE,
        ).forEach { status ->
            assertEquals(
                listOf("account-setup"),
                eventsFor(status),
            )
        }
    }

    private fun eventsFor(
        status: OnlineGoogleAccountStatus,
    ): List<String> {
        val events = mutableListOf<String>()

        enterOnlineRankedFlow(
            accountStatus = status,
            openAccountSetup = {
                events += "account-setup"
            },
            restoreSavedAccount = {
                events += "restore"
            },
            openMatchmaking = {
                events += "matchmaking"
            },
        )

        return events
    }
}
