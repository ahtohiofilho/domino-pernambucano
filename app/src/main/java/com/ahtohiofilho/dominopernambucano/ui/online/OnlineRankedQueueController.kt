package com.ahtohiofilho.dominopernambucano.ui.online

import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankedMatchActivation
import com.ahtohiofilho.dominopernambucano.online.OnlinePublicRankedMatchActivationFailureKind
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueClient
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueClientResult
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueFailureKind
import com.ahtohiofilho.dominopernambucano.online.OnlineRankedQueueState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect

enum class OnlineRankedQueueUiFailureKind {
    AUTHENTICATION_REQUIRED,
    ACCOUNT_REQUIRED,
    RATE_LIMITED,
    UNAVAILABLE,
    PROTOCOL_ERROR,
    SESSION_REJECTED,
    INVALID_MATCH,
    UNKNOWN,
}

sealed interface OnlineRankedQueueUiState {
    data object Idle : OnlineRankedQueueUiState
    data object Resuming : OnlineRankedQueueUiState

    data class Waiting(
        val queuePosition: Int,
    ) : OnlineRankedQueueUiState

    data object Cancelling : OnlineRankedQueueUiState
    data object OpeningMatch : OnlineRankedQueueUiState

    data class MatchReady(
        val activation: OnlinePublicRankedMatchActivation.Ready,
    ) : OnlineRankedQueueUiState

    data object Cancelled : OnlineRankedQueueUiState
    data object NotQueued : OnlineRankedQueueUiState

    data class Failure(
        val kind: OnlineRankedQueueUiFailureKind,
        val retryable: Boolean,
    ) : OnlineRankedQueueUiState
}

class OnlineRankedQueueController(
    private val queueClient: OnlineRankedQueueClient,
    private val activateMatch:
        suspend (matchId: String, localSeatIndex: Int) ->
            OnlinePublicRankedMatchActivation,
) {
    private val mutableState =
        MutableStateFlow<OnlineRankedQueueUiState>(
            OnlineRankedQueueUiState.Idle,
        )

    val state: StateFlow<OnlineRankedQueueUiState> =
        mutableState.asStateFlow()

    suspend fun open(
        playerName: String,
    ) {
        mutableState.value = OnlineRankedQueueUiState.Resuming

        try {
            val resumed = queueClient.resume()
            val initial = if (
                resumed is OnlineRankedQueueClientResult.Success &&
                resumed.state is OnlineRankedQueueState.NotQueued
            ) {
                queueClient.enqueue(
                    playerName = playerName,
                )
            } else {
                resumed
            }

            val shouldPoll = applyClientResult(initial)
            if (!shouldPoll) {
                return
            }

            queueClient.poll().collect { result ->
                applyClientResult(result)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            mutableState.value = OnlineRankedQueueUiState.Failure(
                kind = OnlineRankedQueueUiFailureKind.UNKNOWN,
                retryable = false,
            )
        }
    }

    suspend fun cancel() {
        mutableState.value = OnlineRankedQueueUiState.Cancelling

        try {
            when (val result = queueClient.cancel()) {
                is OnlineRankedQueueClientResult.Success -> {
                    when (val state = result.state) {
                        OnlineRankedQueueState.NotQueued -> {
                            mutableState.value =
                                OnlineRankedQueueUiState.Cancelled
                        }

                        is OnlineRankedQueueState.Matched -> {
                            activateMatchedState(state)
                        }

                        is OnlineRankedQueueState.Waiting -> {
                            mutableState.value =
                                OnlineRankedQueueUiState.Failure(
                                    kind =
                                        OnlineRankedQueueUiFailureKind
                                            .PROTOCOL_ERROR,
                                    retryable = false,
                                )
                        }
                    }
                }

                is OnlineRankedQueueClientResult.Failure -> {
                    mutableState.value = result.toUiFailure()
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            mutableState.value = OnlineRankedQueueUiState.Failure(
                kind = OnlineRankedQueueUiFailureKind.UNKNOWN,
                retryable = false,
            )
        }
    }

    private suspend fun applyClientResult(
        result: OnlineRankedQueueClientResult,
    ): Boolean {
        return when (result) {
            is OnlineRankedQueueClientResult.Success -> {
                when (val state = result.state) {
                    is OnlineRankedQueueState.Waiting -> {
                        mutableState.value =
                            OnlineRankedQueueUiState.Waiting(
                                queuePosition = state.queuePosition,
                            )
                        true
                    }

                    is OnlineRankedQueueState.Matched -> {
                        activateMatchedState(state)
                        false
                    }

                    OnlineRankedQueueState.NotQueued -> {
                        mutableState.value =
                            OnlineRankedQueueUiState.NotQueued
                        false
                    }
                }
            }

            is OnlineRankedQueueClientResult.Failure -> {
                mutableState.value = result.toUiFailure()
                false
            }
        }
    }

    private suspend fun activateMatchedState(
        matched: OnlineRankedQueueState.Matched,
    ) {
        mutableState.value = OnlineRankedQueueUiState.OpeningMatch

        mutableState.value = when (
            val activation = activateMatch(
                matched.matchId,
                matched.localSeatIndex
                    ?: UNREVEALED_RANKED_SEAT_INDEX,
            )
        ) {
            is OnlinePublicRankedMatchActivation.Ready -> {
                OnlineRankedQueueUiState.MatchReady(
                    activation = activation,
                )
            }

            is OnlinePublicRankedMatchActivation.Failure -> {
                activation.toUiFailure()
            }
        }
    }
}

private const val UNREVEALED_RANKED_SEAT_INDEX = -1

private fun OnlineRankedQueueClientResult.Failure.toUiFailure():
    OnlineRankedQueueUiState.Failure {
    val uiKind = when (kind) {
        OnlineRankedQueueFailureKind.AUTHENTICATION_REQUIRED ->
            OnlineRankedQueueUiFailureKind.AUTHENTICATION_REQUIRED

        OnlineRankedQueueFailureKind.ACCOUNT_REQUIRED ->
            OnlineRankedQueueUiFailureKind.ACCOUNT_REQUIRED

        OnlineRankedQueueFailureKind.RATE_LIMITED ->
            OnlineRankedQueueUiFailureKind.RATE_LIMITED

        OnlineRankedQueueFailureKind.UNAVAILABLE ->
            OnlineRankedQueueUiFailureKind.UNAVAILABLE

        OnlineRankedQueueFailureKind.PROTOCOL_ERROR ->
            OnlineRankedQueueUiFailureKind.PROTOCOL_ERROR

        OnlineRankedQueueFailureKind.UNKNOWN ->
            OnlineRankedQueueUiFailureKind.UNKNOWN
    }

    return OnlineRankedQueueUiState.Failure(
        kind = uiKind,
        retryable = retryable,
    )
}

private fun OnlinePublicRankedMatchActivation.Failure.toUiFailure():
    OnlineRankedQueueUiState.Failure {
    val uiKind = when (kind) {
        OnlinePublicRankedMatchActivationFailureKind
            .AUTHENTICATION_REQUIRED ->
            OnlineRankedQueueUiFailureKind.AUTHENTICATION_REQUIRED

        OnlinePublicRankedMatchActivationFailureKind.ACCOUNT_REQUIRED ->
            OnlineRankedQueueUiFailureKind.ACCOUNT_REQUIRED

        OnlinePublicRankedMatchActivationFailureKind.SESSION_REJECTED ->
            OnlineRankedQueueUiFailureKind.SESSION_REJECTED

        OnlinePublicRankedMatchActivationFailureKind.INVALID_MATCH ->
            OnlineRankedQueueUiFailureKind.INVALID_MATCH

        OnlinePublicRankedMatchActivationFailureKind.UNAVAILABLE ->
            OnlineRankedQueueUiFailureKind.UNAVAILABLE
    }

    return OnlineRankedQueueUiState.Failure(
        kind = uiKind,
        retryable =
            kind ==
                OnlinePublicRankedMatchActivationFailureKind.UNAVAILABLE,
    )
}
