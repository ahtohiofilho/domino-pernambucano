package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicRankedLobbyParticipantCodesTest {
    @Test
    fun waiting_lobby_exposes_neutral_sorted_three_character_codes() {
        var accountSequence = 0
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = {
                accountSequence += 1
                "account-$accountSequence"
            },
        )

        val zzz = requireNotNull(
            store.promoteAccount(playerId = "human-zzz"),
        )
        val aaa = requireNotNull(
            store.promoteAccount(playerId = "human-aaa"),
        )

        configure(store, zzz, "ZZZ")
        configure(store, aaa, "AAA")

        val first = store.enqueuePublicRanked(
            request = request(zzz.playerId, "ZZZ"),
            identity = zzz.toRequestIdentity(),
        )
        assertEquals(
            PublicRankedQueueStatus.QUEUED,
            first.status,
        )
        assertEquals(listOf("ZZZ"), first.participantCodes)

        val second = store.enqueuePublicRanked(
            request = request(aaa.playerId, "AAA"),
            identity = aaa.toRequestIdentity(),
        )
        assertEquals(
            PublicRankedQueueStatus.QUEUED,
            second.status,
        )
        assertEquals(
            listOf("AAA", "ZZZ"),
            second.participantCodes,
        )
        assertTrue(
            second.participantCodes.all { code ->
                code.length == 3
            },
        )
    }

    private fun configure(
        store: InMemoryOnlineServerStore,
        account: OnlineServerAccount,
        tableCode: String,
    ) {
        requireNotNull(
            store.updateAccountProfile(
                accountId = account.accountId,
                publicDisplayName = "Jogador Teste",
                tableName = tableCode,
            ),
        )
    }

    private fun request(
        playerId: String,
        tableCode: String,
    ) = CreateOnlineRoomRequestDto(
        localPlayerId = playerId,
        playerName = tableCode,
    )

    private fun OnlineServerAccount.toRequestIdentity() =
        OnlineRequestIdentity(
            playerId = playerId,
            principalId = accountId,
            sessionId = "lobby-session:$playerId",
            kind = OnlinePrincipalKind.ACCOUNT,
            accountId = accountId,
        )
}