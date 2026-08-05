package com.ahtohiofilho.dominopernambucano.online

import android.app.Activity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.games.GamesSignInClient
import com.google.android.gms.games.PlayGames
import com.google.android.gms.tasks.Task
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

fun interface PlayGamesServerAuthCodeProvider {
    suspend fun requestServerAuthCode(): String
}

internal interface PlayGamesSignInClientAdapter {
    suspend fun isAuthenticated(): Boolean

    suspend fun signIn(): Boolean

    suspend fun requestServerAuthCode(
        serverClientId: String,
    ): String
}

class PlayGamesSignInCancelledException(
    cause: Throwable? = null,
) : IllegalStateException(
    "A autenticação do Play Games não foi concluída.",
    cause,
)

class PlayGamesCredentialUnavailableException(
    cause: Throwable? = null,
) : IllegalStateException(
    "Não foi possível obter o código de autorização do Play Games.",
    cause,
)

class AndroidPlayGamesServerAuthCodeProvider internal constructor(
    private val config: PlayGamesSignInConfig,
    private val client: PlayGamesSignInClientAdapter,
) : PlayGamesServerAuthCodeProvider {
    constructor(
        activity: Activity,
        config: PlayGamesSignInConfig,
    ) : this(
        config = config,
        client = AndroidPlayGamesSignInClientAdapter(
            gamesSignInClient =
                PlayGames.getGamesSignInClient(activity),
        ),
    )

    override suspend fun requestServerAuthCode(): String {
        check(config.isConfigured) {
            "O Play Games não está configurado neste build."
        }

        val alreadyAuthenticated = try {
            client.isAuthenticated()
        } catch (error: PlayGamesSignInCancelledException) {
            throw error
        } catch (error: Throwable) {
            throw PlayGamesCredentialUnavailableException(error)
        }

        if (!alreadyAuthenticated) {
            val authenticated = try {
                client.signIn()
            } catch (error: PlayGamesSignInCancelledException) {
                throw error
            } catch (error: Throwable) {
                throw PlayGamesCredentialUnavailableException(error)
            }

            if (!authenticated) {
                throw PlayGamesSignInCancelledException()
            }
        }

        return try {
            client.requestServerAuthCode(
                serverClientId = config.webClientId,
            )
                .trim()
                .takeIf(String::isNotBlank)
                ?: throw PlayGamesCredentialUnavailableException()
        } catch (error: PlayGamesSignInCancelledException) {
            throw error
        } catch (error: PlayGamesCredentialUnavailableException) {
            throw error
        } catch (error: Throwable) {
            throw PlayGamesCredentialUnavailableException(error)
        }
    }
}

private class AndroidPlayGamesSignInClientAdapter(
    private val gamesSignInClient: GamesSignInClient,
) : PlayGamesSignInClientAdapter {
    override suspend fun isAuthenticated(): Boolean {
        return gamesSignInClient
            .isAuthenticated()
            .awaitPlayGamesTask()
            .isAuthenticated
    }

    override suspend fun signIn(): Boolean {
        return gamesSignInClient
            .signIn()
            .awaitPlayGamesTask()
            .isAuthenticated
    }

    override suspend fun requestServerAuthCode(
        serverClientId: String,
    ): String {
        return gamesSignInClient
            .requestServerSideAccess(
                serverClientId,
                false,
            )
            .awaitPlayGamesTask()
    }
}

private suspend fun <T> Task<T>.awaitPlayGamesTask(): T {
    return try {
        suspendCancellableCoroutine { continuation ->
            addOnCompleteListener { completedTask ->
                if (!continuation.isActive) {
                    return@addOnCompleteListener
                }

                if (completedTask.isSuccessful) {
                    continuation.resume(completedTask.result)
                } else {
                    continuation.resumeWithException(
                        completedTask.exception
                            ?: IllegalStateException(
                                "A operação do Play Games falhou sem causa.",
                            ),
                    )
                }
            }
        }
    } catch (error: ApiException) {
        if (error.statusCode == CommonStatusCodes.CANCELED) {
            throw PlayGamesSignInCancelledException(error)
        }

        throw PlayGamesCredentialUnavailableException(error)
    }
}
