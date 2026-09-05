package com.ahtohiofilho.dominopernambucano.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineRankedEntryFlowTest {
    @Test
    fun connected_account_enters_matchmaking_without_reauthentication() {
        val events = mutableListOf<String>()

        enterOnlineRankedFlow(
            accountConnected = true,
            authenticate = { _ ->
                events += "authenticate"
            },
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
    fun successful_authentication_opens_account_setup_and_never_matchmaking() {
        val events = mutableListOf<String>()
        var authenticationSuccess: (() -> Unit)? = null

        enterOnlineRankedFlow(
            accountConnected = false,
            authenticate = { onConnected ->
                events += "authenticate"
                authenticationSuccess = onConnected
            },
            openAccountSetup = {
                events += "account-setup"
            },
            openMatchmaking = {
                events += "matchmaking"
            },
        )

        assertEquals(
            listOf("authenticate"),
            events,
        )

        requireNotNull(authenticationSuccess).invoke()

        assertEquals(
            listOf(
                "authenticate",
                "account-setup",
            ),
            events,
        )
    }
}
