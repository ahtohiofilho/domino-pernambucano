package com.ahtohiofilho.dominopernambucano.server

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OnlineGoogleIdentityTokenVerifierTest {
    @Test
    fun missing_google_client_id_disables_only_google_identity_verification() =
        runBlocking {
            val verifier = createDefaultOnlineGoogleIdentityTokenVerifier(
                readEnvironmentVariable = { null },
            )

            assertSame(
                OnlineGoogleIdentityVerificationResult.Unavailable,
                verifier.verify("any-token"),
            )
        }

    @Test
    fun legacy_single_client_id_remains_supported() {
        val configured = readConfiguredGoogleWebClientIds { variableName ->
            when (variableName) {
                GOOGLE_WEB_CLIENT_ID_ENVIRONMENT_VARIABLE ->
                    " legacy-client.apps.googleusercontent.com "
                else -> null
            }
        }

        assertEquals(
            listOf("legacy-client.apps.googleusercontent.com"),
            configured,
        )
    }

    @Test
    fun migration_client_ids_are_combined_with_legacy_and_deduplicated() {
        val configured = readConfiguredGoogleWebClientIds { variableName ->
            when (variableName) {
                GOOGLE_WEB_CLIENT_ID_ENVIRONMENT_VARIABLE ->
                    "legacy-client.apps.googleusercontent.com"
                GOOGLE_WEB_CLIENT_IDS_ENVIRONMENT_VARIABLE ->
                    " new-client.apps.googleusercontent.com, " +
                        "legacy-client.apps.googleusercontent.com, " +
                        "second-new-client.apps.googleusercontent.com, "
                else -> null
            }
        }

        assertEquals(
            listOf(
                "legacy-client.apps.googleusercontent.com",
                "new-client.apps.googleusercontent.com",
                "second-new-client.apps.googleusercontent.com",
            ),
            configured,
        )
    }

    @Test
    fun migration_list_can_enable_google_without_legacy_variable() {
        val configured = readConfiguredGoogleWebClientIds { variableName ->
            when (variableName) {
                GOOGLE_WEB_CLIENT_IDS_ENVIRONMENT_VARIABLE ->
                    "new-client.apps.googleusercontent.com"
                else -> null
            }
        }

        assertEquals(
            listOf("new-client.apps.googleusercontent.com"),
            configured,
        )
    }
}
