package com.ahtohiofilho.dominopernambucano.ui.account

import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
import com.ahtohiofilho.dominopernambucano.online.OnlineEmailAccountIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineAccountDialogCopyTest {
    @Test
    fun connected_state_suppresses_duplicate_success_feedback() {
        assertNull(
            onlineAccountDialogVisibleFeedback(
                status = OnlineGoogleAccountStatus.CONNECTED,
                feedbackMessage = "Conta conectada com sucesso.",
            ),
        )
    }

    @Test
    fun non_connected_states_keep_operational_feedback() {
        val feedback = "Não foi possível concluir a conexão."

        assertEquals(
            feedback,
            onlineAccountDialogVisibleFeedback(
                status = OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL,
                feedbackMessage = feedback,
            ),
        )
    }

    @Test
    fun create_account_mode_uses_link_intent() {
        assertEquals(
            OnlineEmailAccountIntent.LINK,
            onlineAccountEmailIntentFor(
                OnlineAccountEntryMode.CREATE_ACCOUNT,
            ),
        )
    }

    @Test
    fun sign_in_mode_uses_recovery_intent() {
        assertEquals(
            OnlineEmailAccountIntent.RECOVER,
            onlineAccountEmailIntentFor(
                OnlineAccountEntryMode.SIGN_IN,
            ),
        )
    }

    @Test
    fun recovery_required_defaults_to_sign_in() {
        assertEquals(
            OnlineAccountEntryMode.SIGN_IN,
            defaultOnlineAccountEntryMode(
                OnlineGoogleAccountStatus.RECOVERY_REQUIRED,
            ),
        )
    }

    @Test
    fun new_or_disconnected_account_defaults_to_create() {
        assertEquals(
            OnlineAccountEntryMode.CREATE_ACCOUNT,
            defaultOnlineAccountEntryMode(
                OnlineGoogleAccountStatus.NO_LOCAL_CREDENTIAL,
            ),
        )
    }

    @Test
    fun compact_account_entry_width_stacks_mode_buttons() {
        assertEquals(
            true,
            accountEntryModeButtonsShouldStack(
                availableWidthDp = 266f,
            ),
        )
    }

    @Test
    fun wide_account_entry_width_keeps_mode_buttons_side_by_side() {
        assertEquals(
            false,
            accountEntryModeButtonsShouldStack(
                availableWidthDp = 384f,
            ),
        )
    }
}
