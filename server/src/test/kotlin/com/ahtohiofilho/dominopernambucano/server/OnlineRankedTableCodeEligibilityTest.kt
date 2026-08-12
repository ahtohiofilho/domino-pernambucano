package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRankedTableCodeEligibilityTest {
    @Test
    fun ranked_queue_accepts_authoritative_three_character_table_code() {
        val store = InMemoryOnlineServerStore(
            accountIdFactory = { "account-1" },
        )
        val account = requireNotNull(
            store.promoteAccount(
                playerId = "player-1",
            ),
        )

        requireNotNull(
            store.updateAccountProfile(
                accountId = account.accountId,
                publicDisplayName = "Antônio Filho",
                tableName = "AFI",
            ),
        )

        val result = store.enqueuePublicRanked(
            request = CreateOnlineRoomRequestDto(
                localPlayerId = "player-1",
                playerName = "AFI",
            ),
            identity = accountIdentity(
                playerId = "player-1",
                accountId = account.accountId,
            ),
        )

        assertTrue(result.accepted)
    }

    @Test
    fun ranked_queue_rejects_legacy_table_name_until_user_selects_code() {
        val store = InMemoryOnlineServerStore()
        val account = OnlineServerAccount(
            accountId = "account-legacy",
            playerId = "player-legacy",
            createdAtEpochMillis = 1_000L,
            publicDisplayName = "Antônio Filho",
            tableName = "ANTÔNIO",
            profileUpdatedAtEpochMillis = 2_000L,
        )

        store.restorePersistentState(
            OnlineServerStoreState(
                accounts = listOf(account),
            ),
        )

        val result = store.enqueuePublicRanked(
            request = CreateOnlineRoomRequestDto(
                localPlayerId = account.playerId,
                playerName = "ANTÔNIO",
            ),
            identity = accountIdentity(
                playerId = account.playerId,
                accountId = account.accountId,
            ),
        )

        assertFalse(result.accepted)
        assertTrue(
            result.reason.orEmpty().contains(
                other = "sigla",
                ignoreCase = true,
            ),
        )
    }

    @Test
    fun ranked_queue_rejects_code_that_does_not_match_profile() {
        val store = InMemoryOnlineServerStore(
            accountIdFactory = { "account-1" },
        )
        val account = requireNotNull(
            store.promoteAccount(
                playerId = "player-1",
            ),
        )

        requireNotNull(
            store.updateAccountProfile(
                accountId = account.accountId,
                publicDisplayName = "Antônio Filho",
                tableName = "AFI",
            ),
        )

        val result = store.enqueuePublicRanked(
            request = CreateOnlineRoomRequestDto(
                localPlayerId = "player-1",
                playerName = "XYZ",
            ),
            identity = accountIdentity(
                playerId = "player-1",
                accountId = account.accountId,
            ),
        )

        assertFalse(result.accepted)
        assertTrue(
            result.reason.orEmpty().contains(
                other = "perfil",
                ignoreCase = true,
            ),
        )
    }

    private fun accountIdentity(
        playerId: String,
        accountId: String,
    ): OnlineRequestIdentity {
        return OnlineRequestIdentity(
            playerId = playerId,
            principalId = "principal-$playerId",
            sessionId = "session-$playerId",
            kind = OnlinePrincipalKind.ACCOUNT,
            accountId = accountId,
        )
    }
}
