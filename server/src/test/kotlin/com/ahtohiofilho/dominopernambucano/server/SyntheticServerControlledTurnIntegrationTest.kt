package com.ahtohiofilho.dominopernambucano.server

import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchTiming
import com.ahtohiofilho.dominopernambucano.match.decrementPlayerClockMillis
import com.ahtohiofilho.dominopernambucano.match.findBasicBotMove
import com.ahtohiofilho.dominopernambucano.match.reloadPlayerClockFromReserveMillis
import com.ahtohiofilho.dominopernambucano.online.CreateOnlineRoomRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipantTypeDto
import com.ahtohiofilho.dominopernambucano.online.createOnlinePassTurnAction
import com.ahtohiofilho.dominopernambucano.online.createOnlinePlayMoveAction
import com.ahtohiofilho.dominopernambucano.online.toRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticServerControlledTurnIntegrationTest {
    @Test
    fun matched_synthetic_waits_for_and_accepts_participant_action() {
        var now = 1_000L
        var accountSequence = 0

        val store = InMemoryOnlineServerStore(
            nowEpochMillis = { now },
            resourcePolicy = OnlineServerStoreResourcePolicy(
                publicRankedSyntheticFallbackInitialDelayMillis = 20_000L,
                publicRankedSyntheticFallbackAdditionalSeatDelayMillis = 20_000L,
                publicRankedExactCohortCooldownMillis = 0L,
                pruneIntervalMillis = 60_000L,
            ),
            accountIdFactory = {
                accountSequence += 1
                "account-$accountSequence"
            },
            publicRankedFormationEntropy = ZeroEntropy(),
        )

        val synthetics = (1..3).map { number ->
            requireNotNull(
                store.promoteSyntheticAccount(
                    playerId = "synthetic-$number",
                    expectedAccountId = null,
                ),
            )
        }
        val human = requireNotNull(
            store.promoteAccount(
                playerId = "human-1",
            ),
        )

        synthetics.forEachIndexed { index, account ->
            val tableCode = "S0${index + 1}"
            configureAccount(
                store = store,
                account = account,
                tableCode = tableCode,
            )
            val queued = store.enqueuePublicRanked(
                request = request(
                    playerId = account.playerId,
                    tableCode = tableCode,
                ),
                identity = account.toRequestIdentity(),
            )
            assertEquals(
                PublicRankedQueueStatus.QUEUED,
                queued.status,
            )
        }

        configureAccount(
            store = store,
            account = human,
            tableCode = "H01",
        )
        assertEquals(
            PublicRankedQueueStatus.QUEUED,
            store.enqueuePublicRanked(
                request = request(
                    playerId = human.playerId,
                    tableCode = "H01",
                ),
                identity = human.toRequestIdentity(),
            ).status,
        )

        now = 21_000L
        assertTrue(store.advanceAuthoritativeTime())
        assertEquals(
            PublicRankedQueueStatus.QUEUED,
            store.getPublicRankedQueueStatus(
                human.toRequestIdentity(),
            ).status,
        )

        now = 41_000L
        assertTrue(store.advanceAuthoritativeTime())
        assertEquals(
            PublicRankedQueueStatus.QUEUED,
            store.getPublicRankedQueueStatus(
                human.toRequestIdentity(),
            ).status,
        )

        now = 61_000L
        assertTrue(store.advanceAuthoritativeTime())

        val matched = store.getPublicRankedQueueStatus(
            human.toRequestIdentity(),
        )
        assertEquals(
            PublicRankedQueueStatus.MATCHED,
            matched.status,
        )

        val room = requireNotNull(matched.roomSnapshot)
        val matchId = requireNotNull(room.matchId)

        /*
         * Drive only the single HUMAN when necessary. As soon as a SYNTHETIC
         * owns the playable turn, no action is submitted on that participant's
         * behalf: advanceAuthoritativeTime() alone must publish the transition.
         */
        repeat(128) { step ->
            val before = requireNotNull(
                store.getMatchSnapshot(matchId),
            )
            val runtime = before.toRuntimeState(
                localPlayerIndex =
                    before.gameState.currentPlayerIndex,
            )

            if (runtime.phase == DominoMatchPhase.RoundIntro) {
                releaseRoundIntroForTest(
                    store = store,
                    roomId = room.roomId,
                    matchId = matchId,
                )
                return@repeat
            }

            if (
                runtime.phase ==
                    DominoMatchPhase.WaitingForLocalMove
            ) {
                val currentSeat =
                    before.gameState.currentPlayerIndex
                val currentPlayer = requireNotNull(
                    room.players.firstOrNull { player ->
                        player.seatIndex == currentSeat
                    },
                )

                if (
                    currentPlayer.participantType ==
                        OnlineParticipantTypeDto.SYNTHETIC
                ) {
                    val syntheticMove = findBasicBotMove(
                        state = runtime.gameState,
                    )
                    val beforeRevision = before.revision
                    val beforeGameState = before.gameState

                    assertFalse(
                        "Synthetic identity must not be moved by the server immediately.",
                        store.advanceAuthoritativeTime(),
                    )

                    now +=
                        DominoMatchTiming.BotDecisionDelayMillis

                    assertFalse(
                        "Synthetic identity must still wait for its participant action after bot cadence.",
                        store.advanceAuthoritativeTime(),
                    )

                    val result = if (syntheticMove != null) {
                        store.submitAction(
                            createOnlinePlayMoveAction(
                                roomId = room.roomId,
                                matchId = matchId,
                                playerId = currentPlayer.playerId,
                                revision = beforeRevision,
                                move = syntheticMove,
                                actionId =
                                    "synthetic-participant-$step-$beforeRevision",
                            ),
                        )
                    } else {
                        store.submitAction(
                            createOnlinePassTurnAction(
                                roomId = room.roomId,
                                matchId = matchId,
                                playerId = currentPlayer.playerId,
                                revision = beforeRevision,
                                actionId =
                                    "synthetic-participant-pass-$step-$beforeRevision",
                            ),
                        )
                    }

                    assertTrue(
                        "Synthetic participant action was rejected: ${result.reason}",
                        result.accepted,
                    )

                    val after = requireNotNull(
                        store.getMatchSnapshot(matchId),
                    )

                    assertTrue(
                        "Synthetic participant action did not publish a new revision.",
                        after.revision > beforeRevision,
                    )
                    assertTrue(
                        "Synthetic participant action did not change authoritative state.",
                        after.gameState != beforeGameState,
                    )
                    assertTrue(
                        "Normal synthetic action must not become a timed-out human.",
                        currentSeat !in after.automaticPlayerIndexes,
                    )
                    return
                }

                assertEquals(
                    OnlineParticipantTypeDto.HUMAN,
                    currentPlayer.participantType,
                )

                val move = findBasicBotMove(
                    state = runtime.gameState,
                )
                val result = if (move != null) {
                    store.submitAction(
                        createOnlinePlayMoveAction(
                            roomId = room.roomId,
                            matchId = matchId,
                            playerId = currentPlayer.playerId,
                            revision = before.revision,
                            move = move,
                            actionId =
                                "synthetic-control-human-$step-${before.revision}",
                        ),
                    )
                } else {
                    store.submitAction(
                        createOnlinePassTurnAction(
                            roomId = room.roomId,
                            matchId = matchId,
                            playerId = currentPlayer.playerId,
                            revision = before.revision,
                            actionId =
                                "synthetic-control-pass-$step-${before.revision}",
                        ),
                    )
                }

                assertTrue(
                    "Human setup action was rejected: ${result.reason}",
                    result.accepted,
                )
            } else {
                assertTrue(
                    "Authoritative presentation phase did not progress.",
                    store.advanceAuthoritativeTime(),
                )
            }
        }

        error(
            "No playable SYNTHETIC move was reached within the test bound.",
        )
    }


    private fun playUntilOneRankedRoundCompletes(
        store: InMemoryOnlineServerStore,
        roomId: String,
        matchId: String,
        roomPlayers:
            List<com.ahtohiofilho.dominopernambucano.online.OnlineRoomPlayerDto>,
        advanceSyntheticDecisionClock: () -> Unit,
    ) {
        repeat(512) { step ->
            val accumulator = requireNotNull(
                store.getRankedMatchMetricAccumulator(matchId),
            )
            if (accumulator.completedRounds >= 1) {
                return
            }

            val snapshot = requireNotNull(
                store.getMatchSnapshot(matchId),
            )
            val runtime = snapshot.toRuntimeState(
                localPlayerIndex =
                    snapshot.gameState.currentPlayerIndex,
            )

            when {
                runtime.phase == DominoMatchPhase.RoundIntro -> {
                    releaseRoundIntroForTest(
                        store = store,
                        roomId = roomId,
                        matchId = matchId,
                    )
                }

                runtime.phase ==
                    DominoMatchPhase.WaitingForLocalMove -> {
                    val seat = snapshot.gameState.currentPlayerIndex
                    val player = requireNotNull(
                        roomPlayers.firstOrNull { candidate ->
                            candidate.seatIndex == seat
                        },
                    )

                    if (
                        player.participantType ==
                            OnlineParticipantTypeDto.SYNTHETIC
                    ) {
                        advanceSyntheticDecisionClock()
                        assertTrue(
                            store.advanceAuthoritativeTime(),
                        )
                    } else {
                        assertEquals(
                            OnlineParticipantTypeDto.HUMAN,
                            player.participantType,
                        )
                        val move = findBasicBotMove(
                            state = runtime.gameState,
                        )
                        val result = if (move != null) {
                            store.submitAction(
                                createOnlinePlayMoveAction(
                                    roomId = roomId,
                                    matchId = matchId,
                                    playerId = player.playerId,
                                    revision = snapshot.revision,
                                    move = move,
                                    actionId =
                                        "metric-human-$step-${snapshot.revision}",
                                ),
                            )
                        } else {
                            store.submitAction(
                                createOnlinePassTurnAction(
                                    roomId = roomId,
                                    matchId = matchId,
                                    playerId = player.playerId,
                                    revision = snapshot.revision,
                                    actionId =
                                        "metric-pass-$step-${snapshot.revision}",
                                ),
                            )
                        }
                        assertTrue(result.accepted)
                    }
                }

                runtime.phase is DominoMatchPhase.PresentingPass -> {
                    assertTrue(store.advanceAuthoritativeTime())
                }

                runtime.phase == DominoMatchPhase.RoundSummary ||
                    runtime.phase == DominoMatchPhase.MatchFinished -> {
                    val finalAccumulator = requireNotNull(
                        store.getRankedMatchMetricAccumulator(matchId),
                    )
                    assertTrue(finalAccumulator.completedRounds >= 1)
                    return
                }

                else -> {
                    assertTrue(store.advanceAuthoritativeTime())
                }
            }
        }

        error("Ranked round did not complete within the test bound.")
    }
    private fun configureAccount(
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
            sessionId = "synthetic-turn-session:$playerId",
            kind = OnlinePrincipalKind.ACCOUNT,
            accountId = accountId,
        )

    private class ZeroEntropy : PublicRankedFormationEntropy {
        override fun nextInt(bound: Int): Int {
            require(bound > 0)
            return 0
        }

        override fun nextNonceHex(byteCount: Int): String {
            require(byteCount > 0)
            return "cd".repeat(byteCount)
        }
    }
}