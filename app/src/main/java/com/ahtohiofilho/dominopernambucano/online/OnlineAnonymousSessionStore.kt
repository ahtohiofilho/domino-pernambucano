package com.ahtohiofilho.dominopernambucano.online

interface OnlineAnonymousSessionStore {
    fun read(): OnlineAnonymousSessionDto?

    fun write(
        session: OnlineAnonymousSessionDto,
    )

    fun clear()
}

class InMemoryOnlineAnonymousSessionStore(
    initialSession: OnlineAnonymousSessionDto? = null,
) : OnlineAnonymousSessionStore {
    private var currentSession = initialSession

    override fun read(): OnlineAnonymousSessionDto? {
        return currentSession
    }

    override fun write(
        session: OnlineAnonymousSessionDto,
    ) {
        currentSession = session
    }

    override fun clear() {
        currentSession = null
    }
}
