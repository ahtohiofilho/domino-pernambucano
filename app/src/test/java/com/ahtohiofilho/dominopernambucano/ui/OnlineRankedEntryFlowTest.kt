package com.ahtohiofilho.dominopernambucano.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineRankedEntryFlowTest {
    @Test
    fun connected_account_enters_matchmaking_without_account_gate() {
        val events = mutableListOf<String>()

        enterOnlineRankedFlow(
            accountConnected = true,
            openAccountSetup = {
                events += "account-setup"
            },
            openMatchmaking = {
                events += "matchmaking"
            },
        )

        assertEquals(
            listOf("matchmaking"),
            events,
        )
    }

    @Test
    fun disconnected_account_opens_account_surface_before_matchmaking() {
        val events = mutableListOf<String>()

        enterOnlineRankedFlow(
            accountConnected = false,
            openAccountSetup = {
                events += "account-setup"
            },
            openMatchmaking = {
                events += "matchmaking"
            },
        )

        assertEquals(
            listOf("account-setup"),
            events,
        )
    }
}
