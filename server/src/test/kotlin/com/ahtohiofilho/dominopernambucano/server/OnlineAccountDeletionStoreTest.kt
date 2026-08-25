package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchPlayerResult
import com.ahtohiofilho.dominopernambucano.competitive.RankedMatchResult
import com.ahtohiofilho.dominopernambucano.competitive.RankingCycleKind
import com.ahtohiofilho.dominopernambucano.competitive.createRankedMatchResultId
import com.ahtohiofilho.dominopernambucano.competitive.resolveRankingCycle
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import java.io.File
import java.nio.file.Files
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineAccountDeletionStoreTest {
    private val json = Json {
        encodeDefaults = true
    }

    @Test
    fun deletion_removes_identifier_from_all_persistent_account_surfaces() {
        val deletedAccountId = "account-delete"
        val deletedPlayerId = "player-delete"
        val accountIds = listOf(
            deletedAccountId,
            "account-2",
            "account-3",
            "account-4",
        )
        val playerIds = listOf(
            deletedPlayerId,
            "player-2",
            "player-3",
            "player-4",
        )
        val completedAt = 1_700_000_000_000L
        val period = resolveRankingCycle(
            kind = RankingCycleKind.DAILY,
            completedAtEpochMillis = completedAt,
        )
        val players = accountIds.indices.map { index ->
            RankedMatchPlayerResult(
                playerId = playerIds[index],
                accountId = accountIds[index],
                seatIndex = index,
                teamIndex = index % 2,
                won = index % 2 == 0,
                victoriesDelta = if (index % 2 == 0) 1 else 0,
                gamesDelta = 1,
                teamBalanceDelta = 0,
                individualPointsScored = index + 1,
                touchesGiven = 0,
                automaticRounds = 0,
            )
        }
        val matchId = "historical-match"
        val rankedResult = RankedMatchResult(
            resultId = createRankedMatchResultId(matchId),
            matchId = matchId,
            rankingRuleVersion = period.rankingRuleVersion,
            completedAtEpochMillis = completedAt,
            finalTeamScores = listOf(6, 3),
            collectiveCountPointsByTeam = listOf(0, 0),
            completedRounds = 1,
            players = players,
        )
        val standingSnapshots = accountIds.mapIndexed { index, accountId ->
            RankedCycleStandingSnapshot(
                rank = index + 1,
                accountId = accountId,
                victories = 1,
                games = 1,
                teamBalance = 0,
                individualPoints = (4 - index).toLong(),
                touchesGiven = 0,
                automaticRounds = 0,
            )
        }
        val closedSnapshot = RankedCycleSnapshot(
            period = period,
            resultCount = 1,
            closedAtEpochMillis = period.endsAtEpochMillis,
            standings = standingSnapshots,
        )
        val auditNonce = "a".repeat(64)
        val formation = PublicRankedFormationHistoryEntry(
            roomId = "historical-room",
            matchId = matchId,
            formedAtEpochMillis = completedAt,
            completedAtEpochMillis = completedAt,
            selectedAccountIdsInQueueOrder = accountIds,
            accountIdsBySeat = accountIds,
            auditNonce = auditNonce,
            auditCommitment = createPublicRankedFormationAuditCommitment(
                roomId = "historical-room",
                matchId = matchId,
                formedAtEpochMillis = completedAt,
                selectedAccountIdsInQueueOrder = accountIds,
                accountIdsBySeat = accountIds,
                auditNonce = auditNonce,
            ),
        )
        val accounts = accountIds.indices.map { index ->
            OnlineServerAccount(
                accountId = accountIds[index],
                playerId = playerIds[index],
                createdAtEpochMillis = 1_000L,
                participantType = OnlineParticipantTypeDto.HUMAN,
            )
        }
        val store = InMemoryOnlineServerStore()

        store.restorePersistentState(
            OnlineServerStoreState(
                accounts = accounts,
                externalIdentities = listOf(
                    OnlineServerExternalIdentity(
                        provider = OnlineExternalIdentityProvider.EMAIL,
                        subject = "delete@example.com",
                        accountId = deletedAccountId,
                        linkedAtEpochMillis = 2_000L,
                    ),
                ),
                rankedResults = listOf(rankedResult),
                rankedCycleSnapshots = listOf(closedSnapshot),
                publicRankedFormationHistory = listOf(formation),
            ),
        )

        assertEquals(
            OnlineAccountDeletionResult.DELETED,
            store.deleteHumanAccount(
                accountId = deletedAccountId,
                playerId = deletedPlayerId,
            ),
        )

        val after = store.snapshotPersistentState()
        val serialized = json.encodeToString(after)

        assertFalse(serialized.contains(deletedAccountId))
        assertFalse(serialized.contains(deletedPlayerId))
        assertFalse(serialized.contains("delete@example.com"))
        assertTrue(after.rankedResults.isEmpty())
        assertTrue(after.publicRankedFormationHistory.isEmpty())
        assertEquals(
            listOf("account-2", "account-3", "account-4"),
            after.rankedCycleSnapshots.single()
                .standings
                .map { standing -> standing.accountId },
        )
        assertEquals(
            listOf(1, 2, 3),
            after.rankedCycleSnapshots.single()
                .standings
                .map { standing -> standing.rank },
        )
    }

    @Test
    fun persistent_store_keeps_account_deleted_after_reopen() {
        val directory = Files.createTempDirectory(
            "domino-account-delete-persistence",
        ).toFile()
        val stateFile = File(directory, "state.json")

        try {
            val first = PersistentOnlineServerStore.open(
                stateFile = stateFile,
                accountIdFactory = { "persistent-account" },
            )
            val account = requireNotNull(
                first.promoteAccount(
                    playerId = "persistent-player",
                ),
            )
            first.close()

            val second = PersistentOnlineServerStore.open(
                stateFile = stateFile,
            )
            assertEquals(
                OnlineAccountDeletionResult.DELETED,
                second.deleteHumanAccount(
                    accountId = account.accountId,
                    playerId = account.playerId,
                ),
            )
            second.close()

            val third = PersistentOnlineServerStore.open(
                stateFile = stateFile,
            )
            assertFalse(
                third.isAccountIdentityActive(
                    accountId = account.accountId,
                    playerId = account.playerId,
                ),
            )
            third.close()
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun synthetic_account_is_never_deleted_by_human_flow() {
        val store = InMemoryOnlineServerStore(
            accountIdFactory = { "synthetic-account" },
        )
        val account = requireNotNull(
            store.promoteSyntheticAccount(
                playerId = "synthetic-player",
            ),
        )

        assertEquals(
            OnlineAccountDeletionResult.FORBIDDEN,
            store.deleteHumanAccount(
                accountId = account.accountId,
                playerId = account.playerId,
            ),
        )
        assertTrue(
            store.isAccountIdentityActive(
                accountId = account.accountId,
                playerId = account.playerId,
            ),
        )
    }
}