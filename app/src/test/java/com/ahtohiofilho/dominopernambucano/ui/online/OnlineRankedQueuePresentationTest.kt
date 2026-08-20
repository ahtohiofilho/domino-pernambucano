package com.ahtohiofilho.dominopernambucano.ui.online

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineRankedQueuePresentationTest {
    @Test
    fun ranked_authentication_required_exposes_account_remediation() {
        assertTrue(
            failure(
                OnlineRankedQueueUiFailureKind.AUTHENTICATION_REQUIRED,
            ).showsAccountRemediation(),
        )
    }

    @Test
    fun ranked_account_required_exposes_account_remediation() {
        assertTrue(
            failure(
                OnlineRankedQueueUiFailureKind.ACCOUNT_REQUIRED,
            ).showsAccountRemediation(),
        )
    }

    @Test
    fun ranked_invalid_match_does_not_expose_account_remediation() {
        assertFalse(
            failure(
                OnlineRankedQueueUiFailureKind.INVALID_MATCH,
            ).showsAccountRemediation(),
        )
    }

    @Test
    fun ranked_protocol_error_does_not_expose_account_remediation() {
        assertFalse(
            failure(
                OnlineRankedQueueUiFailureKind.PROTOCOL_ERROR,
            ).showsAccountRemediation(),
        )
    }

    @Test
    fun ranked_session_rejected_does_not_expose_account_remediation() {
        assertFalse(
            failure(
                OnlineRankedQueueUiFailureKind.SESSION_REJECTED,
            ).showsAccountRemediation(),
        )
    }

    private fun failure(
        kind: OnlineRankedQueueUiFailureKind,
    ): OnlineRankedQueueUiState.Failure {
        return OnlineRankedQueueUiState.Failure(
            kind = kind,
            retryable = false,
        )
    }
}