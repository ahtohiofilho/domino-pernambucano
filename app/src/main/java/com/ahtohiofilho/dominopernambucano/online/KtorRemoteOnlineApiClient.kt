package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchResultDto

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class KtorRemoteOnlineApiClient(
    config: OnlineBackendConfig,
    private val httpClient: HttpClient = createDefaultOnlineHttpClient(
        config = config,
    ),
) : RemoteOnlineApiClient {
    private val baseUrl: String =
        requireNotNull(config.baseUrl) {
            "Remote backend baseUrl não configurada."
        }.trimEnd('/')

    private var developmentPlayerId: String? = null
    private var bearerAccessToken: String? = null

    override fun setDevelopmentPlayerId(
        playerId: String?,
    ) {
        developmentPlayerId = playerId
            ?.trim()
            ?.takeIf { value ->
                value.isNotBlank()
            }
    }

    override fun setBearerAccessToken(
        accessToken: String?,
    ) {
        bearerAccessToken = accessToken
            ?.trim()
            ?.takeIf { value ->
                value.isNotBlank()
            }
    }

    override suspend fun createAnonymousSession(): OnlineAnonymousSessionDto {
        return httpClient.post(
            urlString = endpoint(
                OnlineRemoteRoutes.CREATE_ANONYMOUS_SESSION,
            ),
        ) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
        }.body()
    }

    override suspend fun promoteAccount(): OnlineAccountSessionDto {
        return httpClient.post(
            urlString = endpoint(
                OnlineRemoteRoutes.PROMOTE_ACCOUNT,
            ),
        ) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            applyProtectedAuthentication(
                developmentPlayerId = null,
            )
        }.body()
    }

    override suspend fun linkGoogleIdentity(
        request: OnlineGoogleIdentityRequestDto,
        accessToken: String,
    ): OnlineAccountSessionDto {
        val normalizedAccessToken = accessToken.trim()
        require(normalizedAccessToken.isNotBlank()) {
            "A vinculação Google exige uma credencial online válida."
        }

        return executeGoogleIdentityRequest {
            httpClient.post(
                urlString = endpoint(
                    OnlineRemoteRoutes.LINK_GOOGLE_IDENTITY,
                ),
            ) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header(
                    HttpHeaders.Authorization,
                    "Bearer $normalizedAccessToken",
                )
                setBody(request)
            }.body()
        }
    }

    override suspend fun recoverGoogleAccount(
        request: OnlineGoogleIdentityRequestDto,
    ): OnlineAccountSessionDto {
        return executeGoogleIdentityRequest {
            httpClient.post(
                urlString = endpoint(
                    OnlineRemoteRoutes.RECOVER_GOOGLE_ACCOUNT,
                ),
            ) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(request)
            }.body()
        }
    }

    override suspend fun requestEmailCode(
        request: OnlineEmailCodeRequestDto,
    ): OnlineEmailCodeRequestResponseDto {
        return executeEmailIdentityRequest {
            httpClient.post(
                urlString = endpoint(
                    OnlineEmailIdentityRoutes.REQUEST_CODE,
                ),
            ) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(request)
            }.body()
        }
    }

    override suspend fun linkEmailIdentity(
        request: OnlineEmailIdentityRequestDto,
        accessToken: String,
    ): OnlineAccountSessionDto {
        val normalizedAccessToken = accessToken.trim()
        require(normalizedAccessToken.isNotBlank()) {
            "A vinculação por e-mail exige uma credencial online válida."
        }

        return executeEmailIdentityRequest {
            httpClient.post(
                urlString = endpoint(
                    OnlineEmailIdentityRoutes.LINK_IDENTITY,
                ),
            ) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                header(
                    HttpHeaders.Authorization,
                    "Bearer $normalizedAccessToken",
                )
                setBody(request)
            }.body()
        }
    }

    override suspend fun recoverEmailAccount(
        request: OnlineEmailIdentityRequestDto,
    ): OnlineAccountSessionDto {
        return executeEmailIdentityRequest {
            httpClient.post(
                urlString = endpoint(
                    OnlineEmailIdentityRoutes.RECOVER_ACCOUNT,
                ),
            ) {
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                setBody(request)
            }.body()
        }
    }

    override suspend fun enqueuePublicRankedQueue(
        request: PublicRankedQueueEnterRequestDto,
    ): PublicRankedQueueHttpResponseDto {
        return executeRankedQueueRequest(
            acceptedConflict = false,
        ) {
            httpClient.post(
                urlString = endpoint(
                    OnlineRemoteRoutes.RANKED_QUEUE,
                ),
            ) {
                expectSuccess = false
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                applyProtectedAuthentication(
                    developmentPlayerId = null,
                )
                setBody(request)
            }
        }
    }

    override suspend fun fetchPublicRankedQueueStatus():
        PublicRankedQueueHttpResponseDto {
        return executeRankedQueueRequest(
            acceptedConflict = false,
        ) {
            httpClient.get(
                urlString = endpoint(
                    OnlineRemoteRoutes.RANKED_QUEUE,
                ),
            ) {
                expectSuccess = false
                accept(ContentType.Application.Json)
                applyProtectedAuthentication(
                    developmentPlayerId = null,
                )
            }
        }
    }

    override suspend fun cancelPublicRankedQueue():
        PublicRankedQueueHttpResponseDto {
        return executeRankedQueueRequest(
            acceptedConflict = true,
        ) {
            httpClient.delete(
                urlString = endpoint(
                    OnlineRemoteRoutes.RANKED_QUEUE,
                ),
            ) {
                expectSuccess = false
                accept(ContentType.Application.Json)
                applyProtectedAuthentication(
                    developmentPlayerId = null,
                )
            }
        }
    }

    override suspend fun fetchPublicRanking(
        cycle: PublicRankingCycleDto,
        offset: Int,
        limit: Int,
        rankingRevision: String?,
    ): PublicRankingResponseDto {
        return fetchPublicRankingResponse(
            cycle = cycle,
            cycleId = null,
            offset = offset,
            limit = limit,
            rankingRevision = rankingRevision,
            forceRevalidation = false,
        )
    }

    override suspend fun fetchPublicRankingRevalidated(
        cycle: PublicRankingCycleDto,
        offset: Int,
        limit: Int,
        rankingRevision: String?,
    ): PublicRankingResponseDto {
        return fetchPublicRankingResponse(
            cycle = cycle,
            cycleId = null,
            offset = offset,
            limit = limit,
            rankingRevision = rankingRevision,
            forceRevalidation = true,
        )
    }

    override suspend fun fetchHistoricalPublicRanking(
        cycle: PublicRankingCycleDto,
        cycleId: String,
        offset: Int,
        limit: Int,
    ): PublicRankingResponseDto {
        require(cycleId.isNotBlank())

        return fetchPublicRankingResponse(
            cycle = cycle,
            cycleId = cycleId,
            offset = offset,
            limit = limit,
            rankingRevision = null,
            forceRevalidation = false,
        )
    }

    private suspend fun fetchPublicRankingResponse(
        cycle: PublicRankingCycleDto,
        cycleId: String?,
        offset: Int,
        limit: Int,
        rankingRevision: String?,
        forceRevalidation: Boolean,
    ): PublicRankingResponseDto {
        require(offset >= 0)
        require(limit in 1..100)
        require(rankingRevision == null || rankingRevision.isNotBlank())

        val response = httpClient.get(
            urlString = endpoint(
                OnlineRemoteRoutes.RANKING,
            ),
        ) {
            expectSuccess = false
            parameter(
                key = "cycle",
                value = cycle.name,
            )
            parameter(
                key = "offset",
                value = offset,
            )
            parameter(
                key = "limit",
                value = limit,
            )
            cycleId?.let { requestedCycleId ->
                parameter(
                    key = "cycleId",
                    value = requestedCycleId,
                )
            }
            rankingRevision?.let { expectedRevision ->
                parameter(
                    key = "revision",
                    value = expectedRevision,
                )
            }
            if (forceRevalidation) {
                header(
                    HttpHeaders.CacheControl,
                    "no-cache",
                )
            }
            accept(ContentType.Application.Json)
            applyProtectedAuthentication(
                developmentPlayerId = developmentPlayerId,
            )
        }

        if (response.status.value !in 200..299) {
            throw OnlinePublicRankingHttpException(
                statusCode = response.status.value,
            )
        }

        return response.body()
    }

    override suspend fun fetchPublicRankingCycles(
        cycle: PublicRankingCycleDto,
        offset: Int,
        limit: Int,
    ): PublicRankingCyclesResponseDto {
        require(offset >= 0)
        require(limit in 1..100)

        val response = httpClient.get(
            urlString = endpoint(
                OnlineRemoteRoutes.RANKING_CYCLES,
            ),
        ) {
            expectSuccess = false
            parameter(
                key = "cycle",
                value = cycle.name,
            )
            parameter(
                key = "offset",
                value = offset,
            )
            parameter(
                key = "limit",
                value = limit,
            )
            accept(ContentType.Application.Json)
            applyProtectedAuthentication(
                developmentPlayerId = developmentPlayerId,
            )
        }

        if (response.status.value !in 200..299) {
            throw OnlinePublicRankingHttpException(
                statusCode = response.status.value,
            )
        }

        return response.body()
    }

    override suspend fun fetchAccountProfile():
        OnlineAccountProfileResponseDto {
        return executeAccountProfileRequest {
            httpClient.get(
                urlString = endpoint(
                    OnlineRemoteRoutes.ACCOUNT_PROFILE,
                ),
            ) {
                expectSuccess = false
                accept(ContentType.Application.Json)
                applyProtectedAuthentication(
                    developmentPlayerId = null,
                )
            }
        }
    }

    override suspend fun updateAccountProfile(
        request: OnlineAccountProfileUpdateRequestDto,
    ): OnlineAccountProfileResponseDto {
        return executeAccountProfileRequest {
            httpClient.put(
                urlString = endpoint(
                    OnlineRemoteRoutes.ACCOUNT_PROFILE,
                ),
            ) {
                expectSuccess = false
                contentType(ContentType.Application.Json)
                accept(ContentType.Application.Json)
                applyProtectedAuthentication(
                    developmentPlayerId = null,
                )
                setBody(request)
            }
        }
    }

    override suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        setDevelopmentPlayerId(
            playerId = request.localPlayerId,
        )

        return httpClient.post(
            urlString = endpoint(OnlineRemoteRoutes.CREATE_ROOM),
        ) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            applyProtectedAuthentication(
                developmentPlayerId = request.localPlayerId,
            )
            setBody(request)
        }.body()
    }

    override suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        setDevelopmentPlayerId(
            playerId = request.localPlayerId,
        )

        return httpClient.post(
            urlString = endpoint(OnlineRemoteRoutes.JOIN_ROOM),
        ) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            applyProtectedAuthentication(
                developmentPlayerId = request.localPlayerId,
            )
            setBody(request)
        }.body()
    }

    override suspend fun movePrivateRoomSeat(
        request: PrivateRoomSeatChangeRequestDto,
    ): OnlineRoomOperationResultDto {
        setDevelopmentPlayerId(
            playerId = request.localPlayerId,
        )

        return httpClient.post(
            urlString = endpoint(
                OnlineRemoteRoutes.PRIVATE_ROOM_SEAT,
            ),
        ) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            applyProtectedAuthentication(
                developmentPlayerId = request.localPlayerId,
            )
            setBody(request)
        }.body()
    }

    override suspend fun leavePrivateRoom(
        request: PrivateRoomLeaveRequestDto,
    ): OnlineRoomOperationResultDto {
        setDevelopmentPlayerId(
            playerId = request.localPlayerId,
        )

        return httpClient.post(
            urlString = endpoint(
                OnlineRemoteRoutes.PRIVATE_ROOM_LEAVE,
            ),
        ) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            applyProtectedAuthentication(
                developmentPlayerId = request.localPlayerId,
            )
            setBody(request)
        }.body()
    }

    override suspend fun startPrivateRoom(
        request: PrivateRoomStartRequestDto,
    ): OnlineRoomOperationResultDto {
        setDevelopmentPlayerId(
            playerId = request.localPlayerId,
        )

        return httpClient.post(
            urlString = endpoint(
                OnlineRemoteRoutes.PRIVATE_ROOM_START,
            ),
        ) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            applyProtectedAuthentication(
                developmentPlayerId = request.localPlayerId,
            )
            setBody(request)
        }.body()
    }

    override suspend fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto {
        setDevelopmentPlayerId(
            playerId = action.playerId,
        )

        return httpClient.post(
            urlString = endpoint(OnlineRemoteRoutes.SUBMIT_ACTION),
        ) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            applyProtectedAuthentication(
                developmentPlayerId = action.playerId,
            )
            setBody(action)
        }.body()
    }

    override suspend fun submitTraceBatch(
        batch: OnlineTraceBatchDto,
    ): OnlineTraceBatchResultDto {
        return httpClient.post(
            urlString = endpoint(OnlineRemoteRoutes.SUBMIT_TRACE_BATCH),
        ) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            applyProtectedAuthentication(
                developmentPlayerId = developmentPlayerId,
            )
            setBody(batch)
        }.body()
    }

    override suspend fun fetchRoomSnapshot(
        roomId: String,
    ): OnlineRoomSnapshotDto {
        return httpClient.get(
            urlString = endpoint(
                OnlineRemoteRoutes.roomSnapshot(roomId),
            ),
        ) {
            accept(ContentType.Application.Json)
            applyProtectedAuthentication(
                developmentPlayerId = developmentPlayerId,
            )
        }.body()
    }

    override suspend fun fetchMatchSnapshot(
        matchId: String,
    ): OnlineMatchSnapshotDto {
        return httpClient.get(
            urlString = endpoint(
                OnlineRemoteRoutes.matchSnapshot(matchId),
            ),
        ) {
            accept(ContentType.Application.Json)
            applyProtectedAuthentication(
                developmentPlayerId = developmentPlayerId,
            )
        }.body()
    }

    override suspend fun fetchMatchSnapshotsAfter(
        matchId: String,
        afterRevision: Long,
    ): List<OnlineMatchSnapshotDto> {
        return httpClient.get(
            urlString = endpoint(
                OnlineRemoteRoutes.matchSnapshotUpdates(matchId),
            ),
        ) {
            parameter(
                key = "afterRevision",
                value = afterRevision,
            )
            accept(ContentType.Application.Json)
            applyProtectedAuthentication(
                developmentPlayerId = developmentPlayerId,
            )
        }.body()
    }

    private fun HttpRequestBuilder.applyProtectedAuthentication(
        developmentPlayerId: String?,
    ) {
        val accessToken = bearerAccessToken

        if (accessToken != null) {
            header(
                HttpHeaders.Authorization,
                "Bearer $accessToken",
            )
            return
        }

        applyDevelopmentPlayerId(
            playerId = developmentPlayerId,
        )
    }

    private fun HttpRequestBuilder.applyDevelopmentPlayerId(
        playerId: String?,
    ) {
        playerId
            ?.trim()
            ?.takeIf { value ->
                value.isNotBlank()
            }
            ?.let { value ->
                header(
                    OnlineRemoteHeaders.DEVELOPMENT_PLAYER_ID,
                    value,
                )
            }
    }

    private fun endpoint(
        path: String,
    ): String {
        return "$baseUrl/${path.trimStart('/')}"
    }

    private suspend fun executeGoogleIdentityRequest(
        request: suspend () -> OnlineAccountSessionDto,
    ): OnlineAccountSessionDto {
        return try {
            request()
        } catch (error: ResponseException) {
            throw error.toOnlineGoogleIdentityExceptionOrSelf()
        }
    }

    private suspend fun <T> executeEmailIdentityRequest(
        request: suspend () -> T,
    ): T {
        return try {
            request()
        } catch (error: ResponseException) {
            throw error.toOnlineEmailIdentityExceptionOrSelf()
        }
    }

    private suspend fun executeAccountProfileRequest(
        request: suspend () -> HttpResponse,
    ): OnlineAccountProfileResponseDto {
        val response = request()

        if (response.status.value !in 200..299) {
            throw OnlineAccountProfileHttpException(
                statusCode = response.status.value,
            )
        }

        return response.body()
    }

    private suspend fun executeRankedQueueRequest(
        acceptedConflict: Boolean,
        request: suspend () -> HttpResponse,
    ): PublicRankedQueueHttpResponseDto {
        val response = request()
        val statusCode = response.status.value
        val accepted =
            statusCode in 200..299 ||
                    (
                            acceptedConflict &&
                                    response.status == HttpStatusCode.Conflict
                            )

        if (!accepted) {
            throw OnlineRankedQueueHttpException(
                statusCode = statusCode,
            )
        }

        return response.body()
    }
}

private fun createDefaultOnlineHttpClient(
    config: OnlineBackendConfig,
): HttpClient {
    return HttpClient(Android) {
        expectSuccess = true

        install(HttpCache)

        install(HttpTimeout) {
            connectTimeoutMillis = config.connectTimeoutMillis
            requestTimeoutMillis = config.requestTimeoutMillis
        }

        install(ContentNegotiation) {
            json(
                createOnlineJson(),
            )
        }
    }
}

internal fun createOnlineJson(): Json {
    return Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
