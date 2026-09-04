package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticParticipantIdentityTest {
    @Test
    fun provisioning_secret_is_explicit_and_compared_exactly() {
        val secret = "0123456789abcdef0123456789abcdef"
        val policy = SyntheticProvisioningPolicy.fixedForTest(secret)

        assertTrue(policy.authorizes(secret))
        assertFalse(policy.authorizes("${secret}x"))
        assertFalse(SyntheticProvisioningPolicy.Disabled.authorizes(secret))
    }

    @Test
    fun synthetic_account_type_is_persistent_and_cannot_be_downgraded() {
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
            accountIdFactory = { "account-synthetic" },
        )
        val account = requireNotNull(
            store.promoteSyntheticAccount(
                playerId = "player-synthetic",
            ),
        )

        assertEquals(
            OnlineParticipantTypeDto.SYNTHETIC,
            account.participantType,
        )
        assertEquals(
            OnlineParticipantTypeDto.SYNTHETIC,
            store.snapshotPersistentState()
                .accounts
                .single()
                .participantType,
        )
        val ordinaryRetry = requireNotNull(
            store.promoteAccount(
                playerId = "player-synthetic",
                expectedAccountId = account.accountId,
            ),
        )
        assertEquals(
            OnlineParticipantTypeDto.SYNTHETIC,
            ordinaryRetry.participantType,
        )
    }

    @Test
    fun mixed_external_accounts_share_canonical_ranked_match_without_app_bot_control() {
        var now = 1_000L
        var accountSequence = 0
        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
            accountIdFactory = {
                accountSequence++
                "account-$accountSequence"
            },
        )
        val publicNames = listOf(
            "Jogador Um",
            "Jogador Dois",
            "Jogador Tres",
            "Jogador Quatro",
        )
        val accounts = (1..4).associateWith { index ->
            val account = if (index <= 2) {
                store.promoteAccount(playerId = "player-$index")
            } else {
                store.promoteSyntheticAccount(playerId = "player-$index")
            }
            requireNotNull(account).also {
                requireNotNull(
                    store.updateAccountProfile(
                        accountId = it.accountId,
                        publicDisplayName = publicNames[index - 1],
                        tableName = "P0$index",
                    ),
                )
            }
        }

        accounts.forEach { (index, account) ->
            store.enqueuePublicRanked(
                request = CreateOnlineRoomRequestDto(
                    localPlayerId = account.playerId,
                    playerName = "P0$index",
                ),
                identity = account.toRequestIdentity(),
            )
        }

        assertTrue(store.snapshotPersistentState().rooms.isEmpty())

        now = 21_000L
        assertTrue(store.advanceAuthoritativeTime())
        assertEquals(
            PublicRankedQueueStatus.QUEUED,
            store.getPublicRankedQueueStatus(
                identity = accounts.getValue(1).toRequestIdentity(),
            ).status,
        )

        now = 41_000L
        assertTrue(store.advanceAuthoritativeTime())
        val matched = store.getPublicRankedQueueStatus(
            identity = accounts.getValue(1).toRequestIdentity(),
        )
        val room = requireNotNull(matched.roomSnapshot)
        val participantTypeByPlayer = room.players.associate { player ->
            player.playerId to player.participantType
        }
        assertEquals(
            OnlineParticipantTypeDto.HUMAN,
            participantTypeByPlayer.getValue("player-1"),
        )
        assertEquals(
            OnlineParticipantTypeDto.HUMAN,
            participantTypeByPlayer.getValue("player-2"),
        )
        assertEquals(
            OnlineParticipantTypeDto.SYNTHETIC,
            participantTypeByPlayer.getValue("player-3"),
        )
        assertEquals(
            OnlineParticipantTypeDto.SYNTHETIC,
            participantTypeByPlayer.getValue("player-4"),
        )

        val storedMatch = store.snapshotPersistentState().matches.single()
        assertTrue(storedMatch.applicationSeatIndexes.isEmpty())
    }

    @Test
    fun mixed_external_ranked_match_restores_with_canonical_participant_types() {
        var now = 1_000L
        var accountSequence = 0
        val firstStore = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
            accountIdFactory = {
                accountSequence++
                "account-$accountSequence"
            },
        )
        val publicNames = listOf(
            "Jogador Um",
            "Jogador Dois",
            "Jogador Tres",
            "Jogador Quatro",
        )
        val accounts = (1..4).associateWith { index ->
            requireNotNull(
                if (index <= 2) {
                    firstStore.promoteAccount(playerId = "player-$index")
                } else {
                    firstStore.promoteSyntheticAccount(
                        playerId = "player-$index",
                    )
                },
            ).also { account ->
                requireNotNull(
                    firstStore.updateAccountProfile(
                        accountId = account.accountId,
                        publicDisplayName = publicNames[index - 1],
                        tableName = "P0$index",
                    ),
                )
            }
        }

        accounts.forEach { (index, account) ->
            firstStore.enqueuePublicRanked(
                request = CreateOnlineRoomRequestDto(
                    localPlayerId = account.playerId,
                    playerName = "P0$index",
                ),
                identity = account.toRequestIdentity(),
            )
        }
        assertTrue(firstStore.snapshotPersistentState().rooms.isEmpty())

        now = 21_000L
        assertTrue(firstStore.advanceAuthoritativeTime())
        assertEquals(
            PublicRankedQueueStatus.QUEUED,
            firstStore.getPublicRankedQueueStatus(
                identity = accounts.getValue(1).toRequestIdentity(),
            ).status,
        )

        now = 41_000L
        assertTrue(firstStore.advanceAuthoritativeTime())
        val matched = firstStore.getPublicRankedQueueStatus(
            identity = accounts.getValue(1).toRequestIdentity(),
        )
        val originalRoom = requireNotNull(matched.roomSnapshot)
        val persistedState = firstStore.snapshotPersistentState()
        val restartedStore = InMemoryOnlineServerStore(
            nowEpochMillis = { 1_000L },
        )

        restartedStore.restorePersistentState(persistedState)

        assertEquals(
            originalRoom,
            restartedStore.getRoomSnapshot(originalRoom.roomId),
        )
        assertEquals(
            persistedState.accounts.associate { account ->
                account.playerId to account.participantType
            },
            restartedStore.snapshotPersistentState()
                .accounts
                .associate { account ->
                    account.playerId to account.participantType
                },
        )

        val syntheticPlayerIds = persistedState.accounts
            .filter { account ->
                account.participantType ==
                    OnlineParticipantTypeDto.SYNTHETIC
            }
            .mapTo(mutableSetOf()) { account -> account.playerId }
        val divergentState = persistedState.copy(
            rooms = persistedState.rooms.map { room ->
                room.copy(
                    players = room.players.map { player ->
                        if (player.playerId in syntheticPlayerIds) {
                            player.copy(
                                participantType =
                                    OnlineParticipantTypeDto.HUMAN,
                            )
                        } else {
                            player
                        }
                    },
                )
            },
        )
        assertThrows(IllegalArgumentException::class.java) {
            InMemoryOnlineServerStore().restorePersistentState(
                divergentState,
            )
        }
    }

    private fun OnlineServerAccount.toRequestIdentity() =
        OnlineRequestIdentity(
            playerId = playerId,
            principalId = accountId,
            sessionId = "synthetic-test:$playerId",
            kind = OnlinePrincipalKind.ACCOUNT,
            accountId = accountId,
        )
}
