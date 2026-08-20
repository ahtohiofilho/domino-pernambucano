package com.ahtohiofilho.dominopernambucano.ui.account

import com.ahtohiofilho.dominopernambucano.online.OnlineEmailAccountIntent
import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class OnlineAccountDialogEmailFlowTest {
    @Test
    fun disconnected_user_must_choose_link_or_recovery_before_requesting_code() {
        assertEquals(
            listOf(
                OnlineEmailAccountIntent.LINK,
                OnlineEmailAccountIntent.RECOVER,
            ),
            onlineEmailAccountAvailableIntents(
                status =
                    OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL,
                emailAvailable = true,
            ),
        )
    }

    @Test
    fun visitor_can_only_link_and_expired_account_can_only_recover() {
        assertEquals(
            listOf(OnlineEmailAccountIntent.LINK),
            onlineEmailAccountAvailableIntents(
                status = OnlineGoogleAccountStatus.VISITOR,
                emailAvailable = true,
            ),
        )
        assertEquals(
            listOf(OnlineEmailAccountIntent.RECOVER),
            onlineEmailAccountAvailableIntents(
                status =
                    OnlineGoogleAccountStatus.RECOVERY_REQUIRED,
                emailAvailable = true,
            ),
        )
    }

    @Test
    fun connected_account_can_add_email_but_unavailable_build_exposes_nothing() {
        assertEquals(
            listOf(OnlineEmailAccountIntent.LINK),
            onlineEmailAccountAvailableIntents(
                status = OnlineGoogleAccountStatus.CONNECTED,
                emailAvailable = true,
            ),
        )
        assertEquals(
            emptyList<OnlineEmailAccountIntent>(),
            onlineEmailAccountAvailableIntents(
                status = OnlineGoogleAccountStatus.UNAVAILABLE,
                emailAvailable = false,
            ),
        )
    }
}
