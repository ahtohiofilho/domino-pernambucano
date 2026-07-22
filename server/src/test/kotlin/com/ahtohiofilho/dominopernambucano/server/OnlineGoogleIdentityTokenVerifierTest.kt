package com.ahtohiofilho.dominopernambucano.server

import kotlinx.coroutines.runBlocking
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
}
