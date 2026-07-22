package com.ahtohiofilho.dominopernambucano.online

import org.junit.Assert.assertEquals
import org.junit.Test

class OnlinePendingParticipationAccountCredentialTest {
    @Test
    fun account_for_same_player_allows_pending_participation_reconciliation() {
        val binding = OnlineParticipationBinding(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "anonymous-player-1",
            localSeatIndex = 0,
        )
        val credentialStore = PendingAccountCredentialStore(
            OnlineSessionCredential(
                sessionKind = OnlineSessionKind.ACCOUNT,
                accountId = "account-1",
                playerId = binding.playerId,
                accessToken = "account-token",
                expiresAtEpochMillis = 2_000L,
            ),
        )

        val result = OnlinePendingParticipationLocalResolver(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    PendingAccountBindingStore(binding),
                ),
            onlineSessionCredentialRepository =
                OnlineSessionCredentialRepository(
                    store = credentialStore,
                    nowEpochMillis = { 1_000L },
                ),
        ).resolve()

        assertEquals(
            OnlinePendingParticipationLocalResolution
                .ReadyForRemoteReconciliation(binding),
            result,
        )
    }
}

private class PendingAccountCredentialStore(
    private var credential: OnlineSessionCredential?,
) : OnlineSessionCredentialStore {
    override fun read(): OnlineSessionCredential? = credential
    override fun write(credential: OnlineSessionCredential): Boolean {
        this.credential = credential
        return true
    }
    override fun clear(): Boolean {
        credential = null
        return true
    }
}

private class PendingAccountBindingStore(
    private var binding: OnlineParticipationBinding?,
) : OnlineParticipationBindingStore {
    override fun read(): OnlineParticipationBinding? = binding
    override fun write(binding: OnlineParticipationBinding) {
        this.binding = binding
    }
    override fun clear() {
        binding = null
    }
}
