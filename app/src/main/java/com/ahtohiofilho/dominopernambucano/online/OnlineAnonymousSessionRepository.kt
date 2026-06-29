package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val ANONYMOUS_SESSION_REFRESH_MARGIN_MILLIS = 60_000L

class OnlineAnonymousSessionRepository(
    private val apiClient: RemoteOnlineApiClient,
    private val store: OnlineAnonymousSessionStore,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
) {
    private val sessionMutex = Mutex()

    suspend fun getOrCreate(): OnlineAnonymousSessionDto {
        return sessionMutex.withLock {
            val session = store.read()
                ?.takeIf { storedSession ->
                    storedSession.isUsableAt(
                        epochMillis = nowEpochMillis(),
                    )
                }
                ?: apiClient.createAnonymousSession()
                    .also { issuedSession ->
                        require(
                            issuedSession.playerId.isNotBlank() &&
                                issuedSession.accessToken.isNotBlank(),
                        ) {
                            "O servidor retornou uma sessão anônima inválida."
                        }

                        store.write(
                            session = issuedSession,
                        )
                    }

            apiClient.setAnonymousSession(
                session = session,
            )

            session
        }
    }
}

private fun OnlineAnonymousSessionDto.isUsableAt(
    epochMillis: Long,
): Boolean {
    return playerId.isNotBlank() &&
            accessToken.isNotBlank() &&
            expiresAtEpochMillis >
            epochMillis + ANONYMOUS_SESSION_REFRESH_MARGIN_MILLIS
}
