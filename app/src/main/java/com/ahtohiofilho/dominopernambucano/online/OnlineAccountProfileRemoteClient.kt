package com.ahtohiofilho.dominopernambucano.online

import io.ktor.client.plugins.HttpRequestTimeoutException
import java.io.IOException
import kotlinx.coroutines.CancellationException

class OnlineAccountProfileHttpException(
    val statusCode: Int,
) : IllegalStateException(
    "O perfil da conta respondeu com HTTP $statusCode.",
)

enum class OnlineAccountProfileFailureKind {
    AUTHENTICATION_REQUIRED,
    ACCOUNT_REQUIRED,
    NOT_ESTABLISHED,
    INVALID_PROFILE,
    RATE_LIMITED,
    UNAVAILABLE,
    PROTOCOL_ERROR,
    UNKNOWN,
}

sealed interface OnlineAccountProfileClientResult {
    data class Success(
        val profile: OnlineAccountProfileResponseDto,
    ) : OnlineAccountProfileClientResult

    data class Failure(
        val kind: OnlineAccountProfileFailureKind,
        val retryable: Boolean,
    ) : OnlineAccountProfileClientResult
}

interface OnlineAccountProfileClient {
    suspend fun fetch(): OnlineAccountProfileClientResult

    suspend fun update(
        publicDisplayName: String,
        tableName: String?,
    ): OnlineAccountProfileClientResult
}

class OnlineAccountProfileRemoteClient(
    private val remoteApiClient: RemoteOnlineApiClient,
    private val sessionCredentialRepository:
        OnlineSessionCredentialRepository,
) : OnlineAccountProfileClient {
    override suspend fun fetch(): OnlineAccountProfileClientResult {
        return execute(
            operation = ProfileOperation.FETCH,
        ) {
            remoteApiClient.fetchAccountProfile()
        }
    }

    override suspend fun update(
        publicDisplayName: String,
        tableName: String?,
    ): OnlineAccountProfileClientResult {
        val request = try {
            val normalizedPublicName =
                normalizeOnlinePublicDisplayName(
                    rawName = publicDisplayName,
                )

            OnlineAccountProfileUpdateRequestDto(
                publicDisplayName = normalizedPublicName,
                tableName = normalizeOnlineAccountTableName(
                    rawName = tableName,
                    publicDisplayName = normalizedPublicName,
                ),
            )
        } catch (_: IllegalArgumentException) {
            return invalidProfileFailure()
        }

        return execute(
            operation = ProfileOperation.UPDATE,
        ) {
            remoteApiClient.updateAccountProfile(
                request = request,
            )
        }
    }

    private suspend fun execute(
        operation: ProfileOperation,
        request: suspend () -> OnlineAccountProfileResponseDto,
    ): OnlineAccountProfileClientResult {
        val credential =
            sessionCredentialRepository.getValidCredentialOrNull()
                ?: return OnlineAccountProfileClientResult.Failure(
                    kind =
                        OnlineAccountProfileFailureKind
                            .AUTHENTICATION_REQUIRED,
                    retryable = false,
                )

        if (
            credential.sessionKind != OnlineSessionKind.ACCOUNT ||
            credential.accountId.isNullOrBlank()
        ) {
            return OnlineAccountProfileClientResult.Failure(
                kind = OnlineAccountProfileFailureKind.ACCOUNT_REQUIRED,
                retryable = false,
            )
        }

        remoteApiClient.setDevelopmentPlayerId(
            playerId = credential.playerId,
        )
        remoteApiClient.setBearerAccessToken(
            accessToken = credential.accessToken,
        )

        return try {
            val response = request()

            response.requireValid(
                operation = operation,
            )

            OnlineAccountProfileClientResult.Success(
                profile = response,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: OnlineAccountProfileHttpException) {
            error.toClientFailure(
                operation = operation,
            )
        } catch (_: HttpRequestTimeoutException) {
            unavailableFailure()
        } catch (_: IOException) {
            unavailableFailure()
        } catch (_: IllegalArgumentException) {
            protocolFailure()
        } catch (_: IllegalStateException) {
            protocolFailure()
        } catch (_: Throwable) {
            OnlineAccountProfileClientResult.Failure(
                kind = OnlineAccountProfileFailureKind.UNKNOWN,
                retryable = false,
            )
        }
    }
}

private enum class ProfileOperation {
    FETCH,
    UPDATE,
}

private fun OnlineAccountProfileResponseDto.requireValid(
    operation: ProfileOperation,
) {
    val normalizedProfile = when (operation) {
        ProfileOperation.FETCH ->
            createLegacyCompatibleOnlineAccountProfile(
                publicDisplayName = publicDisplayName,
                tableName = tableName,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )

        ProfileOperation.UPDATE ->
            createOnlineAccountProfile(
                publicDisplayName = publicDisplayName,
                tableName = tableName,
                updatedAtEpochMillis = updatedAtEpochMillis,
            )
    }

    require(
        normalizedProfile.publicDisplayName ==
            publicDisplayName,
    )
    require(normalizedProfile.tableName == tableName)
    require(
        normalizedProfile.updatedAtEpochMillis ==
            updatedAtEpochMillis,
    )
}

private fun OnlineAccountProfileHttpException.toClientFailure(
    operation: ProfileOperation,
): OnlineAccountProfileClientResult.Failure {
    return when (statusCode) {
        400 -> {
            if (operation == ProfileOperation.UPDATE) {
                invalidProfileFailure()
            } else {
                protocolFailure()
            }
        }

        401 -> OnlineAccountProfileClientResult.Failure(
            kind =
                OnlineAccountProfileFailureKind
                    .AUTHENTICATION_REQUIRED,
            retryable = false,
        )

        403 -> OnlineAccountProfileClientResult.Failure(
            kind = OnlineAccountProfileFailureKind.ACCOUNT_REQUIRED,
            retryable = false,
        )

        404 -> {
            if (operation == ProfileOperation.FETCH) {
                OnlineAccountProfileClientResult.Failure(
                    kind =
                        OnlineAccountProfileFailureKind
                            .NOT_ESTABLISHED,
                    retryable = false,
                )
            } else {
                protocolFailure()
            }
        }

        429 -> OnlineAccountProfileClientResult.Failure(
            kind = OnlineAccountProfileFailureKind.RATE_LIMITED,
            retryable = true,
        )

        in 500..599 -> unavailableFailure()

        else -> protocolFailure()
    }
}

private fun invalidProfileFailure():
    OnlineAccountProfileClientResult.Failure {
    return OnlineAccountProfileClientResult.Failure(
        kind = OnlineAccountProfileFailureKind.INVALID_PROFILE,
        retryable = false,
    )
}

private fun unavailableFailure():
    OnlineAccountProfileClientResult.Failure {
    return OnlineAccountProfileClientResult.Failure(
        kind = OnlineAccountProfileFailureKind.UNAVAILABLE,
        retryable = true,
    )
}

private fun protocolFailure():
    OnlineAccountProfileClientResult.Failure {
    return OnlineAccountProfileClientResult.Failure(
        kind = OnlineAccountProfileFailureKind.PROTOCOL_ERROR,
        retryable = false,
    )
}
