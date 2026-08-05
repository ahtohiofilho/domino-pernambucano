package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidPlayGamesServerAuthCodeProviderTest {
    @Test
    fun automatic_authentication_requests_code_without_manual_sign_in() =
        runBlocking {
            val client = FakePlayGamesSignInClientAdapter(
                authenticated = true,
                code = " server-auth-code ",
            )
            val provider = provider(client)

            assertEquals(
                "server-auth-code",
                provider.requestServerAuthCode(),
            )
            assertEquals(1, client.authenticationChecks)
            assertEquals(0, client.signInCalls)
            assertEquals(
                "server-client.apps.googleusercontent.com",
                client.requestedServerClientId,
            )
        }

    @Test
    fun failed_automatic_authentication_uses_manual_sign_in_once() =
        runBlocking {
            val client = FakePlayGamesSignInClientAdapter(
                authenticated = false,
                signInResult = true,
            )

            provider(client).requestServerAuthCode()

            assertEquals(1, client.signInCalls)
        }

    @Test
    fun declined_sign_in_is_reported_as_cancellation() {
        assertThrows(
            PlayGamesSignInCancelledException::class.java,
        ) {
            runBlocking {
                provider(
                    FakePlayGamesSignInClientAdapter(
                        authenticated = false,
                        signInResult = false,
                    ),
                ).requestServerAuthCode()
            }
        }
    }

    @Test
    fun blank_code_and_missing_configuration_fail_closed() {
        assertThrows(
            PlayGamesCredentialUnavailableException::class.java,
        ) {
            runBlocking {
                provider(
                    FakePlayGamesSignInClientAdapter(
                        code = " ",
                    ),
                ).requestServerAuthCode()
            }
        }

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                AndroidPlayGamesServerAuthCodeProvider(
                    config = PlayGamesSignInConfig(
                        projectId = "",
                        webClientId = "",
                    ),
                    client = FakePlayGamesSignInClientAdapter(),
                ).requestServerAuthCode()
            }
        }
    }

    private fun provider(
        client: FakePlayGamesSignInClientAdapter,
    ): AndroidPlayGamesServerAuthCodeProvider {
        return AndroidPlayGamesServerAuthCodeProvider(
            config = PlayGamesSignInConfig(
                projectId = "1234567890",
                webClientId =
                    "server-client.apps.googleusercontent.com",
            ),
            client = client,
        )
    }
}

private class FakePlayGamesSignInClientAdapter(
    private val authenticated: Boolean = true,
    private val signInResult: Boolean = true,
    private val code: String = "server-auth-code",
) : PlayGamesSignInClientAdapter {
    var authenticationChecks = 0
        private set
    var signInCalls = 0
        private set
    var requestedServerClientId: String? = null
        private set

    override suspend fun isAuthenticated(): Boolean {
        authenticationChecks += 1
        return authenticated
    }

    override suspend fun signIn(): Boolean {
        signInCalls += 1
        return signInResult
    }

    override suspend fun requestServerAuthCode(
        serverClientId: String,
    ): String {
        requestedServerClientId = serverClientId
        return code
    }
}
