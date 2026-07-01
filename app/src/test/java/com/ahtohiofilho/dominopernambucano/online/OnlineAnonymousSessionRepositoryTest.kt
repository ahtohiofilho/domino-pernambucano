package com.ahtohiofilho.dominopernambucano.online

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineAnonymousSessionRepositoryTest {
    @Test
    fun valid_stored_session_is_returned_without_clearing_store() {
        val session = OnlineAnonymousSessionDto(
            playerId = "anonymous-player-1",
            accessToken = "test-access-token",
            expiresAtEpochMillis = 1_001L,
        )

        val store = FakeOnlineAnonymousSessionStore(
            initialSession = session,
        )

        val repository = OnlineAnonymousSessionRepository(
            store = store,
            nowEpochMillis = { 1_000L },
        )

        assertEquals(
            session,
            repository.getValidSessionOrNull(),
        )

        assertEquals(
            0,
            store.clearCallCount,
        )
    }

    @Test
    fun valid_stored_session_is_reused_without_requesting_new_session() =
        runBlocking {
            val storedSession = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-1",
                accessToken = "stored-access-token",
                expiresAtEpochMillis = 2_000L,
            )

            val store = FakeOnlineAnonymousSessionStore(
                initialSession = storedSession,
            )

            val repository = OnlineAnonymousSessionRepository(
                store = store,
                nowEpochMillis = { 1_000L },
            )

            var createSessionCallCount = 0

            val result = repository.getOrCreateValidSession {
                createSessionCallCount += 1

                OnlineAnonymousSessionDto(
                    playerId = "anonymous-player-2",
                    accessToken = "new-access-token",
                    expiresAtEpochMillis = 3_000L,
                )
            }

            assertEquals(
                storedSession,
                result,
            )

            assertEquals(
                storedSession,
                store.storedSession,
            )

            assertEquals(
                0,
                createSessionCallCount,
            )

            assertEquals(
                0,
                store.clearCallCount,
            )
        }

    @Test
    fun missing_session_is_created_persisted_and_returned() =
        runBlocking {
            val store = FakeOnlineAnonymousSessionStore()

            val repository = OnlineAnonymousSessionRepository(
                store = store,
                nowEpochMillis = { 1_000L },
            )

            val createdSession = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-1",
                accessToken = "new-access-token",
                expiresAtEpochMillis = 2_000L,
            )

            var createSessionCallCount = 0

            val result = repository.getOrCreateValidSession {
                createSessionCallCount += 1
                createdSession
            }

            assertEquals(
                createdSession,
                result,
            )

            assertEquals(
                createdSession,
                store.storedSession,
            )

            assertEquals(
                1,
                createSessionCallCount,
            )

            assertEquals(
                0,
                store.clearCallCount,
            )
        }

    @Test
    fun expired_session_is_cleared_replaced_and_returned() =
        runBlocking {
            val replacementSession = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-2",
                accessToken = "replacement-access-token",
                expiresAtEpochMillis = 2_000L,
            )

            val store = FakeOnlineAnonymousSessionStore(
                initialSession = OnlineAnonymousSessionDto(
                    playerId = "anonymous-player-1",
                    accessToken = "expired-access-token",
                    expiresAtEpochMillis = 1_000L,
                ),
            )

            val repository = OnlineAnonymousSessionRepository(
                store = store,
                nowEpochMillis = { 1_000L },
            )

            var createSessionCallCount = 0

            val result = repository.getOrCreateValidSession {
                createSessionCallCount += 1
                replacementSession
            }

            assertEquals(
                replacementSession,
                result,
            )

            assertEquals(
                replacementSession,
                store.storedSession,
            )

            assertEquals(
                1,
                createSessionCallCount,
            )

            assertEquals(
                1,
                store.clearCallCount,
            )
        }

    @Test
    fun session_expiring_at_current_instant_is_cleared_and_not_returned() {
        val store = FakeOnlineAnonymousSessionStore(
            initialSession = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-1",
                accessToken = "test-access-token",
                expiresAtEpochMillis = 1_000L,
            ),
        )

        val repository = OnlineAnonymousSessionRepository(
            store = store,
            nowEpochMillis = { 1_000L },
        )

        assertNull(
            repository.getValidSessionOrNull(),
        )

        assertNull(
            store.storedSession,
        )

        assertEquals(
            1,
            store.clearCallCount,
        )
    }

    @Test
    fun malformed_stored_session_is_cleared_and_not_returned() {
        val store = FakeOnlineAnonymousSessionStore(
            initialSession = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-1",
                accessToken = "   ",
                expiresAtEpochMillis = 2_000L,
            ),
        )

        val repository = OnlineAnonymousSessionRepository(
            store = store,
            nowEpochMillis = { 1_000L },
        )

        assertNull(
            repository.getValidSessionOrNull(),
        )

        assertNull(
            store.storedSession,
        )

        assertEquals(
            1,
            store.clearCallCount,
        )
    }

    @Test
    fun save_writes_session_without_altering_credentials() {
        val store = FakeOnlineAnonymousSessionStore()

        val repository = OnlineAnonymousSessionRepository(
            store = store,
            nowEpochMillis = { 1_000L },
        )

        val session = OnlineAnonymousSessionDto(
            playerId = "anonymous-player-1",
            accessToken = "test-access-token",
            expiresAtEpochMillis = 2_000L,
        )

        repository.save(
            session = session,
        )

        assertEquals(
            session,
            store.storedSession,
        )
    }

    @Test(
        expected = IllegalArgumentException::class,
    )
    fun save_rejects_session_expired_at_current_instant() {
        val repository = OnlineAnonymousSessionRepository(
            store = FakeOnlineAnonymousSessionStore(),
            nowEpochMillis = { 1_000L },
        )

        repository.save(
            session = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-1",
                accessToken = "expired-access-token",
                expiresAtEpochMillis = 1_000L,
            ),
        )
    }

    @Test
    fun clear_delegates_to_store() {
        val store = FakeOnlineAnonymousSessionStore(
            initialSession = OnlineAnonymousSessionDto(
                playerId = "anonymous-player-1",
                accessToken = "test-access-token",
                expiresAtEpochMillis = 2_000L,
            ),
        )

        val repository = OnlineAnonymousSessionRepository(
            store = store,
        )

        repository.clear()

        assertNull(
            store.storedSession,
        )

        assertEquals(
            1,
            store.clearCallCount,
        )
    }
}

private class FakeOnlineAnonymousSessionStore(
    initialSession: OnlineAnonymousSessionDto? = null,
) : OnlineAnonymousSessionStore {
    var storedSession: OnlineAnonymousSessionDto? = initialSession
        private set

    var clearCallCount: Int = 0
        private set

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
        clearCallCount += 1
    }
}