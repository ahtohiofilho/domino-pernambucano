package com.ahtohiofilho.dominopernambucano.miniproduction

import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileResponseDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountProfileUpdateRequestDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAccountSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineActionResultDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineMatchSnapshotDto
import com.ahtohiofilho.dominopernambucano.online.OnlinePlayerActionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteRoutes
import com.ahtohiofilho.dominopernambucano.online.OnlineRemoteHeaders
import com.ahtohiofilho.dominopernambucano.online.OnlineSyntheticAccountRecoveryRequestDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueEnterRequestDto
import com.ahtohiofilho.dominopernambucano.online.PublicRankedQueueHttpResponseDto
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal class JavaHttpMiniProductionGateway(
    private val config: MiniProductionClientConfig,
    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(config.requestTimeout)
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) : MiniProductionGateway {
    override fun isReady(): Boolean {
        return runCatching {
            execute(
                method = "GET",
                route = "ready",
                accessToken = null,
                requestBody = null,
            ).statusCode() == 200
        }.getOrDefault(false)
    }

    override fun createAnonymousSession(): OnlineAnonymousSessionDto {
        return decode(
            route = OnlineRemoteRoutes.CREATE_ANONYMOUS_SESSION,
            response = executeSuccessful(
                method = "POST",
                route = OnlineRemoteRoutes.CREATE_ANONYMOUS_SESSION,
            ),
        )
    }

    override fun promoteAccount(
        anonymousAccessToken: String,
    ): OnlineAccountSessionDto {
        return decode(
            route = OnlineRemoteRoutes.PROMOTE_ACCOUNT,
            response = executeSuccessful(
                method = "POST",
                route = OnlineRemoteRoutes.PROMOTE_ACCOUNT,
                accessToken = anonymousAccessToken,
                syntheticProvisioning = true,
            ),
        )
    }

    override fun recoverSyntheticAccount(
        accountId: String,
    ): OnlineAccountSessionDto {
        return decode(
            route = OnlineRemoteRoutes.RECOVER_SYNTHETIC_ACCOUNT,
            response = executeSuccessful(
                method = "POST",
                route = OnlineRemoteRoutes.RECOVER_SYNTHETIC_ACCOUNT,
                requestBody = json.encodeToString(
                    OnlineSyntheticAccountRecoveryRequestDto(accountId),
                ),
                syntheticProvisioning = true,
            ),
        )
    }

    override fun fetchAccountProfile(
        accessToken: String,
    ): OnlineAccountProfileResponseDto {
        return decode(
            route = OnlineRemoteRoutes.ACCOUNT_PROFILE,
            response = executeSuccessful(
                method = "GET",
                route = OnlineRemoteRoutes.ACCOUNT_PROFILE,
                accessToken = accessToken,
            ),
        )
    }

    override fun updateAccountProfile(
        accessToken: String,
        profile: SyntheticProfile,
    ): OnlineAccountProfileResponseDto {
        return decode(
            route = OnlineRemoteRoutes.ACCOUNT_PROFILE,
            response = executeSuccessful(
                method = "PUT",
                route = OnlineRemoteRoutes.ACCOUNT_PROFILE,
                accessToken = accessToken,
                requestBody = json.encodeToString(
                    OnlineAccountProfileUpdateRequestDto(
                        publicDisplayName = profile.publicDisplayName,
                        tableName = profile.tableCode,
                    ),
                ),
            ),
        )
    }

    override fun enterRankedQueue(
        accessToken: String,
        tableCode: String,
    ): PublicRankedQueueHttpResponseDto {
        return decode(
            route = OnlineRemoteRoutes.RANKED_QUEUE,
            response = executeSuccessful(
                method = "POST",
                route = OnlineRemoteRoutes.RANKED_QUEUE,
                accessToken = accessToken,
                requestBody = json.encodeToString(
                    PublicRankedQueueEnterRequestDto(
                        playerName = tableCode,
                    ),
                ),
            ),
        )
    }

    override fun fetchMatchSnapshot(
        accessToken: String,
        matchId: String,
    ): OnlineMatchSnapshotDto {
        val route = OnlineRemoteRoutes.matchSnapshot(matchId)
        return decode(
            route = route,
            response = executeSuccessful(
                method = "GET",
                route = route,
                accessToken = accessToken,
            ),
        )
    }

    override fun submitAction(
        accessToken: String,
        action: OnlinePlayerActionDto,
    ): OnlineActionResultDto {
        return decode(
            route = OnlineRemoteRoutes.SUBMIT_ACTION,
            response = executeSuccessful(
                method = "POST",
                route = OnlineRemoteRoutes.SUBMIT_ACTION,
                accessToken = accessToken,
                requestBody = json.encodeToString(action),
            ),
        )
    }

    private fun executeSuccessful(
        method: String,
        route: String,
        accessToken: String? = null,
        requestBody: String? = null,
        syntheticProvisioning: Boolean = false,
    ): HttpResponse<String> {
        val response = execute(
            method = method,
            route = route,
            accessToken = accessToken,
            requestBody = requestBody,
            syntheticProvisioning = syntheticProvisioning,
        )

        if (response.statusCode() !in 200..299) {
            throw MiniProductionHttpException(
                statusCode = response.statusCode(),
                route = route,
                responseBody = response.body(),
            )
        }

        return response
    }

    private fun execute(
        method: String,
        route: String,
        accessToken: String?,
        requestBody: String?,
        syntheticProvisioning: Boolean = false,
    ): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create("${config.normalizedBaseUrl}/${route.trimStart('/')}"))
            .timeout(config.requestTimeout)
            .header("Accept", "application/json")
            .header("User-Agent", "DominoPE-SyntheticPopulation/1")

        accessToken
            ?.trim()
            ?.takeIf { value -> value.isNotBlank() }
            ?.let { token ->
                builder.header("Authorization", "Bearer $token")
            }

        if (syntheticProvisioning) {
            builder.header(
                OnlineRemoteHeaders.SYNTHETIC_PROVISIONING_SECRET,
                config.syntheticProvisioningSecret,
            )
        }

        if (requestBody != null) {
            builder.header("Content-Type", "application/json")
        }

        when (method) {
            "GET" -> builder.GET()
            "POST" -> builder.POST(
                requestBody
                    ?.let {
                        HttpRequest.BodyPublishers.ofString(
                            it,
                            StandardCharsets.UTF_8,
                        )
                    }
                    ?: HttpRequest.BodyPublishers.noBody(),
            )
            "PUT" -> builder.PUT(
                HttpRequest.BodyPublishers.ofString(
                    requireNotNull(requestBody),
                    StandardCharsets.UTF_8,
                ),
            )
            else -> error("Método HTTP não suportado: $method")
        }

        return try {
            httpClient.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
            )
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        }
    }

    private inline fun <reified T> decode(
        route: String,
        response: HttpResponse<String>,
    ): T {
        return runCatching {
            json.decodeFromString<T>(response.body())
        }.getOrElse { cause ->
            throw IllegalStateException(
                "Resposta JSON inválida em $route.",
                cause,
            )
        }
    }
}
