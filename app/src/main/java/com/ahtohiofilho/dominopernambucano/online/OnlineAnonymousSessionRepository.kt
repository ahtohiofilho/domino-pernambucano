package com.ahtohiofilho.dominopernambucano.online

class OnlineAnonymousSessionRepository(
    private val store: OnlineAnonymousSessionStore,
    private val nowEpochMillis: () -> Long = {
        System.currentTimeMillis()
    },
) {
    fun getValidSessionOrNull(): OnlineAnonymousSessionDto? {
        val storedSession = store.read() ?: return null

        if (
            !storedSession.isValidAt(
                nowEpochMillis = nowEpochMillis(),
            )
        ) {
            store.clear()
            return null
        }

        return storedSession
    }

    fun save(
        session: OnlineAnonymousSessionDto,
    ) {
        require(session.playerId.isNotBlank()) {
            "A sessão anônima precisa ter playerId."
        }

        require(session.accessToken.isNotBlank()) {
            "A sessão anônima precisa ter accessToken."
        }

        require(session.expiresAtEpochMillis > 0L) {
            "A sessão anônima precisa ter expiração positiva."
        }

        store.write(
            session = session,
        )
    }

    fun clear() {
        store.clear()
    }
}

private fun OnlineAnonymousSessionDto.isValidAt(
    nowEpochMillis: Long,
): Boolean {
    return playerId.isNotBlank() &&
            accessToken.isNotBlank() &&
            nowEpochMillis < expiresAtEpochMillis
}