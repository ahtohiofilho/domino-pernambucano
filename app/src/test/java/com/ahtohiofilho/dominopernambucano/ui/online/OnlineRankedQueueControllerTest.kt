package com.ahtohiofilho.dominopernambucano.ui.online

import com.ahtohiofilho.dominopernambucano.domain.createInitialDominoGameState
import com.ahtohiofilho.dominopernambucano.match.DominoMatchPhase
import com.ahtohiofilho.dominopernambucano.match.DominoMatchRuntimeState
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankedMatchActivation
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankedMatchActivationFailureKind
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueClient
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueClientResult
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueFailureKind
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueuePollingPolicy
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueState
import com.ahtohiofilho.dominopernambucano.online.toOnlineSnapshotDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineRankedQueueControllerTest {
    @Test
    fun open_is_get_first_then_enqueues_and_activates_matched_result() =
        runBlocking {
            val client = FakeQueueClient(
                resumeResult = success(
                    OnlineRankedQueueState.NotQueued,
                ),
                enqueueResult = success(
                    OnlineRankedQueueState.Waiting(
                        queuePosition = 3,
                    ),
                ),
                pollingResults = listOf(
                    success(
                        OnlineRankedQueueState.Matched(
                            matchId = "match-1",
                            localSeatIndex = 2,
                        ),
                    ),
                ),
            )
            val activation = readyActivation(
                matchId = "match-1",
                localSeatIndex = 2,
            )
            val controller = OnlineRankedQueueController(
                queueClient = client,
                activateMatch = { _, _ -> activation },
            )

            controller.open(
                playerName = "AFI",
            )

            assertEquals(
                listOf("resume", "enqueue:AFI", "poll"),
                client.calls,
            )
            assertEquals(
                OnlineRankedQueueUiState.MatchReady(activation),
                controller.state.value,
            )
        }

    @Test
    fun existing_waiting_queue_is_resumed_without_duplicate_enqueue() =
        runBlocking {
            val client = FakeQueueClient(
                resumeResult = success(
                    OnlineRankedQueueState.Waiting(
                        queuePosition = 1,
                    ),
                ),
                pollingResults = listOf(
                    success(OnlineRankedQueueState.NotQueued),
                ),
            )
            val controller = OnlineRankedQueueController(
                queueClient = client,
                activateMatch = { _, _ ->
                    error("Activation was not expected.")
                },
            )

            controller.open("AFI")

            assertEquals(
                listOf("resume", "poll"),
                client.calls,
            )
            assertEquals(
                OnlineRankedQueueUiState.NotQueued,
                controller.state.value,
            )
        }

    @Test
    fun explicit_cancel_returns_to_cancelled_state() = runBlocking {
        val client = FakeQueueClient(
            cancelResult = success(
                OnlineRankedQueueState.NotQueued,
            ),
        )
        val controller = OnlineRankedQueueController(
            queueClient = client,
            activateMatch = { _, _ ->
                error("Activation was not expected.")
            },
        )

        controller.cancel()

        assertEquals(listOf("cancel"), client.calls)
        assertEquals(
            OnlineRankedQueueUiState.Cancelled,
            controller.state.value,
        )
    }

    @Test
    fun unavailable_cancel_keeps_retryable_failure_state() =
        runBlocking {
            val client = FakeQueueClient(
                cancelResult = OnlineRankedQueueClientResult.Failure(
                    kind = OnlineRankedQueueFailureKind.UNAVAILABLE,
                    retryable = true,
                ),
            )
            val controller = OnlineRankedQueueController(
                queueClient = client,
                activateMatch = { _, _ ->
                    error("Activation was not expected.")
                },
            )

            controller.cancel()

            assertEquals(listOf("cancel"), client.calls)
            assertEquals(
                OnlineRankedQueueUiState.Failure(
                    kind =
                        OnlineRankedQueueUiFailureKind.UNAVAILABLE,
                    retryable = true,
                ),
                controller.state.value,
            )
        }

    @Test
    fun invalid_ranked_activation_is_blocked_before_navigation() =
        runBlocking {
            val client = FakeQueueClient(
                resumeResult = success(
                    OnlineRankedQueueState.Matched(
                        matchId = "match-invalid",
                        localSeatIndex = 0,
                    ),
                ),
            )
            val controller = OnlineRankedQueueController(
                queueClient = client,
                activateMatch = { _, _ ->
                    OnlinePublicRankedMatchActivation.Failure(
                        kind =
                            OnlinePublicRankedMatchActivationFailureKind
                                .INVALID_MATCH,
                    )
                },
            )

            controller.open("AFI")

            assertEquals(
                OnlineRankedQueueUiState.Failure(
                    kind =
                        OnlineRankedQueueUiFailureKind.INVALID_MATCH,
                    retryable = false,
                ),
                controller.state.value,
            )
        }

    private fun success(
        state: OnlineRankedQueueState,
    ): OnlineRankedQueueClientResult {
        return OnlineRankedQueueClientResult.Success(
            state = state,
        )
    }

    private fun readyActivation(
        matchId: String,
        localSeatIndex: Int,
    ): OnlinePublicRankedMatchActivation.Ready {
        val runtimeState = DominoMatchRuntimeState(
            gameState = createInitialDominoGameState(),
            roundNumber = 1,
            localPlayerIndex = localSeatIndex,
            phase = DominoMatchPhase.RoundIntro,
        )

        return OnlinePublicRankedMatchActivation.Ready(
            playerId = "player-1",
            roomId = "room-1",
            matchId = matchId,
            localSeatIndex = localSeatIndex,
            initialSnapshot = runtimeState.toOnlineSnapshotDto(
                roomId = "room-1",
                matchId = matchId,
                revision = 1L,
            ),
        )
    }

    private class FakeQueueClient(
        private val resumeResult: OnlineRankedQueueClientResult =
            successStatic(OnlineRankedQueueState.NotQueued),
        private val enqueueResult: OnlineRankedQueueClientResult =
            successStatic(OnlineRankedQueueState.NotQueued),
        private val cancelResult: OnlineRankedQueueClientResult =
            successStatic(OnlineRankedQueueState.NotQueued),
        private val pollingResults:
            List<OnlineRankedQueueClientResult> = emptyList(),
    ) : OnlineRankedQueueClient {
        val calls = mutableListOf<String>()

        override suspend fun enqueue(
            playerName: String,
        ): OnlineRankedQueueClientResult {
            calls += "enqueue:$playerName"
            return enqueueResult
        }

        override suspend fun resume(): OnlineRankedQueueClientResult {
            calls += "resume"
            return resumeResult
        }

        override suspend fun cancel(): OnlineRankedQueueClientResult {
            calls += "cancel"
            return cancelResult
        }

        override fun poll(
            policy: OnlineRankedQueuePollingPolicy,
        ): Flow<OnlineRankedQueueClientResult> {
            calls += "poll"
            return flowOf(*pollingResults.toTypedArray())
        }
    }

    companion object {
        private fun successStatic(
            state: OnlineRankedQueueState,
        ): OnlineRankedQueueClientResult {
            return OnlineRankedQueueClientResult.Success(
                state = state,
            )
        }
    }
}
