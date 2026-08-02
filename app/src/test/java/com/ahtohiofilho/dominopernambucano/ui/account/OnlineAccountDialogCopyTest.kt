package com.ahtohiofilho.dominopernambucano.ui.account

import com.ahtohiofilho.dominopernambucano.online.OnlineGoogleAccountStatus
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
}
