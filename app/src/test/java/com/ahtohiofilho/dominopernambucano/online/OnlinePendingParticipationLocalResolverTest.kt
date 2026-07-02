package com.ahtohiofilho.dominopernambucano.online

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlinePendingParticipationLocalResolverTest {
    @Test
    fun matching_valid_binding_and_session_are_ready_for_remote_reconciliation() {
        val binding = createBinding()
        val bindingStore = ResolverTestParticipationBindingStore(
            initialBinding = binding,
        )
        val anonymousSessionStore = ResolverTestAnonymousSessionStore(
            initialSession = createSession(
                playerId = binding.playerId,
            ),
        )

        val resolution = OnlinePendingParticipationLocalResolver(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = bindingStore,
                ),
            onlineAnonymousSessionRepository =
                OnlineAnonymousSessionRepository(
                    store = anonymousSessionStore,
                    nowEpochMillis = { 1_000L },
                ),
        ).resolve()

        assertEquals(
            OnlinePendingParticipationLocalResolution
                .ReadyForRemoteReconciliation(
                    binding = binding,
                ),
            resolution,
        )

        assertEquals(
            0,
            bindingStore.clearCallCount,
        )

        assertEquals(
            0,
            anonymousSessionStore.clearCallCount,
        )
    }

    @Test
    fun absent_binding_does_not_read_anonymous_session() {
        val bindingStore = ResolverTestParticipationBindingStore()
        val anonymousSessionStore = ResolverTestAnonymousSessionStore(
            initialSession = createSession(),
        )

        val resolution = OnlinePendingParticipationLocalResolver(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = bindingStore,
                ),
            onlineAnonymousSessionRepository =
                OnlineAnonymousSessionRepository(
                    store = anonymousSessionStore,
                    nowEpochMillis = { 1_000L },
                ),
        ).resolve()

        assertEquals(
            OnlinePendingParticipationLocalResolution
                .NoPendingParticipation,
            resolution,
        )

        assertEquals(
            0,
            anonymousSessionStore.readCallCount,
        )
    }

    @Test
    fun valid_binding_without_session_is_blocked_without_clearing_binding() {
        val binding = createBinding()
        val bindingStore = ResolverTestParticipationBindingStore(
            initialBinding = binding,
        )
        val anonymousSessionStore = ResolverTestAnonymousSessionStore()

        val resolution = OnlinePendingParticipationLocalResolver(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = bindingStore,
                ),
            onlineAnonymousSessionRepository =
                OnlineAnonymousSessionRepository(
                    store = anonymousSessionStore,
                    nowEpochMillis = { 1_000L },
                ),
        ).resolve()

        assertEquals(
            OnlinePendingParticipationLocalResolution
                .BlockedByMissingValidAnonymousSession(
                    binding = binding,
                ),
            resolution,
        )

        assertEquals(
            binding,
            bindingStore.storedBinding,
        )

        assertEquals(
            0,
            bindingStore.clearCallCount,
        )
    }

    @Test
    fun expired_session_blocks_reconciliation_without_clearing_binding() {
        val binding = createBinding()
        val bindingStore = ResolverTestParticipationBindingStore(
            initialBinding = binding,
        )
        val anonymousSessionStore = ResolverTestAnonymousSessionStore(
            initialSession = createSession(
                playerId = binding.playerId,
                expiresAtEpochMillis = 1_000L,
            ),
        )

        val resolution = OnlinePendingParticipationLocalResolver(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = bindingStore,
                ),
            onlineAnonymousSessionRepository =
                OnlineAnonymousSessionRepository(
                    store = anonymousSessionStore,
                    nowEpochMillis = { 1_000L },
                ),
        ).resolve()

        assertEquals(
            OnlinePendingParticipationLocalResolution
                .BlockedByMissingValidAnonymousSession(
                    binding = binding,
                ),
            resolution,
        )

        assertEquals(
            binding,
            bindingStore.storedBinding,
        )

        assertNull(
            anonymousSessionStore.storedSession,
        )

        assertEquals(
            1,
            anonymousSessionStore.clearCallCount,
        )
    }

    @Test
    fun valid_session_for_different_player_blocks_reconciliation_without_clearing_data() {
        val binding = createBinding()
        val bindingStore = ResolverTestParticipationBindingStore(
            initialBinding = binding,
        )
        val anonymousSessionStore = ResolverTestAnonymousSessionStore(
            initialSession = createSession(
                playerId = "anonymous-player-2",
            ),
        )

        val resolution = OnlinePendingParticipationLocalResolver(
            onlineParticipationBindingRepository =
                OnlineParticipationBindingRepository(
                    store = bindingStore,
                ),
            onlineAnonymousSessionRepository =
                OnlineAnonymousSessionRepository(
                    store = anonymousSessionStore,
                    nowEpochMillis = { 1_000L },
                ),
        ).resolve()

        assertEquals(
            OnlinePendingParticipationLocalResolution
                .BlockedByAnonymousSessionIdentityMismatch(
                    binding = binding,
                ),
            resolution,
        )

        assertEquals(
            binding,
            bindingStore.storedBinding,
        )

        assertEquals(
            0,
            bindingStore.clearCallCount,
        )

        assertEquals(
            0,
            anonymousSessionStore.clearCallCount,
        )
    }

    private fun createBinding(): OnlineParticipationBinding {
        return OnlineParticipationBinding(
            roomId = "room-1",
            matchId = "match-1",
            playerId = "anonymous-player-1",
            localSeatIndex = 2,
        )
    }

    private fun createSession(
        playerId: String = "anonymous-player-1",
        expiresAtEpochMillis: Long = 2_000L,
    ): OnlineAnonymousSessionDto {
        return OnlineAnonymousSessionDto(
            playerId = playerId,
            accessToken = "test-access-token",
            expiresAtEpochMillis = expiresAtEpochMillis,
        )
    }
}

private class ResolverTestParticipationBindingStore(
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

private class ResolverTestAnonymousSessionStore(
    initialSession: OnlineAnonymousSessionDto? = null,
) : OnlineAnonymousSessionStore {
    var storedSession: OnlineAnonymousSessionDto? = initialSession
        private set

    var readCallCount: Int = 0
        private set

    var clearCallCount: Int = 0
        private set

    override fun read(): OnlineAnonymousSessionDto? {
        readCallCount += 1
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
