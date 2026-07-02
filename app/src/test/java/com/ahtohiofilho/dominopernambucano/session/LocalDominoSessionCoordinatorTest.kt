package com.ahtohiofilho.dominopernambucano.session

import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionDto
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineAnonymousSessionStore
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBindingRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBindingStore
import com.ahtohiofilho.dominopernambucano.online.OnlinePendingParticipationLocalResolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDominoSessionCoordinatorTest {
    @Test
    fun initial_state_exposes_ready_pending_online_participation_when_local_identity_matches() {
        val binding = OnlineParticipationBinding(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "anonymous-player-1",
            localSeatIndex = 2,
        )

        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = TestOnlineParticipationBindingStore(
                        initialBinding = binding,
                    ),
                ),
            onlineAnonymousSessionRepository =
                createAnonymousSessionRepository(
                    playerId = binding.playerId,
                ),
        )

        assertEquals(
            DominoSessionState.MainMenu(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution
                        .ReadyForRemoteReconciliation(
                            binding = binding,
                        ),
            ),
            coordinator.currentState,
        )

        assertTrue(
            (coordinator.currentState as DominoSessionState.MainMenu)
                .hasPendingOnlineParticipation,
        )
    }

    @Test
    fun initial_state_has_no_pending_online_participation_when_binding_is_absent() {
        val store = TestOnlineParticipationBindingStore()

        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = store,
                ),
        )

        assertEquals(
            DominoSessionState.MainMenu(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
            ),
            coordinator.currentState,
        )

        assertFalse(
            (coordinator.currentState as DominoSessionState.MainMenu)
                .hasPendingOnlineParticipation,
        )

        assertEquals(
            0,
            store.clearCallCount,
        )
    }

    @Test
    fun initial_state_has_no_pending_online_participation_when_binding_is_invalid() {
        val store = TestOnlineParticipationBindingStore(
            initialBinding = OnlineParticipationBinding(
                roomId = "room-1",
                matchId = "match-1",
                playerId = "anonymous-player-1",
                localSeatIndex = 4,
            ),
        )

        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = store,
                ),
        )

        assertEquals(
            DominoSessionState.MainMenu(
                pendingOnlineParticipation =
                    OnlinePendingParticipationLocalResolution
                        .NoPendingParticipation,
            ),
            coordinator.currentState,
        )

        assertFalse(
            (coordinator.currentState as DominoSessionState.MainMenu)
                .hasPendingOnlineParticipation,
        )

        assertNull(
            store.storedBinding,
        )

        assertEquals(
            1,
            store.clearCallCount,
        )
    }

    private fun createAnonymousSessionRepository(
        playerId: String,
    ): OnlineAnonymousSessionRepository {
        return OnlineAnonymousSessionRepository(
            store = TestOnlineAnonymousSessionStore(
                initialSession = OnlineAnonymousSessionDto(
                    playerId = playerId,
                    accessToken = "test-access-token",
                    expiresAtEpochMillis = Long.MAX_VALUE,
                ),
            ),
        )
    }
}

private class TestOnlineParticipationBindingStore(
    initialBinding: OnlineParticipationBinding? = null,
) : OnlineParticipationBindingStore {
    var storedBinding: OnlineParticipationBinding? = initialBinding
        private set

    var clearCallCount: Int = 0
        private set

    override fun read(): OnlineParticipationBinding? {
        return storedBinding
    }

    override fun write(
        binding: OnlineParticipationBinding,
    ) {
        storedBinding = binding
    }

    override fun clear() {
        clearCallCount += 1
        storedBinding = null
    }
}

private class TestOnlineAnonymousSessionStore(
    initialSession: OnlineAnonymousSessionDto? = null,
) : OnlineAnonymousSessionStore {
    private var storedSession: OnlineAnonymousSessionDto? = initialSession

    override fun read(): OnlineAnonymousSessionDto? {
        return storedSession
    }

    override fun write(
        session: OnlineAnonymousSessionDto,
    ) {
        storedSession = session
    }

    override fun clear() {
        storedSession = null
    }
}
