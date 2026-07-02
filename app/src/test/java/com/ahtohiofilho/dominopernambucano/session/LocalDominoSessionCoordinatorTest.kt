package com.ahtohiofilho.dominopernambucano.session

import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBinding
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBindingRepository
import com.ahtohiofilho.dominopernambucano.online.OnlineParticipationBindingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalDominoSessionCoordinatorTest {
    @Test
    fun initial_state_marks_pending_online_participation_when_valid_binding_exists() {
        val coordinator = LocalDominoSessionCoordinator(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = TestOnlineParticipationBindingStore(
                        initialBinding = OnlineParticipationBinding(
                            roomId = "room-1",
                            matchId = "match-1",
                            playerId = "anonymous-player-1",
                            localSeatIndex = 2,
                        ),
                    ),
                ),
        )

        assertEquals(
            DominoSessionState.MainMenu(
                hasPendingOnlineParticipation = true,
            ),
            coordinator.currentState,
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
                hasPendingOnlineParticipation = false,
            ),
            coordinator.currentState,
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
                hasPendingOnlineParticipation = false,
            ),
            coordinator.currentState,
        )

        assertNull(
            store.storedBinding,
        )

        assertEquals(
            1,
            store.clearCallCount,
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
