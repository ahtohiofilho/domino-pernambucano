package com.ahtohiofilho.dominopernambucano.server

import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal const val PLAY_GAMES_WEB_CLIENT_ID_ENVIRONMENT_VARIABLE =
    "DOMINO_PLAY_GAMES_WEB_CLIENT_ID"
internal const val PLAY_GAMES_WEB_CLIENT_SECRET_ENVIRONMENT_VARIABLE =
    "DOMINO_PLAY_GAMES_WEB_CLIENT_SECRET"

private const val GOOGLE_OAUTH_TOKEN_ENDPOINT =
    "https://oauth2.googleapis.com/token"
private const val GOOGLE_PLAY_GAMES_CURRENT_PLAYER_ENDPOINT =
    "https://games.googleapis.com/games/v1/players/me"
private const val MAX_PLAY_GAMES_AUTH_CODE_CHARACTERS = 8_192
private const val MAX_PLAY_GAMES_ACCESS_TOKEN_CHARACTERS = 16_384
private const val MAX_PLAY_GAMES_PLAYER_ID_CHARACTERS = 1_024
private const val MAX_PLAY_GAMES_RESPONSE_CHARACTERS = 65_536
private val PLAY_GAMES_JSON = Json {
    ignoreUnknownKeys = true
}

sealed interface OnlinePlayGamesIdentityVerificationResult {
    data class Verified(
        val playerId: String,
    ) : OnlinePlayGamesIdentityVerificationResult

    data object Invalid : OnlinePlayGamesIdentityVerificationResult

    data object Unavailable : OnlinePlayGamesIdentityVerificationResult
}

fun interface OnlinePlayGamesIdentityVerifier {
    suspend fun verify(
        serverAuthCode: String,
    ): OnlinePlayGamesIdentityVerificationResult
}

internal data class OnlinePlayGamesHttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

internal data class OnlinePlayGamesHttpResponse(
    val statusCode: Int,
    val body: String,
)

internal fun interface OnlinePlayGamesHttpTransport {
    @Throws(IOException::class, InterruptedException::class)
    fun execute(
        request: OnlinePlayGamesHttpRequest,
    ): OnlinePlayGamesHttpResponse
}

internal class JdkOnlinePlayGamesHttpTransport(
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10L))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build(),
) : OnlinePlayGamesHttpTransport {
    override fun execute(
        request: OnlinePlayGamesHttpRequest,
    ): OnlinePlayGamesHttpResponse {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(request.url))
            .timeout(Duration.ofSeconds(10L))

        request.headers.forEach { (name, value) ->
            builder.header(name, value)
        }

        when (request.method) {
            "GET" -> builder.GET()
            "POST" -> builder.POST(
                HttpRequest.BodyPublishers.ofString(
                    request.body.orEmpty(),
                    StandardCharsets.UTF_8,
                ),
            )
            else -> throw IllegalArgumentException(
                "Método HTTP não suportado: ${request.method}",
            )
        }

        val response = httpClient.send(
            builder.build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
        )

        return OnlinePlayGamesHttpResponse(
            statusCode = response.statusCode(),
            body = response.body(),
        )
    }
}

internal class GooglePlayGamesServerIdentityVerifier(
    webClientId: String,
    webClientSecret: String,
    private val transport: OnlinePlayGamesHttpTransport =
        JdkOnlinePlayGamesHttpTransport(),
) : OnlinePlayGamesIdentityVerifier {
    private val webClientId = webClientId.trim().also { value ->
        require(value.isNotBlank()) {
            "O OAuth Web Client ID do Play Games não pode ser vazio."
        }
    }
    private val webClientSecret = webClientSecret.trim().also { value ->
        require(value.isNotBlank()) {
            "O OAuth Web Client Secret do Play Games não pode ser vazio."
        }
    }

    override suspend fun verify(
        serverAuthCode: String,
    ): OnlinePlayGamesIdentityVerificationResult =
        withContext(Dispatchers.IO) {
            val normalizedCode = serverAuthCode.trim()

            if (
                normalizedCode.isBlank() ||
                normalizedCode.length >
                    MAX_PLAY_GAMES_AUTH_CODE_CHARACTERS
            ) {
                return@withContext OnlinePlayGamesIdentityVerificationResult.Invalid
            }

            try {
                verifyBlocking(normalizedCode)
            } catch (_: IOException) {
                OnlinePlayGamesIdentityVerificationResult.Unavailable
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                OnlinePlayGamesIdentityVerificationResult.Unavailable
            } catch (_: Exception) {
                OnlinePlayGamesIdentityVerificationResult.Unavailable
            }
        }

    private fun verifyBlocking(
        serverAuthCode: String,
    ): OnlinePlayGamesIdentityVerificationResult {
        val tokenResponse = transport.execute(
            OnlinePlayGamesHttpRequest(
                method = "POST",
                url = GOOGLE_OAUTH_TOKEN_ENDPOINT,
                headers = mapOf(
                    "Content-Type" to
                        "application/x-www-form-urlencoded",
                    "Accept" to "application/json",
                ),
                body = encodeForm(
                    "code" to serverAuthCode,
                    "client_id" to webClientId,
                    "client_secret" to webClientSecret,
                    "redirect_uri" to "",
                    "grant_type" to "authorization_code",
                ),
            ),
        )

        val accessToken = when (tokenResponse.statusCode) {
            200 -> readRequiredJsonString(
                body = tokenResponse.body,
                fieldName = "access_token",
                maxCharacters =
                    MAX_PLAY_GAMES_ACCESS_TOKEN_CHARACTERS,
            ) ?: return OnlinePlayGamesIdentityVerificationResult.Unavailable

            400, 401 -> {
                return OnlinePlayGamesIdentityVerificationResult.Invalid
            }

            else -> {
                return OnlinePlayGamesIdentityVerificationResult.Unavailable
            }
        }

        val playerResponse = transport.execute(
            OnlinePlayGamesHttpRequest(
                method = "GET",
                url = GOOGLE_PLAY_GAMES_CURRENT_PLAYER_ENDPOINT,
                headers = mapOf(
                    "Authorization" to "Bearer $accessToken",
                    "Accept" to "application/json",
                ),
            ),
        )

        return when (playerResponse.statusCode) {
            200 -> {
                val playerId = readRequiredJsonString(
                    body = playerResponse.body,
                    fieldName = "playerId",
                    maxCharacters =
                        MAX_PLAY_GAMES_PLAYER_ID_CHARACTERS,
                ) ?: return OnlinePlayGamesIdentityVerificationResult.Unavailable

                OnlinePlayGamesIdentityVerificationResult.Verified(
                    playerId = playerId,
                )
            }

            400, 401, 403, 404 -> {
                OnlinePlayGamesIdentityVerificationResult.Invalid
            }

            else -> {
                OnlinePlayGamesIdentityVerificationResult.Unavailable
            }
        }
    }

    private fun readRequiredJsonString(
        body: String,
        fieldName: String,
        maxCharacters: Int,
    ): String? {
        if (
            body.isBlank() ||
            body.length > MAX_PLAY_GAMES_RESPONSE_CHARACTERS
        ) {
            return null
        }

        val value = try {
            PLAY_GAMES_JSON
                .parseToJsonElement(body)
                .jsonObject[fieldName]
                ?.jsonPrimitive
                ?.contentOrNull
                ?.trim()
        } catch (_: Exception) {
            null
        }

        return value?.takeIf { candidate ->
            candidate.isNotBlank() &&
                candidate.length <= maxCharacters
        }
    }

    private fun encodeForm(
        vararg fields: Pair<String, String>,
    ): String = fields.joinToString("&") { (name, value) ->
        "${urlEncode(name)}=${urlEncode(value)}"
    }

    private fun urlEncode(
        value: String,
    ): String = URLEncoder.encode(
        value,
        StandardCharsets.UTF_8.name(),
    )
}

internal fun createDefaultOnlinePlayGamesIdentityVerifier(
    readEnvironmentVariable: (String) -> String? = { variableName ->
        System.getenv(variableName)
    },
): OnlinePlayGamesIdentityVerifier {
    val clientId = readEnvironmentVariable(
        PLAY_GAMES_WEB_CLIENT_ID_ENVIRONMENT_VARIABLE,
    )
        ?.trim()
        ?.takeIf { value -> value.isNotBlank() }
    val clientSecret = readEnvironmentVariable(
        PLAY_GAMES_WEB_CLIENT_SECRET_ENVIRONMENT_VARIABLE,
    )
        ?.trim()
        ?.takeIf { value -> value.isNotBlank() }

    return if (clientId == null || clientSecret == null) {
        OnlinePlayGamesIdentityVerifier {
            OnlinePlayGamesIdentityVerificationResult.Unavailable
        }
    } else {
        GooglePlayGamesServerIdentityVerifier(
            webClientId = clientId,
            webClientSecret = clientSecret,
        )
    }
}
