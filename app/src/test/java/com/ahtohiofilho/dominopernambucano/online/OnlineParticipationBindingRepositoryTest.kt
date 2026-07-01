package com.ahtohiofilho.dominopernambucano.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineParticipationBindingRepositoryTest {
    @Test
    fun valid_binding_is_saved_and_read_back() {
        val store = FakeOnlineParticipationBindingStore()
        val repository = OnlineParticipationBindingRepository(
            store = store,
        )

        val binding = OnlineParticipationBinding(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "anonymous-player-1",
            localSeatIndex = 2,
        )

        repository.save(
            binding = binding,
        )

        assertEquals(
            binding,
            store.storedBinding,
        )

        assertEquals(
            binding,
            repository.getValidBindingOrNull(),
        )

        assertEquals(
            0,
            store.clearCallCount,
        )
    }

    @Test
    fun binding_without_match_id_is_saved_and_read_back() {
        val store = FakeOnlineParticipationBindingStore()
        val repository = OnlineParticipationBindingRepository(
            store = store,
        )

        val binding = OnlineParticipationBinding(
            roomId = "room-1",
            matchId = null,
            playerId = "anonymous-player-1",
            localSeatIndex = 0,
        )

        repository.save(
            binding = binding,
        )

        assertEquals(
            binding,
            repository.getValidBindingOrNull(),
        )
    }

    @Test
    fun absent_binding_returns_null_without_clearing_store() {
        val store = FakeOnlineParticipationBindingStore()
        val repository = OnlineParticipationBindingRepository(
            store = store,
        )

        assertNull(
            repository.getValidBindingOrNull(),
        )

        assertEquals(
            0,
            store.clearCallCount,
        )
    }

    @Test
    fun invalid_stored_binding_is_cleared_and_not_returned() {
        val store = FakeOnlineParticipationBindingStore(
            initialBinding = OnlineParticipationBinding(
                roomId = "room-1",
                matchId = "match-1",
                playerId = "anonymous-player-1",
                localSeatIndex = 4,
            ),
        )

        val repository = OnlineParticipationBindingRepository(
            store = store,
        )

        assertNull(
            repository.getValidBindingOrNull(),
        )

        assertNull(
            store.storedBinding,
        )

        assertEquals(
            1,
            store.clearCallCount,
        )
    }

    @Test
    fun clear_delegates_to_store() {
        val store = FakeOnlineParticipationBindingStore(
            initialBinding = OnlineParticipationBinding(
                roomId = "room-1",
                matchId = "match-1",
                playerId = "anonymous-player-1",
                localSeatIndex = 1,
            ),
        )

        val repository = OnlineParticipationBindingRepository(
            store = store,
        )

        repository.clear()

        assertNull(
            store.storedBinding,
        )

        assertEquals(
            1,
            store.clearCallCount,
        )
    }

    @Test(
        expected = IllegalArgumentException::class,
    )
    fun save_rejects_blank_room_id() {
        createRepository().save(
            binding = OnlineParticipationBinding(
                roomId = "   ",
                matchId = "match-1",
                playerId = "anonymous-player-1",
                localSeatIndex = 0,
            ),
        )
    }

    @Test(
        expected = IllegalArgumentException::class,
    )
    fun save_rejects_blank_player_id() {
        createRepository().save(
            binding = OnlineParticipationBinding(
                roomId = "room-1",
                matchId = "match-1",
                playerId = "   ",
                localSeatIndex = 0,
            ),
        )
    }

    @Test(
        expected = IllegalArgumentException::class,
    )
    fun save_rejects_blank_match_id_when_present() {
        createRepository().save(
            binding = OnlineParticipationBinding(
                roomId = "room-1",
                matchId = "   ",
                playerId = "anonymous-player-1",
                localSeatIndex = 0,
            ),
        )
    }

    @Test(
        expected = IllegalArgumentException::class,
    )
    fun save_rejects_seat_outside_supported_range() {
        createRepository().save(
            binding = OnlineParticipationBinding(
                roomId = "room-1",
                matchId = "match-1",
                playerId = "anonymous-player-1",
                localSeatIndex = -1,
            ),
        )
    }

    private fun createRepository(): OnlineParticipationBindingRepository {
        return OnlineParticipationBindingRepository(
            store = FakeOnlineParticipationBindingStore(),
        )
    }
}

private class FakeOnlineParticipationBindingStore(
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
        storedBinding = null
        clearCallCount += 1
    }
}