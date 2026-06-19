package com.ahtohiofilho.dominopernambucano.online

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
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

    override suspend fun createRoom(
        request: CreateOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        return httpClient.post(
            urlString = endpoint("rooms"),
        ) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun joinRoom(
        request: JoinOnlineRoomRequestDto,
    ): OnlineRoomOperationResultDto {
        return httpClient.post(
            urlString = endpoint("rooms/join"),
        ) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    override suspend fun submitAction(
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto {
        return httpClient.post(
            urlString = endpoint("matches/actions"),
        ) {
            contentType(ContentType.Application.Json)
            accept(ContentType.Application.Json)
            setBody(action)
        }.body()
    }

    override suspend fun fetchRoomSnapshot(
        roomId: String,
    ): OnlineRoomSnapshotDto {
        return httpClient.get(
            urlString = endpoint("rooms/$roomId"),
        ) {
            accept(ContentType.Application.Json)
        }.body()
    }

    override suspend fun fetchMatchSnapshot(
        matchId: String,
    ): OnlineMatchSnapshotDto {
        return httpClient.get(
            urlString = endpoint("matches/$matchId"),
        ) {
            accept(ContentType.Application.Json)
        }.body()
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