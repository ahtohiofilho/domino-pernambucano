package com.ahtohiofilho.dominopernambucano.online

import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchDto
import com.ahtohiofilho.dominopernambucano.online.observability.OnlineTraceBatchResultDto

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
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
}

private fun createDefaultOnlineHttpClient(
    config: OnlineBackendConfig,
): HttpClient {
    return HttpClient(Android) {
        expectSuccess = true

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